package fr.vippneus.intervention.data

import kotlinx.serialization.Serializable

/** Deux façons de produire le bon : la fiche FPS intégrée, ou la 1re page d'un PDF client. */
@Serializable
enum class InterventionType { FPS, DOCUMENT }

/**
 * Signature manuscrite, conservée en vectoriel (traits) pour un rendu net dans le PDF.
 * Chaque trait est une polyligne à plat : [x0, y0, x1, y1, ...] en pixels du bloc de saisie.
 */
@Serializable
data class SignatureData(
    val strokes: List<List<Float>>,
    val width: Float,
    val height: Float,
    val penWidth: Float,
) {
    val isEmpty: Boolean get() = strokes.none { it.size >= 2 }
}

@Serializable
enum class OverlayKind { TEXT, SIGNATURE, CROSS }

/**
 * Élément posé librement sur la page 1 (texte, signature, croix).
 * Coordonnées en points PDF depuis le coin haut-gauche de la page telle qu'affichée.
 * TEXT : (x, y) = coin haut-gauche du bloc de texte.
 * SIGNATURE : cadre (x, y, width, height).  CROSS : carré (x, y, width).
 */
@Serializable
data class Overlay(
    val id: String,
    val kind: OverlayKind,
    val x: Float,
    val y: Float,
    val text: String = "",
    val fontSize: Float = 14f,
    val width: Float = 0f,
    val height: Float = 0f,
    val signature: SignatureData? = null,
)

/** Ajustement manuel d'un champ de la fiche FPS (déplacement, taille du texte, échelle de signature). */
@Serializable
data class FieldAdjust(
    val dx: Float = 0f,
    val dy: Float = 0f,
    val fontSize: Float? = null,
    val scale: Float? = null,
)

@Serializable
enum class AttachmentKind { PDF, IMAGE }

/** Document ajouté après la page 1 (bon de commande, photo...). */
@Serializable
data class Attachment(
    val id: String,
    val file: String,
    val name: String,
    val kind: AttachmentKind,
    val pages: Int = 1,
)

/** Zone de saisie placée sur la page 1 d'un document client reconnu (points, origine en haut à gauche). */
@Serializable
data class PlacedField(
    val key: String,
    val label: String,
    val left: Float,
    val top: Float,
    val right: Float,
    val bottom: Float,
    val fontSize: Float,
    val center: Boolean = false,
    val maxLines: Int = 1,
    val minFontSize: Float = 7f,
    val hint: String = "",
    val numeric: Boolean = false,
    /** Unité ajoutée si la valeur saisie n'est qu'un nombre (« 4559 » -> « 4559 h »). */
    val unit: String = "",
    /** À remplir avant l'envoi (liste « À compléter »). */
    val required: Boolean = false,
)

@Serializable
data class PlacedBox(val left: Float, val top: Float, val right: Float, val bottom: Float)

/** Ligne d'un encart : une valeur saisie, écrite sur une ou plusieurs lignes. */
@Serializable
data class PanelLine(
    val key: String,
    val label: String,
    val fontSize: Float,
    val minFontSize: Float = 7f,
    val maxLines: Int = 1,
)

/**
 * Encart encadré écrit sur la page (ex. client final au-dessus de « Commentaires »).
 * Sa hauteur suit le contenu ; il est posé sur [bottom], entre [left] et [right].
 * [title] nomme l'encart dans l'application ; il n'est écrit sur la page que si [printTitle].
 */
@Serializable
data class PlacedPanel(
    val key: String,
    val title: String,
    val left: Float,
    val right: Float,
    val bottom: Float,
    val lines: List<PanelLine>,
    val printTitle: Boolean = false,
)

/** Modèle de remplissage d'un document client reconnu (ex. feuille de tâche Interfit). */
@Serializable
data class DocTemplate(
    val id: String,
    val name: String,
    val fields: List<PlacedField>,
    val signature: PlacedBox? = null,
    val panel: PlacedPanel? = null,
)

/** PDF client dont on complète la 1re page (mode DOCUMENT). */
@Serializable
data class SourceDoc(
    val file: String,
    val name: String,
    val pageCount: Int,
    /** Taille de la page 1 telle qu'affichée (rotation appliquée), en points. */
    val pageWidth: Float,
    val pageHeight: Float,
)

@Serializable
data class Intervention(
    val id: String,
    val type: InterventionType,
    val createdAt: Long,
    val updatedAt: Long = createdAt,
    /** Valeurs saisies (clés : [fr.vippneus.intervention.pdf.FpsTemplate] ou [DocKeys]). */
    val values: Map<String, String> = emptyMap(),
    /** Signature du client sur la fiche FPS. */
    val signature: SignatureData? = null,
    val adjust: Map<String, FieldAdjust> = emptyMap(),
    val overlays: List<Overlay> = emptyList(),
    val source: SourceDoc? = null,
    /** Mode DOCUMENT : champs placés automatiquement sur la page 1 (document reconnu). */
    val template: DocTemplate? = null,
    /** Indications extraites du document (couple préconisé, dernier horamètre...). */
    val hints: Map<String, String> = emptyMap(),
    /** Nom du type de document reconnu à l'import (ex. « Bon de commande Manuloc »). */
    val recognized: String? = null,
    val attachments: List<Attachment> = emptyList(),
    /** Mode DOCUMENT : le document d'origine (non rempli) suit la page remplie, comme aujourd'hui pour Mastra. */
    val includeOriginal: Boolean = true,
    /** Nom de fichier choisi (sans .pdf). Vide = nom automatique. */
    val fileName: String = "",
    val generatedAt: Long? = null,
    val sentAt: Long? = null,
    /** Valeurs lues dans le document du client à l'import (à vérifier par le technicien). */
    val autoValues: Map<String, String> = emptyMap(),
) {
    fun value(key: String): String = values[key].orEmpty()

    /** Vrai tant que le champ contient la valeur lue dans le document (non retouchée). */
    fun isAuto(key: String): Boolean = autoValues[key]?.let { it.isNotBlank() && it == values[key] } ?: false
}

/** Informations saisies pour un document client (servent au nom du fichier et à la liste). */
object DocKeys {
    const val CLIENT = "doc.client"
    const val SITE = "doc.site"
    const val CP = "doc.cp"
    const val VILLE = "doc.ville"
    const val REFERENCE = "doc.reference"
    const val DATE = "doc.date"
}

enum class DisplayStatus(val label: String) {
    BROUILLON("Brouillon"),
    PRET("PDF prêt"),
    ENVOYE("Envoyé"),
    MODIFIE("Modifié après envoi"),
}

fun Intervention.displayStatus(): DisplayStatus = when {
    sentAt != null && updatedAt <= sentAt -> DisplayStatus.ENVOYE
    sentAt != null -> DisplayStatus.MODIFIE
    generatedAt != null && updatedAt <= generatedAt -> DisplayStatus.PRET
    else -> DisplayStatus.BROUILLON
}
