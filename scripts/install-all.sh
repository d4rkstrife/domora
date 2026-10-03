#!/usr/bin/env bash
set -euo pipefail
[[ $EUID == 0 ]] || { echo 'Exécuter avec sudo.' >&2; exit 1; }
SOURCE=$(cd "$(dirname "$0")/.." && pwd)
export MAISON_MODULES=${MAISON_MODULES:-core,cameras,downloads,media,home}
bash "$SOURCE/scripts/install.sh"
if [[ ${DOMORA_WIREGUARD:-1} == 1 ]]; then
  ENDPOINT=${MAISON_WG_ENDPOINT:-}
  if [[ -z $ENDPOINT && -f /var/lib/maison-wireguard/server.json ]]; then
    ENDPOINT=$(python3 -c 'import json; print(json.load(open("/var/lib/maison-wireguard/server.json"))["endpoint"])')
  fi
  if [[ -z $ENDPOINT ]]; then
    # Prefer a stable global address on the default IPv6 interface, not privacy addresses.
    IPV6=$(ip -j -6 addr show | python3 -c '
import json,sys,ipaddress
for interface in json.load(sys.stdin):
 for address in interface.get("addr_info",[]):
  flags=address.get("flags",[])
  value=address.get("local","")
  if address.get("scope")=="global" and ipaddress.IPv6Address(value).is_global and not any(address.get(f,False) or f in flags for f in ("temporary","tentative","deprecated")):
   print(value); sys.exit(0)
')
    if [[ -n $IPV6 ]]; then ENDPOINT="[$IPV6]:51820"; fi
  fi
  if [[ -n $ENDPOINT ]]; then
    export MAISON_WG_ENDPOINT=$ENDPOINT
    bash "$SOURCE/scripts/install-wireguard.sh"
    echo "Accès 5G préparé : $ENDPOINT"
    echo 'Sur la box, autoriser UDP 51820 vers ce serveur. Dans Android : Réseau → Configurer l’accès 5G.'
  else
    echo 'Serveur installé. Accès 5G non configuré : aucune IPv6 publique stable détectée.'
    echo 'Relancer avec sudo env MAISON_WG_ENDPOINT=nom-public:51820 bash scripts/install-all.sh après configuration de la box.'
  fi
fi
echo 'Installation Domora terminée. Appairer le téléphone sur le Wi-Fi domestique.'
