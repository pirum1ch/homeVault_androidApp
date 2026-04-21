package com.homevalt.app.util

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.ServiceInfo
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.work.ForegroundInfo

object NotificationHelper {
    fun createChannel(context: Context) {
        val channel = NotificationChannel(
            Constants.NOTIFICATION_CHANNEL_ID,
            Constants.NOTIFICATION_CHANNEL_NAME,
            NotificationManager.IMPORTANCE_HIGH
        ).apply { description = "File transfer progress" }
        context.getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    fun buildUploadNotification(context: Context, fileName: String, progress: Int = -1): Notification {
        val builder = NotificationCompat.Builder(context, Constants.NOTIFICATION_CHANNEL_ID)
            .setContentTitle("Uploading")
            .setContentText(fileName)
            .setSmallIcon(android.R.drawable.ic_menu_upload)
            .setOngoing(true)
        if (progress >= 0) builder.setProgress(100, progress, false)
        else builder.setProgress(0, 0, true)
        return builder.build()
    }

    fun buildDownloadNotification(context: Context, fileName: String): Notification =
        NotificationCompat.Builder(context, Constants.NOTIFICATION_CHANNEL_ID)
            .setContentTitle("Downloading")
            .setContentText(fileName)
            .setSmallIcon(android.R.drawable.ic_menu_save)
            .setOngoing(true)
            .setProgress(0, 0, true)
            .build()

    fun buildCompletionNotification(context: Context, title: String, text: String): Notification =
        NotificationCompat.Builder(context, Constants.NOTIFICATION_CHANNEL_ID)
            .setContentTitle(title)
            .setContentText(text)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setAutoCancel(true)
            .build()

    fun buildErrorNotification(context: Context, title: String, text: String): Notification =
        NotificationCompat.Builder(context, Constants.NOTIFICATION_CHANNEL_ID)
            .setContentTitle(title)
            .setContentText(text)
            .setSmallIcon(android.R.drawable.ic_dialog_alert)
            .setAutoCancel(true)
            .build()

    fun uploadForegroundInfo(context: Context, fileName: String): ForegroundInfo =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            ForegroundInfo(
                Constants.UPLOAD_NOTIFICATION_ID,
                buildUploadNotification(context, fileName),
                ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
            )
        } else {
            ForegroundInfo(Constants.UPLOAD_NOTIFICATION_ID, buildUploadNotification(context, fileName))
        }

    fun downloadForegroundInfo(context: Context, fileName: String): ForegroundInfo =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            ForegroundInfo(
                Constants.DOWNLOAD_NOTIFICATION_ID,
                buildDownloadNotification(context, fileName),
                ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
            )
        } else {
            ForegroundInfo(Constants.DOWNLOAD_NOTIFICATION_ID, buildDownloadNotification(context, fileName))
        }

    fun showNotification(context: Context, id: Int, notification: Notification) {
        context.getSystemService(NotificationManager::class.java).notify(id, notification)
    }
}
