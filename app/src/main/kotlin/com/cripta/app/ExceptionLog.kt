package com.cripta.app

import android.util.Log
import java.util.Collections
import java.util.IdentityHashMap

internal fun logException(priority: Int, tag: String, message: String, error: Throwable) {
    if (BuildConfig.DEBUG) {
        when (priority) {
            Log.ERROR -> Log.e(tag, message, error)
            Log.WARN -> Log.w(tag, message, error)
            else -> Log.d(tag, message, error)
        }
    } else {
        Log.println(priority, tag, releaseExceptionMessage(message, error))
    }
}

internal fun releaseExceptionMessage(message: String, error: Throwable): String {
    val seen = Collections.newSetFromMap(IdentityHashMap<Throwable, Boolean>())
    val types = generateSequence(error) { it.cause }
        .takeWhile { seen.add(it) }
        .joinToString(" caused by ") { it.javaClass.simpleName }
    return "$message ($types)"
}
