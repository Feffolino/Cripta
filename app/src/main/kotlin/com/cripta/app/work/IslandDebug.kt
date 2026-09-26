package com.cripta.app.work

import android.content.Context

/**
 * Switches for trying the Hyper Island variants on the phone without a new build (Impostazioni ›
 * Informazioni › Debug isola). Read at every notification, so a change shows on the next update.
 * Plain SharedPreferences: the service reads them too, and nothing here is sensitive.
 */
internal object IslandDebug {
    private const val PREFS = "island_debug"

    data class Config(
        /** Expanded template: "chat" (icon left, title and text beside) or "base". */
        val template: String = "chat",
        /** Buttons: "icon" (round, icon only), "pill" (text in the template's actions), "row" (text buttons below). */
        val buttons: String = "icon",
        /** Channel of high importance (silent) instead of the default one. */
        val highChannel: Boolean = false,
        /** Also ask for the Android 16 Live Update on Xiaomi phones. */
        val liveUpdate: Boolean = false,
        /** The template's "padding" flag. */
        val padding: Boolean = false,
        /** Short name of the operation in the compact island ("MP4") instead of the full title. */
        val shortLabel: Boolean = true,
        /** Swipe down → small window: "class" (com.cripta.app.MainActivity), "component" (package/.MainActivity), "off". */
        val smallWindow: String = "class",
        /** Minimum time between two updates of the ongoing notification, ms. */
        val intervalMs: Int = 1000,
        /** Pop the island open on progress updates too (not only on a choice). */
        val floatOnUpdate: Boolean = false,
        /** Progress bar in the expanded island. */
        val progressBar: Boolean = true,
        /** Pop the island open when a choice arrives. */
        val floatOnChoice: Boolean = true,
        /** How long a result stays in the island before it becomes a normal notification, s. */
        val resultSeconds: Int = 5,
        /** How long a choice stays in the island before it waits in the notifications, s. */
        val choiceSeconds: Int = 15,
        /** Length of a sample operation (Notifiche di prova), s. */
        val sampleSeconds: Int = 5,
    )

    @Volatile private var cached: Config? = null

    fun get(ctx: Context): Config = cached ?: read(ctx).also { cached = it }

    private fun read(ctx: Context): Config {
        val p = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val d = Config()
        return Config(
            template = p.getString("template", d.template) ?: d.template,
            buttons = p.getString("buttons", d.buttons) ?: d.buttons,
            highChannel = p.getBoolean("highChannel", d.highChannel),
            liveUpdate = p.getBoolean("liveUpdate", d.liveUpdate),
            padding = p.getBoolean("padding", d.padding),
            shortLabel = p.getBoolean("shortLabel", d.shortLabel),
            smallWindow = p.getString("smallWindow", d.smallWindow) ?: d.smallWindow,
            intervalMs = p.getInt("intervalMs", d.intervalMs),
            floatOnUpdate = p.getBoolean("floatOnUpdate", d.floatOnUpdate),
            progressBar = p.getBoolean("progressBar", d.progressBar),
            floatOnChoice = p.getBoolean("floatOnChoice", d.floatOnChoice),
            resultSeconds = p.getInt("resultSeconds", d.resultSeconds),
            choiceSeconds = p.getInt("choiceSeconds", d.choiceSeconds),
            sampleSeconds = p.getInt("sampleSeconds", d.sampleSeconds),
        )
    }

    fun set(ctx: Context, c: Config) {
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putString("template", c.template)
            .putString("buttons", c.buttons)
            .putBoolean("highChannel", c.highChannel)
            .putBoolean("liveUpdate", c.liveUpdate)
            .putBoolean("padding", c.padding)
            .putBoolean("shortLabel", c.shortLabel)
            .putString("smallWindow", c.smallWindow)
            .putInt("intervalMs", c.intervalMs)
            .putBoolean("floatOnUpdate", c.floatOnUpdate)
            .putBoolean("progressBar", c.progressBar)
            .putBoolean("floatOnChoice", c.floatOnChoice)
            .putInt("resultSeconds", c.resultSeconds)
            .putInt("choiceSeconds", c.choiceSeconds)
            .putInt("sampleSeconds", c.sampleSeconds)
            .apply()
        cached = c
    }

    fun reset(ctx: Context) = set(ctx, Config())
}
