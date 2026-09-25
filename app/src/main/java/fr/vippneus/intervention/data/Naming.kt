package fr.vippneus.intervention.data

import fr.vippneus.intervention.pdf.FpsTemplate.K
import java.text.Normalizer
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/** Titres affichés et nom des fichiers PDF envoyés à la comptabilité. */
object Naming {
    private val FILE_DATE = DateTimeFormatter.ofPattern("dd-MM-yyyy", Locale.FRANCE)
    private val SHORT_DATE = DateTimeFormatter.ofPattern("dd/MM/yy", Locale.FRANCE)
    private val DATE_RE = Regex("""(\d{1,2})\s*[/.\-]\s*(\d{1,2})\s*[/.\-]\s*(\d{2,4})""")
    private val ISO_RE = Regex("""(\d{4})-(\d{1,2})-(\d{1,2})""")
    private val CP_RE = Regex("""(?<!\d)(\d{5})(?!\d)""")

    fun today(): String = LocalDate.now().format(SHORT_DATE)

    fun formatShort(date: LocalDate): String = date.format(SHORT_DATE)

    fun parseDate(text: String): LocalDate? {
        ISO_RE.find(text)?.let { m ->
            val (y, mo, d) = m.destructured
            runCatching { return LocalDate.of(y.toInt(), mo.toInt(), d.toInt()) }
        }
        val m = DATE_RE.find(text) ?: return null
        val (d, mo, yRaw) = m.destructured
        val y = if (yRaw.length <= 2) 2000 + yRaw.toInt() else yRaw.toInt()
        return runCatching { LocalDate.of(y, mo.toInt(), d.toInt()) }.getOrNull()
    }

    /** Département (2 premiers chiffres du premier code postal trouvé). */
    fun departement(vararg texts: String): String? =
        texts.firstNotNullOfOrNull { CP_RE.find(it)?.groupValues?.get(1)?.substring(0, 2) }

    /** Ville : partie après la dernière virgule d'une adresse (« 15 rue X, Laon » -> « Laon »). */
    fun ville(adresse: String): String =
        adresse.replace('\n', ',').split(',').map { it.trim() }.lastOrNull { it.isNotEmpty() }.orEmpty()

    fun interventionDate(i: Intervention): LocalDate {
        val raw = when (i.type) {
            InterventionType.FPS -> i.value(K.DATE)
            InterventionType.DOCUMENT -> i.value(DocKeys.DATE)
        }
        return parseDate(raw) ?: Instant.ofEpochMilli(i.createdAt).atZone(ZoneId.systemDefault()).toLocalDate()
    }

    fun defaultFileName(i: Intervention, initiales: String): String {
        val date = interventionDate(i).format(FILE_DATE)
        val parts: List<String>
        val dept: String?
        when (i.type) {
            InterventionType.FPS -> {
                val cp = i.value(K.UTILISATEUR_CP)
                val ville = ville(i.value(K.UTILISATEUR_ADRESSE))
                dept = departement(cp, i.value(K.UTILISATEUR_ADRESSE), i.value(K.CLIENT_UTILISATEUR), i.value(K.MANDATAIRE_CP), i.value(K.MANDATAIRE_ADRESSE))
                parts = listOf(
                    i.value(K.CLIENT_MANDATAIRE).uppercase(Locale.FRANCE),
                    i.value(K.CLIENT_UTILISATEUR).uppercase(Locale.FRANCE),
                    cp.uppercase(Locale.FRANCE),
                    ville.uppercase(Locale.FRANCE),
                    i.value(K.NUMERO_COMMANDE),
                )
            }
            InterventionType.DOCUMENT -> {
                dept = departement(i.value(DocKeys.CP), i.value(DocKeys.VILLE), i.value(DocKeys.SITE))
                parts = listOf(
                    i.value(DocKeys.CLIENT).uppercase(Locale.FRANCE),
                    i.value(DocKeys.SITE).uppercase(Locale.FRANCE),
                    i.value(DocKeys.CP),
                    i.value(DocKeys.VILLE).uppercase(Locale.FRANCE),
                    i.value(DocKeys.REFERENCE),
                )
            }
        }
        val infos = parts.map { it.replace('\n', ' ').trim() }.filter { it.isNotEmpty() }
            .fold(mutableListOf<String>()) { acc, p ->
                // Évite les répétitions (ex. code postal ou ville déjà dans le nom du site)
                if (acc.none { it.contains(p, ignoreCase = true) }) acc += p
                acc
            }
        val body = (infos + date + initiales.trim()).filter { it.isNotEmpty() }.joinToString(" ")
        return sanitize(stripAccents(if (dept != null) "$dept- $body" else body))
    }

    /** « RÉSEAUX » -> « RESEAUX » : noms de fichiers sans accents, comme ceux utilisés jusqu'ici. */
    fun stripAccents(s: String): String =
        Normalizer.normalize(s, Normalizer.Form.NFD).replace(Regex("\\p{M}+"), "")
            .replace("Œ", "OE").replace("œ", "oe").replace("Æ", "AE").replace("æ", "ae")

    fun fileName(i: Intervention, initiales: String): String =
        sanitize(i.fileName.ifBlank { defaultFileName(i, initiales) })

    fun sanitize(name: String): String {
        val cleaned = name.replace(Regex("""[\\/:*?"<>|\r\n\t]"""), " ")
            .replace(Regex("\\s+"), " ")
            .trim()
            .trimEnd('.', ' ')
            .removeSuffix(".pdf").removeSuffix(".PDF")
            .take(150)
            .trim()
        return cleaned.ifEmpty { "Bon d'intervention" }
    }

    fun title(i: Intervention): String = when (i.type) {
        InterventionType.FPS -> listOf(i.value(K.CLIENT_MANDATAIRE), i.value(K.CLIENT_UTILISATEUR))
            .map { it.replace('\n', ' ').trim() }.filter { it.isNotEmpty() }.joinToString(" – ")
            .ifEmpty { "Fiche d'intervention sans nom" }
        InterventionType.DOCUMENT -> listOf(i.value(DocKeys.CLIENT), i.value(DocKeys.SITE))
            .map { it.trim() }.filter { it.isNotEmpty() }.joinToString(" – ")
            .ifEmpty { i.source?.name ?: "Document client" }
    }

    fun reference(i: Intervention): String = when (i.type) {
        InterventionType.FPS -> i.value(K.NUMERO_COMMANDE)
        InterventionType.DOCUMENT -> i.value(DocKeys.REFERENCE)
    }.trim()

    fun typeLabel(t: InterventionType) = when (t) {
        InterventionType.FPS -> "Fiche presse mobile"
        InterventionType.DOCUMENT -> "Document client"
    }
}
