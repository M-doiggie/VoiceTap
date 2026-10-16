package com.voicetap.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Intent
import android.content.res.ColorStateList
import android.content.res.Configuration
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.media.AudioManager
import android.media.ToneGenerator
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.WindowManager
import android.widget.Button
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleService
import androidx.lifecycle.lifecycleScope
import com.voicetap.MainActivity
import com.voicetap.data.RegionRepository
import com.voicetap.data.SettingsRepository
import com.voicetap.overlay.DrawingOverlayView
import com.voicetap.util.AppEvent
import com.voicetap.util.CommandEventBus
import com.voicetap.util.ForegroundAppDetector
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject
import kotlin.math.abs

@AndroidEntryPoint
class OverlayService : LifecycleService() {

    @Inject lateinit var bus: CommandEventBus
    @Inject lateinit var appDetector: ForegroundAppDetector
    @Inject lateinit var regionRepository: RegionRepository
    @Inject lateinit var settingsRepository: SettingsRepository

    private lateinit var wm: WindowManager
    private var menuContainer: LinearLayout? = null
    private var subButtons: LinearLayout? = null
    private var toggleButtonRef: ImageButton? = null
    private var isMenuExpanded = false
    private var listeningEnabled = true
    private var drawingOverlay: DrawingOverlayView? = null
    private var regionsPopup: View? = null

    companion object {
        const val CHANNEL_ID = "overlay_channel"
        const val NOTIF_ID = 1
    }

    override fun onCreate() {
        super.onCreate()
        wm = getSystemService(WINDOW_SERVICE) as WindowManager
        createNotificationChannel()
        startForeground(NOTIF_ID, buildNotification())
        addFloatingMenu()
        observeDrawingModeEvents()
        observeTriggerTaps()
    }

    private fun observeDrawingModeEvents() {
        lifecycleScope.launch {
            bus.events.filterIsInstance<AppEvent.DrawingModeChanged>().collect { event ->
                if (event.active) showDrawingOverlay() else removeDrawingOverlay()
            }
        }
    }

    private fun observeTriggerTaps() {
        lifecycleScope.launch {
            bus.events.filterIsInstance<AppEvent.TriggerTap>().collect { event ->
                if (settingsRepository.visualFeedbackEnabled.first()) showTapPulse(event.rect)
                if (settingsRepository.soundFeedbackEnabled.first()) playTapTone()
            }
        }
    }

    // --- Look & feel helpers (Material You dynamic system colors — available since API 31) ---

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

    private fun accentColor(colorRes: Int): Int = ContextCompat.getColor(this, colorRes)

    private fun circleDrawable(color: Int): GradientDrawable = GradientDrawable().apply {
        shape = GradientDrawable.OVAL
        setColor(color)
    }

    private fun roundedRectDrawable(color: Int, radiusDp: Int): GradientDrawable = GradientDrawable().apply {
        shape = GradientDrawable.RECTANGLE
        cornerRadius = dp(radiusDp).toFloat()
        setColor(color)
    }

    // --- Floating menu: draggable main button that expands into draw / listening-toggle / view-regions actions ---

    private fun addFloatingMenu() {
        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
        }

        val sub = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            visibility = View.GONE
        }
        addSubButton(sub, menuIconButton(android.R.drawable.ic_menu_edit) { onDrawClicked() })
        addSubButton(sub, menuIconButton(listeningIcon()) { onToggleListeningClicked() }.also { toggleButtonRef = it })
        addSubButton(sub, menuIconButton(android.R.drawable.ic_menu_view) { onViewRegionsClicked() })
        subButtons = sub

        val mainBtn = ImageButton(this).apply {
            setImageResource(android.R.drawable.ic_input_add)
            background = circleDrawable(accentColor(android.R.color.system_accent1_600))
            setColorFilter(Color.WHITE)
            setPadding(dp(14), dp(14), dp(14), dp(14))
            elevation = dp(6).toFloat()
        }

        container.addView(sub)
        container.addView(
            mainBtn,
            LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
                topMargin = dp(8)
            },
        )

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

        makeDraggable(mainBtn, container, params) { toggleMenuExpanded() }

        wm.addView(container, params)
        menuContainer = container
    }

    private fun addSubButton(sub: LinearLayout, button: ImageButton) {
        sub.addView(
            button,
            LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
                bottomMargin = dp(10)
            },
        )
    }

    private fun makeDraggable(
        handle: View,
        movedView: View,
        params: WindowManager.LayoutParams,
        onTap: () -> Unit,
    ) {
        val touchSlop = ViewConfiguration.get(this).scaledTouchSlop
        var initialX = 0
        var initialY = 0
        var initialTouchX = 0f
        var initialTouchY = 0f
        var dragging = false

        handle.setOnTouchListener { _, event ->
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    initialX = params.x
                    initialY = params.y
                    initialTouchX = event.rawX
                    initialTouchY = event.rawY
                    dragging = false
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = (event.rawX - initialTouchX).toInt()
                    val dy = (event.rawY - initialTouchY).toInt()
                    if (!dragging && (abs(dx) > touchSlop || abs(dy) > touchSlop)) dragging = true
                    if (dragging) {
                        params.x = initialX - dx
                        params.y = initialY + dy
                        wm.updateViewLayout(movedView, params)
                    }
                    true
                }
                MotionEvent.ACTION_UP -> {
                    if (!dragging) onTap()
                    true
                }
                else -> false
            }
        }
    }

    private fun menuIconButton(iconRes: Int, onClick: () -> Unit): ImageButton =
        ImageButton(this).apply {
            setImageResource(iconRes)
            background = circleDrawable(accentColor(android.R.color.system_accent1_100))
            setColorFilter(accentColor(android.R.color.system_accent1_900))
            setPadding(dp(10), dp(10), dp(10), dp(10))
            elevation = dp(4).toFloat()
            setOnClickListener { onClick() }
        }

    private fun listeningIcon(): Int =
        if (listeningEnabled) android.R.drawable.presence_online else android.R.drawable.presence_offline

    private fun toggleMenuExpanded() {
        isMenuExpanded = !isMenuExpanded
        subButtons?.visibility = if (isMenuExpanded) View.VISIBLE else View.GONE
    }

    private fun collapseMenu() {
        isMenuExpanded = false
        subButtons?.visibility = View.GONE
    }

    private fun onDrawClicked() {
        collapseMenu()
        showDrawingOverlay()
    }

    private fun onToggleListeningClicked() {
        listeningEnabled = !listeningEnabled
        toggleButtonRef?.setImageResource(listeningIcon())
        bus.emit(AppEvent.ListeningEnabledChanged(listeningEnabled))
        Toast.makeText(this, if (listeningEnabled) "Voice listening on" else "Voice listening paused", Toast.LENGTH_SHORT).show()
        collapseMenu()
    }

    private fun onViewRegionsClicked() {
        collapseMenu()
        if (regionsPopup != null) {
            removeRegionsPopup()
            return
        }
        lifecycleScope.launch {
            val pkg = appDetector.getForegroundPackage()
            val label = pkg?.let { appDetector.getAppLabel(it) } ?: "the current app"
            val regions = regionRepository.getRegions().filter { it.appPackage == pkg }
            showRegionsPopup(label, regions)
        }
    }

    // --- Region drawing ---

    private fun showDrawingOverlay() {
        if (drawingOverlay != null) return

        val overlay = DrawingOverlayView(
            context = this,
            onRegionConfirmed = { rect ->
                val pkg = appDetector.getForegroundPackage() ?: "unknown"
                val label = appDetector.getAppLabel(pkg)
                val orientation = resources.configuration.orientation
                removeDrawingOverlay()
                bus.emit(AppEvent.RegionDrawn(rect, pkg, label, orientation))
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

    // --- View current regions popup ---

    private fun showRegionsPopup(appLabel: String, regions: List<com.voicetap.data.VoiceRegion>) {
        val card = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = roundedRectDrawable(Color.argb(235, 28, 28, 30), radiusDp = 20)
            setPadding(dp(24), dp(20), dp(24), dp(20))
            elevation = dp(8).toFloat()
        }

        card.addView(TextView(this).apply {
            text = "Regions for $appLabel"
            setTextColor(accentColor(android.R.color.system_accent1_200))
            setTypeface(typeface, android.graphics.Typeface.BOLD)
            textSize = 16f
        })

        if (regions.isEmpty()) {
            card.addView(TextView(this).apply {
                text = "No regions saved for this app yet."
                setTextColor(Color.LTGRAY)
                setPadding(0, dp(16), 0, 0)
            })
        } else {
            regions.forEach { region ->
                val orientationLabel =
                    if (region.orientation == Configuration.ORIENTATION_LANDSCAPE) "Landscape" else "Portrait"
                card.addView(TextView(this).apply {
                    text = "• \"${region.phrase}\" ($orientationLabel)"
                    setTextColor(Color.WHITE)
                    setPadding(0, dp(12), 0, 0)
                })
            }
        }

        val closeButton = Button(this).apply {
            text = "Close"
            setTextColor(Color.WHITE)
            backgroundTintList = ColorStateList.valueOf(accentColor(android.R.color.system_accent1_600))
            setOnClickListener { removeRegionsPopup() }
        }
        card.addView(
            closeButton,
            LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
                topMargin = dp(16)
                gravity = Gravity.CENTER_HORIZONTAL
            },
        )

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT,
        ).apply { gravity = Gravity.CENTER }

        wm.addView(card, params)
        regionsPopup = card
    }

    private fun removeRegionsPopup() {
        regionsPopup?.let { wm.removeView(it) }
        regionsPopup = null
    }

    // --- Trigger feedback cues ---

    private fun showTapPulse(rect: android.graphics.Rect) {
        val size = dp(56)
        val pulse = View(this).apply {
            background = circleDrawable(Color.argb(160, 76, 175, 80))
        }
        val params = WindowManager.LayoutParams(
            size, size,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE,
            PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = (rect.exactCenterX() - size / 2).toInt()
            y = (rect.exactCenterY() - size / 2).toInt()
        }

        wm.addView(pulse, params)
        Handler(Looper.getMainLooper()).postDelayed({
            runCatching { wm.removeView(pulse) }
        }, 400)
    }

    private fun playTapTone() {
        val toneGen = ToneGenerator(AudioManager.STREAM_NOTIFICATION, 70)
        toneGen.startTone(ToneGenerator.TONE_PROP_BEEP, 150)
        Handler(Looper.getMainLooper()).postDelayed({ toneGen.release() }, 200)
    }

    override fun onDestroy() {
        menuContainer?.let { wm.removeView(it) }
        removeDrawingOverlay()
        removeRegionsPopup()
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
