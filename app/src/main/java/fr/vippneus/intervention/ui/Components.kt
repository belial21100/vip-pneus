package fr.vippneus.intervention.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Draw
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.EditNote
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextFieldColors
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.scale
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import fr.vippneus.intervention.R
import fr.vippneus.intervention.data.Completion
import fr.vippneus.intervention.data.DisplayStatus
import fr.vippneus.intervention.data.Todo
import java.util.Locale

// ------------------------------------------------------------------ identité

/** Logo (pneu + coche, comme l'icône de l'application). */
@Composable
fun BrandMark(size: Dp, modifier: Modifier = Modifier) {
    Box(
        modifier
            .size(size)
            .clip(RoundedCornerShape(size * 0.28f))
            .background(Palette.Graphite800),
    ) {
        Image(
            painterResource(R.drawable.ic_launcher_foreground),
            contentDescription = null,
            modifier = Modifier
                .fillMaxSize()
                .scale(1.55f),
        )
    }
}

/** « VIP PNEUS » en capitales condensées. */
@Composable
fun Wordmark(modifier: Modifier = Modifier, fontSize: TextUnit = 30.sp, light: Boolean = true) {
    Text(
        buildAnnotatedString {
            withStyle(SpanStyle(color = if (light) Color.White else Palette.Graphite900)) { append("VIP ") }
            withStyle(SpanStyle(color = if (light) Palette.Amber else Palette.AmberDeep)) { append("PNEUS") }
        },
        fontFamily = BarlowCondensed,
        fontWeight = FontWeight.Bold,
        fontSize = fontSize,
        letterSpacing = 1.2.sp,
        maxLines = 1,
        modifier = modifier,
    )
}

/** Motif de sculpture de pneu (chevrons), en décor discret. */
@Composable
fun TreadPattern(modifier: Modifier = Modifier, color: Color = Color.White.copy(alpha = 0.05f), vertical: Boolean = true) {
    Canvas(modifier) {
        val step = 30.dp.toPx()
        val band = 11.dp.toPx()
        val stroke = Stroke(width = band, cap = StrokeCap.Butt, join = StrokeJoin.Miter)
        if (vertical) {
            val depth = size.width * 0.35f
            var y = -step
            while (y < size.height + step) {
                val p = Path().apply {
                    moveTo(0f, y)
                    lineTo(size.width / 2f, y + depth)
                    lineTo(size.width, y)
                }
                drawPath(p, color, style = stroke)
                y += step
            }
        } else {
            val depth = size.height * 0.35f
            var x = -step
            while (x < size.width + step) {
                val p = Path().apply {
                    moveTo(x, 0f)
                    lineTo(x + depth, size.height / 2f)
                    lineTo(x, size.height)
                }
                drawPath(p, color, style = stroke)
                x += step
            }
        }
    }
}

// ------------------------------------------------------------------ barres et boutons

/** Barre de titre graphite commune aux écrans : retour, titre, statut, actions. */
@Composable
fun VipTopBar(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    onBack: (() -> Unit)? = null,
    status: DisplayStatus? = null,
    actions: @Composable RowScope.() -> Unit = {},
) {
    val c = Vip.colors
    Surface(color = c.chrome, contentColor = c.onChrome, modifier = modifier.fillMaxWidth()) {
        Row(
            Modifier
                .statusBarsPadding()
                .height(72.dp)
                .padding(start = if (onBack != null) 8.dp else 24.dp, end = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (onBack != null) {
                IconButton(onClick = onBack, modifier = Modifier.size(52.dp)) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Retour")
                }
                Spacer(Modifier.width(6.dp))
            }
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        title,
                        style = MaterialTheme.typography.titleLarge,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false),
                    )
                    if (status != null) {
                        Spacer(Modifier.width(12.dp))
                        StatusChip(status, onDark = true)
                    }
                }
                if (!subtitle.isNullOrBlank()) {
                    Text(
                        subtitle,
                        style = MaterialTheme.typography.bodyMedium,
                        color = c.onChromeMuted,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                content = actions,
            )
        }
    }
}

enum class Tone { ACCENT, DARK, GHOST, CHROME, DANGER }

/** Bouton de l'application : ambre (action principale), graphite, contour, sur fond sombre, destructif. */
@Composable
fun VipButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    tone: Tone = Tone.ACCENT,
    enabled: Boolean = true,
    compact: Boolean = false,
) {
    val c = Vip.colors
    val s = MaterialTheme.colorScheme
    val (container, content) = when (tone) {
        Tone.ACCENT -> c.accent to c.onAccent
        Tone.DARK -> s.inverseSurface to s.inverseOnSurface
        Tone.GHOST -> Color.Transparent to s.primary
        Tone.CHROME -> Color.Transparent to c.onChrome
        Tone.DANGER -> c.dangerSoft to c.danger
    }
    val border = when (tone) {
        Tone.GHOST -> BorderStroke(1.dp, s.outline)
        Tone.CHROME -> BorderStroke(1.dp, c.onChrome.copy(alpha = 0.3f))
        else -> null
    }
    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.heightIn(min = if (compact) 44.dp else 52.dp),
        shape = MaterialTheme.shapes.small,
        border = border,
        colors = ButtonDefaults.buttonColors(
            containerColor = container,
            contentColor = content,
            disabledContainerColor = s.surfaceContainerHigh,
            disabledContentColor = s.onSurfaceVariant,
        ),
        elevation = ButtonDefaults.buttonElevation(0.dp, 0.dp, 0.dp, 0.dp, 0.dp),
        contentPadding = PaddingValues(horizontal = if (compact) 14.dp else 20.dp),
    ) {
        if (icon != null) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(10.dp))
        }
        Text(text, style = MaterialTheme.typography.labelLarge, maxLines = 1)
    }
}

// ------------------------------------------------------------------ statut

@Composable
fun statusColor(status: DisplayStatus): Color = when (status) {
    DisplayStatus.BROUILLON -> MaterialTheme.colorScheme.outline
    DisplayStatus.PRET -> Vip.colors.info
    DisplayStatus.ENVOYE -> Vip.colors.success
    DisplayStatus.MODIFIE -> Vip.colors.warning
}

/** Pastille de statut d'un bon (brouillon, PDF prêt, envoyé, modifié après envoi). */
@Composable
fun StatusChip(status: DisplayStatus, modifier: Modifier = Modifier, onDark: Boolean = false) {
    val c = Vip.colors
    val fg = statusColor(status)
    val (bg, text, dot) = when {
        onDark && status == DisplayStatus.BROUILLON -> Triple(Color.White.copy(alpha = 0.12f), Color.White.copy(alpha = 0.85f), Color.White.copy(alpha = 0.6f))
        onDark -> Triple(fg.copy(alpha = 0.28f), Color.White, lerp(fg, Color.White, 0.35f))
        status == DisplayStatus.BROUILLON -> Triple(MaterialTheme.colorScheme.surfaceContainerHigh, MaterialTheme.colorScheme.onSurfaceVariant, fg)
        status == DisplayStatus.PRET -> Triple(c.infoSoft, c.info, fg)
        status == DisplayStatus.ENVOYE -> Triple(c.successSoft, c.success, fg)
        else -> Triple(c.warningSoft, c.warning, fg)
    }
    Row(
        modifier
            .background(bg, CircleShape)
            .padding(horizontal = 10.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .size(7.dp)
                .background(dot, CircleShape),
        )
        Spacer(Modifier.width(6.dp))
        Text(status.label, style = MaterialTheme.typography.labelMedium, color = text, maxLines = 1)
    }
}

// ------------------------------------------------------------------ cartes

/** Pastille carrée portant l'icône d'une section. */
@Composable
fun IconBadge(
    icon: ImageVector,
    modifier: Modifier = Modifier,
    background: Color = Vip.colors.accentSoft,
    tint: Color = Vip.colors.onAccentSoft,
    size: Dp = 40.dp,
) {
    Box(
        modifier
            .size(size)
            .background(background, RoundedCornerShape(size * 0.3f)),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(size * 0.55f))
    }
}

@Composable
private fun CardHeader(
    title: String,
    icon: ImageVector?,
    subtitle: String?,
    trailing: (@Composable RowScope.() -> Unit)?,
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        if (icon != null) {
            IconBadge(icon)
            Spacer(Modifier.width(14.dp))
        }
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            if (subtitle != null) {
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = Vip.colors.muted)
            }
        }
        trailing?.invoke(this)
    }
}

/** Carte blanche titrée : une section du formulaire. */
@Composable
fun SectionCard(
    title: String,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    subtitle: String? = null,
    trailing: (@Composable RowScope.() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    val c = Vip.colors
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        color = c.card,
        border = BorderStroke(1.dp, c.cardBorder),
    ) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            CardHeader(title, icon, subtitle, trailing)
            content()
        }
    }
}

/** Intertitre dans une carte (capitales discrètes suivies d'un filet), avec une action éventuelle. */
@Composable
fun SubHeader(text: String, modifier: Modifier = Modifier, trailing: (@Composable () -> Unit)? = null) {
    val c = Vip.colors
    Row(
        modifier
            .fillMaxWidth()
            .padding(top = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text.uppercase(Locale.FRANCE),
            style = MaterialTheme.typography.labelMedium,
            color = c.muted,
            letterSpacing = 1.2.sp,
        )
        Spacer(Modifier.width(12.dp))
        HorizontalDivider(Modifier.weight(1f), color = c.cardBorder)
        if (trailing != null) {
            Spacer(Modifier.width(12.dp))
            trailing()
        }
    }
}

/** Carte repliable : repliée, elle résume son contenu sur une ligne. */
@Composable
fun CollapsibleCard(
    title: String,
    summary: String,
    expanded: Boolean,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    val c = Vip.colors
    val angle by animateFloatAsState(if (expanded) 180f else 0f, label = "chevron")
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        color = c.card,
        border = BorderStroke(1.dp, c.cardBorder),
    ) {
        Column(Modifier.animateContentSize()) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .clickable(onClick = onToggle)
                    .padding(20.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (icon != null) {
                    IconBadge(icon)
                    Spacer(Modifier.width(14.dp))
                }
                Column(Modifier.weight(1f)) {
                    Text(title, style = MaterialTheme.typography.titleMedium)
                    if (!expanded && summary.isNotBlank()) {
                        Text(
                            summary,
                            style = MaterialTheme.typography.bodyMedium,
                            color = c.muted,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
                Icon(
                    Icons.Filled.ExpandMore,
                    contentDescription = if (expanded) "Replier" else "Déplier",
                    tint = c.muted,
                    modifier = Modifier.rotate(angle),
                )
            }
            if (expanded) {
                Column(
                    Modifier.padding(start = 20.dp, end = 20.dp, bottom = 20.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                    content = content,
                )
            }
        }
    }
}

/** Bandeau d'information (import reconnu, réglages manquants...). */
@Composable
fun InfoBanner(
    icon: ImageVector,
    title: String,
    modifier: Modifier = Modifier,
    text: String? = null,
    background: Color = Vip.colors.accentSoft,
    content: Color = Vip.colors.onAccentSoft,
    action: (@Composable () -> Unit)? = null,
) {
    Surface(color = background, contentColor = content, shape = MaterialTheme.shapes.large, modifier = modifier.fillMaxWidth()) {
        Row(Modifier.padding(horizontal = 18.dp, vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(24.dp))
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleSmall)
                if (text != null) {
                    Text(text, style = MaterialTheme.typography.bodyMedium, color = content.copy(alpha = 0.85f))
                }
            }
            if (action != null) {
                Spacer(Modifier.width(12.dp))
                action()
            }
        }
    }
}

/** Rappel : les champs marqués ✦ viennent du document du client. */
@Composable
fun RecognizedBanner(docType: String, modifier: Modifier = Modifier, text: String? = null) {
    InfoBanner(
        icon = Icons.Filled.AutoAwesome,
        title = "Rempli automatiquement depuis « $docType »",
        text = text ?: "Les champs surlignés en jaune viennent du document : vérifiez-les, puis complétez le reste.",
        modifier = modifier,
    )
}

/** Écran vide (aucun bon, aucun résultat). */
@Composable
fun EmptyState(
    icon: ImageVector,
    title: String,
    text: String,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit = {},
) {
    val c = Vip.colors
    Column(
        modifier
            .fillMaxWidth()
            .padding(horizontal = 32.dp, vertical = 40.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(
            Modifier
                .size(88.dp)
                .background(c.accentSoft, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, contentDescription = null, tint = c.onAccentSoft, modifier = Modifier.size(44.dp))
        }
        Text(title, style = MaterialTheme.typography.titleLarge, textAlign = TextAlign.Center)
        Text(
            text,
            style = MaterialTheme.typography.bodyLarge,
            color = c.muted,
            textAlign = TextAlign.Center,
            modifier = Modifier.widthIn(max = 540.dp),
        )
        content()
    }
}

// ------------------------------------------------------------------ saisie

@Composable
fun vipFieldColors(auto: Boolean = false): TextFieldColors {
    val c = Vip.colors
    val s = MaterialTheme.colorScheme
    val container = if (auto) c.autoFill else c.card
    return OutlinedTextFieldDefaults.colors(
        focusedContainerColor = container,
        unfocusedContainerColor = container,
        disabledContainerColor = container,
        focusedBorderColor = s.primary,
        unfocusedBorderColor = if (auto) c.accent.copy(alpha = 0.7f) else s.outlineVariant,
        focusedLabelColor = s.primary,
        unfocusedLabelColor = c.muted,
        cursorColor = s.primary,
        focusedSupportingTextColor = c.muted,
        unfocusedSupportingTextColor = c.muted,
        focusedSuffixColor = c.muted,
        unfocusedSuffixColor = c.muted,
    )
}

/**
 * Champ de saisie : fond jaune pâle et ✦ s'il a été rempli depuis le document du client,
 * propositions (valeurs déjà saisies sur d'autres bons) sous le champ pendant la saisie.
 */
@Composable
fun VipField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    auto: Boolean = false,
    suggestions: List<String> = emptyList(),
    keyboardType: KeyboardType = KeyboardType.Text,
    capitalization: KeyboardCapitalization = KeyboardCapitalization.Sentences,
    singleLine: Boolean = true,
    minLines: Int = 1,
    placeholder: String? = null,
    suffix: String? = null,
    supporting: String? = null,
    trailing: (@Composable () -> Unit)? = null,
    focusRequester: FocusRequester? = null,
    /** Message d'erreur sous le champ (remplace [supporting]). */
    error: String? = null,
    /** Champ attendu avant l'envoi : « À compléter » tant qu'il est vide. */
    missing: Boolean = false,
) {
    var focused by remember { mutableStateOf(false) }
    val toFill = missing && error == null && value.isBlank()
    val sparkle: @Composable () -> Unit = {
        Icon(
            Icons.Filled.AutoAwesome,
            contentDescription = "Rempli depuis le document",
            tint = Palette.AmberDeep,
            modifier = Modifier.size(20.dp),
        )
    }
    Column(modifier) {
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            label = { Text(label, maxLines = 1, overflow = TextOverflow.Ellipsis) },
            placeholder = placeholder?.let { { Text(it, maxLines = 1, overflow = TextOverflow.Ellipsis) } },
            suffix = suffix?.let { { Text(it) } },
            singleLine = singleLine,
            minLines = minLines,
            keyboardOptions = KeyboardOptions(
                capitalization = capitalization,
                keyboardType = keyboardType,
                imeAction = if (singleLine) ImeAction.Next else ImeAction.Default,
            ),
            trailingIcon = when {
                trailing != null && auto -> {
                    { Row(verticalAlignment = Alignment.CenterVertically) { sparkle(); trailing() } }
                }
                trailing != null -> trailing
                auto -> sparkle
                else -> null
            },
            supportingText = when {
                error != null -> {
                    { Text(error) }
                }
                // « À compléter », suivi de l'aide éventuelle (couple préconisé…)
                toFill -> {
                    val warning = Vip.colors.warning
                    {
                        Text(
                            buildAnnotatedString {
                                withStyle(SpanStyle(color = warning, fontWeight = FontWeight.SemiBold)) { append("À compléter") }
                                if (supporting != null) append("  ·  $supporting")
                            },
                        )
                    }
                }
                supporting != null -> {
                    { Text(supporting) }
                }
                else -> null
            },
            isError = error != null,
            shape = MaterialTheme.shapes.small,
            colors = vipFieldColors(auto),
            textStyle = MaterialTheme.typography.bodyLarge,
            modifier = Modifier
                .fillMaxWidth()
                .then(if (focusRequester != null) Modifier.focusRequester(focusRequester) else Modifier)
                .onFocusChanged { focused = it.isFocused },
        )
        AnimatedVisibility(focused && suggestions.isNotEmpty()) {
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = PaddingValues(top = 8.dp),
            ) {
                items(suggestions) { s -> SuggestionPill(s) { onValueChange(s) } }
            }
        }
    }
}

@Composable
private fun SuggestionPill(text: String, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        shape = CircleShape,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        contentColor = MaterialTheme.colorScheme.onSurface,
    ) {
        Row(
            Modifier
                .heightIn(min = 40.dp)
                .padding(start = 12.dp, end = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.Filled.History, contentDescription = null, modifier = Modifier.size(16.dp), tint = Vip.colors.muted)
            Spacer(Modifier.width(6.dp))
            Text(text, style = MaterialTheme.typography.labelLarge, maxLines = 1)
        }
    }
}

/** Choix Oui / Non (un second appui sur le choix actif l'efface). */
@Composable
fun YesNo(label: String, value: String?, onChange: (String?) -> Unit, modifier: Modifier = Modifier) {
    val c = Vip.colors
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        Text(label, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
        Spacer(Modifier.width(12.dp))
        SingleChoiceSegmentedButtonRow {
            listOf("oui" to "Oui", "non" to "Non").forEachIndexed { index, (v, text) ->
                SegmentedButton(
                    selected = value == v,
                    onClick = { onChange(if (value == v) null else v) },
                    shape = SegmentedButtonDefaults.itemShape(index, 2),
                    colors = SegmentedButtonDefaults.colors(
                        activeContainerColor = MaterialTheme.colorScheme.inverseSurface,
                        activeContentColor = MaterialTheme.colorScheme.inverseOnSurface,
                        activeBorderColor = MaterialTheme.colorScheme.inverseSurface,
                        inactiveContainerColor = c.card,
                        inactiveBorderColor = MaterialTheme.colorScheme.outlineVariant,
                    ),
                    modifier = Modifier.width(92.dp),
                    label = { Text(text, style = MaterialTheme.typography.labelLarge) },
                )
            }
        }
    }
}

// ------------------------------------------------------------------ avancement

/** Anneau d'avancement « 5/8 » (coche verte quand tout est rempli). */
@Composable
fun ProgressRing(done: Int, total: Int, modifier: Modifier = Modifier, size: Dp = 52.dp) {
    val c = Vip.colors
    val complete = total == 0 || done >= total
    val fraction by animateFloatAsState(if (total == 0) 1f else done.toFloat() / total, label = "progress")
    val track = MaterialTheme.colorScheme.surfaceContainerHigh
    val color = if (complete) c.success else c.accent
    Box(modifier.size(size), contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val w = this.size.minDimension * 0.1f
            val arc = Size(this.size.width - w, this.size.height - w)
            val tl = Offset(w / 2f, w / 2f)
            drawArc(track, 0f, 360f, useCenter = false, topLeft = tl, size = arc, style = Stroke(w))
            drawArc(color, -90f, 360f * fraction, useCenter = false, topLeft = tl, size = arc, style = Stroke(w, cap = StrokeCap.Round))
        }
        if (complete) {
            Icon(Icons.Filled.Check, contentDescription = "Complet", tint = c.success, modifier = Modifier.size(size * 0.46f))
        } else {
            Text("$done/$total", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
        }
    }
}

/**
 * « À compléter » : chaque élément manquant, avec ce qu'il faut faire et un accès direct ;
 * ce qui est déjà fait est rappelé en dessous.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun TodoPanel(todos: List<Todo>, onJump: (Todo) -> Unit, modifier: Modifier = Modifier) {
    if (todos.isEmpty()) return
    val c = Vip.colors
    val missing = todos.filterNot { it.done }
    val done = todos.filter { it.done }
    val complete = missing.isEmpty()
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        color = if (complete) c.successSoft else c.card,
        border = BorderStroke(1.dp, if (complete) c.success.copy(alpha = 0.3f) else c.cardBorder),
    ) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                ProgressRing(done.size, todos.size)
                Spacer(Modifier.width(16.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        if (complete) "Tout est rempli" else "À compléter avant l'envoi",
                        style = MaterialTheme.typography.titleMedium,
                    )
                    Text(
                        when (missing.size) {
                            0 -> "Le bon est prêt à être envoyé à la comptabilité."
                            1 -> "Il manque 1 élément : touchez-le pour y aller."
                            else -> "Il manque ${missing.size} éléments : touchez-en un pour y aller."
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        color = c.muted,
                    )
                }
            }
            if (!complete) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    missing.forEach { t -> MissingRow(t) { onJump(t) } }
                }
                if (done.isNotEmpty()) {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text("Déjà fait :", style = MaterialTheme.typography.bodySmall, color = c.muted)
                        done.forEach { t ->
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Filled.CheckCircle, contentDescription = null, tint = c.success, modifier = Modifier.size(14.dp))
                                Spacer(Modifier.width(4.dp))
                                Text(t.label, style = MaterialTheme.typography.bodySmall, color = c.muted)
                            }
                        }
                    }
                }
            }
        }
    }
}

/** Élément manquant : libellé, ce qu'il faut faire, action. */
@Composable
private fun MissingRow(t: Todo, onClick: () -> Unit) {
    val c = Vip.colors
    val signature = t.key == Completion.SIGNATURE
    Surface(
        onClick = onClick,
        shape = MaterialTheme.shapes.medium,
        color = c.accentSoft,
        contentColor = c.onAccentSoft,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            Modifier
                .heightIn(min = 56.dp)
                .padding(start = 14.dp, end = 10.dp, top = 8.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(if (signature) Icons.Filled.Draw else Icons.Filled.EditNote, contentDescription = null, modifier = Modifier.size(22.dp))
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(t.label, style = MaterialTheme.typography.titleSmall)
                if (t.hint.isNotEmpty()) {
                    Text(t.hint, style = MaterialTheme.typography.bodySmall, color = c.onAccentSoft.copy(alpha = 0.8f))
                }
            }
            Spacer(Modifier.width(8.dp))
            Text(if (signature) "Faire signer" else "Remplir", style = MaterialTheme.typography.labelLarge)
            Icon(Icons.Filled.ChevronRight, contentDescription = null, modifier = Modifier.size(20.dp))
        }
    }
}

/** Pastille d'état d'une section : à compléter, ou complète. */
@Composable
fun SectionStatus(complete: Boolean) {
    val c = Vip.colors
    Row(
        Modifier
            .background(if (complete) c.successSoft else c.accentSoft, CircleShape)
            .padding(horizontal = 10.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            if (complete) Icons.Filled.Check else Icons.Filled.EditNote,
            contentDescription = null,
            tint = if (complete) c.success else c.onAccentSoft,
            modifier = Modifier.size(14.dp),
        )
        Spacer(Modifier.width(5.dp))
        Text(
            if (complete) "Complet" else "À compléter",
            style = MaterialTheme.typography.labelMedium,
            color = if (complete) c.success else c.onAccentSoft,
        )
    }
}

/**
 * Barre d'envoi en bas des écrans de saisie : ce qui manque (touchez pour y aller),
 * nom du fichier (touchez pour le changer), aperçu, envoi.
 */
@Composable
fun SendBar(
    fileName: String,
    missing: List<String>,
    onRename: () -> Unit,
    onPreview: () -> Unit,
    onSend: () -> Unit,
    onMissing: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val c = Vip.colors
    Surface(modifier.fillMaxWidth(), color = c.card, shadowElevation = 10.dp) {
        Column {
            HorizontalDivider(color = c.cardBorder)
            Row(
                Modifier
                    .navigationBarsPadding()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconBadge(Icons.Filled.PictureAsPdf, background = c.dangerSoft, tint = c.danger)
                Column(
                    Modifier
                        .weight(1f)
                        .padding(start = 6.dp),
                ) {
                    if (missing.isNotEmpty()) {
                        Row(
                            Modifier
                                .clip(MaterialTheme.shapes.small)
                                .clickable(onClick = onMissing)
                                .padding(horizontal = 8.dp, vertical = 2.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(Icons.Filled.Warning, contentDescription = null, tint = c.warning, modifier = Modifier.size(15.dp))
                            Spacer(Modifier.width(6.dp))
                            Text(
                                "Manque : " + missing.joinToString(", "),
                                style = MaterialTheme.typography.labelMedium,
                                color = c.warning,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    } else {
                        Text(
                            "PDF pour la comptabilité",
                            style = MaterialTheme.typography.labelMedium,
                            color = c.muted,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                        )
                    }
                    Row(
                        Modifier
                            .clip(MaterialTheme.shapes.small)
                            .clickable(onClick = onRename)
                            .padding(horizontal = 8.dp, vertical = 2.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            "$fileName.pdf",
                            style = MaterialTheme.typography.titleSmall,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f, fill = false),
                        )
                        Spacer(Modifier.width(6.dp))
                        Icon(Icons.Filled.Edit, contentDescription = "Renommer le fichier", tint = c.muted, modifier = Modifier.size(16.dp))
                    }
                }
                Spacer(Modifier.width(8.dp))
                VipButton("Aperçu PDF", onPreview, icon = Icons.Filled.Visibility, tone = Tone.GHOST)
                Spacer(Modifier.width(10.dp))
                VipButton("Envoyer à la compta", onSend, icon = Icons.AutoMirrored.Filled.Send)
            }
        }
    }
}

// ------------------------------------------------------------------ attente

/** Voile bloquant pendant une opération longue (import, création du PDF...). */
@Composable
fun BusyOverlay(text: String) {
    val c = Vip.colors
    Box(
        Modifier
            .fillMaxSize()
            .background(Palette.Graphite950.copy(alpha = 0.55f))
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {},
        contentAlignment = Alignment.Center,
    ) {
        Surface(shape = MaterialTheme.shapes.large, color = c.card, shadowElevation = 16.dp) {
            Row(Modifier.padding(horizontal = 28.dp, vertical = 24.dp), verticalAlignment = Alignment.CenterVertically) {
                CircularProgressIndicator(
                    modifier = Modifier.size(36.dp),
                    color = c.accent,
                    trackColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                    strokeWidth = 4.dp,
                )
                Spacer(Modifier.width(20.dp))
                Text(text, style = MaterialTheme.typography.titleMedium)
            }
        }
    }
}
