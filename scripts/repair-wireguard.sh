#!/usr/bin/env bash
set -euo pipefail
[[ $EUID == 0 ]] || { echo 'Exécuter sur le serveur avec sudo.' >&2; exit 1; }
# Ubuntu's wg AppArmor profile may deny systemd's protected credential path.
# Grant only this service's private-key file, keeping AppArmor confinement active.
PROFILE=
for CANDIDATE in /etc/apparmor.d/wg /etc/apparmor.d/usr.bin.wg; do
  if [[ -f $CANDIDATE ]] && grep -Eq 'include[[:space:]]+(if[[:space:]]+exists[[:space:]]+)?<local/wg>' "$CANDIDATE"; then
    PROFILE=$CANDIDATE
    break
  fi
done
if [[ -n $PROFILE ]] && command -v apparmor_parser >/dev/null; then
  install -d -m 755 /etc/apparmor.d/local
  LOCAL=/etc/apparmor.d/local/wg
  RULE='/run/credentials/maison-wireguard.service/wireguard-key r,'
  touch "$LOCAL"
  if ! grep -qxF "$RULE" "$LOCAL"; then
    printf '\n# Domora: systemd credential for the WireGuard service only\n%s\n' "$RULE" >> "$LOCAL"
  fi
  apparmor_parser -r "$PROFILE"
  echo 'Accès limité à la clé systemd autorisé dans le profil AppArmor de wg.'
fi
if [[ ${1:-} != --prepare ]]; then
  systemctl restart maison-wireguard
  systemctl restart maison-admin
  systemctl is-active maison-wireguard maison-admin
  ip -brief address show dev maison-wg
  echo 'WireGuard démarré. Reconfigurer l’accès 5G dans le téléphone sur le Wi-Fi.'
fi
