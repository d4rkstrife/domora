function downloadIssue(t) {
  if (!t.error) return null;
  if (t.error === 1 || t.error === 2) {
    if (/\b522\b/.test(t.errorString || '')) return 'Le tracker ne répond pas (HTTP 522). Le moteur réessaie automatiquement ; changer les DNS ne corrige pas cette panne.';
    return 'Le tracker signale un problème. Le téléchargement peut continuer si des pairs restent disponibles.';
  }
  const reason = t.errorString || '';
  if (/permission denied|access denied/i.test(reason)) return 'Le moteur ne peut pas écrire dans le dossier de téléchargement.';
  if (/no space left/i.test(reason)) return 'Le disque du serveur est plein.';
  if (/read-only file system/i.test(reason)) return 'Le dossier utilisé par le moteur est en lecture seule.';
  return 'Le moteur a rencontré une erreur locale. Réessayez ou consultez les journaux du serveur.';
}
export class Downloads {
  constructor({ url = process.env.MAISON_TORRENT_URL || 'http://127.0.0.1:19091/transmission/rpc', fetcher = fetch, credentials } = {}) { this.url = url; this.fetch = fetcher; this.session = ''; this.credentials = credentials; }
  async call(method, args = {}) {
    for (let retry = 0; retry < 2; retry++) {
      const headers = { 'Content-Type': 'application/json', 'X-Transmission-Session-Id': this.session };
      if (this.credentials) headers.Authorization = 'Basic ' + Buffer.from(this.credentials).toString('base64');
      let response;
      try { response = await this.fetch(this.url, { method: 'POST', headers, body: JSON.stringify({ method, arguments: args }), signal: AbortSignal.timeout(8000), redirect: 'error' }); }
      catch { throw new Error('Le service de téléchargements est indisponible.'); }
      if (response.status === 409) { this.session = response.headers.get('X-Transmission-Session-Id') || ''; await response.body?.cancel(); continue; }
      if (!response.ok) throw new Error('Impossible de communiquer avec le service de téléchargements.');
      const result = await response.json(); if (result.result !== 'success') throw new Error('Le téléchargement a été refusé par le moteur torrent.');
      return result.arguments;
    }
    throw new Error('Le service de téléchargements refuse la connexion.');
  }
  async list() {
    const result = await this.call('torrent-get', { fields: ['id','hashString','name','totalSize','percentDone','rateDownload','rateUpload','downloadedEver','uploadedEver','eta','status','error','errorString'] });
    const states = ['pause','en_attente','verification','en_attente','telechargement','en_attente','partage'];
    return result.torrents.map(t => ({ id: t.hashString, name: t.name, size: t.totalSize, progress: t.percentDone, downloadSpeed: t.rateDownload, uploadSpeed: t.rateUpload, downloaded: t.downloadedEver, uploaded: t.uploadedEver, eta: t.eta < 0 ? null : t.eta, state: t.error === 3 ? 'erreur' : states[t.status] || 'inconnu', issueType: t.error === 3 ? 'local' : t.error ? 'tracker' : null, error: downloadIssue(t) }));
  }
  async add(input) {
    const args = { paused: false };
    if (input.magnet && /^magnet:\?/.test(input.magnet) && input.magnet.length <= 16384) args.filename = input.magnet;
    else if (input.torrent && /^[A-Za-z0-9+/]*={0,2}$/.test(input.torrent) && input.torrent.length <= 2800000) args.metainfo = input.torrent;
    else throw new Error('Choisissez un lien magnet ou un fichier torrent valide.');
    const result = await this.call('torrent-add', args); const t = result['torrent-added'] || result['torrent-duplicate']; return { id: t.hashString, name: t.name };
  }
  async action(id, input) {
    if (!/^[a-f0-9]{40,64}$/i.test(id)) throw new Error('Téléchargement invalide.');
    const actions = { pause: 'torrent-stop', resume: 'torrent-start', remove: 'torrent-remove' };
    if (!actions[input.action]) throw new Error('Action invalide.');
    if (input.deleteData && input.confirm !== 'SUPPRIMER') throw new Error('Confirmez la suppression des données.');
    return this.call(actions[input.action], { ids: [id], ...(input.action === 'remove' ? { 'delete-local-data': input.deleteData === true } : {}) });
  }
}
