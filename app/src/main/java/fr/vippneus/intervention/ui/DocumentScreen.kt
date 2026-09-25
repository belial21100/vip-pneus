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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.EditNote
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.TouchApp
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import fr.vippneus.intervention.data.Completion
import fr.vippneus.intervention.data.DocKeys
import fr.vippneus.intervention.data.Intervention
import fr.vippneus.intervention.data.Naming
import fr.vippneus.intervention.data.PlacedField
import fr.vippneus.intervention.data.Suggestions
import fr.vippneus.intervention.data.Todo
import fr.vippneus.intervention.data.displayStatus
import kotlinx.coroutines.launch
import java.time.LocalTime

/**
 * Document du client complété directement (feuille de tâche Mastra, ou document quelconque) :
 * saisie à gauche, page 1 modifiable à droite.
 */
@Composable
fun DocumentScreen(vm: AppViewModel, id: String) {
    val list by vm.interventions.collectAsStateWithLifecycle()
    val i = list.firstOrNull { it.id == id }
    if (i == null) {
        LaunchedEffect(Unit) { if (vm.backStack.lastOrNull() == Screen.Document(id)) vm.back() }
        return
    }
    val settings by vm.settings.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    val nav = rememberFormNav()
    val editor = remember(id) { EditorState() }
    val todos = remember(i) { Completion.todos(i) }
    val send = rememberSendAction(vm, i, todos)
    var tab by rememberSaveable { mutableIntStateOf(0) }
    var signing by remember { mutableStateOf(false) }
    var renaming by remember { mutableStateOf(false) }
    var infosOpen by rememberSaveable { mutableStateOf(i.value(DocKeys.CLIENT).isBlank()) }
    val signerKey = i.template?.fields?.firstOrNull { it.key.endsWith("recuPar") }?.key

    fun jump(t: Todo) {
        when (t.key) {
            Completion.SIGNATURE -> {
                tab = 0
                nav.go("signature")
                signing = true
            }
            Completion.PAGE -> {
                tab = 1
                editor.selected = null
                editor.tool = Tool.TEXT
            }
            DocKeys.CLIENT -> {
                tab = 0
                infosOpen = true
                nav.go("infos", t.key)
            }
            signerKey -> {
                tab = 0
                nav.go("signature", t.key)
            }
            else -> {
                tab = 0
                nav.go("feuille", t.key)
            }
        }
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
                    i.recognized ?: "Document client",
                    Naming.reference(i).takeIf { it.isNotEmpty() },
                    Naming.formatShort(Naming.interventionDate(i)),
                ).joinToString("  ·  "),
                onBack = { vm.back() },
                status = i.displayStatus(),
            )
            if (!wide) ScreenTabs(listOf("Saisie", "Page 1"), tab) { tab = it }
            Row(
                Modifier
                    .weight(1f)
                    .fillMaxWidth(),
            ) {
                if (wide || tab == 0) {
                    DocumentForm(
                        vm, i, nav, todos, ::jump,
                        onSign = { signing = true },
                        infosOpen = infosOpen,
                        onToggleInfos = { infosOpen = !infosOpen },
                        signerKey = signerKey,
                        modifier = Modifier.weight(if (wide) 0.44f else 1f),
                    )
                }
                if (wide || tab == 1) {
                    PageEditor(vm, i, editor, Modifier.weight(if (wide) 0.56f else 1f))
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
            nameLabel = if (signerKey != null) "Reçu par (nom du client)" else null,
            initialName = signerKey?.let { i.value(it) }.orEmpty(),
            onDismiss = { signing = false },
            onDone = { sig, name ->
                signing = false
                vm.update(id) { cur ->
                    val values = when {
                        signerKey == null || name == null -> cur.values
                        name.isBlank() -> cur.values - signerKey
                        else -> cur.values + (signerKey to name)
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
private fun DocumentForm(
    vm: AppViewModel,
    i: Intervention,
    nav: FormNav,
    todos: List<Todo>,
    onJump: (Todo) -> Unit,
    onSign: () -> Unit,
    infosOpen: Boolean,
    onToggleInfos: () -> Unit,
    signerKey: String?,
    modifier: Modifier,
) {
    val suggestions by vm.suggestions.collectAsStateWithLifecycle()
    val t = i.template

    @Composable
    fun field(
        key: String,
        label: String,
        modifier: Modifier = Modifier,
        keyboard: KeyboardType = KeyboardType.Text,
        caps: KeyboardCapitalization = KeyboardCapitalization.Sentences,
        trailing: (@Composable () -> Unit)? = null,
    ) {
        val v = i.value(key)
        VipField(
            label = label,
            value = v,
            onValueChange = { vm.setValue(i.id, key, it) },
            modifier = modifier,
            auto = i.isAuto(key),
            suggestions = Suggestions.filter(suggestions, key, v),
            keyboardType = keyboard,
            capitalization = caps,
            trailing = trailing,
            focusRequester = nav.focus(key),
        )
    }

    Column(
        modifier
            .fillMaxHeight()
            .verticalScroll(nav.scroll)
            .imePadding()
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        i.recognized?.let {
            RecognizedBanner(
                it,
                text = if (t != null) "Les valeurs saisies s'écrivent directement à leur place sur le document ; l'original suit en page 2."
                else null,
            )
        }
        TodoPanel(todos, onJump)

        if (t != null) {
            SectionCard(
                t.name, nav.anchor("feuille"), icon = Icons.Filled.EditNote,
                subtitle = "Chaque valeur s'inscrit à son emplacement sur la feuille",
            ) {
                TemplateFields(vm, i, t.fields.filter { it.key != signerKey }, nav)
            }
            if (t.signature != null) {
                SignatureCard(
                    vm, i,
                    nameKey = signerKey,
                    nameLabel = "Reçu par (nom du client)",
                    onSign = onSign,
                    modifier = nav.anchor("signature"),
                    focus = signerKey?.let { nav.focus(it) },
                )
            }
        } else {
            InfoBanner(
                icon = Icons.Filled.TouchApp,
                title = "Écrivez directement sur la page",
                text = "Choisissez Texte, Date, Croix ou Signature au-dessus de la page, puis touchez l'endroit voulu. " +
                    "Glissez un élément pour le déplacer.",
                background = Vip.colors.infoSoft,
                content = Vip.colors.info,
            )
        }

        val summary = listOf(i.value(DocKeys.CLIENT), i.value(DocKeys.SITE), i.value(DocKeys.REFERENCE))
            .filter { it.isNotBlank() }.joinToString("  ·  ")
        CollapsibleCard(
            "Informations du bon",
            summary = summary.ifEmpty { "Client, site, référence : servent au nom du fichier" },
            expanded = infosOpen,
            onToggle = onToggleInfos,
            icon = Icons.Filled.Info,
            modifier = nav.anchor("infos"),
        ) {
            Text(
                "Servent au nom du fichier PDF et à la liste des bons (elles ne sont pas écrites sur le document).",
                style = MaterialTheme.typography.bodySmall,
                color = Vip.colors.muted,
            )
            field(DocKeys.CLIENT, "Client / donneur d'ordre", caps = KeyboardCapitalization.Characters)
            field(DocKeys.SITE, "Site / client utilisateur")
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                field(DocKeys.CP, "Code postal", Modifier.weight(1f), keyboard = KeyboardType.Number)
                field(DocKeys.VILLE, "Ville", Modifier.weight(2f))
            }
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                field(DocKeys.REFERENCE, "N° de commande / référence", Modifier.weight(1.4f))
                field(
                    DocKeys.DATE, "Date", Modifier.weight(1f),
                    trailing = { DatePickerIcon(i.value(DocKeys.DATE)) { vm.setValue(i.id, DocKeys.DATE, it) } },
                )
            }
        }

        PdfContentCard(vm, i, nav.anchor("pdf"))

        Spacer(Modifier.height(32.dp))
    }
}

/** Champs de la feuille du client, deux par ligne quand ils sont courts. */
@Composable
private fun TemplateFields(vm: AppViewModel, i: Intervention, fields: List<PlacedField>, nav: FormNav) {
    fun wide(f: PlacedField) = f.maxLines > 1 || f.right - f.left > 250f
    val rows = mutableListOf<List<PlacedField>>()
    var pending: PlacedField? = null
    for (f in fields) {
        val p = pending
        when {
            wide(f) -> {
                if (p != null) rows += listOf(p)
                pending = null
                rows += listOf(f)
            }
            p == null -> pending = f
            else -> {
                rows += listOf(p, f)
                pending = null
            }
        }
    }
    pending?.let { rows += listOf(it) }

    rows.forEach { row ->
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            row.forEach { f -> TemplateField(vm, i, f, nav, Modifier.weight(1f)) }
            if (row.size == 1 && !wide(row[0])) Spacer(Modifier.weight(1f))
        }
    }
}

@Composable
private fun TemplateField(vm: AppViewModel, i: Intervention, f: PlacedField, nav: FormNav, modifier: Modifier) {
    val v = i.value(f.key)
    val k = f.key.substringAfterLast('.')
    VipField(
        label = f.label,
        value = v,
        onValueChange = { vm.setValue(i.id, f.key, it) },
        modifier = modifier,
        auto = i.isAuto(f.key),
        suggestions = templateChips(f.key, v, i),
        keyboardType = if (f.numeric) KeyboardType.Number else KeyboardType.Text,
        // L'unité est ajoutée sur le document quand la valeur n'est qu'un nombre
        suffix = f.unit.takeIf { it.isNotEmpty() && (v.isBlank() || v.trim().matches(Regex("[0-9 .,]+"))) },
        supporting = i.hints[f.key],
        trailing = if (k.startsWith("date")) {
            { DatePickerIcon(v) { vm.setValue(i.id, f.key, it) } }
        } else null,
        focusRequester = nav.focus(f.key),
    )
}

/** Propositions rapides pour les champs d'une feuille client (heure actuelle, date du jour, durée calculée). */
private fun templateChips(key: String, value: String, i: Intervention): List<String> {
    val k = key.substringAfterLast('.')
    val now = LocalTime.now()
    val out = mutableListOf<String>()
    when {
        k.startsWith("heure") -> out += "%02d:%02d".format(now.hour, now.minute)
        k.startsWith("date") -> out += Naming.today()
        k == "duree" -> {
            val a = parseTime(i.value("if.heureArrivee"))
            val b = parseTime(i.value("if.heureDepart"))
            if (a != null && b != null) {
                val minutes = ((b - a) + 24 * 60) % (24 * 60)
                out += if (minutes >= 60) "${minutes / 60} h ${"%02d".format(minutes % 60)}" else "$minutes min"
            }
        }
    }
    return out.filter { it != value }
}

/** « 8:05 », « 08h05 », « 8h » -> minutes depuis minuit. */
private fun parseTime(s: String): Int? {
    val m = Regex("""(\d{1,2})\s*[:hH]\s*(\d{2})?""").find(s) ?: return null
    val h = m.groupValues[1].toInt()
    val min = m.groupValues[2].ifEmpty { "0" }.toInt()
    return if (h in 0..23 && min in 0..59) h * 60 + min else null
}
