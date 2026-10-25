import {library} from './library.mjs';
import {tvFileAllowed} from './televisions.mjs';
export async function catalogRoutes({req,res,route,url,device,media,catalog,json,body}){
 if(req.method==='GET'&&['/api/v1/tv/poster','/api/v1/media/catalog/poster'].includes(route)){
  const file=url.searchParams.get('path')||'';
  try{await tvFileAllowed(media,device.role==='tv'?device:{mediaRoots:['']},file)}catch{json(res,403,{message:'Affiche non autorisée.'});return true}
  try{const bytes=await catalog.image(file);res.writeHead(200,{'Content-Type':'image/jpeg','Content-Length':bytes.length,'Cache-Control':'private, max-age=300','X-Content-Type-Options':'nosniff'});res.end(bytes)}catch{json(res,404,{message:'Affiche indisponible.'})}
  return true;
 }
 if(!route.startsWith('/api/v1/media/catalog/'))return false;
 if(device.role!=='admin'){json(res,403,{message:'Cette action est réservée au propriétaire.'});return true}
 await catalog.init();const ok=value=>{json(res,200,value);return true};
 if(req.method==='GET'&&route.endsWith('/settings'))return ok(catalog.status());
 if(req.method==='POST'&&route.endsWith('/settings')){if(!req.socket.encrypted)throw Error('Une connexion HTTPS est requise.');return ok(await catalog.configure((await body(req)).token))}
 if(req.method==='GET'&&route.endsWith('/search'))return ok(await catalog.search(url.searchParams.get('query'),url.searchParams.get('year')));
 if(req.method==='GET'&&route.endsWith('/library')){const videos=[];for(const video of await library(media))videos.push(await catalog.enrich(video));return ok(videos)}
 if(req.method==='POST'&&route.endsWith('/scan')){for(const video of await library(media))catalog.enqueue(video.path);return ok(catalog.status())}
 if(req.method==='POST'&&route.endsWith('/identify')){const input=await body(req);await tvFileAllowed(media,{mediaRoots:['']},input.path);return ok(input.clear?await catalog.clear(input.path):await catalog.identify(input.path,input.id))}
 json(res,404,{message:'Action de catalogue inconnue.'});return true;
}
