import fs from 'node:fs/promises';
import path from 'node:path';
import crypto from 'node:crypto';
import {Vault} from './vault.mjs';

const hash=value=>crypto.createHash('sha256').update(value).digest('hex');
const normalized=value=>String(value||'').normalize('NFD').replace(/\p{M}/gu,'').toLowerCase().replace(/[^\p{L}\p{N}]+/gu,' ').trim();
export function guessMovie(file){
 const name=path.posix.basename(file).replace(/\.[^.]+$/,'').replace(/[._]/g,' ');
 const yearMatch=/\b(19\d{2}|20\d{2})\b/.exec(name);
 const cut=yearMatch?yearMatch.index:name.search(/\b(2160p|1080p|720p|480p|bluray|blu ray|bdrip|brrip|web[ -]?dl|webrip|dvdrip|hdtv|x26[45]|h26[45]|hevc|multi|vff|vfq|truefrench)\b/i);
 return {title:(cut>=0?name.slice(0,cut):name).replace(/[\[\](){}-]+/g,' ').replace(/\s+/g,' ').trim().slice(0,180),year:yearMatch?Number(yearMatch[1]):null,series:/\bS\d{1,2}E\d{1,3}\b/i.test(name)};
}
export function chooseMovie(guess,items){
 const query=normalized(guess.title);
 const scored=items.filter(m=>!m.adult).map(m=>{
  const titles=[normalized(m.title),normalized(m.original_title)];
  const exact=titles.includes(query);
  const words=new Set(query.split(' '));
  const overlap=Math.max(...titles.map(t=>{const set=new Set(t.split(' '));return [...words].filter(w=>set.has(w)).length/new Set([...words,...set]).size}));
  const year=Number(String(m.release_date||'').slice(0,4));
  return {movie:m,score:(exact?1:overlap*.94)-(guess.year&&year!==guess.year?0.3:0)};
 }).sort((a,b)=>b.score-a.score);
 return scored[0]?.score>=(guess.year ? .9 : .98)&&(!scored[1]||scored[0].score-scored[1].score>.06)?scored[0].movie:null;
}
async function limited(response,max){
 const chunks=[];let size=0;
 for await(const chunk of response.body){size+=chunk.length;if(size>max)throw Error('Réponse distante trop volumineuse.');chunks.push(chunk)}
 return Buffer.concat(chunks);
}
export class MovieCatalog {
 constructor(directory,{fetcher=fetch}={}){this.directory=path.join(directory,'.catalog');this.fetch=fetcher;this.cache=new Map();this.pending=new Set();this.queue=[];this.active=0;this.error='';this.token='';}
 async init(){
  this.ready ||= (async()=>{
   await fs.mkdir(this.directory,{recursive:true,mode:0o700});
   const keyFile=path.join(this.directory,'key');let key;
   try{key=await fs.readFile(keyFile)}catch(e){if(e.code!=='ENOENT')throw e;key=crypto.randomBytes(32);await fs.writeFile(keyFile,key,{mode:0o600,flag:'wx'})}
   this.vault=new Vault(key);
   try{this.token=this.vault.decrypt(JSON.parse(await fs.readFile(path.join(this.directory,'credentials.json'),'utf8'))).token}catch(e){if(e.code!=='ENOENT')throw Error('Configuration des jaquettes illisible.')}
  })();await this.ready;
 }
 async write(name,value){const file=path.join(this.directory,name+'.json');const tmp=file+'.'+crypto.randomUUID()+'.tmp';await fs.writeFile(tmp,JSON.stringify(value),{mode:0o600});await fs.rename(tmp,file)}
 async read(name){try{return JSON.parse(await fs.readFile(path.join(this.directory,name+'.json'),'utf8'))}catch(e){if(e.code==='ENOENT')return null;throw e}}
 async api(route,params={},token=this.token){
  const url=new URL('https://api.themoviedb.org/3/'+route);for(const [k,v]of Object.entries(params))if(v!=null)url.searchParams.set(k,String(v));
  const response=await this.fetch(url,{headers:{Authorization:`Bearer ${token}`,accept:'application/json'},signal:AbortSignal.timeout(8000),redirect:'error'});
  if(!response.ok){await response.body?.cancel();throw Error(response.status===401?'Jeton TMDB invalide.':response.status===429?'TMDB limite temporairement les recherches.':'TMDB est temporairement indisponible.')}
  return JSON.parse((await limited(response,1048576)).toString());
 }
 async configure(token){await this.init();token=String(token||'').trim().replace(/^Bearer\s+/i,'');if(token&&!/^[A-Za-z0-9._-]{20,4096}$/.test(token))throw Error('Jeton TMDB invalide.');if(token)await this.api('configuration',{},token);await this.write('credentials',this.vault.encrypt({token}));this.token=token;this.error='';this.cache.clear();return this.status()}
 status(){return {configured:!!this.token,pending:this.pending.size,error:this.error,provider:'TMDB'}}
 async search(query,year){await this.init();if(!this.token)throw Error('Configurez le jeton TMDB depuis le téléphone.');if(typeof query!=='string'||!query.trim()||query.length>180)throw Error('Titre invalide.');return (await this.api('search/movie',{query:query.trim(),year:year||null,language:'fr-FR',include_adult:false})).results.filter(m=>!m.adult).slice(0,15).map(m=>this.clean(m))}
 clean(m){return {id:m.id,title:String(m.title||'').slice(0,180),originalTitle:String(m.original_title||'').slice(0,180),year:String(m.release_date||'').slice(0,4),overview:String(m.overview||'').slice(0,3000),poster:/^\/[a-zA-Z0-9]+\.jpg$/.test(m.poster_path||'')?m.poster_path:null,source:'TMDB'}}
 async poster(movie){
  if(!movie?.poster)return false;const name='poster-'+hash(movie.poster)+'.jpg';const file=path.join(this.directory,name);
  try{await fs.access(file);return true}catch{}
  const response=await this.fetch('https://image.tmdb.org/t/p/w342'+movie.poster,{signal:AbortSignal.timeout(10000),redirect:'error'});
  if(!response.ok){await response.body?.cancel();throw Error('Affiche indisponible.')}
  const bytes=await limited(response,4*1048576);if(bytes[0]!==0xff||bytes[1]!==0xd8)throw Error('Image TMDB invalide.');const tmp=file+'.'+crypto.randomUUID()+'.tmp';await fs.writeFile(tmp,bytes,{mode:0o600});await fs.rename(tmp,file);return true;
 }
 async image(file){await this.init();const data=await this.entry(file);if(!data?.movie?.poster)throw Error('Affiche indisponible.');return fs.readFile(path.join(this.directory,'poster-'+hash(data.movie.poster)+'.jpg'))}
 async entry(file){const override=await this.read('override-'+hash(file));if(override)return override;const guess=guessMovie(file);const key=hash(JSON.stringify(guess));if(!this.cache.has(key))this.cache.set(key,await this.read('movie-'+key));return this.cache.get(key)}
 async enrich(video){await this.init();const guess=guessMovie(video.path);const entry=await this.entry(video.path);
  const stale=!entry||Date.now()-entry.at>7*86400000;
  if(this.token&&!guess.series&&stale)this.enqueue(video.path);
  let posterReady=false;if(entry?.movie?.poster)try{await fs.access(path.join(this.directory,'poster-'+hash(entry.movie.poster)+'.jpg'));posterReady=true}catch{}
  return {...video,title:entry?.movie?.title||guess.title||video.title,metadata:entry?.movie||null,posterReady,catalogState:this.pending.has(video.path)?'pending':entry?.state||(!this.token?'unconfigured':guess.series?'unsupported':'unmatched')};
 }
 enqueue(file){const guess=guessMovie(file);if(!this.token||guess.series||guess.title.length<2||this.pending.has(file)||this.queue.length>=512)return;this.pending.add(file);this.queue.push(file);this.pump()}
 pump(){while(this.active<2&&this.queue.length){const file=this.queue.shift();this.active++;this.lookup(file).catch(()=>{this.error='Recherche TMDB interrompue. Vérifiez le jeton et la connexion Internet.'}).finally(()=>{this.pending.delete(file);this.active--;this.pump()})}}
 async lookup(file){const guess=guessMovie(file);const key=hash(JSON.stringify(guess));let value;
  try{const raw=await this.api('search/movie',{query:guess.title,year:guess.year,language:'fr-FR',include_adult:false});const match=chooseMovie(guess,raw.results||[]);value={at:Date.now(),state:match?'matched':'unmatched',movie:match?this.clean(match):null};if(value.movie)await this.poster(value.movie);this.error=''}catch{value={at:Date.now()-7*86400000+60000,state:'error',movie:null};this.error='Recherche ou affiche TMDB indisponible. Réessayez ultérieurement.'}
  await this.write('movie-'+key,value);this.cache.set(key,value);
 }
 async identify(file,id){await this.init();if(!Number.isSafeInteger(id)||id<1)throw Error('Film invalide.');const raw=await this.api('movie/'+id,{language:'fr-FR'});if(raw.adult)throw Error('Film non pris en charge.');const movie=this.clean(raw);await this.poster(movie);await this.write('override-'+hash(file),{at:Date.now(),state:'matched',movie});return {saved:true}}
 async clear(file){await this.init();await this.write('override-'+hash(file),{at:Date.now(),state:'unmatched',movie:null});return {saved:true}}
}
