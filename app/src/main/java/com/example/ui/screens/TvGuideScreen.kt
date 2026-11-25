package com.example.ui.screens

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.data.local.ChannelEntity
import com.example.data.local.EpgProgramEntity
import com.example.data.local.PlaylistEntity
import com.example.ui.AppUiMode
import com.example.ui.LocalAppUiMode
import com.example.ui.components.ChannelLogo
import com.example.ui.theme.CardSurface
import com.example.ui.theme.CardSurfaceElevated
import com.example.ui.theme.LiveGreen
import com.example.ui.theme.ObsidianBg
import com.example.ui.theme.ObsidianSurface
import com.example.ui.theme.OutlineSubtle
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import com.example.ui.theme.WarmAmber
import com.example.ui.theme.WarmOrange
import com.example.ui.theme.WarmOrangeSoft
import com.example.ui.util.RussianFormatters
import com.example.ui.viewmodel.EpgLoadProgress
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import kotlin.math.roundToInt

/**
 * Экран телегида (EPG): синхронизированная сетка «каналы × время»
 * с линией текущего времени, кэшем Room, обновлением по кнопке и открытием деталей передачи.
 */
@Composable
fun TvGuideScreen(
    activePlaylist: PlaylistEntity?,
    channels: List<ChannelEntity>,
    epgProgramsByChannel: Map<Long, List<EpgProgramEntity>>,
    currentTimeMs: Long,
    isRefreshingEpg: Boolean,
    epgLoadProgress: EpgLoadProgress?,
    epgUpdatedAt: Long? = null,
    onRefreshEpg: () -> Unit,
    onOpenPlaylistSettings: (PlaylistEntity) -> Unit,
    onNavigateToPlaylists: () -> Unit,
    onWatchChannel: (ChannelEntity) -> Unit,
    modifier: Modifier = Modifier
) {
    val uiMode = LocalAppUiMode.current
    var selectedProgramPair by remember { mutableStateOf<Pair<ChannelEntity, EpgProgramEntity>?>(null) }

    if (activePlaylist == null) {
        GuideMessageState(
            title = stringResource(R.string.guide_no_playlist_title),
            subtitle = stringResource(R.string.guide_no_playlist_subtitle),
            buttonText = stringResource(R.string.playlists_title),
            onButtonClick = onNavigateToPlaylists,
            modifier = modifier
        )
        return
    }

    if (activePlaylist.epgUrl.isNullOrBlank()) {
        GuideMessageState(
            title = stringResource(R.string.guide_no_url_title),
            subtitle = stringResource(R.string.guide_no_url_subtitle, activePlaylist.name),
            buttonText = stringResource(R.string.guide_configure_url_button),
            onButtonClick = { onOpenPlaylistSettings(activePlaylist) },
            isSettingsIcon = true,
            modifier = modifier
        )
        return
    }

    val dpPerMinute = if (uiMode == AppUiMode.TvUi) 4.4.dp else 3.4.dp
    val channelColWidth = if (uiMode == AppUiMode.TvUi) 216.dp else 132.dp
    val rowHeight = if (uiMode == AppUiMode.TvUi) 76.dp else 66.dp

    // Окно времени сетки: от -2 часов (округлённо до 30 минут) на 24 часа вперёд
    val windowStartMs = remember(currentTimeMs / (30 * 60_000L)) {
        floorToHalfHour(currentTimeMs - 2 * 3600_000L)
    }
    val totalHours = 24
    val totalMinutes = totalHours * 60
    val windowEndMs = windowStartMs + totalMinutes * 60_000L
    val timelineWidth = dpPerMinute * totalMinutes

    val horizontalScroll = rememberScrollState()
    val scope = rememberCoroutineScope()
    val density = LocalDensity.current
    val refreshFocusRequester = remember { FocusRequester() }

    val nowOffsetDp = remember(currentTimeMs, windowStartMs, dpPerMinute) {
        val minutesFromStart = ((currentTimeMs - windowStartMs).toFloat() / 60_000f)
            .coerceIn(0f, totalMinutes.toFloat())
        dpPerMinute * minutesFromStart
    }

    fun scrollToNow() {
        scope.launch {
            val targetPx = with(density) { (nowOffsetDp - 48.dp).coerceAtLeast(0.dp).toPx() }.roundToInt()
            horizontalScroll.animateScrollTo(targetPx)
        }
    }

    // При открытии сетки автоматически прокручиваем ось времени к текущему моменту
    LaunchedEffect(activePlaylist.id, epgProgramsByChannel.isNotEmpty()) {
        val targetPx = with(density) { (nowOffsetDp - 48.dp).coerceAtLeast(0.dp).toPx() }.roundToInt()
        horizontalScroll.scrollTo(targetPx)
        if (uiMode == AppUiMode.TvUi && epgProgramsByChannel.isEmpty()) {
            runCatching { refreshFocusRequester.requestFocus() }
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(top = if (uiMode == AppUiMode.TvUi) 18.dp else 8.dp)
    ) {
        // Верхняя панель телегида: заголовок, дата/время, кнопки «Сейчас» и «Обновить»
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = if (uiMode == AppUiMode.TvUi) 32.dp else 16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(R.string.guide_title),
                    style = if (uiMode == AppUiMode.TvUi) {
                        MaterialTheme.typography.headlineLarge
                    } else {
                        MaterialTheme.typography.headlineMedium
                    },
                    color = TextPrimary
                )
                val epgUpdatedLabel = activePlaylist.epgUpdatedAt?.let { ts ->
                    " · ${stringResource(R.string.playlists_epg_updated_at, RussianFormatters.formatDateTime(ts))}"
                }.orEmpty()
                Text(
                    text = "${RussianFormatters.formatFullDateWithWeekday(currentTimeMs)} · ${
                        RussianFormatters.formatShortTime(currentTimeMs)
                    }$epgUpdatedLabel",
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Spacer(modifier = Modifier.width(8.dp))

            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (epgProgramsByChannel.isNotEmpty()) {
                    OutlinedButton(
                        onClick = ::scrollToNow,
                        shape = RoundedCornerShape(12.dp),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .testTag("guide_now_button")
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Schedule,
                            contentDescription = null,
                            tint = WarmOrange,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(stringResource(R.string.guide_now_button), color = TextPrimary)
                    }
                }

                Button(
                    onClick = onRefreshEpg,
                    enabled = !isRefreshingEpg,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = WarmOrange,
                        contentColor = ObsidianBg
                    ),
                    shape = RoundedCornerShape(12.dp),
                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp),
                    modifier = Modifier
                        .focusRequester(refreshFocusRequester)
                        .minimumInteractiveComponentSize()
                        .testTag("guide_refresh_button")
                ) {
                    if (isRefreshingEpg) {
                        CircularProgressIndicator(
                            color = ObsidianBg,
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
                    Text(
                        text = stringResource(R.string.guide_refresh_button),
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Компактная панель статуса обновления EPG (всегда видна)
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = if (uiMode == AppUiMode.TvUi) 32.dp else 16.dp)
        ) {
            if (isRefreshingEpg) {
                Row(
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    LinearProgressIndicator(
                        progress = { epgLoadProgress?.fraction ?: 0f },
                        color = WarmOrange,
                        trackColor = OutlineSubtle,
                        modifier = Modifier
                            .weight(1f)
                            .height(4.dp)
                            .clip(RoundedCornerShape(2.dp))
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = if (epgLoadProgress != null && !epgLoadProgress.isIndeterminate) {
                            val percent = (epgLoadProgress.fraction * 100f).roundToInt()
                            "Обновление EPG... $percent%"
                        } else {
                            "Обновление EPG..."
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        color = TextSecondary
                    )
                }
            } else {
                Row(
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    val dateText = epgUpdatedAt?.let { ts ->
                        val sdf = SimpleDateFormat("dd.MM.yyyy HH:mm")
                        "Обновлено: ${sdf.format(Date(ts))}"
                    } ?: "Еще не обновлялось"
                    Text(
                        text = dateText,
                        style = MaterialTheme.typography.bodyMedium,
                        color = TextSecondary
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))
        if (isRefreshingEpg && epgProgramsByChannel.isEmpty()) {
            EpgLoadingState(
                progress = epgLoadProgress,
                modifier = Modifier.fillMaxSize()
            )
        } else if (epgProgramsByChannel.isEmpty()) {
            GuideMessageState(
                title = stringResource(R.string.guide_empty_programs_title),
                subtitle = stringResource(R.string.guide_empty_programs_subtitle),
                buttonText = stringResource(R.string.guide_refresh_button),
                onButtonClick = onRefreshEpg,
                modifier = Modifier.fillMaxSize()
            )
        } else {
            // Шапка временной шкалы + закреплённый заголовок колонки каналов
            TimeAxisHeaderRow(
                channelColWidth = channelColWidth,
                windowStartMs = windowStartMs,
                totalMinutes = totalMinutes,
                dpPerMinute = dpPerMinute,
                timelineWidth = timelineWidth,
                nowOffsetDp = nowOffsetDp,
                currentTimeMs = currentTimeMs,
                horizontalScroll = horizontalScroll,
                uiMode = uiMode
            )

            HorizontalDivider(color = OutlineSubtle, thickness = 1.dp)

            // Виртуализованная сетка строк каналов с их передачами по времени
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(bottom = 24.dp)
            ) {
                items(
                    items = channels,
                    key = { it.id }
                ) { channel ->
                    val channelPrograms = remember(epgProgramsByChannel[channel.id], windowStartMs, windowEndMs) {
                        epgProgramsByChannel[channel.id]
                            ?.filter { it.endTimeMs > windowStartMs && it.startTimeMs < windowEndMs }
                            ?: emptyList()
                    }

                    GuideChannelRow(
                        channel = channel,
                        programs = channelPrograms,
                        channelColWidth = channelColWidth,
                        rowHeight = rowHeight,
                        windowStartMs = windowStartMs,
                        windowEndMs = windowEndMs,
                        dpPerMinute = dpPerMinute,
                        timelineWidth = timelineWidth,
                        nowOffsetDp = nowOffsetDp,
                        currentTimeMs = currentTimeMs,
                        horizontalScroll = horizontalScroll,
                        uiMode = uiMode,
                        onChannelClick = { onWatchChannel(channel) },
                        onProgramClick = { program ->
                            selectedProgramPair = channel to program
                        }
                    )
                    HorizontalDivider(color = OutlineSubtle.copy(alpha = 0.55f), thickness = 1.dp)
                }
            }
        }
    }

    selectedProgramPair?.let { (channel, program) ->
        ProgramDetailsDialog(
            channel = channel,
            program = program,
            currentTimeMs = currentTimeMs,
            onDismiss = { selectedProgramPair = null },
            onWatchChannel = {
                selectedProgramPair = null
                onWatchChannel(channel)
            }
        )
    }
}

@Composable
private fun TimeAxisHeaderRow(
    channelColWidth: Dp,
    windowStartMs: Long,
    totalMinutes: Int,
    dpPerMinute: Dp,
    timelineWidth: Dp,
    nowOffsetDp: Dp,
    currentTimeMs: Long,
    horizontalScroll: ScrollState,
    uiMode: AppUiMode
) {
    val slotCount = totalMinutes / 30
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(44.dp)
            .background(ObsidianSurface)
            .padding(start = if (uiMode == AppUiMode.TvUi) 24.dp else 0.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Левый заголовок колонки каналов
        Box(
            modifier = Modifier
                .width(channelColWidth)
                .fillMaxHeight()
                .padding(horizontal = 12.dp),
            contentAlignment = Alignment.CenterStart
        ) {
            Text(
                text = stringResource(R.string.nav_channels),
                style = MaterialTheme.typography.labelLarge,
                color = TextSecondary
            )
        }

        Box(
            modifier = Modifier
                .width(1.dp)
                .fillMaxHeight()
                .background(OutlineSubtle)
        )

        // Временные слоты по 30 минут + отметка текущего времени
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight()
                .horizontalScroll(horizontalScroll)
        ) {
            Box(
                modifier = Modifier
                    .width(timelineWidth)
                    .fillMaxHeight()
            ) {
                for (slot in 0 until slotCount) {
                    val slotTimeMs = windowStartMs + slot * 30 * 60_000L
                    val offsetX = dpPerMinute * (slot * 30f)
                    Box(
                        modifier = Modifier
                            .offset(x = offsetX)
                            .width(dpPerMinute * 30)
                            .fillMaxHeight()
                            .padding(start = 8.dp),
                        contentAlignment = Alignment.CenterStart
                    ) {
                        Box(
                            modifier = Modifier
                                .align(Alignment.CenterStart)
                                .offset(x = (-8).dp)
                                .width(1.dp)
                                .height(16.dp)
                                .background(OutlineSubtle)
                        )
                        Text(
                            text = RussianFormatters.formatShortTime(slotTimeMs),
                            style = MaterialTheme.typography.labelMedium,
                            color = TextSecondary
                        )
                    }
                }

                // Метка текущего времени на шкале
                Box(
                    modifier = Modifier
                        .offset(x = (nowOffsetDp - 24.dp).coerceAtLeast(0.dp))
                        .align(Alignment.CenterStart)
                        .clip(RoundedCornerShape(8.dp))
                        .background(WarmOrange)
                        .padding(horizontal = 8.dp, vertical = 2.dp)
                ) {
                    Text(
                        text = RussianFormatters.formatShortTime(currentTimeMs),
                        style = MaterialTheme.typography.labelSmall.copy(
                            color = ObsidianBg,
                            fontWeight = FontWeight.Bold
                        )
                    )
                }
            }
        }
    }
}

@Composable
private fun GuideChannelRow(
    channel: ChannelEntity,
    programs: List<EpgProgramEntity>,
    channelColWidth: Dp,
    rowHeight: Dp,
    windowStartMs: Long,
    windowEndMs: Long,
    dpPerMinute: Dp,
    timelineWidth: Dp,
    nowOffsetDp: Dp,
    currentTimeMs: Long,
    horizontalScroll: ScrollState,
    uiMode: AppUiMode,
    onChannelClick: () -> Unit,
    onProgramClick: (EpgProgramEntity) -> Unit
) {
    var channelFocused by remember { mutableStateOf(false) }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(rowHeight)
            .padding(start = if (uiMode == AppUiMode.TvUi) 24.dp else 0.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Левая ячейка канала (нажатие открывает эфир канала)
        Surface(
            onClick = onChannelClick,
            color = if (channelFocused) CardSurfaceElevated else ObsidianSurface,
            border = if (channelFocused) BorderStroke(2.dp, WarmOrange) else null,
            modifier = Modifier
                .width(channelColWidth)
                .fillMaxHeight()
                .onFocusChanged { channelFocused = it.isFocused }
                .testTag("guide_channel_${channel.id}")
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 10.dp)
            ) {
                ChannelLogo(
                    channelName = channel.name,
                    logoUrl = channel.logoUrl,
                    cornerRadius = 8.dp,
                    contentPadding = 4.dp,
                    modifier = Modifier.size(if (uiMode == AppUiMode.TvUi) 42.dp else 34.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = channel.name,
                    style = MaterialTheme.typography.bodyMedium.copy(
                        fontWeight = FontWeight.SemiBold,
                        color = if (channelFocused) WarmOrange else TextPrimary
                    ),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }

        Box(
            modifier = Modifier
                .width(1.dp)
                .fillMaxHeight()
                .background(OutlineSubtle)
        )

        // Правая горизонтальная полоса передач канала
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight()
                .horizontalScroll(horizontalScroll)
        ) {
            Box(
                modifier = Modifier
                    .width(timelineWidth)
                    .fillMaxHeight()
            ) {
                if (programs.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 16.dp),
                        contentAlignment = Alignment.CenterStart
                    ) {
                        Text(
                            text = stringResource(R.string.guide_no_programs_for_channel),
                            style = MaterialTheme.typography.bodyMedium,
                            color = TextMuted
                        )
                    }
                } else {
                    for (program in programs) {
                        val clampedStart = program.startTimeMs.coerceAtLeast(windowStartMs)
                        val clampedEnd = program.endTimeMs.coerceAtMost(windowEndMs)
                        val startOffsetMinutes = (clampedStart - windowStartMs).toFloat() / 60_000f
                        val durationMinutes = ((clampedEnd - clampedStart).toFloat() / 60_000f).coerceAtLeast(6f)
                        val blockX = dpPerMinute * startOffsetMinutes
                        val blockW = dpPerMinute * durationMinutes

                        val isLive = program.startTimeMs <= currentTimeMs && program.endTimeMs > currentTimeMs
                        val isPast = program.endTimeMs <= currentTimeMs

                        GuideProgramCell(
                            program = program,
                            isLive = isLive,
                            isPast = isPast,
                            currentTimeMs = currentTimeMs,
                            onClick = { onProgramClick(program) },
                            modifier = Modifier
                                .offset(x = blockX)
                                .width(blockW)
                                .fillMaxHeight()
                                .padding(horizontal = 1.5.dp, vertical = 4.dp)
                        )
                    }
                }

                // Вертикальная линия текущего времени поверх сетки
                Box(
                    modifier = Modifier
                        .offset(x = nowOffsetDp)
                        .width(2.dp)
                        .fillMaxHeight()
                        .background(WarmOrange.copy(alpha = 0.9f))
                )
            }
        }
    }
}

@Composable
private fun GuideProgramCell(
    program: EpgProgramEntity,
    isLive: Boolean,
    isPast: Boolean,
    currentTimeMs: Long,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    var isFocused by remember { mutableStateOf(false) }
    val borderColor by animateColorAsState(
        targetValue = when {
            isFocused -> WarmOrange
            isLive -> WarmOrange.copy(alpha = 0.7f)
            else -> OutlineSubtle
        },
        label = "guide_cell_border"
    )
    val bgColor = when {
        isFocused -> CardSurfaceElevated
        isLive -> WarmOrangeSoft.copy(alpha = 0.35f)
        isPast -> ObsidianSurface
        else -> CardSurface
    }

    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(10.dp),
        color = bgColor,
        border = BorderStroke(if (isFocused || isLive) 1.5.dp else 1.dp, borderColor),
        modifier = modifier
            .onFocusChanged { isFocused = it.isFocused }
            .testTag("guide_program_${program.id}")
    ) {
        Column(
            verticalArrangement = Arrangement.SpaceBetween,
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 10.dp, vertical = 6.dp)
        ) {
            Column {
                Text(
                    text = program.title,
                    style = MaterialTheme.typography.bodyMedium.copy(
                        fontWeight = if (isLive || isFocused) FontWeight.SemiBold else FontWeight.Normal,
                        color = if (isPast && !isFocused) TextMuted else TextPrimary
                    ),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = stringResource(
                        R.string.guide_program_time_range,
                        RussianFormatters.formatShortTime(program.startTimeMs),
                        RussianFormatters.formatShortTime(program.endTimeMs)
                    ),
                    style = MaterialTheme.typography.labelSmall.copy(
                        color = if (isLive) WarmAmber else TextMuted
                    ),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            if (isLive) {
                val duration = (program.endTimeMs - program.startTimeMs).coerceAtLeast(1L)
                val progress = ((currentTimeMs - program.startTimeMs).toFloat() / duration.toFloat())
                    .coerceIn(0f, 1f)
                LinearProgressIndicator(
                    progress = { progress },
                    color = WarmOrange,
                    trackColor = OutlineSubtle,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(2.5.dp)
                        .clip(RoundedCornerShape(2.dp))
                )
            }
        }
    }
}

/**
 * Диалог подробностей выбранной передачи из телегида с возможностью сразу запустить канал.
 */
@Composable
fun ProgramDetailsDialog(
    channel: ChannelEntity,
    program: EpgProgramEntity,
    currentTimeMs: Long,
    onDismiss: () -> Unit,
    onWatchChannel: () -> Unit
) {
    val isLive = program.startTimeMs <= currentTimeMs && program.endTimeMs > currentTimeMs
    val isUpcoming = program.startTimeMs > currentTimeMs
    val watchFocusRequester = remember { FocusRequester() }

    LaunchedEffect(Unit) {
        runCatching { watchFocusRequester.requestFocus() }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = CardSurface,
        titleContentColor = TextPrimary,
        textContentColor = TextSecondary,
        shape = RoundedCornerShape(22.dp),
        title = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                ChannelLogo(
                    channelName = channel.name,
                    logoUrl = channel.logoUrl,
                    cornerRadius = 10.dp,
                    contentPadding = 6.dp,
                    modifier = Modifier.size(46.dp)
                )
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = program.title,
                        style = MaterialTheme.typography.titleLarge,
                        color = TextPrimary,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = channel.name,
                        style = MaterialTheme.typography.bodyMedium,
                        color = WarmOrange
                    )
                }
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    val statusText = when {
                        isLive -> stringResource(R.string.guide_status_live)
                        isUpcoming -> stringResource(R.string.guide_status_upcoming)
                        else -> stringResource(R.string.guide_status_finished)
                    }
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(if (isLive) WarmOrangeSoft else ObsidianBg)
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        if (isLive) {
                            Box(
                                modifier = Modifier
                                    .size(7.dp)
                                    .clip(CircleShape)
                                    .background(LiveGreen)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                        }
                        Text(
                            text = statusText,
                            style = MaterialTheme.typography.labelSmall.copy(
                                color = if (isLive) WarmOrange else TextSecondary,
                                fontWeight = FontWeight.Bold
                            )
                        )
                    }

                    Text(
                        text = stringResource(
                            R.string.guide_program_time_range,
                            RussianFormatters.formatShortTime(program.startTimeMs),
                            RussianFormatters.formatShortTime(program.endTimeMs)
                        ),
                        style = MaterialTheme.typography.labelLarge,
                        color = TextPrimary
                    )
                }

                if (isLive) {
                    val duration = (program.endTimeMs - program.startTimeMs).coerceAtLeast(1L)
                    val progress = ((currentTimeMs - program.startTimeMs).toFloat() / duration.toFloat())
                        .coerceIn(0f, 1f)
                    LinearProgressIndicator(
                        progress = { progress },
                        color = WarmOrange,
                        trackColor = OutlineSubtle,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(4.dp)
                            .clip(RoundedCornerShape(2.dp))
                    )
                }

                program.category?.takeIf { it.isNotBlank() }?.let { category ->
                    Text(
                        text = category,
                        style = MaterialTheme.typography.labelMedium,
                        color = WarmAmber
                    )
                }

                Text(
                    text = program.description?.takeIf { it.isNotBlank() }
                        ?: stringResource(R.string.guide_program_no_description),
                    style = MaterialTheme.typography.bodyLarge,
                    color = TextSecondary
                )
            }
        },
        confirmButton = {
            Button(
                onClick = onWatchChannel,
                colors = ButtonDefaults.buttonColors(
                    containerColor = WarmOrange,
                    contentColor = ObsidianBg
                ),
                modifier = Modifier
                    .focusRequester(watchFocusRequester)
                    .minimumInteractiveComponentSize()
                    .testTag("program_watch_channel_button")
            ) {
                Icon(
                    imageVector = Icons.Filled.PlayArrow,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(stringResource(R.string.channels_watch_action), fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                modifier = Modifier
                    .minimumInteractiveComponentSize()
                    .testTag("program_close_button")
            ) {
                Text(stringResource(R.string.guide_close), color = TextSecondary)
            }
        }
    )
}

@Composable
private fun EpgLoadingState(
    progress: EpgLoadProgress?,
    modifier: Modifier = Modifier
) {
    val isIndeterminate = progress == null || progress.isIndeterminate
    val fraction = progress?.fraction ?: 0f
    val percent = (fraction * 100f).roundToInt()

    Box(
        modifier = modifier,
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(horizontal = 32.dp)
        ) {
            if (isIndeterminate) {
                LinearProgressIndicator(
                    color = WarmOrange,
                    trackColor = OutlineSubtle,
                    modifier = Modifier
                        .fillMaxWidth(0.5f)
                        .height(4.dp)
                        .clip(RoundedCornerShape(2.dp))
                )
            } else {
                LinearProgressIndicator(
                    progress = { fraction },
                    color = WarmOrange,
                    trackColor = OutlineSubtle,
                    modifier = Modifier
                        .fillMaxWidth(0.5f)
                        .height(4.dp)
                        .clip(RoundedCornerShape(2.dp))
                )
            }
            Spacer(modifier = Modifier.height(14.dp))
            Text(
                text = if (isIndeterminate) {
                    stringResource(R.string.guide_loading)
                } else {
                    stringResource(R.string.guide_loading_percent, percent)
                },
                style = MaterialTheme.typography.bodyLarge,
                color = TextSecondary
            )
        }
    }
}

@Composable
private fun GuideMessageState(
    title: String,
    subtitle: String,
    buttonText: String,
    onButtonClick: () -> Unit,
    modifier: Modifier = Modifier,
    isSettingsIcon: Boolean = false
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
                    imageVector = if (isSettingsIcon) Icons.Filled.Settings else Icons.Filled.CalendarMonth,
                    contentDescription = null,
                    tint = WarmOrange,
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
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodyLarge,
                color = TextSecondary,
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(20.dp))
            Button(
                onClick = onButtonClick,
                colors = ButtonDefaults.buttonColors(
                    containerColor = WarmOrange,
                    contentColor = ObsidianBg
                ),
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier
                    .minimumInteractiveComponentSize()
                    .testTag("guide_empty_cta_button")
            ) {
                Text(buttonText, fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

private fun floorToHalfHour(timeMs: Long): Long {
    val calendar = Calendar.getInstance().apply {
        timeInMillis = timeMs
        val minute = get(Calendar.MINUTE)
        set(Calendar.MINUTE, if (minute < 30) 0 else 30)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
    }
    return calendar.timeInMillis
}
