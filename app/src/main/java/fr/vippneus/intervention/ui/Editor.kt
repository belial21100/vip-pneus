package fr.vippneus.intervention.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateCentroid
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Draw
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.FitScreen
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.FullscreenExit
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.material.icons.filled.TextFields
import androidx.compose.material.icons.filled.TouchApp
import androidx.compose.material.icons.filled.WidthFull
import androidx.compose.material.icons.filled.ZoomIn
import androidx.compose.material.icons.filled.ZoomOut
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import fr.vippneus.intervention.pdf.PageOps
import fr.vippneus.intervention.data.Completion
import fr.vippneus.intervention.data.FieldAdjust
import fr.vippneus.intervention.data.Intervention
import fr.vippneus.intervention.data.InterventionType
import fr.vippneus.intervention.data.Naming
import fr.vippneus.intervention.data.Overlay
import fr.vippneus.intervention.data.OverlayKind
import fr.vippneus.intervention.data.SignatureData
import fr.vippneus.intervention.pdf.Box as PageBox
import fr.vippneus.intervention.pdf.CrossOp
import fr.vippneus.intervention.pdf.DrawOp
import fr.vippneus.intervention.pdf.FpsLayout
import fr.vippneus.intervention.pdf.FpsTemplate
import fr.vippneus.intervention.pdf.PanelOp
import fr.vippneus.intervention.pdf.SignatureOp
import fr.vippneus.intervention.pdf.TextOp
import fr.vippneus.intervention.pdf.Typo
import java.util.UUID
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

enum class Tool(val label: String, val hint: String) {
    NONE("", ""),
    TEXT("Texte", "Touchez la page à l'endroit où écrire"),
    DATE("Date", "Touchez la page pour y mettre la date du jour"),
    CROSS("Croix", "Touchez la case à cocher"),
    SIGNATURE("Signature", "Touchez la page à l'endroit de la signature"),
}

/** Contenu modifiable de la page 1, pour l'annulation. */
data class EditSnapshot(
    val values: Map<String, String>,
    val signature: SignatureData?,
    val adjust: Map<String, FieldAdjust>,
    val overlays: List<Overlay>,
)

fun Intervention.editSnapshot() = EditSnapshot(values, signature, adjust, overlays)

fun Intervention.restore(s: EditSnapshot) = copy(values = s.values, signature = s.signature, adjust = s.adjust, overlays = s.overlays)

@Stable
class EditorState {
    var tool by mutableStateOf(Tool.NONE)
    var selected by mutableStateOf<String?>(null)
    /** Élément à remplir tout de suite sur la page (touché dans « Manque : … » en plein écran). */
    var fillRequest by mutableStateOf<String?>(null)
    var zoom by mutableFloatStateOf(1f)
    var pan by mutableStateOf(Offset.Zero)
    val undo = mutableStateListOf<EditSnapshot>()

    fun push(s: EditSnapshot) {
        if (undo.lastOrNull() == s) return
        undo.add(s)
        if (undo.size > 60) undo.removeAt(0)
    }

    /** Zoom autour de [focus] ; [center] = centre de la page à 100 %, [fit] = taille de la page à 100 %. */
    fun zoomBy(factor: Float, focus: Offset, center: Offset, fit: Size) {
        val z = zoom
        val z2 = (z * factor).coerceIn(1f, 6f)
        val c = center
        val tl = c + pan - Offset(fit.width * z / 2f, fit.height * z / 2f)
        val p = (focus - tl) / z
        val tl2 = focus - p * z2
        pan = if (z2 <= 1.001f) Offset.Zero else tl2 - c + Offset(fit.width * z2 / 2f, fit.height * z2 / 2f)
        zoom = z2
    }
}

private const val MIN_FONT = 5f
private const val MAX_FONT = 60f

/**
 * Éditeur de la page 1 : déplacer / agrandir les textes, ajouter texte, date, croix, signature.
 * Pincer pour zoomer, glisser à deux doigts pour se déplacer.
 * Sur une feuille du client (Mastra), les cases vides sont repérées en jaune : un appui les remplit.
 */
@Composable
fun PageEditor(
    vm: AppViewModel,
    intervention: Intervention,
    state: EditorState,
    modifier: Modifier = Modifier,
    fullscreen: Boolean = false,
    /** Bouton plein écran, en tête des boutons de zoom (absent si null). */
    onToggleFullscreen: (() -> Unit)? = null,
) {
    val id = intervention.id
    val measure = rememberMeasure()
    val ops = remember(intervention) { PageOps.build(intervention, measure) }
    val (pageW, pageH) = PageOps.pageSize(intervention)
    val background = rememberPageBackground(intervention, vm.sourceFile(intervention))
    val renderer = rememberRenderer()
    val selectionColor = Palette.AmberDeep
    val settings by vm.settings.collectAsStateWithLifecycle()

    var textDialog by remember { mutableStateOf<TextDialogRequest?>(null) }
    var signatureAt by remember { mutableStateOf<Offset?>(null) }
    var redoFpsSignature by remember { mutableStateOf(false) }

    val current by rememberUpdatedState(intervention)
    val currentOps by rememberUpdatedState(ops)
    // Cases vides de la feuille du client : repérées sur la page, un appui les remplit
    val slots = remember(intervention) { emptySlots(intervention) }
    val currentSlots by rememberUpdatedState(slots)
    val slotText = rememberTextMeasurer()

    fun snapshot() = state.push(current.editSnapshot())

    fun edit(transform: (Intervention) -> Intervention) {
        snapshot()
        vm.update(id, transform = transform)
    }

    fun overlay(key: String) = current.overlays.firstOrNull { it.id == key }

    fun moveElement(key: String, dx: Float, dy: Float) {
        vm.update(id) { i ->
            val o = i.overlays.firstOrNull { it.id == key }
            if (o != null) {
                i.copy(overlays = i.overlays.map { if (it.id == key) it.copy(x = it.x + dx, y = it.y + dy) else it })
            } else {
                val a = i.adjust[key] ?: FieldAdjust()
                i.copy(adjust = i.adjust + (key to a.copy(dx = a.dx + dx, dy = a.dy + dy)))
            }
        }
    }

    fun addOverlay(o: Overlay) {
        edit { it.copy(overlays = it.overlays + o) }
        state.selected = o.id
    }

    /** Saisie de la valeur d'un champ placé (case de la feuille du client, ou champ de la fiche). */
    fun editField(key: String) {
        textDialog = TextDialogRequest(key, Offset.Zero, current.value(key), 0f, fpsField = true)
    }

    fun isTextField(key: String) =
        current.template?.fields?.any { it.key == key } == true ||
            (current.type == InterventionType.FPS && FpsTemplate.fieldsByKey[key] != null)

    /** Case vide touchée avec l'outil adapté (ou sans outil) : on la remplit directement. */
    fun fillSlot(p: Offset): Boolean {
        val slot = currentSlots.firstOrNull { it.box.contains(p.x, p.y, 4f) } ?: return false
        val date = slot.key.substringAfterLast('.').startsWith("date")
        val ok = when (state.tool) {
            Tool.NONE -> true
            Tool.TEXT -> !slot.signature
            Tool.DATE -> date
            Tool.SIGNATURE -> slot.signature
            Tool.CROSS -> false
        }
        if (!ok) return false
        val tool = state.tool
        state.tool = Tool.NONE
        state.selected = null
        when {
            slot.signature -> redoFpsSignature = true
            tool == Tool.DATE -> edit { it.copy(values = it.values + (slot.key to Naming.today())) }
            else -> editField(slot.key)
        }
        return true
    }

    fun onTap(p: Offset, hit: String?) {
        if (hit == null && fillSlot(p)) return
        when (state.tool) {
            Tool.TEXT -> {
                textDialog = TextDialogRequest(null, p, "", 14f)
            }
            Tool.DATE -> {
                val size = 14f
                addOverlay(
                    Overlay(UUID.randomUUID().toString(), OverlayKind.TEXT, p.x, p.y - size * 0.55f, Naming.today(), size)
                )
            }
            Tool.CROSS -> {
                val w = 10f
                addOverlay(Overlay(UUID.randomUUID().toString(), OverlayKind.CROSS, p.x - w / 2f, p.y - w / 2f, width = w, height = w))
            }
            Tool.SIGNATURE -> signatureAt = p
            Tool.NONE -> {
                if (hit != null && hit == state.selected) {
                    // Second appui sur l'élément choisi : modifier son texte
                    val o = overlay(hit)
                    when {
                        o?.kind == OverlayKind.TEXT -> textDialog = TextDialogRequest(o.id, Offset(o.x, o.y), o.text, o.fontSize)
                        o == null && isTextField(hit) -> editField(hit)
                    }
                } else {
                    state.selected = hit
                }
            }
        }
        if (state.tool != Tool.NONE && state.tool != Tool.TEXT && state.tool != Tool.SIGNATURE) state.tool = Tool.NONE
    }

    val c = Vip.colors
    Column(modifier) {
        // Page, sur fond sombre, avec la palette d'outils flottante
        BoxWithConstraints(
            Modifier
                .weight(1f)
                .fillMaxWidth()
                .clipToBounds()
                .background(c.canvas),
        ) {
            val density = LocalDensity.current
            val viewW = with(density) { maxWidth.toPx() }
            val viewH = with(density) { maxHeight.toPx() }
            val margin = with(density) { 20.dp.toPx() }
            val top = with(density) { 84.dp.toPx() }
            val fitScale = min((viewW - 2 * margin) / pageW, (viewH - top - margin) / pageH).coerceAtLeast(0.05f)
            val fit = Size(pageW * fitScale, pageH * fitScale)
            val scale = fitScale * state.zoom
            val center = Offset(viewW / 2f, top + (viewH - top - margin) / 2f)
            val topLeft = center + state.pan - Offset(fit.width * state.zoom / 2f, fit.height * state.zoom / 2f)
            val touchTol = with(density) { 10.dp.toPx() } / scale

            val currentFit by rememberUpdatedState(fit)
            val currentCenter by rememberUpdatedState(center)
            val currentScale by rememberUpdatedState(scale)
            val currentTopLeft by rememberUpdatedState(topLeft)
            val currentTol by rememberUpdatedState(touchTol)

            fun toPage(o: Offset) = (o - currentTopLeft) / currentScale

            fun hitTest(p: Offset): String? {
                val list = currentOps.asReversed()
                list.firstOrNull { it.bounds.contains(p.x, p.y) }?.let { return it.key }
                return list
                    .filter { it.bounds.contains(p.x, p.y, currentTol) }
                    .minByOrNull { (Offset(it.bounds.centerX, it.bounds.centerY) - p).getDistanceSquared() }
                    ?.key
            }

            Canvas(
                Modifier
                    .fillMaxSize()
                    .pointerInput(pageW, pageH) {
                        awaitEachGesture {
                            val down = awaitFirstDown(requireUnconsumed = false)
                            val startPage = toPage(down.position)
                            val hit = if (state.tool == Tool.NONE) hitTest(startPage) else null
                            var dragging = false
                            var multi = false
                            var dragKey: String? = null
                            var total = Offset.Zero
                            while (true) {
                                val event = awaitPointerEvent()
                                val pressed = event.changes.filter { it.pressed }
                                if (pressed.isEmpty()) break
                                if (pressed.size >= 2) {
                                    multi = true
                                    dragKey = null
                                    val zoomChange = event.calculateZoom()
                                    val panChange = event.calculatePan()
                                    val centroid = event.calculateCentroid(useCurrent = true)
                                    if (centroid != Offset.Unspecified) {
                                        state.zoomBy(zoomChange, centroid, currentCenter, currentFit)
                                    }
                                    if (state.zoom > 1.001f) state.pan += panChange
                                    event.changes.forEach { it.consume() }
                                    continue
                                }
                                val ch = pressed.first()
                                val delta = ch.positionChange()
                                if (multi) {
                                    ch.consume()
                                    continue
                                }
                                total += delta
                                if (!dragging && total.getDistance() > viewConfiguration.touchSlop) {
                                    dragging = true
                                    if (hit != null) {
                                        dragKey = hit
                                        state.selected = hit
                                        snapshot()
                                    }
                                }
                                if (dragging) {
                                    val key = dragKey
                                    if (key != null) {
                                        moveElement(key, delta.x / currentScale, delta.y / currentScale)
                                    } else if (state.zoom > 1.001f) {
                                        state.pan += delta
                                    }
                                    ch.consume()
                                }
                            }
                            if (!dragging && !multi) onTap(startPage, hit)
                        }
                    },
            ) {
                val pageSize = Size(pageW * scale, pageH * scale)
                // ombre portée
                drawRect(Color.Black.copy(alpha = 0.25f), topLeft + Offset(0f, 6f), pageSize)
                drawRect(Color.Black.copy(alpha = 0.12f), topLeft + Offset(-3f, 3f), Size(pageSize.width + 6f, pageSize.height + 8f))
                drawPage(renderer, background, ops, topLeft, pageSize, pageW)
                slots.forEach { drawSlot(it, topLeft, scale, slotText) }
                state.selected?.let { key ->
                    ops.firstOrNull { it.key == key }?.let { drawSelection(it.bounds, topLeft, scale, selectionColor) }
                }
            }
            if (background == null) {
                Column(Modifier.align(Alignment.Center), horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator(color = c.accent)
                    Text("Chargement de la page…", color = c.onChrome, modifier = Modifier.padding(top = 12.dp))
                }
            }

            // Plein écran et zoom, sur le bord droit
            Surface(
                color = c.chrome,
                contentColor = c.onChrome,
                shape = RoundedCornerShape(18.dp),
                shadowElevation = 10.dp,
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .padding(end = 12.dp),
            ) {
                Column(Modifier.padding(4.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    if (onToggleFullscreen != null) {
                        IconButton(onClick = onToggleFullscreen) {
                            Icon(
                                if (fullscreen) Icons.Filled.FullscreenExit else Icons.Filled.Fullscreen,
                                contentDescription = if (fullscreen) "Quitter le plein écran" else "Plein écran",
                            )
                        }
                        Box(
                            Modifier
                                .padding(vertical = 4.dp)
                                .width(28.dp)
                                .height(1.dp)
                                .background(c.onChrome.copy(alpha = 0.2f)),
                        )
                    }
                    IconButton(onClick = { state.zoom = min(6f, state.zoom * 1.5f) }) { Icon(Icons.Filled.ZoomIn, contentDescription = "Zoom +") }
                    IconButton(onClick = {
                        state.zoom = max(1f, state.zoom / 1.5f)
                        if (state.zoom <= 1.001f) state.pan = Offset.Zero
                    }) { Icon(Icons.Filled.ZoomOut, contentDescription = "Zoom −") }
                    IconButton(onClick = {
                        // Toute la largeur de la page, haut de la page sous la palette
                        val z = ((viewW - 2 * margin) / pageW / fitScale).coerceIn(1f, 6f)
                        state.zoom = z
                        state.pan = if (z <= 1.001f) Offset.Zero else Offset(0f, top - center.y + fit.height * z / 2f)
                    }) { Icon(Icons.Filled.WidthFull, contentDescription = "Largeur de la page") }
                    IconButton(onClick = { state.zoom = 1f; state.pan = Offset.Zero }) { Icon(Icons.Filled.FitScreen, contentDescription = "Page entière") }
                }
            }

            // Palette d'outils
            Column(
                Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 14.dp, start = 12.dp, end = 12.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Surface(
                    color = c.chrome,
                    contentColor = c.onChrome,
                    shape = RoundedCornerShape(18.dp),
                    shadowElevation = 10.dp,
                ) {
                    Row(
                        Modifier
                            .horizontalScroll(rememberScrollState())
                            .padding(6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        ToolButton(state, Tool.TEXT, Icons.Filled.TextFields)
                        ToolButton(state, Tool.DATE, Icons.Filled.DateRange)
                        ToolButton(state, Tool.CROSS, Icons.Filled.Close)
                        ToolButton(state, Tool.SIGNATURE, Icons.Filled.Draw)
                        Box(
                            Modifier
                                .padding(horizontal = 6.dp)
                                .width(1.dp)
                                .height(28.dp)
                                .background(c.onChrome.copy(alpha = 0.2f)),
                        )
                        IconButton(
                            onClick = {
                                state.undo.removeLastOrNull()?.let { s -> vm.update(id) { it.restore(s) } }
                            },
                            enabled = state.undo.isNotEmpty(),
                            colors = IconButtonDefaults.iconButtonColors(contentColor = c.onChrome, disabledContentColor = c.onChrome.copy(alpha = 0.3f)),
                        ) { Icon(Icons.AutoMirrored.Filled.Undo, contentDescription = "Annuler la dernière modification") }
                    }
                }
                if (state.tool != Tool.NONE) {
                    Surface(color = c.accent, contentColor = Palette.Graphite900, shape = CircleShape, shadowElevation = 6.dp) {
                        Row(Modifier.padding(start = 18.dp, end = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Filled.TouchApp, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp))
                            Text(state.tool.hint, style = MaterialTheme.typography.labelLarge)
                            IconButton(onClick = { state.tool = Tool.NONE }) { Icon(Icons.Filled.Close, contentDescription = "Annuler l'outil") }
                        }
                    }
                }
            }
        }

        // Réglages de l'élément sélectionné
        val selectedOp = state.selected?.let { key -> ops.firstOrNull { it.key == key } }
        if (selectedOp != null) {
            SelectionBar(
                op = selectedOp,
                overlay = overlay(selectedOp.key),
                intervention = intervention,
                onFontSize = { newSize ->
                    val key = selectedOp.key
                    val o = overlay(key)
                    edit { i ->
                        if (o != null) i.copy(overlays = i.overlays.map { if (it.id == key) it.copy(fontSize = newSize) else it })
                        else {
                            val a = i.adjust[key] ?: FieldAdjust()
                            i.copy(adjust = i.adjust + (key to a.copy(fontSize = newSize)))
                        }
                    }
                },
                onScale = { factor ->
                    val key = selectedOp.key
                    val o = overlay(key)
                    edit { i ->
                        if (o != null) {
                            val nw = (o.width * factor).coerceIn(4f, pageW)
                            val nh = (o.height * factor).coerceIn(if (o.kind == OverlayKind.CROSS) 4f else 2f, pageH)
                            val cx = o.x + o.width / 2f
                            val cy = o.y + o.height / 2f
                            i.copy(overlays = i.overlays.map {
                                if (it.id == key) it.copy(x = cx - nw / 2f, y = cy - nh / 2f, width = nw, height = if (o.kind == OverlayKind.CROSS) nw else nh) else it
                            })
                        } else {
                            val a = i.adjust[key] ?: FieldAdjust()
                            val s = ((a.scale ?: 1f) * factor).coerceIn(0.3f, 4f)
                            i.copy(adjust = i.adjust + (key to a.copy(scale = s)))
                        }
                    }
                },
                onEditText = {
                    val key = selectedOp.key
                    val o = overlay(key)
                    textDialog = if (o != null) TextDialogRequest(o.id, Offset(o.x, o.y), o.text, o.fontSize)
                    else TextDialogRequest(key, Offset.Zero, intervention.value(key), 0f, fpsField = true)
                },
                onRedoSignature = {
                    if (overlay(selectedOp.key) == null) redoFpsSignature = true
                    else signatureAt = Offset(selectedOp.bounds.centerX, selectedOp.bounds.centerY)
                },
                onReset = {
                    val key = selectedOp.key
                    edit { it.copy(adjust = it.adjust - key) }
                },
                onDelete = {
                    val key = selectedOp.key
                    if (overlay(key) != null) {
                        edit { it.copy(overlays = it.overlays.filterNot { o -> o.id == key }) }
                    } else if (key == FpsTemplate.K.SIGNATURE) {
                        edit { it.copy(signature = null, adjust = it.adjust - key) }
                    } else if (key == intervention.template?.panel?.key) {
                        val lines = intervention.template.panel.lines.map { l -> l.key }.toSet()
                        edit { it.copy(values = it.values - lines, adjust = it.adjust - key) }
                    } else {
                        edit { it.copy(values = it.values - key, adjust = it.adjust - key) }
                    }
                    state.selected = null
                },
                onClose = { state.selected = null },
            )
        }
    }

    // Élément demandé depuis « Manque : … » (plein écran) : on l'ouvre directement
    LaunchedEffect(state.fillRequest) {
        val key = state.fillRequest ?: return@LaunchedEffect
        state.fillRequest = null
        state.selected = null
        when {
            key == Completion.SIGNATURE && current.template?.signature != null -> redoFpsSignature = true
            key == Completion.SIGNATURE -> state.tool = Tool.SIGNATURE
            else -> editField(key)
        }
    }

    textDialog?.let { req ->
        val fpsField = req.fpsField
        val placed = if (fpsField) intervention.template?.fields?.firstOrNull { it.key == req.id } else null
        TextEditDialog(
            title = when {
                fpsField -> PageOps.fieldLabel(intervention, req.id ?: "") ?: "Modifier"
                req.id == null -> "Nouveau texte"
                else -> "Modifier le texte"
            },
            initialText = req.text,
            initialSize = if (fpsField) 14f else req.size,
            showSize = !fpsField,
            numeric = placed?.numeric == true,
            supporting = placed?.let { f -> intervention.hints[f.key] ?: f.hint.ifEmpty { null } },
            quickInserts = quickInserts(settings.technicien),
            onDismiss = { textDialog = null },
            onConfirm = { text, size ->
                textDialog = null
                when {
                    fpsField -> edit { i ->
                        val k = req.id!!
                        i.copy(values = if (text.isBlank()) i.values - k else i.values + (k to text))
                    }
                    req.id == null -> if (text.isNotBlank()) {
                        addOverlay(
                            Overlay(UUID.randomUUID().toString(), OverlayKind.TEXT, req.at.x, req.at.y - size * Typo.ASCENT * 0.7f, text.trimEnd(), size)
                        )
                    }
                    text.isBlank() -> edit { i -> i.copy(overlays = i.overlays.filterNot { it.id == req.id }) }
                    else -> edit { i ->
                        i.copy(overlays = i.overlays.map { if (it.id == req.id) it.copy(text = text.trimEnd(), fontSize = size) else it })
                    }
                }
                state.tool = Tool.NONE
            },
        )
    }

    signatureAt?.let { at ->
        SignatureDialog(
            title = "Signature",
            onDismiss = { signatureAt = null; state.tool = Tool.NONE },
            onDone = { sig, _ ->
                signatureAt = null
                state.tool = Tool.NONE
                if (sig != null) {
                    val existing = state.selected?.let { overlay(it) }?.takeIf { it.kind == OverlayKind.SIGNATURE }
                    if (existing != null) {
                        edit { i -> i.copy(overlays = i.overlays.map { if (it.id == existing.id) it.copy(signature = sig) else it }) }
                    } else {
                        val w = 150f
                        val h = w * sig.height / max(1f, sig.width)
                        addOverlay(
                            Overlay(UUID.randomUUID().toString(), OverlayKind.SIGNATURE, at.x - w / 2f, at.y - h / 2f, width = w, height = h, signature = sig)
                        )
                    }
                }
            },
        )
    }

    if (redoFpsSignature) {
        SignatureDialog(
            title = "Signature du client",
            onDismiss = { redoFpsSignature = false },
            onDone = { sig, _ ->
                redoFpsSignature = false
                if (sig != null) edit { it.copy(signature = sig) }
            },
        )
    }
}

/** Case vide d'une feuille du client (Mastra), repérée sur la page. */
private data class Slot(val key: String, val label: String, val box: PageBox, val signature: Boolean = false)

private fun emptySlots(i: Intervention): List<Slot> {
    val t = i.template ?: return emptyList()
    val fields = t.fields.filter { i.value(it.key).isBlank() }.map { f ->
        val a = i.adjust[f.key]
        Slot(f.key, f.label.substringBefore(" ("), PageBox(f.left, f.top, f.right, f.bottom).offset(a?.dx ?: 0f, a?.dy ?: 0f))
    }
    val signature = t.signature?.takeIf { i.signature?.isEmpty != false }?.let { b ->
        Slot("slot.signature", "Signature", FpsLayout.scaledBox(PageBox(b.left, b.top, b.right, b.bottom), i.adjust[FpsTemplate.K.SIGNATURE]), signature = true)
    }
    return fields + listOfNotNull(signature)
}

/** Case à remplir : fond jaune pâle, pointillés et libellé (si la case est assez grande à l'écran). */
private fun DrawScope.drawSlot(slot: Slot, topLeft: Offset, scale: Float, measurer: TextMeasurer) {
    val tl = Offset(topLeft.x + slot.box.left * scale, topLeft.y + slot.box.top * scale)
    val size = Size(slot.box.width * scale, slot.box.height * scale)
    drawRect(Palette.Amber.copy(alpha = 0.22f), tl, size)
    drawRect(
        Palette.AmberDeep, tl, size,
        style = Stroke(width = 2f, pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 6f))),
    )
    if (size.height >= 14f && size.width >= 48f) {
        val avail = size.width - 12f
        var fontPx = (size.height * 0.5f).coerceIn(10f, 22f)
        // Libellé réduit pour tenir dans la case (coupé seulement en dessous de 9 px)
        val natural = measurer.measure(slot.label, TextStyle(fontSize = fontPx.toSp()), softWrap = false, maxLines = 1).size.width
        if (natural > avail) fontPx = max(9f, fontPx * avail / natural)
        drawText(
            measurer, slot.label,
            topLeft = tl + Offset(6f, (size.height - fontPx * 1.3f) / 2f),
            style = TextStyle(color = Palette.AmberInk, fontSize = fontPx.toSp()),
            overflow = TextOverflow.Ellipsis,
            maxLines = 1,
            size = Size(avail, fontPx * 1.4f),
        )
    }
}

private data class TextDialogRequest(
    val id: String?,
    val at: Offset,
    val text: String,
    val size: Float,
    val fpsField: Boolean = false,
)

fun quickInserts(technicien: String): List<Pair<String, String>> = buildList {
    add("Date du jour" to Naming.today())
    if (technicien.isNotBlank()) add(technicien to technicien)
    add("Nm" to " Nm")
    add("h" to " h")
    add("✓" to "✓")
    add("Vu avec le client" to "Vu avec le client")
}

@Composable
private fun ToolButton(state: EditorState, tool: Tool, icon: ImageVector) {
    val c = Vip.colors
    val selected = state.tool == tool
    Surface(
        onClick = {
            state.tool = if (selected) Tool.NONE else tool
            state.selected = null
        },
        shape = RoundedCornerShape(12.dp),
        color = if (selected) c.accent else Color.Transparent,
        contentColor = if (selected) Palette.Graphite900 else c.onChrome,
    ) {
        Row(
            Modifier
                .heightIn(min = 44.dp)
                .padding(horizontal = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(8.dp))
            Text(tool.label, style = MaterialTheme.typography.labelLarge)
        }
    }
}

@Composable
private fun SelectionBar(
    op: DrawOp,
    overlay: Overlay?,
    intervention: Intervention,
    onFontSize: (Float) -> Unit,
    onScale: (Float) -> Unit,
    onEditText: () -> Unit,
    onRedoSignature: () -> Unit,
    onReset: () -> Unit,
    onDelete: () -> Unit,
    onClose: () -> Unit,
) {
    val isFpsElement = overlay == null && (intervention.type == InterventionType.FPS || intervention.template != null)
    val label = when {
        overlay != null -> when (overlay.kind) {
            OverlayKind.TEXT -> "Texte libre"
            OverlayKind.SIGNATURE -> "Signature"
            OverlayKind.CROSS -> "Croix"
        }
        op.key == FpsTemplate.K.SIGNATURE -> "Signature du client"
        else -> PageOps.fieldLabel(intervention, op.key) ?: "Élément"
    }
    val c = Vip.colors
    Surface(color = c.card, shadowElevation = 12.dp) {
        Column {
            HorizontalDivider(color = c.cardBorder)
            Row(
                Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Column(Modifier.width(170.dp)) {
                    Text("Sélection", style = MaterialTheme.typography.labelMedium, color = c.muted)
                    Text(label, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                when (op) {
                    is TextOp -> {
                        Stepper(
                            text = "${op.size.roundToInt()} pt",
                            onMinus = { onFontSize((op.size - 1f).coerceAtLeast(MIN_FONT)) },
                            onPlus = { onFontSize((op.size + 1f).coerceAtMost(MAX_FONT)) },
                            minusLabel = "Texte plus petit",
                            plusLabel = "Texte plus grand",
                        )
                        VipButton("Modifier le texte", onEditText, icon = Icons.Filled.Edit, tone = Tone.GHOST, compact = true)
                    }
                    is CrossOp, is SignatureOp, is PanelOp -> {
                        Stepper(
                            text = "Taille",
                            onMinus = { onScale(1f / 1.15f) },
                            onPlus = { onScale(1.15f) },
                            minusLabel = "Plus petit",
                            plusLabel = "Plus grand",
                        )
                        if (op is SignatureOp) {
                            VipButton("Refaire", onRedoSignature, icon = Icons.Filled.Draw, tone = Tone.GHOST, compact = true)
                        }
                    }
                }
                if (isFpsElement) {
                    VipButton("Position d'origine", onReset, icon = Icons.Filled.RestartAlt, tone = Tone.GHOST, compact = true)
                }
                VipButton(if (isFpsElement) "Effacer" else "Supprimer", onDelete, icon = Icons.Filled.Delete, tone = Tone.DANGER, compact = true)
                IconButton(onClick = onClose) { Icon(Icons.Filled.Close, contentDescription = "Désélectionner") }
            }
        }
    }
}

/** − valeur + */
@Composable
private fun Stepper(text: String, onMinus: () -> Unit, onPlus: () -> Unit, minusLabel: String, plusLabel: String) {
    val c = Vip.colors
    Row(
        Modifier
            .background(MaterialTheme.colorScheme.surfaceContainerHigh, RoundedCornerShape(12.dp))
            .padding(2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = onMinus) { Icon(Icons.Filled.Remove, contentDescription = minusLabel) }
        Text(
            text,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.widthIn(min = 52.dp),
            textAlign = TextAlign.Center,
        )
        IconButton(onClick = onPlus) { Icon(Icons.Filled.Add, contentDescription = plusLabel, tint = c.onAccentSoft) }
    }
}
