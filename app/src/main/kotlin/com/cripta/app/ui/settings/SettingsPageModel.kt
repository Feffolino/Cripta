package com.cripta.app.ui.settings

import com.cripta.app.plural
import com.cripta.app.R
import com.cripta.app.str
import com.cripta.app.ui.theme.CriptaIcons
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import com.cripta.app.data.DeleteOriginalPolicy

enum class SettingsGroup(private val labelRes: Int) {
    PREFERENCES(R.string.group_preferences), DATA(R.string.group_data), APP(R.string.group_app);

    val label: String get() = str(labelRes)
}

/** Settings pages, in list order, grouped by what the user wants to do. */
enum class SettingsPage(
    private val labelRes: Int,
    val icon: androidx.compose.ui.graphics.vector.ImageVector,
    val group: SettingsGroup,
    val resettable: Boolean = true,
) {
    ASPETTO(R.string.page_appearance, CriptaIcons.Palette, SettingsGroup.PREFERENCES),
    ETICHETTE(R.string.tags, CriptaIcons.Label, SettingsGroup.PREFERENCES),
    VISUALIZZATORE(R.string.page_viewer, CriptaIcons.PlayCircle, SettingsGroup.PREFERENCES),
    ANALISI(R.string.page_analysis, CriptaIcons.Face, SettingsGroup.PREFERENCES),
    SICUREZZA(R.string.page_security, CriptaIcons.Lock, SettingsGroup.DATA),
    IMPORT(R.string.page_import, CriptaIcons.Download, SettingsGroup.DATA),
    BACKUP(R.string.kind_backup, CriptaIcons.Backup, SettingsGroup.DATA, resettable = false),
    SPAZIO(R.string.page_space, CriptaIcons.DeleteSweep, SettingsGroup.DATA, resettable = false),
    INFO(R.string.page_info, CriptaIcons.SystemUpdate, SettingsGroup.APP, resettable = false),
    ;

    val label: String get() = str(labelRes)
}

/** "1 elemento" / "N elementi": what the trash counts. Files and folders alike are elements. */
internal fun elementsLabel(n: Int): String = plural(R.plurals.items_n, n)

/** One-line summary of a page's current state, shown under its name in the list. */
internal fun pageSummary(p: SettingsPage, s: com.cripta.app.data.Settings, tagCount: Int, trashCount: Int, dupGroups: Int?): String = when (p) {
    SettingsPage.ASPETTO -> str(when (s.themeMode) {
        com.cripta.app.data.ThemeMode.SYSTEM -> R.string.sum_theme_system
        com.cripta.app.data.ThemeMode.LIGHT -> R.string.sum_theme_light
        com.cripta.app.data.ThemeMode.DARK -> R.string.sum_theme_dark
    }) + (if (s.dynamicColor) str(R.string.sum_dynamic) else "") + (if (s.filterCurrentFolderOnly) str(R.string.sum_filters_folder) else "")
    SettingsPage.ETICHETTE -> plural(R.plurals.tags_n, tagCount) + (if (s.display.showTagsOnCover) str(R.string.sum_on_covers) else "") +
        if (s.display.showRecentTags) str(R.string.sum_recent) else ""
    SettingsPage.VISUALIZZATORE -> listOfNotNull(
        if (s.display.resumePlayback) str(R.string.sum_resume) else null,
        if (s.videoLoop) str(R.string.sum_loop) else null,
        if (s.autoNext) str(R.string.sum_next_file) else null,
        if (s.splitDetails) str(R.string.sum_split) else null,
    ).joinToString(" · ").ifEmpty { str(R.string.sum_viewer_default) }
    SettingsPage.ANALISI -> str(R.string.sum_analysis,
        str(if (s.facesAnalyzeNew) R.string.sum_auto_analysis else R.string.sum_manual_analysis),
        str(if (s.introAnalyzeNew) R.string.sum_auto else R.string.sum_manual))
    SettingsPage.SICUREZZA -> when (s.autoLockMinutes) {
        -1 -> str(R.string.sum_lock_never); 0 -> str(R.string.sum_lock_now); else -> str(R.string.sum_lock_after, s.autoLockMinutes)
    } + str(if (s.allowScreenshots) R.string.sum_shots_allowed else R.string.sum_shots_blocked)
    SettingsPage.IMPORT -> str(R.string.sum_import,
        str(when (s.deleteOriginalPolicy) {
            DeleteOriginalPolicy.ASK -> R.string.sum_ask; DeleteOriginalPolicy.ALWAYS -> R.string.sum_delete; DeleteOriginalPolicy.NEVER -> R.string.sum_keep
        }),
        str(when (s.convertAfter) {
            com.cripta.app.data.ConvertAfter.REPLACE -> R.string.sum_replaces
            com.cripta.app.data.ConvertAfter.ASK -> R.string.sum_ask
            com.cripta.app.data.ConvertAfter.KEEP_BOTH -> R.string.sum_keep_both
        }))
    SettingsPage.BACKUP -> str(R.string.sum_backup)
    SettingsPage.SPAZIO -> listOfNotNull(
        str(R.string.kind_duplicates),
        if (trashCount > 0) str(R.string.sum_trash_n, elementsLabel(trashCount)) else str(if (s.trashEnabled) R.string.sum_trash_empty else R.string.sum_trash_off),
        dupGroups?.let { str(R.string.sum_results_ready, it) },
    ).joinToString(" · ")
    SettingsPage.INFO -> str(R.string.sum_info)
}

/** A searchable option: what it is called, extra words people may type, where it lives (page + section). */
internal class SearchEntry(val label: String, val keywords: String, val page: SettingsPage, val section: String)

/**
 * The entries of [index] that [query] finds in their label or their keywords, case aside in the
 * app's language ([locale]): keywords such as "PIN Fingerabdruck" have capitals too.
 */
internal fun searchSettings(index: List<SearchEntry>, query: String, locale: java.util.Locale): List<SearchEntry> {
    val q = query.trim().lowercase(locale)
    if (q.isEmpty()) return emptyList()
    return index.filter { it.label.lowercase(locale).contains(q) || it.keywords.lowercase(locale).contains(q) }
}

// A getter: the texts are read in the current language each time the search runs.
internal val SEARCH_INDEX: List<SearchEntry> get() = listOf(
    SearchEntry(str(R.string.si_00), str(R.string.si_00_kw), SettingsPage.ASPETTO, str(R.string.sec_theme)),
    SearchEntry(str(R.string.si_01), str(R.string.si_01_kw), SettingsPage.ASPETTO, str(R.string.sec_theme)),
    SearchEntry(str(R.string.si_42), str(R.string.si_42_kw), SettingsPage.ASPETTO, str(R.string.sec_language)),
    SearchEntry(str(R.string.si_02), str(R.string.si_02_kw), SettingsPage.ASPETTO, str(R.string.tab_folders)),
    SearchEntry(str(R.string.si_03), str(R.string.si_03_kw), SettingsPage.ASPETTO, str(R.string.tab_folders)),
    SearchEntry(str(R.string.si_04), str(R.string.si_04_kw), SettingsPage.ASPETTO, str(R.string.tab_folders)),
    SearchEntry(str(R.string.si_05), str(R.string.si_05_kw), SettingsPage.ASPETTO, str(R.string.tab_folders)),
    SearchEntry(str(R.string.si_06), str(R.string.si_06_kw), SettingsPage.ASPETTO, str(R.string.tab_folders)),
    SearchEntry(str(R.string.si_07), str(R.string.si_07_kw), SettingsPage.ASPETTO, str(R.string.tab_folders)),
    SearchEntry(str(R.string.si_08), str(R.string.si_08_kw), SettingsPage.ASPETTO, str(R.string.sec_buttons)),
    SearchEntry(str(R.string.si_09), str(R.string.si_09_kw), SettingsPage.ETICHETTE, str(R.string.sec_tag_library)),
    SearchEntry(str(R.string.si_10), str(R.string.si_10_kw), SettingsPage.ETICHETTE, str(R.string.sec_on_covers)),
    SearchEntry(str(R.string.si_11), str(R.string.si_11_kw), SettingsPage.ETICHETTE, str(R.string.sec_colours)),
    SearchEntry(str(R.string.si_12), str(R.string.si_12_kw), SettingsPage.ETICHETTE, str(R.string.sec_quick_assign)),
    SearchEntry(str(R.string.si_13), str(R.string.si_13_kw), SettingsPage.ETICHETTE, str(R.string.sec_quick_assign)),
    SearchEntry(str(R.string.si_14), str(R.string.si_14_kw), SettingsPage.ETICHETTE, str(R.string.sec_library_order)),
    SearchEntry(str(R.string.si_15), str(R.string.si_15_kw), SettingsPage.VISUALIZZATORE, str(R.string.sec_playback)),
    SearchEntry(str(R.string.si_16), str(R.string.si_16_kw), SettingsPage.VISUALIZZATORE, str(R.string.sec_playback)),
    SearchEntry(str(R.string.si_17), str(R.string.si_17_kw), SettingsPage.VISUALIZZATORE, str(R.string.sec_playback)),
    SearchEntry(str(R.string.si_18), str(R.string.si_18_kw), SettingsPage.VISUALIZZATORE, str(R.string.sec_video_end)),
    SearchEntry(str(R.string.si_19), str(R.string.si_19_kw), SettingsPage.VISUALIZZATORE, str(R.string.sec_gestures)),
    SearchEntry(str(R.string.si_20), str(R.string.si_20_kw), SettingsPage.VISUALIZZATORE, str(R.string.sec_gestures)),
    SearchEntry(str(R.string.si_21), str(R.string.si_21_kw), SettingsPage.VISUALIZZATORE, str(R.string.sec_gestures)),
    SearchEntry(str(R.string.si_22), str(R.string.si_22_kw), SettingsPage.VISUALIZZATORE, str(R.string.sec_gestures)),
    SearchEntry(str(R.string.si_23), str(R.string.si_23_kw), SettingsPage.VISUALIZZATORE, str(R.string.sec_gestures)),
    SearchEntry(str(R.string.si_24), str(R.string.si_24_kw), SettingsPage.VISUALIZZATORE, str(R.string.sec_screen)),
    SearchEntry(str(R.string.si_25), str(R.string.si_25_kw), SettingsPage.VISUALIZZATORE, str(R.string.sec_screen)),
    SearchEntry(str(R.string.si_26), str(R.string.si_26_kw), SettingsPage.VISUALIZZATORE, str(R.string.sec_nearby_previews)),
    SearchEntry(str(R.string.si_27), str(R.string.si_27_kw), SettingsPage.VISUALIZZATORE, str(R.string.sec_details_panel)),
    SearchEntry(str(R.string.si_28), str(R.string.si_28_kw), SettingsPage.VISUALIZZATORE, str(R.string.sec_leaving_app)),
    SearchEntry(str(R.string.si_29), str(R.string.si_29_kw), SettingsPage.VISUALIZZATORE, str(R.string.sec_leaving_app)),
    SearchEntry(str(R.string.si_30), str(R.string.si_30_kw), SettingsPage.ANALISI, str(R.string.people)),
    SearchEntry(str(R.string.si_31), str(R.string.si_31_kw), SettingsPage.ANALISI, str(R.string.intro_and_ending)),
    SearchEntry(str(R.string.si_32), str(R.string.si_32_kw), SettingsPage.SICUREZZA, str(R.string.sec_unlock)),
    SearchEntry(str(R.string.si_33), str(R.string.si_33_kw), SettingsPage.SICUREZZA, str(R.string.sec_auto_lock)),
    SearchEntry(str(R.string.si_34), str(R.string.si_34_kw), SettingsPage.SICUREZZA, str(R.string.sec_privacy)),
    SearchEntry(str(R.string.si_35), str(R.string.si_35_kw), SettingsPage.IMPORT, str(R.string.sec_original_after_import)),
    SearchEntry(str(R.string.si_36), str(R.string.si_36_kw), SettingsPage.IMPORT, str(R.string.sec_mp4_conversion)),
    SearchEntry(str(R.string.si_37), str(R.string.si_37_kw), SettingsPage.BACKUP, str(R.string.sec_encrypted_backup)),
    SearchEntry(str(R.string.si_38), str(R.string.si_38_kw), SettingsPage.BACKUP, str(R.string.sec_what_to_include)),
    SearchEntry(str(R.string.si_39), str(R.string.si_39_kw), SettingsPage.SPAZIO, str(R.string.sec_duplicate_files)),
    SearchEntry(str(R.string.si_40), str(R.string.si_40_kw), SettingsPage.SPAZIO, str(R.string.sec_trash)),
    SearchEntry(str(R.string.si_41), str(R.string.si_41_kw), SettingsPage.INFO, str(R.string.sec_updates)),
)
