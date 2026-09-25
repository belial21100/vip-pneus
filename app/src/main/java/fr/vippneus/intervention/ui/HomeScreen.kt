package fr.vippneus.intervention.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.EditNote
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.UploadFile
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import fr.vippneus.intervention.data.DisplayStatus
import fr.vippneus.intervention.data.Intervention
import fr.vippneus.intervention.data.InterventionType
import fr.vippneus.intervention.data.Naming
import fr.vippneus.intervention.data.displayStatus
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private enum class Filter(val label: String) { TOUS("Tous"), A_ENVOYER("À envoyer"), ENVOYES("Envoyés") }

@OptIn(ExperimentalMaterial3Api::class)
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

    Scaffold(
        topBar = {
            if (selection.isEmpty()) {
                TopAppBar(
                    title = {
                        Column {
                            Text("VIP Pneus", fontWeight = FontWeight.Bold)
                            Text("Bons d'intervention", style = MaterialTheme.typography.bodyMedium)
                        }
                    },
                    actions = {
                        IconButton(onClick = { vm.navigate(Screen.Settings) }) {
                            Icon(Icons.Filled.Settings, contentDescription = "Réglages")
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        titleContentColor = MaterialTheme.colorScheme.onPrimary,
                        actionIconContentColor = MaterialTheme.colorScheme.onPrimary,
                    ),
                )
            } else {
                TopAppBar(
                    navigationIcon = {
                        IconButton(onClick = { selection = emptySet() }) { Icon(Icons.Filled.Close, contentDescription = "Annuler la sélection") }
                    },
                    title = { Text("${selection.size} sélectionné(s)") },
                    actions = {
                        TextButton(onClick = {
                            val ids = shown.map { it.id }.filter { it in selection }
                            vm.send(context, ids)
                            selection = emptySet()
                        }) {
                            Icon(Icons.AutoMirrored.Filled.Send, contentDescription = null)
                            Text("  Envoyer")
                        }
                        IconButton(onClick = { confirmDelete = selection }) {
                            Icon(Icons.Filled.Delete, contentDescription = "Supprimer")
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
                )
            }
        },
    ) { padding ->
        LazyVerticalGrid(
            columns = GridCells.Adaptive(minSize = 360.dp),
            contentPadding = PaddingValues(16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    BigAction(
                        icon = Icons.Filled.EditNote,
                        title = "Nouvelle fiche d'intervention",
                        text = "Fiche presse mobile vierge, sans document client",
                        modifier = Modifier.weight(1f),
                        onClick = {
                            val id = vm.createFps()
                            vm.navigate(Screen.Fps(id))
                        },
                    )
                    BigAction(
                        icon = Icons.Filled.UploadFile,
                        title = "Importer un PDF client",
                        text = "Bon de commande, feuille de tâche… remplissage automatique",
                        modifier = Modifier.weight(1f),
                        onClick = { pickDocument.launch(arrayOf("application/pdf", "image/*")) },
                    )
                }
            }
            if (settings.emailCompta.isBlank()) {
                item(span = { GridItemSpan(maxLineSpan) }) {
                    Surface(color = MaterialTheme.colorScheme.secondaryContainer, shape = MaterialTheme.shapes.medium) {
                        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Filled.Warning, contentDescription = null)
                            Text(
                                "Indiquez l'adresse e-mail de la comptabilité et le nom du technicien dans les réglages.",
                                modifier = Modifier
                                    .weight(1f)
                                    .padding(horizontal = 12.dp),
                            )
                            TextButton(onClick = { vm.navigate(Screen.Settings) }) { Text("Réglages") }
                        }
                    }
                }
            }
            item(span = { GridItemSpan(maxLineSpan) }) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = query,
                        onValueChange = { query = it },
                        placeholder = { Text("Rechercher (client, n° de commande, ville…)") },
                        leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                        singleLine = true,
                        modifier = Modifier.weight(1f),
                    )
                    Filter.entries.forEach { f ->
                        FilterChip(selected = filter == f, onClick = { filter = f }, label = { Text(f.label) })
                    }
                }
            }
            if (loaded && shown.isEmpty()) {
                item(span = { GridItemSpan(maxLineSpan) }) {
                    Text(
                        if (all.isEmpty()) "Aucun bon pour l'instant. Commencez par « Nouvelle fiche d'intervention »."
                        else "Aucun bon ne correspond.",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(24.dp),
                    )
                }
            }
            items(shown, key = { it.id }) { i ->
                InterventionCard(
                    i = i,
                    selected = i.id in selection,
                    selectionMode = selection.isNotEmpty(),
                    onClick = {
                        if (selection.isNotEmpty()) selection = selection.toggle(i.id) else vm.openIntervention(i)
                    },
                    onLongClick = { selection = selection.toggle(i.id) },
                    onSend = { vm.send(context, listOf(i.id)) },
                    onPreview = {
                        scope.launch { if (vm.generate(i.id) != null) vm.navigate(Screen.Viewer(i.id)) }
                    },
                    onDuplicate = { vm.duplicate(i.id) },
                    onDelete = { confirmDelete = setOf(i.id) },
                )
            }
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

@Composable
private fun BigAction(icon: ImageVector, title: String, text: String, modifier: Modifier, onClick: () -> Unit) {
    ElevatedCard(
        onClick = onClick,
        modifier = modifier,
        colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
    ) {
        Row(Modifier.padding(20.dp), verticalAlignment = Alignment.CenterVertically) {
            Surface(shape = CircleShape, color = MaterialTheme.colorScheme.primary) {
                Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.onPrimary, modifier = Modifier.padding(12.dp).size(32.dp))
            }
            Column(Modifier.padding(start = 16.dp)) {
                Text(title, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onPrimaryContainer)
                Text(text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onPrimaryContainer)
            }
        }
    }
}

@Composable
fun StatusBadge(status: DisplayStatus) {
    val (bg, fg) = when (status) {
        DisplayStatus.BROUILLON -> MaterialTheme.colorScheme.surfaceContainerHigh to MaterialTheme.colorScheme.onSurfaceVariant
        DisplayStatus.PRET -> MaterialTheme.colorScheme.primaryContainer to MaterialTheme.colorScheme.onPrimaryContainer
        DisplayStatus.ENVOYE -> MaterialTheme.colorScheme.tertiaryContainer to MaterialTheme.colorScheme.onTertiaryContainer
        DisplayStatus.MODIFIE -> MaterialTheme.colorScheme.secondaryContainer to MaterialTheme.colorScheme.onSecondaryContainer
    }
    Surface(color = bg, shape = MaterialTheme.shapes.small) {
        Text(status.label, color = fg, style = MaterialTheme.typography.labelMedium, modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp))
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
    var menu by remember { mutableStateOf(false) }
    val date = Naming.interventionDate(i)
    val status = i.displayStatus()
    Card(
        colors = CardDefaults.cardColors(
            containerColor = if (selected) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surfaceContainerLowest,
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(onClick = onClick, onLongClick = onLongClick),
    ) {
        Row(Modifier.padding(start = 16.dp, top = 12.dp, bottom = 12.dp, end = 4.dp), verticalAlignment = Alignment.Top) {
            Icon(
                if (selected) Icons.Filled.CheckCircle else if (i.type == InterventionType.FPS) Icons.Filled.Description else Icons.Filled.PictureAsPdf,
                contentDescription = null,
                tint = if (selected) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(top = 2.dp),
            )
            Column(
                Modifier
                    .weight(1f)
                    .padding(horizontal = 12.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(Naming.title(i), style = MaterialTheme.typography.titleMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                val ref = Naming.reference(i)
                Text(
                    listOfNotNull(
                        ref.takeIf { it.isNotEmpty() }?.let { "N° $it" },
                        Naming.formatShort(date),
                        Naming.typeLabel(i.type),
                    ).joinToString(" · "),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    StatusBadge(status)
                    val sent = i.sentAt
                    if (sent != null && status == DisplayStatus.ENVOYE) {
                        Text(
                            "le " + SimpleDateFormat("dd/MM à HH:mm", Locale.FRANCE).format(Date(sent)),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    if (i.attachments.isNotEmpty()) {
                        Text(
                            "${i.attachments.size} pièce(s) jointe(s)",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
            if (!selectionMode) {
                Box {
                    IconButton(onClick = { menu = true }) { Icon(Icons.Filled.MoreVert, contentDescription = "Actions") }
                    DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
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
                            text = { Text("Supprimer", color = MaterialTheme.colorScheme.error) },
                            leadingIcon = { Icon(Icons.Filled.Delete, null, tint = MaterialTheme.colorScheme.error) },
                            onClick = { menu = false; onDelete() },
                        )
                    }
                }
            }
        }
    }
}

