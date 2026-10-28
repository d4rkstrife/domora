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

## Domora 0.9.2 (build 20) : connexion et identité mobile

Le nom Android du téléphone est **Domora**. Son icône reprend le fichier PNG du lanceur Domora TV ; l’accueil et les notifications utilisent également l’identité Domora. L’identifiant Android reste `fr.mamaison.app`, le certificat de signature est conservé et les noms des APK archivées gardent le préfixe historique `ma-maison`, conformément à la règle d’archivage du projet. Installer la mise à jour par-dessus l’application existante.

Un bandeau dans les écrans ordinaires distingue la connexion locale/distante, la reconnexion en cours, le serveur inaccessible, une session à renouveler et une identité sécurisée à vérifier. En cas de coupure, il indique l’heure de la dernière vérification réussie et propose Réessayer. Cette heure concerne la vérification du serveur, pas une garantie que chaque liste métier a été relue au même instant.

La vérification utilise la route `server` existante, au premier plan et hors des vues de lecture, environ toutes les vingt secondes. Un changement de réseau ou le retour au premier plan déclenche une nouvelle vérification. Un retour réussi provoque le rechargement des listes visibles, sans changer la page, la recherche ou les filtres. Un contrôle sain périodique ne présente pas inutilement un message de reconnexion.

Le bouton Réessayer renouvelle une session rejetée uniquement sur action explicite. Il ne recrée pas les clés, ne rouvre pas l’appairage, n’accepte pas un certificat différent et ne contourne pas une révocation. Aucune commande de création, suppression, ajout de torrent ou contrôle d’appareil n’est rejouée automatiquement.

Les listes déjà reçues restent temporairement en mémoire pendant l’utilisation : fichiers, vidéos et métadonnées, téléchargements, caméras, appareils locaux, pièces et scènes. Les données sont isolées par serveur et chemin ; le cache est plafonné à 8 Mio et 24 entrées. Les réponses d’authentification, de configuration, de sauvegarde et les secrets ne sont pas mis dans ce cache. Il ne s’agit pas d’une sauvegarde ni d’un mode hors ligne persistant : une fermeture du processus Android peut le supprimer. Une nouvelle réponse vide remplace correctement l’ancienne liste ; une erreur de lecture ne l’efface pas.

Les téléchargements continuent leur actualisation de quatre secondes. Leur erreur de lecture disparaît après un chargement réussi, sans masquer une erreur distincte d’une commande. Les erreurs de service, les refus de droits et les erreurs réseau sont présentés séparément ; une panne de Transmission n’annonce pas à elle seule que tout le Pi est déconnecté. L’explorateur et les caméras proposent également une nouvelle tentative de chargement, sans rejouer la dernière modification.

Le client partagé, WireGuard, la vérification cryptographique, les API serveur et Domora TV ne sont pas modifiés. Le lecteur vidéo conserve son implémentation ; le contrôle de disponibilité est suspendu dans les vues de lecture. Le direct caméra conserve ses temporisations JPEG/FFmpeg, avec une explication lorsqu’une ancienne image reste affichée après une erreur.

Recette sur téléphone : vérifier le nouveau nom/logo après mise à jour, ouvrir une liste puis interrompre le réseau, constater la conservation de la page et des dernières données, rétablir le réseau et vérifier le rechargement, essayer le bouton Réessayer, puis contrôler une lecture et un passage Wi-Fi/5G. Les tests JVM couvrent les transitions d’état, la classification des erreurs, l’isolation et les limites du cache ; ils ne constituent pas un essai matériel de ces transitions.
