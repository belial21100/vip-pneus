package fr.vippneus.intervention.pdf

/**
 * Gabarit de la « Fiche d'intervention presse mobile » (reprise de la fiche FPS, au logo VIP).
 *
 * Le fond est l'image de la fiche vierge (assets/templates). Les zones de saisie ont été
 * relevées sur les fiches remplies par les techniciens : positions en pixels de l'image
 * (1449 × 2048), converties en points PDF (page A4 595 × 842). Les tailles de police
 * reprennent celles utilisées sur les exemples (texte réduit automatiquement s'il est trop long).
 * Le tableau « Fournitures » a six lignes : AV, AR, puis les autres essieux et roues intérieures.
 */
object FpsTemplate {
    const val BACKGROUND_ASSET = "templates/fiche_presse_mobile.jpg"
    const val PAGE_W = 595.28f
    const val PAGE_H = 841.89f
    private const val IMG_W = 1449f
    private const val IMG_H = 2048f

    private fun px(x: Number) = x.toFloat() * PAGE_W / IMG_W
    private fun py(y: Number) = y.toFloat() * PAGE_H / IMG_H
    private fun box(x0: Number, y0: Number, x1: Number, y1: Number) = Box(px(x0), py(y0), px(x1), py(y1))

    enum class HAlign { START, CENTER }

    class Field(
        val key: String,
        val label: String,
        val box: Box,
        val fontSize: Float,
        val align: HAlign = HAlign.START,
        val maxLines: Int = 1,
        val minFontSize: Float = 7f,
        /** Si renseigné : 1re ligne centrée sur cette ordonnée (alignée sur l'étiquette imprimée). */
        val firstLineY: Float? = null,
    )

    class Option(val value: String, val label: String, val cx: Float, val cy: Float)

    /** Cases à cocher « Oui / Non » : une croix est tracée dans la case choisie. */
    class Choice(val key: String, val label: String, val options: List<Option>)

    object K {
        const val CLIENT_MANDATAIRE = "clientMandataire"
        const val MANDATAIRE_ADRESSE = "mandataireAdresse"
        const val MANDATAIRE_CP = "mandataireCp"
        const val NUMERO_COMMANDE = "numeroCommande"
        const val MONTEUR = "monteur"
        const val DATE = "date"
        const val CLIENT_UTILISATEUR = "clientUtilisateur"
        const val UTILISATEUR_ADRESSE = "utilisateurAdresse"
        const val UTILISATEUR_CP = "utilisateurCp"

        const val MARQUE = "materiel.marque"
        const val TYPE = "materiel.type"
        const val SERIE = "materiel.serie"
        const val PARC = "materiel.parc"
        const val HORAMETRE = "materiel.horametre"

        const val AV_FOURNI = "av.fourni"
        const val AR_FOURNI = "ar.fourni"
        /** Case d'une ligne du tableau « Fournitures » ([row] : av, ar, sup1…sup4). */
        fun pneu(row: String, col: String) = "$row.$col"
        fun fourni(row: String) = pneu(row, "fourni")
        /** Essieu ou roues d'une ligne à préciser (ex. « Essieu 3 », « AR int. »). */
        fun essieu(row: String) = pneu(row, "essieu")

        fun prestation(row: String, col: String) = "prest.$row.$col"
        const val DEPLACEMENT = "deplacement"
        const val KMS = "kms"

        const val SERRAGE_AV = "serrage.av"
        const val SERRAGE_AR = "serrage.ar"
        const val SERRAGE_AV_REMARQUE = "serrage.av.remarque"
        const val SERRAGE_AR_REMARQUE = "serrage.ar.remarque"

        const val JANTES_LUSTREES = "jantes.lustrees"
        const val JANTES_FISSUREES = "jantes.fissurees"
        const val JANTES_HS = "jantes.hs"
        const val JANTES_AUTRES = "jantes.autres"

        const val OBSERVATIONS = "observations"
        const val SIGNATAIRE = "signataire"
        /** Clé d'ajustement de la signature du client. */
        const val SIGNATURE = "signature"
    }

    /**
     * Ligne du tableau « Fournitures » : [printed] est l'essieu imprimé sur la fiche (AV, AR) ;
     * sans lui, c'est une ligne à préciser, dont l'essieu est écrit par le technicien. y0, y1 en pixels.
     */
    class PneuRow(val key: String, val printed: String?, val y0: Int, val y1: Int) {
        /** Nom de la ligne dans l'application (« AV », « ligne 3 »). */
        val name: String get() = printed ?: "ligne ${pneuRows.indexOf(this) + 1}"
    }

    val pneuRows = listOf(
        PneuRow("av", "AV", 830, 861),
        PneuRow("ar", "AR", 863, 894),
        PneuRow("sup1", null, 896, 927),
        PneuRow("sup2", null, 929, 960),
        PneuRow("sup3", null, 962, 993),
        PneuRow("sup4", null, 995, 1026),
    )

    /** Lignes à préciser : autres essieux, roues intérieures. */
    val extraPneuRows = pneuRows.filter { it.printed == null }

    /** Clé d'une case du tableau « Fournitures ». */
    fun isPneuKey(key: String) = pneuRows.any { key.startsWith(it.key + ".") }

    /** Colonnes du tableau « Fournitures » : clé -> (libellé, x0, x1 en pixels). */
    val pneuColumns = listOf(
        Triple("dimensions", "Dimensions", 454 to 624),
        Triple("marque", "Marque", 638 to 810),
        Triple("profil", "Profil", 824 to 995),
        Triple("type", "Type", 1009 to 1180),
        Triple("quantite", "Quantité", 1194 to 1366),
    )

    /** Lignes du tableau « Prestations » : clé -> (libellé, y0, y1). */
    val prestationRows = listOf(
        Triple("depose", "Dépose / repose roues", 1136 to 1212),
        Triple("depressage", "Dépressage / pressage pneus", 1218 to 1297),
        Triple("dechets", "Déchets", 1303 to 1378),
    )

    /** Colonnes du tableau « Prestations » : clé -> (libellé, x0, x1). */
    val prestationColumns = listOf(
        Triple("8", "8 pouces", 266 to 441),
        Triple("9", "9 pouces", 452 to 625),
        Triple("10", "10 pouces", 636 to 811),
        Triple("12", "12 pouces", 822 to 996),
        Triple("15", "15 pouces", 1007 to 1181),
        Triple("autres", "Autres", 1192 to 1366),
    )

    val fields: List<Field> = buildList {
        // En-tête : donneur d'ordre et commande
        add(Field(K.CLIENT_MANDATAIRE, "Client mandataire", box(915, 208, 1395, 244), 16f))
        add(Field(K.MANDATAIRE_ADRESSE, "Adresse / Ville (mandataire)", box(878, 245, 1395, 281), 14f))
        add(Field(K.MANDATAIRE_CP, "Code postal (mandataire)", box(848, 282, 1395, 316), 14f))
        add(Field(K.NUMERO_COMMANDE, "N° de commande", box(925, 331, 1358, 409), 28f, minFontSize = 12f))
        add(Field(K.MONTEUR, "Commercial / Monteur", box(360, 437, 700, 473), 18f))
        add(Field(K.DATE, "Date", box(808, 436, 1395, 473), 18f))
        add(
            Field(
                K.CLIENT_UTILISATEUR, "Client utilisateur", box(300, 507, 712, 588), 16f,
                maxLines = 2, minFontSize = 8f, firstLineY = py(524),
            )
        )
        add(Field(K.UTILISATEUR_ADRESSE, "Adresse / Ville (utilisateur)", box(910, 474, 1395, 505), 14f))
        add(Field(K.UTILISATEUR_CP, "Code postal (utilisateur)", box(882, 507, 1395, 540), 14f))

        // Matériel
        val materiel = listOf(
            Triple(K.MARQUE, "Marque", 82 to 330),
            Triple(K.TYPE, "Type", 341 to 589),
            Triple(K.SERIE, "N° de série", 600 to 848),
            Triple(K.PARC, "N° de parc", 859 to 1107),
            Triple(K.HORAMETRE, "Horamètre", 1118 to 1366),
        )
        materiel.forEach { (key, label, xs) ->
            add(Field(key, label, box(xs.first, 690, xs.second, 731), 16f, HAlign.CENTER, minFontSize = 7f))
        }

        // Fournitures : une ligne par essieu (AV, AR, puis autres essieux et roues intérieures)
        pneuRows.forEach { row ->
            if (row.printed == null) {
                add(Field(K.essieu(row.key), "Essieu (${row.name})", box(84, row.y0, 254, row.y1), 12f, HAlign.CENTER, minFontSize = 6f))
            }
            pneuColumns.forEach { (col, label, xs) ->
                add(
                    Field(
                        K.pneu(row.key, col), "$label (${row.name})",
                        box(xs.first, row.y0, xs.second, row.y1), 12f, HAlign.CENTER, minFontSize = 6f,
                    )
                )
            }
        }

        // Prestations (quantités)
        prestationRows.forEach { (row, rowLabel, ys) ->
            prestationColumns.forEach { (col, colLabel, xs) ->
                add(
                    Field(
                        K.prestation(row, col), "$rowLabel – $colLabel",
                        box(xs.first, ys.first, xs.second, ys.second), 24f, HAlign.CENTER,
                        maxLines = 2, minFontSize = 8f,
                    )
                )
            }
        }
        add(Field(K.KMS, "Nombre de km départ agence", box(1142, 1395, 1305, 1460), 16f, HAlign.CENTER))

        // Serrage des roues
        add(Field(K.SERRAGE_AV, "Serrage AV (Nm)", box(378, 1481, 492, 1511), 16f, HAlign.CENTER, minFontSize = 8f))
        add(Field(K.SERRAGE_AR, "Serrage AR (Nm)", box(378, 1516, 492, 1546), 16f, HAlign.CENTER, minFontSize = 8f))
        add(Field(K.SERRAGE_AV_REMARQUE, "Remarque serrage AV", box(548, 1481, 1395, 1511), 13f))
        add(Field(K.SERRAGE_AR_REMARQUE, "Remarque serrage AR", box(548, 1516, 1395, 1546), 13f))

        // État des jantes
        listOf(
            Triple(K.JANTES_LUSTREES, "Jantes lustrées", 289 to 547),
            Triple(K.JANTES_FISSUREES, "Jantes fissurées", 558 to 835),
            Triple(K.JANTES_HS, "Jantes HS", 846 to 1104),
            Triple(K.JANTES_AUTRES, "Autres (jantes)", 1115 to 1370),
        ).forEach { (key, label, xs) ->
            add(Field(key, label, box(xs.first, 1686, xs.second, 1747), 14f, HAlign.CENTER, maxLines = 2, minFontSize = 7f))
        }

        add(Field(K.OBSERVATIONS, "Observations", box(84, 1815, 1365, 1862), 13f, maxLines = 2, minFontSize = 7f))
        add(
            Field(
                K.SIGNATAIRE, "Nom / mention du signataire", box(312, 1880, 790, 2005), 18f,
                maxLines = 2, minFontSize = 9f, firstLineY = py(1912),
            )
        )
    }

    val fieldsByKey: Map<String, Field> = fields.associateBy { it.key }

    val choices: List<Choice> = pneuRows.map { row ->
        // Cases « Oui ☐  Non ☐ » de la colonne « Fournis », centrées sur la ligne
        val cy = py((row.y0 + row.y1 + 1) / 2f)
        Choice(K.fourni(row.key), "Pneus ${row.name} fournis", listOf(Option("oui", "Oui", px(329), cy), Option("non", "Non", px(417), cy)))
    } + listOf(
        Choice(
            K.DEPLACEMENT, "Déplacement pour prestation < 4 pneus",
            listOf(Option("oui", "Oui", px(660.5), py(1408)), Option("non", "Non", px(660.5), py(1442.5))),
        ),
    )

    /** Demi-côté de la croix tracée dans une case (points). */
    val crossHalf: Float = px(9)

    /** Zone de la signature du client. */
    val signatureBox: Box = box(800, 1872, 1385, 2006)
}
