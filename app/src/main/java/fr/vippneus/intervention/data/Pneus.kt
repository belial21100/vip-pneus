package fr.vippneus.intervention.data

import fr.vippneus.intervention.pdf.FpsTemplate
import fr.vippneus.intervention.pdf.FpsTemplate.K

/**
 * Lignes du tableau « Fournitures » de la fiche : AV, AR, puis les lignes à préciser
 * (autres essieux, roues intérieures), ajoutées à la demande.
 */
object Pneus {
    /** Cases d'une ligne, sans l'essieu : dimensions, marque, profil, type, quantité. */
    private val columns = FpsTemplate.pneuColumns.map { it.first }

    /** Toutes les cases d'une ligne (suffixes des clés). */
    private val suffixes = listOf("essieu", "fourni") + columns

    /** La ligne décrit des pneus (dimensions, marque, quantité…). */
    fun hasInfo(i: Intervention, row: String) = columns.any { i.value(K.pneu(row, it)).isNotBlank() }

    private fun used(i: Intervention, row: String) = suffixes.any { i.value(K.pneu(row, it)).isNotBlank() }

    /** Nombre de lignes à préciser occupées (jusqu'à la dernière remplie). */
    fun extraCount(i: Intervention): Int = FpsTemplate.extraPneuRows.indexOfLast { used(i, it.key) } + 1

    /** Recopie une ligne sur une autre : pneus et « fournis » (l'essieu reste celui de la ligne). */
    fun copy(i: Intervention, from: String, to: String): Intervention {
        var values = i.values
        for (s in columns + "fourni") {
            val v = i.value(K.pneu(from, s))
            values = if (v.isEmpty()) values - K.pneu(to, s) else values + (K.pneu(to, s) to v)
        }
        return i.copy(values = values)
    }

    /** Retire une ligne à préciser : les suivantes remontent (valeurs, valeurs lues, ajustements). */
    fun remove(i: Intervention, row: String): Intervention {
        val rows = FpsTemplate.extraPneuRows.map { it.key }
        val from = rows.indexOf(row)
        if (from < 0) return i
        fun <V> shift(map: Map<String, V>): Map<String, V> {
            val out = map.toMutableMap()
            for (k in from until rows.size) {
                val next = rows.getOrNull(k + 1)
                for (s in suffixes) {
                    val v = next?.let { map[K.pneu(it, s)] }
                    if (v == null) out.remove(K.pneu(rows[k], s)) else out[K.pneu(rows[k], s)] = v
                }
            }
            return out
        }
        return i.copy(values = shift(i.values), autoValues = shift(i.autoValues), adjust = shift(i.adjust))
    }
}
