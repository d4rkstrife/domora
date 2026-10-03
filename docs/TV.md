# Domora TV — première version

L’APK Domora TV est distincte de Ma Maison : Android TV / Google TV sous Android 8 minimum, navigation à la télécommande, bibliothèque vidéo, lecture plein écran et reprise propre à chaque téléviseur. Les APK téléphone et TV sont compilées et archivées par `scripts/build-android.ps1`.

## Première autorisation

1. Mettre à jour le serveur et installer Ma Maison 0.7.0 ou plus récente sur le téléphone déjà appairé au serveur.
2. Installer Domora TV sur le téléviseur. Connecter téléphone et téléviseur au même réseau local pour cette étape ; le serveur peut être ailleurs.
3. Ouvrir Domora TV. Dans Ma Maison, ouvrir **Plus → Téléviseurs**, scanner le QR affiché, choisir les dossiers vidéo et autoriser.
4. Sur la télévision, accepter la demande Android d’accès VPN. Domora TV utilise ensuite son propre tunnel WireGuard intégré. Aucune application VPN supplémentaire n’est nécessaire.

Aucune adresse personnelle n’est intégrée à l’APK. Le téléphone transmet la configuration du serveur et son certificat public à la télévision, via une demande locale temporaire authentifiée. Les clés privées restent sur chaque appareil. Le QR expire après dix minutes ; le port local TCP 8846 doit être accessible entre téléphone et TV (pas de réseau invité isolé). Le scanner peut télécharger son module Google lors de sa première utilisation.

## Accès et limites

La télévision peut uniquement consulter et lire les vidéos des dossiers autorisés, et enregistrer sa progression. Elle ne peut pas gérer les caméras, téléchargements, fichiers, scènes ou réglages du serveur. Le propriétaire retire son accès dans **Plus → Téléviseurs**. La révocation bloque les nouvelles requêtes et retire son pair WireGuard ; un flux HTTP déjà ouvert peut finir avant sa fermeture.

Pour utiliser un autre réseau, le serveur WireGuard doit être joignable depuis Internet. Avec l’installation SFR actuelle, cela nécessite une connectivité IPv6 sur le réseau du téléviseur et la règle UDP 51820 de la box. Une modification de l’adresse publique du serveur nécessite une mise à jour de la configuration ; aucun domaine ou relais tiers n’est utilisé.

La lecture est directe : la compatibilité des codecs dépend du téléviseur. Le Pi ne transcode pas les vidéos dans cette version. La validation sur le téléviseur physique reste nécessaire après compilation et tests des contrôles d’accès serveur.
