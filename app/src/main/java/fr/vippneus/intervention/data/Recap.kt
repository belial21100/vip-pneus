package fr.vippneus.intervention.data

import fr.vippneus.intervention.pdf.FpsTemplate.K
import java.text.SimpleDateFormat
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.util.Date
import java.util.Locale

/**
 * Récapitulatif d'un mois pour la comptabilité : une ligne par bon (date, client, n° de commande,
 * fichier PDF, statut d'envoi, ce qui manque). Fichier CSV qui s'ouvre directement dans Excel :
 * séparateur « ; », UTF-8 avec BOM, fins de ligne Windows.
 */
object Recap {
    private val DAY = DateTimeFormatter.ofPattern("dd/MM/yyyy", Locale.FRANCE)
    private val MONTH = DateTimeFormatter.ofPattern("MMMM yyyy", Locale.FRANCE)

    val HEADER = listOf(
        "Date", "Type", "Client", "Site", "Code postal", "Ville", "N° de commande / référence",
        "Fichier PDF", "Statut", "Envoyé le", "Manque",
    )

    /** « septembre 2026 ». */
    fun monthLabel(month: YearMonth): String = month.format(MONTH)

    /** Bons dont l'intervention date de [month], du plus ancien au plus récent. */
    fun bonsOf(all: List<Intervention>, month: YearMonth): List<Intervention> =
        all.filter { YearMonth.from(Naming.interventionDate(it)) == month }
            .sortedWith(compareBy({ Naming.interventionDate(it) }, { it.createdAt }))

    fun rows(bons: List<Intervention>, initiales: String): List<List<String>> = bons.map { i ->
        val fps = i.type == InterventionType.FPS
        val status = i.displayStatus()
        val manque = when (status) {
            DisplayStatus.ENVOYE -> emptyList()
            DisplayStatus.INCOMPLET -> i.sentMissing
            else -> Completion.missing(i).map { it.label }
        }
        listOf(
            Naming.interventionDate(i).format(DAY),
            Naming.kindLabel(i),
            i.value(if (fps) K.CLIENT_MANDATAIRE else DocKeys.CLIENT),
            i.value(if (fps) K.CLIENT_UTILISATEUR else DocKeys.SITE),
            i.value(if (fps) K.UTILISATEUR_CP else DocKeys.CP),
            if (fps) Naming.ville(i.value(K.UTILISATEUR_ADRESSE)) else i.value(DocKeys.VILLE),
            Naming.reference(i),
            Naming.fileName(i, initiales) + ".pdf",
            status.label,
            i.sentAt?.let { SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.FRANCE).format(Date(it)) }.orEmpty(),
            manque.joinToString(", "),
        )
    }

    fun csv(bons: List<Intervention>, initiales: String): String =
        "\uFEFF" + (listOf(HEADER) + rows(bons, initiales)).joinToString("") { row ->
            row.joinToString(";") { cell(it) } + "\r\n"
        }

    /** Une cellule : sur une ligne, entre guillemets si elle contient « ; » ou « " ». */
    private fun cell(value: String): String {
        val v = value.replace("\r\n", " ").replace('\n', ' ').replace('\r', ' ').trim()
        return if (v.contains(';') || v.contains('"')) "\"" + v.replace("\"", "\"\"") + "\"" else v
    }
}
