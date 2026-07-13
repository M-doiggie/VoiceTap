package com.voicetap.overlay

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import android.view.MotionEvent
import android.view.View

class DrawingOverlayView(
    context: Context,
    private val onRegionConfirmed: (Rect) -> Unit,
    private val onCancel: () -> Unit,
) : View(context) {

    private var startX = 0f
    private var startY = 0f
    private var currentX = 0f
    private var currentY = 0f
    private var isDrawing = false

    private val fillPaint = Paint().apply {
        color = Color.argb(60, 33, 150, 243)
        style = Paint.Style.FILL
    }

    private val strokePaint = Paint().apply {
        color = Color.rgb(33, 150, 243)
        style = Paint.Style.STROKE
        strokeWidth = 4f
        isAntiAlias = true
    }

    private val hintPaint = Paint().apply {
        color = Color.WHITE
        textSize = 48f
        isAntiAlias = true
        textAlign = Paint.Align.CENTER
    }

    private val dimPaint = Paint().apply {
        color = Color.argb(100, 0, 0, 0)
        style = Paint.Style.FILL
    }

    override fun onDraw(canvas: Canvas) {
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), dimPaint)

        if (isDrawing) {
            val rect = buildRectF()
            canvas.drawRect(rect, fillPaint)
            canvas.drawRect(rect, strokePaint)
        } else {
            canvas.drawText("Drag to select a region", width / 2f, height / 2f, hintPaint)
            canvas.drawText("Lift finger to confirm  •  Two-finger tap to cancel",
                width / 2f, height / 2f + 64f, hintPaint.apply { textSize = 32f })
        }
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                startX = event.x
                startY = event.y
                currentX = event.x
                currentY = event.y
                isDrawing = true
                invalidate()
            }
            MotionEvent.ACTION_MOVE -> {
                currentX = event.x
                currentY = event.y
                invalidate()
            }
            MotionEvent.ACTION_UP -> {
                currentX = event.x
                currentY = event.y
                isDrawing = false
                invalidate()
                val rect = buildRect()
                if (rect.width() > 20 && rect.height() > 20) {
                    onRegionConfirmed(rect)
                } else {
                    // Too small — reset and let user try again
                    isDrawing = false
                    invalidate()
                }
            }
            MotionEvent.ACTION_POINTER_DOWN -> {
                // Two-finger tap cancels drawing
                onCancel()
            }
        }
        return true
    }

    private fun buildRectF() = RectF(
        minOf(startX, currentX),
        minOf(startY, currentY),
        maxOf(startX, currentX),
        maxOf(startY, currentY),
    )

    private fun buildRect(): Rect {
        val rf = buildRectF()
        return Rect(rf.left.toInt(), rf.top.toInt(), rf.right.toInt(), rf.bottom.toInt())
    }
}
