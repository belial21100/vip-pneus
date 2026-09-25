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
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Download
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.min
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import fr.vippneus.intervention.BuildConfigInfo
import fr.vippneus.intervention.pdf.PdfPages
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.math.roundToInt

/** Ajustement de la mise en page de la fiche FPS (positions, tailles, mentions libres). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditorScreen(vm: AppViewModel, id: String) {
    val list by vm.interventions.collectAsStateWithLifecycle()
    val i = list.firstOrNull { it.id == id }
    if (i == null) {
        LaunchedEffect(Unit) { vm.back() }
        return
    }
    val state = remember(id) { EditorState() }
    Scaffold(
        topBar = {
            TopAppBar(
                navigationIcon = {
                    IconButton(onClick = { vm.back() }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Retour") }
                },
                title = {
                    Column {
                        Text("Ajuster la fiche")
                        Text(
                            "Glissez un texte pour le déplacer, touchez-le pour changer sa taille",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                },
                actions = {
                    Button(onClick = { vm.back() }, modifier = Modifier.padding(end = 8.dp)) {
                        Icon(Icons.Filled.Check, contentDescription = null)
                        Text("  Terminé")
                    }
                },
            )
        },
    ) { padding ->
        PageEditor(
            vm, i, state,
            Modifier
                .padding(padding)
                .fillMaxSize(),
        )
    }
}

/** Visionneuse du PDF final, avant envoi. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ViewerScreen(vm: AppViewModel, id: String) {
    val list by vm.interventions.collectAsStateWithLifecycle()
    val i = list.firstOrNull { it.id == id }
    val context = LocalContext.current
    val file = remember(id, i?.generatedAt) { vm.exportedFile(id) }
    val save = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/pdf")) { uri ->
        if (uri != null && file != null) vm.saveCopy(file, uri)
    }
    Scaffold(
        topBar = {
            TopAppBar(
                navigationIcon = {
                    IconButton(onClick = { vm.back() }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Retour") }
                },
                title = { Text(file?.name ?: "PDF", maxLines = 1, overflow = TextOverflow.Ellipsis) },
                actions = {
                    if (file != null) {
                        IconButton(onClick = { vm.openExternal(context, file) }) {
                            Icon(Icons.AutoMirrored.Filled.OpenInNew, contentDescription = "Ouvrir avec une autre application")
                        }
                        IconButton(onClick = { save.launch(file.name) }) {
                            Icon(Icons.Filled.Download, contentDescription = "Enregistrer une copie")
                        }
                        Button(onClick = { vm.send(context, listOf(id)) }, modifier = Modifier.padding(end = 8.dp)) {
                            Icon(Icons.AutoMirrored.Filled.Send, contentDescription = null)
                            Text("  Envoyer")
                        }
                    }
                },
            )
        },
    ) { padding ->
        if (file == null || !file.exists()) {
            Box(
                Modifier
                    .padding(padding)
                    .fillMaxSize(),
                contentAlignment = Alignment.Center,
            ) { Text("PDF introuvable : revenez en arrière et relancez « PDF ».") }
        } else {
            PdfPagesList(file, Modifier.padding(padding))
        }
    }
}

@Composable
private fun PdfPagesList(file: File, modifier: Modifier) {
    val count by produceState(0, file) { value = withContext(Dispatchers.IO) { runCatching { PdfPages.pageCount(file) }.getOrDefault(0) } }
    BoxWithConstraints(
        modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surfaceContainerHigh),
    ) {
        val pageWidth = min(maxWidth - 32.dp, 920.dp)
        val widthPx = with(LocalDensity.current) { pageWidth.toPx() }.roundToInt().coerceIn(400, 2000)
        LazyColumn(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp),
            contentPadding = PaddingValues(16.dp),
            modifier = Modifier.fillMaxSize(),
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
                            .shadow(3.dp)
                            .background(Color.White),
                        contentAlignment = Alignment.Center,
                    ) {
                        val b = bmp
                        if (b != null) Image(b, contentDescription = "Page ${index + 1}", modifier = Modifier.fillMaxSize())
                        else CircularProgressIndicator(Modifier.size(36.dp))
                    }
                    Text(
                        "Page ${index + 1} / $count",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 6.dp),
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(vm: AppViewModel) {
    val initial = remember { vm.settings.value }
    var s by remember { mutableStateOf(initial) }
    fun change(n: fr.vippneus.intervention.data.Settings) {
        s = n
        vm.saveSettings(n)
    }
    Scaffold(
        topBar = {
            TopAppBar(
                navigationIcon = {
                    IconButton(onClick = { vm.back() }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Retour") }
                },
                title = { Text("Réglages") },
            )
        },
    ) { padding ->
        Column(
            Modifier
                .padding(padding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .imePadding()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Column(Modifier.widthIn(max = 760.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Section("Technicien", subtitle = "Pré-remplit « Commercial / Monteur » et termine le nom des fichiers") {
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        OutlinedTextField(
                            value = s.technicien,
                            onValueChange = { change(s.copy(technicien = it)) },
                            label = { Text("Nom affiché (ex. Chris.E)") },
                            singleLine = true,
                            modifier = Modifier.weight(2f),
                        )
                        OutlinedTextField(
                            value = s.initiales,
                            onValueChange = { change(s.copy(initiales = it.take(6))) },
                            label = { Text("Initiales (ex. CE)") },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Characters),
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
                Section("Comptabilité", subtitle = "Destinataire proposé lors de l'envoi des PDF") {
                    OutlinedTextField(
                        value = s.emailCompta,
                        onValueChange = { change(s.copy(emailCompta = it)) },
                        label = { Text("E-mail de la comptabilité") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                        modifier = Modifier.fillMaxWidth(),
                    )
                    OutlinedTextField(
                        value = s.emailCopie,
                        onValueChange = { change(s.copy(emailCopie = it)) },
                        label = { Text("Copie à (facultatif)") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                        modifier = Modifier.fillMaxWidth(),
                    )
                    OutlinedTextField(
                        value = s.message,
                        onValueChange = { change(s.copy(message = it)) },
                        label = { Text("Message de l'e-mail") },
                        minLines = 3,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    TextButton(onClick = { change(s.copy(message = fr.vippneus.intervention.data.Settings.DEFAULT_MESSAGE)) }) {
                        Text("Message par défaut")
                    }
                }
                Section("À propos") {
                    Text("VIP Pneus – Bons d'intervention, version ${BuildConfigInfo.versionName(LocalContext.current)}")
                    Text(
                        "Les bons sont enregistrés uniquement sur cette tablette. Police Source Sans 3 (licence SIL OFL 1.1), " +
                            "bibliothèque PdfBox-Android (licence Apache 2.0).",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}
