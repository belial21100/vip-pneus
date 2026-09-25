package fr.vippneus.intervention.data

import fr.vippneus.intervention.pdf.FpsTemplate
import fr.vippneus.intervention.pdf.FpsTemplate.K

/** Élément attendu sur le bon avant l'envoi à la comptabilité. */
data class Todo(
    /** Champ à remplir, ou [Completion.SIGNATURE]. */
    val key: String,
    val label: String,
    val done: Boolean,
    /** Ce qu'il faut faire, en quelques mots. */
    val hint: String = "",
)

/**
 * Ce qu'il reste à compléter sur un bon : affiché en tête du formulaire, dans la barre d'envoi,
 * sur la liste des bons et vérifié avant l'envoi.
 * - fiche FPS : les rubriques de la fiche ;
 * - feuille de tâche Mastra : les cases que remplit le technicien, et la signature ;
 * - autre document (bon de livraison…) : seulement la signature du client.
 */
object Completion {
    const val SIGNATURE = "signature"

    fun todos(i: Intervention): List<Todo> = when (i.type) {
        InterventionType.FPS -> fps(i)
        InterventionType.DOCUMENT -> document(i)
    }

    fun missing(i: Intervention): List<Todo> = todos(i).filterNot { it.done }

    /** Champs des formulaires à signaler « À compléter ». */
    fun missingFields(i: Intervention): Set<String> =
        missing(i).flatMapTo(mutableSetOf()) { fieldsOf(i, it.key) }

    private fun fieldsOf(i: Intervention, key: String): List<String> = when {
        key == SIGNATURE -> emptyList()
        i.type == InterventionType.DOCUMENT -> listOf(key)
        key == K.CLIENT_MANDATAIRE -> listOf(K.CLIENT_MANDATAIRE, K.CLIENT_UTILISATEUR)
        key == K.MARQUE -> listOf(K.MARQUE, K.TYPE, K.SERIE)
        key == K.SERRAGE_AV -> listOf(K.SERRAGE_AV, K.SERRAGE_AR)
        key == K.pneu("av", "dimensions") -> listOf(K.pneu("av", "dimensions"), K.pneu("av", "quantite"))
        key.startsWith("prest.") -> emptyList()
        else -> listOf(key)
    }

    private fun filled(i: Intervention, vararg keys: String) = keys.any { i.value(it).isNotBlank() }

    /** Signature dans la case prévue, ou posée sur la page avec l'outil « Signature ». */
    private fun signed(i: Intervention) =
        i.signature?.isEmpty == false || i.overlays.any { it.kind == OverlayKind.SIGNATURE && it.signature?.isEmpty == false }

    private val pneuKeys = listOf("av", "ar").flatMap { e -> FpsTemplate.pneuColumns.map { (c, _, _) -> K.pneu(e, c) } }

    private val prestationKeys = FpsTemplate.prestationRows.flatMap { (r, _, _) ->
        FpsTemplate.prestationColumns.map { (c, _, _) -> K.prestation(r, c) }
    }

    private fun fps(i: Intervention) = listOf(
        Todo(K.NUMERO_COMMANDE, "N° de commande", filled(i, K.NUMERO_COMMANDE), "Numéro de la commande du client"),
        Todo(K.CLIENT_MANDATAIRE, "Client", filled(i, K.CLIENT_MANDATAIRE, K.CLIENT_UTILISATEUR), "Client mandataire ou client utilisateur"),
        Todo(K.MARQUE, "Matériel", filled(i, K.MARQUE, K.TYPE, K.SERIE), "Marque, type ou n° de série de l'engin"),
        Todo(K.HORAMETRE, "Horamètre", filled(i, K.HORAMETRE), "Heures relevées sur l'engin"),
        Todo(K.pneu("av", "dimensions"), "Pneus", pneuKeys.any { filled(i, it) }, "Au moins une information : dimensions, marque, quantité…"),
        Todo(K.prestation("depose", "8"), "Prestations", prestationKeys.any { filled(i, it) }, "Au moins une quantité dans le tableau"),
        Todo(
            K.SERRAGE_AV, "Serrage des roues",
            filled(i, K.SERRAGE_AV, K.SERRAGE_AR, K.SERRAGE_AV_REMARQUE, K.SERRAGE_AR_REMARQUE),
            "Couple AV ou AR en Nm, ou une remarque",
        ),
        Todo(SIGNATURE, "Signature du client", signed(i), "À faire signer en fin d'intervention"),
    )

    /** Aide des cases de la feuille de tâche Mastra (suffixe de la clé). */
    private val HINTS = mapOf(
        "compteur" to "Heures relevées au compteur de l'engin",
        "couple" to "Couple de serrage appliqué, en Nm",
        "monteur" to "Nom du technicien",
        "date" to "Date de l'intervention",
        "recuPar" to "Nom de la personne qui signe",
    )

    private fun document(i: Intervention): List<Todo> {
        val t = i.template
        if (t != null) {
            return t.fields.filter { it.required }.map {
                Todo(it.key, it.label.substringBefore(" ("), filled(i, it.key), HINTS[it.key.substringAfterLast('.')].orEmpty())
            } + listOfNotNull(t.signature?.let { Todo(SIGNATURE, "Signature du client", signed(i), "À faire signer en fin d'intervention") })
        }
        // Bon de livraison ou autre document : seule la signature est exigée
        return listOf(
            Todo(SIGNATURE, "Signature du client", signed(i), "Touchez « Signature » au-dessus de la page, puis l'endroit où signer"),
        )
    }
}
