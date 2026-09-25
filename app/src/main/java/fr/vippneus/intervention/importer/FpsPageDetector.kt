package fr.vippneus.intervention.importer

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import fr.vippneus.intervention.pdf.FpsTemplate
import fr.vippneus.intervention.pdf.PdfPages
import java.io.File
import kotlin.math.abs
import kotlin.math.sqrt

/**
 * Reconnaît une « Fiche d'intervention presse mobile » (vierge ou remplie) en 1re page d'un PDF,
 * par comparaison d'une miniature avec la fiche vierge (corrélation ≈ 0,8 pour une fiche, < 0,2 sinon).
 */
object FpsPageDetector {
    private const val W = 60
    private const val H = 85
    private const val THRESHOLD = 0.5

    @Volatile
    private var reference: DoubleArray? = null

    fun isFpsForm(context: Context, pdf: File, pageWidth: Float, pageHeight: Float): Boolean {
        if (pageHeight <= 0f || abs(pageWidth / pageHeight - FpsTemplate.PAGE_W / FpsTemplate.PAGE_H) > 0.05f) return false
        return try {
            val page = PdfPages.render(pdf, 0, W * 3)
            val score = correlation(gray(page), reference(context))
            page.recycle()
            score > THRESHOLD
        } catch (_: Throwable) {
            false
        }
    }

    private fun reference(context: Context): DoubleArray = reference ?: synchronized(this) {
        reference ?: context.applicationContext.assets.open(FpsTemplate.BACKGROUND_ASSET).use { input ->
            val bmp = BitmapFactory.decodeStream(input, null, BitmapFactory.Options().apply { inSampleSize = 4 })!!
            gray(bmp).also { bmp.recycle() }
        }.also { reference = it }
    }

    private fun gray(src: Bitmap): DoubleArray {
        val small = Bitmap.createScaledBitmap(src, W, H, true)
        val px = IntArray(W * H)
        small.getPixels(px, 0, W, 0, 0, W, H)
        if (small !== src) small.recycle()
        return DoubleArray(px.size) { i ->
            val c = px[i]
            0.299 * ((c shr 16) and 0xFF) + 0.587 * ((c shr 8) and 0xFF) + 0.114 * (c and 0xFF)
        }
    }

    private fun correlation(a: DoubleArray, b: DoubleArray): Double {
        val ma = a.average()
        val mb = b.average()
        var num = 0.0
        var da = 0.0
        var db = 0.0
        for (i in a.indices) {
            val x = a[i] - ma
            val y = b[i] - mb
            num += x * y
            da += x * x
            db += y * y
        }
        return if (da == 0.0 || db == 0.0) 0.0 else num / sqrt(da * db)
    }
}
