package fr.vippneus.intervention.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Badge
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.EditNote
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.FactCheck
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material.icons.filled.Save
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import fr.vippneus.intervention.BuildConfigInfo
import fr.vippneus.intervention.data.Naming
import fr.vippneus.intervention.data.Settings
import fr.vippneus.intervention.data.SettingsRules
import java.util.Locale

// ------------------------------------------------------------------ saisie des réglages

/** Réglages en cours de saisie (première configuration ou écran Réglages). */
private class SettingsForm(initial: Settings) {
    var technicien by mutableStateOf(initial.technicien)
    var initiales by mutableStateOf(initial.initiales)
    var emailCompta by mutableStateOf(initial.emailCompta)
    var emailCopie by mutableStateOf(initial.emailCopie)
    var message by mutableStateOf(initial.message)

    /** Initiales déduites du nom tant que le technicien ne les a pas saisies lui-même. */
    var initialesAuto by mutableStateOf(initial.initiales.isBlank())

    fun onTechnicien(v: String) {
        technicien = v
        if (initialesAuto) initiales = SettingsRules.initialesFrom(v)
    }

    fun onInitiales(v: String) {
        initiales = v.uppercase(Locale.FRANCE).filter { !it.isWhitespace() }.take(6)
        initialesAuto = false
    }

    fun reset(s: Settings) {
        technicien = s.technicien
        initiales = s.initiales
        emailCompta = s.emailCompta
        emailCopie = s.emailCopie
        message = s.message
        initialesAuto = s.initiales.isBlank()
    }

    fun applyTo(s: Settings) = s.copy(
        technicien = technicien,
        initiales = initiales,
        emailCompta = emailCompta,
        emailCopie = emailCopie,
        message = message,
    )

    val technicienOk get() = SettingsRules.technicienError(technicien) == null && SettingsRules.initialesError(initiales) == null
    val comptaOk get() = SettingsRules.emailsError(emailCompta, required = true) == null &&
        SettingsRules.emailsError(emailCopie, required = false) == null
}

@Composable
private fun rememberSettingsForm(initial: Settings): SettingsForm {
    // Les valeurs survivent à la rotation de la tablette
    val values = rememberSaveable {
        arrayListOf(initial.technicien, initial.initiales, initial.emailCompta, initial.emailCopie, initial.message)
    }
    val form = remember { SettingsForm(initial.copy(technicien = values[0], initiales = values[1], emailCompta = values[2], emailCopie = values[3], message = values[4])) }
    values[0] = form.technicien
    values[1] = form.initiales
    values[2] = form.emailCompta
    values[3] = form.emailCopie
    values[4] = form.message
    return form
}

/** Nom de fichier d'exemple avec les initiales saisies. */
private fun fileNameExample(initiales: String): String {
    val d = Naming.today().split('/')
    val date = if (d.size == 3) "${d[0]}-${d[1]}-20${d[2]}" else Naming.today()
    return "02- CLIENT SITE 02000 VILLE 1234567 $date ${initiales.trim().ifEmpty { "XX" }}.pdf"
}

@Composable
private fun TechnicienCard(form: SettingsForm, showErrors: Boolean) {
    val c = Vip.colors
    SectionCard(
        "Technicien",
        icon = Icons.Filled.Badge,
        subtitle = "Écrit sur chaque bon et à la fin du nom des PDF",
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            VipField(
                label = "Nom affiché sur les bons",
                value = form.technicien,
                onValueChange = form::onTechnicien,
                placeholder = "ex. Chris.E",
                capitalization = KeyboardCapitalization.Words,
                supporting = "Rempli d'office dans « Commercial / Monteur »",
                error = if (showErrors) SettingsRules.technicienError(form.technicien) else null,
                modifier = Modifier.weight(1.8f),
            )
            VipField(
                label = "Initiales",
                value = form.initiales,
                onValueChange = form::onInitiales,
                placeholder = "ex. CE",
                capitalization = KeyboardCapitalization.Characters,
                supporting = if (form.initialesAuto && form.initiales.isNotEmpty()) "Déduites du nom" else "Fin du nom des PDF",
                error = if (showErrors) SettingsRules.initialesError(form.initiales) else null,
                modifier = Modifier.weight(1f),
            )
        }
        Column(
            Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.surfaceContainerLow, MaterialTheme.shapes.small)
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text("APERÇU", style = MaterialTheme.typography.labelSmall, color = c.muted, letterSpacing = 1.4.sp)
            PreviewLine("Commercial / Monteur :", form.technicien.trim().ifEmpty { "—" })
            PreviewLine("Nom des PDF :", fileNameExample(form.initiales))
        }
    }
}

@Composable
private fun PreviewLine(label: String, value: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(label, style = MaterialTheme.typography.bodyMedium, color = Vip.colors.muted)
        Spacer(Modifier.width(8.dp))
        Text(
            value,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun ComptaCard(form: SettingsForm, showErrors: Boolean) {
    SectionCard(
        "Comptabilité",
        icon = Icons.Filled.Email,
        subtitle = "Destinataire proposé à chaque envoi de PDF",
    ) {
        VipField(
            label = "E-mail de la comptabilité",
            value = form.emailCompta,
            onValueChange = { form.emailCompta = it },
            placeholder = "ex. compta@exemple.fr",
            keyboardType = KeyboardType.Email,
            capitalization = KeyboardCapitalization.None,
            error = if (showErrors) SettingsRules.emailsError(form.emailCompta, required = true) else null,
        )
        VipField(
            label = "Copie à (facultatif)",
            value = form.emailCopie,
            onValueChange = { form.emailCopie = it },
            keyboardType = KeyboardType.Email,
            capitalization = KeyboardCapitalization.None,
            supporting = "Plusieurs adresses : séparez-les par une virgule",
            error = if (showErrors) SettingsRules.emailsError(form.emailCopie, required = false) else null,
        )
        VipField(
            label = "Message de l'e-mail",
            value = form.message,
            onValueChange = { form.message = it },
            singleLine = false,
            minLines = 4,
        )
        if (form.message != Settings.DEFAULT_MESSAGE) {
            VipButton(
                "Remettre le message par défaut",
                { form.message = Settings.DEFAULT_MESSAGE },
                icon = Icons.Filled.Restore,
                tone = Tone.GHOST,
                compact = true,
            )
        }
    }
}

// ------------------------------------------------------------------ première configuration

private enum class SetupStep(val title: String, val subtitle: String, val heading: String, val intro: String) {
    TECHNICIEN(
        "Technicien", "Votre nom et vos initiales",
        "Qui êtes-vous ?",
        "Votre nom est écrit sur chaque bon, vos initiales terminent le nom des PDF envoyés.",
    ),
    COMPTA(
        "Comptabilité", "Où envoyer les bons",
        "Où envoyer les bons ?",
        "Cette adresse est proposée automatiquement à chaque envoi à la comptabilité.",
    ),
    RECAP(
        "C'est prêt", "Vérifier et commencer",
        "Tout est prêt",
        "Vérifiez vos réglages avant de commencer. Ils restent modifiables à tout moment dans Réglages.",
    ),
}

/**
 * Première ouverture de l'application : réglages obligatoires, en trois étapes.
 * Chaque étape validée est enregistrée aussitôt sur la tablette.
 */
@Composable
fun SetupScreen(vm: AppViewModel) {
    val saved = remember { vm.settings.value }
    val form = rememberSettingsForm(saved)
    var stepIndex by rememberSaveable { mutableIntStateOf(0) }
    var showErrors by rememberSaveable { mutableStateOf(false) }
    val step = SetupStep.entries[stepIndex]
    val stepOk = when (step) {
        SetupStep.TECHNICIEN -> form.technicienOk
        SetupStep.COMPTA -> form.comptaOk
        SetupStep.RECAP -> form.technicienOk && form.comptaOk
    }

    fun next() {
        if (!stepOk) {
            showErrors = true
            return
        }
        showErrors = false
        val s = form.applyTo(vm.settings.value)
        if (step == SetupStep.RECAP) {
            vm.completeSetup(s)
        } else {
            vm.saveSettings(s)
            stepIndex++
        }
    }

    fun previous() {
        showErrors = false
        stepIndex--
    }

    BackHandler(enabled = stepIndex > 0) { previous() }

    val content: @Composable ColumnScope.() -> Unit = {
        when (step) {
            SetupStep.TECHNICIEN -> TechnicienCard(form, showErrors)
            SetupStep.COMPTA -> ComptaCard(form, showErrors)
            SetupStep.RECAP -> Recap(form, onEdit = { stepIndex = it })
        }
    }

    BoxWithConstraints(
        Modifier
            .fillMaxSize()
            .background(Vip.colors.chrome),
    ) {
        val sheet = MaterialTheme.colorScheme.background
        if (maxWidth >= 900.dp) {
            val panel = if (maxWidth >= 1100.dp) 420.dp else 360.dp
            Row(Modifier.fillMaxSize()) {
                SetupPanel(stepIndex, Modifier.width(panel))
                SetupPane(
                    step, stepIndex, content, ::previous, ::next,
                    Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .statusBarsPadding()
                        .clip(RoundedCornerShape(topStart = 28.dp))
                        .background(sheet),
                )
            }
        } else {
            Column(Modifier.fillMaxSize()) {
                SetupHeader(stepIndex)
                SetupPane(
                    step, stepIndex, content, ::previous, ::next,
                    Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp))
                        .background(sheet),
                )
            }
        }
    }
}

/** Colonne de droite : titre de l'étape, formulaire, boutons. */
@Composable
private fun SetupPane(
    step: SetupStep,
    stepIndex: Int,
    content: @Composable ColumnScope.() -> Unit,
    onBack: () -> Unit,
    onNext: () -> Unit,
    modifier: Modifier,
) {
    val c = Vip.colors
    val last = stepIndex == SetupStep.entries.lastIndex
    Column(modifier.imePadding()) {
        Box(
            Modifier
                .weight(1f)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState()),
            contentAlignment = Alignment.TopCenter,
        ) {
            Column(
                Modifier
                    .widthIn(max = 680.dp)
                    .fillMaxWidth()
                    .padding(horizontal = 32.dp, vertical = 36.dp),
                verticalArrangement = Arrangement.spacedBy(20.dp),
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        "ÉTAPE ${stepIndex + 1} SUR ${SetupStep.entries.size}",
                        style = MaterialTheme.typography.labelMedium,
                        color = Palette.AmberDeep,
                        letterSpacing = 1.4.sp,
                    )
                    Text(step.heading, style = MaterialTheme.typography.headlineMedium)
                    Text(step.intro, style = MaterialTheme.typography.bodyLarge, color = c.muted)
                }
                content()
            }
        }
        Surface(color = c.card, shadowElevation = 10.dp) {
            Column {
                HorizontalDivider(color = c.cardBorder)
                Row(
                    Modifier
                        .fillMaxWidth()
                        .navigationBarsPadding()
                        .padding(horizontal = 24.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (stepIndex > 0) {
                        VipButton("Retour", onBack, icon = Icons.AutoMirrored.Filled.ArrowBack, tone = Tone.GHOST)
                    } else {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Filled.Lock, contentDescription = null, tint = c.muted, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp))
                            Text("Enregistré sur cette tablette", style = MaterialTheme.typography.bodyMedium, color = c.muted)
                        }
                    }
                    Spacer(Modifier.weight(1f))
                    VipButton(
                        if (last) "Commencer" else "Continuer",
                        onNext,
                        icon = if (last) Icons.Filled.Check else Icons.AutoMirrored.Filled.ArrowForward,
                    )
                }
            }
        }
    }
}

/** Récapitulatif avant de commencer. */
@Composable
private fun Recap(form: SettingsForm, onEdit: (Int) -> Unit) {
    val c = Vip.colors
    SectionCard("Récapitulatif", icon = Icons.Filled.FactCheck) {
        RecapRow("Technicien", "${form.technicien.trim()}  ·  initiales ${form.initiales.trim()}") { onEdit(0) }
        HorizontalDivider(color = c.cardBorder)
        RecapRow("Comptabilité", form.emailCompta.trim()) { onEdit(1) }
        if (form.emailCopie.isNotBlank()) {
            HorizontalDivider(color = c.cardBorder)
            RecapRow("Copie à", form.emailCopie.trim()) { onEdit(1) }
        }
        HorizontalDivider(color = c.cardBorder)
        RecapRow("Message", form.message.lines().firstOrNull { it.isNotBlank() }.orEmpty() + " …") { onEdit(1) }
        HorizontalDivider(color = c.cardBorder)
        RecapRow("Exemple de PDF", fileNameExample(form.initiales), onEdit = null)
    }
    InfoBanner(
        icon = Icons.Filled.Lock,
        title = "Enregistré uniquement sur cette tablette",
        text = "Ces réglages servent à remplir vos bons et à préparer l'e-mail pour la comptabilité.",
        background = c.infoSoft,
        content = c.info,
    )
}

@Composable
private fun RecapRow(label: String, value: String, onEdit: (() -> Unit)?) {
    val c = Vip.colors
    Row(verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(label, style = MaterialTheme.typography.labelMedium, color = c.muted)
            Text(value, style = MaterialTheme.typography.bodyLarge, maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
        if (onEdit != null) {
            TextButton(onClick = onEdit) {
                Icon(Icons.Filled.Edit, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(6.dp))
                Text("Modifier")
            }
        }
    }
}

/** Panneau sombre (tablette à l'horizontale) : accueil et étapes. */
@Composable
private fun SetupPanel(stepIndex: Int, modifier: Modifier) {
    val c = Vip.colors
    Box(
        modifier
            .fillMaxHeight()
            .background(c.chrome),
    ) {
        TreadPattern(
            Modifier
                .align(Alignment.BottomEnd)
                .padding(bottom = 72.dp)
                .width(84.dp)
                .height(260.dp),
        )
        Column(
            Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .padding(horizontal = 36.dp, vertical = 28.dp),
        ) {
            SetupBrand()
            Spacer(Modifier.height(48.dp))
            Text("Bienvenue", style = MaterialTheme.typography.displaySmall, fontFamily = BarlowCondensed, color = c.onChrome)
            Spacer(Modifier.height(8.dp))
            Text(
                "Trois réglages avant de commencer. Ils sont enregistrés sur cette tablette.",
                style = MaterialTheme.typography.bodyLarge,
                color = c.onChromeMuted,
            )
            Spacer(Modifier.height(40.dp))
            SetupSteps(stepIndex)
            Spacer(Modifier.weight(1f))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.Info, contentDescription = null, tint = c.onChromeMuted, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text("Modifiable à tout moment dans Réglages", style = MaterialTheme.typography.bodySmall, color = c.onChromeMuted)
            }
        }
    }
}

/** En-tête sombre (tablette à la verticale). */
@Composable
private fun SetupHeader(stepIndex: Int) {
    val c = Vip.colors
    Column(
        Modifier
            .fillMaxWidth()
            .background(c.chrome)
            .statusBarsPadding()
            .padding(horizontal = 28.dp, vertical = 20.dp),
    ) {
        SetupBrand()
        Spacer(Modifier.height(20.dp))
        Text("Bienvenue", style = MaterialTheme.typography.headlineMedium, color = c.onChrome)
        Text(
            "Trois réglages avant de commencer. Ils sont enregistrés sur cette tablette.",
            style = MaterialTheme.typography.bodyMedium,
            color = c.onChromeMuted,
        )
        Spacer(Modifier.height(20.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            SetupStep.entries.forEachIndexed { i, s ->
                StepDot(i, stepIndex)
                Spacer(Modifier.width(8.dp))
                Text(
                    s.title,
                    style = MaterialTheme.typography.labelLarge,
                    color = if (i <= stepIndex) c.onChrome else c.onChromeMuted,
                    maxLines = 1,
                )
                if (i < SetupStep.entries.lastIndex) {
                    Box(
                        Modifier
                            .weight(1f)
                            .padding(horizontal = 12.dp)
                            .height(2.dp)
                            .background(if (i < stepIndex) c.accent else Color.White.copy(alpha = 0.18f)),
                    )
                }
            }
        }
        Spacer(Modifier.height(8.dp))
    }
}

@Composable
private fun SetupBrand() {
    Row(verticalAlignment = Alignment.CenterVertically) {
        BrandMark(52.dp)
        Spacer(Modifier.width(14.dp))
        Column {
            Wordmark(fontSize = 28.sp)
            Text("Bons d'intervention", style = MaterialTheme.typography.bodyMedium, color = Vip.colors.onChromeMuted)
        }
    }
}

/** Étapes, reliées par un trait (fait / en cours / à venir). */
@Composable
private fun SetupSteps(current: Int) {
    val c = Vip.colors
    Column {
        SetupStep.entries.forEachIndexed { i, s ->
            Row {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    StepDot(i, current)
                    if (i < SetupStep.entries.lastIndex) {
                        Box(
                            Modifier
                                .padding(vertical = 4.dp)
                                .width(2.dp)
                                .height(34.dp)
                                .background(if (i < current) c.accent else Color.White.copy(alpha = 0.18f)),
                        )
                    }
                }
                Spacer(Modifier.width(16.dp))
                Column(Modifier.padding(top = 5.dp)) {
                    Text(
                        s.title,
                        style = MaterialTheme.typography.titleMedium,
                        color = if (i <= current) c.onChrome else c.onChromeMuted,
                    )
                    Text(s.subtitle, style = MaterialTheme.typography.bodySmall, color = c.onChromeMuted)
                }
            }
        }
    }
}

@Composable
private fun StepDot(index: Int, current: Int) {
    val c = Vip.colors
    val size = 34.dp
    when {
        index < current -> Box(
            Modifier
                .size(size)
                .background(c.accent, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.Filled.Check, contentDescription = "Fait", tint = c.onAccent, modifier = Modifier.size(18.dp))
        }
        index == current -> Box(
            Modifier
                .size(size)
                .border(2.dp, c.accent, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Text("${index + 1}", style = MaterialTheme.typography.titleSmall, color = c.accent)
        }
        else -> Box(
            Modifier
                .size(size)
                .border(1.5.dp, Color.White.copy(alpha = 0.25f), CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Text("${index + 1}", style = MaterialTheme.typography.titleSmall, color = c.onChromeMuted)
        }
    }
}

// ------------------------------------------------------------------ écran Réglages

/** Réglages : mêmes champs qu'à la première ouverture, enregistrés d'un appui sur « Enregistrer ». */
@Composable
fun SettingsScreen(vm: AppViewModel) {
    val saved by vm.settings.collectAsStateWithLifecycle()
    val form = rememberSettingsForm(saved)
    var showErrors by remember { mutableStateOf(false) }
    var confirmLeave by remember { mutableStateOf(false) }
    val c = Vip.colors
    val draft = form.applyTo(saved)
    val dirty = draft.trimmed() != saved.trimmed()

    fun save(): Boolean {
        if (!draft.isComplete) {
            showErrors = true
            vm.message("Corrigez les champs signalés avant d'enregistrer")
            return false
        }
        showErrors = false
        vm.saveSettings(draft)
        form.reset(draft.trimmed())
        vm.message("Réglages enregistrés")
        return true
    }

    BackHandler(enabled = dirty) { confirmLeave = true }

    Column(
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
    ) {
        VipTopBar(
            title = "Réglages",
            subtitle = "Enregistrés sur cette tablette",
            onBack = { if (dirty) confirmLeave = true else vm.back() },
        )
        Box(
            Modifier
                .weight(1f)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .imePadding(),
            contentAlignment = Alignment.TopCenter,
        ) {
            Column(
                Modifier
                    .widthIn(max = 780.dp)
                    .fillMaxWidth()
                    .padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                TechnicienCard(form, showErrors)
                ComptaCard(form, showErrors)
                SectionCard("À propos", icon = Icons.Filled.Info) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        BrandMark(44.dp)
                        Spacer(Modifier.width(14.dp))
                        Column {
                            Text("VIP Pneus – Bons d'intervention", style = MaterialTheme.typography.titleSmall)
                            Text(
                                "Version ${BuildConfigInfo.versionName(LocalContext.current)}",
                                style = MaterialTheme.typography.bodySmall,
                                color = c.muted,
                            )
                        }
                    }
                    Text(
                        "Les bons et les réglages sont enregistrés uniquement sur cette tablette. Polices Barlow et " +
                            "Source Sans 3 (licence SIL OFL 1.1), bibliothèque PdfBox-Android (licence Apache 2.0).",
                        style = MaterialTheme.typography.bodySmall,
                        color = c.muted,
                    )
                }
                Spacer(Modifier.height(8.dp))
            }
        }
        SaveBar(dirty, onCancel = { showErrors = false; form.reset(saved) }, onSave = { save() })
    }

    if (confirmLeave) {
        AlertDialog(
            onDismissRequest = { confirmLeave = false },
            containerColor = c.card,
            icon = { Icon(Icons.Filled.EditNote, contentDescription = null, tint = c.warning) },
            title = { Text("Enregistrer les modifications ?") },
            text = { Text("Vos réglages ont été modifiés mais pas encore enregistrés.") },
            confirmButton = {
                VipButton("Enregistrer", {
                    confirmLeave = false
                    if (save()) vm.back()
                }, compact = true)
            },
            dismissButton = {
                TextButton(onClick = {
                    confirmLeave = false
                    vm.back()
                }) { Text("Ne pas enregistrer") }
            },
        )
    }
}

/** Barre du bas des réglages : état de l'enregistrement et boutons. */
@Composable
private fun SaveBar(dirty: Boolean, onCancel: () -> Unit, onSave: () -> Unit) {
    val c = Vip.colors
    Surface(color = c.card, shadowElevation = 10.dp) {
        Column {
            HorizontalDivider(color = c.cardBorder)
            Row(
                Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .padding(horizontal = 24.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                val (icon: ImageVector, text, tint) = if (dirty) {
                    Triple(Icons.Filled.EditNote, "Modifications non enregistrées", c.warning)
                } else {
                    Triple(Icons.Filled.CheckCircle, "Réglages enregistrés sur cette tablette", c.success)
                }
                Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(10.dp))
                Text(text, style = MaterialTheme.typography.bodyMedium, color = if (dirty) c.warning else c.muted, modifier = Modifier.weight(1f))
                if (dirty) {
                    VipButton("Annuler", onCancel, tone = Tone.GHOST)
                    Spacer(Modifier.width(10.dp))
                    VipButton("Enregistrer", onSave, icon = Icons.Filled.Save)
                }
            }
        }
    }
}
