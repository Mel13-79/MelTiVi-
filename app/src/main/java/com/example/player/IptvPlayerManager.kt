package com.example.player

import android.content.Context
import androidx.annotation.OptIn
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.ui.AspectRatioFrameLayout
import com.example.R
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Режим масштабирования видео в плеере.
 */
enum class VideoAspectMode(val resizeMode: Int, val labelRes: Int) {
    FIT(AspectRatioFrameLayout.RESIZE_MODE_FIT, R.string.player_aspect_fit),
    FILL(AspectRatioFrameLayout.RESIZE_MODE_ZOOM, R.string.player_aspect_fill),
    STRETCH(AspectRatioFrameLayout.RESIZE_MODE_FILL, R.string.player_aspect_zoom)
}

/**
 * Состояние воспроизведения потока в ExoPlayer.
 */
data class PlayerPlaybackState(
    val isPlaying: Boolean = false,
    val isBuffering: Boolean = true,
    val errorMessage: String? = null,
    val aspectMode: VideoAspectMode = VideoAspectMode.FIT
)

/**
 * Контроллер Media3 ExoPlayer с поддержкой HLS (m3u8), прямых IPTV-потоков и редиректов.
 */
class IptvPlayerManager(private val context: Context) {

    private val _state = MutableStateFlow(PlayerPlaybackState())
    val state: StateFlow<PlayerPlaybackState> = _state.asStateFlow()

    val exoPlayer: ExoPlayer = createExoPlayer(context)

    private var currentStreamUrl: String? = null

    private val playerListener = object : Player.Listener {
        override fun onPlaybackStateChanged(playbackState: Int) {
            when (playbackState) {
                Player.STATE_BUFFERING -> {
                    _state.value = _state.value.copy(
                        isBuffering = true,
                        errorMessage = null
                    )
                }
                Player.STATE_READY -> {
                    _state.value = _state.value.copy(
                        isBuffering = false,
                        isPlaying = exoPlayer.playWhenReady,
                        errorMessage = null
                    )
                }
                Player.STATE_ENDED -> {
                    _state.value = _state.value.copy(
                        isBuffering = false,
                        isPlaying = false
                    )
                }
                Player.STATE_IDLE -> {
                    _state.value = _state.value.copy(isBuffering = false)
                }
            }
        }

        override fun onIsPlayingChanged(isPlaying: Boolean) {
            _state.value = _state.value.copy(isPlaying = isPlaying)
        }

        override fun onPlayerError(error: PlaybackException) {
            val localizedMessage = when (error.errorCode) {
                PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED,
                PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_TIMEOUT,
                PlaybackException.ERROR_CODE_IO_BAD_HTTP_STATUS,
                PlaybackException.ERROR_CODE_IO_UNSPECIFIED -> {
                    context.getString(R.string.player_error_network)
                }
                else -> {
                    context.getString(R.string.player_error_format)
                }
            }
            _state.value = _state.value.copy(
                isBuffering = false,
                isPlaying = false,
                errorMessage = localizedMessage
            )
        }
    }

    init {
        exoPlayer.addListener(playerListener)
    }

    @OptIn(UnstableApi::class)
    private fun createExoPlayer(context: Context): ExoPlayer {
        val httpDataSourceFactory = DefaultHttpDataSource.Factory()
            .setUserAgent("MelTiVi/1.0 (Android; ExoPlayer)")
            .setAllowCrossProtocolRedirects(true)
            .setConnectTimeoutMs(15_000)
            .setReadTimeoutMs(20_000)

        val mediaSourceFactory = DefaultMediaSourceFactory(context)
            .setDataSourceFactory(httpDataSourceFactory)

        val audioAttributes = AudioAttributes.Builder()
            .setUsage(C.USAGE_MEDIA)
            .setContentType(C.AUDIO_CONTENT_TYPE_MOVIE)
            .build()

        return ExoPlayer.Builder(context)
            .setMediaSourceFactory(mediaSourceFactory)
            .setAudioAttributes(audioAttributes, true)
            .setHandleAudioBecomingNoisy(true)
            .build()
    }

    /**
     * Запускает воспроизведение указанного URL (включая потоки HLS .m3u8).
     */
    fun playUrl(url: String) {
        currentStreamUrl = url
        _state.value = _state.value.copy(
            isBuffering = true,
            errorMessage = null
        )

        val mediaItemBuilder = MediaItem.Builder().setUri(url)
        // Явно указываем MIME-тип для HLS-потоков, если ссылка содержит .m3u8
        if (url.contains(".m3u8", ignoreCase = true)) {
            mediaItemBuilder.setMimeType(MimeTypes.APPLICATION_M3U8)
        }

        exoPlayer.setMediaItem(mediaItemBuilder.build())
        exoPlayer.prepare()
        exoPlayer.playWhenReady = true
    }

    fun retry() {
        currentStreamUrl?.let { playUrl(it) }
    }

    fun togglePlayPause() {
        if (exoPlayer.isPlaying) {
            exoPlayer.pause()
        } else {
            if (exoPlayer.playbackState == Player.STATE_IDLE ||
                exoPlayer.playbackState == Player.STATE_ENDED
            ) {
                retry()
            } else {
                exoPlayer.play()
            }
        }
    }

    fun cycleAspectMode() {
        val modes = VideoAspectMode.entries
        val next = modes[(_state.value.aspectMode.ordinal + 1) % modes.size]
        _state.value = _state.value.copy(aspectMode = next)
    }

    fun stop() {
        exoPlayer.stop()
        exoPlayer.clearMediaItems()
    }

    fun release() {
        exoPlayer.removeListener(playerListener)
        exoPlayer.release()
    }
}
