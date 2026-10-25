# Domora TV — accueil cinéma, version 0.3.0

Composition : en-tête Domora avec horloge/date réelles et profil panda, bandeau du titre sélectionné, actions Regarder/Ma liste/Informations, rangée Reprendre la lecture et catalogue Films et séries A–Z. La liste personnelle est enregistrée sur la télévision et accessible depuis le profil. Le tri utilise le collateur français, insensible à la casse, avec le chemin comme départage stable. Les rangées ne contiennent que les fichiers réellement autorisés.

Les épisodes présents restent des fichiers lisibles ; cette interface n’ajoute pas d’identification TMDB des saisons et épisodes. Les panoramas, genres et durées des films proviennent de TMDB et sont mis en cache par le serveur. Le paysage original est le fond de secours des fichiers sans panorama. La liste Reprendre exclut les lectures arrivées à 97 % ou plus.

## Assets livrés

- `src/main/res/drawable-nodpi/domora_landscape.png` : paysage original généré avec l’outil intégré image_gen, puis copié dans le projet sans modification.
- `src/main/res/drawable/domora_mark.xml` : nouveau symbole Domora vectoriel en ruban violet.
- `src/main/res/drawable/domora_icon.xml` : nouvelle icône de lancement.
- `src/main/res/drawable-nodpi/domora_banner.png` : bannière TV réalisée par code graphique, avec le symbole et le nom Domora.
- `src/main/res/drawable/domora_panda.xml` : avatar vectoriel du profil.
- Les pictogrammes lecture, ajout, validation et informations sont dessinés en Canvas vectoriel dans `CinemaHome.kt`.

## Génération du paysage

Mode : outil image_gen intégré, sans appel CLI ni clé API externe. Prompt utilisé :

> Use case: stylized-concept. Asset type: production background artwork for Domora Android TV media-library dashboard. Create a cinematic 16:9 landscape environment, wide mountain lake in Canadian wilderness at twilight, violet and dusty pink clouds, distant snow-capped mountain range, dense evergreen forest, small warm amber-lit timber cabin near the far RIGHT edge reflected in the lake. Atmospheric realistic high-end film still with detailed natural textures and subtle volumetric dusk haze. Composition: LEFT 45% stays very dark and quiet, silhouettes and subtle water texture, for white application text overlays. Dramatic beautiful scenery on center-right. Lower 25% naturally fades to near-black blue for media rows, top-left also subdued for logo. Premium streaming-service art direction inspired by moody postapocalyptic wilderness without any characters or identifiable film franchise imagery. No people, no text, no logos, no UI, no borders, no poster collage. Entire image edge-to-edge. Palette midnight teal, dark pine, indigo, violet sunset, warm cabin window. Landscape wide 16:9.

Les affiches et images de films ne sont pas des images générées : elles restent fournies par TMDB, avec les crédits officiels dans le profil → À propos.
