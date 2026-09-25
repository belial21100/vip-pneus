package fr.vippneus.intervention.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Assignment
import androidx.compose.material.icons.automirrored.filled.Notes
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Handyman
import androidx.compose.material.icons.filled.PrecisionManufacturing
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.TireRepair
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
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
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import fr.vippneus.intervention.data.Completion
import fr.vippneus.intervention.data.Intervention
import fr.vippneus.intervention.data.Naming
import fr.vippneus.intervention.data.Suggestions
import fr.vippneus.intervention.data.Todo
import fr.vippneus.intervention.data.displayStatus
import fr.vippneus.intervention.pdf.FpsTemplate
import fr.vippneus.intervention.pdf.FpsTemplate.K
import kotlinx.coroutines.launch

/** Accès simplifié aux champs du formulaire. */
private class Form(val vm: AppViewModel, val i: Intervention, val suggestions: Map<String, List<String>>, val nav: FormNav) {
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
    suffix: String? = null,
    trailing: (@Composable () -> Unit)? = null,
) {
    val v = value(key)
    VipField(
        label = label,
        value = v,
        onValueChange = { set(key, it) },
        modifier = modifier,
        auto = i.isAuto(key),
        suggestions = Suggestions.filter(suggestions, key, v),
        keyboardType = keyboard,
        capitalization = caps,
        singleLine = singleLine,
        minLines = minLines,
        placeholder = placeholder,
        suffix = suffix,
        trailing = trailing,
        focusRequester = nav.focus(key),
    )
}

/** Sommaire de la fiche (identifiant de section -> libellé). */
private val SECTIONS = listOf(
    "client" to "Client",
    "materiel" to "Matériel",
    "pneus" to "Pneus",
    "prestations" to "Prestations",
    "serrage" to "Serrage",
    "jantes" to "Jantes",
    "signature" to "Signature",
    "pdf" to "PDF",
)

/** Section de la fiche où se trouve un élément à compléter. */
private fun sectionOf(key: String): String = when {
    key == K.MARQUE || key == K.HORAMETRE -> "materiel"
    key.startsWith("av.") || key.startsWith("ar.") -> "pneus"
    key.startsWith("prest.") -> "prestations"
    key.startsWith("serrage") -> "serrage"
    key == Completion.SIGNATURE -> "signature"
    else -> "client"
}

/** Fiche d'intervention presse mobile : formulaire à gauche, aperçu de la fiche à droite. */
@Composable
fun FpsFormScreen(vm: AppViewModel, id: String) {
    val list by vm.interventions.collectAsStateWithLifecycle()
    val i = list.firstOrNull { it.id == id }
    if (i == null) {
        LaunchedEffect(Unit) { if (vm.backStack.lastOrNull() == Screen.Fps(id)) vm.back() }
        return
    }
    val settings by vm.settings.collectAsStateWithLifecycle()
    val suggestions by vm.suggestions.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    val nav = rememberFormNav()
    val todos = remember(i) { Completion.todos(i) }
    val send = rememberSendAction(vm, i, todos)
    var tab by rememberSaveable { mutableIntStateOf(0) }
    var signing by remember { mutableStateOf(false) }
    var renaming by remember { mutableStateOf(false) }
    val form = Form(vm, i, suggestions, nav)

    fun jump(t: Todo) {
        tab = 0
        nav.go(sectionOf(t.key), t.key)
        if (t.key == Completion.SIGNATURE) signing = true
    }

    BoxWithConstraints(
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
    ) {
        val wide = maxWidth >= 900.dp
        Column(Modifier.fillMaxSize()) {
            VipTopBar(
                title = Naming.title(i),
                subtitle = listOfNotNull(
                    "Fiche presse mobile",
                    Naming.reference(i).takeIf { it.isNotEmpty() }?.let { "N° $it" },
                    Naming.formatShort(Naming.interventionDate(i)),
                ).joinToString("  ·  "),
                onBack = { vm.back() },
                status = i.displayStatus(),
            ) {
                if (!wide) {
                    VipButton("Ajuster la page", { vm.navigate(Screen.Editor(id)) }, icon = Icons.Filled.Tune, tone = Tone.CHROME, compact = true)
                }
            }
            if (!wide) ScreenTabs(listOf("Saisie", "Aperçu"), tab) { tab = it }
            Row(
                Modifier
                    .weight(1f)
                    .fillMaxWidth(),
            ) {
                if (wide || tab == 0) {
                    FpsForm(form, todos, ::jump, onSign = { signing = true }, Modifier.weight(if (wide) 0.56f else 1f))
                }
                if (wide || tab == 1) {
                    PagePreviewPane(vm, i, Modifier.weight(if (wide) 0.44f else 1f))
                }
            }
            SendBar(
                fileName = Naming.fileName(i, settings.initiales),
                missing = todos.count { !it.done },
                onRename = { renaming = true },
                onPreview = { scope.launch { if (vm.generate(id) != null) vm.navigate(Screen.Viewer(id)) } },
                onSend = send,
                compact = !wide,
            )
        }
    }

    if (signing) {
        SignatureDialog(
            title = "Signature du client",
            nameLabel = "Nom du signataire / mention",
            initialName = i.value(K.SIGNATAIRE),
            onDismiss = { signing = false },
            onDone = { sig, name ->
                signing = false
                vm.update(id) { cur ->
                    val values = when {
                        name == null -> cur.values
                        name.isBlank() -> cur.values - K.SIGNATAIRE
                        else -> cur.values + (K.SIGNATAIRE to name)
                    }
                    cur.copy(signature = sig ?: cur.signature, values = values)
                }
            },
        )
    }
    if (renaming) {
        FileNameDialog(
            auto = Naming.defaultFileName(i, settings.initiales),
            current = i.fileName,
            onDismiss = { renaming = false },
            onConfirm = { v ->
                renaming = false
                vm.update(id) { it.copy(fileName = v) }
            },
        )
    }
}

@Composable
private fun FpsForm(form: Form, todos: List<Todo>, onJump: (Todo) -> Unit, onSign: () -> Unit, modifier: Modifier) {
    val vm = form.vm
    val i = form.i
    val nav = form.nav
    val incomplete = todos.filterNot { it.done }.map { sectionOf(it.key) }.toSet()
    Column(modifier.fillMaxHeight()) {
        SectionNavRow(SECTIONS, incomplete) { nav.go(it) }
        Column(
            Modifier
                .weight(1f)
                .fillMaxWidth()
                .verticalScroll(nav.scroll)
                .imePadding()
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            i.recognized?.let { RecognizedBanner(it) }
            TodoPanel(todos, onJump)

            SectionCard("Commande et client", nav.anchor("client"), icon = Icons.AutoMirrored.Filled.Assignment) {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    form.Field(K.CLIENT_MANDATAIRE, "Client mandataire", Modifier.weight(1.4f), caps = KeyboardCapitalization.Characters)
                    form.Field(K.NUMERO_COMMANDE, "N° de commande", Modifier.weight(1f), caps = KeyboardCapitalization.Characters)
                }
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    form.Field(K.MANDATAIRE_ADRESSE, "Adresse / Ville du mandataire", Modifier.weight(2f))
                    form.Field(K.MANDATAIRE_CP, "Code postal", Modifier.weight(1f), keyboard = KeyboardType.Number)
                }
                SubHeader("Intervention")
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    form.Field(K.MONTEUR, "Commercial / Monteur", Modifier.weight(1f))
                    form.Field(
                        K.DATE, "Date", Modifier.weight(1f),
                        trailing = { DatePickerIcon(i.value(K.DATE)) { form.set(K.DATE, it) } },
                    )
                }
                form.Field(K.CLIENT_UTILISATEUR, "Client utilisateur (site d'intervention)", singleLine = false)
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    form.Field(K.UTILISATEUR_ADRESSE, "Adresse / Ville", Modifier.weight(2f))
                    form.Field(K.UTILISATEUR_CP, "Code postal", Modifier.weight(1f), keyboard = KeyboardType.Number)
                }
            }

            SectionCard("Matériel", nav.anchor("materiel"), icon = Icons.Filled.PrecisionManufacturing) {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    form.Field(K.MARQUE, "Marque", Modifier.weight(1f), caps = KeyboardCapitalization.Words)
                    form.Field(K.TYPE, "Type", Modifier.weight(1f), caps = KeyboardCapitalization.Characters)
                }
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    form.Field(K.SERIE, "N° de série", Modifier.weight(1f), caps = KeyboardCapitalization.Characters)
                    form.Field(K.PARC, "N° de parc", Modifier.weight(1f), caps = KeyboardCapitalization.Characters)
                    form.Field(K.HORAMETRE, "Horamètre", Modifier.weight(1f), keyboard = KeyboardType.Number)
                }
            }

            SectionCard("Pneus fournis", nav.anchor("pneus"), icon = Icons.Filled.TireRepair) {
                PneuBlock(form, "av", "Pneus avant (AV) fournis")
                SubHeader("Arrière") {
                    VipButton(
                        "Recopier l'avant",
                        {
                            vm.update(i.id) { cur ->
                                var values = cur.values
                                FpsTemplate.pneuColumns.forEach { (col, _, _) ->
                                    val v = cur.value(K.pneu("av", col))
                                    values = if (v.isEmpty()) values - K.pneu("ar", col) else values + (K.pneu("ar", col) to v)
                                }
                                cur.value(K.AV_FOURNI).let { v -> values = if (v.isEmpty()) values - K.AR_FOURNI else values + (K.AR_FOURNI to v) }
                                cur.copy(values = values)
                            }
                        },
                        icon = Icons.Filled.ContentCopy,
                        tone = Tone.GHOST,
                        compact = true,
                    )
                }
                PneuBlock(form, "ar", "Pneus arrière (AR) fournis")
            }

            SectionCard("Prestations", nav.anchor("prestations"), icon = Icons.Filled.Handyman, subtitle = "Quantités par taille de jante") {
                PrestationsGrid(form)
                SubHeader("Déplacement")
                YesNo(
                    "Déplacement pour prestation < 4 pneus",
                    form.value(K.DEPLACEMENT).ifEmpty { null },
                    { form.set(K.DEPLACEMENT, it.orEmpty()) },
                )
                form.Field(K.KMS, "Nombre de km départ agence", Modifier.width(320.dp), keyboard = KeyboardType.Number)
            }

            SectionCard(
                "Serrage des roues", nav.anchor("serrage"), icon = Icons.Filled.Speed,
                subtitle = "Couple en Nm ; la remarque s'écrit à droite de « Nm »",
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    form.Field(K.SERRAGE_AV, "AV", Modifier.weight(1f), keyboard = KeyboardType.Number, suffix = "Nm")
                    form.Field(K.SERRAGE_AV_REMARQUE, "Remarque AV", Modifier.weight(2.4f))
                }
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    form.Field(K.SERRAGE_AR, "AR", Modifier.weight(1f), keyboard = KeyboardType.Number, suffix = "Nm")
                    form.Field(K.SERRAGE_AR_REMARQUE, "Remarque AR", Modifier.weight(2.4f), placeholder = "ex. Démonté et remonté par le client")
                }
            }

            SectionCard("État des jantes et observations", nav.anchor("jantes"), icon = Icons.AutoMirrored.Filled.Notes) {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    form.Field(K.JANTES_LUSTREES, "Jantes lustrées", Modifier.weight(1f))
                    form.Field(K.JANTES_FISSUREES, "Jantes fissurées", Modifier.weight(1f))
                }
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    form.Field(K.JANTES_HS, "Jantes HS", Modifier.weight(1f))
                    form.Field(K.JANTES_AUTRES, "Autres", Modifier.weight(1f))
                }
                form.Field(K.OBSERVATIONS, "Observations", singleLine = false, minLines = 2)
            }

            SignatureCard(
                vm, i,
                nameKey = K.SIGNATAIRE,
                nameLabel = "Nom du signataire / mention",
                namePlaceholder = "ex. Vu avec le client – Jérôme, technicien",
                onSign = onSign,
                modifier = nav.anchor("signature"),
                focus = nav.focus(K.SIGNATAIRE),
            )

            PdfContentCard(vm, i, nav.anchor("pdf"))

            Spacer(Modifier.height(32.dp))
        }
    }
}

@Composable
private fun PneuBlock(form: Form, essieu: String, title: String) {
    val key = if (essieu == "av") K.AV_FOURNI else K.AR_FOURNI
    YesNo(title, form.value(key).ifEmpty { null }, { form.set(key, it.orEmpty()) })
    val cols = FpsTemplate.pneuColumns
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        cols.take(3).forEach { (col, label, _) ->
            form.Field(
                K.pneu(essieu, col), label, Modifier.weight(1f),
                caps = if (col == "dimensions") KeyboardCapitalization.None else KeyboardCapitalization.Words,
            )
        }
    }
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        cols.drop(3).forEach { (col, label, _) ->
            form.Field(
                K.pneu(essieu, col), label, Modifier.weight(1f),
                keyboard = if (col == "quantite") KeyboardType.Number else KeyboardType.Text,
            )
        }
        Spacer(Modifier.weight(1f))
    }
}

@Composable
private fun PrestationsGrid(form: Form) {
    val labelWidth = 150.dp
    val c = Vip.colors
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Spacer(Modifier.width(labelWidth))
            FpsTemplate.prestationColumns.forEach { (_, label, _) ->
                Text(
                    label.replace(" pouces", "\""),
                    style = MaterialTheme.typography.labelLarge,
                    color = c.muted,
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
                        textStyle = MaterialTheme.typography.titleLarge.copy(textAlign = TextAlign.Center, fontSize = 20.sp),
                        keyboardOptions = KeyboardOptions(
                            keyboardType = if (col == "autres") KeyboardType.Text else KeyboardType.Number,
                            imeAction = ImeAction.Next,
                        ),
                        shape = MaterialTheme.shapes.small,
                        colors = vipFieldColors(form.i.isAuto(key)),
                        modifier = Modifier
                            .weight(1f)
                            .focusRequester(form.nav.focus(key)),
                    )
                }
            }
        }
    }
}
