#!/usr/bin/env bash
set -euo pipefail
[[ $EUID == 0 ]] || { echo 'Exécuter avec sudo.' >&2; exit 1; }
command -v apt-get >/dev/null || { echo 'Ubuntu ou Debian avec systemd requis.' >&2; exit 1; }
command -v systemctl >/dev/null || { echo 'systemd requis.' >&2; exit 1; }
REF=${DOMORA_REF:-v0.7.0}
[[ $REF =~ ^[a-zA-Z0-9._-]+$ ]] || { echo 'Version Domora invalide.' >&2; exit 1; }
apt-get update
apt-get install -y curl ca-certificates iproute2
TEMP=$(mktemp -d)
trap 'rm -rf -- "$TEMP"' EXIT
curl --fail --silent --show-error --location --proto '=https' \
  "https://codeload.github.com/d4rkstrife/domora/tar.gz/$REF" -o "$TEMP/domora.tar.gz"
mkdir "$TEMP/source"
tar -xzf "$TEMP/domora.tar.gz" --strip-components=1 -C "$TEMP/source"
[[ -f $TEMP/source/scripts/install-all.sh ]] || { echo 'Archive Domora incomplète.' >&2; exit 1; }
bash "$TEMP/source/scripts/install-all.sh"
