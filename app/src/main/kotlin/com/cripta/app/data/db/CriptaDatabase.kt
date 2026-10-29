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
    entities = [FolderEntity::class, FileEntity::class, TagEntity::class, FileTagCrossRef::class, SavedFilterEntity::class, PendingJobEntity::class,
        AudioPrintEntity::class, MediaSegmentEntity::class, RejectedPrintEntity::class, SegmentRuleEntity::class],
    version = 20,
    exportSchema = false,
)
abstract class CriptaDatabase : RoomDatabase() {
    /** The SQLCipher key given to the open helper; wiped by [wipeKey] once the database is closed. */
    @Volatile private var key: ByteArray? = null

    /** Zero the key copy held for the open helper. Call only after [close], once nothing reopens it. */
    fun wipeKey() { key?.fill(0); key = null }

    abstract fun folderDao(): FolderDao
    abstract fun fileDao(): FileDao
    abstract fun tagDao(): TagDao
    abstract fun savedFilterDao(): SavedFilterDao
    abstract fun pendingJobDao(): PendingJobDao
    abstract fun introDao(): IntroDao

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

        /** Folders can go to the trash with their content (navigable there, restorable per file). */
        private val MIGRATION_13_14 = object : Migration(13, 14) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE folders ADD COLUMN deletedAt INTEGER")
            }
        }

        private val MIGRATION_12_13 = object : Migration(12, 13) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE files ADD COLUMN contentHash TEXT")
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `pending_jobs` (" +
                        "`id` TEXT NOT NULL, `kind` TEXT NOT NULL, `payload` TEXT NOT NULL, `createdAt` INTEGER NOT NULL, " +
                        "PRIMARY KEY(`id`))"
                )
            }
        }

        private val MIGRATION_14_15 = object : Migration(14, 15) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE folders ADD COLUMN isFavorite INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE folders ADD COLUMN excludedFromRandom INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE folders ADD COLUMN sortKey TEXT")
                db.execSQL("ALTER TABLE folders ADD COLUMN sortAscending INTEGER NOT NULL DEFAULT 0")
            }
        }

        /** Intro/outro detection: fingerprints, segments, rejected samples. */
        internal val MIGRATION_15_16 = object : Migration(15, 16) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `audio_prints` (`fileId` TEXT NOT NULL, `part` TEXT NOT NULL, " +
                        "`startMs` INTEGER NOT NULL, `hashes` BLOB NOT NULL, `status` TEXT NOT NULL, PRIMARY KEY(`fileId`, `part`))"
                )
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `media_segments` (`fileId` TEXT NOT NULL, `kind` TEXT NOT NULL, " +
                        "`startMs` INTEGER NOT NULL, `endMs` INTEGER NOT NULL, `source` TEXT NOT NULL, PRIMARY KEY(`fileId`, `kind`))"
                )
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `rejected_prints` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "`kind` TEXT NOT NULL, `originFileId` TEXT NOT NULL, `originName` TEXT NOT NULL, `startMs` INTEGER NOT NULL, " +
                        "`endMs` INTEGER NOT NULL, `hashes` BLOB NOT NULL, `createdAt` INTEGER NOT NULL)"
                )
            }
        }

        /** Which prints were matched already: older scans could stop before matching any. */
        internal val MIGRATION_16_17 = object : Migration(16, 17) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE audio_prints ADD COLUMN matched INTEGER NOT NULL DEFAULT 0")
            }
        }

        /**
         * Detected segments could come from two copies of the same video (a whole analysed minute):
         * drop them all and match every print again; the user's own marks stay.
         */
        internal val MIGRATION_17_18 = object : Migration(17, 18) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("DELETE FROM media_segments WHERE source = 'auto'")
                db.execSQL("UPDATE audio_prints SET matched = 0")
            }
        }

        /** The matcher now tolerates short, differently compressed intros: compare every print again. */
        internal val MIGRATION_18_19 = object : Migration(18, 19) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("UPDATE audio_prints SET matched = 0")
            }
        }

        /** Segments remember the audio they were found on; rules extend a sound's segments everywhere. */
        internal val MIGRATION_19_20 = object : Migration(19, 20) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE media_segments ADD COLUMN autoStartMs INTEGER")
                db.execSQL("ALTER TABLE media_segments ADD COLUMN autoEndMs INTEGER")
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `segment_rules` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "`kind` TEXT NOT NULL, `originFileId` TEXT NOT NULL, `originName` TEXT NOT NULL, `hashes` BLOB NOT NULL, " +
                        "`startDeltaMs` INTEGER NOT NULL, `endDeltaMs` INTEGER NOT NULL, `createdAt` INTEGER NOT NULL)"
                )
            }
        }

        /** Delete the encrypted database file (and its -wal/-shm siblings). Used on vault reset. */
        fun deleteDatabase(context: Context) {
            context.deleteDatabase(NAME)
        }

        /** Open the encrypted DB with the given raw passphrase (SQLCipher). */
        fun open(context: Context, passphrase: ByteArray): CriptaDatabase {
            SQLiteDatabase.loadLibs(context)
            // clearPassphrase = false: SQLCipher's default zeroes the key right after the first
            // open, and Room reopens the helper when a query Flow is cancelled around a close
            // (InvalidationTracker.removeObserver): with a zeroed key that reopen failed with "file is
            // not a database" on Room's thread and crashed the app when it came back from the lock.
            // The key is wiped by SessionManager after the (delayed) close instead.
            val key = passphrase.copyOf()
            val factory = SupportFactory(key, null, false)
            return Room.databaseBuilder(context, CriptaDatabase::class.java, NAME)
                .openHelperFactory(factory)
                .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6, MIGRATION_6_7, MIGRATION_7_8, MIGRATION_8_9, MIGRATION_9_10, MIGRATION_10_11, MIGRATION_11_12, MIGRATION_12_13, MIGRATION_13_14, MIGRATION_14_15, MIGRATION_15_16, MIGRATION_16_17, MIGRATION_17_18, MIGRATION_18_19, MIGRATION_19_20)
                // No destructive fallback: a missing migration must fail loudly, never wipe the vault.
                .build()
                .also { it.key = key }
        }
    }
}
