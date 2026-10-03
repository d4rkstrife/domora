import fs from 'node:fs/promises';
import crypto from 'node:crypto';
export class Vault {
  constructor(key) { if (key.length !== 32) throw new Error('Clé de coffre invalide.'); this.key = key; }
  encrypt(value) { const iv = crypto.randomBytes(12); const cipher = crypto.createCipheriv('aes-256-gcm', this.key, iv); const data = Buffer.concat([cipher.update(JSON.stringify(value)), cipher.final()]); return { iv: iv.toString('base64'), tag: cipher.getAuthTag().toString('base64'), data: data.toString('base64') }; }
  decrypt(value) { const cipher = crypto.createDecipheriv('aes-256-gcm', this.key, Buffer.from(value.iv,'base64')); cipher.setAuthTag(Buffer.from(value.tag,'base64')); return JSON.parse(Buffer.concat([cipher.update(Buffer.from(value.data,'base64')), cipher.final()]).toString()); }
}
export async function openVault(keyFile) { return new Vault(await fs.readFile(keyFile)); }
