package fr.vippneus.intervention.data

import fr.vippneus.intervention.pdf.FpsTemplate
import fr.vippneus.intervention.pdf.FpsTemplate.K

/** Élément attendu sur le bon avant l'envoi à la comptabilité. */
data class Todo(
    /** Champ à remplir, ou [Completion.SIGNATURE] / [Completion.PAGE]. */
    val key: String,
    val label: String,
    val done: Boolean,
)

/** Ce qu'il reste à compléter sur un bon (affiché en tête du formulaire et vérifié avant l'envoi). */
object Completion {
    const val SIGNATURE = "signature"
    const val PAGE = "page"

    fun todos(i: Intervention): List<Todo> = when (i.type) {
        InterventionType.FPS -> fps(i)
        InterventionType.DOCUMENT -> document(i)
    }

    fun missing(i: Intervention): List<Todo> = todos(i).filterNot { it.done }

    private fun filled(i: Intervention, vararg keys: String) = keys.any { i.value(it).isNotBlank() }

    private fun signed(i: Intervention) = i.signature?.isEmpty == false

    private val pneuKeys = listOf("av", "ar").flatMap { e -> FpsTemplate.pneuColumns.map { (c, _, _) -> K.pneu(e, c) } }

    private val prestationKeys = FpsTemplate.prestationRows.flatMap { (r, _, _) ->
        FpsTemplate.prestationColumns.map { (c, _, _) -> K.prestation(r, c) }
    }

    private fun fps(i: Intervention) = listOf(
        Todo(K.NUMERO_COMMANDE, "N° de commande", filled(i, K.NUMERO_COMMANDE)),
        Todo(K.CLIENT_MANDATAIRE, "Client", filled(i, K.CLIENT_MANDATAIRE, K.CLIENT_UTILISATEUR)),
        Todo(K.MARQUE, "Matériel", filled(i, K.MARQUE, K.TYPE, K.SERIE)),
        Todo(K.HORAMETRE, "Horamètre", filled(i, K.HORAMETRE)),
        Todo(K.pneu("av", "dimensions"), "Pneus", pneuKeys.any { filled(i, it) }),
        Todo(K.prestation("depose", "8"), "Prestations", prestationKeys.any { filled(i, it) }),
        Todo(
            K.SERRAGE_AV, "Serrage des roues",
            filled(i, K.SERRAGE_AV, K.SERRAGE_AR, K.SERRAGE_AV_REMARQUE, K.SERRAGE_AR_REMARQUE),
        ),
        Todo(SIGNATURE, "Signature du client", signed(i)),
    )

    private fun document(i: Intervention): List<Todo> {
        val t = i.template
        if (t != null) {
            return t.fields.filter { it.required }.map { Todo(it.key, it.label.substringBefore(" ("), filled(i, it.key)) } +
                listOfNotNull(t.signature?.let { Todo(SIGNATURE, "Signature du client", signed(i)) })
        }
        return listOf(
            Todo(PAGE, "Compléter la page 1", i.overlays.isNotEmpty()),
            Todo(DocKeys.CLIENT, "Client", filled(i, DocKeys.CLIENT)),
        )
    }
}
