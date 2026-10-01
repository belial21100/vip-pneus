package fr.vippneus.intervention.data

/** Quantités saisies avec les boutons − et + (le texte libre reste possible au clavier). */
object Qty {
    /** Ajoute [delta] à une quantité entière ; vide à zéro ; un texte libre (« 1,5 h ») n'est pas modifié. */
    fun step(value: String, delta: Int): String {
        val t = value.trim()
        val n = if (t.isEmpty()) 0 else t.toIntOrNull() ?: return value
        val r = n + delta
        return if (r <= 0) "" else r.toString()
    }

    /** La quantité peut se changer avec − et + (vide ou nombre entier). */
    fun isCount(value: String): Boolean = value.isBlank() || value.trim().toIntOrNull() != null
}

/** Pavé des dimensions : touches et frappe (« 315/80 » + « R » -> « 315/80 R »). */
object DimensionInput {
    /** Touches, ligne par ligne (la dernière ligne « 0 / espace / effacer » est à part). */
    val ROWS = listOf(
        listOf("7", "8", "9", "/", "R"),
        listOf("4", "5", "6", "x", "-"),
        listOf("1", "2", "3", ",", "."),
    )

    fun type(value: String, key: String): String = when (key) {
        // « 315/80 R22.5 » : espace avant le R s'il manque
        "R" -> if (value.isNotEmpty() && !value.endsWith(" ")) "$value R" else value + "R"
        " " -> if (value.isEmpty() || value.endsWith(" ")) value else "$value "
        else -> value + key
    }
}
