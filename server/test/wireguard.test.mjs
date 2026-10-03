import test from 'node:test';
import assert from 'node:assert/strict';
import fs from 'node:fs/promises';
import os from 'node:os';
import path from 'node:path';
import {WireGuardPeers,validateWireGuardPeer} from '../wireguard.mjs';
const id=n=>`00000000-0000-0000-0000-${String(n).padStart(12,'0')}`;
const key=n=>Buffer.alloc(32,n).toString('base64');
test('WireGuard refuse les identités et clés malformées',()=>{
 assert.throws(()=>validateWireGuardPeer('shell;command',key(1)));
 assert.throws(()=>validateWireGuardPeer(id(1),key(0)));
 assert.throws(()=>validateWireGuardPeer(id(1),'invalid'));
});
test('WireGuard attribue des adresses distinctes, interdit une clé partagée et révoque',async()=>{
 const directory=await fs.mkdtemp(path.join(os.tmpdir(),'maison-wg-'));
 const calls=[];
 try{
  await fs.writeFile(path.join(directory,'server.json'),JSON.stringify({endpoint:'example.org:51820'}));
  const peers=new WireGuardPeers({directory,exec:async(command,args)=>{calls.push([command,...args]);return {stdout:key(3)+'\n'};}});
  const profiles=await Promise.all([peers.enroll(id(1),key(1)),peers.enroll(id(2),key(2))]);
  assert.equal(profiles[0].address,'10.203.77.2/32');
  assert.equal(profiles[1].address,'10.203.77.3/32');
  await assert.rejects(peers.enroll(id(3),key(1)),/autre appareil/);
  await peers.revoke(id(1));
  assert.equal(JSON.parse(await fs.readFile(path.join(directory,'peers.json'))).length,1);
  assert.ok(calls.some(args=>args.at(-1)==='remove'));
 }finally{await fs.rm(directory,{recursive:true,force:true});}
});
