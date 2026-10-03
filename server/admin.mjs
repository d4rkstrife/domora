import http from 'node:http';
import fs from 'node:fs/promises';
import crypto from 'node:crypto';
import {spawn,execFile} from 'node:child_process';
import {promisify} from 'node:util';
import {WireGuardPeers} from './wireguard.mjs';
const wireguard=new WireGuardPeers();
const exec=promisify(execFile);
const socket='/run/maison-admin/control.sock';
try{await fs.unlink(socket);}catch(e){if(e.code!=='ENOENT')throw e;}
const server=http.createServer(async(req,res)=>{
 const reply=(status,value)=>{res.writeHead(status,{'Content-Type':'application/json'});res.end(JSON.stringify(value));};
 try{
  if(req.method!=='POST')return reply(405,{message:'Méthode interdite.'});let raw='';for await(const chunk of req){raw+=chunk;if(raw.length>8192)throw Error('Requête invalide.');}const input=JSON.parse(raw||'{}');
  if(input.action==='wireguard-status')return reply(200,await wireguard.status());
  if(input.action==='wireguard-enroll')return reply(200,await wireguard.enroll(input.deviceId,input.publicKey));
  if(input.action==='wireguard-revoke')return reply(200,await wireguard.revoke(input.deviceId));
  if(input.action==='restart-services'){reply(200,{scheduled:true});setTimeout(()=>exec('systemctl',['restart','maison']).catch(()=>{}),1000);return;}
  if(input.action==='reboot'){if(input.confirm!=='REDEMARRER')throw Error('Confirmation requise.');reply(200,{scheduled:true});setTimeout(()=>exec('systemctl',['reboot']).catch(()=>{}),2000);return;}
  if(input.action==='smb-credentials'){
   const password=crypto.randomBytes(18).toString('base64url');
   await new Promise((resolve,reject)=>{const process=spawn('smbpasswd',['-s','-a','maisonmedia'],{stdio:['pipe','ignore','pipe']});process.stdin.on('error',reject);process.stdin.end(`${password}\n${password}\n`);process.stderr.on('data',()=>{});process.on('error',reject);process.on('exit',code=>code===0?resolve():reject(Error('Configuration Kodi indisponible.')));});
   return reply(200,{username:'maisonmedia',password,share:'Media'});
  }
  reply(400,{message:'Action non prise en charge.'});
 }catch(e){reply(400,{message:e.message||'Action impossible.'});}
});
server.listen(socket,async()=>{await fs.chmod(socket,0o660);const {stdout}=await exec('getent',['group','maison']);const gid=Number(stdout.split(':')[2]);if(!Number.isInteger(gid))throw Error('Groupe invalide');await fs.chown(socket,0,gid);});
