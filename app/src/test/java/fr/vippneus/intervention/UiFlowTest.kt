package fr.vippneus.intervention

import android.app.Application
import android.graphics.Bitmap
import android.net.Uri
import android.graphics.Canvas
import androidx.activity.ComponentActivity
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.test.core.app.ApplicationProvider
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import fr.vippneus.intervention.ui.AppRoot
import fr.vippneus.intervention.ui.AppViewModel
import fr.vippneus.intervention.ui.Screen
import fr.vippneus.intervention.ui.VipTheme
import fr.vippneus.intervention.pdf.FpsTemplate.K
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File
import kotlinx.coroutines.launch
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import android.os.Looper
import org.robolectric.Shadows.shadowOf

/**
 * Parcours complet sur une tablette simulée ; les captures d'écran sont écrites
 * dans app/build/test-output/ecrans pour contrôle visuel.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w1280dp-h800dp-land-mdpi")
class UiFlowTest {
    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private val shots = File(System.getProperty("user.dir"), "build/test-output/ecrans").apply { mkdirs() }

    /** Capture logicielle de la fenêtre (captureToImage n'est pas disponible sous Robolectric). */
    private fun snap(name: String) {
        compose.waitForIdle()
        val view = compose.activity.window.decorView
        val bmp = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
        compose.runOnUiThread { view.draw(Canvas(bmp)) }
        File(shots, "$name.png").outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    private fun newVm(): AppViewModel {
        val app = ApplicationProvider.getApplicationContext<Application>()
        PDFBoxResourceLoader.init(app)
        return AppViewModel(app)
    }

    @Test
    fun ficheFps_saisieApercuAjustement() {
        val vm = newVm()
        vm.saveSettings(vm.settings.value.copy(technicien = "Chris.E", initiales = "CE", emailCompta = "compta@example.com"))
        compose.setContent { VipTheme { AppRoot(vm) } }
        snap("01-accueil-vide")

        compose.onNodeWithText("Nouvelle fiche d'intervention").performClick()
        compose.waitForIdle()
        assertTrue(vm.backStack.last() is Screen.Fps)
        compose.onNodeWithText("Client mandataire").performTextInput("Loc Manutention")
        compose.onNodeWithText("N° de commande").performTextInput("1234567")
        compose.onNodeWithText("Client utilisateur (site d'intervention)").performTextInput("Entrepôts Dupont")
        val id = (vm.backStack.last() as Screen.Fps).id
        vm.update(id) {
            it.copy(
                values = it.values + mapOf(
                    K.MARQUE to "Hyster", K.SERIE to "123456", K.HORAMETRE to "1293",
                    K.pneu("av", "dimensions") to "22x12x16", K.pneu("av", "type") to "Bandage", K.pneu("av", "quantite") to "2",
                    K.AV_FOURNI to "oui", K.prestation("depose", "autres") to "4", K.SERRAGE_AV to "650",
                    K.SIGNATAIRE to "Vu avec le client",
                ),
            )
        }
        snap("02-fiche-saisie")

        compose.onAllNodesWithText("Ajuster", substring = true)[0].performClick()
        compose.waitForIdle()
        assertTrue(vm.backStack.last() is Screen.Editor)
        snap("03-ajuster")

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
        compose.onNodeWithText("Nouvelle fiche d'intervention").performClick()
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
        val deadline = System.currentTimeMillis() + 30_000
        while (vm.backStack.last() !is Screen.Document && System.currentTimeMillis() < deadline) {
            shadowOf(Looper.getMainLooper()).idle()
            Thread.sleep(50)
        }
        job.cancel()
        assertTrue("Import : $messages", vm.backStack.last() is Screen.Document)
        val id = (vm.backStack.last() as Screen.Document).id
        vm.update(id) { it.copy(values = it.values + mapOf("if.compteur" to "4559", "if.couple" to "180", "if.recuPar" to "M. Martin")) }
        // Laisse le temps au rendu de la page 1 (rendu PDFBox, plus lent, sous Robolectric)
        repeat(200) {
            shadowOf(Looper.getMainLooper()).idle()
            Thread.sleep(50)
        }
        snap("09-document-client")
    }

    @Test
    fun bonDeCommande_ficheRemplieAutomatiquement() {
        val vm = newVm()
        vm.saveSettings(vm.settings.value.copy(technicien = "Chris.E", initiales = "CE"))
        val app = ApplicationProvider.getApplicationContext<Application>()
        val src = File(app.cacheDir, "Bon de commande 7654321.pdf").also { FakeDocs.manuloc(it) }
        compose.setContent { VipTheme { AppRoot(vm) } }
        vm.importClient(Uri.fromFile(src))
        val deadline = System.currentTimeMillis() + 30_000
        while (vm.backStack.last() !is Screen.Fps && System.currentTimeMillis() < deadline) {
            shadowOf(Looper.getMainLooper()).idle()
            Thread.sleep(50)
        }
        assertTrue(vm.backStack.last() is Screen.Fps)
        repeat(20) {
            shadowOf(Looper.getMainLooper()).idle()
            Thread.sleep(50)
        }
        snap("10-fiche-auto-bon-de-commande")
    }
}
