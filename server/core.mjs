import fs from 'node:fs/promises';
import path from 'node:path';
import crypto from 'node:crypto';

export const digest = value => crypto.createHash('sha256').update(value).digest('hex');
export async function resolveFile(root, relative = '') {
  const base = await fs.realpath(root);
  const candidate = path.resolve(base, relative);
  if (candidate !== base && !candidate.startsWith(base + path.sep)) throw new Error('Accès interdit');
  const actual = await fs.realpath(candidate);
  if (actual !== base && !actual.startsWith(base + path.sep)) throw new Error('Accès interdit');
  return actual;
}
export function byteRange(header, size) {
  if (!header) return null;
  const m = /^bytes=(\d*)-(\d*)$/.exec(header);
  if (!m || (!m[1] && !m[2]) || !size) throw new Error('Plage invalide');
  const start = m[1] ? Number(m[1]) : Math.max(0, size - Number(m[2]));
  const end = m[1] && m[2] ? Math.min(Number(m[2]), size - 1) : size - 1;
  if (!Number.isSafeInteger(start) || !Number.isSafeInteger(end) || start > end || start >= size) throw new Error('Plage invalide');
  return { start, end };
}
export class Store {
  constructor(directory) { this.directory = directory; this.queue = Promise.resolve(); }
  async open() {
    await fs.mkdir(this.directory, { recursive: true, mode: 0o700 });
    this.file = path.join(this.directory, 'configuration.json');
    try { this.state = JSON.parse(await fs.readFile(this.file, 'utf8')); }
    catch (e) { if (e.code !== 'ENOENT') throw e; this.state = { id: crypto.randomUUID(), name: 'Ma Maison', devices: [], rooms: [], cameras: [], scenes: [] }; await this.save(); }
    this.state.homeDevices ||= []; this.state.events ||= []; this.state.playback ||= {};
    this.state.devices.forEach((device,index) => { device.role ||= index === 0 ? 'admin' : 'user'; });
    return this;
  }
  save() {
    const snapshot = JSON.stringify(this.state, null, 2);
    this.queue = this.queue.then(async () => { await fs.writeFile(this.file + '.tmp', snapshot, { mode: 0o600 }); await fs.rename(this.file + '.tmp', this.file); });
    return this.queue;
  }
}
