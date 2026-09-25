package fr.vippneus.intervention

import fr.vippneus.intervention.data.DocKeys
import fr.vippneus.intervention.data.Intervention
import fr.vippneus.intervention.data.InterventionType
import fr.vippneus.intervention.data.Recap
import fr.vippneus.intervention.data.SignatureData
import fr.vippneus.intervention.pdf.FpsTemplate.K
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.YearMonth

/** Récapitulatif mensuel pour la comptabilité (tableur). */
class RecapTest {
    private val sig = SignatureData(listOf(listOf(0f, 0f, 10f, 10f)), 100f, 50f, 3f)

    private val envoyee = Intervention(
        "a", InterventionType.FPS, 1L, updatedAt = 10L, sentAt = 20L,
        values = mapOf(
            K.DATE to "03/09/26", K.NUMERO_COMMANDE to "1234567", K.CLIENT_MANDATAIRE to "LOC TEST",
            K.CLIENT_UTILISATEUR to "Entrepôt \"Nord\"; quai 2", K.UTILISATEUR_CP to "02000",
            K.UTILISATEUR_ADRESSE to "Rue des Essais, Laon", K.MARQUE to "Hyster", K.HORAMETRE to "1293",
            K.pneu("av", "quantite") to "2", K.prestation("depose", "8") to "2", K.SERRAGE_AV to "650",
        ),
        signature = sig,
    )
    private val livraison = Intervention(
        "b", InterventionType.DOCUMENT, 2L,
        values = mapOf(DocKeys.DATE to "10/09/26", DocKeys.CLIENT to "Client Test"),
    )
    private val aout = Intervention("c", InterventionType.FPS, 3L, values = mapOf(K.DATE to "28/08/26"))

    @Test
    fun bonsDuMois_duPlusAncienAuPlusRecent() {
        val bons = Recap.bonsOf(listOf(livraison, aout, envoyee), YearMonth.of(2026, 9))
        assertEquals(listOf("a", "b"), bons.map { it.id })
        assertEquals("septembre 2026", Recap.monthLabel(YearMonth.of(2026, 9)))
    }

    @Test
    fun tableurPourExcel() {
        val csv = Recap.csv(listOf(envoyee, livraison), "CE")
        // BOM UTF-8, séparateur « ; », fins de ligne Windows
        assertTrue(csv.startsWith("\uFEFFDate;Type;Client;"))
        val lines = csv.removePrefix("\uFEFF").split("\r\n").filter { it.isNotEmpty() }
        assertEquals(3, lines.size)
        val row = lines[1]
        // Cellule avec « ; » et guillemets : entre guillemets, guillemets doublés
        assertTrue(row, row.startsWith("03/09/2026;Fiche d'intervention;LOC TEST;\"Entrepôt \"\"Nord\"\"; quai 2\";02000;Laon;1234567;"))
        assertTrue(row, row.contains("03-09-2026 CE.pdf"))
        assertTrue(row, row.contains(";Envoyé;"))
        // Rien ne manque
        assertTrue(row, row.endsWith(";"))
        // Pas encore envoyé : statut et ce qui manque
        assertTrue(lines[2], lines[2].startsWith("10/09/2026;Document à signer;Client Test;"))
        assertTrue(lines[2], lines[2].endsWith(";En cours;;Signature du client"))
    }
}
