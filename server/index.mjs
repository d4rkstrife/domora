import http from 'node:http';
import https from 'node:https';
import fs from 'node:fs/promises';
import { createReadStream } from 'node:fs';
import path from 'node:path';
import os from 'node:os';
import crypto from 'node:crypto';
import { fileURLToPath } from 'node:url';
import { Store, digest, resolveFile, byteRange } from './core.mjs';
import { discoverUsb, CameraCapture } from './cameras.mjs';
import { modules } from './modules.mjs';
import { televisions, tvRouteAllowed, tvFileAllowed } from './televisions.mjs';
import { Downloads } from './downloads.mjs';
import { administrative } from './admin-client.mjs';

const home = path.resolve(process.env.MAISON_DATA || 'data');
const media = path.resolve(process.env.MAISON_MEDIA || path.join(home, 'media'));
const store = await new Store(home).open();
await fs.mkdir(media, { recursive: true });
let code = crypto.randomInt(100000, 1000000).toString();
let pairUntil = Date.now() + 600000;
const challenges = new Map();
const attempts = new Map();
const web = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '../web');
const tlsKeyFile=process.env.MAISON_TLS_KEY || (process.env.CREDENTIALS_DIRECTORY ? path.join(process.env.CREDENTIALS_DIRECTORY,'tls-key') : null);
let certificate;
if(tlsKeyFile&&process.env.MAISON_TLS_CERT)certificate=new crypto.X509Certificate(await fs.readFile(process.env.MAISON_TLS_CERT));
function json(res, status, value) { res.writeHead(status, { 'Content-Type': 'application/json; charset=utf-8', 'Cache-Control': 'no-store', 'X-Content-Type-Options': 'nosniff' }); res.end(JSON.stringify(value)); }
async function body(req) { let raw = ''; for await (const part of req) { raw += part; if (Buffer.byteLength(raw) > 3000000) throw new Error('Requête trop grande'); } return raw ? JSON.parse(raw) : {}; }
const capture = new CameraCapture();
const capabilities = { files: true, media: true, server: true, rooms: true, cameras: process.platform === 'linux', downloads: false, devices: process.platform === 'linux', scenes: true, remote: false, backup:true, notifications:true, integrations:['shelly'] };
const downloadEngine = new Downloads();
const monitor=setInterval(async()=>{
  let changed=false;
  function emit(type,message){store.state.events ||= [];store.state.events.unshift({id:crypto.randomUUID(),type,message,at:new Date().toISOString()});store.state.events=store.state.events.slice(0,200);changed=true;}
  try{const torrents=await downloadEngine.list();const previous=store.state.downloadProgress;const current={};for(const torrent of torrents){current[torrent.id]=torrent.progress;if(previous&&previous[torrent.id]!==undefined&&previous[torrent.id]<1&&torrent.progress>=1)emit('downloads',`Téléchargement terminé : ${torrent.name}`);}store.state.downloadProgress=current;if(previous!==undefined)changed=true;}catch{}
  try{const disk=await fs.statfs(media);const low=disk.bavail*disk.bsize<2147483648;if(low&&!store.state.storageWarning)emit('storage','Le stockage du serveur est presque plein.');if(store.state.storageWarning!==low){store.state.storageWarning=low;changed=true;}}catch{}
  if(changed)await store.save().catch(()=>{});
},60000);monitor.unref();
let scanning;
let scanResult; let scannedAt = 0;
function scan(force = false) { if (!force && scanResult && Date.now() - scannedAt < 2000) return Promise.resolve(scanResult); if (!scanning) scanning = discoverUsb().then(result => { scanResult = result; scannedAt = Date.now(); return result; }).finally(() => { scanning = null; }); return scanning; }
const handler = async (req, res) => {
  try {
    const url = new URL(req.url, 'http://localhost');
    const route = url.pathname;
    if(req.method==='GET'&&route==='/api/v1/security')return json(res,200,{tls:!!certificate,port:Number(process.env.MAISON_TLS_PORT||8789),certificate:certificate?.raw.toString('base64'),fingerprint:certificate?.fingerprint256});
    if (req.method === 'GET' && route === '/api/v1/discovery') return json(res, 200, { id: store.state.id, name: store.state.name, version: '0.2.0', pairingAvailable: !req.remoteAccess && Date.now() < pairUntil, secure: !!req.socket.encrypted, remoteAvailable: !!store.state.remoteUrl });
    if(process.env.MAISON_REQUIRE_TLS==='1'&&route.startsWith('/api/')&&!req.socket.encrypted&&!req.remoteAccess)return json(res,426,{message:'Une connexion chiffrée est nécessaire. Mettez à jour l’application.'});
    if (req.method === 'POST' && route === '/api/v1/pair') {
      if (req.remoteAccess) return json(res,403,{message:'L’appairage initial se fait uniquement à la maison.'});
      const address = req.socket.remoteAddress; const attempt = attempts.get(address) || { count: 0, until: Date.now() + 600000 };
      if (Date.now() > attempt.until) { attempt.count = 0; attempt.until = Date.now() + 600000; }
      attempts.set(address, attempt);
      if (++attempt.count > 10) return json(res, 429, { message: 'Trop de tentatives. Réessayez dans dix minutes.' });
      const input = await body(req);
      if (Date.now() >= pairUntil || digest(String(input.code)) !== digest(code)) return json(res, 403, { message: 'Code incorrect ou expiré.' });
      const publicKey = crypto.createPublicKey(input.publicKey);
      if (publicKey.asymmetricKeyType !== 'ec') throw new Error('Clé EC requise');
      const device = { id: crypto.randomUUID(), name: String(input.name || 'Téléphone').slice(0, 80), publicKey: publicKey.export({ type: 'spki', format: 'pem' }), createdAt: new Date().toISOString(), revoked: false, role: store.state.devices.some(d=>!d.revoked&&d.role==='admin')?'user':'admin' };
      store.state.devices.push(device); await store.save();
      return json(res, 201, { deviceId: device.id, serverId: store.state.id });
    }
    if (req.method === 'POST' && route === '/api/v1/auth/challenge') {
      const input = await body(req); const device = store.state.devices.find(d => d.id === input.deviceId && !d.revoked);
      if (!device) return json(res, 403, { message: 'Appareil non autorisé.' });
      if (challenges.size > 1000) challenges.clear();
      const challenge = crypto.randomBytes(32).toString('base64'); challenges.set(device.id, { challenge, expires: Date.now() + 60000 });
      return json(res, 200, { challenge });
    }
    if (req.method === 'POST' && route === '/api/v1/auth/session') {
      const input = await body(req); const device = store.state.devices.find(d => d.id === input.deviceId && !d.revoked); const pending = challenges.get(input.deviceId); challenges.delete(input.deviceId);
      if (!device || !pending || pending.expires < Date.now() || !crypto.verify('sha256', Buffer.from(pending.challenge), device.publicKey, Buffer.from(input.signature || '', 'base64'))) return json(res, 403, { message: 'Authentification refusée.' });
      const token = crypto.randomBytes(32).toString('base64url'); device.tokenHash = digest(token); device.expires = Date.now() + 86400000; await store.save();
      return json(res, 200, { token, expires: device.expires });
    }
    if (route.startsWith('/api/')) {
      const token = (req.headers.authorization || '').replace(/^Bearer /, '');
      const device = store.state.devices.find(d => !d.revoked && d.expires > Date.now() && d.tokenHash === digest(token));
      if (!device) return json(res, 401, { message: 'Appairez votre appareil pour accéder au serveur.' });
      if (device.role === 'tv') {
        if (!tvRouteAllowed(req.method,route)) return json(res,403,{message:'Cet accès est réservé aux vidéos autorisées.'});
        if (route === '/api/v1/files/content') {
          try { await tvFileAllowed(media,device,url.searchParams.get('path') || ''); }
          catch { return json(res,403,{message:'Vidéo non autorisée.'}); }
        }
      }
      if (await televisions({req,res,route,url,store,media,device,json,body,certificate})) return;
      if (req.method === 'POST' && route === '/api/v1/pairing/open') { if(device.role!=='admin')return json(res,403,{message:'Action réservée au propriétaire.'});code=crypto.randomInt(100000,1000000).toString();pairUntil=Date.now()+600000;return json(res,200,{code,expires:pairUntil}); }
      if (await modules({ req,res,route,url,store,media,device,json,body })) return;
      if (req.method === 'POST' && route === '/api/v1/cameras/discover') { try { return json(res, 200, await scan(true)); } catch (e) { return json(res, 503, { message: e.message }); } }
      if (req.method === 'POST' && route === '/api/v1/cameras') {
        const input = await body(req); const found = (await scan()).cameras.find(c => c.sourceId === input.sourceId);
        if (!found) return json(res, 404, { message: 'Cette webcam n’est plus disponible. Relancez la recherche.' });
        const existing = store.state.cameras.find(c => c.sourceId === found.sourceId);
        if (existing) return json(res, 200, existing);
        const camera = { ...found, id: crypto.randomUUID(), name: String(input.name || found.name).trim().slice(0, 80), room: String(input.room || '').slice(0, 80) };
        store.state.cameras.push(camera); await store.save(); return json(res, 201, camera);
      }
      if (req.method === 'GET' && route === '/api/v1/cameras') {
        const found = await scan(); return json(res, 200, store.state.cameras.map(c => ({ ...c, online: found.cameras.some(f => f.sourceId === c.sourceId) })));
      }
      const cameraRoute = /^\/api\/v1\/cameras\/([^/]+)(\/snapshot)?$/.exec(route);
      if (cameraRoute) {
        const camera = store.state.cameras.find(c => c.id === cameraRoute[1]);
        if (!camera) return json(res, 404, { message: 'Caméra introuvable.' });
        if (req.method === 'DELETE' && !cameraRoute[2]) { capture.stop(camera.id); store.state.cameras = store.state.cameras.filter(c => c.id !== camera.id); await store.save(); return json(res, 200, { removed: true }); }
        if (req.method === 'GET' && cameraRoute[2]) {
          const found = (await scan()).cameras.find(c => c.sourceId === camera.sourceId);
          if (!found) return json(res, 503, { message: 'La webcam est débranchée ou inaccessible.' });
          try { const image = await capture.frame({ ...camera, source: found.source }); res.writeHead(200, { 'Content-Type': 'image/jpeg', 'Content-Length': image.length, 'Cache-Control': 'no-store', 'X-Content-Type-Options': 'nosniff' }); return res.end(image); }
          catch (e) { return json(res, 503, { message: e.message }); }
        }
      }
      if (req.method === 'GET' && route === '/api/v1/capabilities') { try { await downloadEngine.call('session-get'); capabilities.downloads=true; } catch { capabilities.downloads=false; } return json(res,200,{...capabilities,remote:!!store.state.remoteUrl}); }
      if (req.method === 'GET' && route === '/api/v1/server') {
        const disk = await fs.statfs(media); const cpus = os.cpus();
        return json(res, 200, { id: store.state.id, name: store.state.name, hostname: os.hostname(), version: '0.2.0', uptime: os.uptime(), cpuCount: cpus.length, load: os.loadavg()[0], memory: { total: os.totalmem(), free: os.freemem() }, storage: { total: disk.blocks * disk.bsize, free: disk.bavail * disk.bsize }, connection: req.remoteAccess?'remote':'local', secure:!!req.socket.encrypted||!!req.remoteAccess, remoteAvailable: !!store.state.remoteUrl });
      }
      if (req.method === 'GET' && route === '/api/v1/devices/authorized') { if(device.role!=='admin')return json(res,403,{message:'Action réservée au propriétaire.'});return json(res, 200, store.state.devices.map(({ id, name, role, revoked, createdAt, mediaRoots }) => ({ id, name, role, revoked, createdAt, mediaRoots }))); }
      if (req.method === 'DELETE' && route.startsWith('/api/v1/devices/authorized/')) { if(device.role!=='admin')return json(res,403,{message:'Action réservée au propriétaire.'});const target = store.state.devices.find(d => d.id === route.split('/').at(-1)); if (!target) return json(res, 404, { message: 'Appareil introuvable.' }); target.revoked = true; delete target.tokenHash; await store.save();try{await administrative({action:'wireguard-revoke',deviceId:target.id});}catch{console.error('Révocation du tunnel à vérifier pour l’appareil '+target.id);}return json(res, 200, { revoked: true }); }
      if (req.method === 'GET' && ['/api/v1/rooms', '/api/v1/scenes'].includes(route)) return json(res, 200, store.state[route.split('/').at(-1)]);
      if (req.method === 'GET' && route === '/api/v1/files') {
        const relative = url.searchParams.get('path') || ''; const folder = await resolveFile(media, relative); const entries = await fs.readdir(folder, { withFileTypes: true }); const result = [];
        for (const item of entries) { if (item.isSymbolicLink() || item.name.startsWith('.')) continue; const stat = await fs.stat(path.join(folder, item.name)); result.push({ name: item.name, path: path.posix.join(relative.replaceAll('\\', '/'), item.name), directory: item.isDirectory(), size: stat.size, modified: stat.mtime.toISOString() }); }
        return json(res, 200, result);
      }
      if (req.method === 'GET' && route === '/api/v1/files/content') {
        const file = await resolveFile(media, url.searchParams.get('path') || ''); const stat = await fs.stat(file); if (!stat.isFile()) return json(res, 400, { message: 'Sélectionnez un fichier.' });
        let range; try { range = byteRange(req.headers.range, stat.size); } catch { res.writeHead(416, { 'Content-Range': `bytes */${stat.size}` }); return res.end(); }
        const types = { '.mp4': 'video/mp4', '.mkv': 'video/x-matroska', '.webm': 'video/webm', '.mp3': 'audio/mpeg', '.jpg': 'image/jpeg', '.png': 'image/png' };
        const headers = { 'Content-Type': types[path.extname(file).toLowerCase()] || 'application/octet-stream', 'Accept-Ranges': 'bytes', 'Content-Length': range ? range.end - range.start + 1 : stat.size, 'X-Content-Type-Options': 'nosniff', 'Cache-Control': 'no-store' };
        if (range) headers['Content-Range'] = `bytes ${range.start}-${range.end}/${stat.size}`;
        res.writeHead(range ? 206 : 200, headers); const stream = createReadStream(file, range || {}); stream.on('error', () => res.destroy()); res.on('close', () => stream.destroy()); return stream.pipe(res);
      }
      return json(res, 404, { message: 'Fonction indisponible dans cette version.' });
    }
    if (req.method !== 'GET') return json(res, 405, { message: 'Méthode non autorisée.' });
    const file = await resolveFile(web, route === '/' ? 'index.html' : route.slice(1));
    res.writeHead(200, { 'Content-Type': { '.html': 'text/html; charset=utf-8', '.css': 'text/css', '.js': 'text/javascript' }[path.extname(file)] || 'application/octet-stream', 'Content-Security-Policy': "default-src 'self'; style-src 'self'; script-src 'self'; connect-src 'self'; img-src 'self' blob:", 'X-Content-Type-Options': 'nosniff' }); res.end(await fs.readFile(file));
  } catch (e) { if (!res.headersSent) json(res, e.code === 'ENOENT' ? 404 : 400, { message: e.code === 'ENOENT' ? 'Fichier introuvable.' : e.message || 'Impossible de traiter cette demande.' }); else res.destroy(); }
};
const server = http.createServer(handler);
let tlsServer; let remoteServer;
if (tlsKeyFile && process.env.MAISON_TLS_CERT) {
  tlsServer=https.createServer({key:await fs.readFile(tlsKeyFile),cert:await fs.readFile(process.env.MAISON_TLS_CERT),minVersion:'TLSv1.2'},handler);
  tlsServer.listen(Number(process.env.MAISON_TLS_PORT||8789),process.env.HOST||'127.0.0.1');
}
if(process.env.MAISON_REMOTE_PORT){remoteServer=http.createServer((req,res)=>{req.remoteAccess=true;return handler(req,res);});remoteServer.listen(Number(process.env.MAISON_REMOTE_PORT),'127.0.0.1');}
for (const signal of ['SIGINT', 'SIGTERM']) process.on(signal, () => { capture.close(); server.close(); process.exit(0); });
server.listen(Number(process.env.PORT || 8787), process.env.HOST || '127.0.0.1', () => console.log(`Ma Maison : http://${process.env.HOST || '127.0.0.1'}:${process.env.PORT || 8787}\nCode d’appairage (10 minutes) : ${code}\nNe partagez ce code qu’avec les appareils à autoriser.`));
