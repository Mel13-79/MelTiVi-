package com.example.ui.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.R
import com.example.data.local.ChannelEntity
import com.example.data.local.ChannelGroupEntity
import com.example.data.local.EpgProgramEntity
import com.example.data.local.LastPlaybackState
import com.example.data.local.MelTiViDatabase
import com.example.data.local.PlaybackPreferences
import com.example.data.local.PlaylistEntity
import com.example.data.network.EpgDownloader
import com.example.data.network.EpgLoadException
import com.example.data.network.PlaylistDownloader
import com.example.data.network.PlaylistLoadException
import com.example.data.repository.IptvRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * Секция каналов, объединённая по группе, для отображения рядами (Leanback / мобильные ряды).
 */
data class ChannelGroupSection(
    val groupId: Long,
    val groupName: String,
    val channels: List<ChannelEntity>
)

/**
 * Текущая («сейчас идёт») и следующая («далее») передачи для телеканала.
 */
data class ChannelNowNext(
    val current: EpgProgramEntity? = null,
    val next: EpgProgramEntity? = null
)

/**
 * Прогресс загрузки телегида (EPG): прочитано байт из общего количества.
 * Если [totalBytes] не определён (<= 0), прогресс считается неопределённым.
 */
data class EpgLoadProgress(
    val bytesRead: Long,
    val totalBytes: Long
) {
    val isIndeterminate: Boolean get() = totalBytes <= 0L
    val fraction: Float
        get() = if (totalBytes > 0L) {
            (bytesRead.toFloat() / totalBytes.toFloat()).coerceIn(0f, 1f)
        } else {
            0f
        }
}

/**
 * Общий ViewModel для телефонного и ТВ-интерфейсов (PhoneUi / TvUi).
 */
@OptIn(ExperimentalCoroutinesApi::class)
class MelTiViViewModel(application: Application) : AndroidViewModel(application) {

    private val database = MelTiViDatabase.getInstance(application)
    private val preferences = PlaybackPreferences(application)
    private val downloader = PlaylistDownloader(application)
    private val epgDownloader = EpgDownloader(application)
    private val repository = IptvRepository(
        context = application,
        dao = database.dao(),
        downloader = downloader,
        epgDownloader = epgDownloader,
        preferences = preferences
    )

    val playlists: StateFlow<List<PlaylistEntity>> = repository.playlists
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val lastPlaybackState = repository.lastPlaybackState
        .stateIn(viewModelScope, SharingStarted.Eagerly, LastPlaybackState())

    private val _selectedPlaylistId = MutableStateFlow<Long?>(null)

    /**
     * Активный плейлист: выбранный вручную или сохранённый при прошлом запуске.
     */
    val activePlaylist: StateFlow<PlaylistEntity?> = combine(
        playlists,
        lastPlaybackState,
        _selectedPlaylistId
    ) { allPlaylists, lastState, manualId ->
        val targetId = manualId ?: lastState.lastPlaylistId
        allPlaylists.firstOrNull { it.id == targetId } ?: allPlaylists.firstOrNull()
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    val groups: StateFlow<List<ChannelGroupEntity>> = activePlaylist
        .flatMapLatest { playlist ->
            if (playlist == null) flowOf(emptyList())
            else repository.observeGroups(playlist.id)
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val allChannels: StateFlow<List<ChannelEntity>> = activePlaylist
        .flatMapLatest { playlist ->
            if (playlist == null) flowOf(emptyList())
            else repository.observeChannels(playlist.id)
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val favoriteChannels: StateFlow<List<ChannelEntity>> = activePlaylist
        .flatMapLatest { playlist ->
            if (playlist == null) flowOf(emptyList())
            else repository.observeFavoriteChannels(playlist.id)
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    /**
     * Кэшированные в Room программы телегида для активного плейлиста.
     */
    private val allEpgPrograms: StateFlow<List<EpgProgramEntity>> = activePlaylist
        .flatMapLatest { playlist ->
            if (playlist == null) flowOf(emptyList())
            else repository.observeEpgPrograms(playlist.id)
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    /**
     * Поток текущего времени (обновляется каждые 30 секунд) для линии времени и блоков «Сейчас / Далее».
     */
    private val tickerFlow: Flow<Long> = flow {
        while (true) {
            emit(System.currentTimeMillis())
            delay(30_000L)
        }
    }

    val currentTimeMs: StateFlow<Long> = tickerFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), System.currentTimeMillis())

    /**
     * Карта передач по ID канала (отсортированных по времени начала) для сетки телегида.
     */
    val epgProgramsByChannel: StateFlow<Map<Long, List<EpgProgramEntity>>> = allEpgPrograms
        .combine(activePlaylist) { programs, _ ->
            if (programs.isEmpty()) emptyMap()
            else programs.groupBy { it.channelId }
        }
        .flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyMap())

    /**
     * Карта «сейчас идёт» и «далее» для каждого канала активного плейлиста.
     */
    val channelNowNextMap: StateFlow<Map<Long, ChannelNowNext>> = combine(
        epgProgramsByChannel,
        currentTimeMs
    ) { byChannel, now ->
        if (byChannel.isEmpty()) return@combine emptyMap()
        val result = HashMap<Long, ChannelNowNext>(byChannel.size)
        for ((channelId, programs) in byChannel) {
            val currentIdx = programs.indexOfFirst { it.startTimeMs <= now && it.endTimeMs > now }
            if (currentIdx >= 0) {
                result[channelId] = ChannelNowNext(
                    current = programs[currentIdx],
                    next = programs.getOrNull(currentIdx + 1)
                )
            } else {
                val upcoming = programs.firstOrNull { it.startTimeMs > now }
                if (upcoming != null) {
                    result[channelId] = ChannelNowNext(current = null, next = upcoming)
                }
            }
        }
        result
    }.flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyMap())

    /**
     * Последний открытый канал в активном плейлисте, чтобы открывать плейлист именно на нём.
     */
    val lastWatchedChannel: StateFlow<ChannelEntity?> = combine(
        allChannels,
        lastPlaybackState
    ) { channels, lastState ->
        val lastId = lastState.lastChannelId ?: return@combine null
        channels.firstOrNull { it.id == lastId }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    private val _selectedGroupId = MutableStateFlow<Long?>(null)
    val selectedGroupId: StateFlow<Long?> = _selectedGroupId.asStateFlow()

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    /**
     * Отфильтрованный плоский список каналов для сетки и поиска.
     */
    val filteredChannels: StateFlow<List<ChannelEntity>> = combine(
        allChannels,
        _selectedGroupId,
        _searchQuery
    ) { channels, groupId, query ->
        val trimmed = query.trim()
        channels.filter { ch ->
            val matchesGroup = groupId == null || ch.groupId == groupId
            val matchesQuery = trimmed.isEmpty() || ch.name.contains(trimmed, ignoreCase = true)
            matchesGroup && matchesQuery
        }
    }.flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    /**
     * Сгруппированные секции каналов для горизонтальных рядов (Leanback на ТВ и ряды на телефоне).
     */
    val groupedSections: StateFlow<List<ChannelGroupSection>> = combine(
        groups,
        filteredChannels
    ) { groupList, channels ->
        if (channels.isEmpty()) return@combine emptyList()
        val byGroup = channels.groupBy { it.groupId }
        val sections = ArrayList<ChannelGroupSection>(byGroup.size)
        for (group in groupList) {
            val list = byGroup[group.id]
            if (!list.isNullOrEmpty()) {
                sections.add(
                    ChannelGroupSection(
                        groupId = group.id,
                        groupName = group.name,
                        channels = list
                    )
                )
            }
        }
        sections
    }.flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // --- Состояния операций с плейлистами и телегидом ---

    private val _isAddingPlaylist = MutableStateFlow(false)
    val isAddingPlaylist: StateFlow<Boolean> = _isAddingPlaylist.asStateFlow()

    private val _refreshingPlaylistId = MutableStateFlow<Long?>(null)
    val refreshingPlaylistId: StateFlow<Long?> = _refreshingPlaylistId.asStateFlow()

    private val _isRefreshingEpg = MutableStateFlow(false)
    val isRefreshingEpg: StateFlow<Boolean> = _isRefreshingEpg.asStateFlow()

    private val _epgLoadProgress = MutableStateFlow<EpgLoadProgress?>(null)
    val epgLoadProgress: StateFlow<EpgLoadProgress?> = _epgLoadProgress.asStateFlow()

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()

    private val _infoMessage = MutableStateFlow<String?>(null)
    val infoMessage: StateFlow<String?> = _infoMessage.asStateFlow()

    // --- Текущий воспроизводимый канал и очередь переключения ---

    private val _currentPlayingChannel = MutableStateFlow<ChannelEntity?>(null)
    val currentPlayingChannel: StateFlow<ChannelEntity?> = _currentPlayingChannel.asStateFlow()

    private val _playbackQueue = MutableStateFlow<List<ChannelEntity>>(emptyList())

    fun selectPlaylist(playlistId: Long) {
        _selectedPlaylistId.value = playlistId
        _selectedGroupId.value = null
        _searchQuery.value = ""
        viewModelScope.launch {
            repository.saveLastPlaylist(playlistId)
        }
    }

    fun selectGroup(groupId: Long?) {
        _selectedGroupId.value = groupId
    }

    fun updateSearchQuery(query: String) {
        _searchQuery.value = query
    }

    fun toggleFavorite(channel: ChannelEntity) {
        viewModelScope.launch {
            repository.toggleFavorite(channel)
            if (_currentPlayingChannel.value?.id == channel.id) {
                _currentPlayingChannel.value = channel.copy(isFavorite = !channel.isFavorite)
            }
        }
    }

    fun addPlaylist(
        name: String,
        url: String,
        epgUrl: String? = null,
        onSuccess: () -> Unit = {}
    ) {
        if (_isAddingPlaylist.value) return
        viewModelScope.launch {
            _isAddingPlaylist.value = true
            _errorMessage.value = null
            try {
                val created = repository.addPlaylistFromUrl(name, url, epgUrl)
                _selectedPlaylistId.value = created.id
                _selectedGroupId.value = null
                _searchQuery.value = ""
                _infoMessage.value = getApplication<Application>()
                    .getString(R.string.playlists_loaded_toast, created.channelCount)
                onSuccess()

                // Если задана ссылка на телегид, пробуем сразу кэшировать программу передач
                if (!created.epgUrl.isNullOrBlank()) {
                    refreshEpg(created.id)
                }
            } catch (e: PlaylistLoadException) {
                _errorMessage.value = e.message
            } catch (_: Exception) {
                _errorMessage.value = getApplication<Application>()
                    .getString(R.string.error_network_playlist)
            } finally {
                _isAddingPlaylist.value = false
            }
        }
    }

    fun updatePlaylistSettings(
        playlistId: Long,
        name: String,
        epgUrl: String?,
        loadEpgNow: Boolean,
        onSuccess: () -> Unit = {}
    ) {
        viewModelScope.launch {
            repository.updatePlaylistSettings(playlistId, name, epgUrl)
            onSuccess()
            if (loadEpgNow && !epgUrl.isNullOrBlank()) {
                refreshEpg(playlistId)
            }
        }
    }

    fun refreshPlaylist(playlistId: Long) {
        if (_refreshingPlaylistId.value != null) return
        viewModelScope.launch {
            _refreshingPlaylistId.value = playlistId
            _errorMessage.value = null
            try {
                val updated = repository.refreshPlaylist(playlistId)
                _infoMessage.value = getApplication<Application>()
                    .getString(R.string.playlists_loaded_toast, updated.channelCount)
            } catch (e: PlaylistLoadException) {
                _errorMessage.value = e.message
            } catch (_: Exception) {
                _errorMessage.value = getApplication<Application>()
                    .getString(R.string.error_network_playlist)
            } finally {
                _refreshingPlaylistId.value = null
            }
        }
    }

    /**
     * Обновляет и кэширует телегид (EPG) в Room для указанного или активного плейлиста.
     */
    fun refreshEpg(playlistId: Long? = activePlaylist.value?.id) {
        val targetId = playlistId ?: return
        if (_isRefreshingEpg.value) return
        viewModelScope.launch {
            _isRefreshingEpg.value = true
            _errorMessage.value = null
            _epgLoadProgress.value = EpgLoadProgress(0L, 0L)
            try {
                val count = repository.refreshEpg(targetId) { read, total ->
                    _epgLoadProgress.value = EpgLoadProgress(read, total)
                }
                _infoMessage.value = getApplication<Application>()
                    .getString(R.string.guide_loaded_toast, count)
            } catch (e: EpgLoadException) {
                _errorMessage.value = e.message
            } catch (_: Exception) {
                _errorMessage.value = getApplication<Application>()
                    .getString(R.string.error_network_epg)
            } finally {
                _isRefreshingEpg.value = false
                _epgLoadProgress.value = null
            }
        }
    }

    fun deletePlaylist(playlistId: Long) {
        viewModelScope.launch {
            repository.deletePlaylist(playlistId)
            if (_selectedPlaylistId.value == playlistId) {
                _selectedPlaylistId.value = null
            }
        }
    }

    /**
     * Открывает выбранный канал в плеере и сохраняет его как последний открытый.
     */
    fun openChannel(channel: ChannelEntity, queue: List<ChannelEntity>? = null) {
        val list = queue?.takeIf { it.isNotEmpty() } ?: allChannels.value
        _playbackQueue.value = list
        _currentPlayingChannel.value = channel
        viewModelScope.launch {
            repository.saveLastChannel(channel.playlistId, channel.id)
        }
    }

    /**
     * Переключает текущий канал на следующий (+1) или предыдущий (-1) в списке.
     */
    fun switchChannelRelative(delta: Int): ChannelEntity? {
        val queue = _playbackQueue.value.ifEmpty { allChannels.value }
        val current = _currentPlayingChannel.value ?: return null
        if (queue.isEmpty()) return null
        val currentIndex = queue.indexOfFirst { it.id == current.id }
        val nextIndex = if (currentIndex == -1) {
            0
        } else {
            (currentIndex + delta).mod(queue.size)
        }
        val nextChannel = queue[nextIndex]
        _currentPlayingChannel.value = nextChannel
        viewModelScope.launch {
            repository.saveLastChannel(nextChannel.playlistId, nextChannel.id)
        }
        return nextChannel
    }

    fun clearError() {
        _errorMessage.value = null
    }

    fun clearInfo() {
        _infoMessage.value = null
    }
}
