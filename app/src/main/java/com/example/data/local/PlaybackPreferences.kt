package com.example.data.local

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.melTiViDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "meltivi_preferences"
)

/**
 * Сохранённое состояние последнего открытого плейлиста и канала.
 */
data class LastPlaybackState(
    val lastPlaylistId: Long? = null,
    val lastChannelId: Long? = null
)

/**
 * Хранилище локальных настроек воспроизведения на базе Jetpack DataStore.
 */
class PlaybackPreferences(private val context: Context) {

    private object Keys {
        val LAST_PLAYLIST_ID = longPreferencesKey("last_playlist_id")
        val LAST_CHANNEL_ID = longPreferencesKey("last_channel_id")
    }

    val lastPlaybackState: Flow<LastPlaybackState> = context.melTiViDataStore.data.map { prefs ->
        LastPlaybackState(
            lastPlaylistId = prefs[Keys.LAST_PLAYLIST_ID]?.takeIf { it > 0L },
            lastChannelId = prefs[Keys.LAST_CHANNEL_ID]?.takeIf { it > 0L }
        )
    }

    suspend fun saveLastPlaylist(playlistId: Long) {
        context.melTiViDataStore.edit { prefs ->
            prefs[Keys.LAST_PLAYLIST_ID] = playlistId
        }
    }

    suspend fun saveLastChannel(playlistId: Long, channelId: Long) {
        context.melTiViDataStore.edit { prefs ->
            prefs[Keys.LAST_PLAYLIST_ID] = playlistId
            prefs[Keys.LAST_CHANNEL_ID] = channelId
        }
    }

    suspend fun clearLastChannel() {
        context.melTiViDataStore.edit { prefs ->
            prefs.remove(Keys.LAST_CHANNEL_ID)
        }
    }

    suspend fun clearLastIfPlaylistDeleted(deletedPlaylistId: Long) {
        context.melTiViDataStore.edit { prefs ->
            if (prefs[Keys.LAST_PLAYLIST_ID] == deletedPlaylistId) {
                prefs.remove(Keys.LAST_PLAYLIST_ID)
                prefs.remove(Keys.LAST_CHANNEL_ID)
            }
        }
    }
}
