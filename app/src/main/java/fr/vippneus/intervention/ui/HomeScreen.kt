package fr.vippneus.intervention.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Assignment
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.AttachFile
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Draw
import androidx.compose.material.icons.filled.EditNote
import androidx.compose.material.icons.filled.Event
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SearchOff
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Tag
import androidx.compose.material.icons.filled.UploadFile
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import fr.vippneus.intervention.data.Completion
import fr.vippneus.intervention.data.DisplayStatus
import fr.vippneus.intervention.data.Intervention
import fr.vippneus.intervention.data.InterventionType
import fr.vippneus.intervention.data.Naming
import fr.vippneus.intervention.data.Settings
import fr.vippneus.intervention.data.displayStatus
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Date
import java.util.Locale

private enum class Filter(val label: String) { TOUS("Tous"), A_ENVOYER("À envoyer"), ENVOYES("Envoyés") }

/** Chiffres du tableau de bord. */
private class Counts(all: List<Intervention>) {
    val total = all.size
    val toSend = all.count { it.displayStatus() != DisplayStatus.ENVOYE }
    val sent = total - toSend
    val sentThisMonth: Int = run {
        val now = LocalDate.now()
        all.count { i ->
            val s = i.sentAt ?: return@count false
            val d = Instant.ofEpochMilli(s).atZone(ZoneId.systemDefault()).toLocalDate()
            d.year == now.year && d.month == now.month
        }
    }

    fun of(f: Filter) = when (f) {
        Filter.TOUS -> total
        Filter.A_ENVOYER -> toSend
        Filter.ENVOYES -> sent
    }
}

/** Accueil : actions principales et liste des bons. */
@Composable
fun HomeScreen(vm: AppViewModel) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val all by vm.interventions.collectAsStateWithLifecycle()
    val loaded by vm.loaded.collectAsStateWithLifecycle()
    val settings by vm.settings.collectAsStateWithLifecycle()
    var query by rememberSaveable { mutableStateOf("") }
    var filter by rememberSaveable { mutableStateOf(Filter.TOUS) }
    var selection by remember { mutableStateOf(setOf<String>()) }
    var confirmDelete by remember { mutableStateOf<Set<String>?>(null) }

    val pickDocument = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) vm.importClient(uri)
    }
    val onImport = { pickDocument.launch(arrayOf("application/pdf", "image/*")) }
    val onBlank = {
        val id = vm.createFps()
        vm.navigate(Screen.Fps(id))
    }
    val onSettings = { vm.navigate(Screen.Settings) }
    val onFilter = { f: Filter -> filter = if (filter == f && f != Filter.TOUS) Filter.TOUS else f }

    val counts = remember(all) { Counts(all) }
    val shown = remember(all, query, filter) {
        val q = query.trim().lowercase(Locale.FRANCE)
        all.sortedByDescending { it.updatedAt }
            .filter {
                when (filter) {
                    Filter.TOUS -> true
                    Filter.A_ENVOYER -> it.displayStatus() != DisplayStatus.ENVOYE
                    Filter.ENVOYES -> it.displayStatus() == DisplayStatus.ENVOYE
                }
            }
            .filter { i ->
                q.isEmpty() || Naming.title(i).lowercase(Locale.FRANCE).contains(q) ||
                    i.values.values.any { it.lowercase(Locale.FRANCE).contains(q) } ||
                    (i.source?.name?.lowercase(Locale.FRANCE)?.contains(q) ?: false)
            }
    }

    val list: @Composable (Modifier) -> Unit = { modifier ->
        BonsList(
            modifier = modifier,
            loaded = loaded,
            empty = all.isEmpty(),
            shown = shown,
            counts = counts,
            query = query,
            onQuery = { query = it },
            filter = filter,
            onFilter = { filter = it },
            settings = settings,
            selection = selection,
            onOpen = { i -> if (selection.isNotEmpty()) selection = selection.toggle(i.id) else vm.openIntervention(i) },
            onToggle = { i -> selection = selection.toggle(i.id) },
            onSend = { i -> vm.send(context, listOf(i.id)) },
            onPreview = { i -> scope.launch { if (vm.generate(i.id) != null) vm.navigate(Screen.Viewer(i.id)) } },
            onDuplicate = { i -> vm.duplicate(i.id) },
            onDelete = { i -> confirmDelete = setOf(i.id) },
            onSettings = onSettings,
        )
    }

    BoxWithConstraints(
        Modifier
            .fillMaxSize()
            .background(Vip.colors.chrome),
    ) {
        val sheet = MaterialTheme.colorScheme.background
        val panelWidth = if (maxWidth >= 1100.dp) 380.dp else 330.dp
        if (maxWidth >= 840.dp) {
            Row(Modifier.fillMaxSize()) {
                SidePanel(settings, counts, filter, onFilter, onImport, onBlank, onSettings, Modifier.width(panelWidth))
                list(
                    Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .statusBarsPadding()
                        .clip(RoundedCornerShape(topStart = 28.dp))
                        .background(sheet),
                )
            }
        } else {
            Column(Modifier.fillMaxSize()) {
                TopHeader(settings, counts, filter, onFilter, onImport, onBlank, onSettings)
                list(
                    Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp))
                        .background(sheet),
                )
            }
        }

        AnimatedVisibility(
            visible = selection.isNotEmpty(),
            enter = slideInVertically { it } + fadeIn(),
            exit = slideOutVertically { it } + fadeOut(),
            modifier = Modifier.align(Alignment.BottomCenter),
        ) {
            SelectionBar(
                count = selection.size,
                onClose = { selection = emptySet() },
                onDelete = { confirmDelete = selection },
                onSend = {
                    val ids = shown.map { it.id }.filter { it in selection }
                    vm.send(context, ids)
                    selection = emptySet()
                },
            )
        }
    }

    confirmDelete?.let { ids ->
        ConfirmDialog(
            title = if (ids.size == 1) "Supprimer ce bon ?" else "Supprimer ${ids.size} bons ?",
            text = "Le bon, sa signature et ses documents joints seront effacés de la tablette.",
            confirmLabel = "Supprimer",
            onDismiss = { confirmDelete = null },
            onConfirm = {
                vm.delete(ids)
                selection = selection - ids
            },
        )
    }
}

private fun Set<String>.toggle(id: String) = if (id in this) this - id else this + id

private fun greeting(technicien: String): String {
    val hello = if (LocalTime.now().hour >= 18) "Bonsoir" else "Bonjour"
    return if (technicien.isBlank()) hello else "$hello ${technicien.trim()}"
}

private fun todayLong(): String =
    LocalDate.now().format(DateTimeFormatter.ofPattern("EEEE d MMMM yyyy", Locale.FRANCE))
        .replaceFirstChar { it.titlecase(Locale.FRANCE) }

// ------------------------------------------------------------------ en-têtes

/** Panneau latéral sombre (tablette à l'horizontale). */
@Composable
private fun SidePanel(
    settings: Settings,
    counts: Counts,
    filter: Filter,
    onFilter: (Filter) -> Unit,
    onImport: () -> Unit,
    onBlank: () -> Unit,
    onSettings: () -> Unit,
    modifier: Modifier,
) {
    val c = Vip.colors
    BoxWithConstraints(
        modifier
            .fillMaxHeight()
            .background(c.chrome),
    ) {
        val tight = maxHeight < 700.dp
        TreadPattern(
            Modifier
                .align(Alignment.BottomEnd)
                .padding(bottom = 96.dp)
                .width(84.dp)
                .height(260.dp),
        )
        Column(
            Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .padding(horizontal = 28.dp, vertical = 24.dp),
        ) {
            Brand()
            Spacer(Modifier.height(if (tight) 16.dp else 36.dp))
            Text(greeting(settings.technicien), style = MaterialTheme.typography.headlineSmall, color = c.onChrome)
            if (!tight) Text(todayLong(), style = MaterialTheme.typography.bodyLarge, color = c.onChromeMuted)
            Spacer(Modifier.height(if (tight) 16.dp else 28.dp))
            ImportCard(onImport, Modifier.fillMaxWidth())
            Spacer(Modifier.height(12.dp))
            VipButton("Nouvelle fiche vierge", onBlank, Modifier.fillMaxWidth(), icon = Icons.Filled.EditNote, tone = Tone.CHROME)
            Spacer(Modifier.height(if (tight) 16.dp else 32.dp))
            Overline("Suivi")
            Spacer(Modifier.height(10.dp))
            Kpis(counts, filter, onFilter, compact = tight)
            Spacer(Modifier.weight(1f))
            TechnicianRow(settings, onSettings)
        }
    }
}

/** En-tête sombre (tablette à la verticale). */
@Composable
private fun TopHeader(
    settings: Settings,
    counts: Counts,
    filter: Filter,
    onFilter: (Filter) -> Unit,
    onImport: () -> Unit,
    onBlank: () -> Unit,
    onSettings: () -> Unit,
) {
    val c = Vip.colors
    Column(
        Modifier
            .fillMaxWidth()
            .background(c.chrome)
            .statusBarsPadding()
            .padding(start = 28.dp, end = 16.dp, top = 16.dp, bottom = 24.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Brand(Modifier.weight(1f))
            IconButton(onClick = onSettings, modifier = Modifier.size(52.dp)) {
                Icon(Icons.Filled.Settings, contentDescription = "Réglages", tint = c.onChrome)
            }
        }
        Spacer(Modifier.height(20.dp))
        Row(Modifier.padding(end = 12.dp), verticalAlignment = Alignment.Bottom) {
            Column(Modifier.weight(1f)) {
                Text(greeting(settings.technicien), style = MaterialTheme.typography.headlineSmall, color = c.onChrome)
                Text(todayLong(), style = MaterialTheme.typography.bodyLarge, color = c.onChromeMuted)
            }
        }
        Spacer(Modifier.height(20.dp))
        Row(Modifier.padding(end = 12.dp), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
            ImportCard(onImport, Modifier.weight(1.25f))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                VipButton("Nouvelle fiche vierge", onBlank, Modifier.fillMaxWidth(), icon = Icons.Filled.EditNote, tone = Tone.CHROME)
                Kpis(counts, filter, onFilter, compact = true)
            }
        }
    }
}

@Composable
private fun Brand(modifier: Modifier = Modifier) {
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        BrandMark(52.dp)
        Spacer(Modifier.width(14.dp))
        Column {
            Wordmark(fontSize = 28.sp)
            Text("Bons d'intervention", style = MaterialTheme.typography.bodyMedium, color = Vip.colors.onChromeMuted)
        }
    }
}

@Composable
private fun Overline(text: String) {
    Text(
        text.uppercase(Locale.FRANCE),
        style = MaterialTheme.typography.labelSmall,
        color = Vip.colors.onChromeMuted,
        letterSpacing = 1.6.sp,
    )
}

/** Action principale : importer le PDF envoyé par le client. */
@Composable
private fun ImportCard(onClick: () -> Unit, modifier: Modifier = Modifier) {
    val c = Vip.colors
    val ink = Palette.Graphite900
    Surface(onClick = onClick, color = c.accent, contentColor = ink, shape = MaterialTheme.shapes.large, modifier = modifier) {
        Row(Modifier.padding(horizontal = 18.dp, vertical = 18.dp), verticalAlignment = Alignment.CenterVertically) {
            IconBadge(Icons.Filled.UploadFile, background = ink.copy(alpha = 0.1f), tint = ink, size = 52.dp)
            Spacer(Modifier.width(16.dp))
            Column(Modifier.weight(1f)) {
                Text("Importer un PDF client", style = MaterialTheme.typography.titleMedium)
                Text(
                    "Conti, Mac2, Mastra… les champs se remplissent tout seuls",
                    style = MaterialTheme.typography.bodySmall,
                    color = ink.copy(alpha = 0.75f),
                )
            }
        }
    }
}

@Composable
private fun Kpis(counts: Counts, filter: Filter, onFilter: (Filter) -> Unit, compact: Boolean = false) {
    val c = Vip.colors
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        KpiTile(
            counts.toSend, "À envoyer", c.accent, filter == Filter.A_ENVOYER, compact,
            { onFilter(Filter.A_ENVOYER) }, Modifier.weight(1f),
        )
        KpiTile(
            counts.sentThisMonth, "Envoyés ce mois", Color(0xFF53C285), filter == Filter.ENVOYES, compact,
            { onFilter(Filter.ENVOYES) }, Modifier.weight(1f),
        )
    }
}

@Composable
private fun KpiTile(
    value: Int,
    label: String,
    accent: Color,
    selected: Boolean,
    compact: Boolean,
    onClick: () -> Unit,
    modifier: Modifier,
) {
    val c = Vip.colors
    Surface(
        onClick = onClick,
        shape = MaterialTheme.shapes.medium,
        color = c.chromeHigh,
        contentColor = c.onChrome,
        border = if (selected) BorderStroke(2.dp, accent) else BorderStroke(1.dp, Color.White.copy(alpha = 0.06f)),
        modifier = modifier,
    ) {
        Column(Modifier.padding(horizontal = 16.dp, vertical = if (compact) 8.dp else 14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier
                        .size(8.dp)
                        .background(accent, CircleShape),
                )
                Spacer(Modifier.width(8.dp))
                Text(label, style = MaterialTheme.typography.labelMedium, color = c.onChromeMuted, maxLines = 2)
            }
            Text(
                "$value",
                style = if (compact) MaterialTheme.typography.headlineMedium else MaterialTheme.typography.displaySmall,
                fontFamily = BarlowCondensed,
            )
        }
    }
}

@Composable
private fun TechnicianRow(settings: Settings, onSettings: () -> Unit) {
    val c = Vip.colors
    val initials = settings.initiales.ifBlank {
        settings.technicien.split(Regex("[\\s.]+")).filter { it.isNotEmpty() }.take(2).joinToString("") { it.take(1) }
    }.uppercase(Locale.FRANCE).ifBlank { "?" }
    Surface(onClick = onSettings, color = Color.Transparent, contentColor = c.onChrome, shape = MaterialTheme.shapes.medium) {
        Row(Modifier.padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier
                    .size(44.dp)
                    .background(c.accent, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Text(initials.take(3), style = MaterialTheme.typography.titleSmall, color = Palette.Graphite900)
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(settings.technicien.ifBlank { "Technicien à renseigner" }, style = MaterialTheme.typography.titleSmall)
                Text(
                    settings.emailCompta.ifBlank { "E-mail de la compta à renseigner" },
                    style = MaterialTheme.typography.bodySmall,
                    color = c.onChromeMuted,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Icon(Icons.Filled.Settings, contentDescription = "Réglages", tint = c.onChromeMuted)
        }
    }
}

// ------------------------------------------------------------------ liste

@Composable
private fun BonsList(
    modifier: Modifier,
    loaded: Boolean,
    empty: Boolean,
    shown: List<Intervention>,
    counts: Counts,
    query: String,
    onQuery: (String) -> Unit,
    filter: Filter,
    onFilter: (Filter) -> Unit,
    settings: Settings,
    selection: Set<String>,
    onOpen: (Intervention) -> Unit,
    onToggle: (Intervention) -> Unit,
    onSend: (Intervention) -> Unit,
    onPreview: (Intervention) -> Unit,
    onDuplicate: (Intervention) -> Unit,
    onDelete: (Intervention) -> Unit,
    onSettings: () -> Unit,
) {
    val c = Vip.colors
    LazyVerticalGrid(
        columns = GridCells.Adaptive(minSize = 340.dp),
        contentPadding = PaddingValues(start = 28.dp, end = 28.dp, top = 28.dp, bottom = 120.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        modifier = modifier,
    ) {
        item(span = { GridItemSpan(maxLineSpan) }) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Mes bons", style = MaterialTheme.typography.headlineMedium)
                    Text(
                        when (counts.total) {
                            0 -> "Aucun bon enregistré"
                            1 -> "1 bon · ${counts.toSend} à envoyer"
                            else -> "${counts.total} bons · ${counts.toSend} à envoyer"
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        color = c.muted,
                    )
                }
                Spacer(Modifier.width(16.dp))
                SearchField(query, onQuery, Modifier.widthIn(min = 240.dp, max = 380.dp))
            }
        }
        item(span = { GridItemSpan(maxLineSpan) }) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Filter.entries.forEach { f -> FilterPill(f.label, counts.of(f), filter == f) { onFilter(f) } }
            }
        }
        if (settings.emailCompta.isBlank() || settings.technicien.isBlank()) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                InfoBanner(
                    icon = Icons.Filled.Settings,
                    title = "Réglages à compléter",
                    text = "Indiquez votre nom, vos initiales et l'adresse e-mail de la comptabilité.",
                    action = { VipButton("Ouvrir les réglages", onSettings, tone = Tone.DARK, compact = true) },
                )
            }
        }
        if (loaded && empty) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                EmptyState(
                    Icons.Filled.UploadFile,
                    "Aucun bon pour l'instant",
                    "Importez le PDF envoyé par le client : la fiche se remplit toute seule. " +
                        "Vous pouvez aussi ouvrir le PDF depuis la messagerie avec « Ouvrir avec » VIP Pneus.",
                ) { StepsGuide(Modifier.padding(top = 16.dp)) }
            }
        } else if (loaded && shown.isEmpty()) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                EmptyState(Icons.Filled.SearchOff, "Aucun bon ne correspond", "Modifiez la recherche ou le filtre.") {
                    VipButton("Tout afficher", { onQuery(""); onFilter(Filter.TOUS) }, tone = Tone.GHOST, compact = true)
                }
            }
        }
        items(shown, key = { it.id }) { i ->
            InterventionCard(
                i = i,
                selected = i.id in selection,
                selectionMode = selection.isNotEmpty(),
                onClick = { onOpen(i) },
                onLongClick = { onToggle(i) },
                onSend = { onSend(i) },
                onPreview = { onPreview(i) },
                onDuplicate = { onDuplicate(i) },
                onDelete = { onDelete(i) },
            )
        }
    }
}

@Composable
private fun SearchField(query: String, onQuery: (String) -> Unit, modifier: Modifier) {
    OutlinedTextField(
        value = query,
        onValueChange = onQuery,
        placeholder = { Text("Client, n° de commande, ville…", maxLines = 1, overflow = TextOverflow.Ellipsis) },
        leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
        trailingIcon = if (query.isNotEmpty()) {
            { IconButton(onClick = { onQuery("") }) { Icon(Icons.Filled.Close, contentDescription = "Effacer la recherche") } }
        } else null,
        singleLine = true,
        shape = CircleShape,
        colors = vipFieldColors(),
        modifier = modifier,
    )
}

@Composable
private fun FilterPill(label: String, count: Int, selected: Boolean, onClick: () -> Unit) {
    val c = Vip.colors
    Surface(
        onClick = onClick,
        shape = CircleShape,
        color = if (selected) MaterialTheme.colorScheme.inverseSurface else c.card,
        contentColor = if (selected) MaterialTheme.colorScheme.inverseOnSurface else MaterialTheme.colorScheme.onSurface,
        border = if (selected) null else BorderStroke(1.dp, c.cardBorder),
    ) {
        Row(
            Modifier
                .heightIn(min = 42.dp)
                .padding(start = 16.dp, end = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(label, style = MaterialTheme.typography.labelLarge)
            Spacer(Modifier.width(8.dp))
            Box(
                Modifier
                    .background(if (selected) c.accent else MaterialTheme.colorScheme.surfaceContainerHigh, CircleShape)
                    .padding(horizontal = 9.dp, vertical = 2.dp),
            ) {
                Text(
                    "$count",
                    style = MaterialTheme.typography.labelMedium,
                    color = if (selected) Palette.Graphite900 else c.muted,
                )
            }
        }
    }
}

/** Les trois étapes, pour la première utilisation. */
@Composable
private fun StepsGuide(modifier: Modifier = Modifier) {
    val steps = listOf(
        Triple(Icons.Filled.UploadFile, "Importez le PDF du client", "Bon de commande Conti ou Mac2, feuille de tâche Mastra…"),
        Triple(Icons.Filled.Draw, "Complétez sur place", "Horamètre, serrage, observations, puis signature du client."),
        Triple(Icons.AutoMirrored.Filled.Send, "Envoyez à la compta", "Le PDF est nommé automatiquement et joint à l'e-mail."),
    )
    Row(
        modifier
            .widthIn(max = 900.dp)
            .height(IntrinsicSize.Max),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        steps.forEachIndexed { index, (icon, title, text) ->
            SectionCard(
                title = "${index + 1}. $title",
                icon = icon,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight(),
            ) {
                Text(text, style = MaterialTheme.typography.bodyMedium, color = Vip.colors.muted)
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun InterventionCard(
    i: Intervention,
    selected: Boolean,
    selectionMode: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    onSend: () -> Unit,
    onPreview: () -> Unit,
    onDuplicate: () -> Unit,
    onDelete: () -> Unit,
) {
    val c = Vip.colors
    val status = i.displayStatus()
    val todos = remember(i) { Completion.todos(i) }
    val done = todos.count { it.done }
    val shape = MaterialTheme.shapes.large
    Surface(
        shape = shape,
        color = if (selected) c.accentSoft else c.card,
        border = BorderStroke(if (selected) 2.dp else 1.dp, if (selected) c.accent else c.cardBorder),
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .combinedClickable(onClick = onClick, onLongClick = onLongClick),
    ) {
        Row(Modifier.height(IntrinsicSize.Min)) {
            Box(
                Modifier
                    .width(6.dp)
                    .fillMaxHeight()
                    .background(statusColor(status)),
            )
            Column(
                Modifier
                    .weight(1f)
                    .padding(start = 16.dp, top = 16.dp, bottom = 16.dp, end = 4.dp),
            ) {
                Row(verticalAlignment = Alignment.Top) {
                    val (icon, bg, tint) = when {
                        selected -> Triple(Icons.Filled.CheckCircle, c.accent, Palette.Graphite900)
                        i.type == InterventionType.FPS -> Triple(Icons.AutoMirrored.Filled.Assignment, c.chromeHigh, c.accent)
                        i.template != null -> Triple(Icons.Filled.Draw, c.chromeHigh, c.accent)
                        else -> Triple(Icons.Filled.PictureAsPdf, c.chromeHigh, c.accent)
                    }
                    IconBadge(icon, background = bg, tint = tint, size = 44.dp)
                    Spacer(Modifier.width(14.dp))
                    Column(Modifier.weight(1f)) {
                        Text(Naming.title(i), style = MaterialTheme.typography.titleMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        Text(
                            if (i.type == InterventionType.FPS) Naming.typeLabel(i.type) else i.recognized ?: Naming.typeLabel(i.type),
                            style = MaterialTheme.typography.bodySmall,
                            color = c.muted,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    if (!selectionMode) {
                        CardMenu(i, onSend, onPreview, onDuplicate, onDelete)
                    } else {
                        Spacer(Modifier.width(12.dp))
                    }
                }
                Spacer(Modifier.height(14.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Naming.reference(i).takeIf { it.isNotEmpty() }?.let { Meta(Icons.Filled.Tag, it, Modifier.weight(1f, fill = false)) }
                    Meta(Icons.Filled.Event, Naming.formatShort(Naming.interventionDate(i)))
                    if (i.attachments.isNotEmpty()) Meta(Icons.Filled.AttachFile, "${i.attachments.size}")
                }
                Spacer(Modifier.height(14.dp))
                Row(Modifier.padding(end = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                    StatusChip(status)
                    Spacer(Modifier.weight(1f))
                    val sent = i.sentAt
                    if (status == DisplayStatus.ENVOYE && sent != null) {
                        Text(
                            "le " + SimpleDateFormat("dd/MM à HH:mm", Locale.FRANCE).format(Date(sent)),
                            style = MaterialTheme.typography.bodySmall,
                            color = c.muted,
                        )
                    } else if (todos.isNotEmpty()) {
                        CompletionMeter(done, todos.size)
                    }
                }
            }
        }
    }
}

@Composable
private fun Meta(icon: ImageVector, text: String, modifier: Modifier = Modifier) {
    val c = Vip.colors
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, contentDescription = null, tint = c.muted, modifier = Modifier.size(16.dp))
        Spacer(Modifier.width(5.dp))
        Text(text, style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun CompletionMeter(done: Int, total: Int) {
    val c = Vip.colors
    val complete = done >= total
    Row(verticalAlignment = Alignment.CenterVertically) {
        LinearProgressIndicator(
            progress = { if (total == 0) 1f else done.toFloat() / total },
            color = if (complete) c.success else c.accent,
            trackColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            strokeCap = StrokeCap.Round,
            gapSize = 0.dp,
            drawStopIndicator = {},
            modifier = Modifier
                .width(64.dp)
                .height(6.dp),
        )
        Spacer(Modifier.width(8.dp))
        Text(
            if (complete) "Complet" else "$done/$total",
            style = MaterialTheme.typography.labelMedium,
            color = if (complete) c.success else c.muted,
        )
    }
}

@Composable
private fun CardMenu(i: Intervention, onSend: () -> Unit, onPreview: () -> Unit, onDuplicate: () -> Unit, onDelete: () -> Unit) {
    var menu by remember { mutableStateOf(false) }
    val c = Vip.colors
    Box {
        IconButton(onClick = { menu = true }) { Icon(Icons.Filled.MoreVert, contentDescription = "Actions", tint = c.muted) }
        DropdownMenu(
            expanded = menu,
            onDismissRequest = { menu = false },
            containerColor = c.card,
            shape = MaterialTheme.shapes.medium,
        ) {
            DropdownMenuItem(
                text = { Text("Envoyer à la compta") },
                leadingIcon = { Icon(Icons.AutoMirrored.Filled.Send, null) },
                onClick = { menu = false; onSend() },
            )
            DropdownMenuItem(
                text = { Text("Voir le PDF") },
                leadingIcon = { Icon(Icons.Filled.Visibility, null) },
                onClick = { menu = false; onPreview() },
            )
            if (i.type == InterventionType.FPS) {
                DropdownMenuItem(
                    text = { Text("Nouvelle fiche pour ce client") },
                    leadingIcon = { Icon(Icons.Filled.ContentCopy, null) },
                    onClick = { menu = false; onDuplicate() },
                )
            }
            DropdownMenuItem(
                text = { Text("Supprimer", color = c.danger) },
                leadingIcon = { Icon(Icons.Filled.Delete, null, tint = c.danger) },
                onClick = { menu = false; onDelete() },
            )
        }
    }
}

/** Barre flottante de la sélection multiple (appui long sur un bon). */
@Composable
private fun SelectionBar(count: Int, onClose: () -> Unit, onDelete: () -> Unit, onSend: () -> Unit) {
    val c = Vip.colors
    Surface(
        color = c.chrome,
        contentColor = c.onChrome,
        shape = RoundedCornerShape(24.dp),
        shadowElevation = 16.dp,
        modifier = Modifier
            .navigationBarsPadding()
            .padding(bottom = 24.dp),
    ) {
        Row(Modifier.padding(horizontal = 10.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onClose) { Icon(Icons.Filled.Close, contentDescription = "Annuler la sélection") }
            Text(if (count == 1) "1 bon sélectionné" else "$count bons sélectionnés", style = MaterialTheme.typography.titleSmall)
            Spacer(Modifier.width(28.dp))
            VipButton("Supprimer", onDelete, icon = Icons.Filled.Delete, tone = Tone.CHROME, compact = true)
            Spacer(Modifier.width(10.dp))
            VipButton("Envoyer à la compta", onSend, icon = Icons.AutoMirrored.Filled.Send, compact = true)
        }
    }
}
