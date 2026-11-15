package com.example

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.local.ChannelEntity
import com.example.data.local.ChannelGroupEntity
import com.example.data.local.MelTiViDatabase
import com.example.data.local.PlaylistEntity
import com.example.ui.AppUiMode
import com.example.ui.detectUiMode
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ExampleRobolectricTest {

    private lateinit var context: Context
    private lateinit var db: MelTiViDatabase

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
    fun `проверка имени приложения и режима по умолчанию`() {
        val appName = context.getString(R.string.app_name)
        assertEquals("MelTiVi", appName)
        assertEquals(AppUiMode.PhoneUi, detectUiMode(context))
    }

    @Test
    fun `сохранение плейлиста групп каналов и избранного в Room`() = runTest {
        val dao = db.dao()
        val playlist = PlaylistEntity(
            name = "Тест ТВ",
            url = "https://example.com/list.m3u",
            channelCount = 2
        )
        val group = ChannelGroupEntity(
            playlistId = 0L,
            name = "Новости",
            channelCount = 2,
            sortOrder = 0
        )
        val channels = listOf(
            ChannelEntity(
                playlistId = 0L,
                groupId = 0L,
                groupName = "Новости",
                name = "Россия 24",
                streamUrl = "https://example.com/r24.m3u8",
                sortOrder = 0
            ),
            ChannelEntity(
                playlistId = 0L,
                groupId = 0L,
                groupName = "Новости",
                name = "РБК",
                streamUrl = "https://example.com/rbc.m3u8",
                sortOrder = 1
            )
        )

        val playlistId = dao.replacePlaylistContent(playlist, listOf(group to channels))
        val savedChannels = dao.observeChannels(playlistId).first()
        assertEquals(2, savedChannels.size)

        dao.updateFavorite(savedChannels.first().id, true)
        val favorites = dao.observeFavoriteChannels(playlistId).first()
        assertEquals(1, favorites.size)
        assertTrue(favorites.first().isFavorite)
        assertEquals("Россия 24", favorites.first().name)
    }
}
