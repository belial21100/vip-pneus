package fr.vippneus.intervention

import fr.vippneus.intervention.data.FieldAdjust
import fr.vippneus.intervention.data.SignatureData
import fr.vippneus.intervention.pdf.CrossOp
import fr.vippneus.intervention.pdf.FpsLayout
import fr.vippneus.intervention.pdf.FpsTemplate
import fr.vippneus.intervention.pdf.FpsTemplate.K
import fr.vippneus.intervention.pdf.SignatureOp
import fr.vippneus.intervention.pdf.TextLayout
import fr.vippneus.intervention.pdf.TextMeasure
import fr.vippneus.intervention.pdf.TextOp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LayoutTest {
    /** Mesure approximative : un caractère = 0,5 corps. */
    private val m = TextMeasure { text, size -> text.length * size * 0.5f }

    @Test
    fun texteCourt_tailleParDefaut() {
        val f = FpsTemplate.fieldsByKey.getValue(K.NUMERO_COMMANDE)
        val op = TextLayout.layoutField(f, "1234567", null, m)
        assertEquals(f.fontSize, op.size, 0.01f)
        assertEquals(1, op.lines.size)
        assertTrue(op.lines[0].x >= f.box.left - 0.01f)
    }

    @Test
    fun texteLong_reduitPourTenirDansLaCase() {
        val f = FpsTemplate.fieldsByKey.getValue(K.MARQUE)
        val op = TextLayout.layoutField(f, "Une marque au nom vraiment très long", null, m)
        assertTrue(op.size < f.fontSize)
        assertTrue(op.lines.all { m.width(it.text, op.size) <= f.box.width + 0.01f || op.size == f.minFontSize })
        // centré
        val line = op.lines[0]
        assertEquals(f.box.centerX, line.x + line.width / 2f, 0.5f)
    }

    @Test
    fun champMultiligne_passeSurDeuxLignes() {
        val f = FpsTemplate.fieldsByKey.getValue(K.OBSERVATIONS)
        val text = "Pneu arrière gauche usé jusqu'à la toile, remplacement conseillé rapidement. " +
            "Jante avant droite légèrement voilée, à surveiller lors de la prochaine intervention sur ce chariot."
        val op = TextLayout.layoutField(f, text, null, m)
        assertTrue(op.lines.size in 1..2)
        assertTrue(op.lines.all { it.width <= f.box.width + 0.01f })
    }

    @Test
    fun ajustementManuel_deplaceEtGrossit() {
        val f = FpsTemplate.fieldsByKey.getValue(K.MONTEUR)
        val base = TextLayout.layoutField(f, "JPC", null, m)
        val moved = TextLayout.layoutField(f, "JPC", FieldAdjust(dx = 10f, dy = -5f, fontSize = 30f), m)
        assertEquals(base.lines[0].x + 10f, moved.lines[0].x, 0.01f)
        assertEquals(30f, moved.size, 0.01f)
    }

    @Test
    fun casesACocher_etSignature() {
        val sig = SignatureData(listOf(listOf(0f, 0f, 100f, 40f, 200f, 10f)), 300f, 100f, 6f)
        val ops = FpsLayout.build(
            mapOf(K.AV_FOURNI to "oui", K.DEPLACEMENT to "non", K.CLIENT_MANDATAIRE to "Loc Test"),
            sig, emptyMap(), m,
        )
        val crosses = ops.filterIsInstance<CrossOp>()
        assertEquals(2, crosses.size)
        assertEquals(1, ops.filterIsInstance<TextOp>().size)
        val s = ops.filterIsInstance<SignatureOp>().single()
        val expected = FpsTemplate.signatureBox
        assertEquals(expected.left, s.box.left, 0.01f)
        assertEquals(expected.top, s.box.top, 0.01f)
        assertEquals(expected.right, s.box.right, 0.01f)
        assertEquals(expected.bottom, s.box.bottom, 0.01f)
    }

    @Test
    fun motTropLong_coupe() {
        val lines = TextLayout.wrap("ABCDEFGHIJKLMNOPQRSTUVWXYZ", 50f, 10f, m)
        assertTrue(lines.size > 1)
        assertTrue(lines.all { m.width(it, 10f) <= 50f })
        assertEquals("ABCDEFGHIJKLMNOPQRSTUVWXYZ", lines.joinToString(""))
    }
}
