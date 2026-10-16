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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Cancel
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
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
    onStopPhrase: () -> Unit,
    onCancelPhrase: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.padding(16.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("VoiceTap", style = MaterialTheme.typography.headlineMedium)
            StatusBadge(active = state.servicesRunning)
        }
        Spacer(Modifier.height(16.dp))

        if (!state.servicesRunning) {
            PermissionsCard(state, onStartServices)
            Spacer(Modifier.height(16.dp))
        }

        if (state.regions.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                EmptyRegionsHint()
            }
        } else {
            Text(
                "Saved regions (${state.regions.size})",
                style = MaterialTheme.typography.titleMedium,
            )
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
            onStop = onStopPhrase,
            onCancel = onCancelPhrase,
        )
    }
}

@Composable
private fun StatusBadge(active: Boolean) {
    Surface(
        color = if (active) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.errorContainer,
        contentColor = if (active) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onErrorContainer,
        shape = MaterialTheme.shapes.small,
    ) {
        Text(
            if (active) "Active" else "Setup needed",
            style = MaterialTheme.typography.labelMedium,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
        )
    }
}

@Composable
private fun EmptyRegionsHint() {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Icon(
            Icons.Default.Mic,
            contentDescription = null,
            modifier = Modifier.size(48.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(12.dp))
        Text("No regions yet", style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(4.dp))
        Text(
            "Tap the floating ＋ button to draw one.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
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
            .padding(vertical = 6.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, style = MaterialTheme.typography.bodyMedium)
        Icon(
            if (granted) Icons.Default.CheckCircle else Icons.Default.Cancel,
            contentDescription = if (granted) "Granted" else "Not granted",
            tint = if (granted) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
            modifier = Modifier.size(20.dp),
        )
    }
}

@Composable
private fun OrientationBadge(orientation: Int) {
    val label = if (orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE) "Landscape" else "Portrait"
    Surface(
        color = MaterialTheme.colorScheme.secondaryContainer,
        contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
        shape = MaterialTheme.shapes.extraSmall,
    ) {
        Text(
            label,
            style = MaterialTheme.typography.labelSmall,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
        )
    }
}

@Composable
private fun RegionCard(region: VoiceRegion, onDelete: (Long) -> Unit) {
    ElevatedCard(Modifier.fillMaxWidth()) {
        ListItem(
            leadingContent = {
                Icon(Icons.Default.Mic, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            },
            headlineContent = {
                Text("\"${region.phrase}\"", style = MaterialTheme.typography.titleSmall)
            },
            supportingContent = {
                Column {
                    Text(region.appLabel, style = MaterialTheme.typography.bodySmall)
                    Spacer(Modifier.height(6.dp))
                    OrientationBadge(region.orientation)
                }
            },
            trailingContent = {
                IconButton(onClick = { onDelete(region.id) }) {
                    Icon(
                        Icons.Default.Delete,
                        contentDescription = "Delete",
                        tint = MaterialTheme.colorScheme.error,
                    )
                }
            },
        )
    }
}

@Composable
private fun RecordPhraseDialog(
    pending: PendingRegion,
    isRecording: Boolean,
    recordedPhrase: String,
    onConfirm: () -> Unit,
    onRetry: () -> Unit,
    onStop: () -> Unit,
    onCancel: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onCancel,
        title = { Text("Name this region") },
        text = {
            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                Text("App: ${pending.appLabel}", style = MaterialTheme.typography.bodySmall)
                Spacer(Modifier.height(16.dp))
                if (isRecording) {
                    Icon(
                        Icons.Default.Mic,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(40.dp),
                    )
                    Spacer(Modifier.height(12.dp))
                    CircularProgressIndicator(modifier = Modifier.size(24.dp))
                    Spacer(Modifier.height(12.dp))
                    Text("Listening… speak your trigger phrase now")
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "Done speaking? Tap Stop.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                } else if (recordedPhrase.isNotBlank()) {
                    Icon(
                        Icons.Default.CheckCircle,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(32.dp),
                    )
                    Spacer(Modifier.height(8.dp))
                    Text("Heard: \"$recordedPhrase\"", style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "Is this correct?",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                } else {
                    Text("Nothing captured — try again or cancel.")
                }
            }
        },
        confirmButton = {
            if (isRecording) {
                TextButton(onClick = onStop) { Text("Stop") }
            } else {
                TextButton(onClick = onConfirm, enabled = recordedPhrase.isNotBlank()) {
                    Text("Save")
                }
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
