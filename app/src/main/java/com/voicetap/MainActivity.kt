package com.voicetap

import android.Manifest
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.lifecycleScope
import com.voicetap.ui.MainScreen
import com.voicetap.ui.PermissionHelper
import com.voicetap.ui.SettingsScreen
import com.voicetap.ui.VoskModelManager
import com.voicetap.vm.MainViewModel
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    private val viewModel: MainViewModel by viewModels()

    private val requestAudio = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { viewModel.refreshPermissions() }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            VoiceTapTheme {
                App(viewModel)
            }
        }
    }

    override fun onResume() {
        super.onResume()
        viewModel.refreshPermissions()
    }

    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    private fun App(vm: MainViewModel) {
        val state by vm.state.collectAsState()
        val snackbar = remember { SnackbarHostState() }
        val scope = rememberCoroutineScope()
        var showSettings by remember { mutableStateOf(false) }

        LaunchedEffect(Unit) {
            if (!state.hasAudioPermission) {
                requestAudio.launch(Manifest.permission.RECORD_AUDIO)
            }
            if (!state.hasOverlayPermission) {
                PermissionHelper.openOverlaySettings(this@MainActivity)
            }
            if (!state.hasUsageAccess) {
                PermissionHelper.openUsageAccessSettings(this@MainActivity)
            }
            if (!state.isAccessibilityEnabled) {
                PermissionHelper.openAccessibilitySettings(this@MainActivity)
            }
            if (!state.isVoskModelReady) {
                scope.launch {
                    snackbar.showSnackbar("Downloading Vosk model…")
                    val ok = VoskModelManager.downloadModel(this@MainActivity) {}
                    snackbar.showSnackbar(if (ok) "Model ready!" else "Model download failed — check internet")
                    vm.refreshPermissions()
                }
            }
        }

        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text(if (showSettings) "Settings" else "VoiceTap") },
                    navigationIcon = {
                        if (showSettings) {
                            IconButton(onClick = { showSettings = false }) {
                                Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                            }
                        }
                    },
                    actions = {
                        if (!showSettings) {
                            IconButton(onClick = { showSettings = true }) {
                                Icon(Icons.Default.Settings, contentDescription = "Settings")
                            }
                        }
                    },
                )
            },
            snackbarHost = { SnackbarHost(snackbar) },
        ) { padding ->
            if (showSettings) {
                SettingsScreen(
                    visualFeedbackEnabled = state.visualFeedbackEnabled,
                    soundFeedbackEnabled = state.soundFeedbackEnabled,
                    onVisualFeedbackChanged = vm::setVisualFeedbackEnabled,
                    onSoundFeedbackChanged = vm::setSoundFeedbackEnabled,
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding),
                )
            } else {
                MainScreen(
                    state = state,
                    onStartServices = vm::startServices,
                    onDeleteRegion = vm::deleteRegion,
                    onConfirmPhrase = vm::confirmPendingRegion,
                    onRetryPhrase = vm::retryPhraseRecording,
                    onStopPhrase = vm::stopPhraseRecording,
                    onCancelPhrase = vm::cancelPendingRegion,
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding),
                )
            }
        }
    }
}
