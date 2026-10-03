import crypto from 'node:crypto';
import fs from 'node:fs/promises';
import path from 'node:path';
import { resolveFile } from './core.mjs';
import { library } from './library.mjs';
import { administrative } from './admin-client.mjs';
import { validateWireGuardPeer } from './wireguard.mjs';

export function tvRouteAllowed(method, route) {
  return method === 'GET' && ['/api/v1/tv/library', '/api/v1/tv/progress', '/api/v1/files/content'].includes(route)
    || method === 'POST' && route === '/api/v1/tv/progress';
}
export function normalTvPath(value) {
  if (typeof value !== 'string' || value.length > 1024 || value.includes('\\') || value.includes('\0') || value.startsWith('/') || value.split('/').some(p => p === '..' || p === '.' || p.startsWith('.')))
    throw Error('Chemin vidéo invalide.');
  return value.replace(/\/$/, '');
}
export async function tvFileAllowed(root, device, value) {
  const relative = normalTvPath(value);
  if (!/\.(mp4|mkv|webm|avi|mov|m4v)$/i.test(relative)) throw Error('La télévision ne peut lire que des vidéos.');
  const base = await fs.realpath(root);
  const actual = await resolveFile(root, relative);
  for (const folder of device.mediaRoots || []) {
    if (folder !== '' && !relative.startsWith(folder + '/')) continue;
    const allowed = await resolveFile(root, folder);
    if (allowed !== path.resolve(base, folder)) continue; // A replaced/symlinked grant must not redirect access.
    if (actual.startsWith(allowed + path.sep) && (await fs.stat(actual)).isFile()) return true;
  }
  throw Error('Cette vidéo ne fait pas partie des dossiers autorisés.');
}
export async function televisions({req,res,route,url,store,media,device,json,body,certificate,enroll=administrative}) {
  const ok = value => { json(res,200,value); return true; };
  if (req.method === 'POST' && route === '/api/v1/tv/approve') {
    if (device.role !== 'admin') { json(res,403,{message:'Seul le propriétaire peut autoriser une télévision.'}); return true; }
    if (!req.socket.encrypted || !certificate) throw Error('Une connexion HTTPS au serveur est requise.');
    const input = await body(req);
    if (!Array.isArray(input.roots) || !input.roots.length || input.roots.length > 20) throw Error('Sélectionnez au moins un dossier vidéo.');
    const roots = [...new Set(input.roots.map(normalTvPath))];
    const base = await fs.realpath(media);
    for (const folder of roots) {
      const actual = await resolveFile(media, folder);
      if (actual !== path.resolve(base,folder) || !(await fs.stat(actual)).isDirectory()) throw Error('Dossier partagé invalide.');
    }
    const key = crypto.createPublicKey(input.publicKey);
    if (key.asymmetricKeyType !== 'ec' || key.asymmetricKeyDetails.namedCurve !== 'prime256v1') throw Error('Clé de télévision invalide.');
    const publicKey = key.export({type:'spki',format:'pem'});
    let target = store.state.devices.find(d => d.role === 'tv' && d.publicKey === publicKey && !d.revoked);
    const id = target?.id || crypto.randomUUID();
    validateWireGuardPeer(id,input.wireguardKey);
    const profile = await enroll({action:'wireguard-enroll',deviceId:id,publicKey:input.wireguardKey});
    if (!target) {
      target = {id,role:'tv',publicKey,createdAt:new Date().toISOString(),revoked:false};
      store.state.devices.push(target);
    }
    target.name = String(input.name || 'Domora TV').trim().slice(0,80);
    target.mediaRoots = roots;
    await store.save();
    return ok({deviceId:id,serverId:store.state.id,publicKey,profile,certificate:certificate.raw.toString('base64'),roots});
  }
  if (device.role !== 'tv') return false;
  if (req.method === 'GET' && route === '/api/v1/tv/library') {
    const videos = [];
    for (const video of await library(media)) {
      try { await tvFileAllowed(media,device,video.path); videos.push({...video,progress:device.playback?.[video.path] || null}); } catch {}
    }
    return ok(videos);
  }
  if (route === '/api/v1/tv/progress') {
    const input = req.method === 'POST' ? await body(req) : {path:url.searchParams.get('path')};
    await tvFileAllowed(media,device,input.path);
    if (req.method === 'GET') return ok(device.playback?.[input.path] || {position:0,duration:0});
    if (!Number.isFinite(input.position) || input.position < 0 || !Number.isFinite(input.duration) || input.duration < 0) throw Error('Progression invalide.');
    device.playback ||= {};
    device.playback[input.path] = {position:input.position,duration:input.duration,at:new Date().toISOString()};
    await store.save(); return ok({saved:true});
  }
  return false;
}
