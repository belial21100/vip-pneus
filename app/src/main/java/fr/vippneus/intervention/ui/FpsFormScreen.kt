package fr.vippneus.intervention.ui

import android.content.ActivityNotFoundException
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.AddAPhoto
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Draw
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material3.Button
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Tab
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
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
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import fr.vippneus.intervention.data.Attachment
import fr.vippneus.intervention.data.AttachmentKind
import fr.vippneus.intervention.data.Intervention
import fr.vippneus.intervention.data.Naming
import fr.vippneus.intervention.data.SignatureData
import fr.vippneus.intervention.data.Suggestions
import fr.vippneus.intervention.pdf.Box as PdfBox
import fr.vippneus.intervention.pdf.FpsTemplate
import fr.vippneus.intervention.pdf.FpsTemplate.K
import fr.vippneus.intervention.pdf.PageOps
import fr.vippneus.intervention.pdf.SignatureOp
import kotlinx.coroutines.launch
import java.io.File
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

/** Accès simplifié aux champs du formulaire. */
private class Form(val vm: AppViewModel, val i: Intervention, val suggestions: Map<String, List<String>>) {
    fun value(key: String) = i.value(key)
    fun set(key: String, v: String) = vm.setValue(i.id, key, v)
}

@Composable
private fun Form.Field(
    key: String,
    label: String,
    modifier: Modifier = Modifier,
    keyboard: KeyboardType = KeyboardType.Text,
    caps: KeyboardCapitalization = KeyboardCapitalization.Sentences,
    singleLine: Boolean = true,
    minLines: Int = 1,
    placeholder: String? = null,
    trailing: (@Composable () -> Unit)? = null,
) {
    val v = value(key)
    SuggestField(
        label = label,
        value = v,
        onValueChange = { set(key, it) },
        modifier = modifier,
        suggestions = Suggestions.filter(suggestions, key, v),
        keyboardType = keyboard,
        capitalization = caps,
        singleLine = singleLine,
        minLines = minLines,
        placeholder = placeholder,
        trailing = trailing,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FpsFormScreen(vm: AppViewModel, id: String) {
    val list by vm.interventions.collectAsStateWithLifecycle()
    val i = list.firstOrNull { it.id == id }
    if (i == null) {
        LaunchedEffect(Unit) { vm.back() }
        return
    }
    val suggestions by vm.suggestions.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var tab by rememberSaveable { mutableIntStateOf(0) }

    Scaffold(
        topBar = {
            TopAppBar(
                navigationIcon = {
                    IconButton(onClick = { vm.back() }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Retour") }
                },
                title = {
                    Column {
                        Text("Fiche d'intervention", maxLines = 1)
                        Text(
                            Naming.title(i),
                            style = MaterialTheme.typography.bodyMedium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                },
                actions = {
                    TextButton(onClick = { vm.navigate(Screen.Editor(id)) }) {
                        Icon(Icons.Filled.Tune, contentDescription = null)
                        Text(" Ajuster")
                    }
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
        val form = Form(vm, i, suggestions)
        BoxWithConstraints(
            Modifier
                .padding(padding)
                .fillMaxSize(),
        ) {
            if (maxWidth >= 900.dp) {
                Row(Modifier.fillMaxSize()) {
                    FormColumn(form, Modifier.weight(0.57f))
                    PreviewPane(vm, i, Modifier.weight(0.43f))
                }
            } else {
                Column(Modifier.fillMaxSize()) {
                    PrimaryTabRow(selectedTabIndex = tab) {
                        Tab(selected = tab == 0, onClick = { tab = 0 }, text = { Text("Saisie") })
                        Tab(selected = tab == 1, onClick = { tab = 1 }, text = { Text("Aperçu") })
                    }
                    if (tab == 0) FormColumn(form, Modifier.weight(1f)) else PreviewPane(vm, i, Modifier.weight(1f))
                }
            }
        }
    }
}

@Composable
private fun PreviewPane(vm: AppViewModel, i: Intervention, modifier: Modifier) {
    val measure = rememberMeasure()
    val ops = remember(i) { PageOps.build(i, measure) }
    val background = rememberPageBackground(i, null)
    Column(
        modifier
            .fillMaxHeight()
            .background(MaterialTheme.colorScheme.surfaceContainer)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Aperçu de la fiche", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
            TextButton(onClick = { vm.navigate(Screen.Editor(i.id)) }) {
                Icon(Icons.Filled.Tune, contentDescription = null)
                Text(" Ajuster")
            }
        }
        Box(
            Modifier
                .weight(1f)
                .fillMaxWidth(),
            contentAlignment = Alignment.TopCenter,
        ) {
            PagePreview(
                FpsTemplate.PAGE_W, FpsTemplate.PAGE_H, background, ops,
                Modifier
                    .shadow(4.dp)
                    .clickable { vm.navigate(Screen.Editor(i.id)) },
            )
        }
        Text(
            "Touchez l'aperçu pour déplacer un texte, changer sa taille ou ajouter une mention libre.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FormColumn(form: Form, modifier: Modifier) {
    val vm = form.vm
    val i = form.i
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val settings by vm.settings.collectAsStateWithLifecycle()
    var datePicker by remember { mutableStateOf(false) }
    var signing by remember { mutableStateOf(false) }
    var pendingPhoto by rememberSaveable { mutableStateOf<String?>(null) }

    val pickPdf = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) vm.addAttachment(i.id, uri)
    }
    val pickImage = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) vm.addAttachment(i.id, uri)
    }
    val takePhoto = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { ok ->
        pendingPhoto?.let { vm.onPhotoTaken(i.id, File(it), ok) }
        pendingPhoto = null
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
        Section("Commande et client") {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                form.Field(K.CLIENT_MANDATAIRE, "Client mandataire", Modifier.weight(1f))
                form.Field(K.NUMERO_COMMANDE, "N° de commande", Modifier.weight(1f), caps = KeyboardCapitalization.Characters)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                form.Field(K.MANDATAIRE_ADRESSE, "Adresse / Ville du mandataire", Modifier.weight(2f))
                form.Field(K.MANDATAIRE_CP, "Code postal", Modifier.weight(1f), keyboard = KeyboardType.Number)
            }
            HorizontalDivider()
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                form.Field(K.MONTEUR, "Commercial / Monteur", Modifier.weight(1f))
                form.Field(
                    K.DATE, "Date", Modifier.weight(1f),
                    trailing = {
                        IconButton(onClick = { datePicker = true }) { Icon(Icons.Filled.CalendarMonth, contentDescription = "Choisir la date") }
                    },
                )
            }
            form.Field(K.CLIENT_UTILISATEUR, "Client utilisateur (site d'intervention)", singleLine = false)
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                form.Field(K.UTILISATEUR_ADRESSE, "Adresse / Ville", Modifier.weight(2f))
                form.Field(K.UTILISATEUR_CP, "Code postal", Modifier.weight(1f), keyboard = KeyboardType.Number)
            }
        }

        Section("Matériel") {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                form.Field(K.MARQUE, "Marque", Modifier.weight(1f))
                form.Field(K.TYPE, "Type", Modifier.weight(1f), caps = KeyboardCapitalization.Characters)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                form.Field(K.SERIE, "N° de série", Modifier.weight(1f), caps = KeyboardCapitalization.Characters)
                form.Field(K.PARC, "N° de parc", Modifier.weight(1f), caps = KeyboardCapitalization.Characters)
                form.Field(K.HORAMETRE, "Horamètre", Modifier.weight(1f), keyboard = KeyboardType.Number)
            }
        }

        Section("Fournitures") {
            PneuBlock(form, "av", "Pneus AV")
            HorizontalDivider()
            PneuBlock(form, "ar", "Pneus AR")
            TextButton(onClick = {
                vm.update(i.id) { cur ->
                    var values = cur.values
                    FpsTemplate.pneuColumns.forEach { (col, _, _) ->
                        val v = cur.value(K.pneu("av", col))
                        values = if (v.isEmpty()) values - K.pneu("ar", col) else values + (K.pneu("ar", col) to v)
                    }
                    cur.value(K.AV_FOURNI).let { v -> values = if (v.isEmpty()) values - K.AR_FOURNI else values + (K.AR_FOURNI to v) }
                    cur.copy(values = values)
                }
            }) { Text("Recopier les pneus AV dans AR") }
        }

        Section("Prestations", subtitle = "Indiquer les quantités") {
            PrestationsGrid(form)
            HorizontalDivider()
            YesNo(
                "Déplacement pour prestation < 4 pneus",
                form.value(K.DEPLACEMENT).ifEmpty { null },
                { form.set(K.DEPLACEMENT, it.orEmpty()) },
            )
            form.Field(K.KMS, "Nombre de km départ agence", Modifier.width(320.dp), keyboard = KeyboardType.Number)
        }

        Section("Serrage des roues", subtitle = "Couple en Nm ; la remarque s'écrit à droite de « Nm »") {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                form.Field(K.SERRAGE_AV, "AV (Nm)", Modifier.weight(1f), keyboard = KeyboardType.Number)
                form.Field(K.SERRAGE_AV_REMARQUE, "Remarque AV", Modifier.weight(2.4f))
            }
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                form.Field(K.SERRAGE_AR, "AR (Nm)", Modifier.weight(1f), keyboard = KeyboardType.Number)
                form.Field(K.SERRAGE_AR_REMARQUE, "Remarque AR", Modifier.weight(2.4f), placeholder = "ex. Démonté et remonté par le client")
            }
        }

        Section("Remarques, état des jantes") {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                form.Field(K.JANTES_LUSTREES, "Jantes lustrées", Modifier.weight(1f))
                form.Field(K.JANTES_FISSUREES, "Jantes fissurées", Modifier.weight(1f))
            }
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                form.Field(K.JANTES_HS, "Jantes HS", Modifier.weight(1f))
                form.Field(K.JANTES_AUTRES, "Autres", Modifier.weight(1f))
            }
        }

        Section("Observations") {
            form.Field(K.OBSERVATIONS, "Observations", singleLine = false, minLines = 2)
        }

        Section("Signature du client") {
            form.Field(
                K.SIGNATAIRE, "Nom du signataire / mention", singleLine = false,
                placeholder = "ex. Vu avec le client – Jérôme, technicien",
            )
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

        Section("Documents joints", subtitle = "Ajoutés après la fiche dans le PDF (bon de commande, photos…)") {
            Attachments(vm, i)
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedButton(onClick = { pickPdf.launch(arrayOf("application/pdf")) }) {
                    Icon(Icons.Filled.PictureAsPdf, contentDescription = null)
                    Text("  PDF")
                }
                OutlinedButton(onClick = { pickImage.launch(arrayOf("image/*")) }) {
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

    if (datePicker) {
        val initial = Naming.parseDate(i.value(K.DATE)) ?: LocalDate.now()
        val state = rememberDatePickerState(initialSelectedDateMillis = initial.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli())
        DatePickerDialog(
            onDismissRequest = { datePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    state.selectedDateMillis?.let { ms ->
                        form.set(K.DATE, Naming.formatShort(Instant.ofEpochMilli(ms).atZone(ZoneOffset.UTC).toLocalDate()))
                    }
                    datePicker = false
                }) { Text("OK") }
            },
            dismissButton = { TextButton(onClick = { datePicker = false }) { Text("Annuler") } },
        ) { DatePicker(state = state) }
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
}

@Composable
private fun PneuBlock(form: Form, essieu: String, title: String) {
    val key = if (essieu == "av") K.AV_FOURNI else K.AR_FOURNI
    YesNo(title, form.value(key).ifEmpty { null }, { form.set(key, it.orEmpty()) })
    val cols = FpsTemplate.pneuColumns
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        cols.take(3).forEach { (col, label, _) ->
            form.Field(K.pneu(essieu, col), label, Modifier.weight(1f), caps = if (col == "dimensions") KeyboardCapitalization.None else KeyboardCapitalization.Sentences)
        }
    }
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        cols.drop(3).forEach { (col, label, _) ->
            form.Field(K.pneu(essieu, col), label, Modifier.weight(1f), keyboard = if (col == "quantite") KeyboardType.Number else KeyboardType.Text)
        }
        Spacer(Modifier.weight(1f))
    }
}

@Composable
private fun PrestationsGrid(form: Form) {
    val labelWidth = 150.dp
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Spacer(Modifier.width(labelWidth))
            FpsTemplate.prestationColumns.forEach { (_, label, _) ->
                Text(
                    label.replace(" pouces", "\""),
                    style = MaterialTheme.typography.labelLarge,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.weight(1f),
                )
            }
        }
        FpsTemplate.prestationRows.forEach { (row, rowLabel, _) ->
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(rowLabel, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.width(labelWidth))
                FpsTemplate.prestationColumns.forEach { (col, _, _) ->
                    val key = K.prestation(row, col)
                    OutlinedTextField(
                        value = form.value(key),
                        onValueChange = { form.set(key, it) },
                        singleLine = true,
                        textStyle = LocalTextStyle.current.copy(textAlign = TextAlign.Center, fontSize = 20.sp),
                        keyboardOptions = KeyboardOptions(
                            keyboardType = if (col == "autres") KeyboardType.Text else KeyboardType.Number,
                            imeAction = ImeAction.Next,
                        ),
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }
    }
}

@Composable
fun SignaturePreview(sig: SignatureData?, onClick: () -> Unit) {
    val renderer = rememberRenderer()
    Box(
        Modifier
            .fillMaxWidth()
            .height(130.dp)
            .background(Color.White, MaterialTheme.shapes.medium)
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, MaterialTheme.shapes.medium)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        if (sig == null) {
            Text("Aucune signature – touchez pour faire signer", color = Color(0xFF6B7280))
        } else {
            Canvas(
                Modifier
                    .fillMaxSize()
                    .padding(10.dp),
            ) {
                val op = SignatureOp("sig", sig, PdfBox(0f, 0f, size.width, size.height))
                drawIntoCanvas { renderer.draw(it.nativeCanvas, listOf(op), 1f) }
            }
        }
    }
}

@Composable
fun Attachments(vm: AppViewModel, i: Intervention) {
    if (i.attachments.isEmpty()) {
        Text("Aucun document joint.", color = MaterialTheme.colorScheme.onSurfaceVariant)
        return
    }
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        i.attachments.forEachIndexed { index, a ->
            AttachmentRow(
                a = a,
                canUp = index > 0,
                canDown = index < i.attachments.lastIndex,
                onUp = { vm.moveAttachment(i.id, a.id, -1) },
                onDown = { vm.moveAttachment(i.id, a.id, 1) },
                onDelete = { vm.removeAttachment(i.id, a.id) },
            )
        }
    }
}

@Composable
private fun AttachmentRow(a: Attachment, canUp: Boolean, canDown: Boolean, onUp: () -> Unit, onDown: () -> Unit, onDelete: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(
            if (a.kind == AttachmentKind.PDF) Icons.Filled.PictureAsPdf else Icons.Filled.Image,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
        )
        Column(
            Modifier
                .weight(1f)
                .padding(horizontal = 12.dp),
        ) {
            Text(a.name, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                if (a.kind == AttachmentKind.PDF) "${a.pages} page(s)" else "Photo (1 page)",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        IconButton(onClick = onUp, enabled = canUp) { Icon(Icons.Filled.ArrowUpward, contentDescription = "Monter") }
        IconButton(onClick = onDown, enabled = canDown) { Icon(Icons.Filled.ArrowDownward, contentDescription = "Descendre") }
        IconButton(onClick = onDelete) { Icon(Icons.Filled.Delete, contentDescription = "Retirer", tint = MaterialTheme.colorScheme.error) }
    }
}

/** Nom du fichier et boutons d'envoi (communs aux deux types de bon). */
@Composable
fun SendSection(vm: AppViewModel, i: Intervention, initiales: String, onPreview: () -> Unit, onSend: () -> Unit) {
    val auto = Naming.defaultFileName(i, initiales)
    Section("Envoi à la comptabilité") {
        OutlinedTextField(
            value = i.fileName,
            onValueChange = { v -> vm.update(i.id) { it.copy(fileName = v) } },
            label = { Text("Nom du fichier PDF") },
            placeholder = { Text(auto, maxLines = 1, overflow = TextOverflow.Ellipsis) },
            supportingText = { Text("Laisser vide pour le nom automatique : $auto.pdf") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            OutlinedButton(onClick = onPreview) {
                Icon(Icons.Filled.Visibility, contentDescription = null)
                Text("  Voir le PDF")
            }
            Button(onClick = onSend) {
                Icon(Icons.AutoMirrored.Filled.Send, contentDescription = null)
                Text("  Envoyer à la compta")
            }
        }
    }
}
