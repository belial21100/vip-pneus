package fr.vippneus.intervention.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Backspace
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Dialpad
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalViewConfiguration
import androidx.compose.ui.platform.ViewConfiguration
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import fr.vippneus.intervention.data.DimensionInput
import fr.vippneus.intervention.data.Qty

// ------------------------------------------------------------------ choix rapides

/** Choix rapides sous un champ : les valeurs les plus utilisées ; un appui remplit le champ. */
@Composable
fun QuickPicks(label: String, options: List<String>, selected: String, onPick: (String) -> Unit, modifier: Modifier = Modifier) {
    if (options.isEmpty()) return
    Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(label, style = MaterialTheme.typography.labelMedium, color = Vip.colors.muted, modifier = Modifier.width(92.dp))
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier
                .weight(1f)
                .horizontalScroll(rememberScrollState()),
        ) {
            options.forEach { o -> PickChip(o, o.equals(selected.trim(), ignoreCase = true)) { onPick(o) } }
        }
    }
}

@Composable
fun PickChip(text: String, selected: Boolean, onClick: () -> Unit) {
    val s = MaterialTheme.colorScheme
    Surface(
        onClick = onClick,
        shape = CircleShape,
        color = if (selected) s.inverseSurface else s.surfaceContainerHigh,
        contentColor = if (selected) s.inverseOnSurface else s.onSurface,
    ) {
        Box(
            Modifier
                .heightIn(min = 40.dp)
                .padding(horizontal = 14.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(text, style = MaterialTheme.typography.labelLarge, maxLines = 1)
        }
    }
}

// ------------------------------------------------------------------ quantités

/** Quantité : − et + de part et d'autre ; le nombre reste modifiable au clavier. */
@Composable
fun QtyStepper(
    value: String,
    onChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    description: String = "",
    auto: Boolean = false,
    focusRequester: FocusRequester? = null,
) {
    val c = Vip.colors
    val s = MaterialTheme.colorScheme
    val count = Qty.isCount(value)
    val shape = MaterialTheme.shapes.small
    Row(
        modifier
            .height(48.dp)
            .clip(shape)
            .background(if (auto) c.autoFill else c.card)
            .border(1.dp, if (auto) c.accent.copy(alpha = 0.7f) else s.outlineVariant, shape),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = { onChange(Qty.step(value, -1)) }, enabled = count && value.isNotBlank(), modifier = Modifier.size(44.dp)) {
            Icon(Icons.Filled.Remove, contentDescription = "Retirer 1 $description".trim())
        }
        BasicTextField(
            value = value,
            onValueChange = onChange,
            singleLine = true,
            textStyle = MaterialTheme.typography.titleMedium.copy(textAlign = TextAlign.Center, color = s.onSurface),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal, imeAction = ImeAction.Next),
            cursorBrush = SolidColor(s.primary),
            modifier = Modifier
                .weight(1f)
                .then(if (focusRequester != null) Modifier.focusRequester(focusRequester) else Modifier),
        )
        IconButton(onClick = { onChange(Qty.step(value, 1)) }, enabled = count, modifier = Modifier.size(44.dp)) {
            Icon(Icons.Filled.Add, contentDescription = "Ajouter 1 $description".trim())
        }
    }
}

/** Quantité avec son libellé au-dessus. */
@Composable
fun LabeledStepper(
    label: String,
    value: String,
    onChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    auto: Boolean = false,
    focusRequester: FocusRequester? = null,
) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(label, style = MaterialTheme.typography.labelMedium, color = Vip.colors.muted)
        QtyStepper(value, onChange, Modifier.fillMaxWidth(), description = label, auto = auto, focusRequester = focusRequester)
    }
}

/**
 * Case de comptage (tableau des prestations) : un appui ajoute 1, « − » retire 1,
 * un appui long ouvre le clavier (texte libre).
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun CounterCell(
    value: String,
    onChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    description: String = "",
    auto: Boolean = false,
    focusRequester: FocusRequester? = null,
) {
    val c = Vip.colors
    val s = MaterialTheme.colorScheme
    var typing by remember { mutableStateOf(false) }
    if (typing) {
        val focus = focusRequester ?: remember { FocusRequester() }
        var focused by remember { mutableStateOf(false) }
        OutlinedTextField(
            value = value,
            onValueChange = onChange,
            singleLine = true,
            textStyle = MaterialTheme.typography.titleLarge.copy(textAlign = TextAlign.Center),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { typing = false }),
            shape = MaterialTheme.shapes.small,
            colors = vipFieldColors(auto),
            modifier = modifier
                .focusRequester(focus)
                .onFocusChanged {
                    if (focused && !it.isFocused) typing = false
                    focused = it.isFocused
                },
        )
        LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }
        return
    }
    val count = Qty.isCount(value)
    val shape = MaterialTheme.shapes.small
    Box(
        modifier
            .height(56.dp)
            .clip(shape)
            .background(if (auto) c.autoFill else c.card)
            .border(1.dp, if (auto) c.accent.copy(alpha = 0.7f) else s.outlineVariant, shape)
            .then(if (focusRequester != null) Modifier.focusRequester(focusRequester) else Modifier)
            .focusable()
            .combinedClickable(
                onClick = { if (count) onChange(Qty.step(value, 1)) else typing = true },
                onLongClick = { typing = true },
            )
            .semantics { contentDescription = description },
        contentAlignment = Alignment.Center,
    ) {
        if (value.isBlank()) {
            Icon(Icons.Filled.Add, contentDescription = null, tint = c.muted.copy(alpha = 0.45f))
        } else {
            Text(value, style = MaterialTheme.typography.titleLarge, maxLines = 1)
        }
        if (count && value.isNotBlank()) {
            // « − » dans le coin : sa zone d'appui reste le coin (agrandie à 48 dp, elle prendrait le centre de la case)
            val config = LocalViewConfiguration.current
            CompositionLocalProvider(LocalViewConfiguration provides ExactTouchTarget(config)) {
                Box(
                    Modifier
                        .align(Alignment.TopStart)
                        .size(28.dp)
                        .clip(CircleShape)
                        .clickable { onChange(Qty.step(value, -1)) }
                        .semantics { contentDescription = "Retirer 1 $description".trim() },
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(Icons.Filled.Remove, contentDescription = null, modifier = Modifier.size(16.dp))
                }
            }
        }
    }
}

/** Réglages tactiles sans agrandissement des petites zones d'appui. */
private class ExactTouchTarget(base: ViewConfiguration) : ViewConfiguration by base {
    override val minimumTouchTargetSize: DpSize get() = DpSize.Zero
}

// ------------------------------------------------------------------ dimensions

/** Champ « Dimensions » : un appui ouvre le pavé des dimensions (sans le clavier complet). */
@Composable
fun DimensionField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    frequent: List<String>,
    modifier: Modifier = Modifier,
    auto: Boolean = false,
    missing: Boolean = false,
    focusRequester: FocusRequester? = null,
) {
    var pad by remember { mutableStateOf(false) }
    VipField(
        label = label,
        value = value,
        onValueChange = onValueChange,
        modifier = modifier,
        auto = auto,
        missing = missing,
        focusRequester = focusRequester,
        trailing = { Icon(Icons.Filled.Dialpad, contentDescription = null) },
        onClick = { pad = true },
    )
    if (pad) {
        DimensionPadDialog(label, value, frequent, onDismiss = { pad = false }) {
            onValueChange(it)
            pad = false
        }
    }
}

/** Pavé des dimensions : chiffres, « / », « R », « x »… et les dimensions les plus utilisées. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun DimensionPadDialog(title: String, initial: String, frequent: List<String>, onDismiss: () -> Unit, onDone: (String) -> Unit) {
    val c = Vip.colors
    val s = MaterialTheme.colorScheme
    var value by remember { mutableStateOf(initial) }
    var keyboard by remember { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = c.card,
        title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                if (keyboard) {
                    val focus = remember { FocusRequester() }
                    OutlinedTextField(
                        value = value,
                        onValueChange = { value = it },
                        singleLine = true,
                        textStyle = MaterialTheme.typography.titleLarge,
                        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Characters, imeAction = ImeAction.Done),
                        keyboardActions = KeyboardActions(onDone = { onDone(value.trim()) }),
                        shape = MaterialTheme.shapes.small,
                        colors = vipFieldColors(),
                        modifier = Modifier
                            .fillMaxWidth()
                            .focusRequester(focus),
                    )
                    LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }
                } else {
                    Surface(
                        shape = MaterialTheme.shapes.small,
                        color = s.background,
                        border = BorderStroke(1.dp, s.outlineVariant),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(
                            value.ifEmpty { "ex. 315/80 R22.5" },
                            style = MaterialTheme.typography.headlineSmall,
                            color = if (value.isEmpty()) c.muted else s.onSurface,
                            maxLines = 1,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                        )
                    }
                }
                if (frequent.isNotEmpty()) {
                    Text("Les plus utilisées", style = MaterialTheme.typography.labelMedium, color = c.muted)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        frequent.forEach { PickChip(it, it.equals(value.trim(), ignoreCase = true)) { onDone(it) } }
                    }
                }
                if (!keyboard) {
                    DimensionKeypad(
                        onKey = { value = DimensionInput.type(value, it) },
                        onBack = { value = value.dropLast(1) },
                    )
                }
                TextButton(onClick = { keyboard = !keyboard }) {
                    Text(if (keyboard) "Pavé des dimensions" else "Clavier complet")
                }
            }
        },
        confirmButton = { VipButton("OK", { onDone(value.trim()) }, compact = true) },
        dismissButton = {
            Row {
                TextButton(onClick = { value = "" }) { Text("Effacer") }
                TextButton(onClick = onDismiss) { Text("Annuler") }
            }
        },
    )
}

@Composable
private fun DimensionKeypad(onKey: (String) -> Unit, onBack: () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        DimensionInput.ROWS.forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                row.forEach { k -> PadKey(k, Modifier.weight(1f)) { onKey(k) } }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            PadKey("0", Modifier.weight(2f)) { onKey("0") }
            PadKey("espace", Modifier.weight(2f)) { onKey(" ") }
            Surface(
                onClick = onBack,
                shape = MaterialTheme.shapes.small,
                color = MaterialTheme.colorScheme.surfaceContainerHighest,
                modifier = Modifier
                    .weight(1f)
                    .height(52.dp),
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(Icons.AutoMirrored.Filled.Backspace, contentDescription = "Effacer le dernier caractère")
                }
            }
        }
    }
}

@Composable
private fun PadKey(text: String, modifier: Modifier, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        shape = MaterialTheme.shapes.small,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        modifier = modifier
            .height(52.dp)
            .semantics { contentDescription = "Touche $text" },
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(text, style = if (text.length > 1) MaterialTheme.typography.labelLarge else MaterialTheme.typography.titleLarge)
        }
    }
}
