package fr.vippneus.intervention.pdf

import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.PDPage
import com.tom_roush.pdfbox.pdmodel.PDPageContentStream
import com.tom_roush.pdfbox.pdmodel.common.PDRectangle
import com.tom_roush.pdfbox.pdmodel.graphics.image.JPEGFactory
import com.tom_roush.pdfbox.rendering.PDFRenderer
import java.io.File
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/** Lecture, rendu et petites manipulations de PDF. */
object PdfPages {
    private val renderLock = Any()

    data class Info(val pageCount: Int, val width: Float, val height: Float)

    /** Nombre de pages et taille de la page 1 telle qu'affichée (rotation appliquée). */
    fun info(file: File): Info = PDDocument.load(file).use { doc ->
        val (w, h) = displaySize(doc.getPage(0))
        Info(doc.numberOfPages, w, h)
    }

    fun rotation(page: PDPage): Int {
        val r = ((page.rotation % 360) + 360) % 360
        return (r / 90) * 90
    }

    fun displaySize(page: PDPage): Pair<Float, Float> {
        val crop = page.cropBox
        return if (rotation(page) % 180 == 90) crop.height to crop.width else crop.width to crop.height
    }

    fun pageCount(file: File): Int = synchronized(renderLock) {
        try {
            ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY).use { pfd ->
                PdfRenderer(pfd).use { it.pageCount }
            }
        } catch (_: Exception) {
            PDDocument.load(file).use { it.numberOfPages }
        }
    }

    /** Rendu d'une page en image, largeur imposée (rendu natif Android, sinon PDFBox). */
    fun render(file: File, index: Int, targetWidth: Int): Bitmap = synchronized(renderLock) {
        try {
            ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY).use { pfd ->
                PdfRenderer(pfd).use { renderer ->
                    renderer.openPage(index).use { page ->
                        val h = max(1, (targetWidth.toFloat() * page.height / page.width).roundToInt())
                        val bmp = Bitmap.createBitmap(targetWidth, h, Bitmap.Config.ARGB_8888)
                        bmp.eraseColor(Color.WHITE)
                        page.render(bmp, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                        bmp
                    }
                }
            }
        } catch (e: Throwable) {
            // Rendu natif indisponible ou en échec : rendu PDFBox (plus lent)
            PDDocument.load(file).use { doc ->
                val (w, _) = displaySize(doc.getPage(index))
                PDFRenderer(doc).renderImage(index, targetWidth / w)
            }
        }
    }

    /** Ajoute une page A4 (portrait ou paysage selon l'image) contenant la photo, avec une petite marge. */
    fun addImagePage(doc: PDDocument, image: File, margin: Float = 18f) {
        val bmp = Images.decode(image)
        try {
            val xobject = JPEGFactory.createFromImage(doc, bmp, 0.85f)
            val a4 = PDRectangle.A4
            val rect = if (bmp.width > bmp.height) PDRectangle(a4.height, a4.width) else PDRectangle(a4.width, a4.height)
            val page = PDPage(rect)
            doc.addPage(page)
            val s = min((rect.width - 2 * margin) / bmp.width, (rect.height - 2 * margin) / bmp.height)
            val w = bmp.width * s
            val h = bmp.height * s
            PDPageContentStream(doc, page).use { cs ->
                cs.drawImage(xobject, (rect.width - w) / 2f, (rect.height - h) / 2f, w, h)
            }
        } finally {
            bmp.recycle()
        }
    }

    /** Transforme une photo en PDF d'une page (pour compléter un document photographié). */
    fun imageToPdf(image: File, out: File) {
        PDDocument().use { doc ->
            addImagePage(doc, image, margin = 0f)
            doc.save(out)
        }
    }
}
