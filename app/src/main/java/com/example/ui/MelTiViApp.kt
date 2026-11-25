package com.example.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.PlaylistPlay
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Tv
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.PlaylistPlay
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.StarOutline
import androidx.compose.material.icons.outlined.Tv
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.NavigationRailItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.example.R
import com.example.data.local.ChannelEntity
import com.example.data.local.PlaylistEntity
import com.example.ui.components.PlaylistSettingsDialog
import com.example.ui.screens.ChannelsScreen
import com.example.ui.screens.PlayerScreen
import com.example.ui.screens.PlaylistsScreen
import com.example.ui.screens.SplashScreen
import com.example.ui.screens.TvGuideScreen
import com.example.ui.theme.CardSurface
import com.example.ui.theme.DisplayFontFamily
import com.example.ui.theme.ObsidianBg
import com.example.ui.theme.ObsidianSurface
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import com.example.ui.theme.WarmOrange
import com.example.ui.theme.WarmOrangeSoft
import com.example.ui.viewmodel.MelTiViViewModel
import kotlinx.serialization.Serializable

@Serializable
private object SplashRoute

@Serializable
private object MainRoute

@Serializable
private object PlayerRoute

private enum class MainTab(
    val labelRes: Int,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector,
    val testTag: String
) {
    CHANNELS(R.string.nav_channels, Icons.Filled.Tv, Icons.Outlined.Tv, "nav_channels"),
    GUIDE(R.string.nav_guide, Icons.Filled.CalendarMonth, Icons.Outlined.CalendarMonth, "nav_guide"),
    FAVORITES(R.string.nav_favorites, Icons.Filled.Star, Icons.Outlined.StarOutline, "nav_favorites"),
    SEARCH(R.string.nav_search, Icons.Filled.Search, Icons.Outlined.Search, "nav_search"),
    PLAYLISTS(R.string.nav_playlists, Icons.Filled.PlaylistPlay, Icons.Outlined.PlaylistPlay, "nav_playlists")
}

/**
 * Корневой навигационный контейнер MelTiVi, объединяющий общий ViewModel,
 * вкладку «Телегид» и два режима UI (TvUi / PhoneUi).
 */
@Composable
fun MelTiViApp(
    uiMode: AppUiMode,
    viewModel: MelTiViViewModel = viewModel()
) {
    val navController = rememberNavController()
    val playlists by viewModel.playlists.collectAsStateWithLifecycle()
    val activePlaylist by viewModel.activePlaylist.collectAsStateWithLifecycle()
    val groups by viewModel.groups.collectAsStateWithLifecycle()
    val allChannels by viewModel.allChannels.collectAsStateWithLifecycle()
    val filteredChannels by viewModel.filteredChannels.collectAsStateWithLifecycle()
    val groupedSections by viewModel.groupedSections.collectAsStateWithLifecycle()
    val favoriteChannels by viewModel.favoriteChannels.collectAsStateWithLifecycle()
    val epgProgramsByChannel by viewModel.epgProgramsByChannel.collectAsStateWithLifecycle()
    val channelNowNextMap by viewModel.channelNowNextMap.collectAsStateWithLifecycle()
    val currentTimeMs by viewModel.currentTimeMs.collectAsStateWithLifecycle()
    val lastWatchedChannel by viewModel.lastWatchedChannel.collectAsStateWithLifecycle()
    val selectedGroupId by viewModel.selectedGroupId.collectAsStateWithLifecycle()
    val searchQuery by viewModel.searchQuery.collectAsStateWithLifecycle()
    val isAddingPlaylist by viewModel.isAddingPlaylist.collectAsStateWithLifecycle()
    val refreshingPlaylistId by viewModel.refreshingPlaylistId.collectAsStateWithLifecycle()
    val isRefreshingEpg by viewModel.isRefreshingEpg.collectAsStateWithLifecycle()
    val epgLoadProgress by viewModel.epgLoadProgress.collectAsStateWithLifecycle()
    val errorMessage by viewModel.errorMessage.collectAsStateWithLifecycle()
    val infoMessage by viewModel.infoMessage.collectAsStateWithLifecycle()
    val currentPlayingChannel by viewModel.currentPlayingChannel.collectAsStateWithLifecycle()

    CompositionLocalProvider(LocalAppUiMode provides uiMode) {
        NavHost(
            navController = navController,
            startDestination = SplashRoute
        ) {
            composable<SplashRoute> {
                SplashScreen(
                    onFinished = {
                        navController.navigate(MainRoute) {
                            popUpTo(SplashRoute) { inclusive = true }
                        }
                    }
                )
            }

            composable<MainRoute> {
                val snackbarHostState = remember { SnackbarHostState() }
                var selectedTab by rememberSaveable { mutableStateOf(MainTab.CHANNELS) }
                var initialTabResolved by rememberSaveable { mutableStateOf(false) }
                var settingsTargetPlaylist by remember { mutableStateOf<PlaylistEntity?>(null) }

                // Если сохранённых плейлистов ещё нет, открываем сразу вкладку «Плейлисты»
                LaunchedEffect(playlists) {
                    if (!initialTabResolved) {
                        if (playlists.isEmpty()) {
                            selectedTab = MainTab.PLAYLISTS
                        } else {
                            selectedTab = MainTab.CHANNELS
                            initialTabResolved = true
                        }
                    }
                }

                // Показываем уведомления и ошибки загрузки
                LaunchedEffect(infoMessage) {
                    infoMessage?.let { msg ->
                        snackbarHostState.showSnackbar(msg)
                        viewModel.clearInfo()
                    }
                }
                LaunchedEffect(errorMessage, selectedTab) {
                    if (errorMessage != null && !isAddingPlaylist) {
                        snackbarHostState.showSnackbar(errorMessage!!)
                        viewModel.clearError()
                    }
                }

                // Нажатие Back на второстепенных вкладках возвращает на «Каналы»
                BackHandler(enabled = selectedTab != MainTab.CHANNELS && playlists.isNotEmpty()) {
                    if (selectedTab == MainTab.SEARCH) {
                        viewModel.updateSearchQuery("")
                    }
                    selectedTab = MainTab.CHANNELS
                }

                val openChannelInPlayer: (ChannelEntity, List<ChannelEntity>) -> Unit = { ch, queue ->
                    viewModel.openChannel(ch, queue)
                    navController.navigate(PlayerRoute)
                }

                val renderMainContent: @Composable () -> Unit = {
                    when (selectedTab) {
                        MainTab.CHANNELS, MainTab.FAVORITES, MainTab.SEARCH -> {
                            ChannelsScreen(
                                activePlaylist = activePlaylist,
                                groups = groups,
                                filteredChannels = filteredChannels,
                                groupedSections = groupedSections,
                                favoriteChannels = favoriteChannels,
                                channelNowNextMap = channelNowNextMap,
                                currentTimeMs = currentTimeMs,
                                lastWatchedChannel = lastWatchedChannel,
                                selectedGroupId = selectedGroupId,
                                searchQuery = searchQuery,
                                favoritesOnly = selectedTab == MainTab.FAVORITES,
                                showSearchBarInTv = selectedTab == MainTab.SEARCH,
                                onSelectGroup = viewModel::selectGroup,
                                onSearchQueryChange = viewModel::updateSearchQuery,
                                onChannelClick = openChannelInPlayer,
                                onToggleFavorite = viewModel::toggleFavorite,
                                onNavigateToPlaylists = { selectedTab = MainTab.PLAYLISTS }
                            )
                        }
                        MainTab.GUIDE -> {
                            TvGuideScreen(
                                activePlaylist = activePlaylist,
                                channels = allChannels,
                                epgProgramsByChannel = epgProgramsByChannel,
                                currentTimeMs = currentTimeMs,
                                isRefreshingEpg = isRefreshingEpg,
                                epgLoadProgress = epgLoadProgress,
                                epgUpdatedAt = activePlaylist?.epgUpdatedAt,
                                onRefreshEpg = { viewModel.refreshEpg() },
                                onOpenPlaylistSettings = { playlist ->
                                    settingsTargetPlaylist = playlist
                                },
                                onNavigateToPlaylists = { selectedTab = MainTab.PLAYLISTS },
                                onWatchChannel = { channel ->
                                    openChannelInPlayer(channel, allChannels)
                                }
                            )
                        }
                        MainTab.PLAYLISTS -> {
                            PlaylistsScreen(
                                playlists = playlists,
                                activePlaylistId = activePlaylist?.id,
                                isAddingPlaylist = isAddingPlaylist,
                                refreshingPlaylistId = refreshingPlaylistId,
                                errorMessage = errorMessage,
                                onAddPlaylist = { name, url, epgUrl, onSuccess ->
                                    viewModel.addPlaylist(name, url, epgUrl) {
                                        onSuccess()
                                        selectedTab = MainTab.CHANNELS
                                    }
                                },
                                onSelectPlaylist = { id ->
                                    viewModel.selectPlaylist(id)
                                    selectedTab = MainTab.CHANNELS
                                },
                                onRefreshPlaylist = viewModel::refreshPlaylist,
                                onUpdatePlaylistSettings = { id, name, url, epgUrl, loadEpgNow, onSuccess ->
                                    viewModel.updatePlaylistSettings(id, name, url, epgUrl, loadEpgNow, onSuccess)
                                },
                                onDeletePlaylist = viewModel::deletePlaylist,
                                onClearError = viewModel::clearError
                            )
                        }
                    }
                }

                if (uiMode == AppUiMode.TvUi) {
                    // ТВ-режим: левая боковая панель навигации (Leanback-стиль)
                    Scaffold(
                        containerColor = ObsidianBg,
                        contentWindowInsets = WindowInsets.safeDrawing,
                        snackbarHost = {
                            SnackbarHost(hostState = snackbarHostState) { data ->
                                Snackbar(
                                    snackbarData = data,
                                    containerColor = CardSurface,
                                    contentColor = TextPrimary,
                                    actionColor = WarmOrange
                                )
                            }
                        }
                    ) { innerPadding ->
                        Row(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(innerPadding)
                        ) {
                            TvNavigationRail(
                                selectedTab = selectedTab,
                                onTabSelected = { tab ->
                                    if (selectedTab == MainTab.SEARCH && tab != MainTab.SEARCH) {
                                        viewModel.updateSearchQuery("")
                                    }
                                    selectedTab = tab
                                }
                            )

                            Box(modifier = Modifier.weight(1f).fillMaxHeight()) {
                                renderMainContent()
                            }
                        }
                    }
                } else {
                    // Телефонный режим: нижняя панель навигации Material 3
                    val phoneTabs = listOf(
                        MainTab.CHANNELS,
                        MainTab.GUIDE,
                        MainTab.FAVORITES,
                        MainTab.PLAYLISTS
                    )
                    Scaffold(
                        containerColor = ObsidianBg,
                        contentWindowInsets = WindowInsets.safeDrawing,
                        snackbarHost = {
                            SnackbarHost(hostState = snackbarHostState) { data ->
                                Snackbar(
                                    snackbarData = data,
                                    containerColor = CardSurface,
                                    contentColor = TextPrimary,
                                    actionColor = WarmOrange
                                )
                            }
                        },
                        bottomBar = {
                            NavigationBar(
                                containerColor = ObsidianSurface,
                                tonalElevation = 4.dp
                            ) {
                                phoneTabs.forEach { tab ->
                                    val isSelected = selectedTab == tab
                                    NavigationBarItem(
                                        selected = isSelected,
                                        onClick = { selectedTab = tab },
                                        icon = {
                                            Icon(
                                                imageVector = if (isSelected) tab.selectedIcon else tab.unselectedIcon,
                                                contentDescription = stringResource(tab.labelRes)
                                            )
                                        },
                                        label = {
                                            Text(
                                                text = stringResource(tab.labelRes),
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                        },
                                        colors = NavigationBarItemDefaults.colors(
                                            selectedIconColor = WarmOrange,
                                            selectedTextColor = WarmOrange,
                                            indicatorColor = WarmOrangeSoft,
                                            unselectedIconColor = TextSecondary,
                                            unselectedTextColor = TextSecondary
                                        ),
                                        modifier = Modifier.testTag(tab.testTag)
                                    )
                                }
                            }
                        }
                    ) { innerPadding ->
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(innerPadding)
                        ) {
                            renderMainContent()
                        }
                    }
                }

                // Диалог настройки XMLTV-ссылки, открытый прямо из экрана «Телегид»
                settingsTargetPlaylist?.let { target ->
                    PlaylistSettingsDialog(
                        playlist = target,
                        onDismiss = { settingsTargetPlaylist = null },
                        onSave = { newName, newUrl, newEpgUrl, loadEpgNow ->
                            viewModel.updatePlaylistSettings(
                                playlistId = target.id,
                                name = newName,
                                url = newUrl,
                                epgUrl = newEpgUrl,
                                loadEpgNow = loadEpgNow
                            ) {
                                settingsTargetPlaylist = null
                            }
                        }
                    )
                }
            }

            composable<PlayerRoute> {
                val activeChannel = currentPlayingChannel
                if (activeChannel != null) {
                    PlayerScreen(
                        channel = activeChannel,
                        nowNext = channelNowNextMap[activeChannel.id],
                        currentTimeMs = currentTimeMs,
                        onSwitchChannel = { delta -> viewModel.switchChannelRelative(delta) },
                        onToggleFavorite = viewModel::toggleFavorite,
                        onBack = { navController.popBackStack() }
                    )
                } else {
                    LaunchedEffect(Unit) {
                        navController.popBackStack()
                    }
                }
            }
        }
    }
}

@Composable
private fun TvNavigationRail(
    selectedTab: MainTab,
    onTabSelected: (MainTab) -> Unit
) {
    NavigationRail(
        containerColor = ObsidianSurface,
        modifier = Modifier
            .fillMaxHeight()
            .width(96.dp)
            .padding(vertical = 16.dp),
        header = {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
                modifier = Modifier.padding(top = 8.dp, bottom = 20.dp)
            ) {
                Box(
                    modifier = Modifier
                        .width(28.dp)
                        .height(3.dp)
                        .clip(RoundedCornerShape(2.dp))
                        .background(WarmOrange)
                )
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = buildAnnotatedString {
                        withStyle(SpanStyle(color = TextPrimary)) { append("Mel") }
                        withStyle(SpanStyle(color = WarmOrange)) { append("TiVi") }
                    },
                    fontFamily = DisplayFontFamily,
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp
                )
            }
        }
    ) {
        MainTab.entries.forEach { tab ->
            val isSelected = selectedTab == tab
            NavigationRailItem(
                selected = isSelected,
                onClick = { onTabSelected(tab) },
                icon = {
                    Icon(
                        imageVector = if (isSelected) tab.selectedIcon else tab.unselectedIcon,
                        contentDescription = stringResource(tab.labelRes)
                    )
                },
                label = {
                    Text(
                        text = stringResource(tab.labelRes),
                        style = MaterialTheme.typography.labelSmall,
                        maxLines = 1
                    )
                },
                colors = NavigationRailItemDefaults.colors(
                    selectedIconColor = WarmOrange,
                    selectedTextColor = WarmOrange,
                    indicatorColor = WarmOrangeSoft,
                    unselectedIconColor = TextSecondary,
                    unselectedTextColor = TextSecondary
                ),
                modifier = Modifier
                    .padding(vertical = 4.dp)
                    .testTag(tab.testTag)
            )
        }
    }
}
