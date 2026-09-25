package fr.vippneus.intervention.importer

import fr.vippneus.intervention.pdf.PdfExporter
import java.io.File

/**
 * Analyse du PDF (une page) reçu d'un client :
 * - bon de commande (Mac2, Conti…) -> fiche d'intervention FPS pré-remplie, le document suit ;
 * - feuille de tâche Mastra -> on écrit directement dessus (page 1), l'original suit (page 2).
 */
object ClientImport {
    fun analyze(pdf: File, technicien: String, today: String): ImportPlan {
        val text = try {
            PdfExporter.loadDecrypted(pdf).use { DocText.read(it) }
        } catch (_: Exception) {
            DocText(emptyList())
        }
        return ClientDocs.analyze(text, technicien, today)
    }
}
