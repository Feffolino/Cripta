package com.cripta.app

import android.content.Context
import androidx.annotation.PluralsRes
import androidx.annotation.StringRes

/**
 * The app's texts: English in res/values, Italian in res/values-it, in the language the system
 * (or Android 13+'s per-app language setting) picks. Read through the application context so the
 * same call works in composables, view models, services and notifications. A language change
 * recreates the activity, so the screens read the new texts.
 */
object Strings {
    @Volatile private var app: Context? = null

    fun init(context: Context) { app = context.applicationContext }

    val context: Context get() = app ?: error("Strings.init was not called")
}

fun str(@StringRes id: Int): String = Strings.context.getString(id)

fun str(@StringRes id: Int, vararg args: Any?): String = Strings.context.getString(id, *args)

/** A counted text; with no [args] the count itself fills its placeholder. */
fun plural(@PluralsRes id: Int, count: Int, vararg args: Any?): String =
    if (args.isEmpty()) Strings.context.resources.getQuantityString(id, count, count)
    else Strings.context.resources.getQuantityString(id, count, *args)
