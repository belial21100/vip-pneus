package fr.vippneus.intervention.data

import fr.vippneus.intervention.pdf.FpsTemplate.K
import java.text.Normalizer
import java.util.Locale

/** Propositions de saisie apprises des bons précédents (clients, adresses, marques, dimensions...). */
object Suggestions {

    private val keys = setOf(
        K.CLIENT_MANDATAIRE, K.MANDATAIRE_ADRESSE, K.MANDATAIRE_CP, K.MONTEUR,
        K.CLIENT_UTILISATEUR, K.UTILISATEUR_ADRESSE, K.UTILISATEUR_CP,
        K.MARQUE, K.TYPE, K.SERRAGE_AV, K.SERRAGE_AR, K.SERRAGE_AV_REMARQUE, K.SERRAGE_AR_REMARQUE,
        DocKeys.CLIENT, DocKeys.SITE, DocKeys.CP, DocKeys.VILLE,
    )

    /** Regroupe les champs équivalents (pneus AV/AR, serrages...). */
    fun group(key: String): String? = when {
        key.startsWith("av.") || key.startsWith("ar.") ->
            key.substringAfter('.').takeIf { it != "fourni" && it != "quantite" }?.let { "pneu.$it" }
        key == K.SERRAGE_AV || key == K.SERRAGE_AR -> "serrage"
        key == K.SERRAGE_AV_REMARQUE || key == K.SERRAGE_AR_REMARQUE -> "serrage.remarque"
        key == K.CLIENT_MANDATAIRE || key == DocKeys.CLIENT -> "client"
        key == K.CLIENT_UTILISATEUR || key == DocKeys.SITE -> "site"
        key == K.UTILISATEUR_CP || key == K.MANDATAIRE_CP || key == DocKeys.CP -> "cp"
        key in keys -> key
        else -> null
    }

    fun build(interventions: List<Intervention>): Map<String, List<String>> {
        val out = LinkedHashMap<String, MutableList<String>>()
        for (i in interventions.sortedByDescending { it.updatedAt }) {
            for ((k, v) in i.values) {
                val g = group(k) ?: continue
                val value = v.trim()
                if (value.isEmpty() || value.length > 80) continue
                val list = out.getOrPut(g) { mutableListOf() }
                if (list.size < 40 && list.none { it.equals(value, ignoreCase = true) }) list += value
            }
        }
        return out
    }

    fun filter(all: Map<String, List<String>>, key: String, typed: String, max: Int = 8): List<String> {
        val g = group(key) ?: return emptyList()
        val list = all[g] ?: return emptyList()
        val t = normalize(typed)
        return list.asSequence()
            .filter { !it.equals(typed.trim(), ignoreCase = true) }
            .filter { t.isEmpty() || normalize(it).contains(t) }
            .take(max)
            .toList()
    }

    private fun normalize(s: String): String =
        Normalizer.normalize(s.trim().lowercase(Locale.FRANCE), Normalizer.Form.NFD).replace(Regex("\\p{M}+"), "")
}
