package fr.vippneus.intervention.pdf

import android.content.Context
import com.tom_roush.pdfbox.multipdf.PDFMergerUtility
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.PDPage
import com.tom_roush.pdfbox.pdmodel.PDPageContentStream
import com.tom_roush.pdfbox.pdmodel.common.PDRectangle
import com.tom_roush.pdfbox.pdmodel.font.PDType0Font
import com.tom_roush.pdfbox.pdmodel.graphics.image.JPEGFactory
import com.tom_roush.pdfbox.util.Matrix
import fr.vippneus.intervention.data.AttachmentKind
import fr.vippneus.intervention.data.Intervention
import fr.vippneus.intervention.data.InterventionType
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException
import java.util.Calendar

/** Opérations de dessin et format de la page 1 d'une intervention. */
object PageOps {
    fun build(i: Intervention, m: TextMeasure): List<DrawOp> = when (i.type) {
        InterventionType.FPS -> FpsLayout.build(i.values, i.signature, i.adjust, m) + OverlayLayout.build(i.overlays, m)
        InterventionType.DOCUMENT ->
            (i.template?.let { TemplateLayout.build(it, i.values, i.signature, i.adjust, m) } ?: emptyList()) +
                OverlayLayout.build(i.overlays, m)
    }

    /** Libellé d'un champ (fiche FPS ou document reconnu), pour l'éditeur. */
    fun fieldLabel(i: Intervention, key: String): String? =
        FpsTemplate.fieldsByKey[key]?.label
            ?: i.template?.fields?.firstOrNull { it.key == key }?.label
            ?: FpsTemplate.choices.firstOrNull { it.key == key }?.label

    fun pageSize(i: Intervention): Pair<Float, Float> = when (i.type) {
        InterventionType.FPS -> FpsTemplate.PAGE_W to FpsTemplate.PAGE_H
        InterventionType.DOCUMENT -> (i.source?.pageWidth ?: PDRectangle.A4.width) to (i.source?.pageHeight ?: PDRectangle.A4.height)
    }
}

/**
 * Passage des coordonnées « page affichée » (origine en haut à gauche, en points)
 * à l'espace utilisateur PDF, en tenant compte de la zone visible (CropBox) et de la rotation.
 */
class PageMapper(
    private val llx: Float,
    private val lly: Float,
    private val urx: Float,
    private val ury: Float,
    private val rotation: Int,
) {
    fun x(dx: Float, dy: Float): Float = when (rotation) {
        90 -> llx + dy
        180 -> urx - dx
        270 -> urx - dy
        else -> llx + dx
    }

    fun y(dx: Float, dy: Float): Float = when (rotation) {
        90 -> lly + dx
        180 -> lly + dy
        270 -> ury - dx
        else -> ury - dy
    }

    /** Matrice de texte : sens de la ligne de base (a, b) et vertical du glyphe (c, d). */
    fun textMatrix(dx: Float, baseline: Float): Matrix {
        val (a, b, c, d) = when (rotation) {
            90 -> listOf(0f, 1f, -1f, 0f)
            180 -> listOf(-1f, 0f, 0f, -1f)
            270 -> listOf(0f, -1f, 1f, 0f)
            else -> listOf(1f, 0f, 0f, 1f)
        }
        return Matrix(a, b, c, d, x(dx, baseline), y(dx, baseline))
    }

    companion object {
        fun of(page: PDPage): PageMapper {
            val cb = page.cropBox
            return PageMapper(cb.lowerLeftX, cb.lowerLeftY, cb.upperRightX, cb.upperRightY, PdfPages.rotation(page))
        }

        fun plain(width: Float, height: Float) = PageMapper(0f, 0f, width, height, 0)
    }
}

class ExportException(message: String, cause: Throwable? = null) : Exception(message, cause)

/** Fabrique le PDF final : page 1 complétée + pages suivantes + documents joints. */
class PdfExporter(private val context: Context) {

    fun export(i: Intervention, dir: File, out: File, title: String, author: String) {
        val measure = Fonts.measure(context)
        val ops = PageOps.build(i, measure)
        val opened = mutableListOf<PDDocument>()
        try {
            val doc = when (i.type) {
                InterventionType.FPS -> buildFps(ops)
                InterventionType.DOCUMENT -> buildDocument(i, dir, ops)
            }
            opened += doc
            val merger = PDFMergerUtility()

            if (i.type == InterventionType.DOCUMENT && i.includeOriginal) {
                val src = i.source ?: throw ExportException("Document client introuvable")
                val original = loadDecrypted(File(dir, src.file))
                opened += original
                merger.appendDocument(doc, original)
            }

            for (a in i.attachments) {
                val f = File(dir, a.file)
                if (!f.exists()) throw ExportException("Pièce jointe introuvable : ${a.name}")
                try {
                    when (a.kind) {
                        AttachmentKind.PDF -> {
                            val src = loadDecrypted(f)
                            opened += src
                            merger.appendDocument(doc, src)
                        }
                        AttachmentKind.IMAGE -> PdfPages.addImagePage(doc, f)
                    }
                } catch (e: ExportException) {
                    throw e
                } catch (e: Exception) {
                    throw ExportException("Impossible d'ajouter « ${a.name} » : ${e.message}", e)
                }
            }

            doc.documentInformation.apply {
                this.title = title
                this.author = author
                creator = "VIP Pneus – Bons d'intervention"
                producer = "VIP Pneus"
                modificationDate = Calendar.getInstance()
                if (creationDate == null) creationDate = Calendar.getInstance()
            }

            out.parentFile?.mkdirs()
            val tmp = File(out.parentFile, out.name + ".tmp")
            doc.save(tmp)
            if (out.exists() && !out.delete()) throw IOException("Impossible de remplacer ${out.name}")
            if (!tmp.renameTo(out)) {
                tmp.copyTo(out, overwrite = true)
                tmp.delete()
            }
        } catch (e: ExportException) {
            throw e
        } catch (e: Exception) {
            throw ExportException("Erreur lors de la création du PDF : ${e.message ?: e.javaClass.simpleName}", e)
        } finally {
            opened.forEach { runCatching { it.close() } }
        }
    }

    private fun buildFps(ops: List<DrawOp>): PDDocument {
        val doc = PDDocument()
        try {
            val page = PDPage(PDRectangle(FpsTemplate.PAGE_W, FpsTemplate.PAGE_H))
            doc.addPage(page)
            val background = context.assets.open(FpsTemplate.BACKGROUND_ASSET).use { JPEGFactory.createFromStream(doc, it) }
            val font = loadFont(doc)
            PDPageContentStream(doc, page).use { cs ->
                cs.drawImage(background, 0f, 0f, FpsTemplate.PAGE_W, FpsTemplate.PAGE_H)
                drawOps(cs, ops, PageMapper.plain(FpsTemplate.PAGE_W, FpsTemplate.PAGE_H), font)
            }
            return doc
        } catch (e: Exception) {
            doc.close()
            throw e
        }
    }

    private fun buildDocument(i: Intervention, dir: File, ops: List<DrawOp>): PDDocument {
        val src = i.source ?: throw ExportException("Aucun document client importé")
        val file = File(dir, src.file)
        if (!file.exists()) throw ExportException("Document client introuvable : ${src.name}")
        val doc = loadDecrypted(file)
        try {
            if (ops.isNotEmpty()) {
                val page = doc.getPage(0)
                val font = loadFont(doc)
                PDPageContentStream(doc, page, PDPageContentStream.AppendMode.APPEND, true, true).use { cs ->
                    drawOps(cs, ops, PageMapper.of(page), font)
                }
            }
            return doc
        } catch (e: Exception) {
            doc.close()
            throw e
        }
    }

    private fun loadFont(doc: PDDocument): PDType0Font =
        context.assets.open(Fonts.ASSET).use { PDType0Font.load(doc, it) }

    private fun drawOps(cs: PDPageContentStream, ops: List<DrawOp>, map: PageMapper, font: PDType0Font) {
        val safe = SafeText(font)
        cs.setNonStrokingColor(Ink.R, Ink.G, Ink.B)
        cs.setStrokingColor(Ink.R, Ink.G, Ink.B)
        cs.setLineCapStyle(1)
        cs.setLineJoinStyle(1)
        for (op in ops) {
            when (op) {
                is TextOp -> {
                    val lines = op.lines.map { it to safe.clean(it.text) }.filter { it.second.isNotBlank() }
                    if (lines.isEmpty()) continue
                    cs.beginText()
                    cs.setFont(font, op.size)
                    for ((line, text) in lines) {
                        cs.setTextMatrix(map.textMatrix(line.x, line.baseline))
                        cs.showText(text)
                    }
                    cs.endText()
                }
                is CrossOp -> {
                    cs.setLineWidth(op.stroke)
                    val l = op.cx - op.half
                    val r = op.cx + op.half
                    val t = op.cy - op.half
                    val b = op.cy + op.half
                    cs.moveTo(map.x(l, t), map.y(l, t))
                    cs.lineTo(map.x(r, b), map.y(r, b))
                    cs.moveTo(map.x(l, b), map.y(l, b))
                    cs.lineTo(map.x(r, t), map.y(r, t))
                    cs.stroke()
                }
                is SignatureOp -> {
                    val fit = op.signature.fitInto(op.box)
                    cs.setLineWidth(fit.pen)
                    for (stroke in op.signature.strokes) {
                        val segs = strokePath(stroke, fit)
                        if (segs.isEmpty()) continue
                        var cx = 0f
                        var cy = 0f
                        for (s in segs) {
                            when (s) {
                                is PathSeg.MoveTo -> {
                                    cs.moveTo(map.x(s.x, s.y), map.y(s.x, s.y)); cx = s.x; cy = s.y
                                }
                                is PathSeg.LineTo -> {
                                    cs.lineTo(map.x(s.x, s.y), map.y(s.x, s.y)); cx = s.x; cy = s.y
                                }
                                is PathSeg.QuadTo -> {
                                    // Courbe quadratique convertie en cubique (seule forme connue du PDF)
                                    val c1x = cx + 2f / 3f * (s.cx - cx)
                                    val c1y = cy + 2f / 3f * (s.cy - cy)
                                    val c2x = s.x + 2f / 3f * (s.cx - s.x)
                                    val c2y = s.y + 2f / 3f * (s.cy - s.y)
                                    cs.curveTo(
                                        map.x(c1x, c1y), map.y(c1x, c1y),
                                        map.x(c2x, c2y), map.y(c2x, c2y),
                                        map.x(s.x, s.y), map.y(s.x, s.y),
                                    )
                                    cx = s.x; cy = s.y
                                }
                            }
                        }
                        cs.stroke()
                    }
                }
            }
        }
    }

    /** Remplace les caractères absents de la police (émojis...) pour ne jamais faire échouer l'export. */
    private class SafeText(private val font: PDType0Font) {
        private val cache = HashMap<Int, Boolean>()

        fun clean(s: String): String {
            val sb = StringBuilder(s.length)
            var i = 0
            while (i < s.length) {
                val cp = s.codePointAt(i)
                i += Character.charCount(cp)
                when {
                    cp == '\t'.code -> sb.append(' ')
                    Character.isISOControl(cp) -> Unit
                    supported(cp) -> sb.appendCodePoint(cp)
                    Character.isSpaceChar(cp) || Character.isWhitespace(cp) -> sb.append(' ')
                    else -> sb.append('?')
                }
            }
            return sb.toString()
        }

        private fun supported(cp: Int): Boolean = cache.getOrPut(cp) {
            try {
                font.encode(String(Character.toChars(cp)))
                true
            } catch (_: Exception) {
                false
            }
        }
    }

    companion object {
        /** Ouvre un PDF ; s'il est protégé (sans mot de passe d'ouverture), la protection est retirée. */
        fun loadDecrypted(file: File): PDDocument {
            val doc = PDDocument.load(file)
            if (!doc.isEncrypted) return doc
            return try {
                doc.isAllSecurityToBeRemoved = true
                val bytes = ByteArrayOutputStream().also { doc.save(it) }.toByteArray()
                PDDocument.load(bytes)
            } finally {
                doc.close()
            }
        }
    }
}
