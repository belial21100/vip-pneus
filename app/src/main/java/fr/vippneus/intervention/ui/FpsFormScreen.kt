package fr.vippneus.intervention.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Assignment
import androidx.compose.material.icons.automirrored.filled.Notes
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AttachFile
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Handyman
import androidx.compose.material.icons.filled.PrecisionManufacturing
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.TireRepair
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import fr.vippneus.intervention.data.Completion
import fr.vippneus.intervention.data.Intervention
import fr.vippneus.intervention.data.InterventionType
import fr.vippneus.intervention.data.Naming
import fr.vippneus.intervention.data.Pneus
import fr.vippneus.intervention.data.Suggestions
import fr.vippneus.intervention.data.Todo
import fr.vippneus.intervention.data.displayStatus
import fr.vippneus.intervention.pdf.FpsTemplate
import fr.vippneus.intervention.pdf.FpsTemplate.K
import kotlinx.coroutines.launch

/** Accès simplifié aux champs du formulaire. */
internal class Form(val vm: AppViewModel, val i: Intervention, val suggestions: Map<String, List<String>>, val nav: FormNav) {
    /** Champs signalés « À compléter ». */
    val missing = Completion.missingFields(i)

    fun value(key: String) = i.value(key)
    fun set(key: String, v: String) = vm.setValue(i.id, key, v)
}

@Composable
internal fun Form.Field(
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
        missing = key in missing,
    )
}

/** Dimensions : pavé des dimensions (et les plus utilisées) au lieu du clavier. */
@Composable
internal fun Form.Dimension(key: String, label: String, modifier: Modifier = Modifier) {
    DimensionField(
        label = label,
        value = value(key),
        onValueChange = { set(key, it) },
        frequent = Suggestions.top(suggestions, key, max = 8),
        modifier = modifier,
        auto = i.isAuto(key),
        missing = key in missing,
        focusRequester = nav.focus(key),
    )
}

/** Boutons des valeurs les plus utilisées, tant que le champ est vide. */
@Composable
internal fun Form.Picks(key: String, label: String) {
    if (value(key).isBlank()) QuickPicks(label, Suggestions.top(suggestions, key), "", { set(key, it) })
}

/** Quantité avec − et +. */
@Composable
internal fun Form.Stepper(key: String, label: String, modifier: Modifier = Modifier) {
    LabeledStepper(label, value(key), { set(key, it) }, modifier, auto = i.isAuto(key), focusRequester = nav.focus(key))
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
    FpsTemplate.isPneuKey(key) -> "pneus"
    key.startsWith("prest.") -> "prestations"
    key.startsWith("serrage") -> "serrage"
    key == Completion.SIGNATURE -> "signature"
    else -> "client"
}

/** Sommaire d'une fiche et section où se trouve chaque élément à compléter. */
internal class FormOutline(val sections: List<Pair<String, String>>, val sectionOf: (String) -> String)

private val FPS_OUTLINE = FormOutline(SECTIONS, ::sectionOf)

/** Fiche intégrée (presse mobile ou poids lourds) : formulaire à gauche, aperçu de la fiche à droite. */
@Composable
fun SheetFormScreen(vm: AppViewModel, id: String) {
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
    var tab by rememberSaveable { mutableIntStateOf(0) }
    var signing by remember { mutableStateOf(false) }
    var renaming by remember { mutableStateOf(false) }
    val form = Form(vm, i, suggestions, nav)
    val outline = if (i.type == InterventionType.PL) PL_OUTLINE else FPS_OUTLINE

    /** Emmène au champ manquant (ou ouvre la signature). */
    fun jump(t: Todo) {
        tab = 0
        nav.go(outline.sectionOf(t.key), t.key)
        if (t.key == Completion.SIGNATURE) signing = true
    }
    val send = rememberSendAction(vm, i, todos, ::jump)

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
                    Naming.kindLabel(i),
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
                    val modifier = Modifier.weight(if (wide) 0.56f else 1f)
                    if (i.type == InterventionType.PL) {
                        PlForm(form, todos, ::jump, onSign = { signing = true }, onResend = send, modifier)
                    } else {
                        FpsForm(form, todos, ::jump, onSign = { signing = true }, onResend = send, modifier)
                    }
                }
                if (wide || tab == 1) {
                    PagePreviewPane(vm, i, Modifier.weight(if (wide) 0.44f else 1f))
                }
            }
            SendBar(
                fileName = Naming.fileName(i, settings.initiales),
                missing = todos.filterNot { it.done }.map { it.label },
                onRename = { renaming = true },
                onPreview = { scope.launch { if (vm.generate(id) != null) vm.navigate(Screen.Viewer(id)) } },
                onSend = send,
                onMissing = { todos.firstOrNull { !it.done }?.let(::jump) },
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

/**
 * Colonne de saisie d'une fiche : sommaire, bandeaux, cadre « À compléter », rubriques de la fiche
 * ([content], qui reçoit la pastille « À compléter / Complet » de chaque section), signature et contenu du PDF.
 */
@Composable
internal fun SheetFormColumn(
    form: Form,
    outline: FormOutline,
    todos: List<Todo>,
    onJump: (Todo) -> Unit,
    onSign: () -> Unit,
    onResend: () -> Unit,
    modifier: Modifier,
    banner: @Composable ColumnScope.() -> Unit = {},
    content: @Composable ColumnScope.(status: (String) -> (@Composable RowScope.() -> Unit)?) -> Unit,
) {
    val vm = form.vm
    val i = form.i
    val nav = form.nav
    val incomplete = todos.filterNot { it.done }.map { outline.sectionOf(it.key) }.toSet()
    // Pastille « À compléter » / « Complet » des sections qui ont des éléments attendus
    val expected = todos.map { outline.sectionOf(it.key) }.toSet()
    fun status(section: String): (@Composable RowScope.() -> Unit)? =
        if (section in expected) {
            { SectionStatus(section !in incomplete) }
        } else null
    Column(modifier.fillMaxHeight()) {
        SectionNavRow(outline.sections, incomplete) { nav.go(it) }
        Column(
            Modifier
                .weight(1f)
                .fillMaxWidth()
                .verticalScroll(nav.scroll)
                .imePadding()
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            SentIncompleteBanner(i, todos, onResend)
            i.recognized?.let { RecognizedBanner(it) }
            banner()
            TodoPanel(todos, onJump, onSend = sendFromPanel(i, onResend), sendLabel = sendLabel(i))

            content(::status)

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
private fun FpsForm(form: Form, todos: List<Todo>, onJump: (Todo) -> Unit, onSign: () -> Unit, onResend: () -> Unit, modifier: Modifier) {
    val vm = form.vm
    val i = form.i
    val nav = form.nav
    // Bon de commande du client (Mac2, Conti…) : joint à la fiche, il la pré-remplit
    val pickOrder = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) vm.addAttachment(i.id, uri)
    }
    SheetFormColumn(
        form, FPS_OUTLINE, todos, onJump, onSign, onResend, modifier,
        banner = {
            if (i.attachments.isEmpty()) {
                InfoBanner(
                    icon = Icons.Filled.AttachFile,
                    title = "Joindre le bon de commande du client",
                    text = "Mac2, Conti… : ses informations remplissent la fiche, et il la suit dans le PDF.",
                    action = {
                        VipButton(
                            "Joindre",
                            { pickOrder.launch(arrayOf("application/pdf", "image/*")) },
                            icon = Icons.Filled.AttachFile,
                            tone = Tone.DARK,
                            compact = true,
                        )
                    },
                )
            }
        },
    ) { status ->
        SectionCard("Commande et client", nav.anchor("client"), icon = Icons.AutoMirrored.Filled.Assignment, trailing = status("client")) {
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

        SectionCard("Matériel", nav.anchor("materiel"), icon = Icons.Filled.PrecisionManufacturing, trailing = status("materiel")) {
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

        SectionCard(
            "Pneus montés", nav.anchor("pneus"), icon = Icons.Filled.TireRepair,
            subtitle = "Une ligne par essieu ; les roues intérieures sur une ligne à part",
            trailing = status("pneus"),
        ) {
            PneuRows(form)
        }

        SectionCard(
            "Prestations", nav.anchor("prestations"), icon = Icons.Filled.Handyman,
            subtitle = "Quantités par taille de jante : un appui ajoute 1, appui long pour taper", trailing = status("prestations"),
        ) {
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
            trailing = status("serrage"),
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
    }
}

/** Tableau « Fournitures » : AV, AR, puis autres essieux et roues intérieures, ajoutés à la demande. */
@Composable
private fun PneuRows(form: Form) {
    val vm = form.vm
    val i = form.i
    val extra = FpsTemplate.extraPneuRows
    // Lignes ouvertes avec « Ajouter » et pas encore remplies
    var opened by rememberSaveable(i.id) { mutableIntStateOf(0) }
    val shown = maxOf(Pneus.extraCount(i), opened).coerceAtMost(extra.size)

    PneuBlock(form, "av", "Pneus avant (AV) fournis")
    SubHeader("Arrière (AR)") {
        CopyButton("Recopier l'avant") { vm.update(i.id) { Pneus.copy(it, "av", "ar") } }
    }
    PneuBlock(form, "ar", "Pneus arrière (AR) fournis")
    extra.take(shown).forEach { row ->
        val above = FpsTemplate.pneuRows[FpsTemplate.pneuRows.indexOf(row) - 1].key
        SubHeader(form.value(K.essieu(row.key)).ifBlank { "Autre essieu" }) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                CopyButton("Recopier la ligne du dessus") { vm.update(i.id) { Pneus.copy(it, above, row.key) } }
                VipButton(
                    "Retirer",
                    {
                        opened = shown - 1
                        vm.update(i.id) { Pneus.remove(it, row.key) }
                    },
                    icon = Icons.Filled.Close,
                    tone = Tone.GHOST,
                    compact = true,
                )
            }
        }
        form.Field(
            K.essieu(row.key), "Essieu / roues", Modifier.width(360.dp),
            placeholder = "ex. Essieu 3, AR int.",
            caps = KeyboardCapitalization.Sentences,
        )
        PneuBlock(form, row.key, "Pneus fournis")
    }
    if (shown < extra.size) {
        VipButton(
            "Ajouter un essieu ou des roues intérieures",
            { opened = shown + 1 },
            icon = Icons.Filled.Add,
            tone = Tone.GHOST,
        )
    }
}

@Composable
private fun CopyButton(text: String, onClick: () -> Unit) =
    VipButton(text, onClick, icon = Icons.Filled.ContentCopy, tone = Tone.GHOST, compact = true)

@Composable
private fun PneuBlock(form: Form, row: String, title: String) {
    val key = K.fourni(row)
    YesNo(title, form.value(key).ifEmpty { null }, { form.set(key, it.orEmpty()) })
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        form.Dimension(K.pneu(row, "dimensions"), "Dimensions", Modifier.weight(1f))
        form.Field(K.pneu(row, "marque"), "Marque", Modifier.weight(1f), caps = KeyboardCapitalization.Words)
        form.Field(K.pneu(row, "profil"), "Profil", Modifier.weight(1f), caps = KeyboardCapitalization.Words)
    }
    form.Picks(K.pneu(row, "dimensions"), "Dimensions")
    form.Picks(K.pneu(row, "marque"), "Marque")
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.Bottom) {
        form.Field(K.pneu(row, "type"), "Type", Modifier.weight(1f), caps = KeyboardCapitalization.Words)
        form.Stepper(K.pneu(row, "quantite"), "Quantité", Modifier.weight(1f))
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
                FpsTemplate.prestationColumns.forEach { (col, colLabel, _) ->
                    val key = K.prestation(row, col)
                    CounterCell(
                        value = form.value(key),
                        onChange = { form.set(key, it) },
                        modifier = Modifier.weight(1f),
                        description = "$rowLabel – $colLabel",
                        auto = form.i.isAuto(key),
                        focusRequester = form.nav.focus(key),
                    )
                }
            }
        }
    }
}
