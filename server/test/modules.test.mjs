import {test} from 'node:test';
import assert from 'node:assert/strict';
import fs from 'node:fs/promises';
import os from 'node:os';
import path from 'node:path';
import crypto from 'node:crypto';
import { Downloads } from '../downloads.mjs';
import { library,fileAction } from '../library.mjs';
import { localAddress,controlDevice } from '../home.mjs';
import { Vault } from '../vault.mjs';
test('RPC torrent : handshake 409, ajout et suppression explicite par hash stable',async()=>{
 const calls=[];let count=0;
 const engine=new Downloads({fetcher:async(_,request)=>{calls.push(JSON.parse(request.body));if(count++===0)return new Response('',{status:409,headers:{'X-Transmission-Session-Id':'test-session'}});return Response.json({result:'success',arguments:{'torrent-added':{hashString:'a'.repeat(40),name:'Ubuntu'}}});}});
 const result=await engine.add({magnet:'magnet:?xt=urn:btih:'+'a'.repeat(40)});assert.equal(result.name,'Ubuntu');assert.equal(calls.length,2);
 await assert.rejects(engine.add({magnet:'http://127.0.0.1/secrets'}));await assert.rejects(engine.action('a'.repeat(40),{action:'remove',deleteData:true}));await engine.action('a'.repeat(40),{action:'remove',deleteData:true,confirm:'SUPPRIMER'});assert.equal(calls.at(-1).arguments['delete-local-data'],true);
});
test('Un tracker indisponible conserve l’état réel, les erreurs locales restent bloquantes et les secrets sont masqués',async()=>{
 const engine=new Downloads({fetcher:async()=>Response.json({result:'success',arguments:{torrents:[{hashString:'a'.repeat(40),status:4,error:2,errorString:'Tracker HTTP response 522 https://tracker.invalid/secret-key'},{hashString:'b'.repeat(40),status:0,error:3,errorString:'Permission denied /private/path'},{hashString:'c'.repeat(40),status:0,error:1,errorString:'https://tracker.invalid/secret-key'}]}})});
 const torrents=await engine.list();assert.equal(torrents[0].state,'telechargement');assert.match(torrents[0].error,/522/);assert.equal(torrents[1].state,'erreur');assert.match(torrents[1].error,/écrire/);assert.equal(torrents[2].state,'pause');assert.ok(!JSON.stringify(torrents).includes('secret-key'));assert.ok(!JSON.stringify(torrents).includes('/private/path'));
});
test('Bibliothèque et mutations : pas de liens externes, pas d’écrasement et corbeille récupérable',async()=>{
 const root=await fs.mkdtemp(path.join(os.tmpdir(),'maison-media-'));try{await fs.mkdir(path.join(root,'Films'));await fs.writeFile(path.join(root,'Films','Mon.Film.mkv'),'video');await fs.writeFile(path.join(root,'Films','other.mkv'),'other');const list=await library(root);assert.equal(list.length,2);assert.equal(list[0].title,'Mon Film');await assert.rejects(fileAction(root,{path:'Films/Mon.Film.mkv',action:'rename',name:'other.mkv'}));await assert.rejects(fileAction(root,{path:'Films/Mon.Film.mkv',action:'move',destination:'../outside.mkv'}));const trashed=await fileAction(root,{path:'Films/Mon.Film.mkv',action:'remove'});assert.equal(await fs.readFile(path.join(root,'.trash',trashed.id),'utf8'),'video');assert.equal((await library(root)).length,1);await assert.rejects(fileAction(root,{path:'',action:'remove'}));}finally{await fs.rm(root,{recursive:true,force:true});}
});
test('Domotique : destinations LAN uniquement, capacités et limites respectées',async()=>{
 for(const address of ['127.0.0.1','169.254.169.254','8.8.8.8','example.com','192.168.1.1:80','::1'])assert.throws(()=>localAddress(address));assert.equal(localAddress('192.168.1.5'),'192.168.1.5');
 await assert.rejects(controlDevice({component:'switch:0',capabilities:{brightness:false}},{brightness:80}));await assert.rejects(controlDevice({component:'rgb:0',capabilities:{color:true}},{rgb:[999,0,0]}));
});
test('Coffre : chiffrement aléatoire, intégrité et absence du secret en clair',()=>{
 const vault=new Vault(crypto.randomBytes(32));const a=vault.encrypt({password:'secret-value'}),b=vault.encrypt({password:'secret-value'});assert.notEqual(a.data,b.data);assert.equal(vault.decrypt(a).password,'secret-value');assert.ok(!JSON.stringify(a).includes('secret-value'));a.data=Buffer.from('invalid').toString('base64');assert.throws(()=>vault.decrypt(a));
});
