package fr.vippneus.intervention.ui

import android.app.Application
import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.runtime.mutableStateListOf
import androidx.core.content.FileProvider
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.tom_roush.pdfbox.pdmodel.encryption.InvalidPasswordException
import fr.vippneus.intervention.data.Attachment
import fr.vippneus.intervention.data.AttachmentKind
import fr.vippneus.intervention.data.DocKeys
import fr.vippneus.intervention.data.Intervention
import fr.vippneus.intervention.data.InterventionRepository
import fr.vippneus.intervention.data.InterventionType
import fr.vippneus.intervention.data.Naming
import fr.vippneus.intervention.data.Settings
import fr.vippneus.intervention.data.SettingsStore
import fr.vippneus.intervention.data.SourceDoc
import fr.vippneus.intervention.data.Suggestions
import fr.vippneus.intervention.importer.ClientDocs
import fr.vippneus.intervention.importer.DocText
import fr.vippneus.intervention.importer.FpsPageDetector
import fr.vippneus.intervention.importer.ImportPlan
import fr.vippneus.intervention.pdf.ExportException
import fr.vippneus.intervention.pdf.FpsTemplate.K
import fr.vippneus.intervention.pdf.PdfExporter
import fr.vippneus.intervention.pdf.PdfPages
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.Locale
import java.util.UUID

sealed interface Screen {
    data object Home : Screen
    data object Settings : Screen
    data class Fps(val id: String) : Screen
    data class Document(val id: String) : Screen
    data class Editor(val id: String) : Screen
    data class Viewer(val id: String) : Screen
}

/** Document non reconnu, en attente du choix du technicien. */
data class PendingImport(val id: String, val pdf: File, val name: String, val info: PdfPages.Info, val values: Map<String, String>)

class AppViewModel(app: Application) : AndroidViewModel(app) {
    private val repo = InterventionRepository(app.filesDir)
    private val settingsStore = SettingsStore(app)
    private val exporter = PdfExporter(app)
    private val resolver get() = getApplication<Application>().contentResolver

    private val _interventions = MutableStateFlow<List<Intervention>>(emptyList())
    val interventions: StateFlow<List<Intervention>> = _interventions.asStateFlow()

    private val _loaded = MutableStateFlow(false)
    val loaded: StateFlow<Boolean> = _loaded.asStateFlow()

    private val _settings = MutableStateFlow(settingsStore.load())
    val settings: StateFlow<Settings> = _settings.asStateFlow()

    val suggestions: StateFlow<Map<String, List<String>>> = _interventions
        .map { Suggestions.build(it) }
        .flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyMap())

    /** Pile de navigation (le dernier élément est l'écran affiché). */
    val backStack = mutableStateListOf<Screen>(Screen.Home)

    private val _messages = MutableSharedFlow<String>(extraBufferCapacity = 8)
    val messages: SharedFlow<String> = _messages

    /** Libellé de l'opération en cours (null = aucune). */
    private val _busy = MutableStateFlow<String?>(null)
    val busy: StateFlow<String?> = _busy.asStateFlow()

    private val _pendingImport = MutableStateFlow<PendingImport?>(null)
    val pendingImport: StateFlow<PendingImport?> = _pendingImport.asStateFlow()

    private val saveJobs = HashMap<String, Job>()
    private val dirty = HashSet<String>()

    init {
        viewModelScope.launch {
            val all = withContext(Dispatchers.IO) { repo.loadAll() }
            _interventions.value = all.sortedByDescending { it.updatedAt }
            _loaded.value = true
        }
    }

    // ---------------------------------------------------------------- navigation

    fun navigate(screen: Screen) {
        backStack.add(screen)
    }

    fun back(): Boolean {
        if (backStack.size <= 1) return false
        backStack.removeAt(backStack.lastIndex)
        return true
    }

    fun openIntervention(i: Intervention) {
        navigate(if (i.type == InterventionType.FPS) Screen.Fps(i.id) else Screen.Document(i.id))
    }

    fun message(text: String) {
        _messages.tryEmit(text)
    }

    // ---------------------------------------------------------------- données

    fun get(id: String): Intervention? = _interventions.value.firstOrNull { it.id == id }

    /** Modifie une intervention (sauvegarde automatique différée). */
    fun update(id: String, touch: Boolean = true, transform: (Intervention) -> Intervention) {
        val list = _interventions.value
        val index = list.indexOfFirst { it.id == id }
        if (index < 0) return
        val old = list[index]
        var new = transform(old)
        if (new == old) return
        if (touch) new = new.copy(updatedAt = System.currentTimeMillis())
        _interventions.value = list.toMutableList().also { it[index] = new }
        scheduleSave(id)
    }

    fun setValue(id: String, key: String, value: String) = update(id) {
        it.copy(values = if (value.isEmpty()) it.values - key else it.values + (key to value))
    }

    private fun add(i: Intervention) {
        _interventions.value = listOf(i) + _interventions.value
        dirty += i.id
        persistNow(i.id)
    }

    private fun scheduleSave(id: String) {
        dirty += id
        saveJobs.remove(id)?.cancel()
        saveJobs[id] = viewModelScope.launch {
            delay(600)
            persistNow(id)
        }
    }

    private fun persistNow(id: String) {
        val i = get(id) ?: return
        dirty -= id
        viewModelScope.launch(Dispatchers.IO) {
            runCatching { repo.save(i) }.onFailure { message("Enregistrement impossible : ${it.message}") }
        }
    }

    /** Enregistre immédiatement les modifications en attente (mise en arrière-plan de l'appli). */
    fun flush() {
        val ids = dirty.toList()
        dirty.clear()
        for (id in ids) {
            saveJobs.remove(id)?.cancel()
            get(id)?.let { runCatching { repo.save(it) } }
        }
    }

    override fun onCleared() {
        flush()
        super.onCleared()
    }

    fun saveSettings(s: Settings) {
        settingsStore.save(s)
        _settings.value = settingsStore.load()
    }

    // ---------------------------------------------------------------- création

    fun createFps(): String {
        val s = _settings.value
        val values = buildMap {
            put(K.DATE, Naming.today())
            if (s.technicien.isNotBlank()) put(K.MONTEUR, s.technicien.trim())
        }
        val i = Intervention(id = repo.newId(), type = InterventionType.FPS, createdAt = System.currentTimeMillis(), values = values)
        add(i)
        return i.id
    }

    /** Nouvelle fiche reprenant client, adresse et matériel d'une fiche existante (sans signature). */
    fun duplicate(id: String) {
        val src = get(id) ?: return
        val keep = when (src.type) {
            InterventionType.FPS -> listOf(
                K.CLIENT_MANDATAIRE, K.MANDATAIRE_ADRESSE, K.MANDATAIRE_CP, K.MONTEUR,
                K.CLIENT_UTILISATEUR, K.UTILISATEUR_ADRESSE, K.UTILISATEUR_CP,
            )
            InterventionType.DOCUMENT -> return message("Seules les fiches presse mobile peuvent être dupliquées")
        }
        val values = src.values.filterKeys { it in keep } + (K.DATE to Naming.today())
        val i = Intervention(id = repo.newId(), type = InterventionType.FPS, createdAt = System.currentTimeMillis(), values = values)
        add(i)
        navigate(Screen.Fps(i.id))
    }

    fun delete(ids: Collection<String>) {
        val set = ids.toSet()
        _interventions.value = _interventions.value.filterNot { it.id in set }
        set.forEach { saveJobs.remove(it)?.cancel(); dirty -= it }
        viewModelScope.launch(Dispatchers.IO) { set.forEach { repo.delete(it) } }
    }

    // ---------------------------------------------------------------- import

    private fun isImage(mime: String?, name: String): Boolean {
        if (mime != null && mime.startsWith("image/")) return true
        val n = name.lowercase(Locale.ROOT)
        return listOf(".jpg", ".jpeg", ".png", ".webp", ".heic", ".heif", ".bmp").any { n.endsWith(it) }
    }

    private fun extension(name: String, image: Boolean): String {
        val ext = name.substringAfterLast('.', "").lowercase(Locale.ROOT)
        return when {
            image && ext in listOf("jpg", "jpeg", "png", "webp", "heic", "heif", "bmp") -> ext
            image -> "jpg"
            else -> "pdf"
        }
    }

    private fun friendlyError(e: Throwable): String = when (e) {
        is InvalidPasswordException -> "ce PDF est protégé par un mot de passe"
        is ExportException -> e.message ?: "erreur"
        else -> e.message ?: e.javaClass.simpleName
    }

    /** Copie le fichier choisi dans le dossier de l'intervention ; une photo devient un PDF d'une page. */
    private fun copyAsPdf(id: String, uri: Uri, name: String, image: Boolean): File {
        val f = repo.importUri(resolver, uri, id, if (image) "photo" else "source", extension(name, image))
        if (!image) return f
        val out = repo.newFile(id, "source", "pdf")
        PdfPages.imageToPdf(f, out)
        f.delete()
        return out
    }

    /** Lit le texte du PDF et reconnaît le type de document (bon de commande, feuille de tâche...). */
    private fun analyze(pdf: File, info: PdfPages.Info): ImportPlan {
        val s = _settings.value
        val text = try {
            PdfExporter.loadDecrypted(pdf).use { DocText.read(it) }
        } catch (_: Exception) {
            DocText(emptyList())
        }
        val fpsFirst = FpsPageDetector.isFpsForm(getApplication(), pdf, info.width, info.height)
        return ClientDocs.analyze(text, fpsFirst, s.technicien, Naming.today())
    }

    /**
     * Import d'un PDF (ou d'une photo) envoyé par un client : les informations lues dans le document
     * remplissent automatiquement la fiche, ou les champs de la feuille du client.
     */
    fun importClient(uri: Uri, mimeHint: String? = null) {
        viewModelScope.launch {
            _busy.value = "Lecture du document…"
            val id = repo.newId()
            try {
                val mime = mimeHint?.takeIf { it != "*/*" } ?: resolver.getType(uri)
                val name = InterventionRepository.displayName(resolver, uri) ?: "document.pdf"
                val image = isImage(mime, name)
                val (pdf, info, plan) = withContext(Dispatchers.IO) {
                    val pdf = copyAsPdf(id, uri, name, image)
                    val info = PdfPages.info(pdf)
                    Triple(pdf, info, analyze(pdf, info))
                }
                applyPlan(id, pdf, name, info, plan)
            } catch (e: Exception) {
                withContext(Dispatchers.IO) { repo.delete(id) }
                message("Import impossible : ${friendlyError(e)}")
            } finally {
                _busy.value = null
            }
        }
    }

    private fun fpsWithDocument(id: String, pdf: File, name: String, info: PdfPages.Info, values: Map<String, String>, skipFirst: Boolean, recognized: String?) =
        Intervention(
            id = id,
            type = InterventionType.FPS,
            createdAt = System.currentTimeMillis(),
            values = values.filterKeys { !it.startsWith("doc.") && !it.startsWith("if.") },
            recognized = recognized,
            attachments = listOf(
                Attachment(UUID.randomUUID().toString(), pdf.name, name, AttachmentKind.PDF, info.pageCount, skipFirstPage = skipFirst && info.pageCount > 1),
            ),
        )

    private fun applyPlan(id: String, pdf: File, name: String, info: PdfPages.Info, plan: ImportPlan) {
        when (plan) {
            is ImportPlan.Fiche -> {
                add(fpsWithDocument(id, pdf, name, info, plan.values, plan.skipFirstPage, plan.docType))
                navigate(Screen.Fps(id))
                message("Fiche remplie automatiquement depuis « ${plan.docType} ». Vérifiez puis complétez horamètre, serrage et signature.")
            }
            is ImportPlan.Feuille -> {
                add(
                    Intervention(
                        id = id,
                        type = InterventionType.DOCUMENT,
                        createdAt = System.currentTimeMillis(),
                        values = plan.values,
                        template = plan.template,
                        hints = plan.hints,
                        recognized = plan.docType,
                        source = SourceDoc(pdf.name, name, info.pageCount, info.width, info.height),
                    )
                )
                navigate(Screen.Document(id))
                message("« ${plan.docType} » reconnue : les champs saisis sont placés automatiquement sur le document.")
            }
            is ImportPlan.Inconnu -> _pendingImport.value = PendingImport(id, pdf, name, info, plan.values)
        }
    }

    enum class ImportChoice { FICHE, DOCUMENT, ANNULER }

    /** Document non reconnu : fiche FPS avec le document joint, ou document complété à la main. */
    fun resolvePendingImport(choice: ImportChoice) {
        val p = _pendingImport.value ?: return
        _pendingImport.value = null
        when (choice) {
            ImportChoice.FICHE -> {
                add(fpsWithDocument(p.id, p.pdf, p.name, p.info, p.values, false, null))
                navigate(Screen.Fps(p.id))
            }
            ImportChoice.DOCUMENT -> {
                val values = p.values.filterKeys { it.startsWith("doc.") } + (DocKeys.DATE to Naming.today())
                add(
                    Intervention(
                        id = p.id,
                        type = InterventionType.DOCUMENT,
                        createdAt = System.currentTimeMillis(),
                        values = values,
                        source = SourceDoc(p.pdf.name, p.name, p.info.pageCount, p.info.width, p.info.height),
                    )
                )
                navigate(Screen.Document(p.id))
            }
            ImportChoice.ANNULER -> viewModelScope.launch(Dispatchers.IO) { repo.delete(p.id) }
        }
    }

    /** Fichier reçu d'une autre application (« Ouvrir avec », « Partager »). */
    fun receive(uri: Uri, mimeHint: String?) = importClient(uri, mimeHint)

    // ---------------------------------------------------------------- pièces jointes

    /**
     * Ajoute un document après la page 1. Pour une fiche FPS, un bon de commande reconnu
     * complète aussi les champs encore vides de la fiche.
     */
    fun addAttachment(id: String, uri: Uri) {
        viewModelScope.launch {
            _busy.value = "Ajout du document…"
            var copied: File? = null
            try {
                val mime = resolver.getType(uri)
                val name = InterventionRepository.displayName(resolver, uri) ?: "document"
                val image = isImage(mime, name)
                val kind = if (image) AttachmentKind.IMAGE else AttachmentKind.PDF
                val isFps = get(id)?.type == InterventionType.FPS
                val (att, plan) = withContext(Dispatchers.IO) {
                    val f = repo.importUri(resolver, uri, id, "pj", extension(name, image)).also { copied = it }
                    if (kind == AttachmentKind.PDF) {
                        val info = PdfPages.info(f)
                        val plan = if (isFps) analyze(f, info) else null
                        val skip = plan is ImportPlan.Fiche && plan.skipFirstPage && info.pageCount > 1
                        Attachment(UUID.randomUUID().toString(), f.name, name, kind, info.pageCount, skipFirstPage = skip) to plan
                    } else {
                        Attachment(UUID.randomUUID().toString(), f.name, name, kind, 1) to null
                    }
                }
                var filled = 0
                update(id) { cur ->
                    var values = cur.values
                    if (plan is ImportPlan.Fiche) {
                        for ((k, v) in plan.values) {
                            if (k.startsWith("doc.") || k.startsWith("if.")) continue
                            if (cur.value(k).isBlank() && v.isNotBlank()) {
                                values = values + (k to v)
                                filled++
                            }
                        }
                    }
                    cur.copy(
                        attachments = cur.attachments + att,
                        values = values,
                        recognized = if (filled > 0) plan?.docType ?: cur.recognized else cur.recognized,
                    )
                }
                if (filled > 0) message("$filled champ(s) remplis automatiquement depuis « ${plan?.docType} »")
            } catch (e: Exception) {
                copied?.delete()
                message("Ajout impossible : ${friendlyError(e)}")
            } finally {
                _busy.value = null
            }
        }
    }

    /** Fichier de destination pour une photo prise avec l'appareil. */
    fun newPhotoFile(id: String): File = repo.newFile(id, "photo", "jpg")

    fun photoUri(file: File): Uri = FileProvider.getUriForFile(getApplication(), authority(), file)

    fun onPhotoTaken(id: String, file: File, success: Boolean) {
        if (!success || !file.exists() || file.length() == 0L) {
            file.delete()
            return
        }
        val name = "Photo du " + java.text.SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.FRANCE).format(java.util.Date())
        update(id) {
            it.copy(attachments = it.attachments + Attachment(UUID.randomUUID().toString(), file.name, name, AttachmentKind.IMAGE, 1))
        }
    }

    fun removeAttachment(id: String, attachmentId: String) {
        val att = get(id)?.attachments?.firstOrNull { it.id == attachmentId } ?: return
        update(id) { it.copy(attachments = it.attachments.filterNot { a -> a.id == attachmentId }) }
        viewModelScope.launch(Dispatchers.IO) { File(repo.dir(id), att.file).delete() }
    }

    fun moveAttachment(id: String, attachmentId: String, delta: Int) = update(id) {
        val list = it.attachments.toMutableList()
        val from = list.indexOfFirst { a -> a.id == attachmentId }
        val to = from + delta
        if (from < 0 || to !in list.indices) return@update it
        list.add(to, list.removeAt(from))
        it.copy(attachments = list)
    }

    // ---------------------------------------------------------------- PDF

    fun sourceFile(i: Intervention): File? = i.source?.let { File(repo.dir(i.id), it.file) }

    fun attachmentFile(i: Intervention, a: Attachment): File = File(repo.dir(i.id), a.file)

    fun exportedFile(id: String): File? =
        repo.exportDir(id).listFiles()?.firstOrNull { it.isFile && it.name.endsWith(".pdf") }

    /** Crée (ou recrée) le PDF final ; renvoie null en cas d'erreur (message affiché). */
    suspend fun generate(id: String): File? {
        saveJobs.remove(id)?.cancel()
        if (id in dirty) persistNow(id)
        val i = get(id) ?: return null
        val s = _settings.value
        val name = Naming.fileName(i, s.initiales)
        _busy.value = "Création du PDF…"
        return try {
            val file = withContext(Dispatchers.IO) {
                val dir = repo.exportDir(id).apply { mkdirs() }
                dir.listFiles()?.forEach { it.delete() }
                val out = File(dir, "$name.pdf")
                exporter.export(i, repo.dir(id), out, title = name, author = s.technicien.ifBlank { "VIP Pneus" })
                out
            }
            update(id, touch = false) { it.copy(generatedAt = System.currentTimeMillis()) }
            file
        } catch (e: Exception) {
            message(friendlyError(e))
            null
        } finally {
            _busy.value = null
        }
    }

    private fun authority() = getApplication<Application>().packageName + ".fileprovider"

    /** Génère les PDF puis ouvre le choix de l'appli d'envoi (e-mail pré-rempli pour la comptabilité). */
    fun send(context: Context, ids: List<String>) {
        viewModelScope.launch {
            val files = mutableListOf<File>()
            for (id in ids) files += generate(id) ?: return@launch
            if (files.isEmpty()) return@launch
            val uris = files.map { FileProvider.getUriForFile(context, authority(), it) }
            val s = _settings.value
            val intent = if (uris.size == 1) {
                Intent(Intent.ACTION_SEND).putExtra(Intent.EXTRA_STREAM, uris[0])
            } else {
                Intent(Intent.ACTION_SEND_MULTIPLE).putParcelableArrayListExtra(Intent.EXTRA_STREAM, ArrayList(uris))
            }
            intent.type = "application/pdf"
            if (s.emailCompta.isNotBlank()) intent.putExtra(Intent.EXTRA_EMAIL, splitEmails(s.emailCompta))
            if (s.emailCopie.isNotBlank()) intent.putExtra(Intent.EXTRA_CC, splitEmails(s.emailCopie))
            val names = files.map { it.nameWithoutExtension }
            val subject = if (names.size == 1) "Bon d'intervention – ${names[0]}" else "Bons d'intervention (${names.size})"
            val body = buildString {
                append(s.message.trim())
                if (s.technicien.isNotBlank()) append("\n").append(s.technicien.trim())
                append("\n\nPièce(s) jointe(s) :\n")
                names.forEach { append("• ").append(it).append(".pdf\n") }
            }
            intent.putExtra(Intent.EXTRA_SUBJECT, subject)
            intent.putExtra(Intent.EXTRA_TEXT, body)
            val clip = ClipData.newRawUri(names[0], uris[0])
            uris.drop(1).forEach { clip.addItem(ClipData.Item(it)) }
            intent.clipData = clip
            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            try {
                context.startActivity(Intent.createChooser(intent, "Envoyer à la comptabilité"))
                val now = System.currentTimeMillis()
                ids.forEach { id -> update(id, touch = false) { it.copy(sentAt = now) } }
            } catch (_: ActivityNotFoundException) {
                message("Aucune application d'envoi (messagerie) n'est installée")
            }
        }
    }

    private fun splitEmails(s: String): Array<String> =
        s.split(',', ';', ' ').map { it.trim() }.filter { it.isNotEmpty() }.toTypedArray()

    /** Ouvre le PDF dans une autre application (lecteur PDF). */
    fun openExternal(context: Context, file: File) {
        val uri = FileProvider.getUriForFile(context, authority(), file)
        val intent = Intent(Intent.ACTION_VIEW).setDataAndType(uri, "application/pdf")
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        try {
            context.startActivity(Intent.createChooser(intent, "Ouvrir avec"))
        } catch (_: ActivityNotFoundException) {
            message("Aucune application ne sait ouvrir les PDF")
        }
    }

    /** Copie le PDF vers un emplacement choisi (Téléchargements, Drive...). */
    fun saveCopy(file: File, target: Uri) {
        viewModelScope.launch {
            try {
                withContext(Dispatchers.IO) {
                    val output = resolver.openOutputStream(target) ?: error("destination inaccessible")
                    output.use { out -> file.inputStream().use { it.copyTo(out) } }
                }
                message("Copie enregistrée")
            } catch (e: Exception) {
                message("Enregistrement impossible : ${e.message}")
            }
        }
    }
}
