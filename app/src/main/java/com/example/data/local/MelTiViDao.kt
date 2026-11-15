package com.example.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

/**
 * Объект доступа к данным Room для плейлистов, групп, каналов и кэшированного телегида (EPG).
 */
@Dao
interface MelTiViDao {

    // --- Плейлисты ---

    @Query("SELECT * FROM playlists ORDER BY updated_at DESC, id DESC")
    fun observePlaylists(): Flow<List<PlaylistEntity>>

    @Query("SELECT * FROM playlists WHERE id = :playlistId LIMIT 1")
    suspend fun getPlaylistById(playlistId: Long): PlaylistEntity?

    @Query("SELECT * FROM playlists ORDER BY updated_at DESC, id DESC LIMIT 1")
    suspend fun getFirstPlaylist(): PlaylistEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPlaylist(playlist: PlaylistEntity): Long

    @Update
    suspend fun updatePlaylist(playlist: PlaylistEntity)

    @Query("UPDATE playlists SET name = :name, epg_url = :epgUrl WHERE id = :playlistId")
    suspend fun updatePlaylistSettings(playlistId: Long, name: String, epgUrl: String?)

    @Query("UPDATE playlists SET epg_updated_at = :epgUpdatedAt WHERE id = :playlistId")
    suspend fun updatePlaylistEpgTimestamp(playlistId: Long, epgUpdatedAt: Long)

    @Query("DELETE FROM playlists WHERE id = :playlistId")
    suspend fun deletePlaylistById(playlistId: Long)

    // --- Группы каналов ---

    @Query("SELECT * FROM channel_groups WHERE playlist_id = :playlistId ORDER BY sort_order ASC")
    fun observeGroups(playlistId: Long): Flow<List<ChannelGroupEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertGroups(groups: List<ChannelGroupEntity>): List<Long>

    @Query("DELETE FROM channel_groups WHERE playlist_id = :playlistId")
    suspend fun deleteGroupsByPlaylist(playlistId: Long)

    // --- Каналы ---

    @Query("SELECT * FROM channels WHERE playlist_id = :playlistId ORDER BY sort_order ASC")
    fun observeChannels(playlistId: Long): Flow<List<ChannelEntity>>

    @Query("SELECT * FROM channels WHERE playlist_id = :playlistId ORDER BY sort_order ASC")
    suspend fun getChannelsSync(playlistId: Long): List<ChannelEntity>

    @Query("SELECT * FROM channels WHERE playlist_id = :playlistId AND is_favorite = 1 ORDER BY sort_order ASC")
    fun observeFavoriteChannels(playlistId: Long): Flow<List<ChannelEntity>>

    @Query("SELECT * FROM channels WHERE id = :channelId LIMIT 1")
    suspend fun getChannelById(channelId: Long): ChannelEntity?

    @Query("SELECT stream_url FROM channels WHERE playlist_id = :playlistId AND is_favorite = 1")
    suspend fun getFavoriteStreamUrls(playlistId: Long): List<String>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertChannels(channels: List<ChannelEntity>): List<Long>

    @Query("DELETE FROM channels WHERE playlist_id = :playlistId")
    suspend fun deleteChannelsByPlaylist(playlistId: Long)

    @Query("UPDATE channels SET is_favorite = :isFavorite WHERE id = :channelId")
    suspend fun updateFavorite(channelId: Long, isFavorite: Boolean)

    // --- Телегид (EPG) ---

    @Query("SELECT * FROM epg_programs WHERE playlist_id = :playlistId ORDER BY start_time_ms ASC")
    fun observeEpgPrograms(playlistId: Long): Flow<List<EpgProgramEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertEpgPrograms(programs: List<EpgProgramEntity>)

    @Query("DELETE FROM epg_programs WHERE playlist_id = :playlistId")
    suspend fun deleteEpgByPlaylist(playlistId: Long)

    /**
     * Полностью заменяет кэшированный телегид для плейлиста в одной транзакции.
     */
    @Transaction
    suspend fun replaceEpgForPlaylist(
        playlistId: Long,
        programs: List<EpgProgramEntity>,
        updatedAt: Long
    ) {
        deleteEpgByPlaylist(playlistId)
        programs.chunked(500).forEach { chunk ->
            insertEpgPrograms(chunk)
        }
        updatePlaylistEpgTimestamp(playlistId, updatedAt)
    }

    /**
     * Полностью заменяет группы и каналы плейлиста в одной транзакции.
     * Пакетная вставка безопасно работает на списках из тысяч каналов.
     */
    @Transaction
    suspend fun replacePlaylistContent(
        playlist: PlaylistEntity,
        groupsWithChannels: List<Pair<ChannelGroupEntity, List<ChannelEntity>>>
    ): Long {
        val playlistId = if (playlist.id == 0L) {
            insertPlaylist(playlist)
        } else {
            updatePlaylist(playlist)
            deleteChannelsByPlaylist(playlist.id)
            deleteGroupsByPlaylist(playlist.id)
            playlist.id
        }

        val groupEntities = groupsWithChannels.map { (group, _) ->
            group.copy(playlistId = playlistId)
        }
        val insertedGroupIds = insertGroups(groupEntities)

        val allChannels = ArrayList<ChannelEntity>(playlist.channelCount)
        groupsWithChannels.forEachIndexed { index, (_, channels) ->
            val groupId = insertedGroupIds[index]
            channels.forEach { channel ->
                allChannels.add(
                    channel.copy(
                        playlistId = playlistId,
                        groupId = groupId
                    )
                )
            }
        }

        // Разбиваем вставку на порции по 500 каналов для стабильной работы SQLite на больших плейлистах
        allChannels.chunked(500).forEach { chunk ->
            insertChannels(chunk)
        }

        return playlistId
    }
}
