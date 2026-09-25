package fr.vippneus.intervention.importer

import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.text.PDFTextStripper
import com.tom_roush.pdfbox.text.TextPosition
import java.text.Normalizer
import java.util.Locale
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/**
 * Morceau de texte continu d'une page, avec sa position (points, origine en haut à gauche,
 * rotation de la page appliquée) — les mêmes coordonnées que les éléments posés sur la page.
 */
data class Run(
    val page: Int,
    val text: String,
    val x0: Float,
    val x1: Float,
    val baseline: Float,
    val size: Float,
) {
    val top: Float get() = baseline - size * 0.8f
    val norm: String by lazy { DocText.normalize(text) }
}

/** Texte positionné d'un PDF, et petites requêtes pour y retrouver étiquettes et valeurs. */
class DocText(val runs: List<Run>) {

    val isEmpty: Boolean get() = runs.none { it.text.isNotBlank() }

    fun pageRuns(page: Int) = runs.filter { it.page == page }

    /** Lignes reconstituées (morceaux de même ligne de base, de gauche à droite). */
    fun lines(page: Int? = null): List<String> {
        val src = if (page == null) runs else pageRuns(page)
        val out = mutableListOf<String>()
        val byPage = src.groupBy { it.page }.toSortedMap()
        for ((_, list) in byPage) {
            val sorted = list.sortedWith(compareBy({ it.baseline }, { it.x0 }))
            var cur = mutableListOf<Run>()
            for (r in sorted) {
                val ref = cur.firstOrNull()
                if (ref != null && abs(r.baseline - ref.baseline) > max(2f, 0.35f * min(r.size, ref.size))) {
                    out += cur.sortedBy { it.x0 }.joinToString(" ") { it.text.trim() }
                    cur = mutableListOf()
                }
                cur += r
            }
            if (cur.isNotEmpty()) out += cur.sortedBy { it.x0 }.joinToString(" ") { it.text.trim() }
        }
        return out.map { it.replace(Regex("\\s+"), " ").trim() }.filter { it.isNotEmpty() }
    }

    fun fullText(page: Int? = null): String = lines(page).joinToString("\n")

    fun contains(vararg words: String): Boolean {
        val all = normalize(runs.joinToString(" ") { it.text })
        return words.all { all.contains(normalize(it)) }
    }

    /** Étiquette : morceau dont le texte commence par [label] (ou lui est égal si [exact]). */
    fun label(label: String, page: Int? = null, exact: Boolean = false): Run? {
        val n = normalize(label)
        return runs.asSequence()
            .filter { page == null || it.page == page }
            .filter { if (exact) it.norm.trimEnd(':', ' ') == n.trimEnd(':', ' ') else it.norm.startsWith(n) }
            .sortedWith(compareBy({ it.page }, { it.baseline }, { it.x0 }))
            .firstOrNull()
    }

    private fun looksLikeLabel(r: Run) = r.text.trim().endsWith(":")

    /** Valeur écrite à droite d'une étiquette, sur la même ligne. */
    fun rightOf(label: Run, maxGap: Float = 150f): String? {
        val sameLine = runs.filter {
            it.page == label.page && it !== label && it.x0 >= label.x1 - 1f &&
                abs(it.baseline - label.baseline) <= max(2.5f, 0.4f * label.size)
        }.sortedBy { it.x0 }
        val parts = mutableListOf<String>()
        var lastX = label.x1
        for (r in sameLine) {
            if (r.x0 - lastX > (if (parts.isEmpty()) maxGap else 25f)) break
            if (looksLikeLabel(r)) break
            parts += r.text.trim()
            lastX = r.x1
        }
        return parts.joinToString(" ").trim().ifEmpty { null }
    }

    /** Premier morceau à droite d'une étiquette (utile pour enchaîner sur les lignes suivantes). */
    fun firstRightOf(label: Run, maxGap: Float = 150f): Run? = runs.filter {
        it.page == label.page && it !== label && it.x0 >= label.x1 - 1f && it.x0 - label.x1 <= maxGap &&
            abs(it.baseline - label.baseline) <= max(2.5f, 0.4f * label.size)
    }.minByOrNull { it.x0 }

    /**
     * Lignes d'une même colonne sous un morceau de texte (bloc d'adresse...) :
     * morceaux dont le début est dans [x0 - tolérance, x0 + largeur], regroupés par ligne.
     */
    fun belowLines(anchor: Run, maxLines: Int = 8, xTolerance: Float = 4f, maxWidth: Float = 110f, maxStep: Float = 2.6f): List<String> {
        val candidates = runs.filter {
            it.page == anchor.page && it.baseline > anchor.baseline + 1f &&
                it.x0 >= anchor.x0 - xTolerance && it.x0 <= anchor.x0 + maxWidth
        }.sortedWith(compareBy({ it.baseline }, { it.x0 }))
        val lines = mutableListOf<MutableList<Run>>()
        for (r in candidates) {
            val cur = lines.lastOrNull()
            if (cur != null && abs(r.baseline - cur[0].baseline) <= max(1.5f, 0.3f * r.size)) cur += r else lines += mutableListOf(r)
        }
        val out = mutableListOf<String>()
        var last = anchor.baseline
        for (l in lines) {
            if (out.size >= maxLines) break
            val size = max(l.maxOf { it.size }, anchor.size * 0.8f)
            if (l[0].baseline - last > maxStep * size) break
            out += l.sortedBy { it.x0 }.joinToString(" ") { it.text.trim() }
            last = l[0].baseline
        }
        return out
    }

    /** Valeur à droite d'une étiquette, prolongée sur les lignes suivantes de la même colonne. */
    fun valueBlock(label: Run, maxGap: Float = 150f, maxLines: Int = 3): List<String> {
        val first = firstRightOf(label, maxGap) ?: return emptyList()
        val out = mutableListOf(first.text.trim())
        val labelsX = label.x0
        var last = first
        val next = runs.filter { it.page == first.page && abs(it.x0 - first.x0) < 2f && it.baseline > first.baseline + 1f }
            .sortedBy { it.baseline }
        for (r in next) {
            if (out.size >= maxLines) break
            if (r.baseline - last.baseline > 1.45f * r.size) break
            // une autre étiquette commence sur cette ligne, à gauche : c'est un autre champ
            val otherLabel = runs.any { it.page == r.page && abs(it.baseline - r.baseline) < 2f && abs(it.x0 - labelsX) < 3f }
            if (otherLabel) break
            out += r.text.trim()
            last = r
        }
        return out
    }

    companion object {
        fun normalize(s: String): String =
            Normalizer.normalize(s.lowercase(Locale.FRANCE), Normalizer.Form.NFD)
                .replace(Regex("\\p{M}+"), "")
                .replace('’', '\'')
                .replace(Regex("\\s+"), " ")
                .trim()

        /** Lit le texte des [maxPages] premières pages. */
        fun read(doc: PDDocument, maxPages: Int = 6): DocText {
            val collector = GlyphCollector()
            collector.startPage = 1
            collector.endPage = minOf(doc.numberOfPages, maxPages)
            collector.getText(doc)
            return DocText(group(collector.glyphs))
        }

        private data class Glyph(val page: Int, val x: Float, val y: Float, val w: Float, val size: Float, val text: String)

        private class GlyphCollector : PDFTextStripper() {
            val glyphs = mutableListOf<Glyph>()

            init {
                sortByPosition = true
            }

            override fun processTextPosition(text: TextPosition) {
                val u = text.unicode ?: return
                if (u.isEmpty()) return
                val size = if (text.fontSizeInPt > 0f) text.fontSizeInPt else text.heightDir
                glyphs += Glyph(currentPageNo - 1, text.xDirAdj, text.yDirAdj, text.widthDirAdj, size, u)
            }
        }

        private fun group(glyphs: List<Glyph>): List<Run> {
            val out = mutableListOf<Run>()
            for ((page, list) in glyphs.groupBy { it.page }) {
                // Regroupement par ligne de base
                val sorted = list.sortedWith(compareBy({ it.y }, { it.x }))
                val lines = mutableListOf<MutableList<Glyph>>()
                for (g in sorted) {
                    val line = lines.lastOrNull()
                    if (line != null && abs(line[0].y - g.y) <= max(1.5f, 0.3f * g.size)) line += g else lines += mutableListOf(g)
                }
                for (line in lines) {
                    val gl = line.sortedBy { it.x }
                    val baseline = gl.map { it.y }.average().toFloat()
                    var cur = mutableListOf<Glyph>()
                    var text = StringBuilder()
                    fun flush() {
                        val t = text.toString().trim()
                        val first = cur.firstOrNull { it.text.isNotBlank() }
                        val last = cur.lastOrNull { it.text.isNotBlank() }
                        if (t.isNotEmpty() && first != null && last != null) {
                            out += Run(page, t, first.x, last.x + last.w, baseline, cur.maxOf { it.size })
                        }
                        cur = mutableListOf()
                        text = StringBuilder()
                    }
                    var prev: Glyph? = null
                    for (g in gl) {
                        val p = prev
                        if (p != null) {
                            // Texte doublé (faux gras) : même caractère quasiment au même endroit
                            if (g.text == p.text && abs(g.x - p.x) < max(0.6f, p.w * 0.5f)) continue
                            val gap = g.x - (p.x + p.w)
                            if (gap > max(4f, g.size * 0.9f)) {
                                flush()
                            } else if (gap > g.size * 0.18f && text.isNotEmpty() && !text.endsWith(" ") && g.text != " ") {
                                text.append(' ')
                            }
                        }
                        prev = g
                        if (cur.isEmpty() && g.text.isBlank()) continue
                        cur += g
                        text.append(g.text)
                    }
                    flush()
                }
            }
            return out
        }
    }
}
