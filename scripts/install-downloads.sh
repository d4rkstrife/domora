#!/usr/bin/env bash
set -euo pipefail
if [[ ${MAISON_TRANSMISSION_PREEXISTED:-0} == 0 ]]; then
  systemctl mask --runtime transmission-daemon.service
  trap 'systemctl unmask --runtime transmission-daemon.service' EXIT
fi
apt-get install -y transmission-daemon
if [[ ${MAISON_TRANSMISSION_PREEXISTED:-0} == 0 ]]; then systemctl unmask --runtime transmission-daemon.service; trap - EXIT; fi
if systemctl is-active --quiet maison-downloads; then systemctl stop maison-downloads; fi
install -d -o maison -g maison -m 700 /var/lib/maison/torrent
install -d -o maison -g maison -m 750 /var/lib/maison/media/Telechargements
python3 - <<'PY'
import json, os
file='/var/lib/maison/torrent/settings.json'
try:
    with open(file) as f: settings=json.load(f)
except FileNotFoundError: settings={}
settings.update({'download-dir':'/var/lib/maison/media/Telechargements','rpc-enabled':True,'rpc-bind-address':'127.0.0.1','rpc-port':int(os.environ.get('MAISON_TORRENT_PORT','19091')),'rpc-whitelist':'127.0.0.1','rpc-whitelist-enabled':True,'rpc-authentication-required':False,'rpc-host-whitelist-enabled':True,'rpc-host-whitelist':'localhost','peer-port':51419,'port-forwarding-enabled':False,'umask':18})
with open(file,'w') as f: json.dump(settings,f,indent=2)
PY
chown -R maison:maison /var/lib/maison/torrent
cat > /etc/systemd/system/maison-downloads.service <<'UNIT'
[Unit]
Description=Ma Maison - moteur de téléchargements privé
After=network.target
[Service]
User=maison
Group=maison
ExecStart=/usr/bin/transmission-daemon --foreground --config-dir /var/lib/maison/torrent
Restart=on-failure
NoNewPrivileges=true
PrivateTmp=true
ProtectSystem=strict
ProtectHome=true
ReadWritePaths=/var/lib/maison/torrent /var/lib/maison/media
UMask=0027
[Install]
WantedBy=multi-user.target
UNIT
# Ne modifie pas une installation Transmission existante. Sur une installation
# vierge, le service fourni par le paquet n'a aucune bibliothèque et reste inutilisé.
if [[ ! -e /var/lib/maison/transmission-package-existing ]]; then
  if [[ ${MAISON_TRANSMISSION_PREEXISTED:-0} == 0 ]]; then systemctl disable --now transmission-daemon || true; fi
fi
systemctl daemon-reload
bash "$(dirname "$0")/repair-downloads.sh" --prepare
systemctl enable --now maison-downloads
systemctl restart maison-downloads
