package fr.vippneus.intervention.data

import fr.vippneus.intervention.pdf.PlKeys
import fr.vippneus.intervention.pdf.PlTemplate
import fr.vippneus.intervention.pdf.TyreTable

/**
 * Pneus montés et démontés de la fiche poids lourds, rangés par position (roue du schéma).
 * Une roue touchée sur le schéma est cochée sur la fiche, même sans pneu monté ni démonté
 * (réparation, contrôle…) ; une roue qui a un pneu dans un des tableaux l'est aussi.
 */
object PlTyres {
    const val POSITION = "position"

    /** Informations d'un pneu, sans sa position : dimensions, marque, matricule, usure. */
    val infoColumns = PlTemplate.tyreColumns.map { it.first } - POSITION

    private val columns = PlTemplate.tyreColumns.map { it.first }
    private val rows = 1..PlTemplate.TYRE_ROWS

    fun key(table: TyreTable, row: Int, col: String) = PlKeys.tyre(table, row, col)

    fun info(i: Intervention, table: TyreTable, row: Int): Map<String, String> =
        infoColumns.associateWith { i.value(key(table, row, it)) }

    fun position(i: Intervention, table: TyreTable, row: Int) = i.value(key(table, row, POSITION))

    private fun isEmpty(i: Intervention, table: TyreTable, row: Int) = columns.all { i.value(key(table, row, it)).isBlank() }

    /** Lignes remplies d'un tableau, dans l'ordre de la fiche. */
    fun filledRows(i: Intervention, table: TyreTable): List<Int> = rows.filterNot { isEmpty(i, table, it) }

    /** Ligne du tableau qui porte cette position. */
    fun find(i: Intervention, table: TyreTable, position: String): Int? {
        if (position.isBlank()) return null
        val p = PlTemplate.samePosition(position)
        return rows.firstOrNull { PlTemplate.samePosition(position(i, table, it)) == p }
    }

    /** Roue cochée sur le schéma (touchée par le technicien). */
    fun isChecked(i: Intervention, position: String) = i.value(PlKeys.wheel(position)).isNotBlank()

    /** Roues à cocher sur la fiche : touchées sur le schéma, ou avec un pneu dans un des tableaux. */
    fun usedPositions(i: Intervention): Set<String> = PlTemplate.checkedWheels(i.values).mapTo(linkedSetOf()) { it.position }

    /**
     * Enregistre la saisie d'une roue (fenêtre du schéma) : pneu monté et pneu démonté.
     * Un pneu sans aucune information retire sa ligne ; sinon il va sur sa ligne ([mountedRow], [dismountedRow])
     * ou sur la première ligne libre. Null si un tableau est déjà plein.
     */
    fun save(
        i: Intervention,
        position: String,
        previousPosition: String?,
        mounted: Map<String, String>,
        mountedRow: Int?,
        dismounted: Map<String, String>,
        dismountedRow: Int?,
    ): Intervention? {
        var cur = i
        for ((table, info, row) in listOf(Triple(TyreTable.MONTES, mounted, mountedRow), Triple(TyreTable.DEMONTES, dismounted, dismountedRow))) {
            cur = if (info.values.all { it.isBlank() }) {
                if (row != null) clear(cur, table, row) else cur
            } else {
                put(cur, table, row, info + (POSITION to position.trim())) ?: return null
            }
        }
        var values = cur.values
        if (previousPosition != null && PlTemplate.samePosition(previousPosition) != PlTemplate.samePosition(position)) {
            values = values - PlKeys.wheel(previousPosition)
        }
        PlTemplate.wheel(position)?.let { values = values + (PlKeys.wheel(it.position) to "x") }
        return cur.copy(values = values)
    }

    /**
     * Même saisie pour plusieurs roues (sélection sur le schéma) : chaque roue est cochée, et les cases
     * remplies remplacent les siennes ; une case laissée vide garde ce qui était déjà saisi (matricule…).
     * Null si un tableau est plein (rien n'est alors enregistré).
     */
    fun saveMany(i: Intervention, positions: List<String>, mounted: Map<String, String>, dismounted: Map<String, String>): Intervention? {
        var cur = i
        for (p in positions) {
            val mountedRow = find(cur, TyreTable.MONTES, p)
            val dismountedRow = find(cur, TyreTable.DEMONTES, p)
            fun merge(table: TyreTable, row: Int?, typed: Map<String, String>) = infoColumns.associateWith { col ->
                typed[col]?.trim()?.takeIf { it.isNotEmpty() } ?: row?.let { cur.value(key(table, it, col)) }.orEmpty()
            }
            cur = save(
                cur, p, p,
                merge(TyreTable.MONTES, mountedRow, mounted), mountedRow,
                merge(TyreTable.DEMONTES, dismountedRow, dismounted), dismountedRow,
            ) ?: return null
        }
        return cur
    }

    /** Dernier pneu saisi sur une autre roue que [current] (« Même pneu que la roue précédente »). */
    fun previous(i: Intervention, table: TyreTable, current: String): Map<String, String>? =
        filledRows(i, table)
            .lastOrNull { PlTemplate.samePosition(position(i, table, it)) != PlTemplate.samePosition(current) }
            ?.let { info(i, table, it) }

    /** Retire une roue : décochée, ses pneus montés et démontés effacés. */
    fun remove(i: Intervention, position: String): Intervention {
        var cur = i
        for (t in TyreTable.entries) {
            while (true) cur = clear(cur, t, find(cur, t, position) ?: break)
        }
        val wheel = PlTemplate.wheel(position)?.position ?: position
        return cur.copy(values = cur.values - PlKeys.wheel(wheel))
    }

    /** Écrit une ligne (sur [row] ou la première ligne libre) ; null si le tableau est plein. */
    private fun put(i: Intervention, table: TyreTable, row: Int?, cells: Map<String, String>): Intervention? {
        val target = row ?: rows.firstOrNull { isEmpty(i, table, it) } ?: return null
        var values = i.values
        for (col in columns) {
            val v = cells[col]?.trim().orEmpty()
            values = if (v.isEmpty()) values - key(table, target, col) else values + (key(table, target, col) to v)
        }
        return i.copy(values = values)
    }

    /** Efface une ligne : les suivantes remontent (valeurs, valeurs lues, ajustements). */
    fun clear(i: Intervention, table: TyreTable, row: Int): Intervention {
        fun <V> shift(map: Map<String, V>): Map<String, V> {
            val out = map.toMutableMap()
            for (r in row..PlTemplate.TYRE_ROWS) {
                for (col in columns) {
                    val v = if (r < PlTemplate.TYRE_ROWS) map[key(table, r + 1, col)] else null
                    if (v == null) out.remove(key(table, r, col)) else out[key(table, r, col)] = v
                }
            }
            return out
        }
        return i.copy(values = shift(i.values), autoValues = shift(i.autoValues), adjust = shift(i.adjust))
    }
}
