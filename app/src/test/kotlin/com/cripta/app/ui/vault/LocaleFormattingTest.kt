package com.cripta.app.ui.vault

import android.app.Application
import com.cripta.app.R
import com.cripta.app.Strings
import com.cripta.app.plural
import com.cripta.app.data.db.FileEntity
import com.cripta.app.data.db.FileWithTags
import com.cripta.app.ui.components.formatBytes
import com.cripta.app.ui.components.formatCount
import com.cripta.app.ui.components.formatDuration
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.time.LocalDate
import java.time.ZoneId
import java.util.Locale

/** Formatting follows Cripta's language even when the JVM's default language differs. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class LocaleFormattingTest {
    private lateinit var previousDefault: Locale

    @Before
    fun texts() {
        previousDefault = Locale.getDefault()
        Strings.init(RuntimeEnvironment.getApplication())
        Locale.setDefault(Locale.US)
    }

    @After
    fun restoreDefaultLocale() {
        Locale.setDefault(previousDefault)
    }

    @Test
    @Config(qualifiers = "ar")
    fun arabicUsesArabicDigitsAndSeparators() {
        checkFormatting("ar", "٢٣ يناير ٢٠٠٠", "١٫٥ KB", "٠ B", "١:٠١:٠١", "١٢٬٣٤٥")
    }

    @Test
    @Config(qualifiers = "hi")
    fun hindiUsesItsMonthNameAndDefaultLatinDigits() {
        checkFormatting("hi", "23 जनवरी 2000", "1.5 KB", "0 B", "1:01:01", "12,345")
    }

    @Test
    @Config(qualifiers = "ja")
    fun japaneseUsesItsMonthName() {
        checkFormatting("ja", "23 1月 2000", "1.5 KB", "0 B", "1:01:01", "12,345")
    }

    @Test
    @Config(qualifiers = "de")
    fun germanUsesItsMonthNameAndDecimalComma() {
        checkFormatting("de", "23 Januar 2000", "1,5 KB", "0 B", "1:01:01", "12.345")
    }

    @Test
    @Config(qualifiers = "de")
    fun aCountedTextGivenOnlyItsCountWritesItLikeTheOthers() {
        // "1234 Elemente" next to "1.234 Dateien" before.
        assertEquals("1.234 Elemente", plural(R.plurals.items_n, 1234))
    }

    @Test
    fun aSizeThatRoundsUpToTheNextUnitIsWrittenInIt() {
        assertEquals("1.0 MB", formatBytes(1_048_575))
        assertEquals("1.0 GB", formatBytes(1_073_741_823))
        assertEquals("1023.9 KB", formatBytes(1_048_524))
        assertEquals("1023 B", formatBytes(1023))
    }

    private fun checkFormatting(
        language: String,
        date: String,
        bytes: String,
        zeroBytes: String,
        duration: String,
        count: String,
    ) {
        assertEquals("system language remains different from the app", Locale.US, Locale.getDefault())
        assertEquals("app resource language", language, Strings.locale.language)
        val importedAt = LocalDate.of(2000, 1, 23).atStartOfDay(ZoneId.systemDefault())
            .toInstant().toEpochMilli()
        val file = FileWithTags(
            FileEntity(
                id = "locale-test",
                originalName = "example.jpg",
                mimeType = "image/jpeg",
                sizeBytes = 1536L,
                createdAt = importedAt,
                importedAt = importedAt,
                wrappedKeyset = byteArrayOf(),
            ),
            emptyList(),
        )
        val group = groupByDay(listOf(file)).single()
        assertEquals("localized date heading", date, group.first)
        assertEquals(listOf(file), group.second)
        assertEquals("localized size", bytes, formatBytes(file.file.sizeBytes))
        assertEquals("localized zero size", zeroBytes, formatBytes(0))
        assertEquals("localized duration", duration, formatDuration(3_661_000))
        assertEquals("localized count", count, formatCount(12_345))
    }
}
