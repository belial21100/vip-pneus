# VIP Pneus – Bons d'intervention (application Android pour tablette)

Application pour remplir les bons d'intervention sur tablette, faire signer le client
et envoyer le PDF à la comptabilité en pièce jointe. Tout fonctionne sans connexion ;
les bons restent enregistrés sur la tablette.

## Principe

Trois sortes de bons :

- **Fiche d'intervention** (Mac2, Conti…) : la fiche presse mobile FPS, créée avec
  **Nouvelle fiche d'intervention**. On y joint le bon de commande du client : ses
  informations **remplissent automatiquement** la fiche, et il la suit dans le PDF.
  PDF envoyé = la fiche, puis le bon de commande.
- **Feuille de tâche Mastra** : le document du client est lui-même la fiche. On
  l'**importe** et le technicien écrit directement dans ses cases ; PDF envoyé = page 1
  le document rempli, page 2 le document d'origine.
- **Document à signer** (bon de livraison…) : tout autre document importé. Seule la
  signature du client est exigée ; on peut aussi y écrire librement.

**L'import ne crée jamais de fiche d'intervention** : un document importé est une feuille
Mastra à remplir ou un document à signer. Un bon de commande importé seul est traité comme
un document à signer (ses informations servent au nom du fichier) ; pour la fiche
d'intervention, on le joint à une nouvelle fiche.

### Documents reconnus automatiquement

| Document client | Ce que fait l'application |
|---|---|
| **Bon de commande Manuloc** (joint à une fiche) | Fiche pré-remplie : n° de commande, client mandataire (adresse de facturation), client utilisateur (adresse de livraison), n° de série et type d'engin, pneus AV/AR (dimensions, marque, profil, type, quantité) et quantités de prestations par taille de jante. |
| **Mobile Service Continental** (Conti360°, joint à une fiche) | Fiche pré-remplie : n° Mobile Service, rendez-vous, site (localisation), modèle, immatriculation, n° de flotte, pneus montés et prestations. |
| **Feuille de tâche Mastra** (Interfit, importée) | Écriture directe sur le document, seulement dans les cases que remplit le technicien : lecture du compteur, couple de serrage, monteur, date, « Reçu par » et signature, aux emplacements et tailles qu'il utilise (couple préconisé et horamètre de la demande affichés en aide). Le client final (lieu réel de l'intervention s'il diffère de « Livrer à ») s'écrit dans un encart au-dessus de « Commentaires ». Toute autre mention : texte libre posé sur la page. Page 2 : le document d'origine. Si le PDF reçu a plusieurs pages (page 1 scannée ou déjà traitée), la feuille est cherchée dans les pages suivantes et seule sa page est gardée. |
| Autre document importé (bon de livraison…) | Document à signer : **seule la signature du client est exigée**, aucun champ n'est imposé. Sans texte lisible (photo, scan), un message le signale. |

Dans tous les cas, la date du jour et le nom du technicien sont remplis d'office.

### Sur la tablette

1. **Importer un document** (bouton jaune de l'accueil, ou « Ouvrir avec / Partager →
   VIP Pneus » depuis la messagerie) pour une feuille Mastra ou un document à signer ;
   ou **Nouvelle fiche d'intervention** pour Mac2 et Conti, puis **Joindre** le bon de
   commande (en tête de la fiche).
2. Les champs lus dans le document sont surlignés en jaune (✦) : les vérifier. Le cadre
   **À compléter** nomme chaque élément qui manque encore (horamètre, serrage, signature…)
   avec ce qu'il faut faire ; un appui mène directement au champ. Les champs attendus
   portent la mention « À compléter » et chaque rubrique une pastille « À compléter » ou
   « Complet ». Ce qui manque est aussi rappelé dans la barre d'envoi, sur la liste des bons
   et au-dessus de l'aperçu du PDF. L'aperçu de la page se met à jour en direct.
3. **Faire signer** le client au doigt ou au stylet (son nom se saisit dans la même fenêtre).
4. Au besoin, écrire sur la page (ou **Ajuster** la fiche) : déplacer un texte, changer sa
   taille, ajouter une mention, une date, une croix ou une signature (pincer pour zoomer).
   Le bouton **plein écran** de la palette donne toute la tablette à la page, et le bouton
   **largeur** l'agrandit à la largeur de l'écran. Sur une feuille Mastra, les cases vides
   sont repérées en jaune sur la page : un appui les remplit ; en plein écran, « Manque : … »
   en bas ouvre directement la case suivante.
5. **Envoyer à la compta** (barre du bas, avec le nom du fichier) : s'il manque quelque
   chose, l'application dit précisément quoi avant l'envoi (y compris depuis la liste des
   bons, et bon par bon pour un envoi groupé). L'envoi reste possible (« Envoyer quand
   même ») : le bon est alors marqué **Envoyé incomplet**, avec ce qui manquait, sur la
   liste et dans le bon, jusqu'à ce qu'il soit complété et renvoyé. Le PDF est créé et la
   messagerie s'ouvre avec l'adresse de la comptabilité, l'objet et la pièce jointe.
   Plusieurs bons peuvent être envoyés ensemble (appui long dans la liste).

Des photos (bon papier, pneus…) et d'autres PDF peuvent être ajoutés à la suite de la fiche.

Nom de fichier par défaut, sur le modèle déjà utilisé :
`02- LOC TEST ENTREPOT DUPONT 02000 LAON 1234567 24-08-2026 CE.pdf`
(département, client, site, code postal, ville, n° de commande, date, initiales) ;
pour Mastra : `54- MASTRA <livrer à> JobSheet_<n°> <date> <initiales>.pdf` ;
document sans informations saisies (bon de livraison) : nom du fichier reçu, date, initiales.

### Réglages

Nom du technicien (pré-remplit « Commercial / Monteur »), initiales (fin du nom des
fichiers), e-mail de la comptabilité, copie éventuelle et message de l'e-mail.
À la première ouverture, une page **Bienvenue** (technicien, comptabilité, récapitulatif)
les demande obligatoirement avant d'accéder aux bons ; les adresses e-mail sont vérifiées.
Tout est enregistré sur la tablette (rien n'est envoyé ailleurs) et reste modifiable dans
**Réglages** (bouton « Enregistrer »).

## Installation sur les tablettes

1. Copier le fichier `vip-pneus-<version>.apk` sur la tablette (e-mail, clé USB, Drive…).
2. L'ouvrir ; accepter « Installer des applications inconnues » pour l'application
   utilisée (Fichiers, Gmail…).
3. Au premier lancement, la page **Bienvenue** demande le nom du technicien, ses initiales
   et l'e-mail de la comptabilité.

Android 8.0 minimum. Conçue pour tablette (paysage et portrait), utilisable sur téléphone.

**Mises à jour** : une nouvelle version s'installe par-dessus l'ancienne (les bons sont
conservés) à condition d'être signée avec **la même clé**. Conservez précieusement le
fichier de clé `vip-pneus-release.jks` et ses mots de passe : sans eux, il faudrait
désinstaller l'application (et perdre les bons enregistrés) pour installer une nouvelle version.

## Développement

- Kotlin, Jetpack Compose (Material 3), PdfBox-Android pour la lecture et l'écriture des PDF.
- `app/src/main/java/fr/vippneus/intervention/`
  - `pdf/FpsTemplate.kt` : zones de saisie de la fiche FPS (positions et tailles relevées
    sur des fiches remplies par les techniciens) ;
  - `pdf/Layout.kt` : mise en page commune à l'aperçu écran et au PDF (ce que l'on voit
    est ce que l'on obtient) ; `pdf/PdfExporter.kt` : création du PDF final ;
  - `importer/` : lecture du texte positionné des PDF, reconnaissance des documents
    clients et pré-remplissage (`ClientDocs.kt`) ;
  - `data/Completion.kt` : ce qu'il reste à remplir sur un bon (cadre « À compléter ») ;
  - `ui/` : écrans (accueil, fiche, document client, éditeur de page, signature, réglages) ;
    `Theme.kt` (graphite et jaune, police Barlow) et `Components.kt` (éléments communs).
- `app/src/main/assets/templates/fiche_presse_mobile.jpg` : fiche vierge (fond de page).

### Construire

```bash
./gradlew testDebugUnitTest   # tests (Robolectric) : mise en page, PDF, import, parcours écran
./gradlew assembleRelease     # APK dans app/build/outputs/apk/release/
```

Les tests génèrent des PDF et des captures d'écran de tablette dans `app/build/test-output/`.
`SamplesTest` vérifie la reconnaissance sur de vrais documents clients, gardés hors du
dépôt : `VIP_SAMPLES_DIR=/chemin/vers/pdf ./gradlew testDebugUnitTest --tests '*SamplesTest*'`.

### Signature de l'APK

La clé n'est **jamais** versionnée (dépôt public). En local, créer à la racine un fichier
`keystore.properties` (ignoré par git) :

```properties
storeFile=/chemin/vers/vip-pneus-release.jks
storePassword=...
keyAlias=vip-pneus
keyPassword=...
```

Sur GitHub (Actions), le workflow `APK Android` construit et teste l'application à chaque
envoi. Pour qu'il produise un APK signé avec la clé officielle, ajouter dans
*Settings → Secrets and variables → Actions* : `VIP_KEYSTORE_BASE64` (contenu du `.jks`
encodé en base64 : `base64 -w0 vip-pneus-release.jks`), `VIP_KEYSTORE_PASSWORD`,
`VIP_KEY_ALIAS` (`vip-pneus`) et `VIP_KEY_PASSWORD`. Sans ces secrets, l'APK produit est
signé avec une clé de test : installable, mais pas en mise à jour de la version officielle.

## Licences

Police Source Sans 3 (SIL Open Font License 1.1, voir `app/src/main/assets/licences/`),
PdfBox-Android (Apache 2.0).
