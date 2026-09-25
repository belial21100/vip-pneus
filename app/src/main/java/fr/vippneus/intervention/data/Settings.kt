package fr.vippneus.intervention.data

import android.content.Context

data class Settings(
    /** Nom écrit dans « Commercial / Monteur » (ex. « Chris.E »). */
    val technicien: String = "",
    /** Initiales ajoutées à la fin du nom des fichiers (ex. « CE »). */
    val initiales: String = "",
    val emailCompta: String = "",
    val emailCopie: String = "",
    val message: String = DEFAULT_MESSAGE,
) {
    companion object {
        const val DEFAULT_MESSAGE = "Bonjour,\n\nVeuillez trouver ci-joint le bon d'intervention.\n\nCordialement,"
    }
}

class SettingsStore(context: Context) {
    private val prefs = context.getSharedPreferences("reglages", Context.MODE_PRIVATE)

    fun load() = Settings(
        technicien = prefs.getString("technicien", "").orEmpty(),
        initiales = prefs.getString("initiales", "").orEmpty(),
        emailCompta = prefs.getString("emailCompta", "").orEmpty(),
        emailCopie = prefs.getString("emailCopie", "").orEmpty(),
        message = prefs.getString("message", null) ?: Settings.DEFAULT_MESSAGE,
    )

    fun save(s: Settings) {
        prefs.edit()
            .putString("technicien", s.technicien.trim())
            .putString("initiales", s.initiales.trim())
            .putString("emailCompta", s.emailCompta.trim())
            .putString("emailCopie", s.emailCopie.trim())
            .putString("message", s.message)
            .apply()
    }
}
