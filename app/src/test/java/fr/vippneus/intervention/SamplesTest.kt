package fr.vippneus.intervention

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.tom_roush.pdfbox.pdmodel.PDDocument
import fr.vippneus.intervention.data.Attachment
import fr.vippneus.intervention.data.AttachmentKind
import fr.vippneus.intervention.data.Intervention
import fr.vippneus.intervention.data.InterventionType
import fr.vippneus.intervention.data.SourceDoc
import fr.vippneus.intervention.importer.ClientImport
import fr.vippneus.intervention.importer.ImportPlan
import fr.vippneus.intervention.pdf.PdfExporter
import fr.vippneus.intervention.pdf.PdfPages
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

/**
 * Vérification sur de vrais documents clients, conservés hors du dépôt (données personnelles).
 * Lancer avec la variable d'environnement VIP_SAMPLES_DIR = dossier contenant les PDF.
 * Pour chaque PDF, on analyse le fichier entier, puis sa dernière page seule (le document tel
 * qu'envoyé par le client) ;
 * les PDF remplis automatiquement sont écrits dans app/build/test-output/samples.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35])
class SamplesTest {

    @Test
    fun documentsClients() {
        val dir = System.getenv("VIP_SAMPLES_DIR")?.let(::File)
        assumeTrue("VIP_SAMPLES_DIR non défini", dir != null && dir.isDirectory)
        val context = ApplicationProvider.getApplicationContext<Context>()
        PDFBoxResourceLoader.init(context)
        val out = File(System.getProperty("user.dir"), "build/test-output/samples").apply { mkdirs() }

        dir!!.listFiles { f -> f.name.endsWith(".pdf", ignoreCase = true) }!!.sorted().forEachIndexed { index, pdf ->
            // Le PDF entier, tel que reçu (éventuellement déjà traité : page remplie puis original)
            val whole = ClientImport.inspect(pdf, "Chris.E", "25/09/26")
            println(
                "===== [$index/entier] ${pdf.name} -> ${whole.plan.javaClass.simpleName} (${whole.plan.docType}), " +
                    "page ${whole.page + 1}" + (whole.reason?.let { " : $it" } ?: ""),
            )
            if (pdf.name.contains("MASTRA", ignoreCase = true)) assertTrue(whole.plan is ImportPlan.Feuille)

            val variants = mutableListOf<Pair<String, File>>()
            PDDocument.load(pdf).use { doc ->
                val last = File(out, "src-$index-client.pdf")
                PDDocument().use { single ->
                    single.importPage(doc.getPage(doc.numberOfPages - 1))
                    single.save(last)
                }
                variants += "client" to last
            }
            for ((label, file) in variants) {
                val info = PdfPages.info(file)
                val plan = ClientImport.analyze(file, "Chris.E", "25/09/26")
                println("===== [$index/$label] ${pdf.name} -> ${plan.javaClass.simpleName} (${plan.docType})")
                val work = File(out, "work-$index-$label").apply { deleteRecursively(); mkdirs() }
                val copy = File(work, "source.pdf").also { file.copyTo(it, overwrite = true) }
                val intervention = when (plan) {
                    is ImportPlan.Fiche -> {
                        plan.values.toSortedMap().forEach { (k, v) -> println("   $k = $v") }
                        Intervention(
                            "s$index", InterventionType.FPS, 0L, values = plan.values,
                            attachments = listOf(Attachment("a", copy.name, pdf.name, AttachmentKind.PDF, info.pageCount)),
                        )
                    }
                    is ImportPlan.Feuille -> {
                        plan.values.toSortedMap().forEach { (k, v) -> println("   $k = $v") }
                        plan.hints.forEach { (k, v) -> println("   (indication) $k : $v") }
                        plan.template.fields.forEach { println("   champ ${it.key} @ (${it.left.toInt()}, ${it.top.toInt()})–(${it.right.toInt()}, ${it.bottom.toInt()}) ${it.fontSize} pt") }
                        // Valeurs de démonstration pour voir le placement
                        val demo = plan.values + mapOf(
                            "if.compteur" to "4559", "if.couple" to "180", "if.recuPar" to "M. Martin",
                            "if.clientFinal.nom" to "Site de démonstration", "if.clientFinal.adresse" to "1 rue de l'Exemple",
                        )
                        Intervention(
                            "s$index", InterventionType.DOCUMENT, 0L, values = demo, template = plan.template,
                            source = SourceDoc(copy.name, pdf.name, info.pageCount, info.width, info.height),
                        )
                    }
                    is ImportPlan.Inconnu -> null
                }
                if (intervention != null) {
                    val result = File(out, "resultat-$index-$label.pdf")
                    PdfExporter(context).export(intervention, work, result, "Test", "Chris.E")
                    println("   -> ${result.name} (${PdfPages.info(result).pageCount} pages)")
                }
            }
        }
    }
}
