# Compiler Android

Android 10 minimum, JDK 17, SDK 35, Gradle 8.9, Kotlin 2.4.0. R8 9.1.56 est explicitement utilisé pour le SDK Google Home. Coroutines 1.11.0 alignées par BOM : une version plus ancienne peut provoquer un NoSuchMethodError asynchrone au lancement du SDK Home.

Télécharger officiellement le SDK Google Home Android 1.11.0 après acceptation de ses conditions : https://developers.home.google.com/apis/android/sdk. Extraire son dépôt Maven dans tools/google-home-sdk-1.11.0. Le SDK n'est pas redistribué dans le dépôt.

Sur le poste Windows utilisé pour ce projet, placer JDK dans tools/jdk, Android SDK dans tools/android-sdk et Gradle 8.9 dans tools/gradle/gradle-8.9. Puis :

```powershell
./scripts/build-android.ps1
```

Ce script compile les modules téléphone et télévision et archive leurs APK sans écraser les APK historiques, avec version, build, date Europe/Paris et empreinte du contenu. Le préfixe téléphone reste `ma-maison` ; celui de la télévision est `domora-tv`. Les sources de connexion sécurisée et WireGuard sont partagées dans `android/common`. La télévision n’embarque pas le SDK Google Home. Conserver le certificat de signature local pour mettre à jour les installations existantes.

Pour Google Home, créer son propre client OAuth Android, package fr.mamaison.app et SHA-1 de son certificat, écran de consentement et utilisateur de test. Documentation : https://developers.home.google.com/apis/android/oauth. Les comptes et jetons Google restent dans le SDK Android. Le Pi n'en reçoit aucun.
