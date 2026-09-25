package fr.vippneus.intervention.importer

import fr.vippneus.intervention.pdf.PdfExporter
import java.io.File

/**
 * Analyse du PDF importé. L'import ne crée jamais de fiche d'intervention :
 * - feuille de tâche Mastra -> on écrit directement dans ses cases (page 1), l'original suit ;
 * - tout autre document (bon de livraison…) -> document à signer.
 * Les bons de commande (Mac2, Conti…) sont aussi reconnus : joints à une fiche d'intervention,
 * ils la pré-remplissent ; importés seuls, leurs informations nomment le fichier.
 */
object ClientImport {
    /**
     * Résultat de l'analyse : [page] = page du PDF qui porte la feuille reconnue (0 = la 1re),
     * [readable] = le document contient du texte (faux pour une photo ou un document scanné).
     */
    data class Result(val plan: ImportPlan, val page: Int = 0, val readable: Boolean = true)

    fun analyze(pdf: File, technicien: String, today: String): ImportPlan = inspect(pdf, technicien, today).plan

    fun inspect(pdf: File, technicien: String, today: String): Result {
        val text = read(pdf) ?: DocText(emptyList())
        val plan = ClientDocs.analyze(text, technicien, today)
        if (plan is ImportPlan.Feuille) return Result(plan)
        // Feuille de tâche Mastra sur une autre page (ex. PDF déjà traité : page remplie puis original)
        for (p in text.runs.map { it.page }.distinct().filter { it > 0 }.sorted()) {
            val other = ClientDocs.analyze(DocText(text.pageRuns(p).map { it.copy(page = 0) }), technicien, today)
            if (other is ImportPlan.Feuille) return Result(other, page = p)
        }
        return Result(plan, readable = text.pageRuns(0).isNotEmpty())
    }

    /** Texte du PDF, ou null s'il n'a pas pu être lu. */
    private fun read(pdf: File): DocText? = try {
        PdfExporter.loadDecrypted(pdf).use { DocText.read(it) }
    } catch (_: Exception) {
        null
    }
}
