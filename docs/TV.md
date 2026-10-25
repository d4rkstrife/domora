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

La lecture est directe : la compatibilité des codecs dépend du téléviseur. Le Pi ne transcode pas les vidéos dans cette version. La grille, les affiches TMDB et la navigation à la télécommande ont été vérifiées sur un appareil Fire TV sous Android 9 ; les autres modèles restent à valider.

## Jaquettes et identification des films

À partir du serveur et téléphone 0.8.0 et de Domora TV 0.2.0, la vidéothèque affiche des cartes verticales, le titre sous chaque affiche et une sélection blanche agrandie à la télécommande. La lecture conserve les marges noires et le maintien de l’écran allumé.

Dans le téléphone, ouvrir **Plus → Téléviseurs → Jaquettes et identification des films**. Créer un compte TMDB, demander un accès développeur pour un usage personnel et copier le **jeton d’accès en lecture à l’API** depuis https://www.themoviedb.org/settings/api. Les conditions TMDB sont acceptées par le titulaire du compte. Coller le jeton dans l’application et choisir **Enregistrer et rechercher les jaquettes**. Sur la télévision, choisir **Actualiser**.

Les titres nettoyés et l’année du nom de fichier servent à la recherche ; les épisodes de séries sont exclus. Les correspondances ambiguës gardent une affiche de remplacement. Depuis le même écran téléphone, utiliser **Corriger l’identification**, rechercher le titre puis choisir le bon film et son année. **Retirer la correspondance** conserve un remplacement pour ce fichier. Les recherches et affiches nécessitent Internet lors du premier chargement ; les affiches en cache restent disponibles sans accès à TMDB.

Le jeton est chiffré dans `/var/lib/maison/.catalog/credentials.json`, avec une clé locale privée dans le même dossier. Il n’est transmis ni à la télévision ni aux sauvegardes JSON, et n’est pas inclus dans les APK ou GitHub. Sauvegarder le dossier `.catalog` de manière privée pour conserver la connexion et le cache après réinstallation. La clé locale protège contre une divulgation du seul fichier de configuration, pas contre une compromission complète du serveur.

Domora TV 0.3.0 ajoute un accueil cinéma : image panoramique du film sélectionné ou paysage original de secours, rangée de reprise, catalogue alphabétique français, horloge et profil. Les boutons Ma liste et Informations sont opérationnels. La liste personnelle est locale à chaque télévision. Le profil donne accès à cette liste, à l’actualisation et aux crédits. L’icône et la bannière de lancement ont été remplacées. Voir [les assets et leur création](../android/tv/DESIGN.md).

Le serveur limite les recherches simultanées à deux, met les résultats en cache pendant sept jours et temporise après une erreur. Il ne recherche que les vidéos visibles par la TV ayant déclenché une consultation ; une analyse propriétaire peut porter sur toute la bibliothèque. L’API des affiches vérifie les droits sur la vidéo avant de servir une image. La configuration, la recherche manuelle et la correction sont réservées au propriétaire.

Crédits : TMDB fournit les métadonnées et affiches. Le logo officiel est présenté, avec la mention requise, dans **À propos** sur la TV et dans les crédits de l’écran téléphone. Ressource d’origine : https://www.themoviedb.org/assets/2/v4/logos/v2/blue_short-8e7b30f73a4020692ccca9c88bafe5dcb6f8a62a4c6bc55cd9ba82bb2cd95f6c.svg (conversion vectorielle Android, proportions et couleurs conservées).
