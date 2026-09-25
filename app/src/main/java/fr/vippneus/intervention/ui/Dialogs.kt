package fr.vippneus.intervention.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Draw
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import fr.vippneus.intervention.data.Intervention
import fr.vippneus.intervention.data.Naming
import fr.vippneus.intervention.data.SignatureData
import fr.vippneus.intervention.data.Todo
import fr.vippneus.intervention.pdf.Fonts
import kotlin.math.roundToInt

/**
 * Saisie d'une signature au doigt ou au stylet, avec le nom du signataire si [nameLabel] est donné.
 * [onDone] reçoit null si le cadre a été laissé vide, et le nom saisi (null sans champ nom).
 */
@Composable
fun SignatureDialog(
    title: String,
    onDismiss: () -> Unit,
    onDone: (SignatureData?, String?) -> Unit,
    nameLabel: String? = null,
    initialName: String = "",
) {
    val strokes = remember { mutableStateListOf<List<Offset>>() }
    val current = remember { mutableStateListOf<Offset>() }
    var padSize by remember { mutableStateOf(IntSize.Zero) }
    var name by remember { mutableStateOf(initialName) }
    val penPx = with(LocalDensity.current) { 3.dp.toPx() }
    val ink = Color(0xFF14171C)
    val c = Vip.colors
    val empty = strokes.isEmpty() && current.isEmpty()

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(
            shape = MaterialTheme.shapes.extraLarge,
            color = c.card,
            modifier = Modifier
                .fillMaxWidth(0.94f)
                .widthIn(max = 1000.dp),
        ) {
            Column(Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconBadge(Icons.Filled.Draw)
                    Spacer(Modifier.width(14.dp))
                    Column(Modifier.weight(1f)) {
                        Text(title, style = MaterialTheme.typography.titleLarge)
                        Text(
                            "Faites signer dans le cadre blanc, avec le doigt ou un stylet.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = c.muted,
                        )
                    }
                }
                if (nameLabel != null) {
                    VipField(
                        label = nameLabel,
                        value = name,
                        onValueChange = { name = it },
                        capitalization = KeyboardCapitalization.Words,
                    )
                }
                Box(
                    Modifier
                        .fillMaxWidth()
                        .aspectRatio(2.7f)
                        .background(Color.White, MaterialTheme.shapes.medium)
                        .border(BorderStroke(1.dp, MaterialTheme.colorScheme.outline), MaterialTheme.shapes.medium)
                        .onSizeChanged { padSize = it }
                        .pointerInput(Unit) {
                            awaitEachGesture {
                                val down = awaitFirstDown()
                                down.consume()
                                current.clear()
                                current.add(down.position)
                                while (true) {
                                    val event = awaitPointerEvent()
                                    val change = event.changes.firstOrNull { it.id == down.id } ?: break
                                    change.historical.forEach { h -> addPoint(current, h.position) }
                                    addPoint(current, change.position)
                                    change.consume()
                                    if (!change.pressed) break
                                }
                                if (current.isNotEmpty()) strokes.add(current.toList())
                                current.clear()
                            }
                        },
                ) {
                    Canvas(Modifier.matchParentSize()) {
                        // Ligne de signature
                        val y = size.height * 0.76f
                        drawLine(Color(0xFFCBD2DC), Offset(size.width * 0.05f, y), Offset(size.width * 0.95f, y), strokeWidth = 2f)
                        val style = Stroke(width = penPx, cap = StrokeCap.Round, join = StrokeJoin.Round)
                        (strokes + listOf(current.toList())).forEach { pts ->
                            if (pts.isEmpty()) return@forEach
                            val path = Path().apply {
                                moveTo(pts[0].x, pts[0].y)
                                if (pts.size == 1) lineTo(pts[0].x + 0.1f, pts[0].y + 0.1f)
                                for (i in 1 until pts.size - 1) {
                                    val mid = (pts[i] + pts[i + 1]) / 2f
                                    quadraticTo(pts[i].x, pts[i].y, mid.x, mid.y)
                                }
                                if (pts.size > 1) lineTo(pts.last().x, pts.last().y)
                            }
                            drawPath(path, ink, style = style)
                        }
                    }
                    if (empty) {
                        // « ✕ Signer ici » posé juste au-dessus de la ligne de signature
                        Column(
                            Modifier
                                .matchParentSize()
                                .padding(start = 28.dp),
                        ) {
                            Spacer(Modifier.weight(0.64f))
                            Text("✕  Signer ici", style = MaterialTheme.typography.titleMedium, color = Color(0xFFABB2BB))
                            Spacer(Modifier.weight(0.24f))
                        }
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    VipButton("Effacer", { strokes.clear(); current.clear() }, icon = Icons.Filled.Delete, tone = Tone.GHOST, enabled = !empty)
                    Spacer(Modifier.weight(1f))
                    TextButton(onClick = onDismiss, modifier = Modifier.heightIn(min = 52.dp)) { Text("Annuler", style = MaterialTheme.typography.labelLarge) }
                    VipButton("Valider la signature", {
                        val data = strokes.filter { it.isNotEmpty() }.map { pts ->
                            pts.flatMap { listOf(round1(it.x), round1(it.y)) }
                        }
                        onDone(
                            if (data.isEmpty()) null
                            else SignatureData(data, padSize.width.toFloat(), padSize.height.toFloat(), penPx),
                            if (nameLabel != null) name.trim() else null,
                        )
                    })
                }
            }
        }
    }
}

private fun addPoint(list: MutableList<Offset>, p: Offset) {
    val last = list.lastOrNull()
    if (last == null || (p - last).getDistance() >= 1.5f) list.add(p)
}

private fun round1(v: Float) = (v * 10f).roundToInt() / 10f

/** Saisie ou modification d'un texte libre posé sur la page. */
@Composable
fun TextEditDialog(
    title: String,
    initialText: String,
    initialSize: Float,
    quickInserts: List<Pair<String, String>>,
    onDismiss: () -> Unit,
    onConfirm: (String, Float) -> Unit,
    showSize: Boolean = true,
    /** Clavier numérique (lecture du compteur, couple…). */
    numeric: Boolean = false,
    /** Aide sous le champ (ex. couple préconisé). */
    supporting: String? = null,
) {
    var value by remember { mutableStateOf(TextFieldValue(initialText, TextRange(initialText.length))) }
    var size by remember { mutableFloatStateOf(initialSize) }
    val focus = remember { FocusRequester() }
    val c = Vip.colors
    val context = LocalContext.current
    val pdfFont = remember { FontFamily(Fonts.typeface(context)) }
    LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = c.card,
        title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                OutlinedTextField(
                    value = value,
                    onValueChange = { value = it },
                    minLines = if (numeric) 1 else 2,
                    maxLines = 6,
                    keyboardOptions = KeyboardOptions(keyboardType = if (numeric) KeyboardType.Number else KeyboardType.Text),
                    supportingText = supporting?.let { { Text(it) } },
                    shape = MaterialTheme.shapes.small,
                    colors = vipFieldColors(),
                    textStyle = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier
                        .fillMaxWidth()
                        .focusRequester(focus),
                )
                if (quickInserts.isNotEmpty()) {
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(quickInserts) { (label, text) ->
                            Surface(
                                onClick = {
                                    val t = value.text
                                    val start = value.selection.min
                                    val end = value.selection.max
                                    val insert = if (start > 0 && !t[start - 1].isWhitespace() && !text.startsWith(" ")) " $text" else text
                                    val nt = t.substring(0, start) + insert + t.substring(end)
                                    value = TextFieldValue(nt, TextRange(start + insert.length))
                                },
                                shape = CircleShape,
                                color = c.accentSoft,
                                contentColor = c.onAccentSoft,
                            ) {
                                Text(
                                    label,
                                    style = MaterialTheme.typography.labelLarge,
                                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 9.dp),
                                )
                            }
                        }
                    }
                }
                if (showSize) {
                    Text("Taille du texte : ${size.roundToInt()} pt", style = MaterialTheme.typography.labelLarge)
                    Slider(
                        value = size,
                        onValueChange = { size = it.roundToInt().toFloat() },
                        valueRange = 6f..48f,
                        colors = SliderDefaults.colors(thumbColor = c.chromeHigh, activeTrackColor = c.chromeHigh),
                    )
                    Text(
                        value.text.lineSequence().firstOrNull()?.ifBlank { null } ?: "Aperçu",
                        fontSize = (size * 1.1f).coerceAtMost(40f).sp,
                        fontFamily = pdfFont,
                        maxLines = 1,
                    )
                }
            }
        },
        confirmButton = { VipButton("OK", { onConfirm(value.text, size) }, compact = true) },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Annuler") } },
    )
}

/** Nom du fichier PDF envoyé (vide = nom automatique). */
@Composable
fun FileNameDialog(auto: String, current: String, onDismiss: () -> Unit, onConfirm: (String) -> Unit) {
    var value by remember { mutableStateOf(current.ifBlank { auto }) }
    val c = Vip.colors
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = c.card,
        title = { Text("Nom du fichier PDF") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = value,
                    onValueChange = { value = it },
                    suffix = { Text(".pdf") },
                    singleLine = true,
                    shape = MaterialTheme.shapes.small,
                    colors = vipFieldColors(),
                    modifier = Modifier.fillMaxWidth(),
                )
                Text("Nom automatique : $auto.pdf", style = MaterialTheme.typography.bodySmall, color = c.muted)
                if (value.trim() != auto) {
                    TextButton(onClick = { value = auto }) { Text("Revenir au nom automatique") }
                }
            }
        },
        confirmButton = {
            VipButton("Enregistrer", {
                val v = value.trim()
                onConfirm(if (v == auto) "" else v)
            }, compact = true)
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Annuler") } },
    )
}

/** Avant l'envoi : ce qu'il manque sur le bon ; un appui sur un élément y mène directement. */
@Composable
fun MissingDialog(missing: List<Todo>, onJump: (Todo) -> Unit, onDismiss: () -> Unit, onSendAnyway: () -> Unit) {
    val c = Vip.colors
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = c.card,
        icon = { Icon(Icons.Filled.Warning, contentDescription = null, tint = c.warning) },
        title = { Text(if (missing.size == 1) "Il manque 1 élément" else "Il manque ${missing.size} éléments") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Touchez un élément pour le compléter :", color = c.muted)
                missing.forEach { t ->
                    Surface(
                        onClick = { onJump(t) },
                        shape = MaterialTheme.shapes.small,
                        color = c.accentSoft,
                        contentColor = c.onAccentSoft,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Row(Modifier.padding(horizontal = 12.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(t.label, style = MaterialTheme.typography.titleSmall)
                                if (t.hint.isNotEmpty()) {
                                    Text(t.hint, style = MaterialTheme.typography.bodySmall, color = c.onAccentSoft.copy(alpha = 0.8f))
                                }
                            }
                            Icon(Icons.Filled.ChevronRight, contentDescription = null)
                        }
                    }
                }
                SendAnywayNote(plural = false)
            }
        },
        confirmButton = { VipButton("Compléter le bon", { onJump(missing.first()) }, compact = true) },
        dismissButton = { TextButton(onClick = onSendAnyway) { Text("Envoyer quand même") } },
    )
}

/** Rappel sous la liste de ce qui manque : un envoi incomplet reste possible, mais il est signalé. */
@Composable
private fun SendAnywayNote(plural: Boolean) {
    Text(
        if (plural) "Envoyés quand même, ces bons seront marqués « Envoyé incomplet » pour les corriger ensuite."
        else "Envoyé quand même, le bon sera marqué « Envoyé incomplet » pour le corriger ensuite.",
        style = MaterialTheme.typography.bodySmall,
        color = Vip.colors.muted,
        modifier = Modifier.padding(top = 4.dp),
    )
}

/** Envoi de plusieurs bons depuis la liste : les bons où il manque quelque chose, et quoi. */
@Composable
fun IncompleteBonsDialog(
    incomplete: List<Pair<Intervention, List<Todo>>>,
    total: Int,
    onOpen: (Intervention) -> Unit,
    onDismiss: () -> Unit,
    onSendAnyway: () -> Unit,
) {
    val c = Vip.colors
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = c.card,
        icon = { Icon(Icons.Filled.Warning, contentDescription = null, tint = c.warning) },
        title = {
            Text(if (incomplete.size == 1) "1 bon incomplet sur $total" else "${incomplete.size} bons incomplets sur $total")
        },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Touchez un bon pour le compléter :", color = c.muted)
                incomplete.forEach { (i, missing) ->
                    Surface(
                        onClick = { onOpen(i) },
                        shape = MaterialTheme.shapes.small,
                        color = c.accentSoft,
                        contentColor = c.onAccentSoft,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Row(Modifier.padding(horizontal = 12.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(Naming.title(i), style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text(
                                    "Manque : " + missing.joinToString(", ") { it.label },
                                    style = MaterialTheme.typography.bodySmall,
                                    color = c.onAccentSoft.copy(alpha = 0.8f),
                                )
                            }
                            Icon(Icons.Filled.ChevronRight, contentDescription = null)
                        }
                    }
                }
                SendAnywayNote(plural = incomplete.size > 1)
            }
        },
        confirmButton = { VipButton("Compléter", { onOpen(incomplete.first().first) }, compact = true) },
        dismissButton = { TextButton(onClick = onSendAnyway) { Text("Envoyer quand même") } },
    )
}

