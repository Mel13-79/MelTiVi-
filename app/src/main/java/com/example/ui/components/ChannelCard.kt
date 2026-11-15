package com.example.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.StarOutline
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.data.local.ChannelEntity
import com.example.ui.theme.CardSurface
import com.example.ui.theme.CardSurfaceElevated
import com.example.ui.theme.FavoriteGold
import com.example.ui.theme.ObsidianBg
import com.example.ui.theme.OutlineSubtle
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import com.example.ui.theme.WarmOrange
import com.example.ui.theme.WarmOrangeSoft
import com.example.ui.util.RussianFormatters
import com.example.ui.viewmodel.ChannelNowNext

/**
 * Карточка телеканала в стиле Android TV (Leanback):
 * крупный формат, фокус-обводка и информация «сейчас идёт» / «далее» из EPG под названием канала.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun TvChannelCard(
    channel: ChannelEntity,
    isLastWatched: Boolean,
    nowNext: ChannelNowNext? = null,
    currentTimeMs: Long = System.currentTimeMillis(),
    onClick: () -> Unit,
    onToggleFavorite: () -> Unit,
    onFocused: () -> Unit,
    modifier: Modifier = Modifier,
    focusRequester: FocusRequester? = null
) {
    var isFocused by remember { mutableStateOf(false) }

    val scale by animateFloatAsState(
        targetValue = if (isFocused) 1.07f else 1f,
        animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
        label = "tv_card_scale"
    )
    val borderColor by animateColorAsState(
        targetValue = when {
            isFocused -> WarmOrange
            isLastWatched -> WarmOrange.copy(alpha = 0.5f)
            else -> OutlineSubtle
        },
        label = "tv_card_border"
    )

    val focusModifier = if (focusRequester != null) {
        Modifier.focusRequester(focusRequester)
    } else {
        Modifier
    }

    val hasEpg = nowNext?.current != null || nowNext?.next != null

    Surface(
        modifier = modifier
            .width(232.dp)
            .height(if (hasEpg) 168.dp else 148.dp)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .then(focusModifier)
            .onFocusChanged { state ->
                isFocused = state.isFocused
                if (state.isFocused) onFocused()
            }
            .clip(RoundedCornerShape(16.dp))
            .combinedClickable(
                onClick = onClick,
                onLongClick = onToggleFavorite
            )
            .testTag("tv_channel_card_${channel.id}"),
        shape = RoundedCornerShape(16.dp),
        color = if (isFocused) CardSurfaceElevated else CardSurface,
        border = BorderStroke(if (isFocused) 2.5.dp else 1.dp, borderColor),
        tonalElevation = if (isFocused) 8.dp else 2.dp
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            // Область логотипа канала
            ChannelLogo(
                channelName = channel.name,
                logoUrl = channel.logoUrl,
                cornerRadius = 0.dp,
                contentPadding = 16.dp,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(94.dp)
            )

            // Нижний градиент с названием канала и строками «Сейчас / Далее»
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.BottomCenter)
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(
                                Color.Transparent,
                                ObsidianBg.copy(alpha = 0.94f),
                                ObsidianBg
                            )
                        )
                    )
                    .padding(horizontal = 12.dp, vertical = 10.dp)
            ) {
                Column {
                    Text(
                        text = channel.name,
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontWeight = if (isFocused) FontWeight.Bold else FontWeight.SemiBold,
                            color = TextPrimary
                        ),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )

                    if (hasEpg) {
                        Spacer(modifier = Modifier.height(3.dp))
                        ChannelEpgCompactBlock(
                            nowNext = nowNext,
                            currentTimeMs = currentTimeMs
                        )
                    } else {
                        Text(
                            text = channel.groupName,
                            style = MaterialTheme.typography.labelSmall.copy(
                                color = if (isFocused) WarmOrange else TextMuted
                            ),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }

            // Бейджи «Недавний» и звёздочка избранного
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(8.dp)
                    .align(Alignment.TopCenter),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (isLastWatched) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(WarmOrange)
                            .padding(horizontal = 7.dp, vertical = 3.dp)
                    ) {
                        Text(
                            text = stringResource(R.string.channels_last_watched_badge),
                            style = MaterialTheme.typography.labelSmall.copy(
                                color = ObsidianBg,
                                fontWeight = FontWeight.Bold
                            )
                        )
                    }
                } else {
                    Spacer(modifier = Modifier.width(1.dp))
                }

                if (channel.isFavorite) {
                    Box(
                        modifier = Modifier
                            .size(26.dp)
                            .clip(CircleShape)
                            .background(ObsidianBg.copy(alpha = 0.78f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Star,
                            contentDescription = stringResource(R.string.channels_remove_favorite),
                            tint = FavoriteGold,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }
        }
    }
}

/**
 * Компактная карточка телеканала для телефонного тач-режима (сетка и ряды) с блоком EPG.
 */
@Composable
fun PhoneChannelCard(
    channel: ChannelEntity,
    isLastWatched: Boolean,
    nowNext: ChannelNowNext? = null,
    currentTimeMs: Long = System.currentTimeMillis(),
    onClick: () -> Unit,
    onToggleFavorite: () -> Unit,
    modifier: Modifier = Modifier
) {
    val hasEpg = nowNext?.current != null || nowNext?.next != null

    Surface(
        onClick = onClick,
        modifier = modifier
            .fillMaxWidth()
            .testTag("phone_channel_card_${channel.id}"),
        shape = RoundedCornerShape(16.dp),
        color = if (isLastWatched) CardSurfaceElevated else CardSurface,
        border = BorderStroke(
            width = if (isLastWatched) 1.5.dp else 1.dp,
            color = if (isLastWatched) WarmOrange.copy(alpha = 0.7f) else OutlineSubtle
        ),
        tonalElevation = 2.dp
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(86.dp)
            ) {
                ChannelLogo(
                    channelName = channel.name,
                    logoUrl = channel.logoUrl,
                    cornerRadius = 12.dp,
                    contentPadding = 10.dp,
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(8.dp)
                )

                if (isLastWatched) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.TopStart)
                            .padding(10.dp)
                            .clip(RoundedCornerShape(6.dp))
                            .background(WarmOrangeSoft)
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = stringResource(R.string.channels_last_watched_badge),
                            style = MaterialTheme.typography.labelSmall.copy(
                                color = WarmOrange,
                                fontWeight = FontWeight.SemiBold
                            )
                        )
                    }
                }

                IconButton(
                    onClick = onToggleFavorite,
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .minimumInteractiveComponentSize()
                        .testTag("favorite_button_${channel.id}")
                ) {
                    Icon(
                        imageVector = if (channel.isFavorite) Icons.Filled.Star else Icons.Outlined.StarOutline,
                        contentDescription = stringResource(
                            if (channel.isFavorite) R.string.channels_remove_favorite
                            else R.string.channels_add_favorite
                        ),
                        tint = if (channel.isFavorite) FavoriteGold else TextSecondary,
                        modifier = Modifier.size(22.dp)
                    )
                }
            }

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 12.dp, end = 12.dp, bottom = 12.dp)
            ) {
                Text(
                    text = channel.name,
                    style = MaterialTheme.typography.titleMedium.copy(
                        color = TextPrimary,
                        fontWeight = FontWeight.SemiBold
                    ),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(3.dp))
                if (hasEpg) {
                    ChannelEpgCompactBlock(
                        nowNext = nowNext,
                        currentTimeMs = currentTimeMs
                    )
                } else {
                    Text(
                        text = channel.groupName,
                        style = MaterialTheme.typography.labelSmall.copy(color = TextMuted),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}

/**
 * Компактный блок «Сейчас идёт» и «Далее» под названием канала.
 */
@Composable
fun ChannelEpgCompactBlock(
    nowNext: ChannelNowNext?,
    currentTimeMs: Long,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        nowNext?.current?.let { current ->
            val duration = (current.endTimeMs - current.startTimeMs).coerceAtLeast(1L)
            val progress = ((currentTimeMs - current.startTimeMs).toFloat() / duration.toFloat())
                .coerceIn(0f, 1f)

            Text(
                text = stringResource(R.string.channels_now_playing, current.title),
                style = MaterialTheme.typography.labelSmall.copy(
                    color = WarmOrange,
                    fontWeight = FontWeight.Medium
                ),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
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

        nowNext?.next?.let { next ->
            Text(
                text = stringResource(
                    R.string.channels_next_playing,
                    RussianFormatters.formatShortTime(next.startTimeMs),
                    next.title
                ),
                style = MaterialTheme.typography.labelSmall.copy(color = TextSecondary),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}
