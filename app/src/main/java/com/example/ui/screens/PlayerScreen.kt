package com.example.ui.screens

import android.app.Activity
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.activity.compose.BackHandler
import androidx.annotation.OptIn
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AspectRatio
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.StarOutline
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.common.util.UnstableApi
import androidx.media3.ui.PlayerView
import com.example.R
import com.example.data.local.ChannelEntity
import com.example.player.IptvPlayerManager
import com.example.ui.AppUiMode
import com.example.ui.LocalAppUiMode
import com.example.ui.components.ChannelEpgCompactBlock
import com.example.ui.components.ChannelLogo
import com.example.ui.viewmodel.ChannelNowNext
import com.example.ui.theme.CardSurface
import com.example.ui.theme.ErrorRed
import com.example.ui.theme.FavoriteGold
import com.example.ui.theme.LiveGreen
import com.example.ui.theme.ObsidianBg
import com.example.ui.theme.OutlineSubtle
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import com.example.ui.theme.WarmOrange
import com.example.ui.theme.WarmOrangeSoft
import com.example.ui.util.RussianFormatters
import kotlinx.coroutines.delay

/**
 * Полноэкранный плеер на базе Media3 ExoPlayer с поддержкой HLS (.m3u8),
 * информационным оверлеем (название и логотип на несколько секунд после старта и переключения),
 * управлением с пульта D-pad и сенсорного экрана.
 */
@OptIn(UnstableApi::class)
@Composable
fun PlayerScreen(
    channel: ChannelEntity,
    nowNext: ChannelNowNext? = null,
    currentTimeMs: Long = System.currentTimeMillis(),
    onSwitchChannel: (Int) -> Unit,
    onToggleFavorite: (ChannelEntity) -> Unit,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val uiMode = LocalAppUiMode.current
    val playerManager = remember(context) { IptvPlayerManager(context) }
    val playbackState by playerManager.state.collectAsStateWithLifecycle()

    var showOverlay by remember { mutableStateOf(true) }
    var overlayTrigger by remember { mutableIntStateOf(0) }
    var currentTime by remember { mutableStateOf(RussianFormatters.formatShortTime()) }

    val rootFocusRequester = remember { FocusRequester() }
    val playPauseFocusRequester = remember { FocusRequester() }
    val retryFocusRequester = remember { FocusRequester() }

    // Погружаемый полноэкранный режим на время просмотра
    DisposableEffect(context) {
        val window = (context as? Activity)?.window
        val controller = window?.let { WindowCompat.getInsetsController(it, it.decorView) }
        controller?.systemBarsBehavior =
            WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        controller?.hide(WindowInsetsCompat.Type.systemBars())

        onDispose {
            controller?.show(WindowInsetsCompat.Type.systemBars())
            playerManager.release()
        }
    }

    // Запуск потока при смене канала и показ оверлея
    LaunchedEffect(channel.id, channel.streamUrl) {
        showOverlay = true
        overlayTrigger++
        playerManager.playUrl(channel.streamUrl)
        if (uiMode == AppUiMode.TvUi) {
            runCatching { rootFocusRequester.requestFocus() }
        }
    }

    // Автоматическое скрытие оверлея через 4 секунды
    LaunchedEffect(showOverlay, overlayTrigger) {
        if (showOverlay && playbackState.errorMessage == null) {
            delay(4000L)
            showOverlay = false
            if (uiMode == AppUiMode.TvUi) {
                runCatching { rootFocusRequester.requestFocus() }
            }
        }
    }

    // Обновляем 24-часовые часы пока открыт оверлей
    LaunchedEffect(showOverlay) {
        if (showOverlay) {
            while (true) {
                currentTime = RussianFormatters.formatShortTime()
                delay(15_000L)
            }
        }
    }

    // При ошибке в ТВ-режиме переносим фокус на кнопку «Повторить»
    LaunchedEffect(playbackState.errorMessage) {
        if (playbackState.errorMessage != null && uiMode == AppUiMode.TvUi) {
            runCatching { retryFocusRequester.requestFocus() }
        }
    }

    fun handleBack() {
        playerManager.stop()
        onBack()
    }

    BackHandler(onBack = ::handleBack)

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .focusRequester(rootFocusRequester)
            .focusable()
            .onPreviewKeyEvent { event ->
                if (event.type == KeyEventType.KeyDown) {
                    when (event.key) {
                        Key.Back, Key.Escape -> {
                            handleBack()
                            true
                        }
                        Key.DirectionUp, Key.ChannelUp -> {
                            onSwitchChannel(-1)
                            true
                        }
                        Key.DirectionDown, Key.ChannelDown -> {
                            onSwitchChannel(1)
                            true
                        }
                        Key.MediaPlayPause -> {
                            playerManager.togglePlayPause()
                            showOverlay = true
                            overlayTrigger++
                            true
                        }
                        Key.DirectionCenter, Key.Enter, Key.NumPadEnter -> {
                            if (!showOverlay) {
                                showOverlay = true
                                overlayTrigger++
                                runCatching { playPauseFocusRequester.requestFocus() }
                                true
                            } else {
                                // Если оверлей уже показан, откладываем автоскрытие и позволяем фокусу сработать на кнопках
                                overlayTrigger++
                                false
                            }
                        }
                        Key.DirectionLeft, Key.DirectionRight -> {
                            if (!showOverlay) {
                                showOverlay = true
                                overlayTrigger++
                                runCatching { playPauseFocusRequester.requestFocus() }
                                true
                            } else {
                                overlayTrigger++
                                false
                            }
                        }
                        else -> false
                    }
                } else {
                    false
                }
            }
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null
            ) {
                showOverlay = !showOverlay
                if (showOverlay) overlayTrigger++
            }
            .testTag("player_root")
    ) {
        // Видеоповерхность Media3 PlayerView
        AndroidView(
            factory = { ctx ->
                PlayerView(ctx).apply {
                    player = playerManager.exoPlayer
                    useController = false
                    keepScreenOn = true
                    layoutParams = FrameLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT
                    )
                }
            },
            update = { playerView ->
                if (playerView.player !== playerManager.exoPlayer) {
                    playerView.player = playerManager.exoPlayer
                }
                playerView.resizeMode = playbackState.aspectMode.resizeMode
            },
            modifier = Modifier.fillMaxSize()
        )

        // Индикатор буферизации
        if (playbackState.isBuffering && playbackState.errorMessage == null) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier
                    .align(Alignment.Center)
                    .clip(RoundedCornerShape(16.dp))
                    .background(ObsidianBg.copy(alpha = 0.72f))
                    .padding(horizontal = 24.dp, vertical = 18.dp)
            ) {
                CircularProgressIndicator(
                    color = WarmOrange,
                    strokeWidth = 3.dp,
                    modifier = Modifier.size(36.dp)
                )
                Spacer(modifier = Modifier.height(10.dp))
                Text(
                    text = stringResource(R.string.player_buffering),
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextPrimary
                )
            }
        }

        // Карточка ошибки воспроизведения на русском
        playbackState.errorMessage?.let { errorText ->
            Surface(
                shape = RoundedCornerShape(20.dp),
                color = CardSurface.copy(alpha = 0.94f),
                border = BorderStroke(1.dp, ErrorRed.copy(alpha = 0.6f)),
                modifier = Modifier
                    .align(Alignment.Center)
                    .padding(24.dp)
                    .fillMaxWidth(if (uiMode == AppUiMode.TvUi) 0.52f else 0.88f)
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.padding(24.dp)
                ) {
                    Text(
                        text = stringResource(R.string.player_error_title),
                        style = MaterialTheme.typography.titleLarge,
                        color = TextPrimary,
                        textAlign = TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = errorText,
                        style = MaterialTheme.typography.bodyLarge,
                        color = TextSecondary,
                        textAlign = TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(20.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        OutlinedButton(
                            onClick = ::handleBack,
                            modifier = Modifier
                                .minimumInteractiveComponentSize()
                                .testTag("player_error_back_button")
                        ) {
                            Text(stringResource(R.string.player_back))
                        }
                        Button(
                            onClick = { playerManager.retry() },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = WarmOrange,
                                contentColor = ObsidianBg
                            ),
                            modifier = Modifier
                                .focusRequester(retryFocusRequester)
                                .minimumInteractiveComponentSize()
                                .testTag("player_retry_button")
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Refresh,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(stringResource(R.string.player_retry), fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }

        // Информационный оверлей поверх видео: название канала, логотип, часы и кнопки управления
        AnimatedVisibility(
            visible = showOverlay && playbackState.errorMessage == null,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.fillMaxSize()
        ) {
            Box(modifier = Modifier.fillMaxSize()) {
                // Верхний градиент и панель возврата + статуса эфира
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .align(Alignment.TopCenter)
                        .background(
                            Brush.verticalGradient(
                                colors = listOf(
                                    ObsidianBg.copy(alpha = 0.88f),
                                    Color.Transparent
                                )
                            )
                        )
                        .safeDrawingPadding()
                        .padding(horizontal = 20.dp, vertical = 14.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    FilledTonalIconButton(
                        onClick = ::handleBack,
                        colors = IconButtonDefaults.filledTonalIconButtonColors(
                            containerColor = CardSurface.copy(alpha = 0.8f),
                            contentColor = TextPrimary
                        ),
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .testTag("player_back_button")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.player_back)
                        )
                    }

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .background(CardSurface.copy(alpha = 0.82f))
                                .padding(horizontal = 10.dp, vertical = 5.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(8.dp)
                                    .clip(CircleShape)
                                    .background(LiveGreen)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = stringResource(R.string.player_live_badge),
                                style = MaterialTheme.typography.labelSmall.copy(
                                    color = TextPrimary,
                                    fontWeight = FontWeight.Bold
                                )
                            )
                        }

                        Text(
                            text = currentTime,
                            style = MaterialTheme.typography.titleMedium.copy(
                                color = TextPrimary,
                                fontWeight = FontWeight.SemiBold
                            )
                        )
                    }
                }

                // Нижний градиент с логотипом канала, названием и панелью управления
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .align(Alignment.BottomCenter)
                        .background(
                            Brush.verticalGradient(
                                colors = listOf(
                                    Color.Transparent,
                                    ObsidianBg.copy(alpha = 0.88f),
                                    ObsidianBg.copy(alpha = 0.96f)
                                )
                            )
                        )
                        .safeDrawingPadding()
                        .padding(horizontal = 24.dp, vertical = 20.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        // Логотип и название канала
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.weight(1f)
                        ) {
                            ChannelLogo(
                                channelName = channel.name,
                                logoUrl = channel.logoUrl,
                                cornerRadius = 14.dp,
                                contentPadding = 8.dp,
                                modifier = Modifier.size(if (uiMode == AppUiMode.TvUi) 72.dp else 56.dp)
                            )
                            Spacer(modifier = Modifier.width(14.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = channel.name,
                                    style = if (uiMode == AppUiMode.TvUi) {
                                        MaterialTheme.typography.headlineMedium
                                    } else {
                                        MaterialTheme.typography.titleLarge
                                    },
                                    color = TextPrimary,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Spacer(modifier = Modifier.height(3.dp))
                                Text(
                                    text = channel.groupName,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = WarmOrange,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                if (nowNext?.current != null || nowNext?.next != null) {
                                    Spacer(modifier = Modifier.height(4.dp))
                                    ChannelEpgCompactBlock(
                                        nowNext = nowNext,
                                        currentTimeMs = currentTimeMs,
                                        modifier = Modifier.fillMaxWidth(0.85f)
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.width(12.dp))

                        // Кнопки переключения каналов, паузы, избранного и масштаба
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            FilledTonalIconButton(
                                onClick = {
                                    overlayTrigger++
                                    onSwitchChannel(-1)
                                },
                                colors = IconButtonDefaults.filledTonalIconButtonColors(
                                    containerColor = CardSurface,
                                    contentColor = TextPrimary
                                ),
                                modifier = Modifier
                                    .minimumInteractiveComponentSize()
                                    .testTag("player_prev_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.SkipPrevious,
                                    contentDescription = stringResource(R.string.player_prev_channel)
                                )
                            }

                            FilledTonalIconButton(
                                onClick = {
                                    overlayTrigger++
                                    playerManager.togglePlayPause()
                                },
                                colors = IconButtonDefaults.filledTonalIconButtonColors(
                                    containerColor = WarmOrange,
                                    contentColor = ObsidianBg
                                ),
                                modifier = Modifier
                                    .focusRequester(playPauseFocusRequester)
                                    .minimumInteractiveComponentSize()
                                    .testTag("player_play_pause_button")
                            ) {
                                Icon(
                                    imageVector = if (playbackState.isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                                    contentDescription = stringResource(R.string.player_play_pause)
                                )
                            }

                            FilledTonalIconButton(
                                onClick = {
                                    overlayTrigger++
                                    onSwitchChannel(1)
                                },
                                colors = IconButtonDefaults.filledTonalIconButtonColors(
                                    containerColor = CardSurface,
                                    contentColor = TextPrimary
                                ),
                                modifier = Modifier
                                    .minimumInteractiveComponentSize()
                                    .testTag("player_next_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.SkipNext,
                                    contentDescription = stringResource(R.string.player_next_channel)
                                )
                            }

                            FilledTonalIconButton(
                                onClick = {
                                    overlayTrigger++
                                    onToggleFavorite(channel)
                                },
                                colors = IconButtonDefaults.filledTonalIconButtonColors(
                                    containerColor = CardSurface,
                                    contentColor = if (channel.isFavorite) FavoriteGold else TextPrimary
                                ),
                                modifier = Modifier
                                    .minimumInteractiveComponentSize()
                                    .testTag("player_favorite_button")
                            ) {
                                Icon(
                                    imageVector = if (channel.isFavorite) Icons.Filled.Star else Icons.Outlined.StarOutline,
                                    contentDescription = stringResource(
                                        if (channel.isFavorite) R.string.channels_remove_favorite
                                        else R.string.channels_add_favorite
                                    )
                                )
                            }

                            FilledTonalIconButton(
                                onClick = {
                                    overlayTrigger++
                                    playerManager.cycleAspectMode()
                                },
                                colors = IconButtonDefaults.filledTonalIconButtonColors(
                                    containerColor = CardSurface,
                                    contentColor = TextPrimary
                                ),
                                modifier = Modifier
                                    .minimumInteractiveComponentSize()
                                    .testTag("player_aspect_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.AspectRatio,
                                    contentDescription = stringResource(playbackState.aspectMode.labelRes)
                                )
                            }
                        }
                    }

                    if (uiMode == AppUiMode.TvUi) {
                        Spacer(modifier = Modifier.height(10.dp))
                        Text(
                            text = stringResource(R.string.player_tv_controls_hint),
                            style = MaterialTheme.typography.labelMedium,
                            color = TextSecondary
                        )
                    }
                }
            }
        }
    }
}
