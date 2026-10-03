import fs from 'node:fs/promises';
import path from 'node:path';
import crypto from 'node:crypto';
import { execFile } from 'node:child_process';
import { promisify } from 'node:util';
import { resolveFile } from './core.mjs';
const exec = promisify(execFile);
export async function library(root) {
  const result = [];
  async function walk(folder, depth) {
    if (depth > 12 || result.length >= 2000) return;
    for (const file of await fs.readdir(path.join(root, folder), { withFileTypes: true })) {
      if (file.isSymbolicLink() || file.name.startsWith('.')) continue;
      const relative = path.posix.join(folder, file.name);
      if (file.isDirectory()) await walk(relative, depth + 1);
      else if (/\.(mkv|mp4|webm|avi|mov|m4v)$/i.test(file.name)) {
        const stat = await fs.stat(path.join(root, relative));
        result.push({ path: relative, title: file.name.replace(/\.[^.]+$/, '').replace(/[._]/g,' '), size: stat.size, modified: stat.mtime.toISOString() });
      }
      if (result.length >= 2000) break;
    }
  }
  await walk('', 0); return result;
}
export async function probe(root, file) {
  const actual = await resolveFile(root, file);
  try {
    const { stdout } = await exec('ffprobe', ['-v','error','-show_format','-show_streams','-of','json', actual], { timeout: 15000, maxBuffer: 1048576 });
    const parsed = JSON.parse(stdout);
    return { duration: Number(parsed.format?.duration || 0), container: parsed.format?.format_name, tracks: parsed.streams.map(s => ({ index: s.index, type: s.codec_type, codec: s.codec_name, language: s.tags?.language, title: s.tags?.title })), playback: 'direct', transcoding: false };
  } catch { throw new Error('Impossible d’analyser ce média. Vérifiez que FFmpeg est installé.'); }
}
export async function fileAction(root, input) {
  const source = await resolveFile(root, input.path);
  if (source === await fs.realpath(root)) throw new Error('Le dossier partagé ne peut pas être modifié.');
  if (input.action === 'remove') {
    const trash = path.join(root, '.trash'); await fs.mkdir(trash, { recursive: true });
    const id = crypto.randomUUID();
    await fs.rename(source, path.join(trash, id));
    await fs.writeFile(path.join(trash, id + '.json'), JSON.stringify({ original: input.path, removedAt: new Date().toISOString() }));
    return { removed: true, recoverable: true, id };
  }
  if (!['rename','move'].includes(input.action)) throw new Error('Action invalide.');
  const relative = input.action === 'rename' ? path.join(path.dirname(input.path), input.name || '') : input.destination;
  if (!relative || (input.action === 'rename' && (!input.name || /[\\/]/.test(input.name)))) throw new Error('Nom de fichier invalide.');
  const parent = await resolveFile(root, path.dirname(relative)); const target = path.join(parent, path.basename(relative));
  if (target === source) return { path: input.path };
  try { await fs.lstat(target); throw new Error('Un fichier porte déjà ce nom.'); } catch (e) { if (e.code !== 'ENOENT') throw e; }
  await fs.rename(source, target); return { path: relative };
}
