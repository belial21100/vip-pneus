# VIP Pneus – Bons d'intervention (application Android pour tablette)

Application pour remplir les bons d'intervention sur tablette, faire signer le client
et envoyer le PDF à la comptabilité en pièce jointe. Tout fonctionne sans connexion ;
les bons restent enregistrés sur la tablette.

## Principe

Le client envoie un PDF d'une page. Au lieu d'ajouter chaque champ à la main dans Adobe,
on **importe le PDF dans l'application** : elle lit les informations du document et
**remplit automatiquement** la fiche d'inter. Le technicien n'a plus qu'à vérifier et
compléter ce qui se constate sur place (horamètre, serrage, remarques, signature).

Deux traitements :

- **Mac2, Conti…** : la fiche d'intervention FPS est créée et pré-remplie ;
  PDF envoyé = la fiche, puis le document du client.
- **Mastra** : pas de fiche FPS, le technicien écrit directement sur le document du
  client ; PDF envoyé = page 1 le document rempli, page 2 le document d'origine.

### Documents reconnus automatiquement

| Document client | Ce que fait l'application |
|---|---|
| **Bon de commande Manuloc** | Fiche presse mobile FPS pré-remplie : n° de commande, client mandataire (adresse de facturation), client utilisateur (adresse de livraison), n° de série et type d'engin, pneus AV/AR (dimensions, marque, profil, type, quantité) et quantités de prestations par taille de jante. Le bon de commande est joint après la fiche. |
| **Mobile Service Continental** (Conti360°) | Fiche FPS pré-remplie : n° Mobile Service, rendez-vous, site (localisation), modèle, immatriculation, n° de flotte, pneus montés et prestations. Document joint après la fiche. |
| **Feuille de tâche Mastra** (Interfit) | Écriture directe sur le document : lecture du compteur, monteur, heures, date, « Reçu par », couple de serrage, lieu d'intervention et signature sont placés automatiquement aux emplacements et tailles utilisés par les techniciens (couple préconisé et horamètre de la demande affichés en aide). Page 2 : le document d'origine. |
| Autre document | Au choix : fiche FPS avec le document joint, ou écriture libre sur le document. |

Dans tous les cas, la date du jour et le nom du technicien sont remplis d'office.

### Sur la tablette

1. **Importer un PDF client** (ou « Ouvrir avec / Partager → VIP Pneus » depuis la
   messagerie), ou **Nouvelle fiche d'intervention** pour une fiche vierge.
2. Vérifier et compléter les champs ; l'aperçu de la page se met à jour en direct.
3. **Faire signer** le client au doigt ou au stylet.
4. Au besoin, **Ajuster** : déplacer un texte, changer sa taille, ajouter une mention,
   une date, une croix ou une signature n'importe où sur la page (pincer pour zoomer).
5. **Envoyer** : le PDF est créé (nom de fichier automatique, modifiable) et la
   messagerie s'ouvre avec l'adresse de la comptabilité, l'objet et la pièce jointe.
   Plusieurs bons peuvent être envoyés ensemble (appui long dans la liste).

Des photos (bon papier, pneus…) et d'autres PDF peuvent être ajoutés à la suite de la fiche.

Nom de fichier par défaut, sur le modèle déjà utilisé :
`02- LOC TEST ENTREPOT DUPONT 02000 LAON 1234567 24-08-2026 CE.pdf`
(département, client, site, code postal, ville, n° de commande, date, initiales) ;
pour Mastra : `54- MASTRA <livrer à> JobSheet_<n°> <date> <initiales>.pdf`.

### Réglages

Nom du technicien (pré-remplit « Commercial / Monteur »), initiales (fin du nom des
fichiers), e-mail de la comptabilité, copie éventuelle et message de l'e-mail.

## Installation sur les tablettes

1. Copier le fichier `vip-pneus-1.0.0.apk` sur la tablette (e-mail, clé USB, Drive…).
2. L'ouvrir ; accepter « Installer des applications inconnues » pour l'application
   utilisée (Fichiers, Gmail…).
3. Au premier lancement, ouvrir **Réglages** et renseigner le technicien et l'e-mail
   de la comptabilité.

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
    clients et pré-remplissage (`ClientDocs.kt`), détection d'une fiche FPS en 1re page ;
  - `ui/` : écrans (liste, fiche, document client, éditeur de page, signature, réglages).
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
