package com.voicetap.util

import android.graphics.Rect
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import javax.inject.Inject
import javax.inject.Singleton

sealed interface AppEvent {
    /** VoiceListenerService → ClickAccessibilityService (and OverlayService for feedback): perform a tap at this rect center */
    data class TriggerTap(val rect: Rect) : AppEvent
    /** OverlayService → VoiceListenerService: start/stop the drawing overlay */
    data class DrawingModeChanged(val active: Boolean) : AppEvent
    /** OverlayService → VoiceListenerService: region drawn, pause always-on listener and capture phrase */
    data class RegionDrawn(val rect: Rect, val appPackage: String, val appLabel: String, val orientation: Int) : AppEvent
    /** VoiceListenerService → MainViewModel: one-shot phrase captured, show confirmation dialog */
    data class PhraseRecorded(val phrase: String, val rect: Rect, val appPackage: String, val appLabel: String, val orientation: Int) : AppEvent
    /** VoiceListenerService → MainViewModel: mic opened but nothing was heard */
    data class PhraseCaptureFailed(val rect: Rect, val appPackage: String, val appLabel: String, val orientation: Int) : AppEvent
    /** MainViewModel → VoiceListenerService: user tapped stop, end the in-progress phrase capture now */
    object StopPhraseCapture : AppEvent
    /** OverlayService → VoiceListenerService: user toggled always-on voice listening from the overlay menu */
    data class ListeningEnabledChanged(val enabled: Boolean) : AppEvent
}

@Singleton
class CommandEventBus @Inject constructor() {
    private val _events = MutableSharedFlow<AppEvent>(extraBufferCapacity = 16)
    val events: SharedFlow<AppEvent> = _events.asSharedFlow()

    fun emit(event: AppEvent) {
        _events.tryEmit(event)
    }
}
