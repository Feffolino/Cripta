package com.cripta.app.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import net.sqlcipher.database.SQLiteDatabase
import net.sqlcipher.database.SupportFactory

@Database(
    entities = [FolderEntity::class, FileEntity::class, TagEntity::class, FileTagCrossRef::class, SavedFilterEntity::class],
    version = 12,
    exportSchema = false,
)
abstract class CriptaDatabase : RoomDatabase() {
    abstract fun folderDao(): FolderDao
    abstract fun fileDao(): FileDao
    abstract fun tagDao(): TagDao
    abstract fun savedFilterDao(): SavedFilterDao

    companion object {
        private const val NAME = "cripta.db"

        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE tags ADD COLUMN alias TEXT")
            }
        }

        private val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE files ADD COLUMN durationMs INTEGER")
            }
        }

        private val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE files ADD COLUMN sortWeight INTEGER NOT NULL DEFAULT 0")
                // Seed the manual order with import time so it starts sensible before the user drags.
                db.execSQL("UPDATE files SET sortWeight = importedAt")
            }
        }

        private val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE folders ADD COLUMN color INTEGER")
                db.execSQL("ALTER TABLE folders ADD COLUMN emoji TEXT")
            }
        }

        private val MIGRATION_5_6 = object : Migration(5, 6) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE tags ADD COLUMN orderIndex INTEGER NOT NULL DEFAULT 0")
                // Seed a stable initial order (by id) before the user customizes it.
                db.execSQL("UPDATE tags SET orderIndex = id")
            }
        }

        private val MIGRATION_6_7 = object : Migration(6, 7) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE files ADD COLUMN sourceUrl TEXT")
            }
        }

        private val MIGRATION_7_8 = object : Migration(7, 8) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE files ADD COLUMN width INTEGER")
                db.execSQL("ALTER TABLE files ADD COLUMN height INTEGER")
            }
        }

        private val MIGRATION_8_9 = object : Migration(8, 9) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE files ADD COLUMN playbackPosMs INTEGER")
                db.execSQL("ALTER TABLE files ADD COLUMN deletedAt INTEGER")
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `saved_filters` (" +
                        "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `name` TEXT NOT NULL, `json` TEXT NOT NULL)"
                )
            }
        }

        private val MIGRATION_9_10 = object : Migration(9, 10) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE tags ADD COLUMN color INTEGER")
            }
        }

        private val MIGRATION_10_11 = object : Migration(10, 11) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE tags ADD COLUMN pinned INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE tags ADD COLUMN lastUsedAt INTEGER")
            }
        }

        private val MIGRATION_11_12 = object : Migration(11, 12) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE files ADD COLUMN lastPlayedAt INTEGER")
            }
        }

        /** Delete the encrypted database file (and its -wal/-shm siblings). Used on vault reset. */
        fun deleteDatabase(context: Context) {
            context.deleteDatabase(NAME)
        }

        /** Open the encrypted DB with the given raw passphrase (SQLCipher). */
        fun open(context: Context, passphrase: ByteArray): CriptaDatabase {
            SQLiteDatabase.loadLibs(context)
            val factory = SupportFactory(passphrase.copyOf())
            return Room.databaseBuilder(context, CriptaDatabase::class.java, NAME)
                .openHelperFactory(factory)
                .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6, MIGRATION_6_7, MIGRATION_7_8, MIGRATION_8_9, MIGRATION_9_10, MIGRATION_10_11, MIGRATION_11_12)
                // No destructive fallback: a missing migration must fail loudly, never wipe the vault.
                .build()
        }
    }
}
