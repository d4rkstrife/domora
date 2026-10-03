import fs from 'node:fs/promises';
import path from 'node:path';
import crypto from 'node:crypto';
import { execFile, spawn } from 'node:child_process';
import { promisify } from 'node:util';
const exec = promisify(execFile);
const run = async (command, args) => (await exec(command, args, { timeout: 5000, maxBuffer: 262144 })).stdout;

export function captureDevice(output) {
  const caps = output.split(/Device Caps\s*:/)[1]?.split(/^[^\s]/m)[0] || '';
  return /Video Capture/.test(caps) && /Driver name\s*:\s*uvcvideo/.test(output);
}
export async function discoverUsb({ platform = process.platform, sys = '/sys/class/video4linux', dev = '/dev', execute = run } = {}) {
  if (platform !== 'linux') return { cameras: [], message: 'La détection USB est disponible sur votre serveur Linux.' };
  let nodes; try { nodes = await fs.readdir(sys); } catch (e) { if (e.code === 'ENOENT') return { cameras: [], message: 'Aucune webcam USB branchée.' }; throw e; }
  const aliases = new Map();
  for (const directory of ['by-path', 'by-id']) {
    const folder = path.join(dev, 'v4l', directory);
    try { for (const name of (await fs.readdir(folder)).sort()) aliases.set(await fs.realpath(path.join(folder, name)), path.join(folder, name)); } catch (e) { if (e.code !== 'ENOENT') throw e; }
  }
  const cameras = []; const warnings = [];
  for (const node of nodes.filter(n => /^video\d+$/.test(n)).sort()) {
    const source = path.join(dev, node);
    try {
      let details;
      try { details = await execute('v4l2-ctl', ['--device', source, '--all']); }
      catch (e) { if (e.code === 'ENOENT') e.cameraToolsMissing = true; throw e; }
      if (!captureDevice(details)) continue;
      const name = (await fs.readFile(path.join(sys, node, 'name'), 'utf8')).trim();
      const stablePath = aliases.get(source) || source;
      const identity = aliases.has(source) ? stablePath : await fs.realpath(path.join(sys, node, 'device')) + ':' + name + ':' + (await fs.readFile(path.join(sys, node, 'index'), 'utf8')).trim();
      cameras.push({ sourceId: crypto.createHash('sha256').update(identity).digest('hex'), name, source: stablePath, type: 'usb', online: true });
    } catch (e) {
      if (e.cameraToolsMissing) throw new Error('Les outils vidéo ne sont pas installés. Relancez l’installateur du serveur.');
      warnings.push('Une webcam est inaccessible. Vérifiez qu’elle n’est pas utilisée par une autre application et que le service dispose des permissions vidéo.');
    }
  }
  return { cameras, warnings: [...new Set(warnings)], message: cameras.length ? `${cameras.length} webcam(s) trouvée(s).` : warnings[0] || 'Aucune webcam USB compatible branchée.' };
}

export class JpegFrames {
  constructor(onFrame) { this.buffer = Buffer.alloc(0); this.onFrame = onFrame; }
  push(data) {
    this.buffer = Buffer.concat([this.buffer, data]);
    if (this.buffer.length > 2097152) { this.buffer = Buffer.alloc(0); throw new Error('Image caméra trop grande.'); }
    for (;;) {
      const start = this.buffer.indexOf(Buffer.from([255, 216]));
      if (start < 0) { this.buffer = this.buffer.subarray(-1); return; }
      const end = this.buffer.indexOf(Buffer.from([255, 217]), start + 2);
      if (end < 0) { this.buffer = this.buffer.subarray(start); return; }
      this.onFrame(Buffer.from(this.buffer.subarray(start, end + 2))); this.buffer = this.buffer.subarray(end + 2);
    }
  }
}
export function captureError(details) {
  if (/permission denied|operation not permitted/i.test(details)) return 'Le serveur n’a pas la permission d’utiliser cette webcam.';
  if (/device or resource busy/i.test(details)) return 'La webcam est déjà utilisée par une autre application.';
  if (/no such file|no such device/i.test(details)) return 'La webcam a été débranchée ou n’est plus accessible.';
  if (/invalid argument|not supported|cannot set|could not set/i.test(details)) return 'Le format vidéo demandé n’est pas accepté par cette webcam.';
  return 'Impossible de démarrer le direct de la webcam. Le diagnostic vidéo est disponible dans le journal du serveur.';
}
export class CameraCapture {
  constructor({ spawnProcess = spawn, idleMs = 15000, logger = console.error } = {}) { this.sessions = new Map(); this.spawnProcess = spawnProcess; this.idleMs = idleMs; this.logger = logger; }
  stop(id, error = new Error('La caméra a été arrêtée.')) { const state = this.sessions.get(id); if (!state) return; this.sessions.delete(id); clearTimeout(state.idle); state.process.kill(); for (const waiter of state.waiters) waiter.reject(error); state.waiters.clear(); }
  close() { for (const id of this.sessions.keys()) this.stop(id); }
  async frame(camera) {
    let state = this.sessions.get(camera.id);
    if (!state) {
      if (this.sessions.size >= 2) throw new Error('Deux caméras sont déjà ouvertes. Fermez un direct pour continuer.');
      const child = this.spawnProcess('ffmpeg', ['-hide_banner', '-loglevel', 'error', '-nostdin', '-f', 'v4l2', '-video_size', '640x480', '-i', camera.source, '-an', '-vf', 'fps=5,scale=640:-2', '-threads', '1', '-c:v', 'mjpeg', '-q:v', '5', '-f', 'image2pipe', 'pipe:1'], { stdio: ['ignore', 'pipe', 'pipe'] });
      state = { process: child, waiters: new Set(), image: null, lastFrame: 0, stderr: '' }; this.sessions.set(camera.id, state);
      const parser = new JpegFrames(image => { state.image = image; state.lastFrame = Date.now(); for (const waiter of state.waiters) waiter.resolve(image); state.waiters.clear(); });
      child.stdout.on('data', bytes => { try { parser.push(bytes); } catch (e) { this.stop(camera.id, e); } });
      child.stderr.on('data', bytes => { state.stderr = (state.stderr + bytes.toString()).slice(-32768); });
      const failed = error => {
        if (this.sessions.get(camera.id) !== state) return;
        this.logger(`Capture USB ${camera.id} : ${state.stderr || error?.message || 'FFmpeg arrêté sans image.'}`);
        this.stop(camera.id, new Error(error?.code === 'ENOENT' ? 'Le moteur vidéo FFmpeg n’est pas installé sur le serveur.' : captureError(state.stderr)));
      };
      child.on('error', failed); child.on('exit', () => failed());
    }
    clearTimeout(state.idle); state.idle = setTimeout(() => this.stop(camera.id), this.idleMs); state.idle.unref();
    if (state.image && Date.now() - state.lastFrame < 1500) return state.image;
    if (state.waiters.size >= 8) throw new Error('Trop de demandes pour cette caméra.');
    return new Promise((resolve, reject) => {
      const waiter = { resolve: image => { clearTimeout(timer); resolve(image); }, reject: error => { clearTimeout(timer); reject(error); } };
      const timer = setTimeout(() => { state.waiters.delete(waiter); reject(new Error('Impossible de recevoir le direct. Vérifiez la webcam et ses permissions.')); if (!state.waiters.size) this.stop(camera.id); }, 10000);
      state.waiters.add(waiter);
    });
  }
}
