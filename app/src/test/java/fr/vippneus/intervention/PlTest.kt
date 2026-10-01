package fr.vippneus.intervention

import fr.vippneus.intervention.data.Completion
import fr.vippneus.intervention.data.Intervention
import fr.vippneus.intervention.data.InterventionType
import fr.vippneus.intervention.data.Naming
import fr.vippneus.intervention.data.PlTyres
import fr.vippneus.intervention.data.SignatureData
import fr.vippneus.intervention.data.Suggestions
import fr.vippneus.intervention.pdf.CrossOp
import fr.vippneus.intervention.pdf.FpsTemplate.K
import fr.vippneus.intervention.pdf.PlKeys
import fr.vippneus.intervention.pdf.PlTemplate
import fr.vippneus.intervention.pdf.SheetLayout
import fr.vippneus.intervention.pdf.TextMeasure
import fr.vippneus.intervention.pdf.TextOp
import fr.vippneus.intervention.pdf.TyreTable
import fr.vippneus.intervention.pdf.sheet
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Fiche poids lourds : gabarit, roues du schéma, pneus montés / démontés, liste « À compléter ». */
class PlTest {
    private val m = TextMeasure { text, size -> text.length * size * 0.5f }

    private fun pl(vararg values: Pair<String, String>) = Intervention("pl", InterventionType.PL, 0L, values = mapOf(*values))

    private fun mounted(dims: String, brand: String = "Michelin") =
        mapOf("dimensions" to dims, "marque" to brand, "matricule" to "", "usure" to "")

    private val none = mapOf("dimensions" to "", "marque" to "", "matricule" to "", "usure" to "")

    @Test
    fun gabarit_casesDansLaPageSansChevauchement() {
        assertEquals(PlTemplate, InterventionType.PL.sheet)
        val boxes = PlTemplate.fields.map { it.key to it.box }
        assertEquals("clés uniques", boxes.size, boxes.map { it.first }.toSet().size)
        for ((key, b) in boxes) {
            assertTrue(key, b.left >= 0f && b.top >= 0f && b.right <= PlTemplate.pageW && b.bottom <= PlTemplate.pageH && b.width > 0f && b.height > 0f)
        }
        for ((n, a) in boxes.withIndex()) {
            for (b in boxes.drop(n + 1)) {
                val overlap = a.second.left < b.second.right && b.second.left < a.second.right &&
                    a.second.top < b.second.bottom && b.second.top < a.second.bottom
                assertFalse("${a.first} / ${b.first}", overlap)
            }
        }
        // 8 lignes de pneus montés et 8 de démontés, 9 fournitures, 15 services
        assertEquals(2 * 8 * 5, PlTemplate.fields.count { Regex("""^pl\.[md]\d\.""").containsMatchIn(it.key) })
        assertEquals(9, PlTemplate.fournitures.size)
        assertEquals(15, PlTemplate.services.size)
    }

    @Test
    fun schema_rouesDeLaFiche() {
        // Porteur : 1 AV, 2 AV (2 roues), 1 AR à 3 AR (jumelées), roue de secours ; remorque : 1 à 3 ESS, roue de secours
        assertEquals(2 + 2 + 3 * 4 + 1 + 3 * 4 + 1, PlTemplate.wheels.size)
        assertEquals(PlTemplate.wheels.size, PlTemplate.wheels.map { it.position }.toSet().size)
        for (w in PlTemplate.wheels) assertTrue(w.position, w.box.left > 100f && w.box.right < 185f && w.box.top > 450f && w.box.bottom < 570f)
        assertEquals("2 AR D ext", PlTemplate.wheel("2 ar  d EXT")?.position)
        assertNull(PlTemplate.wheel("roue avant"))
    }

    @Test
    fun rouesCocheesSurLaPage() {
        val values = mapOf(
            PlKeys.tyre(TyreTable.MONTES, 1, "position") to "2 AR D ext",
            PlKeys.tyre(TyreTable.DEMONTES, 1, "position") to "2 ar d ext",
            PlKeys.wheel("1 ESS G int") to "x",
            PlKeys.tyre(TyreTable.MONTES, 2, "position") to "Stock atelier",
        )
        val ops = SheetLayout.build(PlTemplate, values, null, emptyMap(), m)
        val crosses = ops.filterIsInstance<CrossOp>()
        assertEquals(setOf(PlKeys.wheel("2 AR D ext"), PlKeys.wheel("1 ESS G int")), crosses.map { it.key }.toSet())
        val box = PlTemplate.wheel("2 AR D ext")!!.box
        val cross = crosses.single { it.key == PlKeys.wheel("2 AR D ext") }
        assertTrue(cross.bounds.left >= box.left && cross.bounds.right <= box.right && cross.bounds.top >= box.top && cross.bounds.bottom <= box.bottom)
        // La position hors schéma s'écrit dans la colonne, sans croix
        assertTrue(ops.filterIsInstance<TextOp>().any { op -> op.lines.any { it.text == "Stock atelier" } })
        assertEquals("Roue 2 AR D ext", PlTemplate.label(PlKeys.wheel("2 AR D ext")))
    }

    @Test
    fun saisieDUneRoue_monteEtDemonte() {
        var i = pl()
        // Remplacement en 2 AR D ext : pneu neuf monté, ancien démonté
        i = PlTyres.save(
            i, "2 AR D ext", "2 AR D ext",
            mapOf("dimensions" to "315/80 R22.5", "marque" to "Michelin", "matricule" to "M123", "usure" to "16"), null,
            mapOf("dimensions" to "315/80 R22.5", "marque" to "Bridgestone", "matricule" to "", "usure" to "3"), null,
        )!!
        assertEquals("2 AR D ext", i.value(PlKeys.tyre(TyreTable.MONTES, 1, "position")))
        assertEquals("M123", i.value(PlKeys.tyre(TyreTable.MONTES, 1, "matricule")))
        assertEquals("3", i.value(PlKeys.tyre(TyreTable.DEMONTES, 1, "usure")))
        assertTrue(PlTyres.isChecked(i, "2 AR D ext"))

        // Roue seulement cochée (réparation) : aucune ligne, mais cochée
        i = PlTyres.save(i, "1 AV G", "1 AV G", none, null, none, null)!!
        assertEquals(listOf(1), PlTyres.filledRows(i, TyreTable.MONTES))
        assertEquals(setOf("1 AV G", "2 AR D ext"), PlTyres.usedPositions(i))

        // Nouvelle roue : 2e ligne ; puis on vide le pneu démonté de la 1re roue : sa ligne disparaît
        i = PlTyres.save(i, "2 AR G ext", "2 AR G ext", mounted("315/80 R22.5"), null, none, null)!!
        assertEquals(2, PlTyres.find(i, TyreTable.MONTES, "2 AR G ext"))
        i = PlTyres.save(i, "2 AR D ext", "2 AR D ext", PlTyres.info(i, TyreTable.MONTES, 1), 1, none, 1)!!
        assertEquals(emptyList<Int>(), PlTyres.filledRows(i, TyreTable.DEMONTES))

        // Retirer une roue : ses lignes partent, les suivantes remontent
        i = PlTyres.remove(i, "2 AR D ext")
        assertEquals(listOf(1), PlTyres.filledRows(i, TyreTable.MONTES))
        assertEquals("2 AR G ext", i.value(PlKeys.tyre(TyreTable.MONTES, 1, "position")))
        assertFalse(PlTyres.isChecked(i, "2 AR D ext"))
        assertFalse("2 AR D ext" in PlTyres.usedPositions(i))
    }

    @Test
    fun changerLaPositionDUnPneu() {
        var i = PlTyres.save(pl(), "1 AR G ext", "1 AR G ext", mounted("315/80 R22.5"), null, none, null)!!
        i = PlTyres.save(i, "1 AR G int", "1 AR G ext", PlTyres.info(i, TyreTable.MONTES, 1), 1, none, null)!!
        assertEquals("1 AR G int", i.value(PlKeys.tyre(TyreTable.MONTES, 1, "position")))
        assertEquals(setOf("1 AR G int"), PlTyres.usedPositions(i))
    }

    @Test
    fun tableauPlein() {
        var i = pl()
        val positions = PlTemplate.wheels.map { it.position }
        for (p in positions.take(PlTemplate.TYRE_ROWS)) i = PlTyres.save(i, p, p, mounted("315/80 R22.5"), null, none, null)!!
        assertNull(PlTyres.save(i, positions[8], positions[8], mounted("315/80 R22.5"), null, none, null))
        // Le démonté seul passe encore
        assertTrue(PlTyres.save(i, positions[8], positions[8], none, null, mounted("315/80 R22.5"), null) != null)
    }

    @Test
    fun aCompleter_ficheVidePuisComplete() {
        val empty = pl()
        assertEquals(
            listOf("N°", "Client", "Lieu du dépannage", "Véhicule", "Km / heure", "Travail effectué", "Signature du client"),
            Completion.missing(empty).map { it.label },
        )
        // Une roue touchée suffit comme travail effectué
        val roue = pl(PlKeys.wheel("1 AV G") to "x")
        assertTrue(Completion.todos(roue).single { it.key == Completion.PL_TRAVAIL }.done)
        val full = pl(
            K.NUMERO_COMMANDE to "4521", K.CLIENT_MANDATAIRE to "Transports Test", PlKeys.LIEU to "A26 sortie 13",
            K.SERIE to "AB-123-CD", K.HORAMETRE to "452 300", PlKeys.service("demMont") to "1",
        ).copy(signature = SignatureData(listOf(listOf(0f, 0f, 10f, 10f)), 100f, 50f, 3f))
        assertEquals(emptyList<String>(), Completion.missing(full).map { it.label })
    }

    @Test
    fun nomDuFichierEtListe() {
        val i = pl(
            K.DATE to "01/10/26", K.NUMERO_COMMANDE to "4521", K.CLIENT_MANDATAIRE to "Transports Test",
            K.CLIENT_UTILISATEUR to "Logistique Exemple", K.UTILISATEUR_ADRESSE to "12 rue des Essais, 51100 Reims",
            K.SERIE to "AB-123-CD",
        )
        assertEquals("Fiche poids lourds", Naming.kindLabel(i))
        assertEquals("Transports Test – Logistique Exemple", Naming.title(i))
        assertEquals("4521", Naming.reference(i))
        assertEquals("51- TRANSPORTS TEST LOGISTIQUE EXEMPLE AB-123-CD 4521 01-10-2026 CE", Naming.defaultFileName(i, "CE"))
        assertEquals("Reims", Naming.ville(i.value(K.UTILISATEUR_ADRESSE)))
        assertEquals("51100", Naming.cp(i.value(K.UTILISATEUR_ADRESSE)))
    }

    @Test
    fun propositions_dimensionsPartageesAvecLaFichePresseMobile() {
        val all = Suggestions.build(listOf(pl(PlKeys.tyre(TyreTable.MONTES, 3, "dimensions") to "315/80 R22.5", PlKeys.LIEU to "A26 sortie 13")))
        assertEquals(listOf("315/80 R22.5"), Suggestions.filter(all, K.pneu("av", "dimensions"), ""))
        assertEquals(listOf("315/80 R22.5"), Suggestions.filter(all, PlKeys.pneu("dimensions"), ""))
        assertEquals(listOf("A26 sortie 13"), Suggestions.filter(all, PlKeys.LIEU, ""))
        // Pas de propositions pour les matricules ni les fournitures
        assertEquals(emptyList<String>(), Suggestions.filter(all, PlKeys.tyre(TyreTable.MONTES, 1, "matricule"), ""))
    }
}
