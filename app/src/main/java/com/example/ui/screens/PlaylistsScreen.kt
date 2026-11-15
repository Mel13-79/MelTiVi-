package com.example.ui.screens

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
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
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.PlaylistPlay
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.data.local.PlaylistEntity
import com.example.ui.AppUiMode
import com.example.ui.LocalAppUiMode
import com.example.ui.components.AddPlaylistDialog
import com.example.ui.components.DeletePlaylistDialog
import com.example.ui.components.PlaylistSettingsDialog
import com.example.ui.theme.CardSurface
import com.example.ui.theme.CardSurfaceElevated
import com.example.ui.theme.ErrorRed
import com.example.ui.theme.ObsidianBg
import com.example.ui.theme.OutlineSubtle
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import com.example.ui.theme.WarmOrange
import com.example.ui.theme.WarmOrangeSoft
import com.example.ui.util.RussianFormatters

/**
 * Экран управления плейлистами (сохранённые M3U-списки, добавление по URL, обновление и удаление).
 */
@Composable
fun PlaylistsScreen(
    playlists: List<PlaylistEntity>,
    activePlaylistId: Long?,
    isAddingPlaylist: Boolean,
    refreshingPlaylistId: Long?,
    errorMessage: String?,
    onAddPlaylist: (name: String, url: String, epgUrl: String?, onSuccess: () -> Unit) -> Unit,
    onSelectPlaylist: (Long) -> Unit,
    onRefreshPlaylist: (Long) -> Unit,
    onUpdatePlaylistSettings: (playlistId: Long, name: String, epgUrl: String?, loadEpgNow: Boolean, onSuccess: () -> Unit) -> Unit,
    onDeletePlaylist: (Long) -> Unit,
    onClearError: () -> Unit,
    modifier: Modifier = Modifier
) {
    val uiMode = LocalAppUiMode.current
    var showAddDialog by remember { mutableStateOf(false) }
    var playlistForSettings by remember { mutableStateOf<PlaylistEntity?>(null) }
    var playlistToDelete by remember { mutableStateOf<PlaylistEntity?>(null) }
    val addBtnFocusRequester = remember { FocusRequester() }

    LaunchedEffect(playlists.isEmpty(), uiMode) {
        if (uiMode == AppUiMode.TvUi && playlists.isEmpty()) {
            runCatching { addBtnFocusRequester.requestFocus() }
        }
    }

    Box(modifier = modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = if (uiMode == AppUiMode.TvUi) 32.dp else 16.dp)
        ) {
            Spacer(modifier = Modifier.height(if (uiMode == AppUiMode.TvUi) 24.dp else 12.dp))

            // Заголовок экрана и кнопка «Добавить»
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = stringResource(R.string.playlists_title),
                        style = if (uiMode == AppUiMode.TvUi) {
                            MaterialTheme.typography.headlineLarge
                        } else {
                            MaterialTheme.typography.headlineMedium
                        },
                        color = TextPrimary
                    )
                    if (playlists.isNotEmpty()) {
                        Text(
                            text = "${playlists.size} · ${
                                RussianFormatters.formatNumber(playlists.sumOf { it.channelCount })
                            } ${stringResource(R.string.playlists_channels_count, playlists.sumOf { it.channelCount }).substringAfter(" ")}",
                            style = MaterialTheme.typography.bodyMedium,
                            color = TextSecondary
                        )
                    }
                }

                Button(
                    onClick = {
                        onClearError()
                        showAddDialog = true
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = WarmOrange,
                        contentColor = ObsidianBg
                    ),
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier
                        .focusRequester(addBtnFocusRequester)
                        .minimumInteractiveComponentSize()
                        .testTag("add_playlist_button")
                ) {
                    Icon(
                        imageVector = Icons.Filled.Add,
                        contentDescription = null,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = stringResource(R.string.playlists_add_button),
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            if (playlists.isEmpty()) {
                // Пустое состояние
                EmptyPlaylistsView(
                    onAddClick = {
                        onClearError()
                        showAddDialog = true
                    },
                    focusRequester = if (uiMode == AppUiMode.TvUi) addBtnFocusRequester else null
                )
            } else {
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    contentPadding = PaddingValues(bottom = if (uiMode == AppUiMode.TvUi) 32.dp else 88.dp),
                    modifier = Modifier.fillMaxSize()
                ) {
                    itemsIndexed(
                        items = playlists,
                        key = { _, item -> item.id }
                    ) { _, playlist ->
                        PlaylistItemCard(
                            playlist = playlist,
                            isActive = playlist.id == activePlaylistId,
                            isRefreshing = refreshingPlaylistId == playlist.id,
                            uiMode = uiMode,
                            onSelect = { onSelectPlaylist(playlist.id) },
                            onRefresh = { onRefreshPlaylist(playlist.id) },
                            onSettings = { playlistForSettings = playlist },
                            onDelete = { playlistToDelete = playlist }
                        )
                    }
                }
            }
        }

        // Плавающая кнопка на телефоне для быстрого добавления при длинном списке
        if (uiMode == AppUiMode.PhoneUi && playlists.isNotEmpty()) {
            FloatingActionButton(
                onClick = {
                    onClearError()
                    showAddDialog = true
                },
                containerColor = WarmOrange,
                contentColor = ObsidianBg,
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(20.dp)
                    .testTag("fab_add_playlist")
            ) {
                Icon(
                    imageVector = Icons.Filled.Add,
                    contentDescription = stringResource(R.string.playlists_add_button)
                )
            }
        }
    }

    if (showAddDialog) {
        AddPlaylistDialog(
            isLoading = isAddingPlaylist,
            errorMessage = errorMessage,
            onDismiss = {
                onClearError()
                showAddDialog = false
            },
            onConfirm = { name, url, epgUrl ->
                onAddPlaylist(name, url, epgUrl) {
                    showAddDialog = false
                }
            }
        )
    }

    playlistForSettings?.let { target ->
        PlaylistSettingsDialog(
            playlist = target,
            onDismiss = { playlistForSettings = null },
            onSave = { newName, newEpgUrl, loadEpgNow ->
                onUpdatePlaylistSettings(target.id, newName, newEpgUrl, loadEpgNow) {
                    playlistForSettings = null
                }
            }
        )
    }

    playlistToDelete?.let { target ->
        DeletePlaylistDialog(
            playlist = target,
            onDismiss = { playlistToDelete = null },
            onConfirm = {
                onDeletePlaylist(target.id)
                playlistToDelete = null
            }
        )
    }
}

@Composable
private fun PlaylistItemCard(
    playlist: PlaylistEntity,
    isActive: Boolean,
    isRefreshing: Boolean,
    uiMode: AppUiMode,
    onSelect: () -> Unit,
    onRefresh: () -> Unit,
    onSettings: () -> Unit,
    onDelete: () -> Unit
) {
    var isCardFocused by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(
        targetValue = if (uiMode == AppUiMode.TvUi && isCardFocused) 1.015f else 1f,
        label = "playlist_card_scale"
    )
    val borderColor by animateColorAsState(
        targetValue = when {
            isCardFocused -> WarmOrange
            isActive -> WarmOrange.copy(alpha = 0.65f)
            else -> OutlineSubtle
        },
        label = "playlist_card_border"
    )

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .onFocusChanged { isCardFocused = it.isFocused || it.hasFocus }
            .testTag("playlist_card_${playlist.id}"),
        shape = RoundedCornerShape(18.dp),
        color = if (isActive) CardSurfaceElevated else CardSurface,
        border = BorderStroke(if (isCardFocused || isActive) 1.5.dp else 1.dp, borderColor),
        tonalElevation = if (isActive) 4.dp else 1.dp
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f)
                ) {
                    Box(
                        modifier = Modifier
                            .size(44.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(if (isActive) WarmOrangeSoft else ObsidianBg),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = if (isActive) Icons.Filled.CheckCircle else Icons.Filled.PlaylistPlay,
                            contentDescription = null,
                            tint = if (isActive) WarmOrange else TextSecondary,
                            modifier = Modifier.size(24.dp)
                        )
                    }

                    Spacer(modifier = Modifier.width(12.dp))

                    Column(modifier = Modifier.weight(1f)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(
                                text = playlist.name,
                                style = MaterialTheme.typography.titleMedium.copy(
                                    fontWeight = FontWeight.Bold,
                                    color = TextPrimary
                                ),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            if (isActive) {
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(6.dp))
                                        .background(WarmOrangeSoft)
                                        .padding(horizontal = 7.dp, vertical = 2.dp)
                                ) {
                                    Text(
                                        text = stringResource(R.string.playlists_active_badge),
                                        style = MaterialTheme.typography.labelSmall.copy(
                                            color = WarmOrange,
                                            fontWeight = FontWeight.SemiBold
                                        )
                                    )
                                }
                            }
                        }
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = playlist.url,
                            style = MaterialTheme.typography.bodyMedium,
                            color = TextMuted,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .padding(end = 8.dp)
                ) {
                    Text(
                        text = stringResource(
                            R.string.playlists_channels_count,
                            playlist.channelCount
                        ),
                        style = MaterialTheme.typography.labelLarge.copy(color = WarmOrange),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = stringResource(
                            R.string.playlists_updated_at,
                            RussianFormatters.formatDateTime(playlist.updatedAt)
                        ),
                        style = MaterialTheme.typography.labelSmall,
                        color = TextSecondary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    val epgLabel = when {
                        playlist.epgUpdatedAt != null -> stringResource(
                            R.string.playlists_epg_updated_at,
                            RussianFormatters.formatDateTime(playlist.epgUpdatedAt)
                        )
                        !playlist.epgUrl.isNullOrBlank() -> stringResource(R.string.playlists_epg_configured)
                        else -> stringResource(R.string.playlists_epg_not_configured)
                    }
                    Text(
                        text = epgLabel,
                        style = MaterialTheme.typography.labelSmall,
                        color = if (!playlist.epgUrl.isNullOrBlank()) WarmOrange else TextMuted,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Кнопка «Открыть» для быстрого перехода к каналам плейлиста
                    Button(
                        onClick = onSelect,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (isActive) CardSurface else WarmOrange,
                            contentColor = if (isActive) WarmOrange else ObsidianBg
                        ),
                        shape = RoundedCornerShape(10.dp),
                        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp),
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .testTag("select_playlist_${playlist.id}")
                    ) {
                        Icon(
                            imageVector = Icons.Filled.PlayArrow,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(stringResource(R.string.playlists_select_action))
                    }

                    // Кнопка «Обновить» (перезапрос по URL)
                    if (uiMode == AppUiMode.TvUi) {
                        OutlinedButton(
                            onClick = onRefresh,
                            enabled = !isRefreshing,
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier
                                .minimumInteractiveComponentSize()
                                .testTag("refresh_playlist_${playlist.id}")
                        ) {
                            if (isRefreshing) {
                                CircularProgressIndicator(
                                    color = WarmOrange,
                                    strokeWidth = 2.dp,
                                    modifier = Modifier.size(18.dp)
                                )
                            } else {
                                Icon(
                                    imageVector = Icons.Filled.Refresh,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(stringResource(R.string.playlists_refresh_action))
                        }

                        OutlinedButton(
                            onClick = onSettings,
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier
                                .minimumInteractiveComponentSize()
                                .testTag("settings_playlist_${playlist.id}")
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Settings,
                                contentDescription = stringResource(R.string.playlists_settings_action),
                                modifier = Modifier.size(18.dp)
                            )
                        }

                        OutlinedButton(
                            onClick = onDelete,
                            shape = RoundedCornerShape(10.dp),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = ErrorRed),
                            modifier = Modifier
                                .minimumInteractiveComponentSize()
                                .testTag("delete_playlist_${playlist.id}")
                        ) {
                            Icon(
                                imageVector = Icons.Filled.DeleteOutline,
                                contentDescription = stringResource(R.string.playlists_delete_action),
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    } else {
                        IconButton(
                            onClick = onSettings,
                            modifier = Modifier
                                .minimumInteractiveComponentSize()
                                .testTag("settings_playlist_${playlist.id}")
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Settings,
                                contentDescription = stringResource(R.string.playlists_settings_action),
                                tint = WarmOrange
                            )
                        }

                        IconButton(
                            onClick = onRefresh,
                            enabled = !isRefreshing,
                            modifier = Modifier
                                .minimumInteractiveComponentSize()
                                .testTag("refresh_playlist_${playlist.id}")
                        ) {
                            if (isRefreshing) {
                                CircularProgressIndicator(
                                    color = WarmOrange,
                                    strokeWidth = 2.dp,
                                    modifier = Modifier.size(20.dp)
                                )
                            } else {
                                Icon(
                                    imageVector = Icons.Filled.Refresh,
                                    contentDescription = stringResource(R.string.playlists_refresh_action),
                                    tint = TextPrimary
                                )
                            }
                        }

                        IconButton(
                            onClick = onDelete,
                            modifier = Modifier
                                .minimumInteractiveComponentSize()
                                .testTag("delete_playlist_${playlist.id}")
                        ) {
                            Icon(
                                imageVector = Icons.Filled.DeleteOutline,
                                contentDescription = stringResource(R.string.playlists_delete_action),
                                tint = ErrorRed
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun EmptyPlaylistsView(
    onAddClick: () -> Unit,
    focusRequester: FocusRequester?
) {
    Box(
        modifier = Modifier.fillMaxSize(),
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
                    .size(76.dp)
                    .clip(RoundedCornerShape(22.dp))
                    .background(WarmOrangeSoft),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Filled.PlaylistPlay,
                    contentDescription = null,
                    tint = WarmOrange,
                    modifier = Modifier.size(40.dp)
                )
            }
            Spacer(modifier = Modifier.height(18.dp))
            Text(
                text = stringResource(R.string.playlists_empty_title),
                style = MaterialTheme.typography.titleLarge,
                color = TextPrimary,
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = stringResource(R.string.playlists_empty_subtitle),
                style = MaterialTheme.typography.bodyLarge,
                color = TextSecondary,
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(22.dp))
            Button(
                onClick = onAddClick,
                colors = ButtonDefaults.buttonColors(
                    containerColor = WarmOrange,
                    contentColor = ObsidianBg
                ),
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier
                    .then(
                        if (focusRequester != null) Modifier.focusRequester(focusRequester)
                        else Modifier
                    )
                    .minimumInteractiveComponentSize()
                    .testTag("empty_add_playlist_button")
            ) {
                Icon(
                    imageVector = Icons.Filled.Add,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = stringResource(R.string.playlists_add_first_button),
                    fontWeight = FontWeight.SemiBold
                )
            }
        }
    }
}
