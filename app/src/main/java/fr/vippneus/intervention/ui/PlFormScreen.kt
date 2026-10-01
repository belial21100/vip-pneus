package fr.vippneus.intervention.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Assignment
import androidx.compose.material.icons.automirrored.filled.Notes
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Checklist
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Handyman
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.filled.LocalShipping
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.TireRepair
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateMap
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import fr.vippneus.intervention.data.Completion
import fr.vippneus.intervention.data.Intervention
import fr.vippneus.intervention.data.PlTyres
import fr.vippneus.intervention.data.Qty
import fr.vippneus.intervention.data.Suggestions
import fr.vippneus.intervention.data.Todo
import fr.vippneus.intervention.pdf.FpsTemplate.K
import fr.vippneus.intervention.pdf.PlKeys
import fr.vippneus.intervention.pdf.PlTemplate
import fr.vippneus.intervention.pdf.TyreTable
import java.time.LocalTime
import java.time.format.DateTimeFormatter

private val TYRE_KEY = Regex("""^pl\.[md]\d+\.""")

/** Section de la fiche poids lourds où se trouve un élément à compléter. */
private fun plSectionOf(key: String): String = when {
    key == K.MARQUE || key == K.HORAMETRE || key == PlKeys.VEHICULE -> "vehicule"
    key == Completion.PL_TRAVAIL || TYRE_KEY.containsMatchIn(key) || key.startsWith("pl.pneu.") || key.startsWith("pl.roue.") -> "roues"
    key.startsWith("pl.fourn.") -> "fournitures"
    key.startsWith("pl.serv.") -> "services"
    key == Completion.SIGNATURE -> "signature"
    else -> "intervention"
}

internal val PL_OUTLINE = FormOutline(
    listOf(
        "intervention" to "Intervention",
        "vehicule" to "Véhicule",
        "roues" to "Roues et pneus",
        "fournitures" to "Fournitures",
        "services" to "Services",
        "signature" to "Signature",
        "pdf" to "PDF",
    ),
    ::plSectionOf,
)

/**
 * Roue ou pneu à saisir : position, et lignes déjà occupées dans les tableaux ;
 * ou plusieurs roues sélectionnées sur le schéma ([group]), saisies d'un coup.
 */
private data class TyreRequest(
    val position: String,
    val mountedRow: Int?,
    val dismountedRow: Int?,
    val group: List<String> = emptyList(),
)

/** Fiche d'intervention poids lourds de VIP. */
@Composable
internal fun PlForm(form: Form, todos: List<Todo>, onJump: (Todo) -> Unit, onSign: () -> Unit, onResend: () -> Unit, modifier: Modifier) {
    val i = form.i
    val nav = form.nav
    var request by remember { mutableStateOf<TyreRequest?>(null) }
    // Sélection de plusieurs roues, saisies d'un coup
    var multi by remember { mutableStateOf(false) }
    var selected by remember { mutableStateOf(setOf<String>()) }

    fun openWheel(position: String) {
        request = TyreRequest(position, PlTyres.find(i, TyreTable.MONTES, position), PlTyres.find(i, TyreTable.DEMONTES, position))
    }

    fun endSelection() {
        multi = false
        selected = emptySet()
    }

    /** Roues sélectionnées, dans l'ordre du schéma. */
    fun selection() = PlTemplate.wheels.map { it.position }.filter { it in selected }

    SheetFormColumn(form, PL_OUTLINE, todos, onJump, onSign, onResend, modifier) { status ->
        SectionCard("Intervention", nav.anchor("intervention"), icon = Icons.AutoMirrored.Filled.Assignment, trailing = status("intervention")) {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                form.Field(K.NUMERO_COMMANDE, "N°", Modifier.weight(1f), caps = KeyboardCapitalization.Characters)
                form.Field(
                    K.DATE, "Date", Modifier.weight(1f),
                    trailing = { DatePickerIcon(i.value(K.DATE)) { form.set(K.DATE, it) } },
                )
            }
            form.Field(K.MONTEUR, "Nom tech")
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                form.Field(K.CLIENT_MANDATAIRE, "Nom du donneur d'ordre", Modifier.weight(1f), caps = KeyboardCapitalization.Characters)
                form.Field(K.CLIENT_UTILISATEUR, "Nom du client", Modifier.weight(1f), caps = KeyboardCapitalization.Characters)
            }
            form.Field(K.UTILISATEUR_ADRESSE, "Adresse")
            form.Field(PlKeys.LIEU, "Lieu du dépannage", placeholder = "ex. A26 sortie 13, parking du dépôt…")
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                form.Field(PlKeys.KM_DEPART, "Km départ", Modifier.weight(1f), keyboard = KeyboardType.Number)
                form.Field(PlKeys.KM_ARRIVEE, "Km arrivée", Modifier.weight(1f), keyboard = KeyboardType.Number)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                form.Field(PlKeys.HEURE_DEPART, "Heure départ", Modifier.weight(1f), trailing = { NowIcon { form.set(PlKeys.HEURE_DEPART, it) } })
                form.Field(PlKeys.HEURE_ARRIVEE, "Heure arrivée", Modifier.weight(1f), trailing = { NowIcon { form.set(PlKeys.HEURE_ARRIVEE, it) } })
            }
        }

        SectionCard("Véhicule", nav.anchor("vehicule"), icon = Icons.Filled.LocalShipping, trailing = status("vehicule")) {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                form.Field(PlKeys.VEHICULE, "Véhicule", Modifier.weight(1f), placeholder = "Tracteur, porteur, semi…")
                form.Field(K.MARQUE, "Marque", Modifier.weight(1f), caps = KeyboardCapitalization.Words)
                form.Field(K.TYPE, "Type", Modifier.weight(1f), caps = KeyboardCapitalization.Characters)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                form.Field(K.SERIE, "N° IMT / série", Modifier.weight(1f), caps = KeyboardCapitalization.Characters)
                form.Field(K.PARC, "N° parc", Modifier.weight(1f), caps = KeyboardCapitalization.Characters)
                form.Field(K.HORAMETRE, "Km / heure", Modifier.weight(1f), keyboard = KeyboardType.Number)
            }
        }

        SectionCard(
            "Roues et pneus", nav.anchor("roues"), icon = Icons.Filled.TireRepair,
            subtitle = "Touchez une roue : pneu monté, pneu démonté ; elle est cochée sur la fiche",
            trailing = status("roues"),
        ) {
            WheelSelectionBar(
                multi = multi,
                count = selected.size,
                onStart = { multi = true },
                onEnter = { request = TyreRequest("", null, null, group = selection()) },
                onCancel = ::endSelection,
            )
            WheelSchema(
                i, selected.takeIf { multi },
                onWheel = { p -> if (multi) selected = if (p in selected) selected - p else selected + p else openWheel(p) },
                onAxle = { axle ->
                    // Le nom d'un essieu sélectionne (ou désélectionne) toutes ses roues
                    val positions = axle.wheels.map { it.position }
                    multi = true
                    selected = if (selected.containsAll(positions)) selected - positions.toSet() else selected + positions
                },
            )
            for (t in TyreTable.entries) {
                TyreList(i, t, onEdit = { row ->
                    val p = PlTyres.position(i, t, row)
                    request = if (p.isNotBlank()) {
                        TyreRequest(p, PlTyres.find(i, TyreTable.MONTES, p), PlTyres.find(i, TyreTable.DEMONTES, p))
                    } else {
                        TyreRequest("", row.takeIf { t == TyreTable.MONTES }, row.takeIf { t == TyreTable.DEMONTES })
                    }
                })
            }
            VipButton(
                "Ajouter un pneu sans le schéma",
                { request = TyreRequest("", null, null) },
                icon = Icons.Filled.Add,
                tone = Tone.GHOST,
                compact = true,
            )
            SubHeader("Pneus : dimensions, AV/AR, N/R/O, quantité")
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                form.Dimension(PlKeys.pneu("dimensions"), "Dimensions pneus", Modifier.weight(1.2f))
                form.Field(PlKeys.pneu("marque"), "Marque", Modifier.weight(1f), caps = KeyboardCapitalization.Words)
                form.Field(PlKeys.pneu("type"), "Type", Modifier.weight(1f))
            }
            form.Picks(PlKeys.pneu("dimensions"), "Dimensions")
            form.Picks(PlKeys.pneu("marque"), "Marque")
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.Bottom) {
                Segments(
                    "AV/AR", listOf("AV" to "AV", "AR" to "AR"), form.value(PlKeys.pneu("avar")).ifEmpty { null },
                    { form.set(PlKeys.pneu("avar"), it.orEmpty()) }, Modifier.weight(1f), itemWidth = 64.dp,
                )
                Segments(
                    "N/R/O", listOf("N" to "N", "R" to "R", "O" to "O"), form.value(PlKeys.pneu("nro")).ifEmpty { null },
                    { form.set(PlKeys.pneu("nro"), it.orEmpty()) }, Modifier.weight(1.2f), itemWidth = 56.dp,
                )
                form.Stepper(PlKeys.pneu("qte"), "Qté", Modifier.width(140.dp))
            }
        }

        SectionCard(
            "Fournitures", nav.anchor("fournitures"), icon = Icons.Filled.Inventory2,
            subtitle = "Un appui sur une fourniture ajoute 1", trailing = status("fournitures"),
        ) {
            TwoColumns(PlTemplate.fournitures, twoFrom = 760.dp) { (item, label, _), m ->
                val qte = PlKeys.fourniture(item, "qte")
                Row(m, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    CountLabel(form, qte, label, Modifier.weight(1f))
                    Cell(form, PlKeys.fourniture(item, "dimensions"), Modifier.width(120.dp), placeholder = "Dim.")
                    QtyStepper(
                        form.value(qte), { form.set(qte, it) }, Modifier.width(136.dp),
                        description = label, auto = i.isAuto(qte), focusRequester = nav.focus(qte),
                    )
                }
            }
        }

        SectionCard(
            "Services", nav.anchor("services"), icon = Icons.Filled.Handyman,
            subtitle = "Un appui sur un service ajoute 1", trailing = status("services"),
        ) {
            TwoColumns(PlTemplate.services) { (item, label, _), m ->
                val key = PlKeys.service(item)
                Row(m, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    CountLabel(form, key, label, Modifier.weight(1f))
                    QtyStepper(
                        form.value(key), { form.set(key, it) }, Modifier.width(136.dp),
                        description = label, auto = i.isAuto(key), focusRequester = nav.focus(key),
                    )
                }
            }
        }

        SectionCard("Observations technicien", icon = Icons.AutoMirrored.Filled.Notes) {
            form.Field(K.OBSERVATIONS, "Observations", singleLine = false, minLines = 3)
            Text(
                "L'avertissement sur le serrage des roues est déjà imprimé sur la fiche.",
                style = MaterialTheme.typography.bodySmall,
                color = Vip.colors.muted,
            )
        }
    }

    request?.let { r ->
        TyreDialog(
            form, r,
            onDismiss = { request = null },
            onSaved = {
                request = null
                if (r.group.isNotEmpty()) endSelection()
            },
        )
    }
}

/** Libellé d'une quantité : un appui ajoute 1. */
@Composable
private fun CountLabel(form: Form, key: String, label: String, modifier: Modifier) {
    val v = form.value(key)
    Text(
        label,
        style = MaterialTheme.typography.bodyMedium,
        modifier = modifier
            .clip(MaterialTheme.shapes.small)
            .clickable(enabled = Qty.isCount(v)) { form.set(key, Qty.step(v, 1)) }
            .padding(vertical = 12.dp, horizontal = 4.dp),
    )
}

/** Heure actuelle (« 14:35 »). */
@Composable
private fun NowIcon(onPick: (String) -> Unit) {
    IconButton(onClick = { onPick(LocalTime.now().format(DateTimeFormatter.ofPattern("HH:mm"))) }) {
        Icon(Icons.Filled.Schedule, contentDescription = "Maintenant")
    }
}

/** Liste sur deux colonnes à partir de la largeur [twoFrom] (une seule en dessous). */
@Composable
private fun <T> TwoColumns(items: List<T>, twoFrom: Dp = 560.dp, cell: @Composable (T, Modifier) -> Unit) {
    BoxWithConstraints {
        val per = if (maxWidth >= twoFrom) 2 else 1
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items.chunked(per).forEach { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                    row.forEach { cell(it, Modifier.weight(1f)) }
                    repeat(per - row.size) { Spacer(Modifier.weight(1f)) }
                }
            }
        }
    }
}

/** Petite case de saisie sans libellé (quantité, dimension). */
@Composable
private fun Cell(form: Form, key: String, modifier: Modifier, number: Boolean = false, placeholder: String? = null) {
    OutlinedTextField(
        value = form.value(key),
        onValueChange = { form.set(key, it) },
        singleLine = true,
        placeholder = placeholder?.let { { Text(it, modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center) } },
        textStyle = MaterialTheme.typography.bodyLarge.copy(textAlign = TextAlign.Center),
        keyboardOptions = KeyboardOptions(
            keyboardType = if (number) KeyboardType.Decimal else KeyboardType.Text,
            imeAction = ImeAction.Next,
        ),
        shape = MaterialTheme.shapes.small,
        colors = vipFieldColors(form.i.isAuto(key)),
        modifier = modifier.focusRequester(form.nav.focus(key)),
    )
}

// ---------------------------------------------------------------- schéma des positions

/** Sélection de plusieurs roues : bouton pour la commencer, puis saisie des roues choisies (« Retirer » est dans la fenêtre). */
@Composable
private fun WheelSelectionBar(multi: Boolean, count: Int, onStart: () -> Unit, onEnter: () -> Unit, onCancel: () -> Unit) {
    val c = Vip.colors
    if (!multi) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                "Touchez une roue ; le nom d'un essieu sélectionne toutes ses roues",
                style = MaterialTheme.typography.bodySmall,
                color = c.muted,
                modifier = Modifier.weight(1f),
            )
            Spacer(Modifier.width(12.dp))
            VipButton("Plusieurs roues", onStart, icon = Icons.Filled.Checklist, tone = Tone.GHOST, compact = true)
        }
        return
    }
    Surface(shape = MaterialTheme.shapes.medium, color = c.accentSoft, contentColor = c.onAccentSoft) {
        Row(Modifier.padding(start = 16.dp, end = 8.dp, top = 6.dp, bottom = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(
                when (count) {
                    0 -> "Touchez les roues à saisir ensemble"
                    1 -> "1 roue sélectionnée"
                    else -> "$count roues sélectionnées"
                },
                style = MaterialTheme.typography.titleSmall,
                modifier = Modifier.weight(1f),
            )
            TextButton(onClick = onCancel) { Text("Annuler") }
            VipButton("Saisir les pneus", onEnter, enabled = count > 0, compact = true)
        }
    }
}

/** Porteur / tracteur et remorque, comme sur la fiche : chaque roue se touche ([selected] : sélection en cours). */
@Composable
private fun WheelSchema(i: Intervention, selected: Set<String>?, onWheel: (String) -> Unit, onAxle: (PlTemplate.Axle) -> Unit) {
    BoxWithConstraints {
        if (maxWidth >= 580.dp) {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                PlTemplate.vehicles.forEach { VehicleSchema(i, it, selected, onWheel, onAxle, Modifier.weight(1f)) }
            }
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                PlTemplate.vehicles.forEach { VehicleSchema(i, it, selected, onWheel, onAxle, Modifier.fillMaxWidth()) }
            }
        }
    }
    Text(
        "M : pneu monté  ·  D : pneu démonté  ·  ✓ : roue cochée",
        style = MaterialTheme.typography.bodySmall,
        color = Vip.colors.muted,
    )
}

@Composable
private fun VehicleSchema(
    i: Intervention,
    vehicle: PlTemplate.Vehicle,
    selected: Set<String>?,
    onWheel: (String) -> Unit,
    onAxle: (PlTemplate.Axle) -> Unit,
    modifier: Modifier,
) {
    val c = Vip.colors
    Column(
        modifier
            .border(1.dp, c.cardBorder, MaterialTheme.shapes.medium)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text(vehicle.name, style = MaterialTheme.typography.titleSmall)
        vehicle.axles.forEach { axle -> AxleRow(i, axle, selected, onWheel, onAxle) }
    }
}

private val WHEEL_W = 44.dp
private val WHEEL_H = 34.dp

@Composable
private fun AxleRow(i: Intervention, axle: PlTemplate.Axle, selected: Set<String>?, onWheel: (String) -> Unit, onAxle: (PlTemplate.Axle) -> Unit) {
    val c = Vip.colors
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            axle.label,
            style = if (axle.label.length > 6) MaterialTheme.typography.labelSmall else MaterialTheme.typography.labelLarge,
            color = c.muted,
            maxLines = 1,
            modifier = Modifier
                .width(64.dp)
                .clip(MaterialTheme.shapes.small)
                .clickable { onAxle(axle) }
                .padding(vertical = 10.dp)
                .semantics { contentDescription = "Essieu ${axle.label}" },
        )
        Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
            // Essieu : trait sous les roues
            if (!axle.spare) {
                Box(
                    Modifier
                        .width(WHEEL_W * 4 + 36.dp)
                        .height(3.dp)
                        .background(c.cardBorder),
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                // 4 emplacements (G ext, G int, D int, D ext) ; roues simples aux places intérieures
                val slots: List<PlTemplate.Wheel?> = when (axle.wheels.size) {
                    4 -> axle.wheels
                    2 -> listOf(null, axle.wheels[0], axle.wheels[1], null)
                    else -> emptyList()
                }
                if (slots.isEmpty()) {
                    axle.wheels.forEach { WheelButton(i, it, selected, onWheel) }
                } else {
                    slots.forEachIndexed { n, w ->
                        if (n == 2) Spacer(Modifier.width(12.dp))
                        if (w == null) Spacer(Modifier.size(WHEEL_W, WHEEL_H)) else WheelButton(i, w, selected, onWheel)
                    }
                }
            }
        }
    }
}

@Composable
private fun WheelButton(i: Intervention, wheel: PlTemplate.Wheel, selected: Set<String>?, onWheel: (String) -> Unit) {
    val c = Vip.colors
    val mounted = PlTyres.find(i, TyreTable.MONTES, wheel.position) != null
    val dismounted = PlTyres.find(i, TyreTable.DEMONTES, wheel.position) != null
    val checked = PlTyres.isChecked(i, wheel.position)
    val badge = when {
        mounted && dismounted -> "M·D"
        mounted -> "M"
        dismounted -> "D"
        checked -> "✓"
        else -> ""
    }
    val (bg, fg) = when {
        mounted || dismounted -> c.accent to c.onAccent
        checked -> c.accentSoft to c.onAccentSoft
        else -> c.card to c.muted
    }
    Surface(
        onClick = { onWheel(wheel.position) },
        shape = RoundedCornerShape(6.dp),
        color = bg,
        contentColor = fg,
        border = when {
            selected != null && wheel.position in selected -> BorderStroke(3.dp, MaterialTheme.colorScheme.primary)
            badge.isEmpty() -> BorderStroke(1.5.dp, MaterialTheme.colorScheme.outline)
            else -> BorderStroke(1.5.dp, c.accent)
        },
        modifier = Modifier
            .size(WHEEL_W, WHEEL_H)
            .semantics {
                contentDescription = "Roue ${wheel.position}"
                if (selected != null) this.selected = wheel.position in selected
            },
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(badge, style = MaterialTheme.typography.labelMedium, maxLines = 1)
        }
    }
}

/** Tableau « Pneus montés » ou « Pneus démontés » : un appui sur une ligne la modifie. */
@Composable
private fun TyreList(i: Intervention, table: TyreTable, onEdit: (Int) -> Unit) {
    val c = Vip.colors
    val rows = PlTyres.filledRows(i, table)
    SubHeader("${table.title} (${rows.size}/${PlTemplate.TYRE_ROWS})")
    if (rows.isEmpty()) {
        Text("Aucun", style = MaterialTheme.typography.bodyMedium, color = c.muted)
        return
    }
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        rows.forEach { row ->
            val info = PlTyres.info(i, table, row)
            val position = PlTyres.position(i, table, row)
            Surface(
                onClick = { onEdit(row) },
                shape = MaterialTheme.shapes.small,
                color = MaterialTheme.colorScheme.background,
                border = BorderStroke(1.dp, c.cardBorder),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Row(Modifier.padding(horizontal = 12.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        position.ifBlank { "—" },
                        style = MaterialTheme.typography.labelLarge,
                        modifier = Modifier.width(96.dp),
                    )
                    Text(
                        listOfNotNull(
                            info["dimensions"]?.takeIf { it.isNotBlank() },
                            info["marque"]?.takeIf { it.isNotBlank() },
                            info["matricule"]?.takeIf { it.isNotBlank() }?.let { "mat. $it" },
                            info["usure"]?.takeIf { it.isNotBlank() }?.let { "usure $it mm" },
                        ).joinToString("  ·  "),
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }
    }
}

// ---------------------------------------------------------------- saisie d'une roue

/** Pneu monté et pneu démonté d'une roue (ou d'un pneu hors schéma, ou de plusieurs roues à la fois). */
@Composable
private fun TyreDialog(form: Form, request: TyreRequest, onDismiss: () -> Unit, onSaved: () -> Unit) {
    val vm = form.vm
    val i = form.i
    val c = Vip.colors
    val group = request.group
    var position by remember(request) { mutableStateOf(request.position) }
    val mounted = remember(request) {
        mutableStateMapOf<String, String>().apply { request.mountedRow?.let { putAll(PlTyres.info(i, TyreTable.MONTES, it)) } }
    }
    val dismounted = remember(request) {
        mutableStateMapOf<String, String>().apply { request.dismountedRow?.let { putAll(PlTyres.info(i, TyreTable.DEMONTES, it)) } }
    }
    val wheel = PlTemplate.wheel(request.position)
    val existing = group.isNotEmpty() || request.mountedRow != null || request.dismountedRow != null ||
        (wheel != null && PlTyres.isChecked(i, wheel.position))
    // Dernier pneu saisi sur une autre roue
    val previousMounted = if (group.isEmpty()) PlTyres.previous(i, TyreTable.MONTES, request.position) else null
    val previousDismounted = if (group.isEmpty()) PlTyres.previous(i, TyreTable.DEMONTES, request.position) else null

    fun save() {
        var full = false
        vm.update(i.id) { cur ->
            val next = if (group.isNotEmpty()) {
                PlTyres.saveMany(cur, group, mounted.toMap(), dismounted.toMap())
            } else {
                PlTyres.save(
                    cur, position, request.position.takeIf { it.isNotBlank() },
                    mounted.toMap(), request.mountedRow, dismounted.toMap(), request.dismountedRow,
                )
            }
            next ?: cur.also { full = true }
        }
        if (full) vm.message("Tableau plein : ${PlTemplate.TYRE_ROWS} pneus au plus") else onSaved()
    }

    fun remove() {
        vm.update(i.id) { cur ->
            when {
                group.isNotEmpty() -> group.fold(cur) { acc, p -> PlTyres.remove(acc, p) }
                request.position.isNotBlank() -> PlTyres.remove(cur, request.position)
                else -> {
                    var next = cur
                    request.dismountedRow?.let { next = PlTyres.clear(next, TyreTable.DEMONTES, it) }
                    request.mountedRow?.let { next = PlTyres.clear(next, TyreTable.MONTES, it) }
                    next
                }
            }
        }
        onSaved()
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = c.card,
        title = {
            Text(
                when {
                    group.size > 1 -> "${group.size} roues"
                    group.size == 1 -> "Roue ${group[0]}"
                    wheel != null -> "Roue ${wheel.position}"
                    else -> "Pneu"
                },
            )
        },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                if (group.isNotEmpty()) {
                    Text(group.joinToString("  ·  "), style = MaterialTheme.typography.titleSmall)
                    Text(
                        "Même pneu pour toutes ces roues. Les cases laissées vides ne changent pas ce qui est déjà saisi ; " +
                            "les matricules se saisissent roue par roue.",
                        style = MaterialTheme.typography.bodySmall,
                        color = c.muted,
                    )
                } else {
                    VipField(
                        label = "Position",
                        value = position,
                        onValueChange = { position = it },
                        placeholder = "ex. 2 AR D ext",
                        capitalization = KeyboardCapitalization.Characters,
                    )
                    if (previousMounted != null || previousDismounted != null) {
                        val summary = listOfNotNull(previousMounted ?: previousDismounted).flatMap { listOf(it["dimensions"], it["marque"]) }
                            .filterNot { it.isNullOrBlank() }.joinToString(" ")
                        VipButton(
                            "Même pneu que la roue précédente" + (if (summary.isNotEmpty()) " ($summary)" else ""),
                            {
                                for (col in listOf("dimensions", "marque")) {
                                    previousMounted?.get(col)?.takeIf { it.isNotBlank() }?.let { mounted[col] = it }
                                    previousDismounted?.get(col)?.takeIf { it.isNotBlank() }?.let { dismounted[col] = it }
                                }
                            },
                            icon = Icons.Filled.ContentCopy,
                            tone = Tone.GHOST,
                            compact = true,
                        )
                    }
                }
                TyreFields(form, "Pneu monté", mounted, TyreTable.MONTES, withMatricule = group.isEmpty())
                TyreFields(form, "Pneu démonté", dismounted, TyreTable.DEMONTES, withMatricule = group.isEmpty()) {
                    TextButton(onClick = {
                        for (col in listOf("dimensions", "marque")) mounted[col]?.takeIf { it.isNotBlank() }?.let { dismounted[col] = it }
                    }) {
                        Icon(Icons.Filled.ContentCopy, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Même dimension et marque")
                    }
                }
                Text(
                    "Sans pneu monté ni démonté, la roue est seulement cochée sur la fiche (réparation, contrôle…).",
                    style = MaterialTheme.typography.bodySmall,
                    color = c.muted,
                )
            }
        },
        confirmButton = { VipButton("Valider", ::save, compact = true) },
        dismissButton = {
            Row {
                if (existing) TextButton(onClick = ::remove) { Text("Retirer", color = c.danger) }
                TextButton(onClick = onDismiss) { Text("Annuler") }
            }
        },
    )
}

/** Usure proposée en un appui (mm). */
private val USURES = (1..20).map { it.toString() }

@Composable
private fun TyreFields(
    form: Form,
    title: String,
    values: SnapshotStateMap<String, String>,
    table: TyreTable,
    withMatricule: Boolean = true,
    action: (@Composable () -> Unit)? = null,
) {
    SubHeader(title, trailing = action)
    // Même historique que les autres champs « dimensions » et « marque » des fiches
    val dimsKey = PlKeys.tyre(table, 1, "dimensions")
    val brandKey = PlKeys.tyre(table, 1, "marque")
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        DimensionField(
            "Dimensions", values["dimensions"].orEmpty(), { values["dimensions"] = it },
            Suggestions.top(form.suggestions, dimsKey, max = 8), Modifier.weight(1f),
        )
        VipField(
            "Marque", values["marque"].orEmpty(), { values["marque"] = it }, Modifier.weight(1f),
            suggestions = Suggestions.filter(form.suggestions, brandKey, values["marque"].orEmpty()),
            capitalization = KeyboardCapitalization.Words,
        )
    }
    if (values["dimensions"].isNullOrBlank()) {
        QuickPicks("Dimensions", Suggestions.top(form.suggestions, dimsKey), "", { values["dimensions"] = it })
    }
    if (values["marque"].isNullOrBlank()) {
        QuickPicks("Marque", Suggestions.top(form.suggestions, brandKey), "", { values["marque"] = it })
    }
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        if (withMatricule) {
            VipField(
                "Matricule", values["matricule"].orEmpty(), { values["matricule"] = it }, Modifier.weight(1f),
                capitalization = KeyboardCapitalization.Characters,
            )
        }
        VipField(
            "Usure", values["usure"].orEmpty(), { values["usure"] = it }, Modifier.weight(1f),
            keyboardType = KeyboardType.Decimal, suffix = "mm",
        )
    }
    QuickPicks("Usure (mm)", USURES, values["usure"].orEmpty(), { values["usure"] = it })
}
