package fr.vippneus.intervention.ui

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.LruCache
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import fr.vippneus.intervention.data.Intervention
import fr.vippneus.intervention.data.InterventionType
import fr.vippneus.intervention.pdf.Box
import fr.vippneus.intervention.pdf.CanvasRenderer
import fr.vippneus.intervention.pdf.DrawOp
import fr.vippneus.intervention.pdf.Fonts
import fr.vippneus.intervention.pdf.FpsTemplate
import fr.vippneus.intervention.pdf.PdfPages
import fr.vippneus.intervention.pdf.TextMeasure
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.math.roundToInt

/** Images de fond de la page 1 (fiche vierge ou 1re page du PDF client), gardées en mémoire. */
object PageBitmaps {
    private const val RENDER_WIDTH = 1654 // ≈ 200 dpi pour un A4
    private val cache = LruCache<String, Bitmap>(3)

    suspend fun fps(context: Context): Bitmap = withContext(Dispatchers.IO) {
        cache.get("fps") ?: context.applicationContext.assets.open(FpsTemplate.BACKGROUND_ASSET).use {
            BitmapFactory.decodeStream(it)
        }.also { cache.put("fps", it) }
    }

    suspend fun firstPage(file: File): Bitmap = withContext(Dispatchers.IO) {
        val key = file.path + ":" + file.lastModified()
        cache.get(key) ?: PdfPages.render(file, 0, RENDER_WIDTH).also { cache.put(key, it) }
    }

    // Miniatures de la liste des bons : petites, gardées à part (16 Mo au plus)
    private const val THUMB_WIDTH = 280
    private val thumbs = object : LruCache<String, Bitmap>(16 * 1024) {
        override fun sizeOf(key: String, value: Bitmap) = value.byteCount / 1024
    }

    suspend fun fpsThumbnail(context: Context): Bitmap = withContext(Dispatchers.IO) {
        thumbs.get("fps") ?: fps(context).let { full ->
            Bitmap.createScaledBitmap(full, THUMB_WIDTH, (full.height * THUMB_WIDTH.toFloat() / full.width).roundToInt(), true)
        }.also { thumbs.put("fps", it) }
    }

    suspend fun thumbnail(file: File): Bitmap = withContext(Dispatchers.IO) {
        val key = file.path + ":" + file.lastModified()
        thumbs.get(key) ?: PdfPages.render(file, 0, THUMB_WIDTH).also { thumbs.put(key, it) }
    }
}

@Composable
fun rememberPageBackground(i: Intervention, sourceFile: File?): ImageBitmap? {
    val context = LocalContext.current
    val key = if (i.type == InterventionType.FPS) "fps" else sourceFile?.path
    val bmp by produceState<ImageBitmap?>(null, key) {
        value = try {
            when (i.type) {
                InterventionType.FPS -> PageBitmaps.fps(context)
                InterventionType.DOCUMENT -> sourceFile?.let { PageBitmaps.firstPage(it) }
            }?.asImageBitmap()
        } catch (_: Throwable) {
            null
        }
    }
    return bmp
}

/** Fond de la miniature d'un bon (liste de l'accueil). */
@Composable
fun rememberThumbnailBackground(i: Intervention, sourceFile: File?): ImageBitmap? {
    val context = LocalContext.current
    val key = if (i.type == InterventionType.FPS) "fps" else sourceFile?.path
    val bmp by produceState<ImageBitmap?>(null, key) {
        value = try {
            when (i.type) {
                InterventionType.FPS -> PageBitmaps.fpsThumbnail(context)
                InterventionType.DOCUMENT -> sourceFile?.let { PageBitmaps.thumbnail(it) }
            }?.asImageBitmap()
        } catch (_: Throwable) {
            null
        }
    }
    return bmp
}

@Composable
fun rememberMeasure(): TextMeasure {
    val context = LocalContext.current
    return remember { Fonts.measure(context) }
}

@Composable
fun rememberRenderer(): CanvasRenderer {
    val context = LocalContext.current
    return remember { CanvasRenderer(Fonts.typeface(context)) }
}

/** Dessine la page (fond + saisies) dans le rectangle [topLeft, size] du DrawScope. */
fun DrawScope.drawPage(
    renderer: CanvasRenderer,
    background: ImageBitmap?,
    ops: List<DrawOp>,
    topLeft: Offset,
    size: Size,
    pageW: Float,
) {
    drawRect(Color.White, topLeft, size)
    if (background != null) {
        drawImage(
            background,
            dstOffset = IntOffset(topLeft.x.roundToInt(), topLeft.y.roundToInt()),
            dstSize = IntSize(size.width.roundToInt(), size.height.roundToInt()),
            filterQuality = FilterQuality.High,
        )
    }
    val scale = size.width / pageW
    drawIntoCanvas { c ->
        c.nativeCanvas.save()
        c.nativeCanvas.translate(topLeft.x, topLeft.y)
        renderer.draw(c.nativeCanvas, ops, scale)
        c.nativeCanvas.restore()
    }
}

fun DrawScope.drawSelection(box: Box, topLeft: Offset, scale: Float, color: Color) {
    val pad = 3f
    drawRect(
        color = color,
        topLeft = Offset(topLeft.x + (box.left - pad) * scale, topLeft.y + (box.top - pad) * scale),
        size = Size((box.width + 2 * pad) * scale, (box.height + 2 * pad) * scale),
        style = Stroke(width = 2.5f, pathEffect = PathEffect.dashPathEffect(floatArrayOf(12f, 8f))),
    )
}

/** Aperçu non interactif de la page 1 (proportions respectées). */
@Composable
fun PagePreview(
    pageW: Float,
    pageH: Float,
    background: ImageBitmap?,
    ops: List<DrawOp>,
    modifier: Modifier = Modifier,
) {
    val renderer = rememberRenderer()
    Canvas(
        modifier
            .aspectRatio(pageW / pageH)
            .background(Color.White),
    ) {
        drawPage(renderer, background, ops, Offset.Zero, size, pageW)
    }
}
