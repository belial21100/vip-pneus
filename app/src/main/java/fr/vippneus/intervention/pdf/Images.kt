package fr.vippneus.intervention.pdf

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import androidx.exifinterface.media.ExifInterface
import java.io.File
import java.io.IOException
import kotlin.math.max
import kotlin.math.roundToInt

/** Lecture des photos (orientation EXIF appliquée, taille limitée, fond blanc). */
object Images {

    fun decode(file: File, maxSide: Int = 2400): Bitmap {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.path, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) throw IOException("Image illisible : ${file.name}")
        var sample = 1
        while (max(bounds.outWidth, bounds.outHeight) / (sample * 2) >= maxSide) sample *= 2
        val decoded = BitmapFactory.decodeFile(file.path, BitmapFactory.Options().apply { inSampleSize = sample })
            ?: throw IOException("Image illisible : ${file.name}")

        val orientation = try {
            ExifInterface(file.path).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)
        } catch (_: Exception) {
            ExifInterface.ORIENTATION_NORMAL
        }
        val m = Matrix()
        when (orientation) {
            ExifInterface.ORIENTATION_ROTATE_90 -> m.postRotate(90f)
            ExifInterface.ORIENTATION_ROTATE_180 -> m.postRotate(180f)
            ExifInterface.ORIENTATION_ROTATE_270 -> m.postRotate(270f)
            ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> m.postScale(-1f, 1f)
            ExifInterface.ORIENTATION_FLIP_VERTICAL -> m.postScale(1f, -1f)
            ExifInterface.ORIENTATION_TRANSPOSE -> { m.postRotate(90f); m.postScale(-1f, 1f) }
            ExifInterface.ORIENTATION_TRANSVERSE -> { m.postRotate(270f); m.postScale(-1f, 1f) }
        }
        val longest = max(decoded.width, decoded.height)
        if (longest > maxSide) {
            val s = maxSide.toFloat() / longest
            m.postScale(s, s)
        }
        val oriented = if (m.isIdentity) decoded else
            Bitmap.createBitmap(decoded, 0, 0, decoded.width, decoded.height, m, true).also {
                if (it !== decoded) decoded.recycle()
            }
        // Aplatit la transparence éventuelle sur fond blanc (JPEG dans le PDF)
        val out = Bitmap.createBitmap(oriented.width, oriented.height, Bitmap.Config.ARGB_8888)
        Canvas(out).apply {
            drawColor(Color.WHITE)
            drawBitmap(oriented, 0f, 0f, null)
        }
        if (out !== oriented) oriented.recycle()
        return out
    }

    /** Miniature pour l'affichage. */
    fun thumbnail(file: File, maxSide: Int): Bitmap? = try {
        val b = decode(file, maxSide * 2)
        val s = maxSide.toFloat() / max(b.width, b.height)
        if (s >= 1f) b else Bitmap.createScaledBitmap(b, (b.width * s).roundToInt(), (b.height * s).roundToInt(), true)
            .also { if (it !== b) b.recycle() }
    } catch (_: Exception) {
        null
    }
}
