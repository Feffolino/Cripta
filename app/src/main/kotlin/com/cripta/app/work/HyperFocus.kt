package com.cripta.app.work

import android.annotation.SuppressLint
import android.app.Notification
import android.app.PendingIntent
import android.content.Context
import android.graphics.drawable.Icon
import android.net.Uri
import android.os.Build
import android.os.Bundle
import org.json.JSONArray
import org.json.JSONObject

/**
 * Xiaomi HyperOS "focus notifications" (the Hyper Island's own template, with its large rounded
 * buttons), added on top of the standard notification as extras. Everywhere else (other phones,
 * HyperOS without the island, or focus notifications turned off for Cripta) nothing is added and
 * the standard Live Update is what shows.
 *
 * Protocol 3 (`miui.focus.param`): a JSON description of the notification, the island (small:
 * icon with a progress ring; big: icon + title on the left, progress / status on the right) and
 * its text buttons, whose PendingIntents and pictures travel in the `miui.focus.actions` and
 * `miui.focus.pics` bundles, referenced by key. Payload shape as used by the open-source
 * HyperIsland-ToolKit (Apache 2.0); written here without the library, which needs a newer Kotlin
 * and SDK than the app builds with.
 *
 * Only what the standard notification already shows goes in: no file names or titles.
 */
internal object HyperFocus {
    private const val PIC = "miui.focus.pic_"
    private const val ACTION = "miui.focus.action_"
    private const val BRAND = "#5AA9FF"

    /** A button of the focus notification. */
    class Button(val key: String, val title: String, val intent: PendingIntent, val icon: Int? = null)

    /**
     * The protocol's `actionIntentType` of a button: 1 activity, 2 broadcast, 3 service. Read from
     * the PendingIntent itself: Annulla, the keep/delete choices and Riprova start the service,
     * Installa and Vedi risultati an activity (all were declared as activities before).
     */
    private fun intentType(pi: PendingIntent): Int = when {
        pi.isActivity -> 1
        pi.isBroadcast -> 2
        else -> 3
    }

    @Volatile private var supported: Boolean? = null

    /** The last focus payload sent (Debug isola › Copia payload), to compare what HyperOS got. */
    @Volatile var lastPayload: String? = null
        private set

    /**
     * Xiaomi with the island: the focus extras are added. Whether Cripta may show focus
     * notifications is not a condition any more: that check (canShowFocus) can answer false or
     * fail where the island would take them anyway, and the extras are ignored when not allowed.
     * Checked once per process.
     */
    fun isSupported(ctx: Context): Boolean = supported ?: check(ctx).also { supported = it }

    private fun check(ctx: Context): Boolean =
        Build.MANUFACTURER.equals("Xiaomi", ignoreCase = true) && (islandFeature() || canShowFocus(ctx) == true)

    /** HyperOS's own answer on whether Cripta may show focus notifications (null: no answer). */
    private fun canShowFocus(ctx: Context): Boolean? = try {
        ctx.contentResolver.call(
            Uri.parse("content://miui.statusbar.notification.public"), "canShowFocus", null,
            Bundle().apply { putString("package", ctx.packageName) },
        )?.getBoolean("canShowFocus", false)
    } catch (e: Exception) {
        null
    }

    /**
     * The focus protocol version the system speaks (HyperOS publishes it in Settings.System;
     * 0 = unknown). A payload newer than the system's is ignored, so it is written in this one.
     */
    private fun protocol(ctx: Context): Int = try {
        android.provider.Settings.System.getInt(ctx.contentResolver, "notification_focus_protocol", 0)
    } catch (e: Exception) {
        0
    }

    @SuppressLint("PrivateApi")
    private fun islandFeature(): Boolean = try {
        Class.forName("android.os.SystemProperties")
            .getDeclaredMethod("getBoolean", String::class.java, Boolean::class.javaPrimitiveType)
            .invoke(null, "persist.sys.feature.island", false) as Boolean
    } catch (e: Exception) {
        false
    }

    /**
     * The island has room for a word next to the icon ("Conversione in MP4" was cut to
     * "Conversione co…"): a short name of the operation there, the full title when expanded.
     */
    private fun islandLabel(title: String): String = when {
        title.startsWith("Conversione") || title.startsWith("Copia MP4") -> "MP4"
        title.contains("cifrat") || title.contains("importat") -> "Importa"
        title.startsWith("Duplicati") || title.startsWith("Media simili") -> "Ricerca"
        title.startsWith("Aggiornamento") -> "Aggiorna"
        title.startsWith("Importazione") -> "Importa"
        title.startsWith("Esportazione") -> "Esporta"
        title.startsWith("Download") -> "Download"
        title.startsWith("Ricerca") -> "Ricerca"
        else -> "Cripta"
    }

    /**
     * Extras for one focus notification, or an empty bundle where unsupported.
     * @param chip short text of the island ("42%", "3/10", "Fatto").
     * @param progress 0..100 for an operation in progress, null for a finished one (a choice).
     * @param float pop the island open once (a choice to make), not on every progress update.
     */
    fun extras(
        ctx: Context,
        business: String,
        title: String,
        text: String?,
        chip: String,
        icon: Int,
        progress: Int?,
        buttons: List<Button>,
        float: Boolean,
        /** A finished operation: shown in the island for this many seconds, then it goes (null: stays). */
        islandTimeoutSec: Int? = null,
    ): Bundle {
        if (!isSupported(ctx)) return Bundle()
        val dbg = IslandDebug.get(ctx)
        // Keys unique to this icon and this notification: HyperOS keeps the pictures and buttons
        // of all of an app's focus notifications by key, and with several at once (a download and
        // a conversion result…) the shared "icon" / "choice0" keys got mixed up (a square icon).
        val picKey = PIC + runCatching { ctx.resources.getResourceEntryName(icon) }.getOrDefault("icon")
        val tag = (business + title).hashCode().toUInt().toString(16)
        val pic = JSONObject().put("type", 1).put("pic", picKey)
        val small = if (progress != null) {
            JSONObject().put("combinePicInfo", JSONObject().put("picInfo", pic)
                .put("progressInfo", JSONObject().put("progress", progress).put("colorReach", BRAND)))
        } else JSONObject().put("picInfo", pic)
        val right = if (progress != null) {
            JSONObject().put("progressTextInfo", JSONObject()
                .put("progressInfo", JSONObject().put("progress", progress).put("colorReach", BRAND))
                .put("textInfo", JSONObject().put("title", chip)))
        } else JSONObject().put("imageTextInfoRight", JSONObject().put("type", 2)
            .put("textInfo", JSONObject().put("title", chip)))
        val big = JSONObject()
            .put("imageTextInfoLeft", JSONObject().put("type", 1).put("picInfo", pic)
                .put("textInfo", JSONObject().put("title", if (dbg.shortLabel) islandLabel(title) else title)))
        right.keys().forEach { big.put(it, right.get(it)) }
        val param = JSONObject()
            .put("protocol", protocol(ctx).takeIf { it in 1..3 } ?: 3)
            .put("business", business)
            .put("updatable", true)
            .put("ticker", chip)
            .put("enableFloat", if (progress != null) dbg.floatOnUpdate else float && dbg.floatOnChoice)
            .put("islandFirstFloat", if (progress != null) dbg.floatOnUpdate else float && dbg.floatOnChoice)
            .put("isShowNotification", true)
        if (dbg.padding) param.put("padding", true)
        // Swipe down on the island: Cripta in a small floating window, as the system apps do
        // (the vault's own lock screen shows first when it is locked).
        when (dbg.smallWindow) {
            "class" -> param.put("smallWindowInfo", JSONObject().put("targetPage", "com.cripta.app.MainActivity"))
            "component" -> param.put("smallWindowInfo", JSONObject().put("targetPage", "${ctx.packageName}/com.cripta.app.MainActivity"))
        }
        // Expanded: the chat template (the operation's icon on the left, title and text beside
        // it, as HyperOS lays out its own ongoing items). The base template put the text above
        // the title with the icon squeezed after it, and its padding flag didn't add margins.
        if (dbg.template == "base") {
            param.put("baseInfo", JSONObject().put("type", 1).put("title", title).put("content", text ?: "")
                .put("picFunction", picKey))
        } else {
            param.put("chatInfo", JSONObject().put("title", title).put("content", text ?: "")
                .put("picFunction", picKey))
        }
        param
            .put("param_island", JSONObject()
                .put("islandProperty", 1)
                .put("islandPriority", 2)
                .put("dismissIsland", islandTimeoutSec != null)
                .apply { islandTimeoutSec?.let { put("islandTimeout", it) } }
                .put("needCloseAnimation", true)
                .put("highlightColor", BRAND)
                .put("smallIslandArea", small)
                .put("bigIslandArea", big))
        if (progress != null && dbg.progressBar) param.put("progressInfo", JSONObject().put("progress", progress).put("colorProgress", BRAND))
        // Buttons as the system Clock shows them: round icon buttons beside the title (the
        // template's actions, icon only), which leaves the progress bar room below and fits two.
        // Without icons: one as the template's text pill, several as the row of text buttons.
        val style = when {
            dbg.buttons == "icon" && buttons.all { it.icon != null } -> "icon"
            dbg.buttons == "row" -> "row"
            dbg.buttons == "pill" -> "pill"
            buttons.size == 1 -> "pill"
            else -> "row"
        }
        if (buttons.isNotEmpty() && style == "icon") {
            param.put("actions", JSONArray().apply {
                buttons.forEach { b ->
                    put(JSONObject()
                        .put("type", 0)
                        .put("action", ACTION + tag + "_" + b.key)
                        .put("actionTitle", "")
                        .put("actionIntentType", intentType(b.intent)))
                }
            })
        } else if (buttons.isNotEmpty() && style == "pill") {
            param.put("actions", JSONArray().apply {
                buttons.forEach { b ->
                    put(JSONObject()
                        .put("type", 2)
                        .put("action", ACTION + tag + "_" + b.key)
                        .put("actionTitle", b.title)
                        .put("actionIntentType", intentType(b.intent)))
                }
            })
        } else if (buttons.isNotEmpty()) {
            param.put("textButton", JSONArray().apply {
                buttons.forEach { b ->
                    put(JSONObject()
                        .put("type", 1)
                        .put("actionTitle", b.title)
                        .put("actionIntentType", intentType(b.intent))
                        .put("actionIntent", ACTION + tag + "_" + b.key)
                        .put("action", ACTION + tag + "_" + b.key))
                }
            })
        }
        val actions = Bundle()
        buttons.forEach { b ->
            actions.putParcelable(ACTION + tag + "_" + b.key,
                Notification.Action.Builder(Icon.createWithResource(ctx, b.icon ?: icon), b.title, b.intent).build())
        }
        val json = JSONObject().put("param_v2", param).put("isShowNotification", true).toString()
        lastPayload = json
        return Bundle().apply {
            putString("miui.focus.param", json)
            putBundle("miui.focus.actions", actions)
            putBundle("miui.focus.pics", Bundle().apply { putParcelable(picKey, Icon.createWithResource(ctx, icon)) })
        }
    }
}
