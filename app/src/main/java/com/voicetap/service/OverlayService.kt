package com.voicetap.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Intent
import android.graphics.PixelFormat
import android.view.Gravity
import android.view.WindowManager
import android.widget.ImageButton
import android.widget.Toast
import kotlinx.coroutines.flow.filterIsInstance
import androidx.lifecycle.LifecycleService
import androidx.lifecycle.lifecycleScope
import com.voicetap.MainActivity
import com.voicetap.overlay.DrawingOverlayView
import com.voicetap.util.AppEvent
import com.voicetap.util.CommandEventBus
import com.voicetap.util.ForegroundAppDetector
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class OverlayService : LifecycleService() {

    @Inject lateinit var bus: CommandEventBus
    @Inject lateinit var appDetector: ForegroundAppDetector

    private lateinit var wm: WindowManager
    private var floatingButton: android.view.View? = null
    private var drawingOverlay: DrawingOverlayView? = null

    companion object {
        const val CHANNEL_ID = "overlay_channel"
        const val NOTIF_ID = 1
    }

    override fun onCreate() {
        super.onCreate()
        wm = getSystemService(WINDOW_SERVICE) as WindowManager
        createNotificationChannel()
        startForeground(NOTIF_ID, buildNotification())
        addFloatingButton()
        observeDrawingModeEvents()
    }

    private fun observeDrawingModeEvents() {
        lifecycleScope.launch {
            bus.events.filterIsInstance<AppEvent.DrawingModeChanged>().collect { event ->
                if (event.active) showDrawingOverlay() else removeDrawingOverlay()
            }
        }
    }

    private fun addFloatingButton() {
        val btn = ImageButton(this).apply {
            setImageResource(android.R.drawable.ic_input_add)
            setBackgroundColor(android.graphics.Color.argb(200, 33, 150, 243))
            setPadding(24, 24, 24, 24)
            setOnClickListener { showDrawingOverlay() }
        }

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = Gravity.TOP or Gravity.END
            x = 16
            y = 200
        }

        wm.addView(btn, params)
        floatingButton = btn
    }

    private fun showDrawingOverlay() {
        if (drawingOverlay != null) return

        val overlay = DrawingOverlayView(
            context = this,
            onRegionConfirmed = { rect ->
                val pkg = appDetector.getForegroundPackage() ?: "unknown"
                val label = appDetector.getAppLabel(pkg)
                removeDrawingOverlay()
                bus.emit(AppEvent.RegionDrawn(rect, pkg, label))
                Toast.makeText(this, "Speak your voice command now…", Toast.LENGTH_LONG).show()
                startActivity(
                    Intent(this, MainActivity::class.java).apply {
                        flags = Intent.FLAG_ACTIVITY_REORDER_TO_FRONT or Intent.FLAG_ACTIVITY_NEW_TASK
                    }
                )
            },
            onCancel = { removeDrawingOverlay() },
        )

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT,
        )

        wm.addView(overlay, params)
        drawingOverlay = overlay
    }

    private fun removeDrawingOverlay() {
        drawingOverlay?.let { wm.removeView(it) }
        drawingOverlay = null
    }

    override fun onDestroy() {
        floatingButton?.let { wm.removeView(it) }
        removeDrawingOverlay()
        super.onDestroy()
    }

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            "VoiceTap Overlay",
            NotificationManager.IMPORTANCE_LOW,
        )
        getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    private fun buildNotification(): Notification {
        val intent = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE,
        )
        return Notification.Builder(this, CHANNEL_ID)
            .setContentTitle("VoiceTap active")
            .setContentText("Tap the floating button to draw a region")
            .setSmallIcon(android.R.drawable.ic_btn_speak_now)
            .setContentIntent(intent)
            .build()
    }
}
