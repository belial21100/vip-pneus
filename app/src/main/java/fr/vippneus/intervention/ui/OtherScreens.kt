package fr.vippneus.intervention.ui

import androidx.activity.compose.BackHandler
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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.EditNote
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.min
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import fr.vippneus.intervention.data.Completion
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
    var fullscreen by rememberSaveable { mutableStateOf(false) }
    BackHandler(enabled = fullscreen) { fullscreen = false }
    ImmersiveMode(fullscreen)
    Column(
        Modifier
            .fillMaxSize()
            .background(Vip.colors.canvas),
    ) {
        if (!fullscreen) {
            VipTopBar(
                title = "Ajuster la mise en page",
                subtitle = "Glissez un texte pour le déplacer · touchez-le pour changer sa taille",
                onBack = { vm.back() },
            ) {
                VipButton("Terminé", { vm.back() }, icon = Icons.Filled.Check, compact = true)
            }
        }
        PageEditor(
            vm, i, state,
            Modifier
                .weight(1f)
                .fillMaxWidth()
                .then(if (fullscreen) Modifier else Modifier.navigationBarsPadding()),
            fullscreen = fullscreen,
            onToggleFullscreen = { fullscreen = !fullscreen },
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
    val missing = todos.filterNot { it.done }
    val send: () -> Unit = if (i != null) rememberSendAction(vm, i, todos) { vm.editIntervention(i) } else ({ })

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
        if (i != null && missing.isNotEmpty()) {
            MissingStrip(missing.map { it.label }) { vm.editIntervention(i) }
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

/** Rappel de ce qui manque encore sur le bon, au-dessus de l'aperçu du PDF. */
@Composable
private fun MissingStrip(labels: List<String>, onComplete: () -> Unit) {
    val c = Vip.colors
    Surface(color = c.warningSoft, contentColor = c.warning) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.Filled.Warning, contentDescription = null, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(12.dp))
            Text(
                "Manque : " + labels.joinToString(", "),
                style = MaterialTheme.typography.titleSmall,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            Spacer(Modifier.width(12.dp))
            VipButton("Compléter le bon", onComplete, icon = Icons.Filled.EditNote, tone = Tone.GHOST, compact = true)
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
