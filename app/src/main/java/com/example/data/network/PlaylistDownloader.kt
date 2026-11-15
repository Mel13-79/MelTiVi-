package com.example.data.network

import android.content.Context
import com.example.R
import com.example.data.parser.M3uParser
import com.example.data.parser.ParsedM3uPlaylist
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import java.util.concurrent.TimeUnit

/**
 * Исключение с понятным русским сообщением при ошибке загрузки или разбора плейлиста.
 */
class PlaylistLoadException(message: String, cause: Throwable? = null) : Exception(message, cause)

/**
 * Сетевой загрузчик M3U/M3U8-плейлистов по URL через OkHttp с потоковым разбором.
 */
class PlaylistDownloader(
    private val context: Context,
    private val parser: M3uParser = M3uParser()
) {

    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(45, TimeUnit.SECONDS)
        .followRedirects(true)
        .followSslRedirects(true)
        .build()

    /**
     * Скачивает файл плейлиста по указанной ссылке и возвращает распознанные каналы и ссылку EPG.
     */
    suspend fun downloadAndParse(rawUrl: String): ParsedM3uPlaylist = withContext(Dispatchers.IO) {
        val url = rawUrl.trim()
        if (url.isEmpty()) {
            throw PlaylistLoadException(context.getString(R.string.error_empty_url))
        }
        val lowerUrl = url.lowercase()
        if (!lowerUrl.startsWith("http://") && !lowerUrl.startsWith("https://")) {
            throw PlaylistLoadException(context.getString(R.string.error_invalid_url))
        }

        val request = try {
            Request.Builder()
                .url(url)
                .header("User-Agent", "MelTiVi/1.0 (Android IPTV Player)")
                .get()
                .build()
        } catch (e: IllegalArgumentException) {
            throw PlaylistLoadException(context.getString(R.string.error_invalid_url), e)
        }

        try {
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    throw PlaylistLoadException(
                        context.getString(R.string.error_http_playlist, response.code)
                    )
                }
                val body = response.body
                    ?: throw PlaylistLoadException(context.getString(R.string.error_network_playlist))

                // Потоково читаем тело ответа без загрузки всего файла в одну строку
                val parsed = body.charStream().use { reader ->
                    parser.parseWithHeader(reader)
                }
                if (parsed.channels.isEmpty()) {
                    throw PlaylistLoadException(context.getString(R.string.error_empty_playlist))
                }
                parsed
            }
        } catch (e: PlaylistLoadException) {
            throw e
        } catch (e: SocketTimeoutException) {
            throw PlaylistLoadException(context.getString(R.string.error_timeout_playlist), e)
        } catch (e: UnknownHostException) {
            throw PlaylistLoadException(context.getString(R.string.error_network_playlist), e)
        } catch (e: IOException) {
            throw PlaylistLoadException(context.getString(R.string.error_network_playlist), e)
        } catch (e: Exception) {
            throw PlaylistLoadException(context.getString(R.string.error_network_playlist), e)
        }
    }
}
