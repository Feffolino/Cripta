# R8 rules for the RELEASE build (the CI "debug" APK is not minified).
# Deliberately conservative: every library below uses reflection, JNI or service loading, so its
# classes are kept whole. The win is dead-code removal in the rest of the app and the AndroidX /
# Compose stack, not squeezing these libraries.

# Keep line numbers for readable crash traces, hide the original file names.
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile
-keepattributes *Annotation*,Signature,InnerClasses,EnclosingMethod,Exceptions

# --- App ---------------------------------------------------------------------------------------
# Room entities / DAOs / database (generated _Impl classes are looked up by name).
-keep class com.cripta.app.data.db.** { *; }
-keep class com.cripta.crypto.** { *; }

# --- SQLCipher (JNI: native code calls back into these classes by name) ------------------------
-keep class net.sqlcipher.** { *; }
-keep class net.sqlcipher.database.** { *; }
-keep class net.zetetic.** { *; }
-dontwarn net.sqlcipher.**
-dontwarn net.zetetic.**

# --- Tink + protobuf-lite (key managers registered and parsed reflectively) --------------------
-keep class com.google.crypto.tink.** { *; }
-keep class * extends com.google.crypto.tink.shaded.protobuf.GeneratedMessageLite { *; }
-keep class com.google.protobuf.** { *; }
-dontwarn com.google.crypto.tink.**
-dontwarn com.google.protobuf.**
-dontwarn com.google.errorprone.annotations.**
-dontwarn javax.annotation.**
-dontwarn com.google.api.client.**
-dontwarn org.joda.time.**

# --- Room ----------------------------------------------------------------------------------------
-keep class * extends androidx.room.RoomDatabase { *; }
-keep @androidx.room.Entity class * { *; }
-keep @androidx.room.Dao interface * { *; }
-keep class **_Impl { *; }
-dontwarn androidx.room.paging.**

# --- Hilt / Dagger (the Gradle plugin ships its own rules; these are a safety net) --------------
-keep class dagger.hilt.** { *; }
-keep class * extends dagger.hilt.android.internal.managers.ViewComponentManager$FragmentContextWrapper { *; }
-keep @dagger.hilt.android.AndroidEntryPoint class * { *; }
-keep @dagger.hilt.android.HiltAndroidApp class * { *; }
-keep @dagger.hilt.EntryPoint interface * { *; }
-keep class **_HiltModules* { *; }
-keep class **Hilt_* { *; }
-keep class hilt_aggregated_deps.** { *; }

# --- Media3 / ExoPlayer (renderers and extractors created reflectively, native decoders) --------
-keep class androidx.media3.** { *; }
-dontwarn androidx.media3.**

# --- youtubedl-android + its ffmpeg / python payload --------------------------------------------
# Jackson maps yt-dlp's JSON onto the library's model classes by reflection; commons-compress and
# the zip/xz code unpack the bundled Python at first run.
-keep class com.yausername.** { *; }
-keep class io.github.junkfood02.** { *; }
-keep class com.fasterxml.jackson.** { *; }
-keepclassmembers class * {
    @com.fasterxml.jackson.annotation.* <fields>;
    @com.fasterxml.jackson.annotation.* <methods>;
    @com.fasterxml.jackson.annotation.* <init>(...);
}
-keep class org.apache.commons.compress.** { *; }
-keep class org.apache.commons.io.** { *; }
-keep class org.tukaani.xz.** { *; }
-dontwarn com.fasterxml.jackson.**
-dontwarn org.apache.commons.compress.**
-dontwarn org.apache.commons.io.**
-dontwarn org.tukaani.xz.**
-dontwarn org.brotli.dec.**
-dontwarn com.github.luben.zstd.**
-dontwarn org.objectweb.asm.**
-dontwarn java.beans.**
-dontwarn org.w3c.dom.bootstrap.**

# --- PdfBox-Android (fonts / CMaps / glyph lists loaded as Java resources by class-relative path) -
-keep class com.tom_roush.** { *; }
-keepdirectories com/tom_roush/**
-dontwarn com.tom_roush.**
-dontwarn com.gemalto.jp2.**
-dontwarn org.bouncycastle.**

# --- Coil ---------------------------------------------------------------------------------------
-keep class coil.** { *; }
-dontwarn coil.**
-dontwarn okhttp3.**
-dontwarn okio.**

# --- Kotlin / coroutines ------------------------------------------------------------------------
-keepclassmembers class kotlinx.coroutines.internal.MainDispatcherFactory { *; }
-keep class kotlinx.coroutines.android.AndroidDispatcherFactory { *; }
-keep class kotlinx.coroutines.android.AndroidExceptionPreHandler { *; }
-dontwarn kotlinx.coroutines.debug.**

# --- Misc: JSON via org.json (platform), DataStore preferences (protobuf-lite) ------------------
-keep class androidx.datastore.preferences.** { *; }
-keepclassmembers class * extends androidx.datastore.preferences.protobuf.GeneratedMessageLite { <fields>; }
