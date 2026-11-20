package com.khader.mawjachat.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.util.AttributeSet
import android.view.View
import com.khader.mawjachat.model.WaveSegment

class WaveformView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private val charPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#00E5B8")
        strokeWidth = 6f
        strokeCap = Paint.Cap.ROUND
    }

    private val silencePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#FFB300")
        strokeWidth = 3f
        strokeCap = Paint.Cap.ROUND
    }

    private val spacePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#4A658A")
        strokeWidth = 2f
    }

    private val labelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#B0C4DE")
        textSize = 22f
        textAlign = Paint.Align.CENTER
    }

    private var segments: List<WaveSegment> = emptyList()
    private val liveAmplitudes = mutableListOf<Float>()

    fun setSegments(newSegments: List<WaveSegment>) {
        segments = newSegments
        liveAmplitudes.clear()
        invalidate()
    }

    fun addLiveAmplitude(amp: Float) {
        if (liveAmplitudes.size > 80) {
            liveAmplitudes.removeAt(0)
        }
        liveAmplitudes.add(amp)
        invalidate()
    }

    fun clearLive() {
        liveAmplitudes.clear()
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val w = width.toFloat()
        val h = height.toFloat()
        val centerY = h / 2f

        if (liveAmplitudes.isNotEmpty()) {
            val step = w / 82f
            liveAmplitudes.forEachIndexed { idx, amp ->
                val x = idx * step + 10f
                val barH = (amp * h * 0.42f).coerceAtLeast(6f)
                canvas.drawLine(x, centerY - barH, x, centerY + barH, charPaint)
            }
            return
        }

        if (segments.isEmpty()) {
            canvas.drawLine(16f, centerY, w - 16f, centerY, spacePaint)
            return
        }

        val visibleSegments = segments.take(65)
        val slotWidth = (w - 24f) / visibleSegments.size.coerceAtLeast(1)

        visibleSegments.forEachIndexed { idx, seg ->
            val x = 12f + idx * slotWidth + slotWidth / 2f
            when (seg.type) {
                "CHAR_WAVE" -> {
                    val barH = (seg.peakAmplitude * h * 0.36f).coerceAtLeast(10f)
                    canvas.drawLine(x, centerY - barH, x, centerY + barH, charPaint)
                    if (slotWidth >= 18f) {
                        canvas.drawText(seg.label, x, h - 6f, labelPaint)
                    }
                }
                "SILENCE" -> {
                    canvas.drawCircle(x, centerY, 4f, silencePaint)
                }
                "SPACE" -> {
                    canvas.drawLine(x, centerY - 12f, x, centerY + 12f, spacePaint)
                }
            }
        }
    }
}