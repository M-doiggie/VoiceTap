package com.voicetap.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.graphics.Rect
import androidx.lifecycle.LifecycleService
import androidx.lifecycle.lifecycleScope
import com.voicetap.data.RegionRepository
import com.voicetap.data.VoiceRegion
import com.voicetap.util.AppEvent
import com.voicetap.util.CommandEventBus
import com.voicetap.util.ForegroundAppDetector
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.vosk.Model
import org.vosk.Recognizer
import org.vosk.android.RecognitionListener
import org.vosk.android.SpeechService
import javax.inject.Inject

@AndroidEntryPoint
class VoiceListenerService : LifecycleService(), RecognitionListener {

    @Inject lateinit var bus: CommandEventBus
    @Inject lateinit var repository: RegionRepository
    @Inject lateinit var appDetector: ForegroundAppDetector

    private var model: Model? = null
    private var speechService: SpeechService? = null
    private var currentRegions: List<VoiceRegion> = emptyList()
    private var isCapturingPhrase = false
    @Volatile private var stopCaptureRequested = false
    @Volatile private var listeningEnabled = true

    companion object {
        const val CHANNEL_ID = "voice_channel"
        const val NOTIF_ID = 2
        private const val SAMPLE_RATE = 16000.0f
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        startForeground(NOTIF_ID, buildNotification())
        observeRegions()
        observeRegionDrawn()
        observeStopCapture()
        observeListeningToggle()
        initModel()
    }

    private fun initModel() {
        lifecycleScope.launch(Dispatchers.IO) {
            val modelPath = filesDir.absolutePath + "/vosk-model"
            if (!java.io.File(modelPath).exists()) return@launch
            model = Model(modelPath)
            startContinuousListening()
        }
    }

    private fun observeRegions() {
        lifecycleScope.launch {
            repository.observeRegions().collect { regions ->
                currentRegions = regions
                if (!isCapturingPhrase && listeningEnabled) restartContinuousListening()
            }
        }
    }

    private fun observeRegionDrawn() {
        lifecycleScope.launch {
            bus.events.filterIsInstance<AppEvent.RegionDrawn>().collect { event ->
                capturePhrase(event.rect, event.appPackage, event.appLabel, event.orientation)
            }
        }
    }

    private fun observeStopCapture() {
        lifecycleScope.launch {
            bus.events.filterIsInstance<AppEvent.StopPhraseCapture>().collect {
                stopCaptureRequested = true
            }
        }
    }

    private fun observeListeningToggle() {
        lifecycleScope.launch {
            bus.events.filterIsInstance<AppEvent.ListeningEnabledChanged>().collect { event ->
                listeningEnabled = event.enabled
                withContext(Dispatchers.Main) {
                    if (listeningEnabled && !isCapturingPhrase) startContinuousListening()
                    else if (!listeningEnabled) stopContinuousListening()
                }
            }
        }
    }

    private fun startContinuousListening() {
        val m = model ?: return
        val grammar = buildGrammar(currentRegions)
        val recognizer = Recognizer(m, SAMPLE_RATE, grammar)
        speechService = SpeechService(recognizer, SAMPLE_RATE)
        speechService?.startListening(this)
    }

    private fun stopContinuousListening() {
        speechService?.stop()
        speechService?.shutdown()
        speechService = null
    }

    private fun restartContinuousListening() {
        stopContinuousListening()
        startContinuousListening()
    }

    /**
     * Pauses the always-on listener, opens a one-shot Vosk recognizer to capture a
     * single phrase, then restarts the always-on listener.
     */
    private fun capturePhrase(rect: Rect, appPackage: String, appLabel: String, orientation: Int) {
        lifecycleScope.launch(Dispatchers.IO) {
            isCapturingPhrase = true
            withContext(Dispatchers.Main) { stopContinuousListening() }

            val m = model
            if (m == null) {
                bus.emit(AppEvent.PhraseCaptureFailed(rect, appPackage, appLabel, orientation))
                isCapturingPhrase = false
                return@launch
            }

            try {
                val recognizer = Recognizer(m, SAMPLE_RATE)
                val bufferSize = android.media.AudioRecord.getMinBufferSize(
                    16000,
                    android.media.AudioFormat.CHANNEL_IN_MONO,
                    android.media.AudioFormat.ENCODING_PCM_16BIT,
                )
                val recorder = android.media.AudioRecord(
                    android.media.MediaRecorder.AudioSource.MIC,
                    16000,
                    android.media.AudioFormat.CHANNEL_IN_MONO,
                    android.media.AudioFormat.ENCODING_PCM_16BIT,
                    bufferSize * 4,
                )

                recorder.startRecording()
                val buffer = ShortArray(bufferSize)
                var silenceFrames = 0
                val maxSilence = 25  // ~1.5 s of silence stops capture
                val maxIterations = 1000  // safety cap (~60s) in case the user never taps stop
                stopCaptureRequested = false

                var iterations = 0
                while (iterations < maxIterations && !stopCaptureRequested) {
                    val read = recorder.read(buffer, 0, buffer.size)
                    if (read > 0) {
                        val bytes = ByteArray(read * 2)
                        for (i in 0 until read) {
                            bytes[i * 2] = (buffer[i].toInt() and 0xFF).toByte()
                            bytes[i * 2 + 1] = (buffer[i].toInt() shr 8).toByte()
                        }
                        recognizer.acceptWaveForm(bytes, bytes.size)
                        val maxAmp = buffer.take(read).maxOrNull() ?: 0
                        if (maxAmp < 800) silenceFrames++ else silenceFrames = 0
                        if (silenceFrames >= maxSilence) break
                    }
                    iterations++
                }

                recorder.stop()
                recorder.release()

                val phrase = parseVoskResult(recognizer.finalResult).trim().lowercase()
                recognizer.close()

                if (phrase.isNotBlank()) {
                    bus.emit(AppEvent.PhraseRecorded(phrase, rect, appPackage, appLabel, orientation))
                } else {
                    bus.emit(AppEvent.PhraseCaptureFailed(rect, appPackage, appLabel, orientation))
                }
            } catch (e: Exception) {
                e.printStackTrace()
                bus.emit(AppEvent.PhraseCaptureFailed(rect, appPackage, appLabel, orientation))
            } finally {
                isCapturingPhrase = false
                withContext(Dispatchers.Main) { if (listeningEnabled) startContinuousListening() }
            }
        }
    }

    // Always-on recognition result
    override fun onResult(hypothesis: String?) {
        if (isCapturingPhrase) return
        hypothesis ?: return
        val text = parseVoskResult(hypothesis).trim().lowercase()
        if (text.isEmpty() || text == "[unk]") return

        val foregroundPkg = appDetector.getForegroundPackage() ?: return
        val currentOrientation = resources.configuration.orientation
        val matched = currentRegions.firstOrNull {
            it.phrase == text && it.appPackage == foregroundPkg && it.orientation == currentOrientation
        } ?: return
        bus.emit(AppEvent.TriggerTap(matched.rect))
    }

    override fun onPartialResult(hypothesis: String?) {}
    override fun onFinalResult(hypothesis: String?) {}
    override fun onError(exception: Exception?) { exception?.printStackTrace() }
    override fun onTimeout() { if (!isCapturingPhrase && listeningEnabled) startContinuousListening() }

    private fun parseVoskResult(json: String): String =
        Regex("\"text\"\\s*:\\s*\"([^\"]+)\"").find(json)?.groupValues?.get(1) ?: ""

    private fun buildGrammar(regions: List<VoiceRegion>): String {
        if (regions.isEmpty()) return "[\"[unk]\"]"
        val phrases = regions.joinToString(",") { "\"${it.phrase}\"" }
        return "[$phrases, \"[unk]\"]"
    }

    override fun onDestroy() {
        stopContinuousListening()
        super.onDestroy()
    }

    private fun createNotificationChannel() {
        val channel = NotificationChannel(CHANNEL_ID, "VoiceTap Listener", NotificationManager.IMPORTANCE_LOW)
        getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    private fun buildNotification(): Notification =
        Notification.Builder(this, CHANNEL_ID)
            .setContentTitle("VoiceTap listening")
            .setContentText("Say a voice command to trigger a tap")
            .setSmallIcon(android.R.drawable.ic_btn_speak_now)
            .build()
}
