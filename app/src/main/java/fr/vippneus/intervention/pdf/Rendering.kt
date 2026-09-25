package fr.vippneus.intervention.pdf

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Typeface

/** Couleur d'écriture (quasi noir), identique à l'écran et dans le PDF. */
object Ink {
    const val ARGB: Int = 0xFF14171C.toInt()
    const val R = 0x14 / 255f
    const val G = 0x17 / 255f
    const val B = 0x1C / 255f
}

object Fonts {
    const val ASSET = "fonts/SourceSans3-Regular.ttf"

    @Volatile
    private var typeface: Typeface? = null

    @Volatile
    private var measure: TextMeasure? = null

    fun typeface(context: Context): Typeface =
        typeface ?: synchronized(this) {
            typeface ?: Typeface.createFromAsset(context.applicationContext.assets, ASSET).also { typeface = it }
        }

    fun measure(context: Context): TextMeasure =
        measure ?: synchronized(this) {
            measure ?: PaintTextMeasure(typeface(context)).also { measure = it }
        }

    /** Réglages communs : texte linéaire, sans crénage, pour correspondre exactement au PDF. */
    fun textPaint(typeface: Typeface): Paint =
        Paint(Paint.ANTI_ALIAS_FLAG or Paint.SUBPIXEL_TEXT_FLAG or Paint.LINEAR_TEXT_FLAG).apply {
            this.typeface = typeface
            fontFeatureSettings = "'kern' 0, 'liga' 0"
            color = Ink.ARGB
        }
}

/** Mesure des largeurs avec la police embarquée dans le PDF. */
class PaintTextMeasure(typeface: Typeface) : TextMeasure {
    private val paint = Fonts.textPaint(typeface).apply { textSize = REF_SIZE }

    @Synchronized
    override fun width(text: String, size: Float): Float =
        if (text.isEmpty()) 0f else paint.measureText(text) * size / REF_SIZE

    private companion object {
        const val REF_SIZE = 100f
    }
}

/** Dessine les opérations de la page 1 sur un Canvas Android (aperçu écran). */
class CanvasRenderer(typeface: Typeface) {
    private val textPaint = Fonts.textPaint(typeface)
    private val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
        color = Ink.ARGB
    }
    private val framePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeJoin = Paint.Join.MITER
        color = Ink.ARGB
    }
    private val path = Path()

    /** [scale] : pixels par point PDF. */
    fun draw(canvas: Canvas, ops: List<DrawOp>, scale: Float) {
        for (op in ops) {
            when (op) {
                is TextOp -> {
                    textPaint.textSize = op.size * scale
                    for (line in op.lines) {
                        if (line.text.isNotEmpty()) canvas.drawText(line.text, line.x * scale, line.baseline * scale, textPaint)
                    }
                }
                is CrossOp -> {
                    strokePaint.strokeWidth = op.stroke * scale
                    val l = (op.cx - op.half) * scale
                    val r = (op.cx + op.half) * scale
                    val t = (op.cy - op.half) * scale
                    val b = (op.cy + op.half) * scale
                    canvas.drawLine(l, t, r, b, strokePaint)
                    canvas.drawLine(l, b, r, t, strokePaint)
                }
                is PanelOp -> {
                    framePaint.strokeWidth = op.stroke * scale
                    val f = op.frame
                    canvas.drawRect(f.left * scale, f.top * scale, f.right * scale, f.bottom * scale, framePaint)
                    draw(canvas, op.texts, scale)
                }
                is SignatureOp -> {
                    val fit = op.signature.fitInto(op.box)
                    strokePaint.strokeWidth = fit.pen * scale
                    for (stroke in op.signature.strokes) {
                        path.reset()
                        for (seg in strokePath(stroke, fit)) {
                            when (seg) {
                                is PathSeg.MoveTo -> path.moveTo(seg.x * scale, seg.y * scale)
                                is PathSeg.LineTo -> path.lineTo(seg.x * scale, seg.y * scale)
                                is PathSeg.QuadTo -> path.quadTo(seg.cx * scale, seg.cy * scale, seg.x * scale, seg.y * scale)
                            }
                        }
                        canvas.drawPath(path, strokePaint)
                    }
                }
            }
        }
    }
}
