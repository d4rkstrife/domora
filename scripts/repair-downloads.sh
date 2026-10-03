#!/usr/bin/env bash
set -euo pipefail
[[ $EUID == 0 ]] || { echo 'Exécuter sur le serveur avec sudo.' >&2; exit 1; }
if [[ ${1:-} != --prepare ]]; then systemctl stop maison-downloads; fi
PROFILE=
LOCAL_NAME=
for CANDIDATE in /etc/apparmor.d/transmission /etc/apparmor.d/transmission-daemon /etc/apparmor.d/usr.bin.transmission-daemon; do
  [[ -f $CANDIDATE ]] || continue
  for NAME in transmission-daemon usr.bin.transmission-daemon; do
    if grep -Eq "include[[:space:]]+(if[[:space:]]+exists[[:space:]]+)?<local/$NAME>" "$CANDIDATE"; then
      PROFILE=$CANDIDATE
      LOCAL_NAME=$NAME
      break 2
    fi
  done
done
if [[ -n $PROFILE ]] && command -v apparmor_parser >/dev/null; then
  install -d -m 755 /etc/apparmor.d/local
  LOCAL=/etc/apparmor.d/local/$LOCAL_NAME
  touch "$LOCAL"
  for RULE in 'owner /var/lib/maison/torrent/ rw,' 'owner /var/lib/maison/torrent/** rwk,' 'owner /var/lib/maison/media/Telechargements/ rw,' 'owner /var/lib/maison/media/Telechargements/** rwk,'; do
    if ! grep -qxF "$RULE" "$LOCAL"; then printf '\n%s\n' "$RULE" >> "$LOCAL"; fi
  done
  apparmor_parser -r "$PROFILE"
  echo 'AppArmor : accès autorisé aux dossiers privés de Transmission.'
fi
chown -R maison:maison /var/lib/maison/torrent
chmod 700 /var/lib/maison/torrent
if [[ ${1:-} != --prepare ]]; then
  systemctl start maison-downloads
  sleep 2
  systemctl is-active maison-downloads
  /opt/maison/runtime/bin/node --input-type=module -e 'import {Downloads} from "/opt/maison/server/downloads.mjs"; await new Downloads().call("session-get"); console.log("Connexion au moteur de téléchargements : OK");'
fi
