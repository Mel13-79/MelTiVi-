package com.example.data.local

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Таблица сохранённых IPTV-плейлистов (включая ссылку на XMLTV-телегид).
 */
@Entity(tableName = "playlists")
data class PlaylistEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0L,
    val name: String,
    val url: String,
    @ColumnInfo(name = "channel_count")
    val channelCount: Int = 0,
    @ColumnInfo(name = "updated_at")
    val updatedAt: Long = System.currentTimeMillis(),
    @ColumnInfo(name = "epg_url")
    val epgUrl: String? = null,
    @ColumnInfo(name = "epg_updated_at")
    val epgUpdatedAt: Long? = null
)

/**
 * Таблица групп (категорий) каналов внутри плейлиста.
 */
@Entity(
    tableName = "channel_groups",
    foreignKeys = [
        ForeignKey(
            entity = PlaylistEntity::class,
            parentColumns = ["id"],
            childColumns = ["playlist_id"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index(value = ["playlist_id", "sort_order"])
    ]
)
data class ChannelGroupEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0L,
    @ColumnInfo(name = "playlist_id")
    val playlistId: Long,
    val name: String,
    @ColumnInfo(name = "channel_count")
    val channelCount: Int = 0,
    @ColumnInfo(name = "sort_order")
    val sortOrder: Int = 0
)

/**
 * Таблица телеканалов с метаданными из M3U и флагом избранного.
 */
@Entity(
    tableName = "channels",
    foreignKeys = [
        ForeignKey(
            entity = PlaylistEntity::class,
            parentColumns = ["id"],
            childColumns = ["playlist_id"],
            onDelete = ForeignKey.CASCADE
        ),
        ForeignKey(
            entity = ChannelGroupEntity::class,
            parentColumns = ["id"],
            childColumns = ["group_id"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index(value = ["playlist_id", "sort_order"]),
        Index(value = ["group_id"]),
        Index(value = ["playlist_id", "group_id", "sort_order"]),
        Index(value = ["playlist_id", "is_favorite"]),
        Index(value = ["playlist_id", "name"])
    ]
)
data class ChannelEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0L,
    @ColumnInfo(name = "playlist_id")
    val playlistId: Long,
    @ColumnInfo(name = "group_id")
    val groupId: Long,
    @ColumnInfo(name = "group_name")
    val groupName: String,
    val name: String,
    @ColumnInfo(name = "stream_url")
    val streamUrl: String,
    @ColumnInfo(name = "logo_url")
    val logoUrl: String? = null,
    @ColumnInfo(name = "tvg_id")
    val tvgId: String? = null,
    @ColumnInfo(name = "is_favorite")
    val isFavorite: Boolean = false,
    @ColumnInfo(name = "sort_order")
    val sortOrder: Int = 0
)

/**
 * Таблица кэшированных передач телегида (EPG), привязанных к каналам плейлиста.
 */
@Entity(
    tableName = "epg_programs",
    foreignKeys = [
        ForeignKey(
            entity = PlaylistEntity::class,
            parentColumns = ["id"],
            childColumns = ["playlist_id"],
            onDelete = ForeignKey.CASCADE
        ),
        ForeignKey(
            entity = ChannelEntity::class,
            parentColumns = ["id"],
            childColumns = ["channel_id"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index(value = ["playlist_id", "channel_id", "start_time_ms"]),
        Index(value = ["channel_id"])
    ]
)
data class EpgProgramEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0L,
    @ColumnInfo(name = "playlist_id")
    val playlistId: Long,
    @ColumnInfo(name = "channel_id")
    val channelId: Long,
    val title: String,
    val description: String? = null,
    val category: String? = null,
    @ColumnInfo(name = "start_time_ms")
    val startTimeMs: Long,
    @ColumnInfo(name = "end_time_ms")
    val endTimeMs: Long
)
