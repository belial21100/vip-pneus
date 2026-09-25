package fr.vippneus.intervention.pdf

import fr.vippneus.intervention.data.DocTemplate
import fr.vippneus.intervention.data.FieldAdjust
import fr.vippneus.intervention.data.PlacedField
import fr.vippneus.intervention.data.PlacedPanel
import fr.vippneus.intervention.data.Overlay
import fr.vippneus.intervention.data.OverlayKind
import fr.vippneus.intervention.data.SignatureData
import kotlin.math.max
import kotlin.math.min

/** Rectangle en points, origine en haut à gauche de la page. */
data class Box(val left: Float, val top: Float, val right: Float, val bottom: Float) {
    val width: Float get() = right - left
    val height: Float get() = bottom - top
    val centerX: Float get() = (left + right) / 2f
    val centerY: Float get() = (top + bottom) / 2f

    fun contains(x: Float, y: Float, pad: Float = 0f): Boolean =
        x >= left - pad && x <= right + pad && y >= top - pad && y <= bottom + pad

    fun offset(dx: Float, dy: Float) = Box(left + dx, top + dy, right + dx, bottom + dy)
    fun union(o: Box) = Box(min(left, o.left), min(top, o.top), max(right, o.right), max(bottom, o.bottom))
    fun inflate(d: Float) = Box(left - d, top - d, right + d, bottom + d)
}

/** Largeur d'un texte pour un corps donné (en points). */
fun interface TextMeasure {
    fun width(text: String, size: Float): Float
}

/** Métriques de Source Sans 3, en fraction du corps. */
object Typo {
    const val CAP = 0.66f
    const val ASCENT = 0.80f
    const val DESCENT = 0.24f
    const val LINE = 1.18f
}

/**
 * Opérations de dessin de la page 1, en points depuis le coin haut-gauche.
 * Le même résultat sert à l'aperçu écran et à l'écriture du PDF : ce que l'on voit est ce que l'on obtient.
 */
sealed interface DrawOp {
    /** Clé du champ FPS ou identifiant de l'élément libre. */
    val key: String
    val bounds: Box
}

data class TextLine(val text: String, val x: Float, val baseline: Float, val width: Float)

data class TextOp(
    override val key: String,
    val lines: List<TextLine>,
    val size: Float,
    override val bounds: Box,
) : DrawOp

data class CrossOp(
    override val key: String,
    val cx: Float,
    val cy: Float,
    val half: Float,
    val stroke: Float,
) : DrawOp {
    override val bounds: Box get() = Box(cx - half, cy - half, cx + half, cy + half)
}

data class SignatureOp(
    override val key: String,
    val signature: SignatureData,
    val box: Box,
) : DrawOp {
    override val bounds: Box get() = box
}

/** Encart encadré (cadre, titre, lignes de texte), déplacé et agrandi d'un seul bloc. */
data class PanelOp(
    override val key: String,
    val frame: Box,
    val stroke: Float,
    val texts: List<TextOp>,
) : DrawOp {
    override val bounds: Box get() = frame
}

object TextLayout {

    /** Découpe un texte en lignes d'au plus [maxW] points (retours à la ligne saisis respectés). */
    fun wrap(text: String, maxW: Float, size: Float, m: TextMeasure): List<String> {
        val out = mutableListOf<String>()
        for (para in text.split('\n')) {
            val words = para.trim().split(Regex("\\s+")).filter { it.isNotEmpty() }
            if (words.isEmpty()) {
                out += ""
                continue
            }
            var cur = ""
            for (w in words) {
                val cand = if (cur.isEmpty()) w else "$cur $w"
                if (m.width(cand, size) <= maxW) {
                    cur = cand
                    continue
                }
                if (cur.isNotEmpty()) {
                    out += cur
                    cur = ""
                }
                if (m.width(w, size) <= maxW) {
                    cur = w
                    continue
                }
                // Mot plus long que la zone : coupé au caractère
                var piece = ""
                for (ch in w) {
                    val next = piece + ch
                    if (piece.isNotEmpty() && m.width(next, size) > maxW) {
                        out += piece
                        piece = ch.toString()
                    } else {
                        piece = next
                    }
                }
                cur = piece
            }
            if (cur.isNotEmpty()) out += cur
        }
        while (out.size > 1 && out.last().isEmpty()) out.removeAt(out.lastIndex)
        while (out.size > 1 && out.first().isEmpty()) out.removeAt(0)
        return out
    }

    fun textOp(key: String, lines: List<String>, xs: (String, Float) -> Float, firstBaseline: Float, size: Float, m: TextMeasure): TextOp {
        val lineH = size * Typo.LINE
        val placed = lines.mapIndexed { i, t ->
            val w = m.width(t, size)
            TextLine(t, xs(t, w), firstBaseline + i * lineH, w)
        }
        var bounds: Box? = null
        for (l in placed) {
            val b = Box(l.x, l.baseline - size * Typo.ASCENT, l.x + max(l.width, size * 0.3f), l.baseline + size * Typo.DESCENT)
            bounds = bounds?.union(b) ?: b
        }
        return TextOp(key, placed, size, bounds ?: Box(0f, 0f, 0f, 0f))
    }

    /** Place un champ du gabarit : réduction automatique du corps si le texte est trop long. */
    fun layoutField(f: FpsTemplate.Field, text: String, adj: FieldAdjust?, m: TextMeasure): TextOp {
        val manual = adj?.fontSize
        var size = manual ?: f.fontSize
        val maxW = f.box.width
        var lines: List<String>
        if (f.maxLines <= 1) {
            val single = text.trim().replace(Regex("\\s*\\n\\s*"), " ")
            lines = listOf(single)
            if (manual == null) {
                while (size > f.minFontSize && m.width(single, size) > maxW) size = max(f.minFontSize, size - 0.5f)
            }
        } else {
            lines = wrap(text.trim(), maxW, size, m)
            if (manual == null) {
                while (size > f.minFontSize && (lines.size > f.maxLines || lines.any { m.width(it, size) > maxW + 0.01f })) {
                    size = max(f.minFontSize, size - 0.5f)
                    lines = wrap(text.trim(), maxW, size, m)
                }
            }
        }
        val lineH = size * Typo.LINE
        val cap = size * Typo.CAP
        val firstBaseline = f.firstLineY?.let { it + cap / 2f }
            ?: (f.box.centerY - ((lines.size - 1) * lineH + cap) / 2f + cap)
        val dx = adj?.dx ?: 0f
        val dy = adj?.dy ?: 0f
        return textOp(
            key = f.key,
            lines = lines,
            xs = { _, w ->
                dx + when (f.align) {
                    FpsTemplate.HAlign.START -> f.box.left
                    FpsTemplate.HAlign.CENTER -> f.box.centerX - w / 2f
                }
            },
            firstBaseline = firstBaseline + dy,
            size = size,
            m = m,
        )
    }
}

/** Mise en page de la fiche FPS à partir des valeurs saisies. */
object FpsLayout {
    fun build(
        values: Map<String, String>,
        signature: SignatureData?,
        adjust: Map<String, FieldAdjust>,
        m: TextMeasure,
    ): List<DrawOp> {
        val ops = mutableListOf<DrawOp>()
        for (f in FpsTemplate.fields) {
            val text = values[f.key]?.trim().orEmpty()
            if (text.isEmpty()) continue
            ops += TextLayout.layoutField(f, text, adjust[f.key], m)
        }
        for (c in FpsTemplate.choices) {
            val v = values[c.key] ?: continue
            val opt = c.options.firstOrNull { it.value == v } ?: continue
            val adj = adjust[c.key]
            val half = FpsTemplate.crossHalf * (adj?.scale ?: 1f)
            ops += CrossOp(c.key, opt.cx + (adj?.dx ?: 0f), opt.cy + (adj?.dy ?: 0f), half, max(0.9f, half * 0.3f))
        }
        if (signature != null && !signature.isEmpty) {
            val adj = adjust[FpsTemplate.K.SIGNATURE]
            ops += SignatureOp(FpsTemplate.K.SIGNATURE, signature, scaledBox(FpsTemplate.signatureBox, adj))
        }
        return ops
    }

    fun scaledBox(b: Box, adj: FieldAdjust?): Box {
        val s = adj?.scale ?: 1f
        val w = b.width * s
        val h = b.height * s
        val cx = b.centerX + (adj?.dx ?: 0f)
        val cy = b.centerY + (adj?.dy ?: 0f)
        return Box(cx - w / 2f, cy - h / 2f, cx + w / 2f, cy + h / 2f)
    }
}

fun PlacedField.toField() = FpsTemplate.Field(
    key = key,
    label = label,
    box = Box(left, top, right, bottom),
    fontSize = fontSize,
    align = if (center) FpsTemplate.HAlign.CENTER else FpsTemplate.HAlign.START,
    maxLines = maxLines,
    minFontSize = minFontSize,
)

/** Mise en page d'un document client reconnu (champs placés automatiquement + signature). */
object TemplateLayout {
    fun build(
        t: DocTemplate,
        values: Map<String, String>,
        signature: SignatureData?,
        adjust: Map<String, FieldAdjust>,
        m: TextMeasure,
    ): List<DrawOp> {
        val ops = mutableListOf<DrawOp>()
        for (f in t.fields) {
            val raw = values[f.key]?.trim().orEmpty()
            if (raw.isEmpty()) continue
            val text = if (f.unit.isNotEmpty() && raw.matches(Regex("[0-9 .,]+"))) "$raw ${f.unit}" else raw
            ops += TextLayout.layoutField(f.toField(), text, adjust[f.key], m)
        }
        t.panel?.let { p -> PanelLayout.build(p, values, adjust[p.key], m)?.let { ops += it } }
        val box = t.signature
        if (box != null && signature != null && !signature.isEmpty) {
            ops += SignatureOp(
                FpsTemplate.K.SIGNATURE, signature,
                FpsLayout.scaledBox(Box(box.left, box.top, box.right, box.bottom), adjust[FpsTemplate.K.SIGNATURE]),
            )
        }
        return ops
    }
}

/**
 * Mise en page d'un encart : les valeurs saisies (réduites si besoin), précédées d'un titre discret s'il est demandé,
 * cadre ajusté au contenu et posé sur son bord bas ; l'échelle d'ajustement agrandit le tout.
 */
object PanelLayout {
    const val TITLE_SIZE = 7.8f

    fun build(p: PlacedPanel, values: Map<String, String>, adj: FieldAdjust?, m: TextMeasure): PanelOp? {
        val filled = p.lines.mapNotNull { l -> values[l.key]?.trim()?.takeIf { it.isNotEmpty() }?.let { l to it } }
        if (filled.isEmpty()) return null
        val s = adj?.scale ?: 1f
        val left = p.left + (adj?.dx ?: 0f)
        val width = (p.right - p.left) * s
        val pad = 5f * s
        val innerW = width - 2 * pad

        // Positions depuis le haut du cadre (lignes, corps, 1re ligne de base)
        val blocks = mutableListOf<Triple<List<String>, Float, Float>>()
        var y = pad
        if (p.printTitle) {
            val titleSize = TITLE_SIZE * s
            y += titleSize * Typo.ASCENT
            blocks += Triple(listOf(p.title), titleSize, y)
            y += titleSize * Typo.DESCENT + 2f * s
        }
        for ((l, text) in filled) {
            var size = l.fontSize * s
            val minSize = l.minFontSize * s
            var lines = TextLayout.wrap(text, innerW, size, m)
            while (size > minSize && (lines.size > l.maxLines || lines.any { m.width(it, size) > innerW + 0.01f })) {
                size = max(minSize, size - 0.5f)
                lines = TextLayout.wrap(text, innerW, size, m)
            }
            val first = y + size * Typo.ASCENT
            blocks += Triple(lines, size, first)
            y = first + (lines.size - 1) * size * Typo.LINE + size * Typo.DESCENT + 1.5f * s
        }
        val height = y - 1.5f * s + pad
        val bottom = p.bottom + (adj?.dy ?: 0f)
        val top = bottom - height
        val texts = blocks.mapIndexed { i, (lines, size, base) ->
            TextLayout.textOp("${p.key}#$i", lines, { _, _ -> left + pad }, top + base, size, m)
        }
        return PanelOp(p.key, Box(left, top, left + width, bottom), max(0.5f, 0.7f * s), texts)
    }
}

/** Mise en page des éléments posés librement. */
object OverlayLayout {
    fun build(overlays: List<Overlay>, m: TextMeasure): List<DrawOp> = overlays.mapNotNull { o ->
        when (o.kind) {
            OverlayKind.TEXT -> {
                if (o.text.isBlank()) null
                else TextLayout.textOp(
                    key = o.id,
                    lines = o.text.trimEnd().split('\n'),
                    xs = { _, _ -> o.x },
                    firstBaseline = o.y + o.fontSize * Typo.ASCENT,
                    size = o.fontSize,
                    m = m,
                )
            }
            OverlayKind.CROSS -> {
                val half = o.width / 2f
                CrossOp(o.id, o.x + half, o.y + half, half * 0.8f, max(0.8f, o.width * 0.1f))
            }
            OverlayKind.SIGNATURE -> o.signature?.takeUnless { it.isEmpty }?.let {
                SignatureOp(o.id, it, Box(o.x, o.y, o.x + o.width, o.y + o.height))
            }
        }
    }
}

/** Mise à l'échelle d'une signature dans un cadre (proportions conservées, centrée). */
class SignatureFit(val scale: Float, val dx: Float, val dy: Float, val pen: Float) {
    fun x(v: Float) = dx + v * scale
    fun y(v: Float) = dy + v * scale
}

fun SignatureData.fitInto(box: Box): SignatureFit {
    var minX = Float.MAX_VALUE
    var minY = Float.MAX_VALUE
    var maxX = -Float.MAX_VALUE
    var maxY = -Float.MAX_VALUE
    for (s in strokes) {
        var i = 0
        while (i + 1 < s.size) {
            minX = min(minX, s[i]); maxX = max(maxX, s[i])
            minY = min(minY, s[i + 1]); maxY = max(maxY, s[i + 1])
            i += 2
        }
    }
    if (minX > maxX) return SignatureFit(1f, box.left, box.top, 1f)
    val pad = penWidth / 2f
    minX -= pad; minY -= pad; maxX += pad; maxY += pad
    val w = max(1f, maxX - minX)
    val h = max(1f, maxY - minY)
    val s = min(box.width / w, box.height / h)
    val dx = box.left + (box.width - w * s) / 2f - minX * s
    val dy = box.top + (box.height - h * s) / 2f - minY * s
    return SignatureFit(s, dx, dy, (penWidth * s).coerceIn(0.6f, 3.5f))
}

/** Segments lissés d'un trait de signature (en points de la page). */
sealed interface PathSeg {
    data class MoveTo(val x: Float, val y: Float) : PathSeg
    data class LineTo(val x: Float, val y: Float) : PathSeg
    data class QuadTo(val cx: Float, val cy: Float, val x: Float, val y: Float) : PathSeg
}

fun strokePath(points: List<Float>, fit: SignatureFit): List<PathSeg> {
    val n = points.size / 2
    if (n == 0) return emptyList()
    fun px(i: Int) = fit.x(points[2 * i])
    fun py(i: Int) = fit.y(points[2 * i + 1])
    val out = mutableListOf<PathSeg>(PathSeg.MoveTo(px(0), py(0)))
    when (n) {
        1 -> out += PathSeg.LineTo(px(0) + 0.01f, py(0) + 0.01f)
        2 -> out += PathSeg.LineTo(px(1), py(1))
        else -> {
            for (i in 1 until n - 1) {
                out += PathSeg.QuadTo(px(i), py(i), (px(i) + px(i + 1)) / 2f, (py(i) + py(i + 1)) / 2f)
            }
            out += PathSeg.LineTo(px(n - 1), py(n - 1))
        }
    }
    return out
}
