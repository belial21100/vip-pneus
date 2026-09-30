package fr.vippneus.intervention

import fr.vippneus.intervention.data.Completion
import fr.vippneus.intervention.data.FieldAdjust
import fr.vippneus.intervention.data.Intervention
import fr.vippneus.intervention.data.InterventionType
import fr.vippneus.intervention.data.Pneus
import fr.vippneus.intervention.data.Suggestions
import fr.vippneus.intervention.importer.ClientDocs
import fr.vippneus.intervention.importer.TireLine
import fr.vippneus.intervention.pdf.CrossOp
import fr.vippneus.intervention.pdf.FpsLayout
import fr.vippneus.intervention.pdf.FpsTemplate
import fr.vippneus.intervention.pdf.FpsTemplate.K
import fr.vippneus.intervention.pdf.TextMeasure
import fr.vippneus.intervention.pdf.TextOp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Tableau « Fournitures » : AV, AR, puis autres essieux et roues intérieures. */
class PneusTest {
    private val m = TextMeasure { text, size -> text.length * size * 0.5f }

    private fun fiche(vararg values: Pair<String, String>) = Intervention("x", InterventionType.FPS, 0L, values = mapOf(*values))

    @Test
    fun gabarit_sixLignesSansChevauchement() {
        assertEquals(listOf("av", "ar", "sup1", "sup2", "sup3", "sup4"), FpsTemplate.pneuRows.map { it.key })
        val boxes = FpsTemplate.pneuRows.map { row ->
            FpsTemplate.pneuColumns.map { (col, _, _) -> FpsTemplate.fieldsByKey.getValue(K.pneu(row.key, col)).box }
        }
        // Lignes les unes sous les autres, colonnes de gauche à droite, sans se toucher
        boxes.zipWithNext().forEach { (a, b) -> assertTrue(a.first().bottom < b.first().top) }
        boxes.forEach { row -> row.zipWithNext().forEach { (a, b) -> assertTrue(a.right < b.left) } }
        // L'essieu ne se saisit que sur les lignes à préciser (AV et AR sont imprimés)
        assertNull(FpsTemplate.fieldsByKey[K.essieu("av")])
        FpsTemplate.extraPneuRows.forEach { assertTrue(FpsTemplate.fieldsByKey[K.essieu(it.key)] != null) }
        // Une case « Fournis Oui / Non » par ligne
        FpsTemplate.pneuRows.forEach { row -> assertTrue(FpsTemplate.choices.any { it.key == K.fourni(row.key) }) }
        // Les champs de la fiche ne se chevauchent pas avec « Prestations » ni « Matériel »
        val table = FpsTemplate.pneuRows.flatMap { r -> FpsTemplate.pneuColumns.map { FpsTemplate.fieldsByKey.getValue(K.pneu(r.key, it.first)).box } }
        assertTrue(table.all { it.top > FpsTemplate.fieldsByKey.getValue(K.HORAMETRE).box.bottom })
        assertTrue(table.all { it.bottom < FpsTemplate.fieldsByKey.getValue(K.prestation("depose", "8")).box.top })
    }

    @Test
    fun ligneAjoutee_essieuEtCroixSurSaLigne() {
        val ops = FpsLayout.build(
            mapOf(
                K.essieu("sup1") to "AR int.",
                K.pneu("sup1", "dimensions") to "315/80 R22.5",
                K.fourni("sup1") to "non",
            ),
            null, emptyMap(), m,
        )
        val essieu = ops.filterIsInstance<TextOp>().single { it.key == K.essieu("sup1") }
        val dims = ops.filterIsInstance<TextOp>().single { it.key == K.pneu("sup1", "dimensions") }
        val cross = ops.filterIsInstance<CrossOp>().single()
        val row = FpsTemplate.fieldsByKey.getValue(K.pneu("sup1", "dimensions")).box
        for (y in listOf(essieu.bounds.centerY, dims.bounds.centerY, cross.cy)) assertTrue(y > row.top && y < row.bottom)
        // « Non » : la case de droite
        val (oui, non) = FpsTemplate.choices.single { it.key == K.fourni("sup1") }.options
        assertEquals(non.cx, cross.cx, 0.01f)
        assertTrue(oui.cx < non.cx)
    }

    @Test
    fun import_uneLigneParEssieuEtParDimension() {
        fun t(qty: Int, axle: String?, dims: String, brand: String = "Michelin") = TireLine(qty, axle, dims, null, brand, "XZE", "Pneumatique")
        val v = ClientDocs.tireValues(
            listOf(
                t(2, "av", "315/80 R22.5"),
                t(4, "ar", "315/80 R22.5"),
                t(2, "ar", "295/80 R22.5", "Conti"),
                t(4, "Essieu 3", "315/80 R22.5"),
            ),
        )
        assertEquals("2", v[K.pneu("av", "quantite")])
        assertEquals("4", v[K.pneu("ar", "quantite")])
        assertEquals("315/80 R22.5", v[K.pneu("ar", "dimensions")])
        // Autre dimension sur l'arrière : ligne ajoutée, essieu AR
        assertEquals("AR", v[K.essieu("sup1")])
        assertEquals("295/80 R22.5", v[K.pneu("sup1", "dimensions")])
        assertEquals("Conti", v[K.pneu("sup1", "marque")])
        assertEquals("2", v[K.pneu("sup1", "quantite")])
        // 3e essieu
        assertEquals("Essieu 3", v[K.essieu("sup2")])
        assertEquals("4", v[K.pneu("sup2", "quantite")])
        assertNull(v[K.pneu("sup3", "dimensions")])
        assertEquals("ar", ClientDocs.axleOfPosition(2))
        assertEquals("Essieu 4", ClientDocs.axleOfPosition(4))
    }

    @Test
    fun recopierEtRetirerUneLigne() {
        val i = fiche(
            K.pneu("ar", "dimensions") to "315/80 R22.5", K.pneu("ar", "quantite") to "2", K.fourni("ar") to "oui",
            K.essieu("sup1") to "Essieu 3", K.pneu("sup1", "dimensions") to "385/65 R22.5",
            K.essieu("sup2") to "AR int.", K.pneu("sup2", "quantite") to "2",
        ).copy(
            autoValues = mapOf(K.pneu("sup2", "quantite") to "2"),
            adjust = mapOf(K.pneu("sup2", "quantite") to FieldAdjust(dx = 3f)),
        )
        assertEquals(2, Pneus.extraCount(i))

        // « Recopier la ligne du dessus » : pneus et « fournis », l'essieu reste
        val copied = Pneus.copy(i, "ar", "sup2")
        assertEquals("315/80 R22.5", copied.value(K.pneu("sup2", "dimensions")))
        assertEquals("oui", copied.value(K.fourni("sup2")))
        assertEquals("AR int.", copied.value(K.essieu("sup2")))

        // Retirer la ligne « Essieu 3 » : « AR int. » remonte, avec sa valeur lue et son ajustement
        val removed = Pneus.remove(i, "sup1")
        assertEquals(1, Pneus.extraCount(removed))
        assertEquals("AR int.", removed.value(K.essieu("sup1")))
        assertEquals("", removed.value(K.pneu("sup1", "dimensions")))
        assertEquals("2", removed.value(K.pneu("sup1", "quantite")))
        assertTrue(removed.isAuto(K.pneu("sup1", "quantite")))
        assertEquals(3f, removed.adjust.getValue(K.pneu("sup1", "quantite")).dx, 0.01f)
        assertFalse(removed.values.keys.any { it.startsWith("sup2.") })
        assertTrue(removed.adjust.keys.none { it.startsWith("sup2.") })
    }

    @Test
    fun ligneAjoutee_essieuAPreciser() {
        val sansEssieu = fiche(K.pneu("sup1", "dimensions") to "315/80 R22.5")
        val todo = Completion.todos(sansEssieu).single { it.key == K.essieu("sup1") }
        assertFalse(todo.done)
        assertEquals("Essieu de la ligne 3", todo.label)
        // Les pneus de la ligne ajoutée comptent pour « Pneus »
        assertTrue(Completion.todos(sansEssieu).single { it.label == "Pneus" }.done)
        assertTrue(K.essieu("sup1") in Completion.missingFields(sansEssieu))

        val avecEssieu = sansEssieu.copy(values = sansEssieu.values + (K.essieu("sup1") to "Essieu 3"))
        assertTrue(Completion.todos(avecEssieu).single { it.key == K.essieu("sup1") }.done)
        // Ligne vide : rien à préciser
        assertTrue(Completion.todos(fiche()).none { it.key == K.essieu("sup1") })
    }

    @Test
    fun propositionsPourLEssieu() {
        val all = Suggestions.build(listOf(fiche(K.essieu("sup1") to "Remorque AR")))
        val proposals = Suggestions.filter(all, K.essieu("sup3"), "")
        assertEquals("Remorque AR", proposals.first())
        assertTrue(proposals.containsAll(listOf("Essieu 3", "AR int.")))
        assertEquals(listOf("Essieu 3", "Essieu 3 int."), Suggestions.filter(all, K.essieu("sup2"), "3"))
        // Dimensions : partagées entre toutes les lignes
        val dims = Suggestions.build(listOf(fiche(K.pneu("sup2", "dimensions") to "315/80 R22.5")))
        assertEquals(listOf("315/80 R22.5"), Suggestions.filter(dims, K.pneu("av", "dimensions"), ""))
    }
}
