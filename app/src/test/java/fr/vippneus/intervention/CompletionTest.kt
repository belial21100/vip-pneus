package fr.vippneus.intervention

import fr.vippneus.intervention.data.Completion
import fr.vippneus.intervention.data.DisplayStatus
import fr.vippneus.intervention.data.DocTemplate
import fr.vippneus.intervention.data.Intervention
import fr.vippneus.intervention.data.InterventionType
import fr.vippneus.intervention.data.Overlay
import fr.vippneus.intervention.data.OverlayKind
import fr.vippneus.intervention.data.PlacedBox
import fr.vippneus.intervention.data.PlacedField
import fr.vippneus.intervention.data.SignatureData
import fr.vippneus.intervention.data.displayStatus
import fr.vippneus.intervention.pdf.FpsTemplate.K
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Liste « À compléter » et champs lus dans le document du client. */
class CompletionTest {
    private val sig = SignatureData(listOf(listOf(0f, 0f, 10f, 10f)), 100f, 50f, 3f)

    private fun missing(i: Intervention) = Completion.missing(i).map { it.label }

    @Test
    fun ficheFps_videPuisComplete() {
        val empty = Intervention("x", InterventionType.FPS, 0L)
        assertEquals(8, Completion.todos(empty).size)
        assertEquals(8, missing(empty).size)

        val full = empty.copy(
            values = mapOf(
                K.NUMERO_COMMANDE to "1234567",
                K.CLIENT_UTILISATEUR to "Entrepôt Test",
                K.TYPE to "S4.5FT",
                K.HORAMETRE to "1293",
                K.pneu("ar", "quantite") to "2",
                K.prestation("dechets", "autres") to "4",
                // Serrage : la remarque suffit (« Démonté et remonté par le client »)
                K.SERRAGE_AR_REMARQUE to "Démonté et remonté par le client",
            ),
            signature = sig,
        )
        assertEquals(emptyList<String>(), missing(full))
    }

    @Test
    fun ficheFps_signatureVideNeComptePas() {
        val i = Intervention("x", InterventionType.FPS, 0L, signature = SignatureData(emptyList(), 100f, 50f, 3f))
        assertTrue("Signature du client" in missing(i))
    }

    @Test
    fun feuilleMastra_champsObligatoiresEtSignature() {
        fun f(key: String, label: String, required: Boolean) = PlacedField(key, label, 0f, 0f, 10f, 10f, 12f, required = required)
        val t = DocTemplate(
            "interfit", "Feuille de tâche Mastra",
            listOf(
                f("if.compteur", "Lecture du compteur (h)", true),
                f("if.heureArrivee", "Heure d'arrivée", false),
                f("if.recuPar", "Reçu par (nom du client)", true),
            ),
            signature = PlacedBox(0f, 0f, 10f, 10f),
        )
        val i = Intervention("x", InterventionType.DOCUMENT, 0L, template = t, values = mapOf("if.compteur" to "4559"))
        // Libellés courts (sans la parenthèse), champs facultatifs ignorés
        assertEquals(listOf("Reçu par", "Signature du client"), missing(i))
        assertEquals(3, Completion.todos(i).size)
    }

    @Test
    fun bonDeLivraison_seuleLaSignatureEstExigee() {
        val i = Intervention("x", InterventionType.DOCUMENT, 0L)
        // Aucun champ forcé : ni client, ni texte sur la page
        assertEquals(listOf("Signature du client"), missing(i))
        assertEquals(emptySet<String>(), Completion.missingFields(i))

        val texte = i.copy(overlays = listOf(Overlay("t", OverlayKind.TEXT, 10f, 10f, "OK")))
        assertEquals(listOf("Signature du client"), missing(texte))

        val signeVide = i.copy(overlays = listOf(Overlay("s", OverlayKind.SIGNATURE, 10f, 10f, signature = SignatureData(emptyList(), 100f, 50f, 3f))))
        assertEquals(listOf("Signature du client"), missing(signeVide))

        val signe = i.copy(overlays = listOf(Overlay("s", OverlayKind.SIGNATURE, 10f, 10f, width = 150f, height = 75f, signature = sig)))
        assertEquals(emptyList<String>(), missing(signe))
    }

    @Test
    fun champsSignalesDansLeFormulaire() {
        val empty = Intervention("x", InterventionType.FPS, 0L)
        val fields = Completion.missingFields(empty)
        // Client : les deux champs possibles ; matériel : marque, type, série
        assertTrue(K.CLIENT_MANDATAIRE in fields && K.CLIENT_UTILISATEUR in fields)
        assertTrue(K.MARQUE in fields && K.TYPE in fields && K.SERIE in fields)
        assertTrue(K.SERRAGE_AV in fields && K.SERRAGE_AR in fields)
        assertFalse(K.OBSERVATIONS in fields)

        val client = empty.copy(values = mapOf(K.CLIENT_UTILISATEUR to "Entrepôt Test"))
        assertFalse(K.CLIENT_MANDATAIRE in Completion.missingFields(client))
        assertFalse("Client" in missing(client))
    }

    @Test
    fun envoiIncomplet_marqueJusquAuRenvoi() {
        val i = Intervention("x", InterventionType.DOCUMENT, 0L, updatedAt = 100L)
        assertEquals(DisplayStatus.BROUILLON, i.displayStatus())
        // Envoyé quand même sans la signature : à corriger
        val incomplet = i.copy(sentAt = 200L, sentMissing = listOf("Signature du client"))
        assertEquals(DisplayStatus.INCOMPLET, incomplet.displayStatus())
        // Complété ensuite : à renvoyer
        assertEquals(DisplayStatus.MODIFIE, incomplet.copy(updatedAt = 300L).displayStatus())
        // Renvoyé complet
        assertEquals(DisplayStatus.ENVOYE, incomplet.copy(updatedAt = 300L, sentAt = 400L, sentMissing = emptyList()).displayStatus())
    }

    @Test
    fun champLuDansLeDocument_tantQuIlNestPasModifie() {
        val i = Intervention(
            "x", InterventionType.FPS, 0L,
            values = mapOf(K.NUMERO_COMMANDE to "7654321", K.MONTEUR to "Chris.E"),
            autoValues = mapOf(K.NUMERO_COMMANDE to "7654321"),
        )
        assertTrue(i.isAuto(K.NUMERO_COMMANDE))
        assertFalse(i.isAuto(K.MONTEUR))
        assertFalse(i.copy(values = i.values + (K.NUMERO_COMMANDE to "7654322")).isAuto(K.NUMERO_COMMANDE))
        assertFalse(i.copy(values = i.values - K.NUMERO_COMMANDE).isAuto(K.NUMERO_COMMANDE))
    }
}
