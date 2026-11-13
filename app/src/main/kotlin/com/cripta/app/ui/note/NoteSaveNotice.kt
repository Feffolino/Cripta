package com.cripta.app.ui.note

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import com.cripta.app.R
import com.cripta.app.str
import com.cripta.app.work.ConversionService

/**
 * The note being written could not be saved when Cripta went to the background: said in a
 * notification, since the editor's message is only seen on coming back, and Android may close
 * Cripta meanwhile. Never the note's title or text: only that it was not saved.
 */
internal object NoteSaveNotice {
    const val ID = 4229

    fun post(context: Context) {
        val mgr = context.getSystemService(NotificationManager::class.java) ?: return
        if (mgr.getNotificationChannel(ConversionService.RESULT_CHANNEL) == null) {
            mgr.createNotificationChannel(
                NotificationChannel(ConversionService.RESULT_CHANNEL, str(R.string.ch_done), NotificationManager.IMPORTANCE_DEFAULT)
            )
        }
        val title = str(R.string.note_unsaved_title)
        val text = str(R.string.note_unsaved_text)
        val open = PendingIntent.getActivity(
            context, 13,
            Intent(context, com.cripta.app.MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val public = NotificationCompat.Builder(context, ConversionService.RESULT_CHANNEL)
            .setSmallIcon(R.drawable.ic_notification)
            .setColor(ConversionService.BRAND_COLOR)
            .setContentTitle(title)
            .build()
        val n = NotificationCompat.Builder(context, ConversionService.RESULT_CHANNEL)
            .setSmallIcon(R.drawable.ic_notification)
            .setColor(ConversionService.BRAND_COLOR)
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setCategory(NotificationCompat.CATEGORY_ERROR)
            .setContentIntent(open)
            .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
            .setPublicVersion(public)
            .setAutoCancel(true)
            .build()
        // Notifications turned off: the editor still says it on coming back.
        runCatching { mgr.notify(ID, n) }
    }

    /** Back in Cripta, the editor says it itself. */
    fun cancel(context: Context) {
        runCatching { context.getSystemService(NotificationManager::class.java)?.cancel(ID) }
    }
}
