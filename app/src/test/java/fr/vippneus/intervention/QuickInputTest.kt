package fr.vippneus.intervention

import fr.vippneus.intervention.data.DimensionInput
import fr.vippneus.intervention.data.Intervention
import fr.vippneus.intervention.data.InterventionType
import fr.vippneus.intervention.data.PlTyres
import fr.vippneus.intervention.data.Qty
import fr.vippneus.intervention.data.Suggestions
import fr.vippneus.intervention.pdf.FpsTemplate.K
import fr.vippneus.intervention.pdf.PlKeys
import fr.vippneus.intervention.pdf.PlTemplate
import fr.vippneus.intervention.pdf.TyreTable
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Saisie rapide : quantités, pavé des dimensions, valeurs les plus utilisées, plusieurs roues d'un coup. */
class QuickInputTest {
    private fun fps(updatedAt: Long, vararg values: Pair<String, String>) =
        Intervention("f$updatedAt", InterventionType.FPS, 0L, updatedAt = updatedAt, values = mapOf(*values))

    private fun pl(vararg values: Pair<String, String>) = Intervention("pl", InterventionType.PL, 0L, values = mapOf(*values))

    @Test
    fun quantites_plusEtMoins() {
        assertEquals("1", Qty.step("", 1))
        assertEquals("4", Qty.step(" 3 ", 1))
        assertEquals("", Qty.step("1", -1))
        assertEquals("", Qty.step("", -1))
        // Texte libre : inchangé, les boutons ne s'appliquent pas
        assertEquals("1,5 h", Qty.step("1,5 h", 1))
        assertTrue(Qty.isCount("") && Qty.isCount("12"))
        assertFalse(Qty.isCount("1,5"))
    }

    @Test
    fun paveDesDimensions() {
        fun typeAll(keys: List<String>) = keys.fold("") { v, k -> DimensionInput.type(v, k) }
        assertEquals("315/80 R22.5", typeAll("315/80".map { it.toString() } + "R" + "22.5".map { it.toString() }))
        assertEquals("22x12x16", typeAll("22x12x16".map { it.toString() }))
        // Espace déjà là : pas d'espace en double avant le R
        assertEquals("18x8x12 1/8", typeAll("18x8x12".map { it.toString() } + " " + " " + "1/8".map { it.toString() }))
        assertEquals("R", DimensionInput.type("", "R"))
        assertEquals("315/80 R", DimensionInput.type("315/80 ", "R"))
        assertEquals(17, DimensionInput.ROWS.flatten().size + 2)
    }

    @Test
    fun lesPlusUtiliseesDAbord() {
        val all = Suggestions.build(
            listOf(
                fps(1, K.pneu("av", "dimensions") to "22x12x16", K.pneu("ar", "dimensions") to "22x12x16"),
                fps(2, K.pneu("av", "dimensions") to "18x7x8"),
                fps(3, K.pneu("av", "dimensions") to "28x9-15"),
            ),
        )
        // 22x12x16 utilisée deux fois ; à égalité, la plus récente d'abord
        assertEquals(listOf("22x12x16", "28x9-15", "18x7x8"), Suggestions.top(all, K.pneu("sup1", "dimensions")))
        assertEquals(listOf("22x12x16"), Suggestions.top(all, PlKeys.tyre(TyreTable.MONTES, 1, "dimensions"), max = 1))
    }

    @Test
    fun marquesProposeesSansHistorique() {
        val top = Suggestions.top(emptyMap(), K.pneu("av", "marque"))
        assertEquals(Suggestions.MARQUES, top)
        val all = Suggestions.build(listOf(fps(1, K.pneu("av", "marque") to "Solideal", K.pneu("ar", "marque") to "michelin")))
        // L'historique d'abord, sans doublon avec la liste de départ
        val withHistory = Suggestions.top(all, K.pneu("av", "marque"))
        assertEquals("Solideal", withHistory[0])
        assertEquals(1, withHistory.count { it.equals("Michelin", ignoreCase = true) })
        assertTrue(Suggestions.top(all, K.MONTEUR).isEmpty())
    }

    @Test
    fun plusieursRouesDUnCoup() {
        val tyre = mapOf("dimensions" to "315/80 R22.5", "marque" to "Michelin", "matricule" to "", "usure" to "")
        val blank = mapOf("dimensions" to "", "marque" to "", "matricule" to "", "usure" to "")
        // 2 AR G ext a déjà un pneu monté avec son matricule
        var i = PlTyres.save(pl(), "2 AR G ext", "2 AR G ext", tyre + ("matricule" to "MX1") + ("usure" to "16"), null, blank, null)!!
        val axle = PlTemplate.vehicles[0].axles.first { it.label == "2 AR" }.wheels.map { it.position }
        i = PlTyres.saveMany(i, axle, mapOf("dimensions" to "315/70 R22.5", "marque" to "", "usure" to ""), mapOf("usure" to "3"))!!
        for (p in axle) {
            val m = PlTyres.find(i, TyreTable.MONTES, p)!!
            assertEquals(p, "315/70 R22.5", i.value(PlKeys.tyre(TyreTable.MONTES, m, "dimensions")))
            assertEquals(p, "3", i.value(PlKeys.tyre(TyreTable.DEMONTES, PlTyres.find(i, TyreTable.DEMONTES, p)!!, "usure")))
            assertTrue(PlTyres.isChecked(i, p))
        }
        // Les cases vides gardent ce qui était saisi : marque et matricule de la roue déjà remplie
        val first = PlTyres.find(i, TyreTable.MONTES, "2 AR G ext")!!
        assertEquals("Michelin", i.value(PlKeys.tyre(TyreTable.MONTES, first, "marque")))
        assertEquals("MX1", i.value(PlKeys.tyre(TyreTable.MONTES, first, "matricule")))
        assertEquals("16", i.value(PlKeys.tyre(TyreTable.MONTES, first, "usure")))
        // Roues seulement cochées : rien d'écrit dans les tableaux
        val checked = PlTyres.saveMany(pl(), listOf("1 AV G", "1 AV D"), blank, blank)!!
        assertEquals(emptyList<Int>(), PlTyres.filledRows(checked, TyreTable.MONTES))
        assertEquals(setOf("1 AV G", "1 AV D"), PlTyres.usedPositions(checked))
    }

    @Test
    fun plusieursRoues_tableauPleinRienNEstEnregistre() {
        val tyre = mapOf("dimensions" to "315/80 R22.5", "marque" to "", "matricule" to "", "usure" to "")
        val positions = PlTemplate.wheels.map { it.position }
        val i = pl()
        assertNull(PlTyres.saveMany(i, positions.take(PlTemplate.TYRE_ROWS + 1), tyre, emptyMap()))
        assertTrue(PlTyres.saveMany(i, positions.take(PlTemplate.TYRE_ROWS), tyre, emptyMap()) != null)
    }

    @Test
    fun memePneuQueLaRouePrecedente() {
        val blank = mapOf("dimensions" to "", "marque" to "", "matricule" to "", "usure" to "")
        var i = PlTyres.save(pl(), "1 AR G ext", "1 AR G ext", mapOf("dimensions" to "315/80 R22.5", "marque" to "Michelin", "matricule" to "", "usure" to ""), null, blank, null)!!
        i = PlTyres.save(i, "1 AR G int", "1 AR G int", mapOf("dimensions" to "295/80 R22.5", "marque" to "Conti", "matricule" to "", "usure" to ""), null, blank, null)!!
        assertEquals("295/80 R22.5", PlTyres.previous(i, TyreTable.MONTES, "2 AR D ext")?.get("dimensions"))
        // Pour la roue elle-même : la précédente est l'autre
        assertEquals("Michelin", PlTyres.previous(i, TyreTable.MONTES, "1 AR G int")?.get("marque"))
        assertNull(PlTyres.previous(i, TyreTable.DEMONTES, "2 AR D ext"))
    }
}
