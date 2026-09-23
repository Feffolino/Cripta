package com.cripta.app

import android.content.Context
import java.io.File

/**
 * Keeps the last uncaught crash so it can be read in Settings › Info without a computer.
 *
 * Only the technical trace is written (exception types, short messages, code lines) to the app's
 * private storage. Text in quotes is dropped from messages and each message is capped, because an
 * error can mention a file name and names of vault files must never land in plaintext on disk.
 */
object CrashLog {
    private const val FILE = "last_crash.txt"
    private const val MAX_FRAMES = 40

    fun install(context: Context) {
        val app = context.applicationContext
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, error ->
            runCatching { File(app.filesDir, FILE).writeText(format(app, thread, error)) }
            previous?.uncaughtException(thread, error)
        }
    }

    fun read(context: Context): String? =
        runCatching { File(context.filesDir, FILE).takeIf { it.exists() }?.readText() }.getOrNull()

    fun clear(context: Context) { runCatching { File(context.filesDir, FILE).delete() } }

    private fun clean(msg: String?): String =
        msg.orEmpty().replace(Regex("\"[^\"]*\"|'[^']*'"), "\"…\"").take(240)

    private fun format(context: Context, thread: Thread, error: Throwable): String = buildString {
        val version = runCatching {
            context.packageManager.getPackageInfo(context.packageName, 0).let { "${it.versionName} (${it.longVersionCode})" }
        }.getOrDefault("?")
        appendLine("Data: ${java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.ROOT).format(java.util.Date())}")
        appendLine("Versione: $version · Android ${android.os.Build.VERSION.RELEASE} (API ${android.os.Build.VERSION.SDK_INT})")
        appendLine("Thread: ${thread.name}")
        var e: Throwable? = error
        var depth = 0
        while (e != null && depth < 6) {
            appendLine((if (depth == 0) "" else "Causato da: ") + e.javaClass.name + ": " + clean(e.message))
            e.stackTrace.take(MAX_FRAMES).forEach { appendLine("    at $it") }
            e = e.cause?.takeIf { it !== e }
            depth++
        }
    }
}
