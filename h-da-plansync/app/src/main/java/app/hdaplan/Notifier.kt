package app.hdaplan

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat

/** Meldet Planänderungen (Raum, Zeit, Dozent) als Benachrichtigung. */
object Notifier {
    private const val CH = "changes"

    fun ensureChannel(ctx: Context) {
        val nm = ctx.getSystemService(NotificationManager::class.java)
        val ch = NotificationChannel(CH, "Planänderungen", NotificationManager.IMPORTANCE_DEFAULT)
        ch.description = "Meldet geänderte Räume, Zeiten und Dozenten"
        nm.createNotificationChannel(ch)
    }

    fun post(ctx: Context, lines: List<String>) {
        if (lines.isEmpty()) return
        if (Build.VERSION.SDK_INT >= 33 &&
            ctx.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            return
        }
        try {
            ensureChannel(ctx)
            val open = PendingIntent.getActivity(
                ctx, 0, Intent(ctx, MainActivity::class.java),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            )
            val style = NotificationCompat.InboxStyle()
            for (l in lines.take(6)) style.addLine(l)
            if (lines.size > 6) style.setSummaryText("+ " + (lines.size - 6) + " weitere")
            val n = NotificationCompat.Builder(ctx, CH)
                .setSmallIcon(R.drawable.ic_notify)
                .setContentTitle("Stundenplan geändert")
                .setContentText(lines[0])
                .setStyle(style)
                .setContentIntent(open)
                .setAutoCancel(true)
                .build()
            NotificationManagerCompat.from(ctx).notify(1001, n)
        } catch (e: Exception) {
            // Benachrichtigungen sind optional
        }
    }
}
