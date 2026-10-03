#!/usr/bin/env bash
set -euo pipefail
NODE=$1
cat > /etc/systemd/system/maison-admin.service <<UNIT
[Unit]
Description=Ma Maison - actions administratives limitées
After=network.target
[Service]
User=root
Group=root
RuntimeDirectory=maison-admin
RuntimeDirectoryMode=0755
ExecStart=$NODE /opt/maison/server/admin.mjs
Restart=on-failure
NoNewPrivileges=true
PrivateTmp=true
ProtectHome=true
UMask=0077
[Install]
WantedBy=multi-user.target
UNIT
systemctl daemon-reload
systemctl enable --now maison-admin
systemctl restart maison-admin
