import { test } from 'node:test';
import assert from 'node:assert/strict';
import fs from 'node:fs/promises';
import os from 'node:os';
import path from 'node:path';
import { resolveFile, byteRange, Store } from '../core.mjs';
test('Les fichiers restent dans le dossier partagé, y compris via liens', async () => {
  const temp = await fs.mkdtemp(path.join(os.tmpdir(), 'maison-')); try {
    const root = path.join(temp, 'media'); await fs.mkdir(root); await fs.writeFile(path.join(temp, 'secret'), 'secret'); await fs.writeFile(path.join(root, 'film'), 'film');
    assert.equal(await resolveFile(root, 'film'), path.join(root, 'film'));
    await assert.rejects(resolveFile(root, '../secret'));
    try { await fs.symlink(path.join(temp, 'secret'), path.join(root, 'link')); await assert.rejects(resolveFile(root, 'link')); } catch (e) { if (e.code !== 'EPERM') throw e; }
  } finally { await fs.rm(temp, { recursive: true, force: true }); }
});
test('Lecture vidéo : plages normales, suffixes et erreurs', () => {
  assert.deepEqual(byteRange('bytes=0-9', 100), { start: 0, end: 9 }); assert.deepEqual(byteRange('bytes=-10', 100), { start: 90, end: 99 }); assert.deepEqual(byteRange('bytes=90-', 100), { start: 90, end: 99 });
  for (const header of ['bytes=100-', 'bytes=20-10', 'bytes=-', 'bytes=0-1,3-4']) assert.throws(() => byteRange(header, 100));
});
test('La configuration conserve son identité après redémarrage', async () => {
  const root = await fs.mkdtemp(path.join(os.tmpdir(), 'maison-store-')); try { const a = await new Store(root).open(); a.state.rooms.push({ name: 'Salon' }); await a.save(); const b = await new Store(root).open(); assert.equal(a.state.id, b.state.id); assert.equal(b.state.rooms[0].name, 'Salon'); } finally { await fs.rm(root, { recursive: true, force: true }); }
});
