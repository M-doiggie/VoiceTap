package com.voicetap.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.voicetap.data.VoiceRegion
import com.voicetap.vm.PendingRegion
import com.voicetap.vm.UiState

@Composable
fun MainScreen(
    state: UiState,
    onStartServices: () -> Unit,
    onDeleteRegion: (Long) -> Unit,
    onConfirmPhrase: () -> Unit,
    onRetryPhrase: () -> Unit,
    onCancelPhrase: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.padding(16.dp),
    ) {
        Text("VoiceTap", style = MaterialTheme.typography.headlineMedium)
        Spacer(Modifier.height(8.dp))

        if (!state.servicesRunning) {
            PermissionsCard(state, onStartServices)
        }

        Spacer(Modifier.height(16.dp))

        if (state.regions.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    "No regions yet.\nTap the floating ＋ button to draw one.",
                    style = MaterialTheme.typography.bodyLarge,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                )
            }
        } else {
            Text("Saved regions", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(8.dp))
            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(state.regions, key = { it.id }) { region ->
                    RegionCard(region, onDeleteRegion)
                }
            }
        }
    }

    if (state.pendingRegion != null) {
        RecordPhraseDialog(
            pending = state.pendingRegion,
            isRecording = state.isRecordingPhrase,
            recordedPhrase = state.recordedPhrase,
            onConfirm = onConfirmPhrase,
            onRetry = onRetryPhrase,
            onCancel = onCancelPhrase,
        )
    }
}

@Composable
private fun PermissionsCard(state: UiState, onStart: () -> Unit) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            Text("Setup required", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(8.dp))
            PermissionRow("Draw over other apps", state.hasOverlayPermission)
            PermissionRow("Microphone", state.hasAudioPermission)
            PermissionRow("Usage access", state.hasUsageAccess)
            PermissionRow("Accessibility service", state.isAccessibilityEnabled)
            PermissionRow("Vosk model downloaded", state.isVoskModelReady)
            Spacer(Modifier.height(12.dp))
            val allReady = state.hasOverlayPermission && state.hasAudioPermission &&
                    state.hasUsageAccess && state.isAccessibilityEnabled && state.isVoskModelReady
            Button(onClick = onStart, enabled = allReady, modifier = Modifier.fillMaxWidth()) {
                Text(if (allReady) "Start VoiceTap" else "Complete setup above first")
            }
        }
    }
}

@Composable
private fun PermissionRow(label: String, granted: Boolean) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(label, style = MaterialTheme.typography.bodyMedium)
        Text(
            if (granted) "✓" else "✗",
            color = if (granted) androidx.compose.ui.graphics.Color(0xFF4CAF50)
            else MaterialTheme.colorScheme.error,
            style = MaterialTheme.typography.bodyMedium,
        )
    }
}

@Composable
private fun RegionCard(region: VoiceRegion, onDelete: (Long) -> Unit) {
    Card(Modifier.fillMaxWidth()) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text("\"${region.phrase}\"", style = MaterialTheme.typography.titleSmall)
                Text(region.appLabel, style = MaterialTheme.typography.bodySmall)
                Text(
                    "Region: ${region.rect.left},${region.rect.top} → ${region.rect.right},${region.rect.bottom}",
                    style = MaterialTheme.typography.labelSmall,
                )
            }
            IconButton(onClick = { onDelete(region.id) }) {
                Icon(Icons.Default.Delete, contentDescription = "Delete")
            }
        }
    }
}

@Composable
private fun RecordPhraseDialog(
    pending: PendingRegion,
    isRecording: Boolean,
    recordedPhrase: String,
    onConfirm: () -> Unit,
    onRetry: () -> Unit,
    onCancel: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onCancel,
        title = { Text("Name this region") },
        text = {
            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                Text("App: ${pending.appLabel}", style = MaterialTheme.typography.bodySmall)
                Spacer(Modifier.height(12.dp))
                if (isRecording) {
                    CircularProgressIndicator()
                    Spacer(Modifier.height(8.dp))
                    Text("Listening… speak your trigger phrase now")
                    Icon(Icons.Default.Mic, contentDescription = null)
                } else if (recordedPhrase.isNotBlank()) {
                    Text("Heard: \"$recordedPhrase\"", style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.height(4.dp))
                    Text("Is this correct?", style = MaterialTheme.typography.bodySmall)
                } else {
                    Text("Nothing captured — try again or cancel.")
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onConfirm, enabled = recordedPhrase.isNotBlank() && !isRecording) {
                Text("Save")
            }
        },
        dismissButton = {
            Row {
                TextButton(onClick = onRetry, enabled = !isRecording) { Text("Retry") }
                TextButton(onClick = onCancel) { Text("Cancel") }
            }
        },
    )
}
