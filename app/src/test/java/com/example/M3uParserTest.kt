package com.example

import com.example.data.parser.M3uParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Модульные тесты для парсера M3U-плейлистов.
 */
class M3uParserTest {

    private val parser = M3uParser()

    @Test
    fun `разбор стандартного EXTINF с атрибутами и EXTGRP`() {
        val m3u = """
            #EXTM3U
            #EXTINF:-1 tvg-id="perviy.ru" tvg-logo="https://cdn.example.com/1tv.png" group-title="Федеральные",Первый канал
            https://stream.example.com/1tv/index.m3u8
            #EXTINF:-1 tvg-id="match" tvg-logo="https://cdn.example.com/match.png",Матч ТВ
            #EXTGRP:Спорт
            https://stream.example.com/match.m3u8
            #EXTINF:0,Канал, с запятой
            #EXTVLCOPT:http-user-agent=VLC
            http://stream.example.com/live/ts
        """.trimIndent()

        val channels = parser.parse(m3u)
        assertEquals(3, channels.size)

        val first = channels[0]
        assertEquals("Первый канал", first.name)
        assertEquals("perviy.ru", first.tvgId)
        assertEquals("https://cdn.example.com/1tv.png", first.logoUrl)
        assertEquals("Федеральные", first.groupName)
        assertEquals("https://stream.example.com/1tv/index.m3u8", first.streamUrl)

        val second = channels[1]
        assertEquals("Матч ТВ", second.name)
        assertEquals("Спорт", second.groupName)
        assertEquals("https://stream.example.com/match.m3u8", second.streamUrl)

        val third = channels[2]
        assertEquals("Канал, с запятой", third.name)
        assertNull(third.groupName)
        assertEquals("http://stream.example.com/live/ts", third.streamUrl)
    }

    @Test
    fun `разбор большого плейлиста на 3000 каналов`() {
        val builder = StringBuilder("#EXTM3U\n")
        for (i in 1..3000) {
            builder.append("#EXTINF:-1 tvg-id=\"id$i\" group-title=\"Группа ${i % 10}\",Канал №$i\n")
            builder.append("https://tv.example.com/ch$i/playlist.m3u8\n")
        }
        val channels = parser.parse(builder.toString())
        assertEquals(3000, channels.size)
        assertEquals("Канал №3000", channels.last().name)
    }
}
