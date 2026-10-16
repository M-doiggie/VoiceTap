package com.voicetap.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun SettingsScreen(
    visualFeedbackEnabled: Boolean,
    soundFeedbackEnabled: Boolean,
    onVisualFeedbackChanged: (Boolean) -> Unit,
    onSoundFeedbackChanged: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier) {
        Text(
            "Trigger feedback",
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
        )
        ListItem(
            leadingContent = { Icon(Icons.Default.Visibility, contentDescription = null) },
            headlineContent = { Text("Visual cue") },
            supportingContent = { Text("Flash the tapped spot when a voice command triggers") },
            trailingContent = {
                Switch(checked = visualFeedbackEnabled, onCheckedChange = onVisualFeedbackChanged)
            },
        )
        HorizontalDivider()
        ListItem(
            leadingContent = { Icon(Icons.Default.VolumeUp, contentDescription = null) },
            headlineContent = { Text("Sound cue") },
            supportingContent = { Text("Play a short tone when a voice command triggers") },
            trailingContent = {
                Switch(checked = soundFeedbackEnabled, onCheckedChange = onSoundFeedbackChanged)
            },
        )
        HorizontalDivider()
    }
}
