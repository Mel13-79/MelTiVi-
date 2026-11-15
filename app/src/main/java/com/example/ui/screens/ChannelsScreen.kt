package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.PlaylistPlay
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Tv
import androidx.compose.material.icons.filled.ViewStream
import androidx.compose.material.icons.outlined.StarOutline
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.data.local.ChannelEntity
import com.example.data.local.ChannelGroupEntity
import com.example.data.local.PlaylistEntity
import com.example.ui.AppUiMode
import com.example.ui.LocalAppUiMode
import com.example.ui.components.ChannelEpgCompactBlock
import com.example.ui.components.ChannelLogo
import com.example.ui.components.PhoneChannelCard
import com.example.ui.components.TvChannelCard
import com.example.ui.theme.CardSurface
import com.example.ui.theme.CardSurfaceElevated
import com.example.ui.theme.FavoriteGold
import com.example.ui.theme.ObsidianBg
import com.example.ui.theme.ObsidianSurface
import com.example.ui.theme.OutlineSubtle
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import com.example.ui.theme.WarmOrange
import com.example.ui.theme.WarmOrangeSoft
import com.example.ui.util.RussianFormatters
import com.example.ui.viewmodel.ChannelGroupSection
import com.example.ui.viewmodel.ChannelNowNext
import kotlinx.coroutines.delay

/**
 * Экран каналов и избранного с автоматической адаптацией:
 * — TvUi: Leanback-ряды по группам, верхний превью-блок, D-pad фокус и автофокус последнего канала.
 * — PhoneUi: поиск, вкладки/чипы групп, переключатель «Ряды / Сетка», виртуализованная сетка.
 */
@Composable
fun ChannelsScreen(
    activePlaylist: PlaylistEntity?,
    groups: List<ChannelGroupEntity>,
    filteredChannels: List<ChannelEntity>,
    groupedSections: List<ChannelGroupSection>,
    favoriteChannels: List<ChannelEntity>,
    channelNowNextMap: Map<Long, ChannelNowNext>,
    currentTimeMs: Long,
    lastWatchedChannel: ChannelEntity?,
    selectedGroupId: Long?,
    searchQuery: String,
    favoritesOnly: Boolean,
    showSearchBarInTv: Boolean,
    onSelectGroup: (Long?) -> Unit,
    onSearchQueryChange: (String) -> Unit,
    onChannelClick: (ChannelEntity, List<ChannelEntity>) -> Unit,
    onToggleFavorite: (ChannelEntity) -> Unit,
    onNavigateToPlaylists: () -> Unit,
    modifier: Modifier = Modifier
) {
    val uiMode = LocalAppUiMode.current

    if (activePlaylist == null) {
        EmptyChannelsState(
            title = stringResource(R.string.channels_empty_title),
            subtitle = stringResource(R.string.channels_empty_subtitle),
            buttonText = stringResource(R.string.playlists_title),
            onActionClick = onNavigateToPlaylists,
            modifier = modifier
        )
        return
    }

    if (favoritesOnly) {
        FavoritesContent(
            favorites = favoriteChannels,
            channelNowNextMap = channelNowNextMap,
            currentTimeMs = currentTimeMs,
            lastWatchedChannel = lastWatchedChannel,
            uiMode = uiMode,
            onChannelClick = { ch -> onChannelClick(ch, favoriteChannels) },
            onToggleFavorite = onToggleFavorite,
            modifier = modifier
        )
        return
    }

    if (uiMode == AppUiMode.TvUi) {
        TvChannelsContent(
            activePlaylist = activePlaylist,
            groupedSections = groupedSections,
            filteredChannels = filteredChannels,
            channelNowNextMap = channelNowNextMap,
            currentTimeMs = currentTimeMs,
            lastWatchedChannel = lastWatchedChannel,
            searchQuery = searchQuery,
            showSearchBar = showSearchBarInTv,
            onSearchQueryChange = onSearchQueryChange,
            onChannelClick = { ch -> onChannelClick(ch, filteredChannels) },
            onToggleFavorite = onToggleFavorite,
            modifier = modifier
        )
    } else {
        PhoneChannelsContent(
            activePlaylist = activePlaylist,
            groups = groups,
            filteredChannels = filteredChannels,
            groupedSections = groupedSections,
            channelNowNextMap = channelNowNextMap,
            currentTimeMs = currentTimeMs,
            lastWatchedChannel = lastWatchedChannel,
            selectedGroupId = selectedGroupId,
            searchQuery = searchQuery,
            onSelectGroup = onSelectGroup,
            onSearchQueryChange = onSearchQueryChange,
            onChannelClick = { ch -> onChannelClick(ch, filteredChannels) },
            onToggleFavorite = onToggleFavorite,
            onNavigateToPlaylists = onNavigateToPlaylists,
            modifier = modifier
        )
    }
}

/**
 * ТВ-режим (Android TV Leanback): горизонтальные ряды карточек по группам + блок предпросмотра.
 */
@Composable
private fun TvChannelsContent(
    activePlaylist: PlaylistEntity,
    groupedSections: List<ChannelGroupSection>,
    filteredChannels: List<ChannelEntity>,
    channelNowNextMap: Map<Long, ChannelNowNext>,
    currentTimeMs: Long,
    lastWatchedChannel: ChannelEntity?,
    searchQuery: String,
    showSearchBar: Boolean,
    onSearchQueryChange: (String) -> Unit,
    onChannelClick: (ChannelEntity) -> Unit,
    onToggleFavorite: (ChannelEntity) -> Unit,
    modifier: Modifier = Modifier
) {
    var focusedChannel by remember(activePlaylist.id) {
        mutableStateOf(lastWatchedChannel ?: filteredChannels.firstOrNull())
    }
    var currentTime by remember { mutableStateOf(RussianFormatters.formatShortTime()) }
    val columnListState = rememberLazyListState()
    val targetChannelFocusRequester = remember { FocusRequester() }
    val searchFocusRequester = remember { FocusRequester() }
    var initialScrollDone by rememberSaveable(activePlaylist.id) { mutableStateOf(false) }

    // Обновляем 24-часовые часы на ТВ-экране
    LaunchedEffect(Unit) {
        while (true) {
            currentTime = RussianFormatters.formatShortTime()
            delay(30_000L)
        }
    }

    // При первом открытии плейлиста переходим и фокусируемся на последнем открытом канале
    LaunchedEffect(groupedSections, lastWatchedChannel?.id, showSearchBar) {
        if (showSearchBar) {
            runCatching { searchFocusRequester.requestFocus() }
            return@LaunchedEffect
        }
        if (!initialScrollDone && groupedSections.isNotEmpty()) {
            val target = lastWatchedChannel
            if (target != null) {
                val sectionIndex = groupedSections.indexOfFirst { sec ->
                    sec.channels.any { it.id == target.id }
                }
                if (sectionIndex >= 0) {
                    columnListState.scrollToItem(sectionIndex)
                    focusedChannel = target
                }
            } else {
                focusedChannel = groupedSections.first().channels.firstOrNull()
            }
            initialScrollDone = true
            runCatching { targetChannelFocusRequester.requestFocus() }
        }
    }

    val spotlightChannel = focusedChannel ?: lastWatchedChannel ?: filteredChannels.firstOrNull()

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(top = 20.dp)
    ) {
        // Верхняя панель Leanback с информацией о выбранном канале и временем
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 32.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = activePlaylist.name,
                    style = MaterialTheme.typography.titleLarge.copy(color = WarmOrange)
                )
                Spacer(modifier = Modifier.width(12.dp))
                Text(
                    text = stringResource(R.string.playlists_channels_count, activePlaylist.channelCount),
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextSecondary
                )
            }

            Text(
                text = currentTime,
                style = MaterialTheme.typography.headlineMedium.copy(color = TextPrimary)
            )
        }

        Spacer(modifier = Modifier.height(12.dp))

        if (showSearchBar) {
            OutlinedTextField(
                value = searchQuery,
                onValueChange = onSearchQueryChange,
                singleLine = true,
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Filled.Search,
                        contentDescription = null,
                        tint = WarmOrange
                    )
                },
                trailingIcon = {
                    if (searchQuery.isNotEmpty()) {
                        IconButton(onClick = { onSearchQueryChange("") }) {
                            Icon(
                                imageVector = Icons.Filled.Close,
                                contentDescription = stringResource(R.string.channels_search_clear)
                            )
                        }
                    }
                },
                placeholder = { Text(stringResource(R.string.channels_search_hint)) },
                shape = RoundedCornerShape(14.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = WarmOrange,
                    unfocusedBorderColor = OutlineSubtle,
                    focusedContainerColor = CardSurface,
                    unfocusedContainerColor = CardSurface
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 32.dp)
                    .focusRequester(searchFocusRequester)
                    .testTag("tv_search_input")
            )
            Spacer(modifier = Modifier.height(12.dp))
        } else if (spotlightChannel != null) {
            // Карточка предпросмотра текущего канала в стиле Android TV
            TvSpotlightHeader(
                channel = spotlightChannel,
                nowNext = channelNowNextMap[spotlightChannel.id],
                currentTimeMs = currentTimeMs,
                isLastWatched = spotlightChannel.id == lastWatchedChannel?.id,
                onWatchClick = { onChannelClick(spotlightChannel) },
                onToggleFavorite = { onToggleFavorite(spotlightChannel) },
                modifier = Modifier.padding(horizontal = 32.dp)
            )
            Spacer(modifier = Modifier.height(14.dp))
        }

        if (groupedSections.isEmpty()) {
            EmptyChannelsState(
                title = if (searchQuery.isNotBlank()) {
                    stringResource(R.string.channels_search_empty, searchQuery)
                } else {
                    stringResource(R.string.channels_empty_title)
                },
                subtitle = "",
                buttonText = if (searchQuery.isNotBlank()) stringResource(R.string.channels_search_clear) else null,
                onActionClick = { onSearchQueryChange("") }
            )
        } else {
            val targetId = lastWatchedChannel?.id ?: groupedSections.first().channels.firstOrNull()?.id

            LazyColumn(
                state = columnListState,
                contentPadding = PaddingValues(bottom = 32.dp),
                verticalArrangement = Arrangement.spacedBy(20.dp),
                modifier = Modifier.fillMaxSize()
            ) {
                items(
                    items = groupedSections,
                    key = { it.groupId }
                ) { section ->
                    TvChannelGroupRow(
                        section = section,
                        channelNowNextMap = channelNowNextMap,
                        currentTimeMs = currentTimeMs,
                        lastWatchedChannelId = lastWatchedChannel?.id,
                        focusTargetChannelId = targetId,
                        targetFocusRequester = targetChannelFocusRequester,
                        onChannelClick = onChannelClick,
                        onToggleFavorite = onToggleFavorite,
                        onChannelFocused = { focusedChannel = it }
                    )
                }
            }
        }
    }
}

@Composable
private fun TvSpotlightHeader(
    channel: ChannelEntity,
    nowNext: ChannelNowNext?,
    currentTimeMs: Long,
    isLastWatched: Boolean,
    onWatchClick: () -> Unit,
    onToggleFavorite: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        color = CardSurface,
        border = BorderStroke(1.dp, OutlineSubtle)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    Brush.horizontalGradient(
                        colors = listOf(
                            WarmOrangeSoft.copy(alpha = 0.25f),
                            CardSurface,
                            ObsidianSurface
                        )
                    )
                )
                .padding(16.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f)
                ) {
                    ChannelLogo(
                        channelName = channel.name,
                        logoUrl = channel.logoUrl,
                        modifier = Modifier.size(68.dp)
                    )
                    Spacer(modifier = Modifier.width(16.dp))
                    Column {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(
                                text = channel.name,
                                style = MaterialTheme.typography.headlineMedium,
                                color = TextPrimary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            if (isLastWatched) {
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(6.dp))
                                        .background(WarmOrange)
                                        .padding(horizontal = 8.dp, vertical = 2.dp)
                                ) {
                                    Text(
                                        text = stringResource(R.string.channels_last_watched_badge),
                                        style = MaterialTheme.typography.labelSmall.copy(
                                            color = ObsidianBg,
                                            fontWeight = FontWeight.Bold
                                        )
                                    )
                                }
                            }
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        if (nowNext?.current != null || nowNext?.next != null) {
                            ChannelEpgCompactBlock(
                                nowNext = nowNext,
                                currentTimeMs = currentTimeMs,
                                modifier = Modifier.fillMaxWidth(0.9f)
                            )
                        } else {
                            Text(
                                text = "${channel.groupName} · ${stringResource(R.string.channels_tv_ok_hint)}",
                                style = MaterialTheme.typography.bodyMedium,
                                color = TextSecondary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.width(16.dp))

                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedButton(
                        onClick = onToggleFavorite,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.minimumInteractiveComponentSize()
                    ) {
                        Icon(
                            imageVector = if (channel.isFavorite) Icons.Filled.Star else Icons.Outlined.StarOutline,
                            contentDescription = stringResource(
                                if (channel.isFavorite) R.string.channels_remove_favorite
                                else R.string.channels_add_favorite
                            ),
                            tint = if (channel.isFavorite) FavoriteGold else TextPrimary
                        )
                    }
                    Button(
                        onClick = onWatchClick,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = WarmOrange,
                            contentColor = ObsidianBg
                        ),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.minimumInteractiveComponentSize()
                    ) {
                        Icon(
                            imageVector = Icons.Filled.PlayArrow,
                            contentDescription = null,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = stringResource(R.string.channels_watch_action),
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun TvChannelGroupRow(
    section: ChannelGroupSection,
    channelNowNextMap: Map<Long, ChannelNowNext>,
    currentTimeMs: Long,
    lastWatchedChannelId: Long?,
    focusTargetChannelId: Long?,
    targetFocusRequester: FocusRequester,
    onChannelClick: (ChannelEntity) -> Unit,
    onToggleFavorite: (ChannelEntity) -> Unit,
    onChannelFocused: (ChannelEntity) -> Unit
) {
    val rowState = rememberLazyListState()

    // Прокручиваем горизонтальный ряд к последнему просмотренному каналу при старте
    LaunchedEffect(section.groupId, lastWatchedChannelId) {
        if (lastWatchedChannelId != null) {
            val idx = section.channels.indexOfFirst { it.id == lastWatchedChannelId }
            if (idx > 0) {
                rowState.scrollToItem(idx)
            }
        }
    }

    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 32.dp)
        ) {
            Text(
                text = section.groupName,
                style = MaterialTheme.typography.titleLarge,
                color = TextPrimary
            )
            Spacer(modifier = Modifier.width(10.dp))
            Text(
                text = RussianFormatters.formatNumber(section.channels.size),
                style = MaterialTheme.typography.labelMedium,
                color = WarmOrange
            )
        }

        Spacer(modifier = Modifier.height(10.dp))

        LazyRow(
            state = rowState,
            contentPadding = PaddingValues(horizontal = 32.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            items(
                items = section.channels,
                key = { it.id }
            ) { channel ->
                TvChannelCard(
                    channel = channel,
                    isLastWatched = channel.id == lastWatchedChannelId,
                    nowNext = channelNowNextMap[channel.id],
                    currentTimeMs = currentTimeMs,
                    onClick = { onChannelClick(channel) },
                    onToggleFavorite = { onToggleFavorite(channel) },
                    onFocused = { onChannelFocused(channel) },
                    focusRequester = if (channel.id == focusTargetChannelId) targetFocusRequester else null
                )
            }
        }
    }
}

/**
 * Телефонный тач-режим: строка поиска, чипы групп, быстрый запуск последнего канала и сетка/ряды каналов.
 */
@Composable
private fun PhoneChannelsContent(
    activePlaylist: PlaylistEntity,
    groups: List<ChannelGroupEntity>,
    filteredChannels: List<ChannelEntity>,
    groupedSections: List<ChannelGroupSection>,
    channelNowNextMap: Map<Long, ChannelNowNext>,
    currentTimeMs: Long,
    lastWatchedChannel: ChannelEntity?,
    selectedGroupId: Long?,
    searchQuery: String,
    onSelectGroup: (Long?) -> Unit,
    onSearchQueryChange: (String) -> Unit,
    onChannelClick: (ChannelEntity) -> Unit,
    onToggleFavorite: (ChannelEntity) -> Unit,
    onNavigateToPlaylists: () -> Unit,
    modifier: Modifier = Modifier
) {
    val focusManager = LocalFocusManager.current
    var showRowsMode by rememberSaveable { mutableStateOf(false) }
    val gridState = rememberLazyGridState()
    val listState = rememberLazyListState()
    var hasScrolledToLast by rememberSaveable(activePlaylist.id) { mutableStateOf(false) }

    // При старте на сохранённом плейлисте прокручиваем к последнему открытому каналу
    LaunchedEffect(filteredChannels, lastWatchedChannel?.id, showRowsMode) {
        if (!hasScrolledToLast && lastWatchedChannel != null) {
            if (!showRowsMode) {
                val idx = filteredChannels.indexOfFirst { it.id == lastWatchedChannel.id }
                if (idx >= 0) {
                    gridState.scrollToItem(idx)
                    hasScrolledToLast = true
                }
            } else {
                val secIdx = groupedSections.indexOfFirst { sec ->
                    sec.channels.any { it.id == lastWatchedChannel.id }
                }
                if (secIdx >= 0) {
                    listState.scrollToItem(secIdx)
                    hasScrolledToLast = true
                }
            }
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(top = 8.dp)
    ) {
        // Шапка с названием активного плейлиста и переключателем вида (Сетка / Ряды)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(R.string.channels_title),
                    style = MaterialTheme.typography.headlineMedium,
                    color = TextPrimary
                )
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .clickable(onClick = onNavigateToPlaylists)
                        .padding(vertical = 2.dp)
                ) {
                    Icon(
                        imageVector = Icons.Filled.PlaylistPlay,
                        contentDescription = null,
                        tint = WarmOrange,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "${activePlaylist.name} · ${
                            stringResource(R.string.playlists_channels_count, activePlaylist.channelCount)
                        }",
                        style = MaterialTheme.typography.bodyMedium,
                        color = TextSecondary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            if (selectedGroupId == null && searchQuery.isBlank()) {
                IconButton(
                    onClick = { showRowsMode = !showRowsMode },
                    modifier = Modifier
                        .minimumInteractiveComponentSize()
                        .testTag("toggle_channels_view_mode")
                ) {
                    Icon(
                        imageVector = if (showRowsMode) Icons.Filled.GridView else Icons.Filled.ViewStream,
                        contentDescription = stringResource(
                            if (showRowsMode) R.string.channels_view_grid else R.string.channels_view_rows
                        ),
                        tint = WarmOrange
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        // Поле поиска по названию канала
        OutlinedTextField(
            value = searchQuery,
            onValueChange = onSearchQueryChange,
            singleLine = true,
            leadingIcon = {
                Icon(
                    imageVector = Icons.Filled.Search,
                    contentDescription = null,
                    tint = WarmOrange
                )
            },
            trailingIcon = {
                if (searchQuery.isNotEmpty()) {
                    IconButton(onClick = { onSearchQueryChange("") }) {
                        Icon(
                            imageVector = Icons.Filled.Close,
                            contentDescription = stringResource(R.string.channels_search_clear),
                            tint = TextSecondary
                        )
                    }
                }
            },
            placeholder = { Text(stringResource(R.string.channels_search_hint)) },
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(onSearch = { focusManager.clearFocus() }),
            shape = RoundedCornerShape(14.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = WarmOrange,
                unfocusedBorderColor = OutlineSubtle,
                focusedContainerColor = CardSurface,
                unfocusedContainerColor = CardSurface
            ),
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .testTag("phone_search_input")
        )

        Spacer(modifier = Modifier.height(10.dp))

        // Вкладки/чипы групп
        if (groups.isNotEmpty()) {
            LazyRow(
                contentPadding = PaddingValues(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                item(key = "all_groups") {
                    FilterChip(
                        selected = selectedGroupId == null,
                        onClick = { onSelectGroup(null) },
                        label = { Text(stringResource(R.string.channels_all_groups)) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = WarmOrange,
                            selectedLabelColor = ObsidianBg,
                            containerColor = CardSurface,
                            labelColor = TextSecondary
                        ),
                        border = FilterChipDefaults.filterChipBorder(
                            enabled = true,
                            selected = selectedGroupId == null,
                            borderColor = OutlineSubtle
                        ),
                        modifier = Modifier.testTag("group_chip_all")
                    )
                }
                items(
                    items = groups,
                    key = { it.id }
                ) { group ->
                    val isSelected = selectedGroupId == group.id
                    FilterChip(
                        selected = isSelected,
                        onClick = { onSelectGroup(group.id) },
                        label = {
                            Text("${group.name} (${RussianFormatters.formatNumber(group.channelCount)})")
                        },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = WarmOrange,
                            selectedLabelColor = ObsidianBg,
                            containerColor = CardSurface,
                            labelColor = TextSecondary
                        ),
                        border = FilterChipDefaults.filterChipBorder(
                            enabled = true,
                            selected = isSelected,
                            borderColor = OutlineSubtle
                        ),
                        modifier = Modifier.testTag("group_chip_${group.id}")
                    )
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
        }

        // Плашка «Продолжить просмотр» для последнего открытого канала
        if (lastWatchedChannel != null && searchQuery.isBlank()) {
            Surface(
                onClick = { onChannelClick(lastWatchedChannel) },
                shape = RoundedCornerShape(14.dp),
                color = CardSurfaceElevated,
                border = BorderStroke(1.dp, WarmOrange.copy(alpha = 0.5f)),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp)
                    .testTag("continue_watching_banner")
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.weight(1f)
                    ) {
                        ChannelLogo(
                            channelName = lastWatchedChannel.name,
                            logoUrl = lastWatchedChannel.logoUrl,
                            cornerRadius = 8.dp,
                            contentPadding = 4.dp,
                            modifier = Modifier.size(38.dp)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text(
                                text = stringResource(R.string.channels_continue_watching, lastWatchedChannel.name),
                                style = MaterialTheme.typography.titleMedium.copy(
                                    color = TextPrimary,
                                    fontWeight = FontWeight.SemiBold
                                ),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            val lastNowNext = channelNowNextMap[lastWatchedChannel.id]
                            if (lastNowNext?.current != null) {
                                Text(
                                    text = stringResource(R.string.channels_now_playing, lastNowNext.current.title),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = WarmOrange,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            } else {
                                Text(
                                    text = lastWatchedChannel.groupName,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = WarmOrange
                                )
                            }
                        }
                    }
                    Icon(
                        imageVector = Icons.Filled.PlayArrow,
                        contentDescription = stringResource(R.string.channels_watch_action),
                        tint = WarmOrange,
                        modifier = Modifier.size(26.dp)
                    )
                }
            }
            Spacer(modifier = Modifier.height(6.dp))
        }

        if (filteredChannels.isEmpty()) {
            EmptyChannelsState(
                title = if (searchQuery.isNotBlank()) {
                    stringResource(R.string.channels_search_empty, searchQuery)
                } else {
                    stringResource(R.string.channels_empty_title)
                },
                subtitle = "",
                buttonText = if (searchQuery.isNotBlank()) stringResource(R.string.channels_search_clear) else null,
                onActionClick = { onSearchQueryChange("") }
            )
        } else if (showRowsMode && selectedGroupId == null && searchQuery.isBlank()) {
            // Режим горизонтальных рядов по группам на телефоне
            LazyColumn(
                state = listState,
                contentPadding = PaddingValues(bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(18.dp),
                modifier = Modifier.fillMaxSize()
            ) {
                items(
                    items = groupedSections,
                    key = { it.groupId }
                ) { section ->
                    Column(modifier = Modifier.fillMaxWidth()) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(horizontal = 16.dp)
                        ) {
                            Text(
                                text = section.groupName,
                                style = MaterialTheme.typography.titleMedium.copy(
                                    fontWeight = FontWeight.Bold,
                                    color = TextPrimary
                                )
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = RussianFormatters.formatNumber(section.channels.size),
                                style = MaterialTheme.typography.labelMedium,
                                color = WarmOrange
                            )
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        LazyRow(
                            contentPadding = PaddingValues(horizontal = 16.dp),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            items(
                                items = section.channels,
                                key = { it.id }
                            ) { channel ->
                                PhoneChannelCard(
                                    channel = channel,
                                    isLastWatched = channel.id == lastWatchedChannel?.id,
                                    nowNext = channelNowNextMap[channel.id],
                                    currentTimeMs = currentTimeMs,
                                    onClick = { onChannelClick(channel) },
                                    onToggleFavorite = { onToggleFavorite(channel) },
                                    modifier = Modifier.width(164.dp)
                                )
                            }
                        }
                    }
                }
            }
        } else {
            // Виртуализованная сетка каналов (рассчитана на тысячи позиций)
            LazyVerticalGrid(
                state = gridState,
                columns = GridCells.Adaptive(minSize = 154.dp),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 24.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.fillMaxSize()
            ) {
                items(
                    items = filteredChannels,
                    key = { it.id }
                ) { channel ->
                    PhoneChannelCard(
                        channel = channel,
                        isLastWatched = channel.id == lastWatchedChannel?.id,
                        nowNext = channelNowNextMap[channel.id],
                        currentTimeMs = currentTimeMs,
                        onClick = { onChannelClick(channel) },
                        onToggleFavorite = { onToggleFavorite(channel) }
                    )
                }
            }
        }
    }
}

@Composable
private fun FavoritesContent(
    favorites: List<ChannelEntity>,
    channelNowNextMap: Map<Long, ChannelNowNext>,
    currentTimeMs: Long,
    lastWatchedChannel: ChannelEntity?,
    uiMode: AppUiMode,
    onChannelClick: (ChannelEntity) -> Unit,
    onToggleFavorite: (ChannelEntity) -> Unit,
    modifier: Modifier = Modifier
) {
    val firstFocusRequester = remember { FocusRequester() }

    LaunchedEffect(uiMode, favorites.isNotEmpty()) {
        if (uiMode == AppUiMode.TvUi && favorites.isNotEmpty()) {
            runCatching { firstFocusRequester.requestFocus() }
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(top = if (uiMode == AppUiMode.TvUi) 20.dp else 8.dp)
    ) {
        Text(
            text = stringResource(R.string.channels_favorites_title),
            style = if (uiMode == AppUiMode.TvUi) {
                MaterialTheme.typography.headlineLarge
            } else {
                MaterialTheme.typography.headlineMedium
            },
            color = TextPrimary,
            modifier = Modifier.padding(horizontal = if (uiMode == AppUiMode.TvUi) 32.dp else 16.dp)
        )
        Spacer(modifier = Modifier.height(14.dp))

        if (favorites.isEmpty()) {
            EmptyChannelsState(
                title = stringResource(R.string.channels_favorites_empty_title),
                subtitle = stringResource(R.string.channels_favorites_empty_subtitle),
                buttonText = null,
                onActionClick = {},
                iconIsStar = true
            )
        } else if (uiMode == AppUiMode.TvUi) {
            LazyVerticalGrid(
                columns = GridCells.Adaptive(minSize = 220.dp),
                contentPadding = PaddingValues(horizontal = 32.dp, vertical = 10.dp),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
                modifier = Modifier.fillMaxSize()
            ) {
                itemsIndexed(
                    items = favorites,
                    key = { _, item -> item.id }
                ) { index, channel ->
                    TvChannelCard(
                        channel = channel,
                        isLastWatched = channel.id == lastWatchedChannel?.id,
                        nowNext = channelNowNextMap[channel.id],
                        currentTimeMs = currentTimeMs,
                        onClick = { onChannelClick(channel) },
                        onToggleFavorite = { onToggleFavorite(channel) },
                        onFocused = {},
                        focusRequester = if (index == 0) firstFocusRequester else null
                    )
                }
            }
        } else {
            LazyVerticalGrid(
                columns = GridCells.Adaptive(minSize = 154.dp),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.fillMaxSize()
            ) {
                items(
                    items = favorites,
                    key = { it.id }
                ) { channel ->
                    PhoneChannelCard(
                        channel = channel,
                        isLastWatched = channel.id == lastWatchedChannel?.id,
                        nowNext = channelNowNextMap[channel.id],
                        currentTimeMs = currentTimeMs,
                        onClick = { onChannelClick(channel) },
                        onToggleFavorite = { onToggleFavorite(channel) }
                    )
                }
            }
        }
    }
}

@Composable
private fun EmptyChannelsState(
    title: String,
    subtitle: String,
    buttonText: String?,
    onActionClick: () -> Unit,
    modifier: Modifier = Modifier,
    iconIsStar: Boolean = false
) {
    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .padding(24.dp)
                .fillMaxWidth(0.85f)
        ) {
            Box(
                modifier = Modifier
                    .size(72.dp)
                    .clip(RoundedCornerShape(20.dp))
                    .background(WarmOrangeSoft),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = if (iconIsStar) Icons.Filled.Star else Icons.Filled.Tv,
                    contentDescription = null,
                    tint = if (iconIsStar) FavoriteGold else WarmOrange,
                    modifier = Modifier.size(36.dp)
                )
            }
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = title,
                style = MaterialTheme.typography.titleLarge,
                color = TextPrimary,
                textAlign = TextAlign.Center
            )
            if (subtitle.isNotBlank()) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodyLarge,
                    color = TextSecondary,
                    textAlign = TextAlign.Center
                )
            }
            if (buttonText != null) {
                Spacer(modifier = Modifier.height(20.dp))
                Button(
                    onClick = onActionClick,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = WarmOrange,
                        contentColor = ObsidianBg
                    ),
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.minimumInteractiveComponentSize()
                ) {
                    Text(buttonText, fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }
}
