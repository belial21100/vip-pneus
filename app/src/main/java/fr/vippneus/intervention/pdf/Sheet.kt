package fr.vippneus.intervention.pdf

import fr.vippneus.intervention.data.InterventionType

enum class HAlign { START, CENTER }

/** Zone de saisie d'une fiche (points, origine en haut à gauche de la page). */
class Field(
    val key: String,
    val label: String,
    val box: Box,
    val fontSize: Float,
    val align: HAlign = HAlign.START,
    val maxLines: Int = 1,
    val minFontSize: Float = 7f,
    /** Si renseigné : 1re ligne centrée sur cette ordonnée (alignée sur l'étiquette imprimée). */
    val firstLineY: Float? = null,
)

class Option(val value: String, val label: String, val cx: Float, val cy: Float)

/** Cases à cocher « Oui / Non » : une croix est tracée dans la case choisie. */
class Choice(val key: String, val label: String, val options: List<Option>)

/** Croix tracée d'après les valeurs saisies (ex. roue concernée sur le schéma des positions). */
class Mark(val key: String, val label: String, val cx: Float, val cy: Float, val half: Float)

/**
 * Fiche intégrée à l'application (presse mobile, poids lourds) : la fiche vierge sert de fond,
 * les valeurs saisies s'écrivent dans ses zones.
 */
interface Sheet {
    val pageW: Float
    val pageH: Float

    /** Image de la fiche vierge : aperçu à l'écran, et fond du PDF à défaut de [pdfAsset]. */
    val imageAsset: String

    /** Fiche vierge en PDF : page 1 du PDF envoyé (traits et textes nets à l'impression). */
    val pdfAsset: String? get() = null

    val fields: List<Field>
    val fieldsByKey: Map<String, Field>
    val choices: List<Choice>

    /** Demi-côté de la croix tracée dans une case (points). */
    val crossHalf: Float

    /** Zone de la signature du client. */
    val signatureBox: Box

    /** Croix déduites des valeurs (roues concernées…). */
    fun marks(values: Map<String, String>): List<Mark> = emptyList()

    /** Libellé d'un élément de la page, pour l'éditeur. */
    fun label(key: String): String? =
        fieldsByKey[key]?.label ?: choices.firstOrNull { it.key == key }?.label
}

/** Fiche intégrée d'un type de bon (aucune pour un document client). */
val InterventionType.sheet: Sheet?
    get() = when (this) {
        InterventionType.FPS -> FpsTemplate
        InterventionType.PL -> PlTemplate
        InterventionType.DOCUMENT -> null
    }
