package com.example.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * Локальная база данных Room для хранения плейлистов, групп, каналов и кэша телегида (EPG).
 */
@Database(
    entities = [
        PlaylistEntity::class,
        ChannelGroupEntity::class,
        ChannelEntity::class,
        EpgProgramEntity::class
    ],
    version = 2,
    exportSchema = false
)
abstract class MelTiViDatabase : RoomDatabase() {

    abstract fun dao(): MelTiViDao

    companion object {
        @Volatile
        private var instance: MelTiViDatabase? = null

        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE playlists ADD COLUMN epg_url TEXT")
                db.execSQL("ALTER TABLE playlists ADD COLUMN epg_updated_at INTEGER")
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `epg_programs` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `playlist_id` INTEGER NOT NULL,
                        `channel_id` INTEGER NOT NULL,
                        `title` TEXT NOT NULL,
                        `description` TEXT,
                        `category` TEXT,
                        `start_time_ms` INTEGER NOT NULL,
                        `end_time_ms` INTEGER NOT NULL,
                        FOREIGN KEY(`playlist_id`) REFERENCES `playlists`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE,
                        FOREIGN KEY(`channel_id`) REFERENCES `channels`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE
                    )
                    """.trimIndent()
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_epg_programs_playlist_id_channel_id_start_time_ms` ON `epg_programs` (`playlist_id`, `channel_id`, `start_time_ms`)"
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_epg_programs_channel_id` ON `epg_programs` (`channel_id`)"
                )
            }
        }

        fun getInstance(context: Context): MelTiViDatabase {
            return instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    MelTiViDatabase::class.java,
                    "meltivi_database.db"
                )
                    .addMigrations(MIGRATION_1_2)
                    .build()
                    .also { instance = it }
            }
        }
    }
}
