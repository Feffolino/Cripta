# SQLCipher
-keep class net.sqlcipher.** { *; }
-keep class net.sqlcipher.database.** { *; }

# Tink
-keep class com.google.crypto.tink.** { *; }

# Keep Room generated
-keep class * extends androidx.room.RoomDatabase { *; }
