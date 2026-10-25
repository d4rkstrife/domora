import test from 'node:test';
import assert from 'node:assert/strict';
import fs from 'node:fs/promises';
import os from 'node:os';
import path from 'node:path';
import {MovieCatalog,guessMovie,chooseMovie} from '../catalog.mjs';
import {catalogRoutes} from '../catalog-routes.mjs';
const movie={id:42,title:'Le Voyage',original_title:'The Journey',release_date:'2004-05-01',overview:'Un voyage.',poster_path:'/poster42.jpg',backdrop_path:'/landscape42.jpg',runtime:110,genres:[{id:18,name:'Drame'}],adult:false};
const token='private-test-token-not-for-export';
const json=value=>new Response(JSON.stringify(value),{headers:{'Content-Type':'application/json'}});
async function settled(catalog){for(let i=0;i<200&&catalog.pending.size;i++)await new Promise(r=>setTimeout(r,5));assert.equal(catalog.pending.size,0)}

test('Titres nettoyés, années, épisodes et correspondances ambiguës',()=>{
 assert.deepEqual(guessMovie('Films/Le.Voyage.2004.MULTI.1080p.WEB-DL.x265.mkv'),{title:'Le Voyage',year:2004,series:false});
 assert.equal(guessMovie('Films/Le.Voyage.1080p.WEBRip.mkv').title,'Le Voyage');
 assert.equal(guessMovie('Serie.S01E02.1080p.mkv').series,true);
 assert.equal(chooseMovie({title:'The Journey',year:2004},[movie]).id,42);
 assert.equal(chooseMovie({title:'Le Voyage',year:2005},[movie]),null);
 assert.equal(chooseMovie({title:'Le Voyage'},[movie,{...movie,id:43,release_date:'2020-01-01'}]),null);
 assert.equal(chooseMovie({title:'Un autre film'},[movie]),null);
});

test('Jeton chiffré, cache persistant et aucun appel Internet après identification',async()=>{
 const root=await fs.mkdtemp(path.join(os.tmpdir(),'domora-catalog-'));let calls=0;
 const fetcher=async(url,options)=>{calls++;const u=new URL(url);assert.equal(options.redirect,'error');if(u.hostname==='image.tmdb.org')return new Response(Buffer.from([255,216,255,217]));assert.equal(u.hostname,'api.themoviedb.org');assert.equal(options.headers.Authorization,'Bearer '+token);if(u.pathname.endsWith('/configuration'))return json({images:{}});if(u.pathname.endsWith('/movie/42'))return json(movie);return json({results:[movie]})};
 try{
  const catalog=new MovieCatalog(root,{fetcher});await catalog.configure(token);
  const file={path:'Films/Le.Voyage.2004.mkv',title:'Filename'};const initial=await catalog.enrich(file);assert.equal(initial.catalogState,'pending');await settled(catalog);
  const enriched=await catalog.enrich(file);assert.equal(enriched.title,'Le Voyage');assert.equal(enriched.posterReady,true);assert.equal(enriched.metadata.id,42);
  assert.equal(enriched.metadata.backdrop,'/landscape42.jpg');assert.equal(enriched.metadata.runtime,110);assert.deepEqual(enriched.metadata.genres,['Drame']);
  const secret=await fs.readFile(path.join(root,'.catalog','credentials.json'),'utf8');assert.ok(!secret.includes(token));assert.ok(!JSON.stringify(catalog.status()).includes(token));
  const before=calls;const offline=new MovieCatalog(root,{fetcher:()=>{throw Error('Internet coupé')}});assert.equal((await offline.enrich(file)).posterReady,true);assert.equal((await offline.image(file.path)).length,4);assert.equal(calls,before);
  assert.equal((await offline.image(file.path,'backdrop')).length,4);await assert.rejects(offline.image(file.path,'../../private'));
  assert.equal(catalog.clean({...movie,poster_path:'https://127.0.0.1/secret'}).poster,null);
 }finally{await fs.rm(root,{recursive:true,force:true})}
});

test('Échecs distants bornés, correction manuelle et suppression de correspondance',async()=>{
 const root=await fs.mkdtemp(path.join(os.tmpdir(),'domora-correction-'));
 try{
  const catalog=new MovieCatalog(root,{fetcher:async(url)=>{const u=new URL(url);if(u.hostname==='image.tmdb.org')return new Response(Buffer.from([255,216,255,217]));if(u.pathname.endsWith('/configuration'))return json({});if(u.pathname.includes('/movie/42'))return json(movie);return new Response('',{status:503})}});
  await catalog.configure(token);const file={path:'Un.Autre.Nom.mp4',title:'Filename'};await catalog.enrich(file);await settled(catalog);assert.equal((await catalog.enrich(file)).catalogState,'error');assert.ok(!catalog.error.includes(token));
  await catalog.identify(file.path,42);assert.equal((await catalog.enrich(file)).metadata.id,42);
  await catalog.clear(file.path);assert.equal((await catalog.enrich(file)).metadata,null);
  await assert.rejects(catalog.identify(file.path,-1));
 }finally{await fs.rm(root,{recursive:true,force:true})}
});

test('L’affiche respecte les dossiers TV et la configuration reste propriétaire',async()=>{
 const root=await fs.mkdtemp(path.join(os.tmpdir(),'domora-poster-scope-'));let calls=0;let status;
 try{
  await fs.mkdir(path.join(root,'Films'));await fs.writeFile(path.join(root,'Films','ok.mp4'),'video');await fs.writeFile(path.join(root,'private.mp4'),'private');
  const ctx={req:{method:'GET'},res:{writeHead:s=>status=s,end:()=>{}},route:'/api/v1/tv/poster',url:new URL('http://local/api/v1/tv/poster?path=private.mp4'),device:{role:'tv',mediaRoots:['Films']},media:root,catalog:{image:async()=>{calls++;return Buffer.from([255,216])}},json:(_,s)=>{status=s}};
  await catalogRoutes(ctx);assert.equal(status,403);assert.equal(calls,0);
  ctx.url=new URL('http://local/?path=Films/ok.mp4');await catalogRoutes(ctx);assert.equal(status,200);assert.equal(calls,1);
  ctx.route='/api/v1/media/catalog/settings';await catalogRoutes(ctx);assert.equal(status,403);
  ctx.device={role:'user'};await catalogRoutes(ctx);assert.equal(status,403);
 }finally{await fs.rm(root,{recursive:true,force:true})}
});
