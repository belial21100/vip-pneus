package fr.vippneus.intervention

import android.app.Application
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.net.Uri
import android.os.Looper
import android.view.View
import androidx.activity.ComponentActivity
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.click
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onLast
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.core.content.FileProvider
import androidx.test.core.app.ApplicationProvider
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import fr.vippneus.intervention.data.Completion
import fr.vippneus.intervention.data.DisplayStatus
import fr.vippneus.intervention.data.DocKeys
import fr.vippneus.intervention.data.InterventionType
import fr.vippneus.intervention.data.Naming
import fr.vippneus.intervention.data.Overlay
import fr.vippneus.intervention.data.OverlayKind
import fr.vippneus.intervention.data.Recap
import fr.vippneus.intervention.data.Settings
import fr.vippneus.intervention.data.SettingsStore
import fr.vippneus.intervention.data.SignatureData
import fr.vippneus.intervention.data.ThemeMode
import fr.vippneus.intervention.data.displayStatus
import fr.vippneus.intervention.pdf.FpsTemplate.K
import fr.vippneus.intervention.ui.AppRoot
import fr.vippneus.intervention.ui.AppViewModel
import fr.vippneus.intervention.ui.Screen
import fr.vippneus.intervention.ui.Tool
import fr.vippneus.intervention.ui.VipApp
import fr.vippneus.intervention.ui.VipTheme
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File
import java.time.LocalDate
import java.time.YearMonth
import java.util.Locale

/**
 * Parcours complets sur une tablette simulée ; les captures d'écran sont écrites
 * dans app/build/test-output/ecrans pour contrôle visuel.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w1280dp-h800dp-land-mdpi")
class UiFlowTest {
    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private val shots = File(System.getProperty("user.dir"), "build/test-output/ecrans").apply { mkdirs() }

    /**
     * FileProvider garde en mémoire les dossiers de la première application qui l'a utilisé ;
     * sous Robolectric, chaque test a ses propres dossiers : on vide ce cache avant chaque test.
     */
    @Before
    fun videCacheFileProvider() {
        val cache = FileProvider::class.java.getDeclaredField("sCache").apply { isAccessible = true }
        synchronized(cache.get(null)!!) { (cache.get(null) as MutableMap<*, *>).clear() }
    }

    /** Capture logicielle de la fenêtre et des dialogues (captureToImage n'est pas disponible sous Robolectric). */
    private fun snap(name: String) {
        compose.waitForIdle()
        val decor = compose.activity.window.decorView
        val bmp = Bitmap.createBitmap(decor.width, decor.height, Bitmap.Config.ARGB_8888)
        compose.runOnUiThread {
            val canvas = Canvas(bmp)
            decor.draw(canvas)
            // Fenêtres de dialogue : dessinées centrées, sur un voile
            rootViews().filter { it !== decor && it.isAttachedToWindow && it.width > 0 && it.height > 0 }.forEach { v ->
                canvas.drawColor(0x77000000)
                canvas.save()
                canvas.translate((decor.width - v.width) / 2f, (decor.height - v.height) / 2f)
                v.draw(canvas)
                canvas.restore()
            }
        }
        File(shots, "$name.png").outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    @Suppress("UNCHECKED_CAST")
    private fun rootViews(): List<View> = runCatching {
        val wmg = Class.forName("android.view.WindowManagerGlobal")
        val instance = wmg.getMethod("getInstance").invoke(null)
        val field = wmg.getDeclaredField("mViews").apply { isAccessible = true }
        (field.get(instance) as List<View>).toList()
    }.getOrDefault(emptyList())

    /** Réglages du technicien, première configuration déjà faite. */
    private fun configure(vm: AppViewModel) =
        vm.saveSettings(Settings(technicien = "Chris.E", initiales = "CE", emailCompta = "compta@example.com", setupDone = true))

    private fun newVm(): AppViewModel {
        val app = ApplicationProvider.getApplicationContext<Application>()
        PDFBoxResourceLoader.init(app)
        return AppViewModel(app)
    }

    /** Laisse tourner la boucle principale et l'horloge de Compose (imports et rendus en arrière-plan). */
    private fun idle(times: Int = 20) {
        repeat(times) { tick() }
    }

    private fun tick() {
        compose.mainClock.advanceTimeBy(50)
        shadowOf(Looper.getMainLooper()).idle()
        Thread.sleep(50)
    }

    private fun waitFor(condition: () -> Boolean) {
        val deadline = System.currentTimeMillis() + 30_000
        while (!condition() && System.currentTimeMillis() < deadline) tick()
    }

    /** Fiche d'intervention créée à la main, puis bon de commande (fictif) joint : elle se pré-remplit. */
    private fun ficheAvecBonDeCommande(vm: AppViewModel): String {
        val app = ApplicationProvider.getApplicationContext<Application>()
        val order = File(app.cacheDir, "Bon de commande 7654321.pdf").also { FakeDocs.manuloc(it) }
        val id = vm.createFps()
        vm.navigate(Screen.Fps(id))
        vm.addAttachment(id, Uri.fromFile(order))
        waitFor { vm.interventions.value.first { it.id == id }.attachments.isNotEmpty() }
        return id
    }

    private fun input(label: String, text: String) {
        compose.onNode(hasText(label) and hasSetTextAction()).performTextInput(text)
    }

    private fun signature() = SignatureData(
        strokes = listOf(listOf(10f, 60f, 40f, 20f, 70f, 70f, 100f, 25f, 130f, 60f), listOf(150f, 30f, 190f, 65f)),
        width = 220f, height = 90f, penWidth = 3f,
    )

    @Test
    fun ficheFps_saisieApercuAjustement() {
        val vm = newVm()
        configure(vm)
        compose.setContent { VipTheme { AppRoot(vm) } }
        snap("01-accueil-vide")

        compose.onNodeWithText("Nouvelle fiche d'intervention").performClick()
        compose.waitForIdle()
        assertTrue(vm.backStack.last() is Screen.Fps)
        input("Client mandataire", "Loc Manutention")
        input("N° de commande", "1234567")
        input("Client utilisateur (site d'intervention)", "Entrepôts Dupont")
        val id = (vm.backStack.last() as Screen.Fps).id
        vm.update(id) {
            it.copy(
                values = it.values + mapOf(
                    K.MARQUE to "Hyster", K.SERIE to "123456", K.HORAMETRE to "1293",
                    K.pneu("av", "dimensions") to "22x12x16", K.pneu("av", "type") to "Bandage", K.pneu("av", "quantite") to "2",
                    K.AV_FOURNI to "oui", K.prestation("depose", "autres") to "4",
                    K.SIGNATAIRE to "Vu avec le client",
                ),
            )
        }
        snap("02-fiche-saisie")

        // Sommaire : aller directement aux prestations, puis à la signature
        compose.onAllNodesWithText("Prestations")[0].performClick()
        idle()
        snap("02b-fiche-prestations")
        // Avant l'envoi : ce qu'il manque
        compose.onNodeWithText("Envoyer à la compta").performClick()
        idle(4)
        snap("02d-envoi-incomplet")
        compose.onNodeWithText("Compléter le bon").performClick()
        // « À compléter » -> signature (avec le nom du signataire)
        compose.onNode(hasText("Signature du client") and hasClickAction()).performScrollTo().performClick()
        idle(4)
        snap("02e-signature")
        compose.onNodeWithText("Annuler").performClick()
        vm.update(id) { it.copy(signature = signature(), values = it.values + (K.SERRAGE_AV to "650")) }
        compose.onAllNodesWithText("Signature")[0].performClick()
        idle()
        snap("02c-fiche-signature")

        compose.onAllNodesWithText("Ajuster", substring = true)[0].performClick()
        compose.waitForIdle()
        assertTrue(vm.backStack.last() is Screen.Editor)
        snap("03-ajuster")

        // Sélection d'un texte de la fiche (n° de commande) : barre de réglage en bas
        compose.onRoot().performTouchInput { click(Offset(737f, 269f)) }
        idle(4)
        snap("03b-ajuster-selection")

        compose.onNodeWithText("Texte").performClick()
        snap("04-outil-texte")

        vm.back()
        vm.back()
        compose.waitForIdle()
        snap("05-accueil-liste")

        vm.navigate(Screen.Settings)
        snap("06-reglages")
        input("Copie à (facultatif)", "chef@exemple.fr")
        idle(2)
        snap("06b-reglages-modifies")
        compose.onNodeWithText("Enregistrer").performClick()
        idle(4)
        assertEquals("chef@exemple.fr", vm.settings.value.emailCopie)
    }

    @Test
    fun premiereOuverture_reglagesObligatoires() {
        val vm = newVm()
        val app = ApplicationProvider.getApplicationContext<Application>()
        compose.setContent { VipTheme { AppRoot(vm) } }
        idle(4)
        snap("00a-bienvenue-technicien")
        // Les initiales se déduisent du nom
        input("Nom affiché sur les bons", "Chris.E")
        idle(2)
        snap("00b-bienvenue-technicien-rempli")
        compose.onNodeWithText("Continuer").performClick()
        idle(4)
        assertEquals("CE", vm.settings.value.initiales)
        assertFalse(vm.settings.value.setupDone)
        // Adresse invalide : on ne passe pas
        input("E-mail de la comptabilité", "compta")
        compose.onNodeWithText("Continuer").performClick()
        idle(4)
        snap("00c-bienvenue-compta-erreur")
        compose.onAllNodesWithText("Adresse e-mail invalide", substring = true, useUnmergedTree = true)[0].assertExists()
        input("E-mail de la comptabilité", "@exemple.fr")
        compose.onNodeWithText("Continuer").performClick()
        idle(4)
        snap("00d-bienvenue-recapitulatif")
        compose.onNodeWithText("Commencer").performClick()
        idle(10)
        assertTrue(vm.settings.value.setupDone)
        // Bien enregistré sur la tablette
        val store = SettingsStore(app)
        waitFor { store.load().setupDone }
        val saved = store.load()
        assertEquals("Chris.E", saved.technicien)
        assertEquals("CE", saved.initiales)
        assertEquals("compta@exemple.fr", saved.emailCompta)
        assertTrue(saved.setupDone)
        snap("00e-accueil-apres-configuration")
    }

    @Test
    @Config(qualifiers = "w800dp-h1280dp-port-mdpi")
    fun premiereOuverture_portrait() {
        val vm = newVm()
        compose.setContent { VipTheme { AppRoot(vm) } }
        idle(4)
        input("Nom affiché sur les bons", "Chris.E")
        idle(2)
        snap("00f-bienvenue-portrait")
    }

    @Test
    @Config(qualifiers = "w800dp-h1280dp-port-mdpi")
    fun ficheFps_portrait() {
        val vm = newVm()
        configure(vm)
        compose.setContent { VipTheme { AppRoot(vm) } }
        snap("07a-accueil-portrait")
        compose.onNodeWithText("Nouvelle fiche d'intervention").performClick()
        snap("07-fiche-portrait-saisie")
        compose.onNodeWithText("Aperçu").performClick()
        snap("08-fiche-portrait-apercu")
    }

    @Test
    fun documentClient_import() {
        val vm = newVm()
        configure(vm)
        val app = ApplicationProvider.getApplicationContext<Application>()
        // Feuille de tâche fictive importée comme le ferait le sélecteur de fichiers
        val src = File(app.cacheDir, "Feuille de tache 1234567.pdf").also { FakeDocs.interfit(it) }
        compose.setContent { VipTheme { AppRoot(vm) } }
        val messages = mutableListOf<String>()
        val job = CoroutineScope(Dispatchers.Main).launch { vm.messages.collect { messages += it.text } }
        vm.importClient(Uri.fromFile(src))
        waitFor { vm.backStack.last() is Screen.Document }
        job.cancel()
        assertTrue("Import : $messages", vm.backStack.last() is Screen.Document)
        val id = (vm.backStack.last() as Screen.Document).id
        // Laisse le temps au rendu de la page 1 (rendu PDFBox, plus lent, sous Robolectric)
        idle(200)
        snap("09-document-client")
        vm.update(id) {
            it.copy(
                values = it.values + mapOf(
                    "if.compteur" to "4559", "if.couple" to "180", "if.recuPar" to "M. Martin",
                    "if.clientFinal.nom" to "Esat Test", "if.clientFinal.adresse" to "3 allée des Essais",
                ),
                signature = signature(),
            )
        }
        idle(40)
        snap("09b-document-client-rempli")
        compose.onNode(hasText("Nom du client final") and hasSetTextAction()).performScrollTo()
        idle(4)
        snap("09c-document-client-final")
    }

    @Test
    fun ficheIntervention_bonDeCommandeJoint() {
        val vm = newVm()
        configure(vm)
        compose.setContent { VipTheme { AppRoot(vm) } }
        idle()
        val id = ficheAvecBonDeCommande(vm)
        idle()
        snap("10-fiche-auto-bon-de-commande")
        // Les informations du bon de commande ont rempli la fiche
        val fiche = vm.interventions.value.first { it.id == id }
        assertEquals("7654321", fiche.value(K.NUMERO_COMMANDE))
        assertEquals("Bon de commande Manuloc", fiche.recognized)

        // PDF final
        var done = false
        CoroutineScope(Dispatchers.Main).launch {
            assertNotNull(vm.generate(id))
            vm.navigate(Screen.Viewer(id))
            done = true
        }
        waitFor { done }
        idle(60)
        snap("11-apercu-pdf")
    }

    @Test
    fun bonDeCommandeImporte_documentASigner() {
        // L'import ne crée jamais de fiche d'intervention
        val vm = newVm()
        configure(vm)
        val app = ApplicationProvider.getApplicationContext<Application>()
        val src = File(app.cacheDir, "Bon de commande 7654321.pdf").also { FakeDocs.manuloc(it) }
        compose.setContent { VipTheme { AppRoot(vm) } }
        vm.importClient(Uri.fromFile(src))
        waitFor { vm.backStack.last() is Screen.Document }
        val i = vm.interventions.value.first { it.id == (vm.backStack.last() as Screen.Document).id }
        assertEquals(InterventionType.DOCUMENT, i.type)
        assertEquals(listOf("Signature du client"), Completion.missing(i).map { it.label })
        // Ses informations nomment le fichier
        assertEquals("7654321", i.value(DocKeys.REFERENCE))
        idle(200)
        snap("10b-bon-de-commande-importe")
    }

    @Test
    @Config(qualifiers = "w1280dp-h800dp-land-night-mdpi")
    fun modeSombre() {
        val vm = newVm()
        configure(vm)
        compose.setContent { VipTheme { AppRoot(vm) } }
        ficheAvecBonDeCommande(vm)
        idle()
        snap("14-mode-sombre-fiche")
        vm.back()
        idle()
        snap("15-mode-sombre-accueil")
    }

    @Test
    @Config(qualifiers = "w960dp-h600dp-land-mdpi")
    fun petiteTablette() {
        val vm = newVm()
        configure(vm)
        compose.setContent { VipTheme { AppRoot(vm) } }
        ficheAvecBonDeCommande(vm)
        idle()
        snap("16-petite-tablette-fiche")
        vm.back()
        idle()
        snap("17-petite-tablette-accueil")
    }

    @Test
    fun documentInconnu_aSigner() {
        val vm = newVm()
        configure(vm)
        val app = ApplicationProvider.getApplicationContext<Application>()
        val src = File(app.cacheDir, "Devis.pdf").also { FakeDocs.other(it) }
        compose.setContent { VipTheme { AppRoot(vm) } }
        vm.importClient(Uri.fromFile(src))
        // Pas de question : un document importé qui n'est pas une feuille Mastra est à signer
        waitFor { vm.backStack.last() is Screen.Document }
        val i = vm.interventions.value.first { it.id == (vm.backStack.last() as Screen.Document).id }
        assertEquals(null, i.template)
        idle(100)
        snap("12-document-a-signer")
    }

    @Test
    fun bonDeLivraison_seuleLaSignatureEstExigee() {
        val vm = newVm()
        configure(vm)
        val app = ApplicationProvider.getApplicationContext<Application>()
        val src = File(app.cacheDir, "Bon de livraison 0042.pdf").also { FakeDocs.other(it) }
        compose.setContent { VipTheme { AppRoot(vm) } }
        vm.importClient(Uri.fromFile(src))
        waitFor { vm.backStack.last() is Screen.Document }
        val id = (vm.backStack.last() as Screen.Document).id
        idle(200)
        snap("18-bon-de-livraison")
        // Un seul élément attendu : la signature, nommé en clair
        val i = vm.interventions.value.first { it.id == id }
        assertEquals(listOf("Signature du client"), Completion.missing(i).map { it.label })
        compose.onNodeWithText("Manque : Signature du client").assertExists()
        compose.onNodeWithText("BON DE LIVRAISON 0042", substring = true).assertExists()

        // « Faire signer » : l'outil Signature est choisi sur la page
        compose.onNode(hasText("Signature du client") and hasClickAction()).performClick()
        idle(4)
        compose.onNodeWithText(Tool.SIGNATURE.hint).assertExists()
        snap("18b-bon-de-livraison-signature")

        vm.update(id) {
            it.copy(overlays = it.overlays + Overlay("s", OverlayKind.SIGNATURE, 380f, 700f, width = 150f, height = 61f, signature = signature()))
        }
        idle(40)
        compose.onNodeWithText("Tout est rempli").assertExists()
        snap("18c-bon-de-livraison-signe")
    }

    @Test
    fun feuilleMastraEnPage2() {
        val vm = newVm()
        configure(vm)
        val app = ApplicationProvider.getApplicationContext<Application>()
        // PDF de deux pages : page 1 scannée, feuille de tâche en page 2
        val src = File(app.cacheDir, "Feuille de tache 1234567 traitee.pdf").also { FakeDocs.interfitAfterCover(it) }
        compose.setContent { VipTheme { AppRoot(vm) } }
        val messages = mutableListOf<String>()
        val job = CoroutineScope(Dispatchers.Main).launch { vm.messages.collect { messages += it.text } }
        vm.importClient(Uri.fromFile(src))
        waitFor { vm.backStack.last() is Screen.Document }
        idle(10)
        job.cancel()
        val i = vm.interventions.value.first { it.id == (vm.backStack.last() as Screen.Document).id }
        assertEquals("Feuille de tâche Mastra", i.recognized)
        assertEquals(1, i.source?.pageCount)
        assertTrue("Messages : $messages", messages.any { it.contains("trouvée en page 2") })
        // Les cases du technicien sont nommées une à une (monteur et date remplis d'office)
        assertEquals(
            listOf("Lecture du compteur", "Couple de serrage", "Reçu par", "Signature du client"),
            Completion.missing(i).map { it.label },
        )
        idle(200)
        snap("09d-mastra-page-2")
        // Cases à remplir : « À compléter », avec l'aide lue sur la demande
        compose.onNode(hasText("Couple de serrage (Nm)") and hasSetTextAction()).performScrollTo()
        idle(4)
        compose.onAllNodesWithText("Préconisé : 159 Nm", substring = true)[0].assertExists()
        snap("09e-mastra-cases")
    }

    @Test
    fun accueil_plusieursBons() {
        val vm = newVm()
        configure(vm)
        val now = System.currentTimeMillis()
        fun fps(values: Map<String, String>, sent: Boolean = false, signed: Boolean = false) {
            val id = vm.createFps()
            vm.update(id) {
                it.copy(
                    values = it.values + values,
                    signature = if (signed) signature() else null,
                    generatedAt = if (sent) now + 1000 else null,
                    sentAt = if (sent) now + 1000 else null,
                    updatedAt = now,
                )
            }
        }
        fps(
            mapOf(
                K.CLIENT_MANDATAIRE to "LOC TEST", K.CLIENT_UTILISATEUR to "Entrepôt Test Logistique", K.NUMERO_COMMANDE to "7654321",
                K.MARQUE to "Hyster", K.HORAMETRE to "1293", K.pneu("av", "dimensions") to "22x12x16",
                K.prestation("depose", "autres") to "4", K.SERRAGE_AV to "650",
                K.DATE to Naming.formatShort(LocalDate.now().minusDays(1)),
            ),
            sent = true, signed = true,
        )
        fps(mapOf(K.CLIENT_MANDATAIRE to "MANUTENTION TEST", K.CLIENT_UTILISATEUR to "Plateforme Test", K.NUMERO_COMMANDE to "900000001"))
        fps(mapOf(K.CLIENT_MANDATAIRE to "Garage Exemple", K.MARQUE to "Linde"))
        compose.setContent { VipTheme { AppRoot(vm) } }
        idle(40)
        snap("13-accueil-plusieurs-bons")
        // Regroupés par jour d'intervention
        compose.onNodeWithText("AUJOURD'HUI").assertExists()
        compose.onNodeWithText("HIER").assertExists()
        // Ce qui manque est écrit sur chaque carte
        compose.onAllNodesWithText("À compléter :", substring = true)[0].assertExists()

        // Envoi groupé : les bons incomplets sont listés avec ce qui leur manque
        compose.onNodeWithText("Garage Exemple").performTouchInput { longClick() }
        compose.onNodeWithText("MANUTENTION TEST – Plateforme Test").performClick()
        idle(4)
        compose.onAllNodesWithText("Envoyer à la compta").onLast().performClick()
        idle(4)
        compose.onNodeWithText("2 bons incomplets sur 2").assertExists()
        snap("13b-envoi-groupe-incomplet")
    }

    @Test
    fun envoiIncomplet_marqueACorriger() {
        val vm = newVm()
        configure(vm)
        val app = ApplicationProvider.getApplicationContext<Application>()
        val src = File(app.cacheDir, "Bon de livraison 0042.pdf").also { FakeDocs.other(it) }
        compose.setContent { VipTheme { AppRoot(vm) } }
        vm.importClient(Uri.fromFile(src))
        waitFor { vm.backStack.last() is Screen.Document }
        val id = (vm.backStack.last() as Screen.Document).id
        idle(40)
        // Pas signé : l'envoi reste possible, mais le technicien est prévenu
        compose.onNodeWithText("Envoyer à la compta").performClick()
        idle(4)
        compose.onNodeWithText("le bon sera marqué « Envoyé incomplet »", substring = true).assertExists()
        snap("19-envoi-sans-signature")
        compose.onNodeWithText("Envoyer quand même").performClick()
        waitFor { vm.interventions.value.first { it.id == id }.sentAt != null }
        val sent = vm.interventions.value.first { it.id == id }
        assertEquals(listOf("Signature du client"), sent.sentMissing)
        assertEquals(DisplayStatus.INCOMPLET, sent.displayStatus())
        idle(10)
        // Marqué dans le bon…
        compose.onNodeWithText("Envoyé incomplet le", substring = true).assertExists()
        snap("19b-envoye-incomplet-bon")
        // … et dans la liste, avec ce qui manquait
        vm.back()
        idle(10)
        compose.onNodeWithText("à compléter puis renvoyer", substring = true).assertExists()
        snap("19c-envoye-incomplet-liste")
        // Signé ensuite : il reste à le renvoyer
        vm.update(id) {
            it.copy(overlays = it.overlays + Overlay("s", OverlayKind.SIGNATURE, 380f, 700f, width = 150f, height = 61f, signature = signature()))
        }
        vm.openIntervention(vm.interventions.value.first { it.id == id })
        idle(20)
        compose.onNodeWithText("Complété depuis l'envoi incomplet", substring = true).assertExists()
        snap("19d-complete-a-renvoyer")
    }

    @Test
    fun pleinEcran_feuilleMastra() {
        val vm = newVm()
        configure(vm)
        val app = ApplicationProvider.getApplicationContext<Application>()
        val src = File(app.cacheDir, "Feuille de tache 1234567.pdf").also { FakeDocs.interfit(it) }
        compose.setContent { VipTheme { AppRoot(vm) } }
        vm.importClient(Uri.fromFile(src))
        waitFor { vm.backStack.last() is Screen.Document }
        val id = (vm.backStack.last() as Screen.Document).id
        idle(200)
        compose.onNodeWithContentDescription("Plein écran").performClick()
        idle(100)
        // Toute la place pour la page ; ce qui manque reste affiché en bas
        compose.onNodeWithText("À compléter avant l'envoi").assertDoesNotExist()
        compose.onNodeWithText("Manque : Lecture du compteur", substring = true).assertExists()
        snap("20-plein-ecran")
        // Un appui ouvre la première case à remplir, sans quitter la page
        compose.onNodeWithText("Manque : Lecture du compteur", substring = true).performClick()
        idle(4)
        snap("20b-plein-ecran-saisie")
        compose.onNode(hasSetTextAction()).performTextInput("4559")
        compose.onNodeWithText("OK").performClick()
        idle(10)
        assertEquals("4559", vm.interventions.value.first { it.id == id }.value("if.compteur"))
        // Toute la largeur de la page, pour écrire plus facilement
        compose.onNodeWithContentDescription("Largeur de la page").performClick()
        idle(40)
        snap("20c-plein-ecran-largeur")
        compose.onNodeWithContentDescription("Quitter le plein écran").performClick()
        idle(10)
        compose.onNodeWithText("À compléter avant l'envoi").assertExists()
    }

    @Test
    fun suppressionAnnulable() {
        val vm = newVm()
        configure(vm)
        val app = ApplicationProvider.getApplicationContext<Application>()
        val a = vm.createFps()
        vm.update(a) { it.copy(values = it.values + (K.CLIENT_MANDATAIRE to "Client A")) }
        val b = vm.createFps()
        vm.update(b) { it.copy(values = it.values + (K.CLIENT_MANDATAIRE to "Client B")) }
        compose.setContent { VipTheme { AppRoot(vm) } }
        idle()
        // Supprimé tout de suite, sans question, mais « Annuler » le rend
        vm.delete(setOf(a))
        idle(4)
        assertFalse(vm.interventions.value.any { it.id == a })
        compose.onNodeWithText("Bon supprimé").assertExists()
        snap("21-suppression-annulable")
        compose.onNodeWithText("Annuler").performClick()
        idle(4)
        assertTrue(vm.interventions.value.any { it.id == a })
        // Sans « Annuler », les fichiers sont effacés quand le message disparaît
        val dir = File(app.filesDir, "interventions/$b")
        waitFor { dir.exists() }
        vm.delete(setOf(b))
        idle(4)
        compose.mainClock.advanceTimeBy(12_000)
        waitFor { !dir.exists() }
        assertFalse(dir.exists())
        assertTrue(File(app.filesDir, "interventions/$a").exists())
    }

    @Test
    fun toutEnvoyer_bonsComplets() {
        val vm = newVm()
        configure(vm)
        fun complet(client: String): String {
            val id = vm.createFps()
            vm.update(id) {
                it.copy(
                    values = it.values + mapOf(
                        K.CLIENT_MANDATAIRE to client, K.NUMERO_COMMANDE to "1234567", K.MARQUE to "Hyster",
                        K.HORAMETRE to "1293", K.pneu("av", "quantite") to "2", K.prestation("depose", "8") to "2",
                        K.SERRAGE_AV to "650",
                    ),
                    signature = signature(),
                )
            }
            return id
        }
        val a = complet("Client A")
        val b = complet("Client B")
        val incomplet = vm.createFps()
        compose.setContent { VipTheme { AppRoot(vm) } }
        idle(40)
        compose.onNodeWithText("2 bons complets, prêts à partir").assertExists()
        snap("22-tout-envoyer")
        compose.onNodeWithText("Tout envoyer").performClick()
        waitFor { vm.interventions.value.filter { it.id == a || it.id == b }.all { it.sentAt != null } }
        assertEquals(DisplayStatus.ENVOYE, vm.interventions.value.first { it.id == a }.displayStatus())
        assertEquals(DisplayStatus.ENVOYE, vm.interventions.value.first { it.id == b }.displayStatus())
        // Le bon incomplet n'est pas parti
        assertEquals(null, vm.interventions.value.first { it.id == incomplet }.sentAt)
        idle(10)
        compose.onNodeWithText("prêts à partir", substring = true).assertDoesNotExist()
    }

    @Test
    fun recapitulatifDuMois() {
        val vm = newVm()
        configure(vm)
        val id = vm.createFps()
        vm.update(id) { it.copy(values = it.values + (K.CLIENT_MANDATAIRE to "Client A")) }
        compose.setContent { VipTheme { AppRoot(vm) } }
        idle()
        compose.onNodeWithText("Récapitulatif").performClick()
        idle(4)
        snap("23-recapitulatif")
        val mois = Recap.monthLabel(YearMonth.now()).replaceFirstChar { it.titlecase(Locale.FRANCE) }
        compose.onNodeWithText(mois).performClick()
        var started: Intent? = null
        waitFor { shadowOf(compose.activity).nextStartedActivity?.also { started = it } != null }
        // Messagerie : tableau joint, objet et destinataire de la compta
        val mail = started!!.getParcelableExtra(Intent.EXTRA_INTENT, Intent::class.java)!!
        assertEquals("text/csv", mail.type)
        assertTrue(mail.getStringExtra(Intent.EXTRA_SUBJECT)!!.startsWith("Récapitulatif des bons"))
        assertEquals("compta@example.com", mail.getStringArrayExtra(Intent.EXTRA_EMAIL)!!.single())
        val uri = mail.getParcelableExtra(Intent.EXTRA_STREAM, Uri::class.java)!!
        val csv = compose.activity.contentResolver.openInputStream(uri)!!.use { it.readBytes().toString(Charsets.UTF_8) }
        assertTrue(csv, csv.contains("Client A"))
    }

    @Test
    fun apparenceSombre() {
        val vm = newVm()
        configure(vm)
        val app = ApplicationProvider.getApplicationContext<Application>()
        compose.setContent { VipApp(vm) }
        idle()
        vm.navigate(Screen.Settings)
        idle(4)
        // Appliquée et enregistrée dès le choix
        compose.onNodeWithText("Sombre").performScrollTo().performClick()
        idle(10)
        assertEquals(ThemeMode.SOMBRE, vm.settings.value.theme)
        waitFor { SettingsStore(app).load().theme == ThemeMode.SOMBRE }
        assertEquals(ThemeMode.SOMBRE, SettingsStore(app).load().theme)
        snap("24-reglages-apparence-sombre")
        vm.back()
        idle(20)
        snap("24b-accueil-sombre")
    }
}
