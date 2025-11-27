// WheelView.kt
package com.example.datadomeapp.teacher

import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import android.view.View
import kotlin.math.*

class WheelView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private var names: List<String> = emptyList()
    private var colors: List<Int> = emptyList()
    private var wheelRotationAngle = 0f // Renamed to avoid conflict
    private var isSpinning = false

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val centerPaint = Paint(Paint.ANTI_ALIAS_FLAG)

    private val colorList = listOf(
        Color.parseColor("#FF6B6B"),
        Color.parseColor("#4ECDC4"),
        Color.parseColor("#45B7D1"),
        Color.parseColor("#96CEB4"),
        Color.parseColor("#FFEAA7"),
        Color.parseColor("#DDA0DD"),
        Color.parseColor("#98D8C8"),
        Color.parseColor("#F7DC6F"),
        Color.parseColor("#BB8FCE"),
        Color.parseColor("#85C1E9"),
        Color.parseColor("#F8C471"),
        Color.parseColor("#82E0AA")
    )

    init {
        setupPaints()
    }

    private fun setupPaints() {
        textPaint.color = Color.WHITE
        textPaint.textSize = 24f
        textPaint.textAlign = Paint.Align.CENTER
        textPaint.typeface = Typeface.DEFAULT_BOLD

        centerPaint.color = Color.WHITE
    }

    fun setNames(names: List<String>) {
        this.names = names
        generateColors()
        invalidate()
    }

    // CHANGED: Renamed from setRotation to setWheelRotation
    fun setWheelRotation(angle: Float) {
        wheelRotationAngle = angle
        invalidate()
    }

    fun setIsSpinning(spinning: Boolean) {
        isSpinning = spinning
    }

    private fun generateColors() {
        colors = names.indices.map { index ->
            colorList[index % colorList.size]
        }
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        if (names.isEmpty()) return

        val centerX = width / 2f
        val centerY = height / 2f
        val radius = min(centerX, centerY) - 20f
        val centerRadius = radius * 0.15f

        // Save canvas state
        canvas.save()
        // CHANGED: Use the renamed variable
        canvas.rotate(wheelRotationAngle, centerX, centerY)

        val sliceAngle = 360f / names.size

        // Draw slices
        for (i in names.indices) {
            val startAngle = i * sliceAngle
            val sweepAngle = sliceAngle - 2f // Small gap between slices

            // Draw slice
            paint.color = colors[i]
            val rectF = RectF(centerX - radius, centerY - radius, centerX + radius, centerY + radius)
            canvas.drawArc(rectF, startAngle, sweepAngle, true, paint)

            // Draw text
            drawTextInSlice(canvas, centerX, centerY, radius, startAngle + sweepAngle / 2, names[i])
        }

        // Draw center circle
        canvas.drawCircle(centerX, centerY, centerRadius, centerPaint)

        // Restore canvas state
        canvas.restore()
    }

    private fun drawTextInSlice(canvas: Canvas, centerX: Float, centerY: Float, radius: Float, angle: Float, text: String) {
        val textRadius = radius * 0.6f // Moved closer to center
        val angleRad = Math.toRadians(angle.toDouble()).toFloat()

        val x = centerX + textRadius * cos(angleRad)
        val y = centerY + textRadius * sin(angleRad)

        // Smart text rotation - always readable
        val textAngle = if (angle > 90 && angle < 270) angle + 180 else angle

        canvas.save()
        canvas.rotate(textAngle, x, y)

        // Adjust text size and wrapping
        val maxLength = when {
            names.size > 8 -> 8
            names.size > 6 -> 10
            else -> 12
        }
        val adjustedText = if (text.length > maxLength) "${text.take(maxLength)}..." else text

        textPaint.textSize = when {
            names.size > 10 -> 14f
            names.size > 8 -> 16f
            else -> 18f
        }

        canvas.drawText(adjustedText, x, y, textPaint)
        canvas.restore()
    }

    // CHANGED: Add method to get current rotation if needed
    fun getWheelRotation(): Float {
        return wheelRotationAngle
    }
}