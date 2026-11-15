package com.example.data.parser

import java.io.BufferedReader
import java.io.Reader
import java.io.StringReader

/**
 * Распарсенный элемент телеканала из M3U-плейлиста.
 */
data class ParsedChannel(
    val name: String,
    val streamUrl: String,
    val logoUrl: String?,
    val groupName: String?,
    val tvgId: String?,
    val sortOrder: Int
)

/**
 * Результат разбора M3U-плейлиста: список каналов и необязательная ссылка EPG из #EXTM3U.
 */
data class ParsedM3uPlaylist(
    val channels: List<ParsedChannel>,
    val epgUrlFromHeader: String? = null
)

/**
 * Потоковый парсер плейлистов в формате M3U / M3U8.
 * Поддерживает теги #EXTINF (tvg-id, tvg-logo, group-title, название после запятой)
 * и #EXTGRP, а также большие файлы на тысячи каналов.
 */
class M3uParser {

    /**
     * Разбирает M3U-содержимое из строки.
     */
    fun parse(content: String): List<ParsedChannel> {
        return parse(StringReader(content))
    }

    /**
     * Построчно разбирает M3U-плейлист из потока чтения без лишней нагрузки на память.
     */
    fun parse(reader: Reader): List<ParsedChannel> = parseWithHeader(reader).channels

    /**
     * Построчно разбирает M3U-плейлист и дополнительно извлекает ссылку на EPG (url-tvg / x-tvg-url).
     */
    fun parseWithHeader(reader: Reader): ParsedM3uPlaylist {
        val channels = ArrayList<ParsedChannel>()
        val bufferedReader = reader as? BufferedReader ?: BufferedReader(reader, 16 * 1024)

        var headerEpgUrl: String? = null
        var pendingName: String? = null
        var pendingLogo: String? = null
        var pendingGroup: String? = null
        var pendingTvgId: String? = null
        var hasExtInf = false
        var orderIndex = 0

        bufferedReader.useLines { lines ->
            for (rawLine in lines) {
                // Удаляем BOM-символ и лишние пробелы по краям
                val line = rawLine.removePrefix("\uFEFF").trim()
                if (line.isEmpty()) continue

                when {
                    line.startsWith("#EXTM3U", ignoreCase = true) -> {
                        val headerAttrs = parseAttributes(line.substringAfter("#EXTM3U", "").trim())
                        val candidateEpg = (headerAttrs["url-tvg"] ?: headerAttrs["x-tvg-url"])
                            ?.trim()
                            ?.substringBefore(',')
                            ?.takeIf { it.startsWith("http://", ignoreCase = true) || it.startsWith("https://", ignoreCase = true) }
                        if (candidateEpg != null && headerEpgUrl == null) {
                            headerEpgUrl = candidateEpg
                        }
                        continue
                    }
                    line.startsWith("#EXTINF:", ignoreCase = true) -> {
                        val infoPayload = line.substringAfter(":", "")
                        val (attrsPart, titlePart) = splitExtInfHeaderAndTitle(infoPayload)
                        val attrs = parseAttributes(attrsPart)

                        pendingTvgId = attrs["tvg-id"]?.takeIf { it.isNotBlank() }
                        pendingLogo = attrs["tvg-logo"]?.takeIf { it.isNotBlank() }
                        val attrGroup = attrs["group-title"]?.takeIf { it.isNotBlank() }
                        if (attrGroup != null) {
                            pendingGroup = attrGroup
                        }
                        val tvgName = attrs["tvg-name"]?.takeIf { it.isNotBlank() }
                        pendingName = titlePart.trim().ifEmpty { tvgName ?: "" }
                        hasExtInf = true
                    }
                    line.startsWith("#EXTGRP:", ignoreCase = true) -> {
                        val grp = line.substringAfter(":", "").trim()
                        if (grp.isNotEmpty()) {
                            pendingGroup = grp
                        }
                    }
                    line.startsWith("#") -> {
                        // Прочие служебные теги (#EXTVLCOPT, #KODIPROP и др.) игнорируем, сохраняя текущие метаданные
                        continue
                    }
                    else -> {
                        // Строка с адресом потока
                        val url = line
                        if (isValidStreamLine(url)) {
                            val resolvedName = pendingName?.takeIf { it.isNotBlank() }
                                ?: extractFallbackName(url, orderIndex + 1)
                            channels.add(
                                ParsedChannel(
                                    name = resolvedName,
                                    streamUrl = url,
                                    logoUrl = pendingLogo,
                                    groupName = pendingGroup?.takeIf { it.isNotBlank() },
                                    tvgId = pendingTvgId,
                                    sortOrder = orderIndex++
                                )
                            )
                        }
                        // Сбрасываем накопленные теги для следующего канала
                        pendingName = null
                        pendingLogo = null
                        pendingGroup = null
                        pendingTvgId = null
                        hasExtInf = false
                    }
                }
            }
        }
        return ParsedM3uPlaylist(
            channels = channels,
            epgUrlFromHeader = headerEpgUrl
        )
    }

    /**
     * Разделяет часть с атрибутами #EXTINF и название канала после первой запятой вне кавычек.
     */
    private fun splitExtInfHeaderAndTitle(payload: String): Pair<String, String> {
        var inDoubleQuotes = false
        var inSingleQuotes = false
        for (i in payload.indices) {
            when (val ch = payload[i]) {
                '"' -> if (!inSingleQuotes) inDoubleQuotes = !inDoubleQuotes
                '\'' -> if (!inDoubleQuotes) inSingleQuotes = !inSingleQuotes
                ',' -> if (!inDoubleQuotes && !inSingleQuotes) {
                    return payload.substring(0, i) to payload.substring(i + 1)
                }
            }
        }
        return payload to ""
    }

    /**
     * Извлекает пары ключ-значение атрибутов формата key="value" или key='value'.
     */
    private fun parseAttributes(header: String): Map<String, String> {
        if (header.isBlank()) return emptyMap()
        val result = HashMap<String, String>()
        var index = 0
        val length = header.length

        while (index < length) {
            while (index < length && header[index].isWhitespace()) index++
            if (index >= length) break

            val keyStart = index
            while (index < length && header[index] != '=' && !header[index].isWhitespace()) {
                index++
            }
            val key = header.substring(keyStart, index).lowercase()

            while (index < length && header[index].isWhitespace()) index++
            if (index >= length || header[index] != '=') {
                continue
            }
            index++ // Пропускаем '='
            while (index < length && header[index].isWhitespace()) index++
            if (index >= length) break

            val value: String
            val quote = header[index]
            if (quote == '"' || quote == '\'') {
                index++
                val valueStart = index
                while (index < length && header[index] != quote) {
                    index++
                }
                value = header.substring(valueStart, index)
                if (index < length) index++
            } else {
                val valueStart = index
                while (index < length && !header[index].isWhitespace()) {
                    index++
                }
                value = header.substring(valueStart, index)
            }

            if (key.isNotEmpty()) {
                result[key] = value.trim()
            }
        }
        return result
    }

    private fun isValidStreamLine(line: String): Boolean {
        val lower = line.lowercase()
        return lower.startsWith("http://") ||
            lower.startsWith("https://") ||
            lower.startsWith("rtmp://") ||
            lower.startsWith("rtsp://") ||
            lower.startsWith("udp://") ||
            lower.startsWith("rtp://") ||
            lower.startsWith("mms://")
    }

    private fun extractFallbackName(url: String, number: Int): String {
        val segment = url.substringAfterLast('/', "")
            .substringBefore('?')
            .substringBefore('.')
            .trim()
        return if (segment.isNotEmpty() && segment.length <= 40) {
            segment
        } else {
            "Канал $number"
        }
    }
}
