package com.cripta.app.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import net.sqlcipher.database.SQLiteDatabase
import net.sqlcipher.database.SupportFactory

@Database(
    entities = [FolderEntity::class, FileEntity::class, TagEntity::class, FileTagCrossRef::class],
    version = 1,
    exportSchema = false,
)
abstract class CriptaDatabase : RoomDatabase() {
    abstract fun folderDao(): FolderDao
    abstract fun fileDao(): FileDao
    abstract fun tagDao(): TagDao

    companion object {
        private const val NAME = "cripta.db"

        /** Open the encrypted DB with the given raw passphrase (SQLCipher). */
        fun open(context: Context, passphrase: ByteArray): CriptaDatabase {
            SQLiteDatabase.loadLibs(context)
            val factory = SupportFactory(passphrase.copyOf())
            return Room.databaseBuilder(context, CriptaDatabase::class.java, NAME)
                .openHelperFactory(factory)
                .fallbackToDestructiveMigration()
                .build()
        }
    }
}
