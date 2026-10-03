import net from 'node:net';
import crypto from 'node:crypto';
import { execFile } from 'node:child_process';
import { promisify } from 'node:util';
const exec = promisify(execFile);
export function localAddress(address) {
  if (net.isIP(address) !== 4) throw new Error('Une adresse locale IPv4 est requise.');
  const [a,b] = address.split('.').map(Number);
  if (!(a === 10 || a === 192 && b === 168 || a === 172 && b >= 16 && b <= 31)) throw new Error('Cet appareil doit se trouver sur votre réseau privé.');
  return address;
}
export async function shellyRpc(address, method, params = {}, fetcher = fetch) {
  localAddress(address);
  let response;
  try { response = await fetcher(`http://${address}/rpc/${method}`, { method: 'POST', headers: { 'Content-Type':'application/json' }, body: JSON.stringify(params), redirect: 'error', signal: AbortSignal.timeout(4000) }); } catch { throw new Error('Cet appareil ne répond pas sur le réseau local.'); }
  if (response.status === 401) throw new Error('Cet appareil nécessite une authentification externe.');
  if (!response.ok) throw new Error('Cet appareil ne prend pas en charge cette commande.');
  return response.json();
}
export async function inspectShelly(address) {
  const info = await shellyRpc(address, 'Shelly.GetDeviceInfo');
  const status = await shellyRpc(address, 'Shelly.GetStatus');
  const components = Object.keys(status).filter(k => /^(switch|light|rgb|rgbw):\d+$/.test(k));
  return components.map(component => ({ id: crypto.randomUUID(), sourceId: `${info.id}/${component}`, address, component, name: info.name || info.model || info.id, type: component.split(':')[0], technology:'shelly', roomId:null, capabilities: { power:true, brightness: /^(light|rgb)/.test(component), color: /^rgb/.test(component) }, state: status[component] }));
}
export async function discoverHome() {
  let output = '';
  try { output = (await exec('avahi-browse', ['-rtp', '_shelly._tcp'], { timeout: 6000, maxBuffer: 131072 })).stdout; }
  catch (e) { output = e.stdout || ''; }
  const addresses = [...new Set(output.split('\n').filter(line => line.startsWith('=;')).map(line => line.split(';')[7]).filter(Boolean))];
  const devices = [];
  const results=await Promise.allSettled(addresses.slice(0,8).map(address=>inspectShelly(address)));
  for(const result of results)if(result.status==='fulfilled')devices.push(...result.value);
  return { devices, message: devices.length ? 'Appareils compatibles trouvés.' : 'Aucun appareil Shelly compatible trouvé. Les appareils Matter et Google nécessitent une intégration distincte.' };
}
export async function controlDevice(device, values) {
  const [type,idString] = device.component.split(':'); const id = Number(idString);
  const params = { id };
  if (typeof values.on === 'boolean') params.on = values.on;
  if (values.brightness !== undefined) { if (!device.capabilities.brightness || !Number.isFinite(values.brightness) || values.brightness < 0 || values.brightness > 100) throw new Error('Luminosité invalide ou non prise en charge.'); params.brightness = values.brightness; }
  if (values.rgb !== undefined) { if (!device.capabilities.color || !Array.isArray(values.rgb) || values.rgb.length !== 3 || values.rgb.some(n => !Number.isInteger(n) || n < 0 || n > 255)) throw new Error('Couleur invalide ou non prise en charge.'); params.rgb = values.rgb; }
  const methods = { switch:'Switch.Set', light:'Light.Set', rgb:'RGB.Set', rgbw:'RGBW.Set' };
  if (!methods[type] || Object.keys(params).length === 1) throw new Error('Commande non prise en charge.');
  return shellyRpc(device.address, methods[type], params);
}
