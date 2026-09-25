package fr.vippneus.intervention

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import androidx.test.core.app.ApplicationProvider
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.common.PDRectangle
import com.tom_roush.pdfbox.text.PDFTextStripper
import fr.vippneus.intervention.data.Attachment
import fr.vippneus.intervention.data.AttachmentKind
import fr.vippneus.intervention.data.FieldAdjust
import fr.vippneus.intervention.data.Intervention
import fr.vippneus.intervention.data.InterventionType
import fr.vippneus.intervention.data.Overlay
import fr.vippneus.intervention.data.OverlayKind
import fr.vippneus.intervention.data.SignatureData
import fr.vippneus.intervention.data.SourceDoc
import fr.vippneus.intervention.pdf.FpsTemplate
import fr.vippneus.intervention.pdf.FpsTemplate.K
import fr.vippneus.intervention.pdf.PdfExporter
import fr.vippneus.intervention.pdf.PdfPages
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File
import kotlin.math.PI
import kotlin.math.sin

/** Génère de vrais PDF (dossier app/build/test-output) pour vérifier le rendu. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35])
class PdfExportTest {
    private lateinit var context: Context
    private val out = File(System.getProperty("user.dir"), "build/test-output").apply { mkdirs() }

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        PDFBoxResourceLoader.init(context)
    }

    private fun signature(): SignatureData {
        val strokes = listOf(
            (0..60).flatMap { t -> listOf(20f + t * 9f, 120f + 45f * sin(t / 60.0 * 4 * PI).toFloat()) },
            listOf(60f, 60f, 140f, 200f, 260f, 80f, 420f, 170f, 540f, 110f),
        )
        return SignatureData(strokes, 600f, 230f, 6f)
    }

    /** Fiche remplie avec des données fictives, dans l'esprit des exemples fournis. */
    private fun sampleFps(dir: File) = Intervention(
        id = "test-fps",
        type = InterventionType.FPS,
        createdAt = 0L,
        values = mapOf(
            K.CLIENT_MANDATAIRE to "Loc Manutention",
            K.MANDATAIRE_ADRESSE to "12 rue des Artisans, Reims",
            K.MANDATAIRE_CP to "51100",
            K.NUMERO_COMMANDE to "1234567",
            K.MONTEUR to "Chris.E",
            K.DATE to "24/08/26",
            K.CLIENT_UTILISATEUR to "Entrepôts Dupont Logistique\nQuai n°4",
            K.UTILISATEUR_ADRESSE to "Zone industrielle, Laon",
            K.UTILISATEUR_CP to "02000",
            K.MARQUE to "Hyster",
            K.TYPE to "S4.5FT",
            K.SERIE to "123456",
            K.PARC to "P-17",
            K.HORAMETRE to "1293",
            K.AV_FOURNI to "oui",
            K.pneu("av", "dimensions") to "22x12x16",
            K.pneu("av", "marque") to "Sm",
            K.pneu("av", "profil") to "Nm",
            K.pneu("av", "type") to "Bandage",
            K.pneu("av", "quantite") to "2",
            K.AR_FOURNI to "oui",
            K.pneu("ar", "dimensions") to "18x8x12 1/8",
            K.pneu("ar", "marque") to "Gl",
            K.pneu("ar", "profil") to "Nm",
            K.pneu("ar", "type") to "Bandage",
            K.pneu("ar", "quantite") to "2",
            K.prestation("depose", "autres") to "4",
            K.prestation("depressage", "autres") to "4",
            K.prestation("dechets", "autres") to "4",
            K.prestation("depose", "8") to "2",
            K.DEPLACEMENT to "non",
            K.KMS to "86",
            K.SERRAGE_AV to "650",
            K.SERRAGE_AR to "",
            K.SERRAGE_AR_REMARQUE to "Démonté et remonté par le client",
            K.JANTES_LUSTREES to "AVG",
            K.JANTES_AUTRES to "RAS",
            K.OBSERVATIONS to "Pneus AR usés jusqu'à la toile, remplacement conseillé. Serrage à contrôler après 10 h d'utilisation 🙂",
            K.SIGNATAIRE to "Ok vu avec le client…\nJérôme, technicien",
        ),
        signature = signature(),
        overlays = listOf(
            Overlay("o1", OverlayKind.TEXT, 70f, 140f, "Mention libre\nsur deux lignes", 12f),
            Overlay("o2", OverlayKind.CROSS, 150f, 170f, width = 10f, height = 10f),
        ),
    ).also { dir.mkdirs() }

    @Test
    fun ficheFps_avecPiecesJointes() {
        val dir = File(out, "fps").apply { deleteRecursively(); mkdirs() }
        var i = sampleFps(dir)

        // Pièce jointe PDF : une fiche générée juste avant
        val attPdf = File(dir, "pj-commande.pdf")
        PdfExporter(context).export(i, dir, attPdf, "PJ", "Test")
        // Pièce jointe image : une « photo » paysage
        val photo = File(dir, "pj-photo.png")
        Bitmap.createBitmap(1600, 900, Bitmap.Config.ARGB_8888).apply {
            val c = Canvas(this)
            c.drawColor(Color.rgb(90, 120, 160))
            c.drawCircle(800f, 450f, 300f, Paint().apply { color = Color.rgb(30, 30, 30) })
            photo.outputStream().use { compress(Bitmap.CompressFormat.PNG, 100, it) }
        }
        i = i.copy(
            attachments = listOf(
                Attachment("a1", attPdf.name, "Bon de commande.pdf", AttachmentKind.PDF, 1),
                Attachment("a2", photo.name, "Photo", AttachmentKind.IMAGE, 1),
            ),
        )
        val result = File(out, "fiche-fps.pdf")
        PdfExporter(context).export(i, dir, result, "Fiche test", "Chris.E")

        PDDocument.load(result).use { doc ->
            assertEquals(3, doc.numberOfPages)
            val first = doc.getPage(0).mediaBox
            assertEquals(FpsTemplate.PAGE_W, first.width, 0.1f)
            assertEquals(FpsTemplate.PAGE_H, first.height, 0.1f)
            val p3 = doc.getPage(2).mediaBox
            assertTrue("photo paysage -> page paysage", p3.width > p3.height)
            val text = PDFTextStripper().apply { startPage = 1; endPage = 1 }.getText(doc)
            assertTrue(text.contains("1234567"))
            assertTrue(text.contains("Hyster"))
            assertTrue(text.contains("Démonté et remonté par le client"))
            assertEquals("Fiche test", doc.documentInformation.title)
        }
    }

    @Test
    fun ficheFps_ajustementsManuels() {
        val dir = File(out, "fps-ajuste").apply { deleteRecursively(); mkdirs() }
        val i = sampleFps(dir).copy(
            adjust = mapOf(
                K.NUMERO_COMMANDE to FieldAdjust(dx = 20f, dy = 2f, fontSize = 34f),
                K.SIGNATURE to FieldAdjust(dx = -60f, scale = 0.7f),
            ),
        )
        val result = File(out, "fiche-fps-ajustee.pdf")
        PdfExporter(context).export(i, dir, result, "Fiche ajustée", "Chris.E")
        PDDocument.load(result).use { assertEquals(1, it.numberOfPages) }
    }

    /** Document client dont la page 1 est pivotée et recadrée : les ajouts doivent rester droits et bien placés. */
    @Test
    fun documentClient_pagePivotee() {
        val dir = File(out, "doc").apply { deleteRecursively(); mkdirs() }
        // Source : 2 pages ; la page 1 est pivotée de 90° et possède une CropBox décalée
        val base = File(dir, "base.pdf")
        PdfExporter(context).export(sampleFps(dir).copy(overlays = emptyList()), dir, base, "Base", "Test")
        val source = File(dir, "source.pdf")
        PDDocument.load(base).use { doc ->
            val p = doc.getPage(0)
            p.rotation = 90
            p.cropBox = PDRectangle(20f, 30f, 555f, 780f)
            doc.addPage(com.tom_roush.pdfbox.pdmodel.PDPage(PDRectangle.A4)) // 2e page quelconque
            doc.save(source)
        }
        val info = PdfPages.info(source)
        assertEquals(780f, info.width, 0.1f) // largeur affichée = hauteur de la CropBox
        assertEquals(555f, info.height, 0.1f)

        val i = Intervention(
            id = "test-doc",
            type = InterventionType.DOCUMENT,
            createdAt = 0L,
            source = SourceDoc(source.name, "Feuille de tâche.pdf", 2, info.width, info.height),
            overlays = listOf(
                Overlay("t1", OverlayKind.TEXT, 30f, 25f, "EN HAUT À GAUCHE", 20f),
                Overlay("t2", OverlayKind.TEXT, info.width - 260f, info.height - 60f, "En bas à droite\n4559 h – 180 Nm", 16f),
                Overlay("c1", OverlayKind.CROSS, info.width / 2f - 10f, info.height / 2f - 10f, width = 20f, height = 20f),
                Overlay("s1", OverlayKind.SIGNATURE, 300f, 380f, width = 200f, height = 77f, signature = signature()),
            ),
            includeOriginal = true,
        )
        val result = File(out, "document-client.pdf")
        PdfExporter(context).export(i, dir, result, "Document", "Test")
        PDDocument.load(result).use { doc ->
            assertEquals(4, doc.numberOfPages) // 2 pages complétées + 2 pages d'origine
            assertEquals(90, doc.getPage(0).rotation)
            val text = PDFTextStripper().apply { startPage = 1; endPage = 1 }.getText(doc)
            assertTrue(text.contains("EN HAUT À GAUCHE"))
        }
    }
}
