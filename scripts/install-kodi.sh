#!/usr/bin/env bash
set -euo pipefail
PREEXISTED=0
if dpkg-query -W -f='${Status}' samba 2>/dev/null | grep -q 'install ok installed'; then PREEXISTED=1; fi
apt-get install -y samba
id maisonmedia >/dev/null 2>&1 || useradd --system --no-create-home --shell /usr/sbin/nologin maisonmedia
usermod -a -G maison maisonmedia
cat > /etc/maison/samba-share.conf <<'SMB'
[Media]
path = /var/lib/maison/media
browseable = yes
read only = yes
guest ok = no
valid users = maisonmedia
force user = maison
hosts allow = 127. 10. 192.168. 172.16. 172.17. 172.18. 172.19. 172.20. 172.21. 172.22. 172.23. 172.24. 172.25. 172.26. 172.27. 172.28. 172.29. 172.30. 172.31.
hosts deny = ALL
veto files = /.trash/
SMB
if ! grep -q '^include = /etc/maison/samba-share.conf$' /etc/samba/smb.conf; then printf '\ninclude = /etc/maison/samba-share.conf\n' >> /etc/samba/smb.conf; fi
testparm -s >/dev/null
systemctl enable --now smbd
systemctl reload smbd
echo 'Partage Media préparé. Le propriétaire crée les identifiants Kodi depuis Android.'
