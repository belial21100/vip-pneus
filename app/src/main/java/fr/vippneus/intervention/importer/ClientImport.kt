package fr.vippneus.intervention.importer

import fr.vippneus.intervention.pdf.PdfExporter
import java.io.File

/**
 * Analyse du PDF reçu d'un client :
 * - bon de commande (Mac2, Conti…) -> fiche d'intervention FPS pré-remplie, le document suit ;
 * - feuille de tâche Mastra -> on écrit directement dessus (page 1), l'original suit (page 2).
 */
object ClientImport {
    /**
     * Résultat de l'analyse : [page] = page du PDF qui porte la feuille reconnue (0 = la 1re),
     * [reason] = pourquoi le document n'est pas reconnu (message pour le technicien).
     */
    data class Result(val plan: ImportPlan, val page: Int = 0, val reason: String? = null)

    fun analyze(pdf: File, technicien: String, today: String): ImportPlan = inspect(pdf, technicien, today).plan

    fun inspect(pdf: File, technicien: String, today: String): Result {
        val read = read(pdf)
        val text = read ?: DocText(emptyList())
        val plan = ClientDocs.analyze(text, technicien, today)
        if (plan !is ImportPlan.Inconnu) return Result(plan)
        // Feuille de tâche Mastra sur une autre page (ex. PDF déjà traité : page remplie puis original)
        for (p in text.runs.map { it.page }.distinct().filter { it > 0 }.sorted()) {
            val other = ClientDocs.analyze(DocText(text.pageRuns(p).map { it.copy(page = 0) }), technicien, today)
            if (other is ImportPlan.Feuille) return Result(other, page = p)
        }
        val reason = when {
            read == null -> "Le texte du document n'a pas pu être lu (fichier endommagé ?) : rien ne peut être repris automatiquement."
            text.runs.isEmpty() -> "Le document ne contient pas de texte lisible (photo ou document scanné) : rien ne peut être repris automatiquement."
            text.pageRuns(0).isEmpty() -> "La 1re page ne contient pas de texte lisible (photo ou document scanné)."
            else -> "Ce n'est pas un modèle connu (bon de commande Manuloc, Mobile Service Continental ou feuille de tâche Mastra) : les informations ne peuvent pas être reprises automatiquement."
        }
        return Result(plan, reason = reason)
    }

    /** Texte du PDF, ou null s'il n'a pas pu être lu. */
    private fun read(pdf: File): DocText? = try {
        PdfExporter.loadDecrypted(pdf).use { DocText.read(it) }
    } catch (_: Exception) {
        null
    }
}
