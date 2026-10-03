#!/usr/bin/env bash
set -euo pipefail
if [[ $(id -u) != 0 ]]; then echo 'Exécutez cet installateur avec sudo.' >&2; exit 1; fi
if ! command -v apt-get >/dev/null; then echo 'Debian, Ubuntu ou Raspberry Pi OS requis.' >&2; exit 1; fi
SOURCE=$(cd "$(dirname "$0")/.." && pwd)
MAISON_MODULES=${MAISON_MODULES:-core,cameras,downloads,media,home}
export MAISON_MODULES
TRANSMISSION_PREEXISTED=0
if dpkg-query -W -f='${Status}' transmission-daemon 2>/dev/null | grep -q 'install ok installed'; then TRANSMISSION_PREEXISTED=1; fi
export MAISON_TRANSMISSION_PREEXISTED=$TRANSMISSION_PREEXISTED
apt-get update
apt-get install -y curl xz-utils avahi-daemon avahi-utils openssl ca-certificates ffmpeg v4l-utils python3
MAJOR=0
if command -v node >/dev/null; then MAJOR=$(node -p 'Number(process.versions.node.split(".")[0])'); fi
NODE=$(command -v node || true)
if [[ -x /opt/maison/runtime/bin/node ]]; then NODE=/opt/maison/runtime/bin/node; MAJOR=$($NODE -p 'Number(process.versions.node.split(".")[0])'); fi
if (( MAJOR < 22 )); then
  case $(uname -m) in aarch64) ARCH=arm64;; armv7l) ARCH=armv7l;; x86_64) ARCH=x64;; *) echo 'Architecture non prise en charge.' >&2; exit 1;; esac
  TEMP=$(mktemp -d)
  trap 'rm -rf -- "$TEMP"' EXIT
  curl --fail --silent --show-error https://nodejs.org/dist/latest-v22.x/SHASUMS256.txt -o "$TEMP/SHASUMS256.txt"
  PACKAGE=$(awk -v suffix="linux-$ARCH.tar.xz" '$2 ~ suffix"$" {print $2}' "$TEMP/SHASUMS256.txt")
  [[ -n $PACKAGE && $PACKAGE != *$'\n'* ]] || { echo 'Distribution Node introuvable.' >&2; exit 1; }
  curl --fail --silent --show-error "https://nodejs.org/dist/latest-v22.x/$PACKAGE" -o "$TEMP/$PACKAGE"
  (cd "$TEMP" && awk -v file="$PACKAGE" '$2 == file' SHASUMS256.txt | sha256sum -c -)
  install -d /opt/maison/runtime
  tar -xJf "$TEMP/$PACKAGE" -C /opt/maison/runtime --strip-components=1
  NODE=/opt/maison/runtime/bin/node
fi
id maison >/dev/null 2>&1 || useradd --system --home-dir /var/lib/maison --create-home --shell /usr/sbin/nologin maison
usermod -a -G video maison
install -d -o maison -g maison -m 700 /var/lib/maison
install -d -o maison -g maison -m 750 /var/lib/maison/media
for folder in Films Series Documents Photos; do install -d -o maison -g maison -m 750 "/var/lib/maison/media/$folder"; done
install -d -m 755 /etc/maison
if [[ ! -f /etc/maison/server.crt || ! -f /etc/maison/tls-key.cred ]]; then
  TLS_TEMP=$(mktemp -d)
  (
    trap 'rm -f -- "$TLS_TEMP/server.key" "$TLS_TEMP/server.crt" "$TLS_TEMP/tls-key.cred"; rmdir -- "$TLS_TEMP"' EXIT
    openssl req -x509 -newkey rsa:3072 -sha256 -days 1825 -nodes -subj '/CN=Ma Maison' -keyout "$TLS_TEMP/server.key" -out "$TLS_TEMP/server.crt"
    systemd-creds encrypt --with-key=host --name=tls-key "$TLS_TEMP/server.key" "$TLS_TEMP/tls-key.cred"
    install -m 600 "$TLS_TEMP/tls-key.cred" /etc/maison/tls-key.cred
    install -m 644 "$TLS_TEMP/server.crt" /etc/maison/server.crt
  )
fi
install -d -m 755 /opt/maison/server /opt/maison/web
cp "$SOURCE"/server/*.mjs /opt/maison/server/
cp "$SOURCE"/web/* /opt/maison/web/
cat > /etc/systemd/system/maison.service <<'UNIT'
[Unit]
Description=Ma Maison - agent personnel
After=network.target
[Service]
User=maison
Group=maison
WorkingDirectory=/opt/maison
Environment=MAISON_DATA=/var/lib/maison
Environment=MAISON_MEDIA=/var/lib/maison/media
Environment=HOST=0.0.0.0
Environment=PORT=8787
ExecStart=/usr/bin/node /opt/maison/server/index.mjs
Restart=on-failure
RestartSec=5
NoNewPrivileges=true
PrivateTmp=true
ProtectSystem=strict
ProtectHome=true
ReadWritePaths=/var/lib/maison
UMask=0077
LoadCredentialEncrypted=tls-key:/etc/maison/tls-key.cred
Environment=MAISON_TLS_CERT=/etc/maison/server.crt
Environment=MAISON_TLS_PORT=8789
Environment=MAISON_REMOTE_PORT=8788
EnvironmentFile=-/etc/maison/agent.env
[Install]
WantedBy=multi-user.target
UNIT
cat > /etc/avahi/services/maison.service <<'AVAHI'
<?xml version="1.0" standalone="no"?>
<!DOCTYPE service-group SYSTEM "avahi-service.dtd">
<service-group><name replace-wildcards="yes">Ma Maison sur %h</name><service><type>_mamaison._tcp</type><port>8787</port></service></service-group>
AVAHI
systemctl daemon-reload
sed -i "s|ExecStart=/usr/bin/node |ExecStart=$NODE |" /etc/systemd/system/maison.service
if [[ ! -f /etc/maison/agent.env ]]; then
  REQUIRE_TLS=1
  if [[ -f /var/lib/maison/configuration.json ]]; then REQUIRE_TLS=0; fi
  printf 'MAISON_REQUIRE_TLS=%s\n' "$REQUIRE_TLS" > /etc/maison/agent.env
fi
if [[ -n ${MAISON_TORRENT_PORT:-} ]]; then printf 'MAISON_TORRENT_URL=http://127.0.0.1:%s/transmission/rpc\n' "$MAISON_TORRENT_PORT" >> /etc/maison/agent.env; fi
if [[ ",$MAISON_MODULES," == *,downloads,* ]]; then bash "$SOURCE/scripts/install-downloads.sh"; fi
if [[ ",$MAISON_MODULES," == *,kodi,* ]]; then bash "$SOURCE/scripts/install-kodi.sh"; fi
bash "$SOURCE/scripts/install-admin.sh" "$NODE"
if [[ ${MAISON_TEST:-0} == 1 ]]; then
  sed -i 's/Environment=HOST=0.0.0.0/Environment=HOST=127.0.0.1/;s/Environment=PORT=8787/Environment=PORT=18887/;s/Environment=MAISON_TLS_PORT=8789/Environment=MAISON_TLS_PORT=18889/;s/Environment=MAISON_REMOTE_PORT=8788/Environment=MAISON_REMOTE_PORT=18888/' /etc/systemd/system/maison.service
fi
systemctl daemon-reload
systemctl enable --now avahi-daemon maison
systemctl restart maison
if [[ ${MAISON_TEST:-0} != 1 && -n ${MAISON_LAN_CIDR:-} ]] && command -v ufw >/dev/null && ufw status | grep -q '^Status: active'; then
  python3 -c 'import ipaddress,os; n=ipaddress.ip_network(os.environ["MAISON_LAN_CIDR"],strict=True); assert n.version==4 and n.is_private and not n.is_loopback'
  ufw allow from "$MAISON_LAN_CIDR" to any port 8787 proto tcp
  ufw allow from "$MAISON_LAN_CIDR" to any port 8789 proto tcp
fi
if [[ ${MAISON_TEST:-0} == 1 ]]; then systemctl disable --now avahi-daemon avahi-daemon.socket || true; fi
echo 'Ma Maison installé. Le premier téléphone s’appaire sur le Wi-Fi domestique.'
echo 'Empreinte du certificat serveur :'
openssl x509 -in /etc/maison/server.crt -noout -fingerprint -sha256
echo 'Pour afficher le code temporaire : sudo journalctl -u maison -n 20 --no-pager'
