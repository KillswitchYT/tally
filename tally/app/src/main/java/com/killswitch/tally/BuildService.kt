package com.killswitch.tally

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder

/**
 * Runs builds in the background, with a notification so Android keeps it alive after you leave the app. The builds themselves run on
 * GitHub, so this only waits and then fetches the result. If Android kills the process, the sticky restart finds the unfinished builds
 * in [BuildState] and carries on.
 */
class BuildService : Service() {
    private val active = java.util.Collections.synchronizedSet(HashSet<String>())

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        val nm = getSystemService(NotificationManager::class.java)
        if (nm.getNotificationChannel(CH_WORK) == null) nm.createNotificationChannel(NotificationChannel(CH_WORK, "Builds in progress", NotificationManager.IMPORTANCE_LOW))
        if (nm.getNotificationChannel(CH_DONE) == null) nm.createNotificationChannel(NotificationChannel(CH_DONE, "Finished builds", NotificationManager.IMPORTANCE_DEFAULT))
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        BuildState.load(this)
        val first = BuildState.jobs.firstOrNull { it.stage.working }
        val n = working(first?.let { "Building " + it.name } ?: "Tally", first?.detail?.ifEmpty { "Starting" } ?: "Starting")
        try {
            if (Build.VERSION.SDK_INT >= 29) startForeground(ID, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC) else startForeground(ID, n)
        } catch (e: Exception) {
            stopSelf()
            return START_NOT_STICKY
        }
        for (j in BuildState.jobs.filter { it.stage.working }) {
            if (active.add(j.id)) Thread { work(j.id) }.start()
        }
        if (active.isEmpty()) {
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
        }
        return START_STICKY
    }

    private fun work(id: String) {
        try {
            BuildRunner.run(this, id) { j -> post(ID, working("Building " + j.name, j.detail.ifEmpty { "Working" })) }
        } catch (e: Exception) {
            BuildState.update(this, id) { it.copy(stage = Stage.Failed, error = "Something went wrong inside Tally (" + e.javaClass.simpleName + ")", detail = "") }
        } finally {
            active.remove(id)
            val j = BuildState.get(id)
            if (j != null && j.stage == Stage.Done) post(1000 + (id.hashCode() and 0xFFFF), result("Your app is ready", j.name + " is in your Downloads folder"))
            else if (j != null && j.stage == Stage.Failed) post(1000 + (id.hashCode() and 0xFFFF), result("Build failed", j.name + ": " + j.error))
            if (active.isEmpty()) {
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
            }
        }
    }

    private fun post(id: Int, n: Notification) {
        try { getSystemService(NotificationManager::class.java).notify(id, n) } catch (e: Exception) { /* notifications may be off, the Builds tab still shows everything */ }
    }

    private fun open(): PendingIntent = PendingIntent.getActivity(
        this, 0, Intent(this, MainActivity::class.java).putExtra("tab", 1).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_NEW_TASK),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

    private fun working(title: String, text: String): Notification =
        Notification.Builder(this, CH_WORK).setSmallIcon(R.drawable.ic_small).setContentTitle(title).setContentText(text)
            .setContentIntent(open()).setOngoing(true).setOnlyAlertOnce(true).build()

    private fun result(title: String, text: String): Notification =
        Notification.Builder(this, CH_DONE).setSmallIcon(R.drawable.ic_small).setContentTitle(title).setContentText(text)
            .setContentIntent(open()).setAutoCancel(true).build()

    companion object {
        private const val ID = 41
        private const val CH_WORK = "work"
        private const val CH_DONE = "done"

        /** Starts the service, which then picks up every unfinished build. Only call this while the app is on screen. */
        fun kick(c: Context) {
            try { c.startForegroundService(Intent(c, BuildService::class.java)) } catch (e: Exception) { /* shown as a stuck build, "Try again" starts it */ }
        }

        fun resume(c: Context) { if (BuildState.jobs.any { it.stage.working }) kick(c) }
    }
}
