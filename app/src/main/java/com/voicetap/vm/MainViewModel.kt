package com.voicetap.vm

import android.app.Application
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Rect
import android.provider.Settings
import androidx.core.content.ContextCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.voicetap.data.RegionRepository
import com.voicetap.data.SettingsRepository
import com.voicetap.data.VoiceRegion
import com.voicetap.service.OverlayService
import com.voicetap.service.VoiceListenerService
import com.voicetap.util.AppEvent
import com.voicetap.util.CommandEventBus
import com.voicetap.util.ForegroundAppDetector
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class PendingRegion(
    val rect: Rect,
    val appPackage: String,
    val appLabel: String,
    val orientation: Int,
)

data class UiState(
    val regions: List<VoiceRegion> = emptyList(),
    val pendingRegion: PendingRegion? = null,
    val isRecordingPhrase: Boolean = false,
    val recordedPhrase: String = "",
    val hasOverlayPermission: Boolean = false,
    val hasAudioPermission: Boolean = false,
    val hasUsageAccess: Boolean = false,
    val isAccessibilityEnabled: Boolean = false,
    val isVoskModelReady: Boolean = false,
    val servicesRunning: Boolean = false,
    val visualFeedbackEnabled: Boolean = true,
    val soundFeedbackEnabled: Boolean = true,
)

@HiltViewModel
class MainViewModel @Inject constructor(
    app: Application,
    private val repository: RegionRepository,
    private val bus: CommandEventBus,
    private val appDetector: ForegroundAppDetector,
    private val settingsRepository: SettingsRepository,
) : AndroidViewModel(app) {

    private val _state = MutableStateFlow(UiState())
    val state: StateFlow<UiState> = _state.asStateFlow()

    init {
        observeRegions()
        observeBusEvents()
        observeSettings()
    }

    private fun observeSettings() {
        viewModelScope.launch {
            settingsRepository.visualFeedbackEnabled.collect { enabled ->
                _state.update { it.copy(visualFeedbackEnabled = enabled) }
            }
        }
        viewModelScope.launch {
            settingsRepository.soundFeedbackEnabled.collect { enabled ->
                _state.update { it.copy(soundFeedbackEnabled = enabled) }
            }
        }
    }

    fun setVisualFeedbackEnabled(enabled: Boolean) {
        viewModelScope.launch { settingsRepository.setVisualFeedbackEnabled(enabled) }
    }

    fun setSoundFeedbackEnabled(enabled: Boolean) {
        viewModelScope.launch { settingsRepository.setSoundFeedbackEnabled(enabled) }
    }

    private fun observeRegions() {
        viewModelScope.launch {
            repository.observeRegions().collect { regions ->
                _state.update { it.copy(regions = regions) }
            }
        }
    }

    private fun observeBusEvents() {
        viewModelScope.launch {
            bus.events.filterIsInstance<AppEvent.RegionDrawn>().collect { event ->
                // Show dialog immediately so user knows to speak
                _state.update {
                    it.copy(
                        pendingRegion = PendingRegion(event.rect, event.appPackage, event.appLabel, event.orientation),
                        isRecordingPhrase = true,
                        recordedPhrase = "",
                    )
                }
            }
        }
        viewModelScope.launch {
            bus.events.filterIsInstance<AppEvent.PhraseRecorded>().collect { event ->
                _state.update {
                    it.copy(
                        pendingRegion = PendingRegion(event.rect, event.appPackage, event.appLabel, event.orientation),
                        isRecordingPhrase = false,
                        recordedPhrase = event.phrase,
                    )
                }
            }
        }
        viewModelScope.launch {
            bus.events.filterIsInstance<AppEvent.PhraseCaptureFailed>().collect { event ->
                _state.update {
                    it.copy(
                        pendingRegion = PendingRegion(event.rect, event.appPackage, event.appLabel, event.orientation),
                        isRecordingPhrase = false,
                        recordedPhrase = "",
                    )
                }
            }
        }
    }

    fun refreshPermissions() {
        val ctx = getApplication<Application>()
        val hasAudio = ContextCompat.checkSelfPermission(
            ctx, android.Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED
        val hasOverlay = Settings.canDrawOverlays(ctx)
        val hasUsage = appDetector.hasUsageAccess()
        val isA11y = isAccessibilityEnabled()
        val voskReady = java.io.File(ctx.filesDir, "vosk-model").exists()

        _state.update {
            it.copy(
                hasAudioPermission = hasAudio,
                hasOverlayPermission = hasOverlay,
                hasUsageAccess = hasUsage,
                isAccessibilityEnabled = isA11y,
                isVoskModelReady = voskReady,
            )
        }
    }

    fun startServices() {
        val ctx = getApplication<Application>()
        ContextCompat.startForegroundService(ctx, Intent(ctx, OverlayService::class.java))
        ContextCompat.startForegroundService(ctx, Intent(ctx, VoiceListenerService::class.java))
        _state.update { it.copy(servicesRunning = true) }
    }

    fun deleteRegion(id: Long) {
        viewModelScope.launch { repository.deleteRegion(id) }
    }

    fun retryPhraseRecording() {
        val pending = _state.value.pendingRegion ?: return
        _state.update { it.copy(isRecordingPhrase = true, recordedPhrase = "") }
        bus.emit(AppEvent.RegionDrawn(pending.rect, pending.appPackage, pending.appLabel, pending.orientation))
    }

    fun stopPhraseRecording() {
        if (_state.value.isRecordingPhrase) {
            bus.emit(AppEvent.StopPhraseCapture)
        }
    }

    fun cancelPendingRegion() {
        if (_state.value.isRecordingPhrase) {
            bus.emit(AppEvent.StopPhraseCapture)
        }
        _state.update { it.copy(pendingRegion = null, isRecordingPhrase = false, recordedPhrase = "") }
    }

    fun confirmPendingRegion() {
        val pending = _state.value.pendingRegion ?: return
        val phrase = _state.value.recordedPhrase.trim()
        if (phrase.isBlank()) return
        viewModelScope.launch {
            repository.saveRegion(phrase, pending.appPackage, pending.appLabel, pending.rect, pending.orientation)
            _state.update { it.copy(pendingRegion = null, isRecordingPhrase = false, recordedPhrase = "") }
        }
    }

    private fun isAccessibilityEnabled(): Boolean {
        val ctx = getApplication<Application>()
        val enabledServices = Settings.Secure.getString(
            ctx.contentResolver,
            Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES,
        ) ?: return false
        val full = "com.voicetap/com.voicetap.service.ClickAccessibilityService"
        return enabledServices.split(":").any { it.equals(full, ignoreCase = true) }
    }
}
