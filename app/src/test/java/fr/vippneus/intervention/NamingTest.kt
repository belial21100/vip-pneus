package fr.vippneus.intervention

import fr.vippneus.intervention.data.DocKeys
import fr.vippneus.intervention.data.Intervention
import fr.vippneus.intervention.data.InterventionType
import fr.vippneus.intervention.data.Naming
import fr.vippneus.intervention.pdf.FpsTemplate.K
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate

class NamingTest {

    private fun fps(vararg values: Pair<String, String>) =
        Intervention(id = "x", type = InterventionType.FPS, createdAt = 0L, values = mapOf(*values))

    @Test
    fun parseDate_formatsCourants() {
        assertEquals(LocalDate.of(2026, 8, 24), Naming.parseDate("24/8/26"))
        assertEquals(LocalDate.of(2026, 8, 4), Naming.parseDate("04/08/26"))
        assertEquals(LocalDate.of(2026, 8, 27), Naming.parseDate("27-08-2026"))
        assertEquals(LocalDate.of(2026, 8, 27), Naming.parseDate("2026-08-27"))
        assertNull(Naming.parseDate("31/02/26"))
        assertNull(Naming.parseDate("demain"))
    }

    @Test
    fun departement_premierCodePostal() {
        assertEquals("02", Naming.departement("", "02100 ST QUENTIN"))
        assertEquals("54", Naming.departement("54180"))
        assertNull(Naming.departement("Laon", "tel 0600000000"))
    }

    @Test
    fun nomDeFichier_ficheFps() {
        val i = fps(
            K.CLIENT_MANDATAIRE to "Loc Test",
            K.CLIENT_UTILISATEUR to "Entrepot Dupont",
            K.UTILISATEUR_CP to "02000",
            K.UTILISATEUR_ADRESSE to "Rue des Essais, Laon",
            K.NUMERO_COMMANDE to "1234567",
            K.DATE to "24/8/26",
        )
        assertEquals("02- LOC TEST ENTREPOT DUPONT 02000 LAON 1234567 24-08-2026 CE", Naming.defaultFileName(i, "CE"))
    }

    @Test
    fun nomDeFichier_documentClient_sansDoublon() {
        val i = Intervention(
            id = "x", type = InterventionType.DOCUMENT, createdAt = 0L,
            values = mapOf(
                DocKeys.CLIENT to "Client Test",
                DocKeys.SITE to "Esat Ville-Test",
                DocKeys.CP to "54000",
                DocKeys.VILLE to "Ville-Test",
                DocKeys.REFERENCE to "7654321",
                DocKeys.DATE to "27/08/26",
            ),
        )
        assertEquals("54- CLIENT TEST ESAT VILLE-TEST 54000 VILLE-TEST 7654321 27-08-2026 CE", Naming.defaultFileName(i, "CE"))
    }

    @Test
    fun nomDeFichier_caracteresInterdits() {
        assertEquals("Bon A B C", Naming.sanitize("Bon/A:B*C?.pdf"))
        assertEquals("Bon d'intervention", Naming.sanitize("  //  "))
        val custom = fps(K.CLIENT_MANDATAIRE to "X").copy(fileName = "Mon nom perso.pdf")
        assertEquals("Mon nom perso", Naming.fileName(custom, "CE"))
    }

    @Test
    fun ville_apresLaDerniereVirgule() {
        assertEquals("Laon", Naming.ville("12 rue X, Laon"))
        assertEquals("Laon", Naming.ville("Laon"))
        assertEquals("", Naming.ville(""))
    }
}
