package fr.vippneus.intervention.data

import android.content.Context
import java.util.Locale

data class Settings(
    /** Nom écrit dans « Commercial / Monteur » (ex. « Chris.E »). */
    val technicien: String = "",
    /** Initiales ajoutées à la fin du nom des fichiers (ex. « CE »). */
    val initiales: String = "",
    val emailCompta: String = "",
    val emailCopie: String = "",
    val message: String = DEFAULT_MESSAGE,
    /** Première configuration (page « Bienvenue ») terminée. */
    val setupDone: Boolean = false,
) {
    /** Réglages indispensables renseignés et valides. */
    val isComplete: Boolean
        get() = SettingsRules.technicienError(technicien) == null &&
            SettingsRules.initialesError(initiales) == null &&
            SettingsRules.emailsError(emailCompta, required = true) == null &&
            SettingsRules.emailsError(emailCopie, required = false) == null

    /** Valeurs telles qu'enregistrées (espaces superflus retirés). */
    fun trimmed() = copy(
        technicien = technicien.trim(),
        initiales = initiales.trim().uppercase(Locale.FRANCE),
        emailCompta = emailCompta.trim(),
        emailCopie = emailCopie.trim(),
    )

    companion object {
        const val DEFAULT_MESSAGE = "Bonjour,\n\nVeuillez trouver ci-joint le bon d'intervention.\n\nCordialement,"
    }
}

/** Règles de saisie des réglages (messages affichés sous les champs). */
object SettingsRules {
    private val EMAIL = Regex("""^[A-Za-z0-9._%+'\-]+@[A-Za-z0-9\-]+(\.[A-Za-z0-9\-]+)*\.[A-Za-z]{2,}$""")

    /** Adresses séparées par des virgules, points-virgules ou espaces. */
    fun emails(s: String): List<String> = s.split(',', ';', ' ', '\n').map { it.trim() }.filter { it.isNotEmpty() }

    fun emailsError(s: String, required: Boolean): String? {
        val list = emails(s)
        return when {
            list.isEmpty() -> if (required) "Obligatoire" else null
            list.any { !EMAIL.matches(it) } -> if (list.size == 1) "Adresse e-mail invalide" else "Une des adresses est invalide"
            else -> null
        }
    }

    fun technicienError(s: String): String? = if (s.isBlank()) "Obligatoire" else null

    fun initialesError(s: String): String? {
        val t = s.trim()
        return when {
            t.isEmpty() -> "Obligatoire"
            t.length > 6 -> "6 caractères au plus"
            t.any { !it.isLetterOrDigit() } -> "Lettres et chiffres seulement"
            else -> null
        }
    }

    /** « Chris.E » -> « CE », « Jean-Pierre Colin » -> « JPC ». */
    fun initialesFrom(name: String): String =
        name.split(Regex("[\\s.\\-_']+")).filter { it.isNotEmpty() }.take(3)
            .joinToString("") { it.take(1) }.uppercase(Locale.FRANCE)
}

/** Réglages enregistrés sur la tablette (préférences privées de l'application). */
class SettingsStore(context: Context) {
    private val prefs = context.getSharedPreferences("reglages", Context.MODE_PRIVATE)

    fun load() = Settings(
        technicien = prefs.getString("technicien", "").orEmpty(),
        initiales = prefs.getString("initiales", "").orEmpty(),
        emailCompta = prefs.getString("emailCompta", "").orEmpty(),
        emailCopie = prefs.getString("emailCopie", "").orEmpty(),
        message = prefs.getString("message", null) ?: Settings.DEFAULT_MESSAGE,
        setupDone = prefs.getBoolean("setupDone", false),
    )

    /** Écriture immédiate sur le disque ; renvoie false si elle a échoué. */
    fun save(s: Settings): Boolean {
        val t = s.trimmed()
        return prefs.edit()
            .putString("technicien", t.technicien)
            .putString("initiales", t.initiales)
            .putString("emailCompta", t.emailCompta)
            .putString("emailCopie", t.emailCopie)
            .putString("message", t.message)
            .putBoolean("setupDone", t.setupDone)
            .commit()
    }
}
