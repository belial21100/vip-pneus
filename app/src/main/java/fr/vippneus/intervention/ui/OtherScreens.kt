package fr.vippneus.intervention.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Badge
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.min
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import fr.vippneus.intervention.BuildConfigInfo
import fr.vippneus.intervention.data.Completion
import fr.vippneus.intervention.data.Naming
import fr.vippneus.intervention.data.Settings
import fr.vippneus.intervention.data.displayStatus
import fr.vippneus.intervention.pdf.PdfPages
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.math.roundToInt

/** Ajustement de la mise en page de la fiche FPS (positions, tailles, mentions libres). */
@Composable
fun EditorScreen(vm: AppViewModel, id: String) {
    val list by vm.interventions.collectAsStateWithLifecycle()
    val i = list.firstOrNull { it.id == id }
    if (i == null) {
        LaunchedEffect(Unit) { if (vm.backStack.lastOrNull() == Screen.Editor(id)) vm.back() }
        return
    }
    val state = remember(id) { EditorState() }
    Column(
        Modifier
            .fillMaxSize()
            .background(Vip.colors.canvas),
    ) {
        VipTopBar(
            title = "Ajuster la mise en page",
            subtitle = "Glissez un texte pour le déplacer · touchez-le pour changer sa taille",
            onBack = { vm.back() },
        ) {
            VipButton("Terminé", { vm.back() }, icon = Icons.Filled.Check, compact = true)
        }
        PageEditor(
            vm, i, state,
            Modifier
                .weight(1f)
                .fillMaxWidth()
                .navigationBarsPadding(),
        )
    }
}

/** Visionneuse du PDF final, avant envoi. */
@Composable
fun ViewerScreen(vm: AppViewModel, id: String) {
    val list by vm.interventions.collectAsStateWithLifecycle()
    val i = list.firstOrNull { it.id == id }
    val context = LocalContext.current
    val file = remember(id, i?.generatedAt) { vm.exportedFile(id) }
    val save = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/pdf")) { uri ->
        if (uri != null && file != null) vm.saveCopy(file, uri)
    }
    val count by produceState(0, file) {
        value = if (file == null) 0 else withContext(Dispatchers.IO) { runCatching { PdfPages.pageCount(file) }.getOrDefault(0) }
    }
    val todos = remember(i) { i?.let { Completion.todos(it) }.orEmpty() }
    val send: () -> Unit = if (i != null) rememberSendAction(vm, i, todos) else ({ })

    Column(
        Modifier
            .fillMaxSize()
            .background(Vip.colors.canvas),
    ) {
        VipTopBar(
            title = file?.name ?: "PDF",
            subtitle = when (count) {
                0 -> "PDF envoyé à la comptabilité"
                1 -> "1 page · PDF envoyé à la comptabilité"
                else -> "$count pages · PDF envoyé à la comptabilité"
            },
            onBack = { vm.back() },
            status = i?.displayStatus(),
        ) {
            if (file != null) {
                IconButton(onClick = { vm.openExternal(context, file) }) {
                    Icon(Icons.AutoMirrored.Filled.OpenInNew, contentDescription = "Ouvrir avec une autre application")
                }
                IconButton(onClick = { save.launch(file.name) }) {
                    Icon(Icons.Filled.Download, contentDescription = "Enregistrer une copie")
                }
                VipButton("Envoyer à la compta", send, icon = Icons.AutoMirrored.Filled.Send, compact = true)
            }
        }
        if (file == null || !file.exists()) {
            Box(
                Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                contentAlignment = Alignment.Center,
            ) {
                Text("PDF introuvable : revenez en arrière et relancez l'aperçu.", color = Vip.colors.onChrome)
            }
        } else {
            PdfPagesList(file, count, Modifier.weight(1f))
        }
    }
}

@Composable
private fun PdfPagesList(file: File, count: Int, modifier: Modifier) {
    val c = Vip.colors
    BoxWithConstraints(modifier.fillMaxSize()) {
        val pageWidth = min(maxWidth - 48.dp, 900.dp)
        val widthPx = with(LocalDensity.current) { pageWidth.toPx() }.roundToInt().coerceIn(400, 2000)
        LazyColumn(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(20.dp),
            contentPadding = PaddingValues(top = 24.dp, bottom = 48.dp),
            modifier = Modifier
                .fillMaxSize()
                .navigationBarsPadding(),
        ) {
            items(count) { index ->
                val bmp by produceState<ImageBitmap?>(null, file, index) {
                    value = withContext(Dispatchers.IO) { runCatching { PdfPages.render(file, index, widthPx).asImageBitmap() }.getOrNull() }
                }
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Box(
                        Modifier
                            .width(pageWidth)
                            .aspectRatio(bmp?.let { it.width.toFloat() / it.height } ?: 0.707f)
                            .shadow(16.dp, RoundedCornerShape(3.dp))
                            .background(Color.White),
                        contentAlignment = Alignment.Center,
                    ) {
                        val b = bmp
                        if (b != null) Image(b, contentDescription = "Page ${index + 1}", modifier = Modifier.fillMaxSize())
                        else CircularProgressIndicator(Modifier.size(36.dp), color = c.accent)
                    }
                    Text(
                        "Page ${index + 1} / $count",
                        style = MaterialTheme.typography.labelMedium,
                        color = c.onChromeMuted,
                        modifier = Modifier.padding(top = 10.dp),
                    )
                }
            }
        }
    }
}

@Composable
fun SettingsScreen(vm: AppViewModel) {
    val initial = remember { vm.settings.value }
    var s by remember { mutableStateOf(initial) }
    val c = Vip.colors
    fun change(n: Settings) {
        s = n
        vm.saveSettings(n)
    }
    Column(
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
    ) {
        VipTopBar(title = "Réglages", subtitle = "Technicien, envoi à la comptabilité", onBack = { vm.back() })
        Column(
            Modifier
                .weight(1f)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .imePadding()
                .navigationBarsPadding()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Column(
                Modifier
                    .widthIn(max = 780.dp)
                    .fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                SectionCard(
                    "Technicien",
                    icon = Icons.Filled.Badge,
                    subtitle = "Pré-remplit « Commercial / Monteur » et termine le nom des fichiers",
                ) {
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        VipField(
                            label = "Nom affiché (ex. Chris.E)",
                            value = s.technicien,
                            onValueChange = { change(s.copy(technicien = it)) },
                            capitalization = KeyboardCapitalization.Words,
                            modifier = Modifier.weight(2f),
                        )
                        VipField(
                            label = "Initiales (ex. CE)",
                            value = s.initiales,
                            onValueChange = { change(s.copy(initiales = it.take(6))) },
                            capitalization = KeyboardCapitalization.Characters,
                            modifier = Modifier.weight(1f),
                        )
                    }
                    FileNameExample(s.initiales)
                }
                SectionCard(
                    "Comptabilité",
                    icon = Icons.Filled.Email,
                    subtitle = "Destinataire proposé à l'envoi des PDF",
                ) {
                    VipField(
                        label = "E-mail de la comptabilité",
                        value = s.emailCompta,
                        onValueChange = { change(s.copy(emailCompta = it)) },
                        keyboardType = KeyboardType.Email,
                        capitalization = KeyboardCapitalization.None,
                    )
                    VipField(
                        label = "Copie à (facultatif)",
                        value = s.emailCopie,
                        onValueChange = { change(s.copy(emailCopie = it)) },
                        keyboardType = KeyboardType.Email,
                        capitalization = KeyboardCapitalization.None,
                        supporting = "Plusieurs adresses : séparez-les par une virgule",
                    )
                    VipField(
                        label = "Message de l'e-mail",
                        value = s.message,
                        onValueChange = { change(s.copy(message = it)) },
                        singleLine = false,
                        minLines = 4,
                    )
                    if (s.message != Settings.DEFAULT_MESSAGE) {
                        VipButton(
                            "Remettre le message par défaut",
                            { change(s.copy(message = Settings.DEFAULT_MESSAGE)) },
                            icon = Icons.Filled.Restore,
                            tone = Tone.GHOST,
                            compact = true,
                        )
                    }
                }
                SectionCard("À propos", icon = Icons.Filled.Info) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        BrandMark(44.dp)
                        Spacer(Modifier.width(14.dp))
                        Column {
                            Text("VIP Pneus – Bons d'intervention", style = MaterialTheme.typography.titleSmall)
                            Text(
                                "Version ${BuildConfigInfo.versionName(LocalContext.current)}",
                                style = MaterialTheme.typography.bodySmall,
                                color = c.muted,
                            )
                        }
                    }
                    Text(
                        "Les bons sont enregistrés uniquement sur cette tablette. Polices Barlow et Source Sans 3 " +
                            "(licence SIL OFL 1.1), bibliothèque PdfBox-Android (licence Apache 2.0).",
                        style = MaterialTheme.typography.bodySmall,
                        color = c.muted,
                    )
                }
                Spacer(Modifier.height(24.dp))
            }
        }
    }
}

/** Exemple de nom de fichier avec les initiales saisies. */
@Composable
private fun FileNameExample(initiales: String) {
    val c = Vip.colors
    val example = listOf("02- CLIENT SITE 02000 VILLE 1234567", Naming.today().replace('/', '-').let { d ->
        // jj-mm-aa -> jj-mm-aaaa, comme dans les noms de fichiers
        val parts = d.split('-')
        if (parts.size == 3 && parts[2].length == 2) "${parts[0]}-${parts[1]}-20${parts[2]}" else d
    }, initiales.trim().ifEmpty { "XX" }).joinToString(" ")
    Column(
        Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceContainerLow, MaterialTheme.shapes.small)
            .padding(horizontal = 14.dp, vertical = 10.dp),
    ) {
        Text("Exemple de nom de fichier", style = MaterialTheme.typography.labelMedium, color = c.muted)
        Text("$example.pdf", style = MaterialTheme.typography.bodyMedium)
    }
}
