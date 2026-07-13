package com.voicetap.service

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.graphics.Path
import android.graphics.Rect
import android.view.accessibility.AccessibilityEvent
import com.voicetap.util.AppEvent
import com.voicetap.util.CommandEventBus
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class ClickAccessibilityService : AccessibilityService() {

    @Inject lateinit var bus: CommandEventBus

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    override fun onServiceConnected() {
        scope.launch {
            bus.events.filterIsInstance<AppEvent.TriggerTap>().collect { event ->
                performTap(event.rect)
            }
        }
    }

    private fun performTap(rect: Rect) {
        val cx = rect.exactCenterX()
        val cy = rect.exactCenterY()

        val path = Path().apply { moveTo(cx, cy) }
        val stroke = GestureDescription.StrokeDescription(path, 0L, 50L)
        val gesture = GestureDescription.Builder().addStroke(stroke).build()

        dispatchGesture(gesture, null, null)
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {}
    override fun onInterrupt() {}

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }
}
