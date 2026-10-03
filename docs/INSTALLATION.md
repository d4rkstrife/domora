# Installation et réinstallation

## Installation

Ubuntu Server 24.04 LTS 64 bits, systemd et accès Internet. Raspberry Pi 4 ARM64 ou machine x86_64. SSH n'est nécessaire que pour administrer à distance.

```bash
curl -fsSL https://raw.githubusercontent.com/d4rkstrife/domora/v0.7.0/scripts/bootstrap.sh | sudo bash
```

Le script utilise apt et les distributions officielles Node.js (archive Node contrôlée par SHA256). Il crée un compte système maison, TLS, les dossiers de médias et les services maison, maison-admin, maison-downloads. Il préserve les fichiers de données existants et tente de configurer maison-wireguard avec l'adresse IPv6 publique stable détectée. Sans adresse utilisable, il annonce clairement que la 5G reste non configurée.

Après installation, appairer Android en Wi-Fi. Code temporaire :

```bash
sudo journalctl -u maison -n 20 --no-pager
```

Le pare-feu du réseau doit permettre les ports locaux 8787/8789. Avec UFW actif, fournir le réseau privé exact lors de l'installation, par exemple MAISON_LAN_CIDR=192.168.1.0/24. Le script ne désactive pas le pare-feu.

## Accès 5G

Autoriser UDP 51820 vers l'IPv6 du serveur dans le pare-feu de la box. L'adresse affichée par l'installateur est le point de connexion. Activer Réseau → Configurer l'accès 5G depuis Android sur le Wi-Fi domestique, puis tester hors Wi-Fi. En IPv4, fournir MAISON_WG_ENDPOINT et rediriger UDP 51820 ; le CGNAT empêche cet accès direct. Les règles de la box ne peuvent pas être garanties automatiquement par l'installateur.

Pour définir explicitement le point de connexion, depuis le code téléchargé :

```bash
sudo env MAISON_WG_ENDPOINT='[votre-ipv6-publique]:51820' bash scripts/install-all.sh
```

L'installation ne prouve pas la joignabilité extérieure : tester une caméra ou une vidéo en 5G.

## Avant de formater

Sauvegarder les médias, configuration et secrets séparément, sur un support privé. Données : /var/lib/maison ; WireGuard : /var/lib/maison-wireguard et /etc/maison. Les credentials systemd chiffrés sont liés à l'hôte : leur simple copie vers un nouvel Ubuntu ne constitue pas une restauration portable des secrets. Prévoir un nouvel appairage et une nouvelle configuration 5G si ces secrets ne sont pas restaurés par une procédure adaptée.

La sauvegarde JSON de l'application ne contient pas tous les secrets ni les médias. GitHub conserve le code, pas les données. L'installateur n'effectue aucun formatage et ne restaure pas une sauvegarde automatiquement.
