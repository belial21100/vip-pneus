package fr.vippneus.intervention.importer

import fr.vippneus.intervention.data.DocKeys
import fr.vippneus.intervention.data.DocTemplate
import fr.vippneus.intervention.data.PlacedBox
import fr.vippneus.intervention.data.PlacedField
import fr.vippneus.intervention.pdf.FpsTemplate.K
import java.util.Locale

/** Ce que l'on fait d'un PDF client après analyse. */
sealed interface ImportPlan {
    /** Nom du type de document reconnu (affiché au technicien). */
    val docType: String?

    /** Fiche FPS pré-remplie, le PDF client est joint après la fiche. */
    data class Fiche(
        override val docType: String?,
        val values: Map<String, String>,
    ) : ImportPlan

    /** Le document du client sert de fiche d'inter (Mastra) : on écrit directement dessus. */
    data class Feuille(
        override val docType: String,
        val template: DocTemplate,
        val values: Map<String, String>,
        val hints: Map<String, String>,
    ) : ImportPlan

    /** Document non reconnu : le technicien choisit. */
    data class Inconnu(val values: Map<String, String>) : ImportPlan {
        override val docType: String? = null
    }
}

/** Ligne de pneu extraite d'une commande. */
data class TireLine(
    val qty: Int,
    val axle: String?, // "av" / "ar" / null
    val dimensions: String,
    val rim: String?,
    val brand: String,
    val profile: String,
    val type: String,
)

/**
 * Reconnaissance des documents envoyés par les clients et pré-remplissage.
 * Les règles reprennent ce que les techniciens reportaient à la main sur les exemples fournis.
 */
object ClientDocs {

    fun analyze(text: DocText, technicien: String, today: String): ImportPlan {
        val base = buildMap {
            put(K.DATE, today)
            if (technicien.isNotBlank()) put(K.MONTEUR, technicien.trim())
        }
        Interfit.plan(text, technicien, today)?.let { return it }
        Manuloc.parse(text)?.let { return ImportPlan.Fiche("Bon de commande Manuloc", base + it) }
        Continental.parse(text)?.let { return ImportPlan.Fiche("Mobile Service Continental", base + it) }
        return ImportPlan.Inconnu(base + Generic.parse(text))
    }

    // ------------------------------------------------------------------ outils communs

    private val CP_CITY = Regex("""^(\d{5})\s+(.+)$""")
    private val CP_ANY = Regex("""(?<!\d)(\d{5})(?!\d)""")

    data class Address(val name: String?, val street: List<String>, val cp: String?, val city: String?) {
        /** « rue, ville » pour la ligne Adresse / Ville de la fiche. */
        fun streetAndCity(): String = (street.take(1) + listOfNotNull(city)).joinToString(", ")
    }

    /** Bloc d'adresse : nom, rue(s), « CP Ville » ; s'arrête aux téléphones, e-mails et autres étiquettes. */
    fun parseAddress(lines: List<String>, withName: Boolean = true): Address? {
        val clean = lines.map { it.replace(Regex("\\s+"), " ").trim() }.filter { it.isNotEmpty() }
            .takeWhile { l ->
                val n = DocText.normalize(l)
                !(l.contains(':') || l.contains('@') || n.startsWith("tel") || n.startsWith("fax") || n.startsWith("n° tva"))
            }
        if (clean.isEmpty()) return null
        var name: String? = null
        val street = mutableListOf<String>()
        var cp: String? = null
        var city: String? = null
        for ((idx, l) in clean.withIndex()) {
            val m = CP_CITY.find(l)
            if (m != null) {
                cp = m.groupValues[1]
                city = m.groupValues[2].trim()
                break
            }
            if (idx == 0 && withName && !l.first().isDigit()) name = l else street += l.removeSuffix(",").trim()
        }
        return Address(name, street, cp, city)
    }

    // ------------------------------------------------------------------ pneus

    private val brands = mapOf(
        "TREL" to "Trelleborg", "TRELLEBORG" to "Trelleborg",
        "CONTI" to "Continental", "CONTINENTAL" to "Continental",
        "SOLID" to "Solideal", "SOLIDEAL" to "Solideal", "CAMSO" to "Camso",
        "MICH" to "Michelin", "MICHELIN" to "Michelin", "GAL" to "Galaxy", "GALAXY" to "Galaxy",
        "BKT" to "BKT", "MITAS" to "Mitas", "NEXEN" to "Nexen", "ADDO" to "Addo", "TVS" to "TVS",
    )
    private val types = mapOf(
        "BD" to "Bandage", "BDG" to "Bandage", "BANDAGE" to "Bandage",
        "PN" to "Pneumatique", "PNEU" to "Pneumatique", "SE" to "Superélastique", "SUPERELASTIQUE" to "Superélastique",
    )
    private val DIM_IMPERIAL = Regex("""(?i)(\d+(?:[.,]\d+)?)\s*[x×]\s*(\d+(?:[.,]\d+)?)\s*[x×\-]\s*(\d+(?:[.,]\d+)?)(\s+\d+/\d+)?""")
    private val DIM_METRIC = Regex("""(\d{3})/(\d{2})\s*-\s*(\d{1,2}(?:[.,]\d+)?)""")
    private val DIM_DASH = Regex("""(\d+(?:[.,]\d+)?)\s*-\s*(\d{1,2})\b""")

    /** « - 2 BD TREL 22X12X16 SM NM AVANT » -> quantité, type, marque, dimensions, profil, essieu. */
    fun parseOrderTireLine(line: String): TireLine? {
        val l = line.trim().removePrefix("-").trim()
        val dim = DIM_IMPERIAL.find(l) ?: return null
        val qty = Regex("""^(\d{1,2})\s""").find(l)?.groupValues?.get(1)?.toIntOrNull() ?: return null
        val before = l.substring(0, dim.range.first).trim().split(Regex("\\s+")).drop(1).map { it.uppercase(Locale.FRANCE) }
        val after = l.substring(dim.range.last + 1).trim().split(Regex("\\s+")).filter { it.isNotEmpty() }
        val axle = after.lastOrNull()?.let { axleOf(it) }
        val rest = if (axle != null) after.dropLast(1) else after
        val type = before.firstNotNullOfOrNull { types[it] } ?: ""
        val brandCode = before.firstOrNull { it !in types }
        val brand = brandCode?.let { brands[it] ?: it } ?: ""
        val (a, b, c) = dim.destructured
        val frac = dim.groupValues[4].trim()
        val dims = "${a}x${b}x$c" + (if (frac.isNotEmpty()) " $frac" else "")
        return TireLine(qty, axle, dims, c + (if (frac.isNotEmpty()) " $frac" else ""), brand, rest.joinToString(" "), type)
    }

    private fun axleOf(word: String): String? = when (DocText.normalize(word).trimEnd('.')) {
        "avant", "av" -> "av"
        "arriere", "ar" -> "ar"
        else -> null
    }

    /** Colonne « pouces » du tableau des prestations d'après la taille de jante. */
    fun rimColumn(rim: String?): String {
        val r = rim?.trim()?.replace(',', '.') ?: return "autres"
        return when (r) {
            "8", "9", "10", "12", "15" -> r
            "8.0", "9.0", "10.0", "12.0", "15.0" -> r.substringBefore('.')
            else -> "autres"
        }
    }

    /** Remplit tableaux « Pneus AV / AR » et prestations (dépose, dépressage, déchets) à partir des pneus. */
    fun tireValues(tires: List<TireLine>): Map<String, String> {
        val v = mutableMapOf<String, String>()
        val byAxle = tires.groupBy { it.axle ?: "av" }
        for ((axle, list) in byAxle) {
            val first = list.first()
            v[K.pneu(axle, "dimensions")] = first.dimensions
            if (first.brand.isNotEmpty()) v[K.pneu(axle, "marque")] = first.brand
            if (first.profile.isNotEmpty()) v[K.pneu(axle, "profil")] = first.profile
            if (first.type.isNotEmpty()) v[K.pneu(axle, "type")] = first.type
            v[K.pneu(axle, "quantite")] = list.filter { it.dimensions == first.dimensions }.sumOf { it.qty }.toString()
        }
        val perColumn = tires.groupBy { rimColumn(it.rim) }.mapValues { (_, l) -> l.sumOf { it.qty } }
        for ((col, qty) in perColumn) {
            for (row in listOf("depose", "depressage", "dechets")) v[K.prestation(row, col)] = qty.toString()
        }
        return v
    }

    // ------------------------------------------------------------------ Manuloc (bon de commande)

    internal object Manuloc {
        fun parse(t: DocText): Map<String, String>? {
            if (!t.contains("bon de commande") || !(t.contains("manuloc") || t.contains("reference a rappeler"))) return null
            val v = mutableMapOf<String, String>()
            val lines = t.lines()

            // Référence de commande : « N° 1234567AOM/F/XXXX » -> 1234567
            val refIdx = lines.indexOfFirst { DocText.normalize(it).contains("reference a rappeler") }
            if (refIdx >= 0) {
                val zone = lines.subList(refIdx, minOf(lines.size, refIdx + 3)).joinToString(" ")
                Regex("""N°\s*(\d{4,})""").find(zone)?.let { v[K.NUMERO_COMMANDE] = it.groupValues[1] }
            }

            // Donneur d'ordre = adresse de facturation
            t.label("Adresse de facturation")?.let { lab ->
                parseAddress(t.belowLines(lab, maxLines = 6, maxStep = 4.2f))?.let { a ->
                    a.name?.let { v[K.CLIENT_MANDATAIRE] = it }
                    a.streetAndCity().takeIf { it.isNotBlank() }?.let { v[K.MANDATAIRE_ADRESSE] = it }
                    a.cp?.let { v[K.MANDATAIRE_CP] = it }
                }
            }
            // Client utilisateur = adresse de livraison
            t.label("Adresse de livraison")?.let { lab ->
                parseAddress(t.belowLines(lab, maxLines = 7, maxStep = 4.2f))?.let { a ->
                    a.name?.let { v[K.CLIENT_UTILISATEUR] = it }
                    a.streetAndCity().takeIf { it.isNotBlank() }?.let { v[K.UTILISATEUR_ADRESSE] = it }
                    a.cp?.let { v[K.UTILISATEUR_CP] = it }
                }
            }

            // Engin : « Engin n° M 123456 ( 123456) » puis le modèle juste en dessous (« S4.5FT »)
            lines.firstNotNullOfOrNull { Regex("""(?i)Engin\s*n[°o]\s*(?:[A-Z]\s+)?([A-Z0-9][A-Z0-9\-]*)""").find(it) }?.let {
                v[K.SERIE] = it.groupValues[1]
            }
            t.runs.firstOrNull { it.norm.contains("engin n") }?.let { engin ->
                t.runs.filter {
                    it.page == engin.page && it.baseline > engin.baseline + 2f && it.baseline < engin.baseline + 2.2f * engin.size &&
                        it.x0 > engin.x0 - 120f && it.x0 < engin.x1
                }.sortedBy { it.baseline }
                    .firstOrNull { it.text.trim().matches(Regex("""[A-Z0-9][A-Z0-9.\-/]{1,15}""")) }
                    ?.let { v[K.TYPE] = it.text.trim() }
            }

            v += tireValues(lines.mapNotNull { parseOrderTireLine(it) })
            return v
        }
    }

    // ------------------------------------------------------------------ Continental (Mobile Service)

    internal object Continental {
        private val POSITION = Regex("""^([1-9])([LR])([IO])?$""")
        private val BRACKET_DIM = Regex("""\((\d+(?:[.,]\d+)?)\s*[Xx]\s*(\d+(?:[.,]\d+)?)\s*-\s*(\d+(?:[.,]\d+)?)\)""")

        fun parse(t: DocText): Map<String, String>? {
            if (!t.contains("mobile service") || !(t.contains("conti") || t.contains("continental"))) return null
            val v = mutableMapOf<String, String>()
            val lines = t.lines()
            lines.firstNotNullOfOrNull { Regex("""(?i)Mobile Service\s*(\d{6,})""").find(it) }?.let {
                v[K.NUMERO_COMMANDE] = it.groupValues[1]
            }
            t.label("Rendez-vous")?.let { t.rightOf(it) }?.let { v[K.CLIENT_MANDATAIRE] = it }

            t.label("Localisation")?.let { lab ->
                val block = t.belowLines(lab, maxLines = 6, maxWidth = 150f, maxStep = 3.2f)
                val cpLine = block.firstOrNull { CP_CITY.matches(it) }
                val streetLine = block.firstOrNull { it.first().isDigit() && !CP_CITY.matches(it) && CP_ANY.find(it) != null && it.contains(' ') }
                val name = block.firstOrNull { l ->
                    l != cpLine && l != streetLine && !l.contains(':') && l.any { it.isLetter() } && !l.first().isDigit()
                }
                name?.let { v[K.CLIENT_UTILISATEUR] = it }
                val city = cpLine?.let { CP_CITY.find(it)?.groupValues?.get(2) }
                val street = streetLine?.replace(Regex(""",?\s*\d{5}\s*$"""), "")?.trim()
                listOfNotNull(street, city).joinToString(", ").takeIf { it.isNotBlank() }?.let { v[K.UTILISATEUR_ADRESSE] = it }
                (cpLine?.let { CP_CITY.find(it)?.groupValues?.get(1) } ?: streetLine?.let { CP_ANY.find(it)?.groupValues?.get(1) })
                    ?.let { v[K.UTILISATEUR_CP] = it }
            }

            t.label("Modèle", exact = true)?.let { t.rightOf(it) }?.let { m ->
                m.substringAfterLast('/').trim().takeIf { it.isNotEmpty() }?.let { v[K.TYPE] = it }
            }
            t.label("Immatriculation")?.let { t.rightOf(it) }?.let { v[K.SERIE] = it }
            t.label("N° de flotte")?.let { t.rightOf(it) }?.let { v[K.PARC] = it }

            // Pneus montés : une ligne par position (1LI, 1RI... 1 = essieu avant)
            val montes = t.label("Pneus montés")
            if (montes != null) {
                val tires = t.runs.filter { it.page == montes.page && it.baseline > montes.baseline }
                    .mapNotNull { r ->
                        val parts = r.text.trim().split(Regex("\\s+"), limit = 2)
                        val pos = POSITION.find(parts[0]) ?: return@mapNotNull null
                        val desc = if (parts.size > 1) parts[1] else t.firstRightOf(r, 30f)?.text ?: return@mapNotNull null
                        tireFromDescription(desc, if (pos.groupValues[1] == "1") "av" else "ar")
                    }
                v += tireValues(tires)
            }
            return v
        }

        /** « 180/70-8/4.33 SC20+ SIT 125 A5/4.33R8(18X7-8) » */
        fun tireFromDescription(desc: String, axle: String): TireLine? {
            val bracket = BRACKET_DIM.find(desc)
            val metric = DIM_METRIC.find(desc)
            val (dims, rim) = when {
                bracket != null -> "${bracket.groupValues[1]}x${bracket.groupValues[2]}x${bracket.groupValues[3]}" to bracket.groupValues[3]
                metric != null -> metric.value to metric.groupValues[3]
                else -> DIM_DASH.find(desc)?.let { it.value to it.groupValues[2] } ?: return null
            }
            val tokens = desc.trim().split(Regex("\\s+"))
            val profile = tokens.getOrNull(1)?.takeIf { it.any(Char::isLetter) } ?: ""
            val type = tokens.getOrNull(2)?.takeIf { it.all { c -> c.isLetter() } } ?: ""
            return TireLine(1, axle, dims, rim, "Continental", profile, type)
        }
    }

    // ------------------------------------------------------------------ Interfit (feuille de tâche)

    internal object Interfit {
        const val PREFIX = "if."

        /** Champs de la feuille, positionnés par rapport aux étiquettes imprimées (relevés sur une feuille remplie). */
        fun plan(t: DocText, technicien: String, today: String): ImportPlan.Feuille? {
            if (!t.contains("feuille de tache")) return null
            val p = 0
            val lecture = t.label("Lecture du compteur", page = p) ?: return null
            val monteur = t.label("Monteur", page = p, exact = true)
            val arrivee = t.label("Heure d'arrivée", page = p)
            val depart = t.label("Heure de départ", page = p)
            val terminee = t.label("Date terminée", page = p)
            val duree = t.label("Durée", page = p, exact = true)
            val couple = t.label("Couple de serrage", page = p)
            val instructions = t.label("Instructions spéciales", page = p)
            val date = t.pageRuns(p).filter { it.norm == "date" }.maxByOrNull { it.baseline }
            val recu = t.label("Reçu par", page = p)

            fun f(
                anchor: Run?, key: String, label: String, dx0: Float, dy0: Float, dx1: Float, dy1: Float, size: Float,
                hint: String = "", numeric: Boolean = false, unit: String = "", required: Boolean = false,
            ) = anchor?.let {
                PlacedField(
                    PREFIX + key, label, it.x0 + dx0, it.baseline + dy0, it.x0 + dx1, it.baseline + dy1, size,
                    hint = hint, numeric = numeric, unit = unit, required = required,
                )
            }

            val torque = t.lines(p).firstNotNullOfOrNull { Regex("""=\s*(\d+)\s*Nm\s*\(\+/-\s*(\d+)""").find(it) }
            val hour = t.lines(p).firstNotNullOfOrNull { Regex("""(?i)Horam[eè]tre\s*:?\s*(\d+)\s*h""").find(it) }

            // Ordre de saisie : d'abord ce que le technicien relève sur place
            val fields = listOfNotNull(
                f(
                    lecture, "compteur", "Lecture du compteur (h)", 114f, -10.6f, 202f, 9.2f, 17f,
                    hint = hour?.let { "Horamètre indiqué sur la demande : ${it.groupValues[1]} h" } ?: "",
                    numeric = true, unit = "h", required = true,
                ),
                f(
                    couple, "couple", "Couple de serrage (Nm)", 79.8f, -10f, 237.8f, 8f, 13f,
                    hint = torque?.let { "Préconisé : ${it.groupValues[1]} Nm (± ${it.groupValues[2]} Nm)" } ?: "",
                    numeric = true, unit = "Nm", required = true,
                ),
                f(monteur, "monteur", "Monteur", 91.7f, -11.6f, 188.7f, 8.2f, 17f, required = true),
                f(date, "date", "Date", 48f, -5.7f, 228f, 12.5f, 20f, required = true),
                f(arrivee, "heureArrivee", "Heure d'arrivée", 114f, -12.2f, 202f, 8.2f, 14f),
                f(depart, "heureDepart", "Heure de départ", 91.7f, -12.2f, 188.7f, 8.2f, 14f),
                f(terminee, "dateTerminee", "Date terminée", 114f, -11.9f, 202f, 10.2f, 14f),
                f(duree, "duree", "Durée", 91.7f, -11.3f, 188.7f, 10.8f, 14f),
                f(instructions, "lieu", "Lieu d'intervention (si différent)", 126f, -2.2f, 318f, 20f, 24f),
                f(instructions, "lieuAdresse", "Adresse du lieu d'intervention", 122f, 38.8f, 418f, 53.2f, 13f),
                f(recu, "recuPar", "Reçu par (nom du client)", 90f, 12f, 240f, 26f, 15f, required = true),
            )
            val signature = recu?.let { PlacedBox(it.x0 + 240f, it.baseline - 23f, it.x0 + 348f, it.baseline + 21f) }
            val template = DocTemplate("interfit", "Feuille de tâche Mastra", fields, signature)

            val values = mutableMapOf<String, String>()
            if (technicien.isNotBlank() && monteur != null) values[PREFIX + "monteur"] = technicien.trim()
            if (date != null) values[PREFIX + "date"] = today
            values[DocKeys.DATE] = today
            values[DocKeys.CLIENT] = "MASTRA"
            t.label("Livrer à", page = p)?.let { t.valueBlock(it).joinToString(" ") }?.takeIf { it.isNotBlank() }?.let {
                values[DocKeys.SITE] = it.replace(Regex("\\s+"), " ")
            }
            t.label("Adresse du site de livraison", page = p)?.let { t.valueBlock(it).joinToString(" ") }?.let { adr ->
                val parts = adr.split(',').map { it.trim() }.filter { it.isNotEmpty() }
                val cpIdx = parts.indexOfFirst { it.matches(Regex("""\d{5}""")) }
                if (cpIdx >= 0) {
                    values[DocKeys.CP] = parts[cpIdx]
                    parts.getOrNull(cpIdx - 1)?.takeIf { p -> p.none(Char::isDigit) }?.let { values[DocKeys.VILLE] = it }
                } else {
                    CP_ANY.find(adr)?.let { values[DocKeys.CP] = it.groupValues[1] }
                }
            }
            t.label("ID", page = p, exact = true)?.let { t.rightOf(it, 120f) }?.let { values[DocKeys.REFERENCE] = "JobSheet_$it" }

            val hints = fields.filter { it.hint.isNotEmpty() }.associate { it.key to it.hint }
            return ImportPlan.Feuille(template.name, template, values, hints)
        }
    }

    // ------------------------------------------------------------------ autres documents

    internal object Generic {
        private val ORDER = Regex(
            """(?i)(?:n[°o]\s*(?:de\s*)?commande|commande\s*n[°o]?|bon\s+de\s+commande\s*n[°o]?|cde\s*n?[°o]?|order\s*(?:no|n°|number)?)\s*[:.]?\s*([A-Z0-9][A-Z0-9/\-]{3,})"""
        )

        fun parse(t: DocText): Map<String, String> {
            val v = mutableMapOf<String, String>()
            t.lines().firstNotNullOfOrNull { ORDER.find(it) }?.let { m ->
                v[K.NUMERO_COMMANDE] = m.groupValues[1]
                v[DocKeys.REFERENCE] = m.groupValues[1]
            }
            return v
        }
    }
}
