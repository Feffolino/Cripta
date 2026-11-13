package com.cripta.app.util

import kotlin.coroutines.cancellation.CancellationException

/**
 * [runCatching] for suspending work: a cancellation goes on up instead of becoming a failure. In a
 * produceState, the cancelled producer of an old key caught it and wrote its null over the value
 * the new producer had already set.
 */
suspend inline fun <T> catching(crossinline block: suspend () -> T): Result<T> =
    try {
        Result.success(block())
    } catch (e: CancellationException) {
        throw e
    } catch (e: Throwable) {
        Result.failure(e)
    }
