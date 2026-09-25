package fr.vippneus.intervention

import android.app.Application
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
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.test.core.app.ApplicationProvider
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import fr.vippneus.intervention.data.SignatureData
import fr.vippneus.intervention.pdf.FpsTemplate.K
import fr.vippneus.intervention.ui.AppRoot
import fr.vippneus.intervention.ui.AppViewModel
import fr.vippneus.intervention.ui.Screen
import fr.vippneus.intervention.ui.VipTheme
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

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
        vm.saveSettings(vm.settings.value.copy(technicien = "Chris.E", initiales = "CE", emailCompta = "compta@example.com"))
        compose.setContent { VipTheme { AppRoot(vm) } }
        snap("01-accueil-vide")

        compose.onNodeWithText("Nouvelle fiche vierge").performClick()
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
    }

    @Test
    @Config(qualifiers = "w800dp-h1280dp-port-mdpi")
    fun ficheFps_portrait() {
        val vm = newVm()
        compose.setContent { VipTheme { AppRoot(vm) } }
        snap("07a-accueil-portrait")
        compose.onNodeWithText("Nouvelle fiche vierge").performClick()
        snap("07-fiche-portrait-saisie")
        compose.onNodeWithText("Aperçu").performClick()
        snap("08-fiche-portrait-apercu")
    }

    @Test
    fun documentClient_import() {
        val vm = newVm()
        vm.saveSettings(vm.settings.value.copy(technicien = "Chris.E", initiales = "CE"))
        val app = ApplicationProvider.getApplicationContext<Application>()
        // Feuille de tâche fictive importée comme le ferait le sélecteur de fichiers
        val src = File(app.cacheDir, "Feuille de tache 1234567.pdf").also { FakeDocs.interfit(it) }
        compose.setContent { VipTheme { AppRoot(vm) } }
        val messages = mutableListOf<String>()
        val job = CoroutineScope(Dispatchers.Main).launch { vm.messages.collect { messages += it } }
        vm.importClient(Uri.fromFile(src))
        waitFor { vm.backStack.last() is Screen.Document }
        job.cancel()
        assertTrue("Import : $messages", vm.backStack.last() is Screen.Document)
        val id = (vm.backStack.last() as Screen.Document).id
        // Laisse le temps au rendu de la page 1 (rendu PDFBox, plus lent, sous Robolectric)
        idle(200)
        snap("09-document-client")
        vm.update(id) { it.copy(values = it.values + mapOf("if.compteur" to "4559", "if.couple" to "180", "if.recuPar" to "M. Martin"), signature = signature()) }
        idle(40)
        snap("09b-document-client-rempli")
    }

    @Test
    fun bonDeCommande_ficheRemplieAutomatiquement() {
        val vm = newVm()
        vm.saveSettings(vm.settings.value.copy(technicien = "Chris.E", initiales = "CE", emailCompta = "compta@example.com"))
        val app = ApplicationProvider.getApplicationContext<Application>()
        val src = File(app.cacheDir, "Bon de commande 7654321.pdf").also { FakeDocs.manuloc(it) }
        compose.setContent { VipTheme { AppRoot(vm) } }
        vm.importClient(Uri.fromFile(src))
        waitFor { vm.backStack.last() is Screen.Fps }
        assertTrue(vm.backStack.last() is Screen.Fps)
        idle()
        snap("10-fiche-auto-bon-de-commande")

        // PDF final
        val id = (vm.backStack.last() as Screen.Fps).id
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
    @Config(qualifiers = "w1280dp-h800dp-land-night-mdpi")
    fun modeSombre() {
        val vm = newVm()
        vm.saveSettings(vm.settings.value.copy(technicien = "Chris.E", initiales = "CE", emailCompta = "compta@example.com"))
        val app = ApplicationProvider.getApplicationContext<Application>()
        val src = File(app.cacheDir, "Bon de commande 7654321.pdf").also { FakeDocs.manuloc(it) }
        compose.setContent { VipTheme { AppRoot(vm) } }
        vm.importClient(Uri.fromFile(src))
        waitFor { vm.backStack.last() is Screen.Fps }
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
        vm.saveSettings(vm.settings.value.copy(technicien = "Chris.E", initiales = "CE", emailCompta = "compta@example.com"))
        val app = ApplicationProvider.getApplicationContext<Application>()
        val src = File(app.cacheDir, "Bon de commande 7654321.pdf").also { FakeDocs.manuloc(it) }
        compose.setContent { VipTheme { AppRoot(vm) } }
        vm.importClient(Uri.fromFile(src))
        waitFor { vm.backStack.last() is Screen.Fps }
        idle()
        snap("16-petite-tablette-fiche")
        vm.back()
        idle()
        snap("17-petite-tablette-accueil")
    }

    @Test
    fun documentInconnu_choix() {
        val vm = newVm()
        val app = ApplicationProvider.getApplicationContext<Application>()
        val src = File(app.cacheDir, "Devis.pdf").also { FakeDocs.other(it) }
        compose.setContent { VipTheme { AppRoot(vm) } }
        vm.importClient(Uri.fromFile(src))
        waitFor { vm.pendingImport.value != null }
        idle()
        snap("12-document-non-reconnu")
    }

    @Test
    fun accueil_plusieursBons() {
        val vm = newVm()
        vm.saveSettings(vm.settings.value.copy(technicien = "Chris.E", initiales = "CE", emailCompta = "compta@example.com"))
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
            ),
            sent = true, signed = true,
        )
        fps(mapOf(K.CLIENT_MANDATAIRE to "MANUTENTION TEST", K.CLIENT_UTILISATEUR to "Plateforme Test", K.NUMERO_COMMANDE to "900000001"))
        fps(mapOf(K.CLIENT_MANDATAIRE to "Garage Exemple", K.MARQUE to "Linde"))
        compose.setContent { VipTheme { AppRoot(vm) } }
        idle()
        snap("13-accueil-plusieurs-bons")
    }
}
