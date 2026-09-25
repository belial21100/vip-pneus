package fr.vippneus.intervention.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Button
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
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import fr.vippneus.intervention.data.SignatureData
import kotlin.math.roundToInt

/**
 * Saisie d'une signature au doigt ou au stylet.
 * [onDone] reçoit null si le cadre a été laissé vide.
 */
@Composable
fun SignatureDialog(
    title: String,
    onDismiss: () -> Unit,
    onDone: (SignatureData?) -> Unit,
) {
    val strokes = remember { mutableStateListOf<List<Offset>>() }
    val current = remember { mutableStateListOf<Offset>() }
    var padSize by remember { mutableStateOf(IntSize.Zero) }
    val penPx = with(LocalDensity.current) { 3.dp.toPx() }
    val ink = Color(0xFF14171C)

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(
            shape = MaterialTheme.shapes.extraLarge,
            modifier = Modifier
                .fillMaxWidth(0.94f)
                .widthIn(max = 980.dp),
        ) {
            Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Text(title, style = MaterialTheme.typography.titleLarge)
                Text(
                    "Signez dans le cadre blanc avec le doigt ou un stylet.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Box(
                    Modifier
                        .fillMaxWidth()
                        .aspectRatio(2.6f)
                        .background(Color.White, MaterialTheme.shapes.medium)
                        .border(1.dp, MaterialTheme.colorScheme.outline, MaterialTheme.shapes.medium)
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
                        val y = size.height * 0.78f
                        drawLine(Color(0xFFCBD2DC), Offset(size.width * 0.06f, y), Offset(size.width * 0.94f, y), strokeWidth = 2f)
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
                }
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    TextButton(onClick = { strokes.clear(); current.clear() }) { Text("Effacer") }
                    Spacer(Modifier.weight(1f))
                    TextButton(onClick = onDismiss) { Text("Annuler") }
                    Button(onClick = {
                        val data = strokes.filter { it.isNotEmpty() }.map { pts ->
                            pts.flatMap { listOf(round1(it.x), round1(it.y)) }
                        }
                        onDone(
                            if (data.isEmpty()) null
                            else SignatureData(data, padSize.width.toFloat(), padSize.height.toFloat(), penPx)
                        )
                    }) { Text("Valider") }
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
) {
    var value by remember { mutableStateOf(TextFieldValue(initialText, TextRange(initialText.length))) }
    var size by remember { mutableFloatStateOf(initialSize) }
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = value,
                    onValueChange = { value = it },
                    minLines = 2,
                    maxLines = 6,
                    modifier = Modifier
                        .fillMaxWidth()
                        .focusRequester(focus),
                )
                if (quickInserts.isNotEmpty()) {
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(quickInserts) { (label, text) ->
                            AssistChip(
                                onClick = {
                                    val t = value.text
                                    val start = value.selection.min
                                    val end = value.selection.max
                                    val insert = if (start > 0 && !t[start - 1].isWhitespace() && !text.startsWith(" ")) " $text" else text
                                    val nt = t.substring(0, start) + insert + t.substring(end)
                                    value = TextFieldValue(nt, TextRange(start + insert.length))
                                },
                                label = { Text(label) },
                            )
                        }
                    }
                }
                if (showSize) {
                    Text("Taille du texte : ${size.roundToInt()} pt", style = MaterialTheme.typography.labelLarge)
                    Slider(value = size, onValueChange = { size = it.roundToInt().toFloat() }, valueRange = 6f..48f)
                    Text(
                        value.text.lineSequence().firstOrNull()?.ifBlank { null } ?: "Aperçu",
                        fontSize = (size * 1.1f).coerceAtMost(40f).sp,
                        fontFamily = FontFamily.SansSerif,
                        maxLines = 1,
                    )
                }
            }
        },
        confirmButton = {
            FilledTonalButton(onClick = { onConfirm(value.text, size) }) { Text("OK") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Annuler") } },
    )
}

/** Confirmation d'une action destructive. */
@Composable
fun ConfirmDialog(
    title: String,
    text: String,
    confirmLabel: String,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { Text(text) },
        confirmButton = {
            TextButton(onClick = { onConfirm(); onDismiss() }) {
                Text(confirmLabel, color = MaterialTheme.colorScheme.error)
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Annuler") } },
    )
}
