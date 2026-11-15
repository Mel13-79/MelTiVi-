package com.example.data.repository

import android.content.Context
import android.net.Uri
import com.example.R
import com.example.data.local.ChannelEntity
import com.example.data.local.ChannelGroupEntity
import com.example.data.local.EpgProgramEntity
import com.example.data.local.LastPlaybackState
import com.example.data.local.MelTiViDao
import com.example.data.local.PlaybackPreferences
import com.example.data.local.PlaylistEntity
import com.example.data.network.EpgDownloader
import com.example.data.network.EpgLoadException
import com.example.data.network.PlaylistDownloader
import com.example.data.parser.ParsedChannel
import com.example.data.parser.XmltvParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext

/**
 * Единый репозиторий приложения MelTiVi для работы с плейлистами, каналами, избранным,
 * телегидом (EPG) и последним просмотренным каналом (общий для режимов TvUi и PhoneUi).
 */
class IptvRepository(
    private val context: Context,
    private val dao: MelTiViDao,
    private val downloader: PlaylistDownloader,
    private val epgDownloader: EpgDownloader,
    private val preferences: PlaybackPreferences,
    private val xmltvParser: XmltvParser = XmltvParser()
) {

    val playlists: Flow<List<PlaylistEntity>> = dao.observePlaylists()
    val lastPlaybackState: Flow<LastPlaybackState> = preferences.lastPlaybackState

    fun observeGroups(playlistId: Long): Flow<List<ChannelGroupEntity>> =
        dao.observeGroups(playlistId)

    fun observeChannels(playlistId: Long): Flow<List<ChannelEntity>> =
        dao.observeChannels(playlistId)

    fun observeFavoriteChannels(playlistId: Long): Flow<List<ChannelEntity>> =
        dao.observeFavoriteChannels(playlistId)

    fun observeEpgPrograms(playlistId: Long): Flow<List<EpgProgramEntity>> =
        dao.observeEpgPrograms(playlistId)

    suspend fun getPlaylistById(playlistId: Long): PlaylistEntity? =
        dao.getPlaylistById(playlistId)

    suspend fun getChannelById(channelId: Long): ChannelEntity? =
        dao.getChannelById(channelId)

    suspend fun saveLastPlaylist(playlistId: Long) {
        preferences.saveLastPlaylist(playlistId)
    }

    suspend fun saveLastChannel(playlistId: Long, channelId: Long) {
        preferences.saveLastChannel(playlistId, channelId)
    }

    /**
     * Скачивает новый M3U-плейлист по URL, парсит его и сохраняет в Room.
     * Если пользователь указал ссылку на XMLTV или она есть в заголовке #EXTM3U — сохраняет её.
     */
    suspend fun addPlaylistFromUrl(
        name: String,
        url: String,
        epgUrl: String? = null
    ): PlaylistEntity = withContext(Dispatchers.IO) {
        val cleanUrl = url.trim()
        val parsed = downloader.downloadAndParse(cleanUrl)
        val parsedChannels = parsed.channels
        val resolvedName = name.trim().ifEmpty { derivePlaylistName(cleanUrl) }
        val resolvedEpgUrl = epgUrl?.trim()?.takeIf { it.isNotEmpty() } ?: parsed.epgUrlFromHeader
        val now = System.currentTimeMillis()

        val groupsWithChannels = buildGroupsAndChannels(
            playlistId = 0L,
            parsedChannels = parsedChannels,
            favoriteStreamUrls = emptySet()
        )

        val entity = PlaylistEntity(
            name = resolvedName,
            url = cleanUrl,
            channelCount = parsedChannels.size,
            updatedAt = now,
            epgUrl = resolvedEpgUrl,
            epgUpdatedAt = null
        )
        val newId = dao.replacePlaylistContent(entity, groupsWithChannels)
        preferences.saveLastPlaylist(newId)
        entity.copy(id = newId)
    }

    /**
     * Обновляет существующий плейлист по его URL, сохраняя избранные каналы,
     * настройки EPG и ссылку на последний открытый канал (по совпадению URL потока).
     */
    suspend fun refreshPlaylist(playlistId: Long): PlaylistEntity =
        withContext(Dispatchers.IO) {
            val existing = dao.getPlaylistById(playlistId)
                ?: throw IllegalStateException("Плейлист не найден")
            val favoriteUrls = dao.getFavoriteStreamUrls(playlistId).toHashSet()
            val lastState = preferences.lastPlaybackState.first()
            val previousLastChannelUrl = if (lastState.lastPlaylistId == playlistId && lastState.lastChannelId != null) {
                dao.getChannelById(lastState.lastChannelId)?.streamUrl
            } else {
                null
            }

            val parsed = downloader.downloadAndParse(existing.url)
            val parsedChannels = parsed.channels
            val resolvedEpgUrl = existing.epgUrl?.takeIf { it.isNotBlank() } ?: parsed.epgUrlFromHeader
            val updatedPlaylist = existing.copy(
                channelCount = parsedChannels.size,
                updatedAt = System.currentTimeMillis(),
                epgUrl = resolvedEpgUrl
            )
            val groupsWithChannels = buildGroupsAndChannels(
                playlistId = playlistId,
                parsedChannels = parsedChannels,
                favoriteStreamUrls = favoriteUrls
            )

            dao.replacePlaylistContent(updatedPlaylist, groupsWithChannels)

            if (previousLastChannelUrl != null) {
                val refreshedChannels = dao.getChannelsSync(playlistId)
                val matched = refreshedChannels.firstOrNull { it.streamUrl == previousLastChannelUrl }
                if (matched != null) {
                    preferences.saveLastChannel(playlistId, matched.id)
                } else {
                    preferences.clearLastChannel()
                }
            }
            updatedPlaylist
        }

    /**
     * Сохраняет настройки плейлиста (название и ссылку на XMLTV-телегид).
     */
    suspend fun updatePlaylistSettings(
        playlistId: Long,
        name: String,
        epgUrl: String?
    ) {
        withContext(Dispatchers.IO) {
            val existing = dao.getPlaylistById(playlistId) ?: return@withContext
            val resolvedName = name.trim().ifEmpty { existing.name }
            val resolvedEpg = epgUrl?.trim()?.takeIf { it.isNotEmpty() }
            dao.updatePlaylistSettings(playlistId, resolvedName, resolvedEpg)
        }
    }

    /**
     * Скачивает XMLTV (.xml или .xml.gz) по URL из настроек плейлиста,
     * сопоставляет передачи по tvg-id или точному названию канала и кэширует в Room.
     */
    suspend fun refreshEpg(playlistId: Long): Int = withContext(Dispatchers.IO) {
        val playlist = dao.getPlaylistById(playlistId)
            ?: throw IllegalStateException("Плейлист не найден")
        val epgUrl = playlist.epgUrl?.trim()
            ?.takeIf { it.isNotEmpty() }
            ?: throw EpgLoadException(context.getString(R.string.error_empty_epg_url))

        val xmltvData = epgDownloader.downloadAndParse(epgUrl)
        val channels = dao.getChannelsSync(playlistId)
        val matchedPrograms = xmltvParser.matchProgramsToChannels(xmltvData, channels)

        if (matchedPrograms.isEmpty()) {
            throw EpgLoadException(context.getString(R.string.error_empty_epg))
        }

        val entities = matchedPrograms.map { prog ->
            EpgProgramEntity(
                playlistId = playlistId,
                channelId = prog.channelId,
                title = prog.title,
                description = prog.description,
                category = prog.category,
                startTimeMs = prog.startTimeMs,
                endTimeMs = prog.endTimeMs
            )
        }
        dao.replaceEpgForPlaylist(
            playlistId = playlistId,
            programs = entities,
            updatedAt = System.currentTimeMillis()
        )
        entities.size
    }

    /**
     * Удаляет плейлист и все его группы/каналы/передачи EPG (каскадно через внешний ключ Room).
     */
    suspend fun deletePlaylist(playlistId: Long) {
        withContext(Dispatchers.IO) {
            dao.deletePlaylistById(playlistId)
            preferences.clearLastIfPlaylistDeleted(playlistId)
            val remaining = dao.getFirstPlaylist()
            if (remaining != null) {
                val current = preferences.lastPlaybackState.first()
                if (current.lastPlaylistId == null) {
                    preferences.saveLastPlaylist(remaining.id)
                }
            }
        }
    }

    /**
     * Переключает статус «Избранное» для канала.
     */
    suspend fun toggleFavorite(channel: ChannelEntity) {
        dao.updateFavorite(channel.id, !channel.isFavorite)
    }

    private fun buildGroupsAndChannels(
        playlistId: Long,
        parsedChannels: List<ParsedChannel>,
        favoriteStreamUrls: Set<String>
    ): List<Pair<ChannelGroupEntity, List<ChannelEntity>>> {
        val defaultGroupName = context.getString(R.string.channels_ungrouped)
        val grouped = LinkedHashMap<String, MutableList<ParsedChannel>>()

        for (item in parsedChannels) {
            val groupTitle = item.groupName?.takeIf { it.isNotBlank() } ?: defaultGroupName
            grouped.getOrPut(groupTitle) { ArrayList() }.add(item)
        }

        val result = ArrayList<Pair<ChannelGroupEntity, List<ChannelEntity>>>(grouped.size)
        var groupIndex = 0
        for ((groupName, items) in grouped) {
            val groupEntity = ChannelGroupEntity(
                playlistId = playlistId,
                name = groupName,
                channelCount = items.size,
                sortOrder = groupIndex++
            )
            val channelEntities = items.map { ch ->
                ChannelEntity(
                    playlistId = playlistId,
                    groupId = 0L,
                    groupName = groupName,
                    name = ch.name,
                    streamUrl = ch.streamUrl,
                    logoUrl = ch.logoUrl,
                    tvgId = ch.tvgId,
                    isFavorite = favoriteStreamUrls.contains(ch.streamUrl),
                    sortOrder = ch.sortOrder
                )
            }
            result.add(groupEntity to channelEntities)
        }
        return result
    }

    private fun derivePlaylistName(url: String): String {
        val uri = runCatching { Uri.parse(url) }.getOrNull()
        val fileName = uri?.lastPathSegment
            ?.substringBeforeLast('.')
            ?.takeIf { it.isNotBlank() && it.length <= 36 }
        val host = uri?.host?.removePrefix("www.")?.takeIf { it.isNotBlank() }
        return fileName ?: host ?: "Мой плейлист"
    }
}
