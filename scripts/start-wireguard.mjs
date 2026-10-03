import {execFileSync} from 'node:child_process';
import {WireGuardPeers} from '../server/wireguard.mjs';
const run=(command,args)=>execFileSync(command,args,{stdio:'inherit'});
const credential=process.env.CREDENTIALS_DIRECTORY;
if(!credential)throw Error('Credential WireGuard manquant');
try{
 run('ip',['link','add','maison-wg','type','wireguard']);
 run('ip',['address','add','10.203.77.1/24','dev','maison-wg']);
 run('wg',['set','maison-wg','private-key',`${credential}/wireguard-key`,'listen-port','51820']);
 await new WireGuardPeers().restore();
 run('ip',['link','set','dev','maison-wg','mtu','1280','up']);
}catch(error){try{run('ip',['link','delete','maison-wg']);}catch{}throw error;}
