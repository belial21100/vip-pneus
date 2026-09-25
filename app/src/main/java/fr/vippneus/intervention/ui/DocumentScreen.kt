package fr.vippneus.intervention.ui

import android.content.ActivityNotFoundException
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.AddAPhoto
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Draw
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Tab
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.VerticalDivider
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import fr.vippneus.intervention.data.DocKeys
import fr.vippneus.intervention.data.Intervention
import fr.vippneus.intervention.data.Naming
import fr.vippneus.intervention.data.Suggestions
import kotlinx.coroutines.launch
import java.io.File
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneOffset

/** Document client : 1re page complétée à la main (textes, date, croix, signature). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DocumentScreen(vm: AppViewModel, id: String) {
    val list by vm.interventions.collectAsStateWithLifecycle()
    val i = list.firstOrNull { it.id == id }
    if (i == null) {
        LaunchedEffect(Unit) { vm.back() }
        return
    }
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val editor = remember(id) { EditorState() }
    var tab by rememberSaveable { mutableIntStateOf(0) }

    Scaffold(
        topBar = {
            TopAppBar(
                navigationIcon = {
                    IconButton(onClick = { vm.back() }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Retour") }
                },
                title = {
                    Column {
                        Text("Document client", maxLines = 1)
                        Text(
                            i.source?.name ?: "",
                            style = MaterialTheme.typography.bodyMedium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                },
                actions = {
                    TextButton(onClick = { scope.launch { if (vm.generate(id) != null) vm.navigate(Screen.Viewer(id)) } }) {
                        Icon(Icons.Filled.Visibility, contentDescription = null)
                        Text(" PDF")
                    }
                    Button(onClick = { vm.send(context, listOf(id)) }, modifier = Modifier.padding(end = 8.dp)) {
                        Icon(Icons.AutoMirrored.Filled.Send, contentDescription = null)
                        Text("  Envoyer")
                    }
                },
            )
        },
    ) { padding ->
        BoxWithConstraints(
            Modifier
                .padding(padding)
                .fillMaxSize(),
        ) {
            if (maxWidth >= 900.dp) {
                Row(Modifier.fillMaxSize()) {
                    PageEditor(vm, i, editor, Modifier.weight(1.45f))
                    VerticalDivider()
                    DocumentPanel(vm, i, Modifier.weight(1f))
                }
            } else {
                Column(Modifier.fillMaxSize()) {
                    PrimaryTabRow(selectedTabIndex = tab) {
                        Tab(selected = tab == 0, onClick = { tab = 0 }, text = { Text("Page 1") })
                        Tab(selected = tab == 1, onClick = { tab = 1 }, text = { Text("Infos et envoi") })
                    }
                    if (tab == 0) PageEditor(vm, i, editor, Modifier.weight(1f))
                    else DocumentPanel(vm, i, Modifier.weight(1f))
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DocumentPanel(vm: AppViewModel, i: Intervention, modifier: Modifier) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val suggestions by vm.suggestions.collectAsStateWithLifecycle()
    val settings by vm.settings.collectAsStateWithLifecycle()
    var datePicker by remember { mutableStateOf(false) }
    var signing by remember { mutableStateOf(false) }
    var pendingPhoto by rememberSaveable { mutableStateOf<String?>(null) }

    val pickFile = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) vm.addAttachment(i.id, uri)
    }
    val takePhoto = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { ok ->
        pendingPhoto?.let { vm.onPhotoTaken(i.id, File(it), ok) }
        pendingPhoto = null
    }

    @Composable
    fun field(
        key: String,
        label: String,
        modifier: Modifier = Modifier,
        keyboard: KeyboardType = KeyboardType.Text,
        caps: KeyboardCapitalization = KeyboardCapitalization.Sentences,
        trailing: (@Composable () -> Unit)? = null,
    ) {
        val v = i.value(key)
        SuggestField(
            label = label,
            value = v,
            onValueChange = { vm.setValue(i.id, key, it) },
            modifier = modifier,
            suggestions = Suggestions.filter(suggestions, key, v),
            keyboardType = keyboard,
            capitalization = caps,
            trailing = trailing,
        )
    }

    Column(
        modifier
            .fillMaxHeight()
            .verticalScroll(rememberScrollState())
            .imePadding()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        i.recognized?.let { RecognizedBanner(it) }
        i.template?.let { t ->
            Section(t.name, subtitle = "Les valeurs saisies s'inscrivent automatiquement au bon endroit du document") {
                t.fields.forEach { f ->
                    val v = i.value(f.key)
                    SuggestField(
                        label = f.label,
                        value = v,
                        onValueChange = { vm.setValue(i.id, f.key, it) },
                        suggestions = templateChips(f.key, v, i),
                        keyboardType = if (f.numeric) KeyboardType.Number else KeyboardType.Text,
                        supporting = i.hints[f.key],
                    )
                }
                if (t.signature != null) {
                    Text("Signature du client", style = MaterialTheme.typography.titleSmall)
                    SignaturePreview(i.signature, onClick = { signing = true })
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Button(onClick = { signing = true }) {
                            Icon(Icons.Filled.Draw, contentDescription = null)
                            Text(if (i.signature == null) "  Faire signer" else "  Refaire la signature")
                        }
                        if (i.signature != null) {
                            OutlinedButton(onClick = { vm.update(i.id) { it.copy(signature = null) } }) { Text("Effacer la signature") }
                        }
                    }
                }
            }
        }
        Section("Informations", subtitle = "Servent au nom du fichier et à la liste des bons") {
            field(DocKeys.CLIENT, "Client / donneur d'ordre")
            field(DocKeys.SITE, "Site / client utilisateur")
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                field(DocKeys.CP, "Code postal", Modifier.weight(1f), keyboard = KeyboardType.Number)
                field(DocKeys.VILLE, "Ville", Modifier.weight(2f))
            }
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                field(DocKeys.REFERENCE, "N° de commande / référence", Modifier.weight(1.4f), caps = KeyboardCapitalization.Characters)
                field(
                    DocKeys.DATE, "Date", Modifier.weight(1f),
                    trailing = {
                        IconButton(onClick = { datePicker = true }) { Icon(Icons.Filled.CalendarMonth, contentDescription = "Choisir la date") }
                    },
                )
            }
        }

        Section("Document") {
            val src = i.source
            if (src != null) {
                Text(src.name, style = MaterialTheme.typography.bodyLarge)
                Text(
                    if (src.pageCount > 1) "Seule la 1re page est complétée ; les ${src.pageCount - 1} page(s) suivante(s) sont conservées telles quelles."
                    else "Document d'une page.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Checkbox(checked = i.includeOriginal, onCheckedChange = { c -> vm.update(i.id) { it.copy(includeOriginal = c) } })
                Text("Ajouter aussi le document d'origine (non complété) à la fin du PDF")
            }
        }

        Section("Documents joints", subtitle = "Ajoutés à la fin du PDF") {
            Attachments(vm, i)
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedButton(onClick = { pickFile.launch(arrayOf("application/pdf")) }) {
                    Icon(Icons.Filled.PictureAsPdf, contentDescription = null)
                    Text("  PDF")
                }
                OutlinedButton(onClick = { pickFile.launch(arrayOf("image/*")) }) {
                    Icon(Icons.Filled.Image, contentDescription = null)
                    Text("  Image")
                }
                OutlinedButton(onClick = {
                    val f = vm.newPhotoFile(i.id)
                    pendingPhoto = f.path
                    try {
                        takePhoto.launch(vm.photoUri(f))
                    } catch (_: ActivityNotFoundException) {
                        pendingPhoto = null
                        vm.message("Aucun appareil photo disponible")
                    }
                }) {
                    Icon(Icons.Filled.AddAPhoto, contentDescription = null)
                    Text("  Photo")
                }
            }
        }

        SendSection(vm, i, settings.initiales, onPreview = {
            scope.launch { if (vm.generate(i.id) != null) vm.navigate(Screen.Viewer(i.id)) }
        }, onSend = { vm.send(context, listOf(i.id)) })

        Spacer(Modifier.height(48.dp))
    }

    if (signing) {
        SignatureDialog(
            title = "Signature du client",
            onDismiss = { signing = false },
            onDone = { sig ->
                signing = false
                vm.update(i.id) { it.copy(signature = sig) }
            },
        )
    }

    if (datePicker) {
        val initial = Naming.parseDate(i.value(DocKeys.DATE)) ?: LocalDate.now()
        val state = rememberDatePickerState(initialSelectedDateMillis = initial.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli())
        DatePickerDialog(
            onDismissRequest = { datePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    state.selectedDateMillis?.let { ms ->
                        vm.setValue(i.id, DocKeys.DATE, Naming.formatShort(Instant.ofEpochMilli(ms).atZone(ZoneOffset.UTC).toLocalDate()))
                    }
                    datePicker = false
                }) { Text("OK") }
            },
            dismissButton = { TextButton(onClick = { datePicker = false }) { Text("Annuler") } },
        ) { DatePicker(state = state) }
    }
}

/** Propositions rapides pour les champs d'une feuille client (heure actuelle, date du jour, durée calculée). */
private fun templateChips(key: String, value: String, i: Intervention): List<String> {
    val k = key.substringAfterLast('.')
    val now = LocalTime.now()
    val out = mutableListOf<String>()
    when {
        k.startsWith("heure") -> out += "%02d:%02d".format(now.hour, now.minute)
        k.startsWith("date") -> out += Naming.today()
        k == "duree" -> {
            val a = parseTime(i.value("if.heureArrivee"))
            val b = parseTime(i.value("if.heureDepart"))
            if (a != null && b != null) {
                val minutes = ((b - a) + 24 * 60) % (24 * 60)
                out += if (minutes >= 60) "${minutes / 60} h ${"%02d".format(minutes % 60)}" else "$minutes min"
            }
        }
    }
    return out.filter { it != value }
}

/** « 8:05 », « 08h05 », « 8h » -> minutes depuis minuit. */
private fun parseTime(s: String): Int? {
    val m = Regex("""(\d{1,2})\s*[:hH]\s*(\d{2})?""").find(s) ?: return null
    val h = m.groupValues[1].toInt()
    val min = m.groupValues[2].ifEmpty { "0" }.toInt()
    return if (h in 0..23 && min in 0..59) h * 60 + min else null
}
