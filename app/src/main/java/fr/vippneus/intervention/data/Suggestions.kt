package fr.vippneus.intervention.data

import fr.vippneus.intervention.pdf.FpsTemplate
import fr.vippneus.intervention.pdf.FpsTemplate.K
import fr.vippneus.intervention.pdf.PlKeys
import java.text.Normalizer
import java.util.Locale

/** Propositions de saisie apprises des bons précédents (clients, adresses, marques, dimensions...), les plus utilisées d'abord. */
object Suggestions {

    private val keys = setOf(
        K.CLIENT_MANDATAIRE, K.MANDATAIRE_ADRESSE, K.MANDATAIRE_CP, K.MONTEUR,
        K.CLIENT_UTILISATEUR, K.UTILISATEUR_ADRESSE, K.UTILISATEUR_CP,
        K.MARQUE, K.TYPE, K.SERRAGE_AV, K.SERRAGE_AR, K.SERRAGE_AV_REMARQUE, K.SERRAGE_AR_REMARQUE,
        DocKeys.CLIENT, DocKeys.SITE, DocKeys.CP, DocKeys.VILLE,
        // Feuille de tâche Mastra : personne qui signe, client final
        "if.recuPar",
        // Fiche poids lourds
        PlKeys.LIEU, PlKeys.VEHICULE,
    )

    private val PL_PNEU = setOf("dimensions", "marque", "type")

    /** Proposé pour l'essieu d'une ligne de pneus ajoutée, en plus de ce qui a déjà été saisi. */
    val ESSIEUX = listOf("Essieu 2", "Essieu 3", "Essieu 4", "AR int.", "Essieu 2 int.", "Essieu 3 int.")

    /** Marques proposées tant que la tablette n'en a pas d'autres en mémoire. */
    val MARQUES = listOf("Michelin", "Continental", "Bridgestone", "Goodyear", "Trelleborg")

    /** Regroupe les champs équivalents (lignes de pneus, serrages...). */
    fun group(key: String): String? = when {
        FpsTemplate.isPneuKey(key) ->
            key.substringAfter('.').takeIf { it != "fourni" && it != "quantite" }?.let { "pneu.$it" }
        // Fiche poids lourds : dimensions, marques et types de pneus partagés avec la fiche presse mobile
        key.startsWith(PlKeys.PREFIX) && !key.startsWith("pl.fourn.") && key.substringAfterLast('.') in PL_PNEU ->
            "pneu." + key.substringAfterLast('.')
        key == K.SERRAGE_AV || key == K.SERRAGE_AR -> "serrage"
        key == K.SERRAGE_AV_REMARQUE || key == K.SERRAGE_AR_REMARQUE -> "serrage.remarque"
        key == K.CLIENT_MANDATAIRE || key == DocKeys.CLIENT -> "client"
        key == K.CLIENT_UTILISATEUR || key == DocKeys.SITE -> "site"
        key == K.UTILISATEUR_CP || key == K.MANDATAIRE_CP || key == DocKeys.CP -> "cp"
        key == "if.clientFinal.nom" || key == "if.lieu" -> "clientFinal"
        key == "if.clientFinal.adresse" || key == "if.lieuAdresse" -> "clientFinal.adresse"
        key in keys -> key
        else -> null
    }

    /** Valeur déjà saisie : nombre d'utilisations, rang de la plus récente (0 = la plus récente). */
    private class Seen(val text: String, val rank: Int) {
        var count = 0
    }

    /** Par groupe, les valeurs déjà saisies : les plus utilisées d'abord, puis les plus récentes. */
    fun build(interventions: List<Intervention>): Map<String, List<String>> {
        val seen = LinkedHashMap<String, LinkedHashMap<String, Seen>>()
        var rank = 0
        for (i in interventions.sortedByDescending { it.updatedAt }) {
            for ((k, v) in i.values) {
                val g = group(k) ?: continue
                val value = v.trim()
                if (value.isEmpty() || value.length > 80) continue
                seen.getOrPut(g) { LinkedHashMap() }.getOrPut(value.lowercase(Locale.FRANCE)) { Seen(value, rank++) }.count++
            }
        }
        return seen.mapValues { (_, values) ->
            values.values.sortedWith(compareByDescending<Seen> { it.count }.thenBy { it.rank }).take(40).map { it.text }
        }
    }

    /** Proposées même sans historique (tablette neuve). */
    private fun defaults(group: String): List<String> = when (group) {
        "pneu.essieu" -> ESSIEUX
        "pneu.marque" -> MARQUES
        else -> emptyList()
    }

    /** Boutons de choix rapide : les valeurs les plus utilisées pour ce champ. */
    fun top(all: Map<String, List<String>>, key: String, max: Int = 6): List<String> {
        val g = group(key) ?: return emptyList()
        return (all[g].orEmpty() + defaults(g)).distinctBy { it.lowercase(Locale.FRANCE) }.take(max)
    }

    fun filter(all: Map<String, List<String>>, key: String, typed: String, max: Int = 8): List<String> {
        val g = group(key) ?: return emptyList()
        val list = all[g].orEmpty() + defaults(g)
        val t = normalize(typed)
        return list.asSequence()
            .distinctBy { it.lowercase(Locale.FRANCE) }
            .filter { !it.equals(typed.trim(), ignoreCase = true) }
            .filter { t.isEmpty() || normalize(it).contains(t) }
            .take(max)
            .toList()
    }

    private fun normalize(s: String): String =
        Normalizer.normalize(s.trim().lowercase(Locale.FRANCE), Normalizer.Form.NFD).replace(Regex("\\p{M}+"), "")
}
