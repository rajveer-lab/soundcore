package dev.soundtuner

import android.content.Context
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Shader
import android.util.AttributeSet
import android.util.TypedValue
import android.view.View
import kotlin.math.ln

/** Draws the EQ curve: what the profile + sliders do to each frequency. */
class CurveView(context: Context, attrs: AttributeSet?) : View(context, attrs) {
    private var filters: List<Filter> = emptyList()
    private val density = resources.displayMetrics.density
    private val rangeDb = 12.0

    private val accent = context.getColor(R.color.accent)
    private val gridPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = context.getColor(R.color.grid); strokeWidth = density
    }
    private val zeroPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = context.getColor(R.color.muted); strokeWidth = density; alpha = 110
    }
    private val linePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = accent; style = Paint.Style.STROKE; strokeWidth = 3 * density
        strokeJoin = Paint.Join.ROUND; strokeCap = Paint.Cap.ROUND
    }
    private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val labelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = context.getColor(R.color.muted)
        textSize = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, 11f, resources.displayMetrics)
    }

    fun setFilters(newFilters: List<Filter>) {
        filters = newFilters
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        // Draw inside the padding so lines never cross the card's rounded corners
        val w = (width - paddingLeft - paddingRight).toFloat()
        val labelSpace = 18 * density
        val plotH = height - paddingTop - paddingBottom - labelSpace
        val mid = plotH / 2
        canvas.save()
        canvas.translate(paddingLeft.toFloat(), paddingTop.toFloat())

        fun x(f: Double) = (ln(f / 20.0) / ln(1000.0)).toFloat() * w
        fun y(db: Double) = (mid - db.coerceIn(-rangeDb, rangeDb) / rangeDb * mid).toFloat()

        // Grid: dB lines and frequency lines
        for (db in listOf(-12.0, -6.0, 6.0, 12.0)) canvas.drawLine(0f, y(db), w, y(db), gridPaint)
        canvas.drawLine(0f, y(0.0), w, y(0.0), zeroPaint)
        for (f in listOf(50.0, 100.0, 200.0, 500.0, 1000.0, 2000.0, 5000.0, 10000.0)) {
            canvas.drawLine(x(f), 0f, x(f), plotH, gridPaint)
        }
        for ((f, label) in listOf(100.0 to "100 Hz", 1000.0 to "1 kHz", 10000.0 to "10 kHz")) {
            val tx = x(f) - labelPaint.measureText(label) / 2
            canvas.drawText(label, tx, plotH + labelSpace - 4 * density, labelPaint)
        }
        canvas.drawText("+12 dB", 4 * density, y(12.0) + 12 * density, labelPaint)
        canvas.drawText("-12 dB", 4 * density, y(-12.0) - 4 * density, labelPaint)

        // Curve
        val line = Path()
        val area = Path().apply { moveTo(0f, y(0.0)) }
        val steps = 200
        for (i in 0..steps) {
            val f = EqMath.logFreq(i / steps.toDouble())
            val px = x(f)
            val py = y(EqMath.curveDb(filters, f))
            if (i == 0) line.moveTo(px, py) else line.lineTo(px, py)
            area.lineTo(px, py)
        }
        area.lineTo(w, y(0.0))
        area.close()

        fillPaint.shader = LinearGradient(
            0f, 0f, 0f, plotH,
            intArrayOf(withAlpha(accent, 90), withAlpha(accent, 20), withAlpha(accent, 90)),
            floatArrayOf(0f, 0.5f, 1f), Shader.TileMode.CLAMP,
        )
        canvas.drawPath(area, fillPaint)
        canvas.drawPath(line, linePaint)
        canvas.restore()
    }

    private fun withAlpha(color: Int, alpha: Int) = (color and 0x00FFFFFF) or (alpha shl 24)
}
