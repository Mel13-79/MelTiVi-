package com.example.data.network

import android.content.Context
import com.example.R
import com.example.data.parser.ParsedEpgProgram
import com.example.data.parser.XmltvParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.BufferedInputStream
import java.io.IOException
import java.io.InputStream
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import java.util.concurrent.TimeUnit
import java.util.zip.GZIPInputStream

/**
 * Исключение с понятным русским сообщением при ошибке загрузки или разбора телегида XMLTV.
 */
class EpgLoadException(message: String, cause: Throwable? = null) : Exception(message, cause)

/**
 * Сетевой загрузчик XMLTV-файлов (.xml и .xml.gz) по URL.
 * Автоматически определяет GZIP-сжатие по заголовкам, расширению или сигнатуре потока.
 */
class EpgDownloader(
    private val context: Context,
    private val parser: XmltvParser = XmltvParser()
) {

    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(25, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .followRedirects(true)
        .followSslRedirects(true)
        .build()

    suspend fun downloadAndParse(
        rawUrl: String,
        onProgress: ((bytesRead: Long, totalBytes: Long) -> Unit)? = null,
        onBatch: suspend (batch: List<ParsedEpgProgram>, channelDisplayNames: Map<String, List<String>>) -> Unit
    ): Map<String, List<String>> = withContext(Dispatchers.IO) {
        val url = rawUrl.trim()
        if (url.isEmpty()) {
            throw EpgLoadException(context.getString(R.string.error_empty_epg_url))
        }
        val lower = url.lowercase()
        if (!lower.startsWith("http://") && !lower.startsWith("https://")) {
            throw EpgLoadException(context.getString(R.string.error_invalid_url))
        }

        val request = try {
            Request.Builder()
                .url(url)
                .header("User-Agent", "MelTiVi/1.0 (Android IPTV Player)")
                .get()
                .build()
        } catch (e: IllegalArgumentException) {
            throw EpgLoadException(context.getString(R.string.error_invalid_url), e)
        }

        try {
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    throw EpgLoadException(
                        context.getString(R.string.error_http_epg, response.code)
                    )
                }
                val body = response.body
                    ?: throw EpgLoadException(context.getString(R.string.error_network_epg))

                val contentLength = body.contentLength()
                val contentType = response.header("Content-Type").orEmpty()
                val buffered = BufferedInputStream(body.byteStream(), 16 * 1024)
                buffered.mark(4)
                val firstByte = buffered.read()
                val secondByte = buffered.read()
                buffered.reset()

                val isGzipMagic = (firstByte == 0x1f && secondByte == 0x8b)
                val isGzip = isGzipMagic ||
                    lower.endsWith(".gz") ||
                    contentType.contains("gzip", ignoreCase = true)

                // Обёртка с подсчётом прочитанных байт (для отображения прогресса загрузки).
                val source: InputStream = if (onProgress != null) {
                    ProgressInputStream(buffered, contentLength, onProgress)
                } else {
                    buffered
                }

                // Для gzip считаем сжатые байты из недр GZIPInputStream, поэтому обёртка стоит под ним.
                val stream = if (isGzip) {
                    GZIPInputStream(source)
                } else {
                    source
                }

                stream.use { xmlStream ->
                    parser.parseStreaming(xmlStream, onBatch = onBatch)
                }
            }
        } catch (e: EpgLoadException) {
            throw e
        } catch (e: SocketTimeoutException) {
            throw EpgLoadException(context.getString(R.string.error_timeout_playlist), e)
        } catch (e: UnknownHostException) {
            throw EpgLoadException(context.getString(R.string.error_network_epg), e)
        } catch (e: IOException) {
            throw EpgLoadException(context.getString(R.string.error_network_epg), e)
        } catch (e: Exception) {
            throw EpgLoadException(context.getString(R.string.error_parse_epg), e)
        }
    }
}
