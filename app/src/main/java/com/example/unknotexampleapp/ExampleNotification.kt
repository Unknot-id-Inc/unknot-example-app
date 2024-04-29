package com.example.unknotexampleapp

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.ComponentName
import android.content.Context
import android.content.Context.NOTIFICATION_SERVICE
import android.content.Intent
import android.view.View
import android.widget.RemoteViews
import kotlinx.coroutines.runBlocking
import org.unknot.android_sdk.SdkArgs

class ExampleNotification(private val ctx: Context, val channel: NotificationChannel = DefaultChannel) {
    companion object {
        const val CHANNEL_ID = "UnknotExampleNotificationChanId"
        val DefaultChannel = NotificationChannel(
            CHANNEL_ID,
            "Unknot Example Notification",
            NotificationManager.IMPORTANCE_HIGH
        )
    }

    private val builder: Notification.Builder get() = Notification.Builder(ctx, channel.id)
        .setContentIntent(
            PendingIntent.getActivity(
                ctx, 0,
                Intent().setComponent(ComponentName(ctx, MainActivity::class.java)),
                PendingIntent.FLAG_IMMUTABLE
            )
        )
        .setSmallIcon(R.drawable.unknot_logo)
        .setContentTitle("Unknot Example App")
        .setStyle(Notification.DecoratedCustomViewStyle())

    fun registerChannel() {
        (ctx.getSystemService(NOTIFICATION_SERVICE) as NotificationManager)
            .createNotificationChannel(channel)
    }

    fun getNotification(content: String): Notification = builder
        .setContentText(content)
        .build()
}