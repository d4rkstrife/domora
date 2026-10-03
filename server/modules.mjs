import fs from 'node:fs/promises';
import path from 'node:path';
import crypto from 'node:crypto';
import { Downloads } from './downloads.mjs';
import { library, probe, fileAction } from './library.mjs';
import { discoverHome, inspectShelly, controlDevice } from './home.mjs';
import { administrative } from './admin-client.mjs';
const downloads = new Downloads();
const failure = message => { throw new Error(message); };
export async function modules({ req, res, route, url, store, media, device, json, body }) {
  const admin = () => { if (device.role !== 'admin') { json(res,403,{message:'Cette action est réservée au propriétaire.'}); return false; } return true; };
  const input = () => body(req);
  const ok = value => { json(res,200,value); return true; };
  const event = (type, message) => { store.state.events ||= []; store.state.events.unshift({ id: crypto.randomUUID(), type, message, at:new Date().toISOString() }); store.state.events = store.state.events.slice(0,200); };
  if(req.method==='POST'&&route==='/api/v1/server/action'){if(!admin())return true;const v=await input();if(!['restart-services','reboot','smb-credentials'].includes(v.action))failure('Action administrative invalide.');return ok(await administrative(v));}
  if(req.method==='GET'&&route==='/api/v1/wireguard/status')return ok(await administrative({action:'wireguard-status'}));
  if(req.method==='POST'&&route==='/api/v1/wireguard/enroll'){if(!req.socket.encrypted||req.remoteAccess)failure('L’activation WireGuard doit se faire en HTTPS depuis le réseau domestique.');const v=await input();return ok(await administrative({action:'wireguard-enroll',deviceId:device.id,publicKey:v.publicKey}));}
  if(req.method==='GET'&&route==='/api/v1/server/update'){if(!admin())return true;return ok({current:'0.2.0',available:false,sourceConfigured:false,message:'Le dépôt de distribution n’est pas encore connecté. Les mises à jour locales préservent la configuration.'});}
  if (req.method === 'GET' && route === '/api/v1/downloads') return ok(await downloads.list());
  if (req.method === 'POST' && route === '/api/v1/downloads') { const result = await downloads.add(await input()); event('downloads','Téléchargement ajouté'); await store.save(); return ok(result); }
  if (req.method === 'POST' && /^\/api\/v1\/downloads\/[^/]+\/action$/.test(route)) { await downloads.action(route.split('/')[4],await input()); return ok({success:true}); }
  if (req.method === 'GET' && route === '/api/v1/library') return ok((await library(media)).map(item => ({...item, progress:store.state.playback?.[item.path] || null})));
  if (req.method === 'GET' && route === '/api/v1/media/probe') return ok(await probe(media,url.searchParams.get('path') || ''));
  if (req.method === 'GET' && route === '/api/v1/media/progress') return ok(store.state.playback?.[url.searchParams.get('path')] || {position:0,duration:0});
  if (req.method === 'POST' && route === '/api/v1/media/progress') { const v = await input(); if (typeof v.path !== 'string' || !Number.isFinite(v.position) || v.position < 0 || !Number.isFinite(v.duration) || v.duration < 0) failure('Progression invalide.'); store.state.playback ||= {}; store.state.playback[v.path] = { position:v.position,duration:v.duration,at:new Date().toISOString() }; await store.save(); return ok({saved:true}); }
  if (req.method === 'POST' && route === '/api/v1/files/action') return ok(await fileAction(media,await input()));
  if (req.method === 'GET' && route === '/api/v1/events') return ok(store.state.events || []);
  if (req.method === 'GET' && route === '/api/v1/settings') return ok({ name:store.state.name, remoteUrl:store.state.remoteUrl || '', notifications:store.state.notifications || {downloads:true,storage:true,cameras:true,updates:true} });
  if (req.method === 'POST' && route === '/api/v1/settings') { if (!admin()) return true; const v = await input(); if (v.name) store.state.name = String(v.name).trim().slice(0,80); if (v.remoteUrl !== undefined) { if (v.remoteUrl && (!/^https:\/\/[a-z0-9.-]+\/?$/i.test(v.remoteUrl) || new URL(v.remoteUrl).username)) failure('L’accès distant doit utiliser une adresse HTTPS.'); store.state.remoteUrl = v.remoteUrl.replace(/\/$/,''); } if (v.notifications) store.state.notifications = Object.fromEntries(['downloads','storage','cameras','updates'].map(k => [k,v.notifications[k] === true])); await store.save(); return ok({saved:true}); }
  if (req.method === 'GET' && route === '/api/v1/backup') { if (!admin()) return true; const {devices,...config} = store.state; return ok({schema:1,at:new Date().toISOString(),configuration:config,authorizedDevices:devices.map(({id,name,publicKey,role,revoked,createdAt})=>({id,name,publicKey,role,revoked,createdAt})),secretsIncluded:false}); }
  if (req.method === 'POST' && route === '/api/v1/backup/restore') { if (!admin()) return true; const v = await input(); if(v.confirm!=='RESTAURER'||v.backup?.schema!==1) failure('Sauvegarde ou confirmation invalide.'); const c=v.backup.configuration; if(!c||!Array.isArray(c.rooms)||!Array.isArray(c.scenes)||!Array.isArray(c.cameras))failure('Sauvegarde invalide.'); for(const key of ['rooms','scenes','playback','notifications'])if(c[key]!==undefined)store.state[key]=c[key]; event('server','Configuration restaurée. Les appareils et secrets doivent être vérifiés séparément.'); await store.save(); return ok({restored:true}); }
  if (req.method === 'POST' && route === '/api/v1/home/discover') return ok(await discoverHome());
  if (req.method === 'GET' && route === '/api/v1/home/devices') return ok(store.state.homeDevices || []);
  if (req.method === 'POST' && route === '/api/v1/home/devices') { const v=await input(); const found=await inspectShelly(v.address); const selected=found.find(d=>d.component===v.component); if(!selected)failure('Appareil introuvable.'); store.state.homeDevices ||= []; const existing=store.state.homeDevices.find(d=>d.sourceId===selected.sourceId); if(existing)return ok(existing); store.state.homeDevices.push(selected); await store.save(); return ok(selected); }
  const control=/^\/api\/v1\/home\/devices\/([^/]+)\/control$/.exec(route);
  if(req.method==='POST'&&control){const target=store.state.homeDevices?.find(d=>d.id===control[1]);if(!target)failure('Appareil introuvable.');const v=await input();await controlDevice(target,v);target.state={...target.state,...v};await store.save();return ok({success:true});}
  for(const kind of ['rooms','scenes']) {
    if(req.method==='POST'&&route===`/api/v1/${kind}`){const v=await input();if(typeof v.name!=='string'||!v.name.trim())failure('Donnez un nom.');const item={id:crypto.randomUUID(),name:v.name.trim().slice(0,80)};if(kind==='scenes'){if(!Array.isArray(v.actions)||v.actions.length>30||v.actions.some(a=>!store.state.homeDevices?.some(d=>d.id===a.deviceId)))failure('Actions de scène invalides.');item.actions=v.actions;}store.state[kind].push(item);await store.save();return ok(item);}
    const match=new RegExp(`^/api/v1/${kind}/([^/]+)$`).exec(route);
    if(req.method==='DELETE'&&match){store.state[kind]=store.state[kind].filter(i=>i.id!==match[1]);if(kind==='rooms')for(const d of store.state.homeDevices||[])if(d.roomId===match[1])d.roomId=null;await store.save();return ok({removed:true});}
  }
  const run=/^\/api\/v1\/scenes\/([^/]+)\/run$/.exec(route);
  if(req.method==='POST'&&run){const scene=store.state.scenes.find(s=>s.id===run[1]);if(!scene)failure('Scène introuvable.');const results=[];for(const action of scene.actions){try{const d=store.state.homeDevices.find(d=>d.id===action.deviceId);await controlDevice(d,action.values);results.push({deviceId:d.id,success:true});}catch(e){results.push({deviceId:action.deviceId,success:false,message:e.message});}}event('scenes',`Scène ${scene.name} exécutée`);await store.save();return ok({results});}
  if(req.method==='POST'&&route==='/api/v1/home/assign-room'){const v=await input();const d=store.state.homeDevices?.find(d=>d.id===v.deviceId);if(!d||v.roomId&&!store.state.rooms.some(r=>r.id===v.roomId))failure('Appareil ou pièce introuvable.');d.roomId=v.roomId||null;await store.save();return ok({saved:true});}
  if(req.method==='POST'&&route==='/api/v1/users/role'){if(!admin())return true;const v=await input();const d=store.state.devices.find(d=>d.id===v.deviceId&&!d.revoked);if(!d||d.role==='tv'||!['admin','user'].includes(v.role))failure('Utilisateur ou rôle invalide.');if(d.role==='admin'&&v.role!=='admin'&&store.state.devices.filter(d=>d.role==='admin'&&!d.revoked).length<=1)failure('Conservez au moins un administrateur.');d.role=v.role;await store.save();return ok({saved:true});}
  return false;
}
