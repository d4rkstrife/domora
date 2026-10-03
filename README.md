# Domora

Serveur personnel pour maison connectée et médias, avec application Android native. Le serveur gère les webcams USB, fichiers, lecture vidéo, téléchargements Transmission, appareils Shelly et scènes. WireGuard intégré à Android permet un accès privé depuis la 5G. La connexion Google Home reste dans le téléphone.

## Installation Ubuntu

Sur Ubuntu Server 24.04 LTS 64 bits avec systemd, connecté à Internet, lancer dans le terminal du serveur :

```bash
curl -fsSL https://raw.githubusercontent.com/d4rkstrife/domora/v0.7.1/scripts/bootstrap.sh | sudo bash
```

La commande télécharge la version v0.7.1 et installe ses dépendances, les services, les médias, Transmission et WireGuard lorsqu'une IPv6 publique stable est disponible. Le code est téléchargé depuis ce dépôt public via HTTPS. Pour examiner le script avant exécution, télécharger bootstrap.sh puis le lire avant de lancer sudo bash.

L'ouverture UDP 51820 sur la box et l'appairage Android restent nécessaires. Ce dépôt ne fournit aucun domaine ni relais tiers. Voir [installation et réinstallation](docs/INSTALLATION.md).

## Android

L'application porte actuellement le nom Ma Maison, package fr.mamaison.app, Android 10 minimum. Elle propose caméras, fichiers, vidéos en plein écran, téléchargements, pièces, scènes et connexion Google Home. Les commandes Google dépendent des appareils et autorisations exposés par Google. Les alarmes et réglages d'écran du Nest Hub ne sont pas implémentés.

Domora TV fournit une application distincte pour Android TV / Google TV : vidéos autorisées par le téléphone, télécommande, plein écran et tunnel privé intégré. Voir [installation et limites de Domora TV](docs/TV.md).

Les APK historiques restent archivées localement et ne sont pas ajoutées au dépôt. Instructions de compilation : [Android](docs/ANDROID.md).

## Développement

Node.js 22 ou plus récent, sans dépendance npm pour le serveur :

```bash
node --test server/test/*.test.mjs
node server/index.mjs
```

Serveur de développement : http://127.0.0.1:8787. Ne pas exposer cette interface HTTP directement à Internet. Les accès de production utilisent HTTPS et le tunnel privé.

## Données privées

Le dépôt contient le code et des données synthétiques de test. Les données d'exécution, médias, clés, certificats privés, journaux de diagnostic, documents du propriétaire et APK sont exclus. Publier ce code ne sauvegarde pas les données du serveur.
