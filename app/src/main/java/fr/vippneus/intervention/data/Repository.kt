package fr.vippneus.intervention.data

import android.content.ContentResolver
import android.net.Uri
import android.provider.OpenableColumns
import kotlinx.serialization.json.Json
import java.io.File
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID

/**
 * Stockage local : un dossier par intervention (interventions/<id>/) contenant
 * intervention.json, les documents importés et le PDF exporté.
 */
class InterventionRepository(filesDir: File) {
    private val root = File(filesDir, "interventions").apply { mkdirs() }

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    fun dir(id: String) = File(root, id)

    fun exportDir(id: String) = File(dir(id), "export")

    fun newId(): String =
        SimpleDateFormat("yyyyMMdd-HHmmss", Locale.FRANCE).format(Date()) + "-" +
            UUID.randomUUID().toString().substring(0, 6)

    fun loadAll(): List<Intervention> =
        root.listFiles()?.filter { it.isDirectory }?.mapNotNull { load(it.name) }.orEmpty()

    fun load(id: String): Intervention? {
        val f = File(dir(id), FILE)
        if (!f.exists()) return null
        return try {
            json.decodeFromString(Intervention.serializer(), f.readText())
        } catch (_: Exception) {
            // Fichier en cours d'écriture ou abîmé : on tente la copie de secours
            val backup = File(dir(id), "$FILE.bak")
            runCatching { json.decodeFromString(Intervention.serializer(), backup.readText()) }.getOrNull()
        }
    }

    @Synchronized
    fun save(i: Intervention) {
        val d = dir(i.id).apply { mkdirs() }
        val target = File(d, FILE)
        val tmp = File(d, "$FILE.tmp")
        tmp.writeText(json.encodeToString(Intervention.serializer(), i))
        if (target.exists()) target.copyTo(File(d, "$FILE.bak"), overwrite = true)
        if (!tmp.renameTo(target)) {
            tmp.copyTo(target, overwrite = true)
            tmp.delete()
        }
    }

    fun delete(id: String) {
        dir(id).deleteRecursively()
    }

    /** Copie un fichier choisi par l'utilisateur dans le dossier de l'intervention. */
    fun importUri(resolver: ContentResolver, uri: Uri, id: String, prefix: String, extension: String): File {
        val d = dir(id).apply { mkdirs() }
        val f = File(d, "$prefix-${UUID.randomUUID().toString().substring(0, 8)}.$extension")
        val input = resolver.openInputStream(uri) ?: throw IOException("Fichier inaccessible")
        input.use { ins -> f.outputStream().use { ins.copyTo(it) } }
        if (f.length() == 0L) {
            f.delete()
            throw IOException("Fichier vide")
        }
        return f
    }

    fun newFile(id: String, prefix: String, extension: String): File {
        val d = dir(id).apply { mkdirs() }
        return File(d, "$prefix-${UUID.randomUUID().toString().substring(0, 8)}.$extension")
    }

    companion object {
        private const val FILE = "intervention.json"

        fun displayName(resolver: ContentResolver, uri: Uri): String? = try {
            resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
                if (c.moveToFirst()) c.getString(0) else null
            }
        } catch (_: Exception) {
            null
        } ?: uri.lastPathSegment?.substringAfterLast('/')
    }
}
