package nl.marktsniper

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Build
import android.os.IBinder
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.net.URL

/** Achtergronddienst die blijft zoeken, ook als de app dicht is. */
class SnipeService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var loop: Job? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Store.init(filesDir)
        Notifier.channels(this)
        val n = Notifier.status(this, "Zoekt op de achtergrond…")
        if (Build.VERSION.SDK_INT >= 34) startForeground(1, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        else startForeground(1, n)
        if (intent?.action == ACTION_NOW) scope.launch { Sniper.checkAll { w, h -> Notifier.deal(this@SnipeService, w, h) } }
        if (loop?.isActive != true) loop = scope.launch {
            while (isActive) {
                Sniper.checkAll { w, h -> Notifier.deal(this@SnipeService, w, h) }
                delay(Store.intervalMinutes.value * 60_000L)
            }
        }
        return START_STICKY
    }

    override fun onDestroy() { scope.cancel(); super.onDestroy() }

    companion object {
        const val ACTION_NOW = "nl.marktsniper.NOW"

        fun start(ctx: Context, now: Boolean = false) {
            val i = Intent(ctx, SnipeService::class.java).apply { if (now) action = ACTION_NOW }
            runCatching { ctx.startForegroundService(i) }
        }

        fun stop(ctx: Context) = ctx.stopService(Intent(ctx, SnipeService::class.java))
    }
}

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(ctx: Context, intent: Intent) {
        Store.init(ctx.filesDir)
        if (Store.running.value) SnipeService.start(ctx)
    }
}

object Notifier {
    private const val DEALS = "deals"
    private const val STATUS = "status"

    fun channels(ctx: Context) {
        val nm = ctx.getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(NotificationChannel(DEALS, "Koopjes", NotificationManager.IMPORTANCE_HIGH))
        nm.createNotificationChannel(NotificationChannel(STATUS, "Achtergrond zoeken", NotificationManager.IMPORTANCE_MIN))
    }

    fun status(ctx: Context, text: String): Notification =
        Notification.Builder(ctx, STATUS).setSmallIcon(R.drawable.ic_stat).setContentTitle("MarktSniper actief")
            .setContentText(text).setOngoing(true)
            .setContentIntent(PendingIntent.getActivity(ctx, 0, Intent(ctx, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE))
            .build()

    fun deal(ctx: Context, w: Watch, h: Hit) {
        val open = PendingIntent.getActivity(ctx, h.id.hashCode(),
            Intent(Intent.ACTION_VIEW, Uri.parse(h.url)), PendingIntent.FLAG_IMMUTABLE)
        val img: Bitmap? = h.image?.let { runCatching { URL(it).openStream().use(BitmapFactory::decodeStream) }.getOrNull() }
        val b = Notification.Builder(ctx, DEALS).setSmallIcon(R.drawable.ic_stat)
            .setColor(0xFFFF6A3D.toInt())
            .setContentTitle("🎯 €${"%.0f".format(h.price)} · ${w.name}")
            .setContentText("${h.title} · ${h.platform}")
            .setAutoCancel(true).setContentIntent(open)
        if (img != null) b.setLargeIcon(img).setStyle(Notification.BigPictureStyle().bigPicture(img))
        runCatching { ctx.getSystemService(NotificationManager::class.java).notify(h.id.hashCode(), b.build()) }
    }
}
