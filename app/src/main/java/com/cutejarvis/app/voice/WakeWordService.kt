package com.cutejarvis.app.voice

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.drawable.Icon
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import com.cutejarvis.app.MainActivity

/**
 * Foreground service (type "microphone") that keeps the on-device wake-word detector alive while the app is in the
 * background. Its persistent notification is the visible "microphone is on" indicator and has a "Turn off" button.
 *
 * Android 14+ only lets an app START a microphone foreground service while it is visible, so this is started from the
 * Settings toggle (or when the app is opened); it can't be auto-started at boot or from the background.
 */
class WakeWordService : Service() {
    companion object {
        const val ACTION_STOP = "com.cutejarvis.app.action.STOP_WAKE"
        private const val CHANNEL = "wake_listening"
        private const val NOTIF_ID = 4107
        private const val AUTO_OFF_MS = 30L * 60 * 1000

        @Volatile var instance: WakeWordService? = null; private set
        val running get() = instance != null

        fun start(ctx: Context) { ctx.startForegroundService(Intent(ctx, WakeWordService::class.java)) }
        fun stop(ctx: Context) { ctx.stopService(Intent(ctx, WakeWordService::class.java)) }
        fun refresh() { instance?.updateNotification() }
        fun touch() { instance?.scheduleAutoOff() }
    }

    private val main = Handler(Looper.getMainLooper())
    private var foregroundOk = false
    private val autoOff = Runnable {
        VoiceHub.wakeAutoOff()
        notifyOnce("Wake word turned off to save battery", "Open Jarvis and switch it on again in Voice Settings.")
        stopSelf()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        instance = this
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(NotificationChannel(CHANNEL, "Wake word listening", NotificationManager.IMPORTANCE_LOW).apply {
            description = "Shown while Jarvis is listening for its wake phrase."; setShowBadge(false)
        })
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) { VoiceHub.wakeStoppedByUser(); stopSelf(); return START_NOT_STICKY }
        if (!foregroundOk) {
            try {
                if (Build.VERSION.SDK_INT >= 29) startForeground(NOTIF_ID, build(), ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE)
                else startForeground(NOTIF_ID, build())
                foregroundOk = true
            } catch (e: Exception) {
                VoiceHub.setWakeStatus("Android wouldn't start the microphone service: ${e.message}")
                VoiceHub.wakeStoppedByUser(); stopSelf(); return START_NOT_STICKY
            }
        }
        VoiceHub.attachWake()
        scheduleAutoOff()
        return START_NOT_STICKY // never silently restarted in the background (Android 14+ forbids that for the microphone)
    }

    override fun onDestroy() {
        main.removeCallbacks(autoOff)
        instance = null
        VoiceHub.detachWake()
        stopForeground(STOP_FOREGROUND_REMOVE)
        super.onDestroy()
    }

    fun scheduleAutoOff() {
        main.removeCallbacks(autoOff)
        if (VoiceHub.settings.batterySaver) main.postDelayed(autoOff, AUTO_OFF_MS)
    }

    fun updateNotification() { if (foregroundOk) getSystemService(NotificationManager::class.java).notify(NOTIF_ID, build()) }

    private fun build(): Notification {
        val open = PendingIntent.getActivity(this, 0, Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        val stop = PendingIntent.getService(this, 1, Intent(this, WakeWordService::class.java).setAction(ACTION_STOP), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        val b = Notification.Builder(this, CHANNEL)
            .setSmallIcon(android.R.drawable.ic_btn_speak_now)
            .setContentTitle("Jarvis microphone is on")
            .setContentText(VoiceHub.statusLine())
            .setContentIntent(open).setOngoing(true).setOnlyAlertOnce(true).setCategory(Notification.CATEGORY_SERVICE)
            .addAction(Notification.Action.Builder(Icon.createWithResource(this, android.R.drawable.ic_menu_close_clear_cancel), "Turn off", stop).build())
        if (Build.VERSION.SDK_INT >= 31) b.setForegroundServiceBehavior(Notification.FOREGROUND_SERVICE_IMMEDIATE)
        return b.build()
    }

    private fun notifyOnce(title: String, text: String) {
        val n = Notification.Builder(this, CHANNEL).setSmallIcon(android.R.drawable.ic_btn_speak_now).setContentTitle(title).setContentText(text).setAutoCancel(true).build()
        getSystemService(NotificationManager::class.java).notify(NOTIF_ID + 1, n)
    }
}
