package fr.vippneus.intervention.ui

import android.content.ActivityNotFoundException
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddAPhoto
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.AttachFile
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Draw
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRowDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInParent
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import fr.vippneus.intervention.data.Attachment
import fr.vippneus.intervention.data.AttachmentKind
import fr.vippneus.intervention.data.Intervention
import fr.vippneus.intervention.data.InterventionType
import fr.vippneus.intervention.data.Naming
import fr.vippneus.intervention.data.SignatureData
import fr.vippneus.intervention.data.Todo
import fr.vippneus.intervention.pdf.Box as PdfBox
import fr.vippneus.intervention.pdf.PageOps
import fr.vippneus.intervention.pdf.SignatureOp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import java.io.File
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import kotlin.math.roundToInt

// ------------------------------------------------------------------ navigation dans le formulaire

/** Mémorise la position des sections d'un formulaire pour y aller directement (sommaire, « À compléter »). */
@Stable
class FormNav(val scroll: ScrollState, private val scope: CoroutineScope, private val margin: Int) {
    private val anchors = HashMap<String, Int>()
    private val focus = HashMap<String, FocusRequester>()

    /** À poser sur une carte enfant directe de la colonne défilante. */
    fun anchor(section: String): Modifier =
        Modifier.onGloballyPositioned { anchors[section] = it.positionInParent().y.roundToInt() }

    fun focus(key: String): FocusRequester = focus.getOrPut(key) { FocusRequester() }

    fun go(section: String, key: String? = null) {
        scope.launch {
            val y = anchors[section] ?: return@launch
            scroll.animateScrollTo((y - margin).coerceAtLeast(0))
            if (key != null) focus[key]?.let { runCatching { it.requestFocus() } }
        }
    }
}

@Composable
fun rememberFormNav(): FormNav {
    val scroll = rememberScrollState()
    val scope = rememberCoroutineScope()
    val margin = with(LocalDensity.current) { 12.dp.roundToPx() }
    return remember(scroll, scope) { FormNav(scroll, scope, margin) }
}

/** Sommaire du formulaire : un appui fait défiler jusqu'à la section ; point ambre = élément manquant. */
@Composable
fun SectionNavRow(sections: List<Pair<String, String>>, incomplete: Set<String>, onClick: (String) -> Unit) {
    val c = Vip.colors
    Surface(color = MaterialTheme.colorScheme.background) {
        Column {
            Row(
                Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp, vertical = 10.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                sections.forEach { (id, label) ->
                    Surface(
                        onClick = { onClick(id) },
                        shape = CircleShape,
                        color = c.card,
                        border = BorderStroke(1.dp, c.cardBorder),
                    ) {
                        Row(
                            Modifier
                                .heightIn(min = 40.dp)
                                .padding(horizontal = 14.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            if (id in incomplete) {
                                Box(
                                    Modifier
                                        .size(8.dp)
                                        .background(c.accent, CircleShape),
                                )
                                Spacer(Modifier.width(8.dp))
                            }
                            Text(label, style = MaterialTheme.typography.labelLarge)
                        }
                    }
                }
            }
            HorizontalDivider(color = c.cardBorder)
        }
    }
}

/** Onglets sombres sous la barre de titre (écrans étroits : saisie / aperçu). */
@Composable
fun ScreenTabs(labels: List<String>, selected: Int, onSelect: (Int) -> Unit) {
    val c = Vip.colors
    PrimaryTabRow(
        selectedTabIndex = selected,
        containerColor = c.chrome,
        contentColor = c.onChrome,
        indicator = {
            TabRowDefaults.PrimaryIndicator(
                modifier = Modifier.tabIndicatorOffset(selected, matchContentSize = true),
                width = Dp.Unspecified,
                color = c.accent,
            )
        },
        divider = {},
    ) {
        labels.forEachIndexed { index, label ->
            Tab(
                selected = selected == index,
                onClick = { onSelect(index) },
                selectedContentColor = c.onChrome,
                unselectedContentColor = c.onChromeMuted,
                text = { Text(label, style = MaterialTheme.typography.labelLarge) },
            )
        }
    }
}

// ------------------------------------------------------------------ date

/** Icône calendrier (dans un champ date) ouvrant le choix de la date. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DatePickerIcon(current: String, onPick: (String) -> Unit) {
    var open by remember { mutableStateOf(false) }
    IconButton(onClick = { open = true }) { Icon(Icons.Filled.CalendarMonth, contentDescription = "Choisir la date") }
    if (open) {
        val initial = Naming.parseDate(current) ?: LocalDate.now()
        val state = rememberDatePickerState(initialSelectedDateMillis = initial.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli())
        DatePickerDialog(
            onDismissRequest = { open = false },
            confirmButton = {
                TextButton(onClick = {
                    state.selectedDateMillis?.let { ms ->
                        onPick(Naming.formatShort(Instant.ofEpochMilli(ms).atZone(ZoneOffset.UTC).toLocalDate()))
                    }
                    open = false
                }) { Text("OK") }
            },
            dismissButton = { TextButton(onClick = { open = false }) { Text("Annuler") } },
        ) { DatePicker(state = state) }
    }
}

// ------------------------------------------------------------------ signature

/** Aperçu de la signature ; un appui ouvre la saisie. */
@Composable
fun SignaturePreview(sig: SignatureData?, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val renderer = rememberRenderer()
    val c = Vip.colors
    val shape = MaterialTheme.shapes.medium
    Box(
        modifier
            .fillMaxWidth()
            .height(150.dp)
            .background(Color.White, shape)
            .border(
                if (sig == null) BorderStroke(1.5.dp, c.accent) else BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                shape,
            )
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        if (sig == null) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(Icons.Filled.Draw, contentDescription = null, tint = Palette.AmberDeep, modifier = Modifier.size(32.dp))
                Spacer(Modifier.height(8.dp))
                Text("Touchez pour faire signer le client", style = MaterialTheme.typography.titleSmall, color = Palette.Graphite800)
            }
        } else {
            Canvas(
                Modifier
                    .fillMaxSize()
                    .padding(12.dp),
            ) {
                val op = SignatureOp("sig", sig, PdfBox(0f, 0f, size.width, size.height))
                drawIntoCanvas { renderer.draw(it.nativeCanvas, listOf(op), 1f) }
            }
        }
    }
}

/**
 * Carte « Signature du client » : nom du signataire (ou mention), signature, boutons.
 * [nameKey] : champ du nom écrit sur le document (null s'il n'y en a pas).
 */
@Composable
fun SignatureCard(
    vm: AppViewModel,
    i: Intervention,
    nameKey: String?,
    nameLabel: String,
    onSign: () -> Unit,
    modifier: Modifier = Modifier,
    namePlaceholder: String? = null,
    focus: FocusRequester? = null,
) {
    SectionCard(
        "Signature du client",
        icon = Icons.Filled.Draw,
        subtitle = if (i.signature == null) "À faire en fin d'intervention" else "Signé",
        modifier = modifier,
    ) {
        if (nameKey != null) {
            VipField(
                label = nameLabel,
                value = i.value(nameKey),
                onValueChange = { vm.setValue(i.id, nameKey, it) },
                auto = i.isAuto(nameKey),
                singleLine = i.type != InterventionType.FPS,
                placeholder = namePlaceholder,
                capitalization = androidx.compose.ui.text.input.KeyboardCapitalization.Words,
                focusRequester = focus,
            )
        }
        SignaturePreview(i.signature, onClick = onSign)
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            VipButton(
                if (i.signature == null) "Faire signer" else "Refaire la signature",
                onSign,
                icon = Icons.Filled.Draw,
                tone = if (i.signature == null) Tone.ACCENT else Tone.GHOST,
            )
            if (i.signature != null) {
                VipButton("Effacer", { vm.update(i.id) { it.copy(signature = null) } }, icon = Icons.Filled.Close, tone = Tone.GHOST)
            }
        }
    }
}

// ------------------------------------------------------------------ contenu du PDF

/** Contenu du PDF envoyé : page 1, original éventuel, documents joints (ordre modifiable), ajout. */
@Composable
fun PdfContentCard(vm: AppViewModel, i: Intervention, modifier: Modifier = Modifier) {
    val c = Vip.colors
    var pendingPhoto by rememberSaveable { mutableStateOf<String?>(null) }
    val pick = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) vm.addAttachment(i.id, uri)
    }
    val takePhoto = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { ok ->
        pendingPhoto?.let { vm.onPhotoTaken(i.id, File(it), ok) }
        pendingPhoto = null
    }
    val isDoc = i.type == InterventionType.DOCUMENT
    var page = 1
    fun range(n: Int): String {
        val from = page
        page += n
        return if (n <= 1) "Page $from" else "Pages $from–${from + n - 1}"
    }

    SectionCard(
        "Contenu du PDF",
        icon = Icons.Filled.AttachFile,
        subtitle = "Dans l'ordre de l'envoi à la comptabilité",
        modifier = modifier,
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            PdfPartRow(
                range(1),
                if (isDoc) "Document rempli par le technicien" else "Fiche d'intervention presse mobile",
                icon = Icons.Filled.Draw,
            )
            val src = i.source
            if (isDoc && src != null) {
                PdfPartRow(
                    if (i.includeOriginal) range(src.pageCount) else "—",
                    "Document d'origine (${src.name})",
                    icon = Icons.Filled.PictureAsPdf,
                    muted = !i.includeOriginal,
                    trailing = {
                        Switch(
                            checked = i.includeOriginal,
                            onCheckedChange = { v -> vm.update(i.id) { it.copy(includeOriginal = v) } },
                            colors = SwitchDefaults.colors(checkedTrackColor = c.chromeHigh, checkedThumbColor = c.accent),
                        )
                    },
                )
            }
            i.attachments.forEachIndexed { index, a ->
                AttachmentRow(
                    pages = range(a.pages),
                    a = a,
                    canUp = index > 0,
                    canDown = index < i.attachments.lastIndex,
                    onUp = { vm.moveAttachment(i.id, a.id, -1) },
                    onDown = { vm.moveAttachment(i.id, a.id, 1) },
                    onDelete = { vm.removeAttachment(i.id, a.id) },
                )
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            VipButton("Joindre un PDF", { pick.launch(arrayOf("application/pdf")) }, icon = Icons.Filled.PictureAsPdf, tone = Tone.GHOST, compact = true)
            VipButton("Image", { pick.launch(arrayOf("image/*")) }, icon = Icons.Filled.Image, tone = Tone.GHOST, compact = true)
            VipButton(
                "Photo",
                {
                    val f = vm.newPhotoFile(i.id)
                    pendingPhoto = f.path
                    try {
                        takePhoto.launch(vm.photoUri(f))
                    } catch (_: ActivityNotFoundException) {
                        pendingPhoto = null
                        vm.message("Aucun appareil photo disponible")
                    }
                },
                icon = Icons.Filled.AddAPhoto,
                tone = Tone.GHOST,
                compact = true,
            )
        }
    }
}

@Composable
private fun PdfPartRow(
    pages: String,
    title: String,
    icon: ImageVector,
    muted: Boolean = false,
    subtitle: String? = null,
    trailing: (@Composable () -> Unit)? = null,
) {
    val c = Vip.colors
    Row(
        Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceContainerLow, MaterialTheme.shapes.small)
            .heightIn(min = 56.dp)
            .padding(horizontal = 12.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            pages,
            style = MaterialTheme.typography.labelMedium,
            color = c.muted,
            modifier = Modifier.width(78.dp),
        )
        Icon(icon, contentDescription = null, tint = if (muted) c.muted else MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
        Column(
            Modifier
                .weight(1f)
                .padding(horizontal = 12.dp),
        ) {
            Text(
                title,
                style = MaterialTheme.typography.bodyMedium,
                color = if (muted) c.muted else MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (subtitle != null) {
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = c.muted)
            }
        }
        trailing?.invoke()
    }
}

@Composable
private fun AttachmentRow(
    pages: String,
    a: Attachment,
    canUp: Boolean,
    canDown: Boolean,
    onUp: () -> Unit,
    onDown: () -> Unit,
    onDelete: () -> Unit,
) {
    PdfPartRow(
        pages,
        a.name,
        icon = if (a.kind == AttachmentKind.PDF) Icons.Filled.PictureAsPdf else Icons.Filled.Image,
        subtitle = if (a.kind == AttachmentKind.PDF) "Document joint" else "Photo",
        trailing = {
            Row {
                IconButton(onClick = onUp, enabled = canUp) { Icon(Icons.Filled.ArrowUpward, contentDescription = "Monter") }
                IconButton(onClick = onDown, enabled = canDown) { Icon(Icons.Filled.ArrowDownward, contentDescription = "Descendre") }
                IconButton(onClick = onDelete) {
                    Icon(Icons.Filled.Close, contentDescription = "Retirer", tint = Vip.colors.danger)
                }
            }
        },
    )
}

// ------------------------------------------------------------------ envoi

/**
 * Action « Envoyer » : s'il manque des éléments, le technicien est prévenu avant
 * (compléter, ou envoyer quand même).
 */
@Composable
fun rememberSendAction(vm: AppViewModel, i: Intervention, todos: List<Todo>): () -> Unit {
    val context = LocalContext.current
    var ask by remember { mutableStateOf(false) }
    val missing = todos.filterNot { it.done }
    if (ask) {
        MissingDialog(
            missing = missing.map { it.label },
            onDismiss = { ask = false },
            onSendAnyway = {
                ask = false
                vm.send(context, listOf(i.id))
            },
        )
    }
    return {
        if (missing.isNotEmpty()) ask = true else vm.send(context, listOf(i.id))
    }
}

// ------------------------------------------------------------------ aperçu de la page

/** Page 1 sur fond sombre (aperçu non interactif) ; un appui ouvre l'ajustement. */
@Composable
fun PagePreviewPane(vm: AppViewModel, i: Intervention, modifier: Modifier = Modifier) {
    val c = Vip.colors
    val measure = rememberMeasure()
    val ops = remember(i) { PageOps.build(i, measure) }
    val (pageW, pageH) = PageOps.pageSize(i)
    val background = rememberPageBackground(i, vm.sourceFile(i))
    val pages = 1 + i.attachments.sumOf { it.pages }
    Column(
        modifier
            .fillMaxSize()
            .background(c.canvas)
            .padding(horizontal = 24.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Aperçu de la page 1", style = MaterialTheme.typography.titleSmall, color = c.onChrome)
                Text(
                    if (pages == 1) "PDF d'une page" else "PDF de $pages pages (documents joints ensuite)",
                    style = MaterialTheme.typography.bodySmall,
                    color = c.onChromeMuted,
                )
            }
            VipButton("Ajuster", { vm.navigate(Screen.Editor(i.id)) }, icon = Icons.Filled.Tune, tone = Tone.CHROME, compact = true)
        }
        Box(
            Modifier
                .weight(1f)
                .fillMaxWidth(),
            contentAlignment = Alignment.TopCenter,
        ) {
            PagePreview(
                pageW, pageH, background, ops,
                Modifier
                    .shadow(18.dp, RoundedCornerShape(3.dp))
                    .clickable { vm.navigate(Screen.Editor(i.id)) },
            )
        }
        Text(
            "Touchez la page pour déplacer un texte, changer sa taille ou ajouter une mention.",
            style = MaterialTheme.typography.bodySmall,
            color = c.onChromeMuted,
            modifier = Modifier.align(Alignment.CenterHorizontally),
        )
    }
}
