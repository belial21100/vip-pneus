package fr.vippneus.intervention

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import fr.vippneus.intervention.data.DocKeys
import fr.vippneus.intervention.data.Intervention
import fr.vippneus.intervention.data.InterventionType
import fr.vippneus.intervention.data.SourceDoc
import fr.vippneus.intervention.importer.ClientImport
import fr.vippneus.intervention.importer.DocText
import fr.vippneus.intervention.importer.ImportPlan
import fr.vippneus.intervention.pdf.FpsTemplate.K
import fr.vippneus.intervention.pdf.PdfExporter
import fr.vippneus.intervention.pdf.PdfPages
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

/** Reconnaissance des documents clients et pré-remplissage (documents fictifs). */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35])
class ImportTest {
    private lateinit var context: Context
    private val out = File(System.getProperty("user.dir"), "build/test-output/import").apply { mkdirs() }

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        PDFBoxResourceLoader.init(context)
    }

    private fun analyze(file: File): ImportPlan = ClientImport.analyze(file, "Chris.E", "25/09/26")

    @Test
    fun bonDeCommandeManuloc() {
        val f = File(out, "manuloc.pdf").also { FakeDocs.manuloc(it) }
        val plan = analyze(f)
        assertTrue(plan is ImportPlan.Fiche)
        val v = (plan as ImportPlan.Fiche).values
        assertEquals("7654321", v[K.NUMERO_COMMANDE])
        assertEquals("LOC TEST", v[K.CLIENT_MANDATAIRE])
        assertEquals("51100", v[K.MANDATAIRE_CP])
        assertEquals("ENTREPOT TEST LOGISTIQUE", v[K.CLIENT_UTILISATEUR])
        assertEquals("Rue des Essais, LAON", v[K.UTILISATEUR_ADRESSE])
        assertEquals("02000", v[K.UTILISATEUR_CP])
        assertEquals("123456", v[K.SERIE])
        assertEquals("S4.5FT", v[K.TYPE])
        assertEquals("22x12x16", v[K.pneu("av", "dimensions")])
        assertEquals("Trelleborg", v[K.pneu("av", "marque")])
        assertEquals("Bandage", v[K.pneu("av", "type")])
        assertEquals("2", v[K.pneu("av", "quantite")])
        assertEquals("18x8x12 1/8", v[K.pneu("ar", "dimensions")])
        // jantes de 16" et 12 1/8" : colonne « Autres », comme sur les fiches remplies à la main
        assertEquals("4", v[K.prestation("depose", "autres")])
        assertEquals("4", v[K.prestation("dechets", "autres")])
        assertEquals("Chris.E", v[K.MONTEUR])
        assertEquals("25/09/26", v[K.DATE])
    }

    @Test
    fun mobileServiceContinental() {
        val f = File(out, "conti.pdf").also { FakeDocs.continental(it) }
        val plan = analyze(f) as ImportPlan.Fiche
        val v = plan.values
        assertEquals("900000001", v[K.NUMERO_COMMANDE])
        assertEquals("MANUTENTION TEST", v[K.CLIENT_MANDATAIRE])
        assertEquals("Plateforme Test", v[K.CLIENT_UTILISATEUR])
        assertEquals("10 rue des Tests ZAC Exemple, Saint-Quentin", v[K.UTILISATEUR_ADRESSE])
        assertEquals("02100", v[K.UTILISATEUR_CP])
        assertEquals("E16", v[K.TYPE])
        assertEquals("ABC123DEF456", v[K.SERIE])
        assertEquals("12345", v[K.PARC])
        assertEquals("18x7x8", v[K.pneu("av", "dimensions")])
        assertEquals("SC20+", v[K.pneu("av", "profil")])
        assertEquals("SIT", v[K.pneu("av", "type")])
        assertEquals("2", v[K.pneu("av", "quantite")])
        assertEquals("2", v[K.prestation("depose", "8")])
        assertEquals("2", v[K.prestation("depressage", "8")])
    }

    @Test
    fun feuilleDeTacheInterfit_champsPlacesAutomatiquement() {
        val f = File(out, "interfit.pdf").also { FakeDocs.interfit(it) }
        val plan = analyze(f) as ImportPlan.Feuille
        // Seulement les cases que remplit le technicien (le reste : texte libre sur la page)
        assertEquals(
            listOf("if.compteur", "if.couple", "if.monteur", "if.date", "if.recuPar"),
            plan.template.fields.map { it.key },
        )
        assertTrue(plan.template.fields.all { it.required })
        assertNotNull(plan.template.signature)
        // Client final : encart aligné sur la colonne « Commentaires », juste au-dessus du trait
        val panel = plan.template.panel!!
        assertEquals(listOf("if.clientFinal.nom", "if.clientFinal.adresse"), panel.lines.map { it.key })
        assertEquals(422.1f, panel.left, 0.5f)
        assertEquals(426.2f, panel.bottom, 0.5f)
        val compteur = plan.template.fields.first { it.key == "if.compteur" }
        assertEquals(136f, compteur.left, 2f) // cellule à droite de « Lecture du compteur »
        assertEquals("Chris.E", plan.values["if.monteur"])
        assertEquals("25/09/26", plan.values["if.date"])
        assertEquals("MASTRA", plan.values[DocKeys.CLIENT])
        assertEquals("ESAT DU PARC (VILLE-TEST 54000)", plan.values[DocKeys.SITE])
        assertEquals("54000", plan.values[DocKeys.CP])
        assertEquals("VILLE-TEST", plan.values[DocKeys.VILLE])
        assertEquals("JobSheet_1234567", plan.values[DocKeys.REFERENCE])
        assertEquals("Préconisé : 159 Nm (± 39 Nm)", plan.hints["if.couple"])

        // Le PDF final porte les valeurs aux bons endroits
        val work = File(out, "interfit-work").apply { deleteRecursively(); mkdirs() }
        f.copyTo(File(work, "source.pdf"), overwrite = true)
        val info = PdfPages.info(f)
        val i = Intervention(
            "t", InterventionType.DOCUMENT, 0L,
            values = plan.values + mapOf(
                "if.compteur" to "4559", "if.couple" to "180", "if.recuPar" to "M. Martin",
                "if.clientFinal.nom" to "ESAT TEST", "if.clientFinal.adresse" to "3 allée des Essais",
            ),
            template = plan.template,
            source = SourceDoc("source.pdf", "interfit.pdf", info.pageCount, info.width, info.height),
        )
        val result = File(out, "interfit-rempli.pdf")
        PdfExporter(context).export(i, work, result, "Test", "Chris.E")
        // Page 1 : document rempli par le technicien ; page 2 : document d'origine
        assertEquals(2, PdfPages.info(result).pageCount)
        val text = PdfExporter.loadDecrypted(result).use { DocText.read(it) }
        assertTrue(text.pageRuns(1).none { it.text == "4559 h" })
        assertNotNull(text.pageRuns(1).firstOrNull { it.text.startsWith("Lecture du compteur") })
        val compteurRun = text.runs.first { it.text == "4559 h" }
        assertEquals(0, compteurRun.page)
        assertTrue(compteurRun.x0 in 130f..145f && compteurRun.baseline in 478f..492f)
        assertNotNull(text.runs.firstOrNull { it.text == "180 Nm" })
        assertNotNull(text.runs.firstOrNull { it.text == "Chris.E" })
        // Encart du client final dans la case au-dessus de « Commentaires », sans la mention « Client final »
        assertTrue(text.runs.none { it.text.contains("Client final") })
        val nom = text.runs.first { it.page == 0 && it.text == "ESAT TEST" }
        val adresse = text.runs.first { it.page == 0 && it.text == "3 allée des Essais" }
        assertEquals(427.1f, nom.x0, 1f)
        assertTrue(nom.baseline < adresse.baseline && adresse.baseline < 426f)
    }

    @Test
    fun documentInconnu() {
        val f = File(out, "autre.pdf").also { FakeDocs.other(it) }
        val plan = analyze(f)
        assertTrue(plan is ImportPlan.Inconnu)
        assertEquals("Chris.E", (plan as ImportPlan.Inconnu).values[K.MONTEUR])
        assertTrue(ClientImport.inspect(f, "Chris.E", "25/09/26").readable)
    }

    @Test
    fun documentScanne_sansTexte() {
        // Photo ou scan : rien à lire, le document s'ouvrira à signer (avec un message)
        val f = File(out, "scan.pdf").also { FakeDocs.scanned(it) }
        val r = ClientImport.inspect(f, "Chris.E", "25/09/26")
        assertTrue(r.plan is ImportPlan.Inconnu)
        assertFalse(r.readable)
    }

    @Test
    fun feuilleMastraEnPage2_trouveeEtExtraite() {
        // Page 1 sans texte (feuille déjà traitée, photo...), feuille de tâche en page 2
        val f = File(out, "mastra-2-pages.pdf").also { FakeDocs.interfitAfterCover(it) }
        val r = ClientImport.inspect(f, "Chris.E", "25/09/26")
        assertEquals(1, r.page)
        val plan = r.plan as ImportPlan.Feuille
        assertEquals("Feuille de tâche Mastra", plan.docType)
        assertEquals("JobSheet_1234567", plan.values[DocKeys.REFERENCE])

        // Seule cette page est gardée comme document du client
        val single = File(out, "mastra-page-2.pdf")
        PdfPages.extractPage(f, r.page, single)
        assertEquals(1, PdfPages.pageCount(single))
        assertTrue(ClientImport.analyze(single, "Chris.E", "25/09/26") is ImportPlan.Feuille)
    }
}
