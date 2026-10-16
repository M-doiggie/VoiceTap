package com.voicetap.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.settingsDataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

@Singleton
class SettingsRepository @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private object Keys {
        val VISUAL_FEEDBACK = booleanPreferencesKey("visual_feedback")
        val SOUND_FEEDBACK = booleanPreferencesKey("sound_feedback")
    }

    val visualFeedbackEnabled: Flow<Boolean> =
        context.settingsDataStore.data.map { it[Keys.VISUAL_FEEDBACK] ?: true }

    val soundFeedbackEnabled: Flow<Boolean> =
        context.settingsDataStore.data.map { it[Keys.SOUND_FEEDBACK] ?: true }

    suspend fun setVisualFeedbackEnabled(enabled: Boolean) {
        context.settingsDataStore.edit { it[Keys.VISUAL_FEEDBACK] = enabled }
    }

    suspend fun setSoundFeedbackEnabled(enabled: Boolean) {
        context.settingsDataStore.edit { it[Keys.SOUND_FEEDBACK] = enabled }
    }
}
