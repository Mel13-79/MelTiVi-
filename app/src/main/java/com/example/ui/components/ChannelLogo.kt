package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import coil.request.CachePolicy
import coil.request.ImageRequest
import com.example.ui.theme.CardSurfaceElevated
import com.example.ui.theme.ObsidianSurface
import com.example.ui.theme.WarmAmber
import com.example.ui.theme.WarmOrange
import com.example.ui.theme.WarmOrangeSoft

/**
 * Ленивый загрузчик логотипа телеканала с дисковым и оперативным кэшированием Coil
 * и аккуратным запасным значком с инициалами канала.
 */
@Composable
fun ChannelLogo(
    channelName: String,
    logoUrl: String?,
    modifier: Modifier = Modifier,
    cornerRadius: Dp = 12.dp,
    contentPadding: Dp = 8.dp
) {
    val context = LocalContext.current
    var loadFailed by remember(logoUrl) { mutableStateOf(false) }
    val hasValidUrl = !logoUrl.isNullOrBlank() && !loadFailed

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(cornerRadius))
            .background(
                Brush.linearGradient(
                    colors = listOf(
                        CardSurfaceElevated,
                        ObsidianSurface
                    )
                )
            ),
        contentAlignment = Alignment.Center
    ) {
        if (hasValidUrl) {
            val imageRequest = remember(logoUrl) {
                ImageRequest.Builder(context)
                    .data(logoUrl)
                    .crossfade(true)
                    .memoryCachePolicy(CachePolicy.ENABLED)
                    .diskCachePolicy(CachePolicy.ENABLED)
                    .build()
            }
            AsyncImage(
                model = imageRequest,
                contentDescription = channelName,
                contentScale = ContentScale.Fit,
                onError = { loadFailed = true },
                modifier = Modifier
                    .fillMaxSize()
                    .padding(contentPadding)
            )
        } else {
            val initials = remember(channelName) { extractChannelInitials(channelName) }
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(WarmOrangeSoft)
                    .border(1.dp, WarmOrange.copy(alpha = 0.45f), RoundedCornerShape(14.dp)),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = initials,
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontWeight = FontWeight.Bold,
                        color = WarmAmber
                    ),
                    textAlign = TextAlign.Center,
                    maxLines = 1
                )
            }
        }
    }
}

private fun extractChannelInitials(name: String): String {
    val words = name.trim()
        .split(Regex("\\s+"))
        .filter { it.isNotBlank() }
    return when {
        words.isEmpty() -> "ТВ"
        words.size == 1 -> words[0].take(2).uppercase()
        else -> "${words[0].first()}${words[1].first()}".uppercase()
    }
}
