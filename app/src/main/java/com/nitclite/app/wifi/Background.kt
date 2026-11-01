package com.nitclite.app.wifi

import android.app.PendingIntent
import android.app.job.JobInfo
import android.app.job.JobParameters
import android.app.job.JobScheduler
import android.app.job.JobService
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import kotlin.concurrent.thread

/**
 * Keeps watch without a running service:
 * - a PendingIntent network callback wakes [NetReceiver] the moment any WiFi connects, even if the app is closed;
 * - a 15-minute job re-checks, in case the portal session expires while you stay connected.
 */
object WifiWatch {
    private const val JOB_ID = 1

    fun sync(ctx: Context) {
        val c = ctx.applicationContext
        if (WifiPrefs(c).enabled) start(c) else stop(c)
    }

    private fun intent(ctx: Context) = PendingIntent.getBroadcast(
        ctx, 7, Intent(ctx, NetReceiver::class.java),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE,
    )

    private fun start(ctx: Context) {
        val cm = ctx.getSystemService(ConnectivityManager::class.java)
        val req = NetworkRequest.Builder().addTransportType(NetworkCapabilities.TRANSPORT_WIFI).build()
        try { cm.registerNetworkCallback(req, intent(ctx)) } catch (_: Exception) {}
        val js = ctx.getSystemService(JobScheduler::class.java)
        if (js.getPendingJob(JOB_ID) == null) {
            js.schedule(
                JobInfo.Builder(JOB_ID, ComponentName(ctx, CheckJob::class.java))
                    .setPeriodic(15 * 60 * 1000L)
                    .setPersisted(true)
                    .build(),
            )
        }
    }

    private fun stop(ctx: Context) {
        try { ctx.getSystemService(ConnectivityManager::class.java).unregisterNetworkCallback(intent(ctx)) } catch (_: Exception) {}
        ctx.getSystemService(JobScheduler::class.java).cancel(JOB_ID)
    }
}

class NetReceiver : BroadcastReceiver() {
    override fun onReceive(ctx: Context, intent: Intent) {
        val app = ctx.applicationContext
        if (!WifiPrefs(app).enabled) return
        val pending = goAsync()
        thread {
            try {
                Thread.sleep(800)
                Portal.run(app, Portal.Mode.ON_CONNECT)
            } catch (_: Exception) {
            } finally {
                pending.finish()
            }
        }
    }
}

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(ctx: Context, intent: Intent) = WifiWatch.sync(ctx)
}

class CheckJob : JobService() {
    override fun onStartJob(params: JobParameters): Boolean {
        val app = applicationContext
        thread {
            try { if (WifiPrefs(app).enabled) Portal.run(app, Portal.Mode.PERIODIC) } catch (_: Exception) {}
            jobFinished(params, false)
        }
        return true
    }

    override fun onStopJob(params: JobParameters) = false
}

/** Quick Settings tile: one tap to sign in, from anywhere. */
class LoginTile : TileService() {
    override fun onStartListening() {
        qsTile?.let {
            it.state = Tile.STATE_INACTIVE
            if (Build.VERSION.SDK_INT >= 29) it.subtitle = "Tap to sign in"
            it.updateTile()
        }
    }

    override fun onClick() {
        val t = qsTile ?: return
        t.state = Tile.STATE_ACTIVE
        if (Build.VERSION.SDK_INT >= 29) t.subtitle = "Signing in…"
        t.updateTile()
        val app = applicationContext
        thread {
            val msg = try { Portal.run(app, Portal.Mode.MANUAL) } catch (e: Exception) { "Failed" }
            Handler(Looper.getMainLooper()).post {
                qsTile?.let {
                    it.state = Tile.STATE_INACTIVE
                    if (Build.VERSION.SDK_INT >= 29) it.subtitle = msg.take(28)
                    it.updateTile()
                }
            }
        }
    }
}
