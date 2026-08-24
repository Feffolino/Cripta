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
    entities = [FolderEntity::class, FileEntity::class, TagEntity::class, FileTagCrossRef::class],
    version = 3,
    exportSchema = false,
)
abstract class CriptaDatabase : RoomDatabase() {
    abstract fun folderDao(): FolderDao
    abstract fun fileDao(): FileDao
    abstract fun tagDao(): TagDao

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

        /** Open the encrypted DB with the given raw passphrase (SQLCipher). */
        fun open(context: Context, passphrase: ByteArray): CriptaDatabase {
            SQLiteDatabase.loadLibs(context)
            val factory = SupportFactory(passphrase.copyOf())
            return Room.databaseBuilder(context, CriptaDatabase::class.java, NAME)
                .openHelperFactory(factory)
                .addMigrations(MIGRATION_1_2, MIGRATION_2_3)
                .fallbackToDestructiveMigration()
                .build()
        }
    }
}
