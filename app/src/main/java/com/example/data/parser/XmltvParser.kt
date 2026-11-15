package com.example.data.parser

import com.example.data.local.ChannelEntity
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserFactory
import java.io.InputStream
import java.io.StringReader
import java.util.Calendar
import java.util.TimeZone

/**
 * Распарсенная запись телепередачи из XMLTV до сопоставления с конкретным каналом в Room.
 */
data class ParsedEpgProgram(
    val xmltvChannelId: String,
    val title: String,
    val description: String?,
    val category: String?,
    val startTimeMs: Long,
    val endTimeMs: Long
)

/**
 * Результат разбора XMLTV: карта соответствия «xmltvId -> список display-name»
 * и список передач.
 */
data class ParsedXmltvData(
    val channelDisplayNames: Map<String, List<String>>,
    val programs: List<ParsedEpgProgram>
)

/**
 * Готовая передача, сопоставленная с каналом из Room.
 */
data class MatchedEpgProgram(
    val channelId: Long,
    val title: String,
    val description: String?,
    val category: String?,
    val startTimeMs: Long,
    val endTimeMs: Long
)

/**
 * Потоковый парсер телегида в формате XMLTV (поддерживает обычные .xml и распакованные .xml.gz).
 * Использует XmlPullParser для экономной работы с большими файлами.
 */
class XmltvParser {

    fun parse(xmlContent: String): ParsedXmltvData {
        val factory = XmlPullParserFactory.newInstance().apply {
            isNamespaceAware = false
        }
        val parser = factory.newPullParser()
        parser.setInput(StringReader(xmlContent))
        return parseInternal(parser)
    }

    fun parse(inputStream: InputStream): ParsedXmltvData {
        val factory = XmlPullParserFactory.newInstance().apply {
            isNamespaceAware = false
        }
        val parser = factory.newPullParser()
        parser.setInput(inputStream, null)
        return parseInternal(parser)
    }

    private fun parseInternal(parser: XmlPullParser): ParsedXmltvData {
        val channelNames = HashMap<String, MutableList<String>>()
        val programs = ArrayList<ParsedEpgProgram>()

        var currentXmltvChannelId: String? = null
        var inChannelTag = false

        var inProgrammeTag = false
        var progChannelId: String? = null
        var progStartMs: Long? = null
        var progStopMs: Long? = null
        var progTitle: String? = null
        var progDesc: String? = null
        var progCategory: String? = null

        var eventType = parser.eventType
        while (eventType != XmlPullParser.END_DOCUMENT) {
            when (eventType) {
                XmlPullParser.START_TAG -> {
                    when (parser.name.lowercase()) {
                        "channel" -> {
                            inChannelTag = true
                            currentXmltvChannelId = parser.getAttributeValue(null, "id")?.trim()
                        }
                        "display-name" -> {
                            if (inChannelTag && !currentXmltvChannelId.isNullOrBlank()) {
                                val text = safeNextText(parser).trim()
                                if (text.isNotEmpty()) {
                                    channelNames.getOrPut(currentXmltvChannelId) { ArrayList(2) }
                                        .add(text)
                                }
                            }
                        }
                        "programme" -> {
                            inProgrammeTag = true
                            progChannelId = parser.getAttributeValue(null, "channel")?.trim()
                            progStartMs = parseXmltvTimestamp(parser.getAttributeValue(null, "start"))
                            progStopMs = parseXmltvTimestamp(parser.getAttributeValue(null, "stop"))
                            progTitle = null
                            progDesc = null
                            progCategory = null
                        }
                        "title" -> {
                            if (inProgrammeTag && progTitle == null) {
                                val text = safeNextText(parser).trim()
                                if (text.isNotEmpty()) progTitle = text
                            }
                        }
                        "desc" -> {
                            if (inProgrammeTag && progDesc == null) {
                                val text = safeNextText(parser).trim()
                                if (text.isNotEmpty()) progDesc = text
                            }
                        }
                        "category" -> {
                            if (inProgrammeTag && progCategory == null) {
                                val text = safeNextText(parser).trim()
                                if (text.isNotEmpty()) progCategory = text
                            }
                        }
                    }
                }
                XmlPullParser.END_TAG -> {
                    when (parser.name.lowercase()) {
                        "channel" -> {
                            inChannelTag = false
                            currentXmltvChannelId = null
                        }
                        "programme" -> {
                            val chId = progChannelId
                            val start = progStartMs
                            val stop = progStopMs
                            val title = progTitle
                            if (!chId.isNullOrBlank() && start != null && !title.isNullOrBlank()) {
                                val resolvedStop = if (stop != null && stop > start) {
                                    stop
                                } else {
                                    start + 30 * 60 * 1000L
                                }
                                programs.add(
                                    ParsedEpgProgram(
                                        xmltvChannelId = chId,
                                        title = title,
                                        description = progDesc,
                                        category = progCategory,
                                        startTimeMs = start,
                                        endTimeMs = resolvedStop
                                    )
                                )
                            }
                            inProgrammeTag = false
                            progChannelId = null
                        }
                    }
                }
            }
            eventType = parser.next()
        }

        return ParsedXmltvData(
            channelDisplayNames = channelNames,
            programs = programs
        )
    }

    /**
     * Сопоставляет передачи из XMLTV каналам плейлиста:
     * 1. Сначала по tvg-id (без учёта регистра).
     * 2. Если tvg-id не задан или не совпал — по точному названию канала (display-name или id).
     */
    fun matchProgramsToChannels(
        xmltvData: ParsedXmltvData,
        channels: List<ChannelEntity>
    ): List<MatchedEpgProgram> {
        if (channels.isEmpty() || xmltvData.programs.isEmpty()) return emptyList()

        // Индексируем программы по нормализованному xmltvChannelId
        val programsByXmltvId = HashMap<String, MutableList<ParsedEpgProgram>>()
        for (program in xmltvData.programs) {
            programsByXmltvId.getOrPut(program.xmltvChannelId.trim().lowercase()) { ArrayList() }
                .add(program)
        }

        // Индексируем точные display-name -> список xmltvChannelId (без учёта регистра)
        val xmltvIdsByDisplayName = HashMap<String, MutableList<String>>()
        for ((xmltvId, names) in xmltvData.channelDisplayNames) {
            for (displayName in names) {
                val key = displayName.trim().lowercase()
                if (key.isNotEmpty()) {
                    xmltvIdsByDisplayName.getOrPut(key) { ArrayList(1) }
                        .add(xmltvId.trim().lowercase())
                }
            }
        }

        val matched = ArrayList<MatchedEpgProgram>()

        for (channel in channels) {
            val tvgIdKey = channel.tvgId?.trim()?.lowercase()?.takeIf { it.isNotEmpty() }
            var matchedPrograms: List<ParsedEpgProgram>? = null

            // Шаг 1: сопоставление по tvg-id
            if (tvgIdKey != null) {
                matchedPrograms = programsByXmltvId[tvgIdKey]
            }

            // Шаг 2: если tvg-id не совпал — ищем по точному названию канала
            if (matchedPrograms.isNullOrEmpty()) {
                val nameKey = channel.name.trim().lowercase()
                if (nameKey.isNotEmpty()) {
                    val candidateIds = xmltvIdsByDisplayName[nameKey]
                    if (!candidateIds.isNullOrEmpty()) {
                        for (candidateId in candidateIds) {
                            val progs = programsByXmltvId[candidateId]
                            if (!progs.isNullOrEmpty()) {
                                matchedPrograms = progs
                                break
                            }
                        }
                    }
                    // Запасной вариант: некоторые генераторы XMLTV используют само название как channel id
                    if (matchedPrograms.isNullOrEmpty()) {
                        matchedPrograms = programsByXmltvId[nameKey]
                    }
                }
            }

            if (!matchedPrograms.isNullOrEmpty()) {
                for (prog in matchedPrograms) {
                    matched.add(
                        MatchedEpgProgram(
                            channelId = channel.id,
                            title = prog.title,
                            description = prog.description,
                            category = prog.category,
                            startTimeMs = prog.startTimeMs,
                            endTimeMs = prog.endTimeMs
                        )
                    )
                }
            }
        }

        return matched
    }

    private fun safeNextText(parser: XmlPullParser): String {
        return try {
            parser.nextText()
        } catch (_: Exception) {
            ""
        }
    }

    companion object {
        /**
         * Разбирает временную метку XMLTV (например, "20261114183000 +0300" или "20261114183000").
         */
        fun parseXmltvTimestamp(raw: String?): Long? {
            if (raw.isNullOrBlank()) return null
            val trimmed = raw.trim()
            val spaceIdx = trimmed.indexOf(' ')
            val datePart = if (spaceIdx > 0) trimmed.substring(0, spaceIdx) else trimmed
            val offsetPart = if (spaceIdx > 0) trimmed.substring(spaceIdx + 1).trim() else ""

            if (datePart.length < 12) return null
            val year = datePart.substring(0, 4).toIntOrNull() ?: return null
            val month = datePart.substring(4, 6).toIntOrNull() ?: return null
            val day = datePart.substring(6, 8).toIntOrNull() ?: return null
            val hour = datePart.substring(8, 10).toIntOrNull() ?: return null
            val minute = datePart.substring(10, 12).toIntOrNull() ?: return null
            val second = if (datePart.length >= 14) {
                datePart.substring(12, 14).toIntOrNull() ?: 0
            } else {
                0
            }

            val timeZone = parseTimeZone(offsetPart)
            val calendar = Calendar.getInstance(timeZone).apply {
                clear()
                set(year, month - 1, day, hour, minute, second)
                set(Calendar.MILLISECOND, 0)
            }
            return calendar.timeInMillis
        }

        private fun parseTimeZone(offset: String): TimeZone {
            if (offset.isEmpty()) return TimeZone.getDefault()
            if (offset.equals("UTC", ignoreCase = true) || offset.equals("Z", ignoreCase = true)) {
                return TimeZone.getTimeZone("UTC")
            }
            val normalized = if (offset.startsWith("+") || offset.startsWith("-")) {
                "GMT$offset"
            } else {
                "GMT+$offset"
            }
            return TimeZone.getTimeZone(normalized)
        }
    }
}
