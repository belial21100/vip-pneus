package fr.vippneus.intervention

import fr.vippneus.intervention.data.Settings
import fr.vippneus.intervention.data.SettingsRules
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Vérification des réglages saisis à la première ouverture et dans Réglages. */
class SettingsRulesTest {

    @Test
    fun initialesDeduitesDuNom() {
        assertEquals("CE", SettingsRules.initialesFrom("Chris.E"))
        assertEquals("JPC", SettingsRules.initialesFrom("Jean-Pierre Colin"))
        assertEquals("", SettingsRules.initialesFrom("  "))
    }

    @Test
    fun adressesEmail() {
        assertNull(SettingsRules.emailsError("compta@exemple.fr", required = true))
        assertEquals("Adresse e-mail invalide", SettingsRules.emailsError("compta", required = true))
        assertEquals("Obligatoire", SettingsRules.emailsError("  ", required = true))
        assertNull(SettingsRules.emailsError("", required = false))
        assertNull(SettingsRules.emailsError("a@exemple.fr, b.c@exemple.com", required = false))
        assertEquals("Une des adresses est invalide", SettingsRules.emailsError("a@exemple.fr; b@", required = false))
    }

    @Test
    fun initiales() {
        assertNull(SettingsRules.initialesError("CE"))
        assertEquals("Obligatoire", SettingsRules.initialesError(""))
        assertEquals("6 caractères au plus", SettingsRules.initialesError("ABCDEFG"))
        assertEquals("Lettres et chiffres seulement", SettingsRules.initialesError("C.E"))
    }

    @Test
    fun reglagesComplets() {
        val ok = Settings(technicien = "Chris.E", initiales = "CE", emailCompta = "compta@exemple.fr")
        assertTrue(ok.isComplete)
        assertFalse(ok.copy(emailCompta = "").isComplete)
        assertFalse(ok.copy(emailCopie = "pas une adresse").isComplete)
        assertFalse(ok.copy(technicien = " ").isComplete)
        assertEquals("CE", Settings(initiales = " ce ").trimmed().initiales)
    }
}
