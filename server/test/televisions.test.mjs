import {test} from 'node:test';
import assert from 'node:assert/strict';
import fs from 'node:fs/promises';
import os from 'node:os';
import path from 'node:path';
import crypto from 'node:crypto';
import {spawn} from 'node:child_process';
import {tvFileAllowed,normalTvPath,tvRouteAllowed,televisions} from '../televisions.mjs';

test('TV : seuls les fichiers vidéo des dossiers accordés sont accessibles',async()=>{
 const root=await fs.mkdtemp(path.join(os.tmpdir(),'domora-tv-'));
 try{
  await fs.mkdir(path.join(root,'Films'));await fs.mkdir(path.join(root,'Films-prives'));
  await fs.writeFile(path.join(root,'Films','ok.mp4'),'video');await fs.writeFile(path.join(root,'Films','secret.txt'),'secret');await fs.writeFile(path.join(root,'Films-prives','no.mp4'),'private');
  const device={mediaRoots:['Films']};
  assert.equal(await tvFileAllowed(root,device,'Films/ok.mp4'),true);
  for(const file of ['Films-prives/no.mp4','Films/secret.txt','Films/../Films-prives/no.mp4','../x.mp4','/Films/ok.mp4'])await assert.rejects(tvFileAllowed(root,device,file));
  await assert.rejects(tvFileAllowed(root,{mediaRoots:[]},'Films/ok.mp4'));
  await fs.symlink(path.join(root,'Films-prives'),path.join(root,'Link'),'junction');
  await assert.rejects(tvFileAllowed(root,{mediaRoots:['Link']},'Link/no.mp4'));
  assert.throws(()=>normalTvPath('.hidden/film.mp4'));
  assert.equal(tvRouteAllowed('POST','/api/v1/tv/progress'),true);
  for(const route of ['/api/v1/settings','/api/v1/cameras','/api/v1/files','/api/v1/downloads','/api/v1/users/role'])assert.equal(tvRouteAllowed('POST',route),false);
 }finally{await fs.rm(root,{recursive:true,force:true})}
});

test('Autorisation propriétaire, clés distinctes et progression isolée',async()=>{
 const root=await fs.mkdtemp(path.join(os.tmpdir(),'domora-approval-'));
 try{
  await fs.writeFile(path.join(root,'film.mp4'),'video');
  const store={state:{id:'server',devices:[],playback:{'film.mp4':{position:999}}},save:async()=>{}};
  const pair=crypto.generateKeyPairSync('ec',{namedCurve:'prime256v1'});const publicKey=pair.publicKey.export({type:'spki',format:'pem'});
  let input={name:'Salon',publicKey,wireguardKey:crypto.randomBytes(32).toString('base64'),roots:['']};let response,code,enrolls=0;
  const ctx={req:{method:'POST',socket:{encrypted:true}},res:{},route:'/api/v1/tv/approve',store,media:root,device:{role:'user'},json:(_,status,value)=>{code=status;response=value},body:async()=>input,certificate:{raw:Buffer.from('cert')},enroll:async()=>{enrolls++;return {endpoint:'test'}}};
  await televisions(ctx);assert.equal(code,403);assert.equal(enrolls,0);
  ctx.device={role:'admin'};await televisions(ctx);assert.equal(code,200);assert.equal(enrolls,1);assert.equal(store.state.devices[0].role,'tv');assert.deepEqual(store.state.devices[0].mediaRoots,['']);assert.equal(response.publicKey,publicKey);
  await televisions(ctx);assert.equal(store.state.devices.length,1);
  input={...input,roots:['../']};await assert.rejects(televisions(ctx));
  ctx.device=store.state.devices[0];ctx.route='/api/v1/tv/progress';input={path:'film.mp4',position:12,duration:50};await televisions(ctx);
  assert.equal(ctx.device.playback['film.mp4'].position,12);assert.equal(store.state.playback['film.mp4'].position,999);
  input={path:'film.mp4',position:-1,duration:50};await assert.rejects(televisions(ctx));
 }finally{await fs.rm(root,{recursive:true,force:true})}
});

test('API TV : interdiction des commandes maison, lecture partielle et révocation',async()=>{
 const root=await fs.mkdtemp(path.join(os.tmpdir(),'domora-tv-api-'));const port=30000+crypto.randomInt(10000);
 const keys=crypto.generateKeyPairSync('ec',{namedCurve:'prime256v1'});const adminKeys=crypto.generateKeyPairSync('ec',{namedCurve:'prime256v1'});
 const tv={id:crypto.randomUUID(),name:'TV',role:'tv',mediaRoots:['Films'],publicKey:keys.publicKey.export({type:'spki',format:'pem'})};
 const admin={id:crypto.randomUUID(),role:'admin',publicKey:adminKeys.publicKey.export({type:'spki',format:'pem'})};
 await fs.mkdir(path.join(root,'media','Films'),{recursive:true});await fs.writeFile(path.join(root,'media','Films','film.mp4'),'0123456789');await fs.writeFile(path.join(root,'media','private.mp4'),'private');
 await fs.writeFile(path.join(root,'configuration.json'),JSON.stringify({id:crypto.randomUUID(),devices:[admin,tv],rooms:[],cameras:[],scenes:[]}));
 const child=spawn(process.execPath,['server/index.mjs'],{env:{...process.env,PORT:String(port),MAISON_DATA:root},stdio:['ignore','pipe','pipe']});
 const request=async(route,method='GET',body,token)=>fetch(`http://127.0.0.1:${port}/api/v1/${route}`,{method,headers:{'Content-Type':'application/json',...(token?{Authorization:`Bearer ${token}`}:{})},body:body?JSON.stringify(body):undefined});
 try{
  for(let i=0;i<100;i++){try{await request('discovery');break}catch{await new Promise(r=>setTimeout(r,50))}}
  async function login(device,pair){const challenge=await(await request('auth/challenge','POST',{deviceId:device.id})).json();return(await(await request('auth/session','POST',{deviceId:device.id,signature:crypto.sign('sha256',Buffer.from(challenge.challenge),pair.privateKey).toString('base64')})).json()).token}
  const token=await login(tv,keys);assert.ok(token);
  for(const route of ['settings','server','backup','files','cameras','home/devices','downloads','devices/authorized','rooms'])assert.equal((await request(route,'GET',undefined,token)).status,403,route);
  assert.equal((await request('users/role','POST',{id:tv.id,role:'admin'},token)).status,403);
  const videos=await(await request('tv/library','GET',undefined,token)).json();assert.equal(videos.length,1);
  const range=await fetch(`http://127.0.0.1:${port}/api/v1/files/content?path=Films/film.mp4`,{headers:{Authorization:`Bearer ${token}`,Range:'bytes=2-5'}});assert.equal(range.status,206);assert.equal(await range.text(),'2345');
  assert.equal((await request('files/content?path=private.mp4','GET',undefined,token)).status,403);
  const owner=await login(admin,adminKeys);assert.equal((await request('users/role','POST',{id:tv.id,role:'admin'},owner)).status,400);
  assert.equal((await request('devices/authorized/'+tv.id,'DELETE',undefined,owner)).status,200);
  assert.equal((await request('tv/library','GET',undefined,token)).status,401);
 }finally{child.kill();await new Promise(r=>child.once('exit',r));await fs.rm(root,{recursive:true,force:true})}
});
