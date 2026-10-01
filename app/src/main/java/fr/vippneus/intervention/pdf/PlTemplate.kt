package fr.vippneus.intervention.pdf

import fr.vippneus.intervention.pdf.FpsTemplate.K

/** Tableaux de pneus de la fiche poids lourds. */
enum class TyreTable(val code: String, val title: String) {
    MONTES("m", "Pneus montés"),
    DEMONTES("d", "Pneus démontés"),
}

/**
 * Clés propres à la fiche poids lourds. Les informations communes avec la fiche presse mobile
 * gardent les clés de [FpsTemplate.K] (date, n°, technicien, donneur d'ordre, client, adresse,
 * véhicule, observations, signataire) : nom du fichier, récapitulatif et propositions restent les mêmes.
 */
object PlKeys {
    const val PREFIX = "pl."
    const val LIEU = "pl.lieu"
    const val KM_DEPART = "pl.kmDepart"
    const val KM_ARRIVEE = "pl.kmArrivee"
    const val HEURE_DEPART = "pl.heureDepart"
    const val HEURE_ARRIVEE = "pl.heureArrivee"
    const val VEHICULE = "pl.vehicule"

    /** Ligne « Dimensions pneus / AV-AR / Marque / Type / N/R/O / Qté » (sous les kilomètres). */
    fun pneu(col: String) = "pl.pneu.$col"

    /** Case d'un tableau « Pneus montés / démontés » (lignes 1 à 8). */
    fun tyre(table: TyreTable, row: Int, col: String) = "pl.${table.code}$row.$col"

    fun fourniture(item: String, col: String) = "pl.fourn.$item.$col"
    fun service(item: String) = "pl.serv.$item"

    /** Croix d'une roue sur le schéma (éditeur : ajustement de sa position). */
    fun wheel(position: String) = "pl.roue.$position"
}

/**
 * Gabarit de la « Fiche d'intervention » poids lourds de VIP (A4 à l'italienne), reprise telle quelle :
 * le PDF d'origine sert de page 1, les saisies sont écrites dans ses cases (coordonnées relevées
 * sur le PDF, en points depuis le coin haut-gauche). Les roues concernées sont cochées sur le schéma.
 */
object PlTemplate : Sheet {
    override val pageW = 841.92f
    override val pageH = 595.32f
    override val imageAsset = "templates/fiche_poids_lourds.jpg"
    override val pdfAsset = "templates/fiche_poids_lourds.pdf"

    const val TYRE_ROWS = 8

    /** Colonnes des tableaux de pneus : clé -> (libellé, x0, x1). */
    val tyreColumns = listOf(
        Triple("dimensions", "Dimensions", 59f to 188f),
        Triple("marque", "Marque", 192f to 286f),
        Triple("matricule", "Matricule", 291f to 413f),
        Triple("position", "Position", 418f to 463f),
        Triple("usure", "Usure mm", 467f to 527f),
    )

    /** Lignes des tableaux de pneus (haut, bas). */
    private val tyreRows = mapOf(
        TyreTable.MONTES to listOf(
            249.2f to 258.3f, 259.0f to 268.6f, 269.4f to 279.0f, 279.7f to 289.3f,
            290.0f to 299.6f, 300.3f to 309.9f, 310.6f to 320.2f, 321.0f to 330.2f,
        ),
        TyreTable.DEMONTES to listOf(
            357.8f to 366.9f, 367.6f to 377.2f, 378.0f to 387.6f, 388.3f to 397.9f,
            398.6f to 408.2f, 408.9f to 418.5f, 419.3f to 428.9f, 429.6f to 438.8f,
        ),
    )

    /** Ligne « Dimensions pneus / AV-AR / Marque / Type / N/R/O / Qté » : clé -> (libellé, x0, x1). */
    val pneuColumns = listOf(
        Triple("dimensions", "Dimensions pneus", 60f to 188f),
        Triple("avar", "AV/AR", 192f to 237f),
        Triple("marque", "Marque", 242f to 351f),
        Triple("type", "Type", 356f to 413f),
        Triple("nro", "N/R/O", 418f to 463f),
        Triple("qte", "Qté", 467f to 527f),
    )

    /** Fournitures : clé -> (libellé, haut, bas). */
    val fournitures = listOf(
        Triple("flap", "Flap", 210.4f to 223.2f),
        Triple("roue", "Roue", 224.0f to 235.2f),
        Triple("chambre", "Chambre à air", 236.0f to 248.0f),
        Triple("joints", "Joints", 248.7f to 258.3f),
        Triple("patte", "Patte fixation", 259.0f to 268.6f),
        Triple("rallonges", "Rallonges", 269.4f to 279.0f),
        Triple("valves", "Valves", 279.7f to 289.3f),
        Triple("emplatres", "Emplâtres", 290.0f to 299.6f),
        Triple("masses", "Masses", 300.3f to 309.6f),
    )

    /** Services (quantités) : clé -> (libellé, haut, bas). */
    val services = listOf(
        Triple("geo", "Contrôle réglage géo", 321.4f to 330.6f),
        Triple("visuel", "Contrôle visuel", 331.3f to 342.2f),
        Triple("demMont", "Dém./mont.", 342.9f to 356.6f),
        Triple("depPos", "Dép./pos.", 357.3f to 366.9f),
        Triple("depPosRs", "Dép./pos. RS", 367.6f to 377.2f),
        Triple("repChambre", "Réparation chambre", 378.0f to 387.6f),
        Triple("repTubeless", "Réparation tubeless", 388.3f to 397.9f),
        Triple("equilibrage", "Équilibrage roue déposée", 398.6f to 408.2f),
        Triple("mainOeuvre", "Heure main d'œuvre", 408.9f to 418.5f),
        Triple("permutation", "Permutation", 419.3f to 428.9f),
        Triple("env", "Prise en charge env. usagée", 429.6f to 439.2f),
        Triple("pressions", "Pressions des pneus", 439.9f to 453.6f),
        Triple("retaillageMoteur", "Retaillage essieu moteur", 454.3f to 463.9f),
        Triple("retaillageNonMoteur", "Retaillage essieu non moteur", 464.6f to 474.2f),
        Triple("deplacement", "Déplacement", 474.9f to 484.2f),
    )

    // ---------------------------------------------------------------- schéma des positions

    /** Roue du schéma : position écrite dans la colonne « Position », case cochée sur la fiche. */
    class Wheel(val position: String, val short: String, val box: Box)

    /** Essieu du schéma, tel qu'il est nommé sur la fiche (« 1 AV », « 2 ESS »…). */
    class Axle(val label: String, val wheels: List<Wheel>, val spare: Boolean = false)

    class Vehicle(val name: String, val axles: List<Axle>)

    private fun twin(axle: String, y: Float, xs: List<Pair<Float, Float>>, h: Float = 8.4f): Axle {
        val sides = listOf("G ext", "G int", "D int", "D ext")
        return Axle(axle, xs.zip(sides) { (x0, x1), s -> Wheel("$axle $s", s, Box(x0, y, x1, y + h)) })
    }

    private fun single(axle: String, y: Float, xs: List<Pair<Float, Float>>, h: Float = 8.6f): Axle =
        Axle(axle, xs.zip(listOf("G", "D")) { (x0, x1), s -> Wheel("$axle $s", s, Box(x0, y, x1, y + h)) })

    private fun spare(label: String, position: String, box: Box) = Axle(label, listOf(Wheel(position, "RDS", box)), spare = true)

    private val porteurX = listOf(110.2f to 122.8f, 127.6f to 140.2f, 147.3f to 159.9f, 164.9f to 179.6f)
    private val remorqueX = listOf(111.6f to 124.2f, 129.0f to 141.6f, 148.4f to 161.0f, 165.9f to 179.9f)

    val vehicles = listOf(
        Vehicle(
            "Porteur / tracteur",
            listOf(
                single("1 AV", 455.1f, listOf(127.8f to 140.4f, 147.7f to 160.4f)),
                single("2 AV", 465.4f, listOf(128.0f to 140.6f, 147.3f to 159.9f)),
                twin("1 AR", 476.3f, porteurX),
                twin("2 AR", 486.9f, porteurX),
                twin("3 AR", 497.8f, porteurX),
                spare("5 AR/RDS", "RDS", Box(136.3f, 508.2f, 149.0f, 516.1f)),
            ),
        ),
        Vehicle(
            "Remorque / semi",
            listOf(
                twin("1 ESS", 525.7f, remorqueX),
                twin("2 ESS", 537.6f, remorqueX),
                twin("3 ESS", 548.3f, remorqueX),
                spare("RDS", "RDS ESS", Box(137.1f, 559.5f, 149.7f, 567.4f)),
            ),
        ),
    )

    val wheels: List<Wheel> = vehicles.flatMap { v -> v.axles.flatMap { it.wheels } }
    private val wheelsByPosition = wheels.associateBy { samePosition(it.position) }

    /** « 1 ar  g EXT » et « 1 AR G ext » désignent la même roue. */
    fun samePosition(position: String) = position.trim().replace(Regex("\\s+"), " ").lowercase()

    fun wheel(position: String): Wheel? = wheelsByPosition[samePosition(position)]

    /** Positions saisies dans les tableaux de pneus (montés ou démontés). */
    fun positions(values: Map<String, String>): Set<String> =
        TyreTable.entries.flatMapTo(mutableSetOf()) { t ->
            (1..TYRE_ROWS).mapNotNull { r -> values[PlKeys.tyre(t, r, "position")]?.trim()?.takeIf { it.isNotEmpty() } }
        }

    /** Roues cochées sur la fiche : touchées sur le schéma, ou avec un pneu monté ou démonté à leur position. */
    fun checkedWheels(values: Map<String, String>): List<Wheel> {
        val inTables = positions(values).mapTo(mutableSetOf()) { samePosition(it) }
        return wheels.filter { samePosition(it.position) in inTables || !values[PlKeys.wheel(it.position)].isNullOrBlank() }
    }

    override fun marks(values: Map<String, String>): List<Mark> = checkedWheels(values).map { w ->
        Mark(PlKeys.wheel(w.position), "Roue ${w.position}", w.box.centerX, w.box.centerY, w.box.height * 0.42f)
    }

    override fun label(key: String): String? =
        super.label(key) ?: key.removePrefix("pl.roue.").takeIf { key.startsWith("pl.roue.") }?.let { "Roue $it" }

    // ---------------------------------------------------------------- zones de saisie

    override val fields: List<Field> = buildList {
        // En-tête : date et n°, alignés sur « DATE: » et « N° »
        add(Field(K.DATE, "Date", Box(110f, 17f, 330f, 37f), 16f, minFontSize = 10f, firstLineY = 26f))
        add(Field(K.NUMERO_COMMANDE, "N°", Box(760f, 17f, 838f, 37f), 16f, minFontSize = 9f, firstLineY = 26f))

        // Intervention
        add(Field(K.MONTEUR, "Nom tech", Box(194f, 74f, 526f, 90f), 11f))
        add(Field(K.CLIENT_MANDATAIRE, "Nom du donneur d'ordre", Box(193f, 91.6f, 286f, 108f), 10f, maxLines = 2, minFontSize = 5.5f))
        add(Field(K.CLIENT_UTILISATEUR, "Nom du client", Box(357f, 91.6f, 526f, 108f), 11f, maxLines = 2, minFontSize = 5.5f))
        add(Field(K.UTILISATEUR_ADRESSE, "Adresse", Box(194f, 109.6f, 526f, 124.8f), 10f, minFontSize = 6f))
        add(Field(PlKeys.LIEU, "Lieu du dépannage", Box(194f, 126.4f, 526f, 140f), 10f, minFontSize = 6f))
        add(Field(PlKeys.KM_DEPART, "Km départ", Box(192f, 141.5f, 237f, 167.2f), 10f, HAlign.CENTER, minFontSize = 6f))
        add(Field(PlKeys.KM_ARRIVEE, "Km arrivée", Box(291f, 141.5f, 351f, 167.2f), 10f, HAlign.CENTER, minFontSize = 6f))
        add(Field(PlKeys.HEURE_DEPART, "Heure départ", Box(418f, 141.5f, 526f, 154f), 9.5f, HAlign.CENTER, minFontSize = 6f))
        add(Field(PlKeys.HEURE_ARRIVEE, "Heure arrivée", Box(418f, 155.6f, 526f, 167.2f), 9.5f, HAlign.CENTER, minFontSize = 6f))

        // Ligne « Dimensions pneus / AV-AR / Marque / Type / N/R/O / Qté »
        pneuColumns.forEach { (col, label, xs) ->
            add(Field(PlKeys.pneu(col), label, Box(xs.first, 199.2f, xs.second, 222.9f), 10f, HAlign.CENTER, maxLines = 2, minFontSize = 6f))
        }

        // Pneus montés / démontés
        for (t in TyreTable.entries) {
            tyreRows.getValue(t).forEachIndexed { n, (y0, y1) ->
                tyreColumns.forEach { (col, label, xs) ->
                    add(
                        Field(
                            PlKeys.tyre(t, n + 1, col), "$label (${t.title.lowercase()}, ligne ${n + 1})",
                            Box(xs.first, y0, xs.second, y1), 8f, HAlign.CENTER, minFontSize = 4.5f,
                        )
                    )
                }
            }
        }

        // Véhicule
        listOf(
            Triple(PlKeys.VEHICULE, "Véhicule", 73.9f to 90f),
            Triple(K.MARQUE, "Marque", 91.6f to 108f),
            Triple(K.TYPE, "Type", 109.6f to 124.8f),
            Triple(K.SERIE, "N° IMT / série", 126.4f to 140f),
            Triple(K.PARC, "N° parc", 141.5f to 167.2f),
            Triple(K.HORAMETRE, "Km / heure", 168.8f to 185.7f),
        ).forEach { (key, label, ys) -> add(Field(key, label, Box(597f, ys.first, 783f, ys.second), 11f, minFontSize = 6f)) }

        // Fournitures et services
        fournitures.forEach { (item, label, ys) ->
            add(Field(PlKeys.fourniture(item, "dimensions"), "$label – dimensions", Box(596f, ys.first, 670f, ys.second), 8.5f, HAlign.CENTER, minFontSize = 5f))
            add(Field(PlKeys.fourniture(item, "qte"), "$label – quantité", Box(675f, ys.first, 783f, ys.second), 8.5f, HAlign.CENTER, minFontSize = 5f))
        }
        services.forEach { (item, label, ys) ->
            add(Field(PlKeys.service(item), label, Box(675f, ys.first, 783f, ys.second), 9f, HAlign.CENTER, minFontSize = 5f))
        }

        // Observations (sous l'avertissement imprimé) et signataire
        add(Field(K.OBSERVATIONS, "Observations technicien", Box(244f, 518.5f, 526f, 577f), 10f, maxLines = 5, minFontSize = 6f))
        add(Field(K.SIGNATAIRE, "Nom du signataire", Box(533f, 507f, 783f, 519f), 9.5f, HAlign.CENTER, minFontSize = 6f))
    }

    override val fieldsByKey: Map<String, Field> = fields.associateBy { it.key }

    override val choices: List<Choice> = emptyList()

    override val crossHalf: Float = 4f

    override val signatureBox: Box = Box(570f, 521f, 750f, 586f)

    /** Cases de pneus, fournitures et services : un bon complet en a au moins une de remplie. */
    val workKeys: List<String> =
        pneuColumns.map { PlKeys.pneu(it.first) } +
            TyreTable.entries.flatMap { t -> (1..TYRE_ROWS).flatMap { r -> tyreColumns.map { PlKeys.tyre(t, r, it.first) } } } +
            fournitures.flatMap { (item, _, _) -> listOf(PlKeys.fourniture(item, "dimensions"), PlKeys.fourniture(item, "qte")) } +
            services.map { PlKeys.service(it.first) }
}
