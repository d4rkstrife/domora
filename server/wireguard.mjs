import fs from 'node:fs/promises';
import path from 'node:path';
import {execFile} from 'node:child_process';
import {promisify} from 'node:util';
const execute=promisify(execFile);
export function validateWireGuardPeer(deviceId,publicKey){
 if(!/^[a-f0-9]{8}-[a-f0-9]{4}-[a-f0-9]{4}-[a-f0-9]{4}-[a-f0-9]{12}$/i.test(deviceId||''))throw Error('Appareil invalide.');
 if(!/^[A-Za-z0-9+/]{43}=$/.test(publicKey||'')||Buffer.from(publicKey,'base64').length!==32||Buffer.from(publicKey,'base64').every(byte=>byte===0)||Buffer.from(publicKey,'base64').toString('base64')!==publicKey)throw Error('Clé WireGuard invalide.');
}
export class WireGuardPeers{
 constructor({directory='/var/lib/maison-wireguard',exec=execute}={}){this.directory=directory;this.exec=exec;this.queue=Promise.resolve();}
 async status(){try{const config=JSON.parse(await fs.readFile(path.join(this.directory,'server.json'),'utf8'));return {configured:true,endpoint:config.endpoint,serverAddress:'10.203.77.1'};}catch(e){if(e.code==='ENOENT')return {configured:false};throw e;}}
 async enroll(deviceId,publicKey){
  validateWireGuardPeer(deviceId,publicKey);
  const operation=this.queue.then(async()=>{
   const status=await this.status();if(!status.configured)throw Error('WireGuard n’est pas installé sur le Pi.');
   const file=path.join(this.directory,'peers.json');let peers=[];try{peers=JSON.parse(await fs.readFile(file,'utf8'));}catch(e){if(e.code!=='ENOENT')throw e;}
   if(peers.some(peer=>peer.publicKey===publicKey&&peer.deviceId!==deviceId))throw Error('Cette clé appartient déjà à un autre appareil.');
   let peer=peers.find(peer=>peer.deviceId===deviceId);const oldKey=peer?.publicKey;
   if(!peer){const used=new Set(peers.map(p=>p.address));const number=Array.from({length:253},(_,i)=>i+2).find(i=>!used.has(`10.203.77.${i}/32`));if(!number)throw Error('Nombre maximal de téléphones atteint.');peer={deviceId,address:`10.203.77.${number}/32`};peers.push(peer);}
   await this.exec('wg',['set','maison-wg','peer',publicKey,'allowed-ips',peer.address],{timeout:5000});
   if(oldKey&&oldKey!==publicKey)await this.exec('wg',['set','maison-wg','peer',oldKey,'remove'],{timeout:5000});
   peer.publicKey=publicKey;
   await fs.writeFile(file+'.tmp',JSON.stringify(peers),{mode:0o600});await fs.rename(file+'.tmp',file);
   const result=await this.exec('wg',['show','maison-wg','public-key'],{timeout:5000});
   return {publicKey:result.stdout.trim(),endpoint:status.endpoint,address:peer.address,serverAddress:status.serverAddress};
  });this.queue=operation.catch(()=>{});return operation;
 }
 async revoke(deviceId){
  const operation=this.queue.then(async()=>{const file=path.join(this.directory,'peers.json');let peers;try{peers=JSON.parse(await fs.readFile(file,'utf8'));}catch(e){if(e.code==='ENOENT')return;throw e;}const target=peers.find(p=>p.deviceId===deviceId);if(!target)return;await this.exec('wg',['set','maison-wg','peer',target.publicKey,'remove'],{timeout:5000});await fs.writeFile(file+'.tmp',JSON.stringify(peers.filter(p=>p.deviceId!==deviceId)),{mode:0o600});await fs.rename(file+'.tmp',file);});this.queue=operation.catch(()=>{});await operation;return {revoked:true};
 }
 async restore(){const file=path.join(this.directory,'peers.json');let peers;try{peers=JSON.parse(await fs.readFile(file,'utf8'));}catch(e){if(e.code==='ENOENT')return;throw e;}for(const peer of peers){validateWireGuardPeer(peer.deviceId,peer.publicKey);if(!/^10\.203\.77\.\d{1,3}\/32$/.test(peer.address))throw Error('Adresse du pair invalide.');await this.exec('wg',['set','maison-wg','peer',peer.publicKey,'allowed-ips',peer.address],{timeout:5000});}}
}
