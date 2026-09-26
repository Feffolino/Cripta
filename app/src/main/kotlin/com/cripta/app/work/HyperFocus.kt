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

    /** A text button of the focus notification. [service]: the intent starts a service (else an activity). */
    class Button(val key: String, val title: String, val intent: PendingIntent, val service: Boolean = true)

    @Volatile private var supported: Boolean? = null

    /** Xiaomi, with the island, and focus notifications allowed for this app. Checked once per process. */
    fun isSupported(ctx: Context): Boolean = supported ?: check(ctx).also { supported = it }

    private fun check(ctx: Context): Boolean {
        if (!Build.MANUFACTURER.equals("Xiaomi", ignoreCase = true)) return false
        if (!islandFeature()) return false
        return try {
            ctx.contentResolver.call(
                Uri.parse("content://miui.statusbar.notification.public"), "canShowFocus", null,
                Bundle().apply { putString("package", ctx.packageName) },
            )?.getBoolean("canShowFocus", false) ?: false
        } catch (e: Exception) {
            false
        }
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
    ): Bundle {
        if (!isSupported(ctx)) return Bundle()
        val picKey = PIC + "icon"
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
                .put("textInfo", JSONObject().put("title", title)))
        right.keys().forEach { big.put(it, right.get(it)) }
        val param = JSONObject()
            .put("protocol", 3)
            .put("business", business)
            .put("updatable", true)
            .put("ticker", chip)
            .put("enableFloat", float)
            .put("islandFirstFloat", float)
            .put("isShowNotification", true)
            .put("baseInfo", JSONObject().put("type", 1).put("title", title).put("content", text ?: ""))
            .put("param_island", JSONObject()
                .put("islandProperty", 1)
                .put("islandPriority", 2)
                .put("dismissIsland", false)
                .put("needCloseAnimation", true)
                .put("highlightColor", BRAND)
                .put("smallIslandArea", small)
                .put("bigIslandArea", big))
        if (progress != null) param.put("progressInfo", JSONObject().put("progress", progress).put("colorProgress", BRAND))
        if (buttons.isNotEmpty()) {
            param.put("textButton", JSONArray().apply {
                buttons.forEach { b ->
                    put(JSONObject()
                        .put("type", 1)
                        .put("actionTitle", b.title)
                        .put("actionIntentType", if (b.service) 3 else 1)
                        .put("actionIntent", ACTION + b.key)
                        .put("action", ACTION + b.key))
                }
            })
        }
        val actions = Bundle()
        buttons.forEach { b ->
            actions.putParcelable(ACTION + b.key,
                Notification.Action.Builder(Icon.createWithResource(ctx, icon), b.title, b.intent).build())
        }
        return Bundle().apply {
            putString("miui.focus.param", JSONObject().put("param_v2", param).put("isShowNotification", true).toString())
            putBundle("miui.focus.actions", actions)
            putBundle("miui.focus.pics", Bundle().apply { putParcelable(picKey, Icon.createWithResource(ctx, icon)) })
        }
    }
}
