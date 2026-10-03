#!/usr/bin/env bash
set -euo pipefail
[[ $EUID == 0 ]] || { echo 'Exécuter sur le Pi avec sudo.'; exit 1; }
SOURCE=$(cd "$(dirname "$0")/.." && pwd)
ENDPOINT=${MAISON_WG_ENDPOINT:?Définir MAISON_WG_ENDPOINT avec le nom DNS ou IPv4 publique et :51820}
python3 - "$ENDPOINT" <<'PY'
import re,sys,ipaddress
endpoint=sys.argv[1]
if endpoint.startswith('['):
 assert endpoint.endswith(']:51820'), 'Endpoint invalide'
 assert ipaddress.IPv6Address(endpoint[1:-7]).is_global, 'IPv6 publique requise'
else:
 assert re.fullmatch(r'[a-zA-Z0-9][a-zA-Z0-9.-]*:51820',endpoint), 'Endpoint invalide'
PY
apt-get update
apt-get install -y wireguard-tools
NODE=/opt/maison/runtime/bin/node
[[ -x $NODE ]] || NODE=$(command -v node)
install -d -m 700 /var/lib/maison-wireguard
install -d -m 755 /opt/maison/scripts
BACKUP=/var/backups/maison/wireguard-$(date -u +%Y%m%dT%H%M%SZ)
install -d -m 700 "$BACKUP"
for FILE in admin.mjs modules.mjs index.mjs; do
 cp -p "/opt/maison/server/$FILE" "$BACKUP/$FILE"
 "$NODE" --check "$SOURCE/server/$FILE"
done
install -m 644 "$SOURCE/scripts/start-wireguard.mjs" /opt/maison/scripts/start-wireguard.mjs
install -m 644 "$SOURCE/server/wireguard.mjs" /opt/maison/server/wireguard.mjs
for FILE in admin.mjs modules.mjs index.mjs; do
 install -m 644 "$SOURCE/server/$FILE" "/opt/maison/server/$FILE"
done
if [[ ! -f /etc/maison/wireguard-key.cred ]]; then
 KEYFILE=$(mktemp)
 trap 'rm -f "$KEYFILE"' EXIT
 wg genkey > "$KEYFILE"
 systemd-creds encrypt --with-key=host --name=wireguard-key "$KEYFILE" /etc/maison/wireguard-key.cred
 chmod 600 /etc/maison/wireguard-key.cred
 rm -f "$KEYFILE"
 trap - EXIT
fi
python3 - "$ENDPOINT" <<'PY'
import json,sys,os
p='/var/lib/maison-wireguard/server.json'
with open(p+'.tmp','w') as f: json.dump({'endpoint':sys.argv[1]},f)
os.chmod(p+'.tmp',0o600)
os.replace(p+'.tmp',p)
PY
cat > /etc/systemd/system/maison-wireguard.service <<UNIT
[Unit]
Description=Ma Maison - accès WireGuard des téléphones
After=network-online.target
Wants=network-online.target
[Service]
Type=oneshot
RemainAfterExit=yes
LoadCredentialEncrypted=wireguard-key:/etc/maison/wireguard-key.cred
ExecStart=$NODE /opt/maison/scripts/start-wireguard.mjs
ExecStop=/usr/sbin/ip link delete maison-wg
UMask=0077
[Install]
WantedBy=multi-user.target
UNIT
systemctl daemon-reload
systemctl enable maison-wireguard
systemctl restart maison-wireguard
systemctl restart maison-admin maison
if command -v ufw >/dev/null && ufw status | grep -q '^Status: active'; then
 if [[ $ENDPOINT == \[* ]]; then
  WG_IPV6=${ENDPOINT#\[}; WG_IPV6=${WG_IPV6%\]:51820}
  ufw allow to "$WG_IPV6" port 51820 proto udp
 else
  ufw allow 51820/udp
 fi
 ufw allow in on maison-wg from 10.203.77.0/24 to 10.203.77.1 port 8789 proto tcp
fi
if [[ $ENDPOINT == \[* ]]; then
 echo 'WireGuard IPv6 installé. Vérifier le filtrage UDP 51820 vers le Pi et tester en 5G.'
else
 echo 'WireGuard installé. Rediriger UDP 51820 de la box vers le Pi.'
fi
