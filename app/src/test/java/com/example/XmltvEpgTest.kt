package com.example

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.local.ChannelEntity
import com.example.data.local.ChannelGroupEntity
import com.example.data.local.EpgProgramEntity
import com.example.data.local.MelTiViDatabase
import com.example.data.local.PlaylistEntity
import com.example.data.parser.M3uParser
import com.example.data.parser.XmltvParser
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.zip.GZIPInputStream
import java.util.zip.GZIPOutputStream

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class XmltvEpgTest {

    private lateinit var context: Context
    private lateinit var db: MelTiViDatabase
    private val xmltvParser = XmltvParser()
    private val m3uParser = M3uParser()

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
        db = Room.inMemoryDatabaseBuilder(context, MelTiViDatabase::class.java)
            .allowMainThreadQueries()
            .build()
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun `парсинг XMLTV и сопоставление по tvg-id и точному названию канала`() {
        val xml = """
            <?xml version="1.0" encoding="UTF-8"?>
            <tv>
              <channel id="channel.one">
                <display-name>Первый</display-name>
              </channel>
              <channel id="numeric_id_99">
                <display-name>РБК</display-name>
              </channel>
              <channel id="Новости 24">
                <display-name>Канал 24</display-name>
              </channel>
              <programme start="20261114180000 +0300" stop="20261114190000 +0300" channel="channel.one">
                <title>Вечерние новости</title>
                <desc>Главные события дня</desc>
                <category>Новости</category>
              </programme>
              <programme start="20261114190000 +0300" stop="20261114203000 +0300" channel="numeric_id_99">
                <title>Рынки сегодня</title>
              </programme>
              <programme start="20261114200000 +0300" stop="20261114210000 +0300" channel="Новости 24">
                <title>Итоги недели</title>
              </programme>
            </tv>
        """.trimIndent()

        val parsed = xmltvParser.parse(xml)
        assertEquals(3, parsed.programs.size)

        val channels = listOf(
            ChannelEntity(
                id = 10L,
                playlistId = 1L,
                groupId = 1L,
                groupName = "Общие",
                name = "Первый канал HD",
                streamUrl = "https://example.com/1.m3u8",
                tvgId = "CHANNEL.ONE" // проверка сопоставления без учёта регистра
            ),
            ChannelEntity(
                id = 20L,
                playlistId = 1L,
                groupId = 1L,
                groupName = "Новости",
                name = "РБК",
                streamUrl = "https://example.com/2.m3u8",
                tvgId = "unknown_tvg_id" // tvg-id не совпал -> запасной поиск по display-name
            ),
            ChannelEntity(
                id = 30L,
                playlistId = 1L,
                groupId = 1L,
                groupName = "Новости",
                name = "Новости 24",
                streamUrl = "https://example.com/3.m3u8",
                tvgId = null // tvg-id отсутствует -> совпадение по XMLTV channel id
            )
        )

        val matched = xmltvParser.matchProgramsToChannels(parsed, channels)
        assertEquals(3, matched.size)

        val prog1 = matched.first { it.channelId == 10L }
        assertEquals("Вечерние новости", prog1.title)
        assertEquals("Главные события дня", prog1.description)
        assertEquals("Новости", prog1.category)
        assertEquals(3_600_000L, prog1.endTimeMs - prog1.startTimeMs)

        val prog2 = matched.first { it.channelId == 20L }
        assertEquals("Рынки сегодня", prog2.title)

        val prog3 = matched.first { it.channelId == 30L }
        assertEquals("Итоги недели", prog3.title)
    }

    @Test
    fun `распаковка и парсинг сжатого xml gz`() {
        val xml = """
            <?xml version="1.0" encoding="UTF-8"?>
            <tv>
              <channel id="ntv_ru">
                <display-name>НТВ</display-name>
              </channel>
              <programme start="20261114120000 +0000" stop="20261114130000 +0000" channel="ntv_ru">
                <title>Сегодня</title>
              </programme>
            </tv>
        """.trimIndent()

        val compressed = ByteArrayOutputStream().use { baos ->
            GZIPOutputStream(baos).use { gzip ->
                gzip.write(xml.toByteArray(Charsets.UTF_8))
            }
            baos.toByteArray()
        }

        val parsed = GZIPInputStream(ByteArrayInputStream(compressed)).use { gzipIn ->
            xmltvParser.parse(gzipIn)
        }

        assertEquals(1, parsed.programs.size)
        assertEquals("Сегодня", parsed.programs.first().title)
        assertEquals(listOf("НТВ"), parsed.channelDisplayNames["ntv_ru"])
    }

    @Test
    fun `извлечение url-tvg из заголовка EXTM3U`() {
        val m3u = """
            #EXTM3U url-tvg="https://example.com/epg.xml.gz"
            #EXTINF:-1 tvg-id="tv1" group-title="Общие",Первый
            https://example.com/tv1.m3u8
        """.trimIndent()

        val parsed = m3uParser.parseWithHeader(m3u.reader())
        assertEquals("https://example.com/epg.xml.gz", parsed.epgUrlFromHeader)
        assertEquals(1, parsed.channels.size)
    }

    @Test
    fun `кэширование телегида и обновление настроек плейлиста в Room`() = runTest {
        val dao = db.dao()
        val playlistId = dao.replacePlaylistContent(
            PlaylistEntity(
                name = "Домашний ТВ",
                url = "https://example.com/list.m3u",
                channelCount = 1,
                epgUrl = "https://example.com/epg.xml"
            ),
            listOf(
                ChannelGroupEntity(
                    playlistId = 0L,
                    name = "Кино",
                    channelCount = 1,
                    sortOrder = 0
                ) to listOf(
                    ChannelEntity(
                        playlistId = 0L,
                        groupId = 0L,
                        groupName = "Кино",
                        name = "Кино Премьера",
                        streamUrl = "https://example.com/kino.m3u8",
                        tvgId = "kino1"
                    )
                )
            )
        )

        val savedChannel = dao.getChannelsSync(playlistId).first()
        val now = 1_763_140_000_000L
        val programs = listOf(
            EpgProgramEntity(
                playlistId = playlistId,
                channelId = savedChannel.id,
                title = "Премьера фильма",
                description = "Драма",
                category = "Кино",
                startTimeMs = now - 600_000L,
                endTimeMs = now + 3_000_000L
            )
        )

        dao.replaceEpgForPlaylist(playlistId, programs, updatedAt = now)

        val cached = dao.observeEpgPrograms(playlistId).first()
        assertEquals(1, cached.size)
        assertEquals("Премьера фильма", cached.first().title)

        dao.updatePlaylistSettings(playlistId, "Обновлённый ТВ", "https://example.com/new.xml.gz")
        val updatedPlaylist = dao.getPlaylistById(playlistId)
        assertNotNull(updatedPlaylist)
        assertEquals("Обновлённый ТВ", updatedPlaylist?.name)
        assertEquals("https://example.com/new.xml.gz", updatedPlaylist?.epgUrl)
        assertEquals(now, updatedPlaylist?.epgUpdatedAt)
        assertTrue((updatedPlaylist?.epgUpdatedAt ?: 0L) > 0L)
    }
}
