import { test } from 'node:test';
import assert from 'node:assert/strict';
import fs from 'node:fs/promises';
import path from 'node:path';
import os from 'node:os';
import { EventEmitter } from 'node:events';
import { PassThrough } from 'node:stream';
import { captureDevice, discoverUsb, JpegFrames, CameraCapture, captureError } from '../cameras.mjs';
const video = 'Driver name : uvcvideo\nCapabilities :\n\tVideo Capture\nDevice Caps : 0x04200001\n\tVideo Capture\n\tStreaming\nPriority: 2';
test('Les nœuds de métadonnées et les encodeurs du Pi sont exclus', () => {
  assert.equal(captureDevice(video), true);
  assert.equal(captureDevice(video.replace('Device Caps : 0x04200001\n\tVideo Capture', 'Device Caps : 0x04200001\n\tMetadata Capture')), false);
  assert.equal(captureDevice(video.replace('uvcvideo', 'bcm2835-codec')), false);
});
test('Recherche USB serveur : nom réel, sélection des nœuds vidéo et plateforme non Linux', async () => {
  const root = await fs.mkdtemp(path.join(os.tmpdir(), 'maison-usb-'));
  try {
    const sys = path.join(root, 'sys'); const dev = path.join(root, 'dev'); await fs.mkdir(dev);
    for (const node of ['video0', 'video1']) { await fs.mkdir(path.join(sys, node), { recursive: true }); await fs.writeFile(path.join(sys, node, 'name'), 'Webcam Bureau'); await fs.writeFile(path.join(sys, node, 'index'), node === 'video0' ? '0' : '1'); await fs.mkdir(path.join(sys, node, 'device')); }
    const result = await discoverUsb({ platform: 'linux', sys, dev, execute: async (_, args) => args[1].endsWith('video0') ? video : video.replace('Video Capture', 'Metadata Capture').replace('Video Capture', 'Metadata Capture') });
    assert.equal(result.cameras.length, 1); assert.equal(result.cameras[0].name, 'Webcam Bureau'); assert.equal(result.cameras[0].type, 'usb');
    assert.deepEqual((await discoverUsb({ platform: 'win32' })).cameras, []);
    const denied = await discoverUsb({ platform: 'linux', sys, dev, execute: async () => { throw Object.assign(Error('denied'), { code: 1 }); } }); assert.equal(denied.cameras.length, 0); assert.equal(denied.warnings.length, 1);
  } finally { await fs.rm(root, { recursive: true, force: true }); }
});
test('Images JPEG fragmentées, plusieurs images et mémoire bornée', () => {
  const frames = []; const parser = new JpegFrames(frame => frames.push(frame));
  const jpeg = Buffer.from([255,216,1,2,255,217]); parser.push(Buffer.from([0,255])); parser.push(jpeg.subarray(1,4)); parser.push(Buffer.concat([jpeg.subarray(4), jpeg])); assert.equal(frames.length, 2); assert.deepEqual(frames[0], jpeg);
  assert.throws(() => parser.push(Buffer.alloc(2097153)));
});
test('Un seul processus partagé, arrêt et libération des demandes', async () => {
  let starts = 0; let child; let killed = false;
  const capture = new CameraCapture({ spawnProcess: () => { starts++; child = new EventEmitter(); child.stdout = new PassThrough(); child.stderr = new PassThrough(); child.kill = () => { killed = true; }; return child; } });
  const camera = { id: 'cam1', source: '/dev/video0' }; const a = capture.frame(camera); const b = capture.frame(camera); const image = Buffer.from([255,216,4,255,217]); child.stdout.write(image);
  assert.deepEqual(await a, image); assert.deepEqual(await b, image); assert.equal(starts, 1); capture.close(); assert.equal(killed, true); assert.equal(capture.sessions.size, 0);
});
test('Un échec FFmpeg conserve sa cause, libère les demandes et permet un nouvel essai', async () => {
  const children = []; const logs = [];
  const capture = new CameraCapture({ logger: message => logs.push(message), spawnProcess: () => { const child = new EventEmitter(); child.stdout = new PassThrough(); child.stderr = new PassThrough(); child.kill = () => {}; children.push(child); return child; } });
  const first = capture.frame({ id: 'cam', source: '/dev/video0' });
  const rejected = assert.rejects(first, /déjà utilisée/);
  children[0].stderr.write('Device or resource busy'); children[0].emit('exit', 1); await rejected;
  assert.equal(capture.sessions.size, 0); assert.match(logs[0], /Device or resource busy/);
  const second = capture.frame({ id: 'cam', source: '/dev/video0' }); children[1].stdout.write(Buffer.from([255,216,1,255,217])); await second; capture.close();
  assert.match(captureError('Permission denied'), /permission/); assert.match(captureError('Invalid argument'), /format/);
});
