# Compiler Android

Android 10 minimum, JDK 17, SDK 35, Gradle 8.9, Kotlin 2.4.0. R8 9.1.56 est explicitement utilisé pour le SDK Google Home. Coroutines 1.11.0 alignées par BOM : une version plus ancienne peut provoquer un NoSuchMethodError asynchrone au lancement du SDK Home.

Télécharger officiellement le SDK Google Home Android 1.11.0 après acceptation de ses conditions : https://developers.home.google.com/apis/android/sdk. Extraire son dépôt Maven dans tools/google-home-sdk-1.11.0. Le SDK n'est pas redistribué dans le dépôt.

Sur le poste Windows utilisé pour ce projet, placer JDK dans tools/jdk, Android SDK dans tools/android-sdk et Gradle 8.9 dans tools/gradle/gradle-8.9. Puis :

```powershell
./scripts/build-android.ps1
```

Ce script compile les modules téléphone et télévision et archive leurs APK sans écraser les APK historiques, avec version, build, date Europe/Paris et empreinte du contenu. Le préfixe téléphone reste `ma-maison` ; celui de la télévision est `domora-tv`. Les sources de connexion sécurisée et WireGuard sont partagées dans `android/common`. La télévision n’embarque pas le SDK Google Home. Conserver le certificat de signature local pour mettre à jour les installations existantes.

Pour Google Home, créer son propre client OAuth Android, package fr.mamaison.app et SHA-1 de son certificat, écran de consentement et utilisateur de test. Documentation : https://developers.home.google.com/apis/android/oauth. Les comptes et jetons Google restent dans le SDK Android. Le Pi n'en reçoit aucun.

## Application mobile : navigation et vidéothèque

La version 0.9.1 (build 19) conserve les cinq espaces : **Accueil, Seedbox, Médias, Maison et Réglages**. Les caméras, les appareils locaux, Google Home et les pièces/scènes sont accessibles dans Maison. Les téléchargements, les fichiers et le stockage sont dans Seedbox.

Dans Médias, les vidéos sont présentées dans une grille adaptative avec le titre sous l’affiche, dans l’ordre alphabétique français. La recherche reconnaît aussi le titre d’origine et le nom de fichier ; elle ignore la casse et les accents. Les filtres Tous et À reprendre sont conservés.

Toucher une carte ouvre sa fiche : résumé, année, genres et durée lorsque ces données existent, chemin du fichier et position de lecture. Le bouton de lecture sur l’affiche démarre directement la vidéo. Le lecteur Media3, le plein écran et l’enregistrement de progression ne sont pas modifiés.

Le téléphone réutilise deux réponses existantes : `library` pour les fichiers et la progression mobile, puis `media/catalog/library` pour les métadonnées. La fusion se fait par chemin exact et ne remplace pas la progression par celle du catalogue. Le catalogue enrichi est actuellement réservé au propriétaire côté serveur. En cas de refus ou d’indisponibilité, la liste simple et la lecture restent utilisables ; aucune règle d’accès n’est modifiée.

Les images passent par la route authentifiée `media/catalog/poster` du Pi, via le client sécurisé existant. Il n’y a pas de jeton TMDB dans l’APK ni d’URL publique ajoutée pour les affiches. Le chargement est limité à deux images simultanées, avec un cache mémoire plafonné à 12 Mio. Une image absente est remplacée par une carte de titre, sans bloquer la lecture.

La configuration TMDB et les corrections restent dans **Médias → menu ⋯ → Jaquettes et identification des films**. Après une identification ou une correction, revenir à la bibliothèque ou l’actualiser pour retrouver les données. Les séries ne disposent pas encore d’un regroupement complet par saisons : les épisodes restent des fichiers vidéo.

Pour compiler seulement le téléphone et préserver les archives :

```powershell
./scripts/build-android.ps1 -Module app
```

Les tests JVM couvrent la navigation et les règles de bibliothèque : correspondances par chemin, conservation de progression, classement, recherche et repli sans catalogue. Les tests et Lint ne remplacent pas la recette sur téléphone des affiches, du retour depuis le lecteur et de l’accès Wi-Fi/5G.
