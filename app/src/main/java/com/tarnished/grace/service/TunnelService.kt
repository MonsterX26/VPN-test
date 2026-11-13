package com.tarnished.grace.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.net.VpnService
import android.os.Build
import android.os.ParcelFileDescriptor
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import com.tarnished.grace.Core
import com.tarnished.grace.MainActivity
import com.tarnished.grace.R
import com.tarnished.grace.data.Subscription
import com.tarnished.grace.data.VlessConfig
import hev.htproxy.TProxyService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import java.io.File

class TunnelService : VpnService() {
    private var tun: ParcelFileDescriptor? = null
    private var scope: CoroutineScope? = null
    private var lastText = ""

    @Volatile private var started = false
    @Volatile private var stopped = false

    override fun onCreate() {
        super.onCreate()
        if (Build.VERSION.SDK_INT >= 26) {
            val channel = NotificationChannel(CHANNEL, "Grace Tunnel", NotificationManager.IMPORTANCE_LOW)
            (getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager).createNotificationChannel(channel)
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            shutdown()
            return START_NOT_STICKY
        }
        if (!started) {
            started = true
            stopped = false
            promote("La grâce s'éveille")
            Thread { boot() }.start()
        }
        return START_NOT_STICKY
    }

    private fun promote(text: String) {
        val n = notification(text)
        if (Build.VERSION.SDK_INT >= 34) {
            startForeground(NOTIFICATION_ID, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_SYSTEM_EXEMPTED)
        } else {
            startForeground(NOTIFICATION_ID, n)
        }
    }

    private fun notification(text: String): Notification {
        val immutable = if (Build.VERSION.SDK_INT >= 23) PendingIntent.FLAG_IMMUTABLE else 0
        val open = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java), immutable or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val stop = PendingIntent.getService(
            this, 1, Intent(this, TunnelService::class.java).setAction(ACTION_STOP),
            immutable or PendingIntent.FLAG_UPDATE_CURRENT
        )
        return NotificationCompat.Builder(this, CHANNEL)
            .setSmallIcon(R.drawable.ic_stat)
            .setContentTitle("Grace Tunnel")
            .setContentText(text)
            .setContentIntent(open)
            .addAction(0, "Éteindre", stop)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .build()
    }

    private fun boot() {
        val store = Core.store
        val engine = Core.engine
        var raw = store.links
        val subscription = store.subscription
        if (subscription.isNotBlank()) {
            Subscription.fetch(subscription)?.let {
                raw = it
                store.links = it
            }
        }
        val configs = VlessConfig.parseAll(raw)
        if (configs.isEmpty()) {
            engine.fail("Aucune config VLESS enregistrée.")
            shutdown()
            return
        }
        val error = engine.start(configs, Core.HTTP_PORT, Core.SOCKS_PORT)
        if (error != null) {
            engine.fail(error)
            shutdown()
            return
        }
        val tunError = openTun()
        if (tunError != null) {
            engine.fail(tunError)
            shutdown()
            return
        }
        engine.markOn()
        val s = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        scope = s
        s.launch {
            engine.state.collect { ui ->
                val text = when {
                    ui.allDead -> "Connexion perdue"
                    ui.activeName != null -> "${ui.activeName} · ${ui.activeMs ?: 0} ms"
                    else -> "La grâce s'éveille"
                }
                if (text != lastText && !stopped) {
                    lastText = text
                    (getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager)
                        .notify(NOTIFICATION_ID, notification(text))
                }
            }
        }
    }

    private fun openTun(): String? {
        return try {
            val builder = Builder()
                .setSession("Grace Tunnel")
                .setMtu(1500)
                .addAddress("10.0.68.2", 24)
                .addRoute("0.0.0.0", 0)
                .addDnsServer("1.1.1.1")
                .addDnsServer("8.8.8.8")
            runCatching {
                builder.addAddress("fd00:68::2", 128)
                builder.addRoute("::", 0)
            }
            runCatching { builder.addDisallowedApplication(packageName) }
            val pfd = builder.establish() ?: return "Autorisation VPN refusée ou révoquée."
            tun = pfd
            val yml = File(filesDir, "hev.yml")
            yml.writeText(
                """
                tunnel:
                  mtu: 1500
                  ipv4: 10.0.68.2
                socks5:
                  port: ${Core.SOCKS_PORT}
                  address: 127.0.0.1
                  udp: 'udp'
                misc:
                  task-stack-size: 24576
                  log-level: warn
                """.trimIndent()
            )
            TProxyService.TProxyStartService(yml.absolutePath, pfd.fd)
            null
        } catch (t: Throwable) {
            "Interface VPN indisponible : ${t.message}"
        }
    }

    private fun shutdown() {
        synchronized(this) {
            if (stopped) return
            stopped = true
        }
        scope?.cancel()
        scope = null
        runCatching { TProxyService.TProxyStopService() }
        Core.engine.stop()
        runCatching { tun?.close() }
        tun = null
        started = false
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    override fun onRevoke() {
        shutdown()
    }

    override fun onDestroy() {
        shutdown()
        super.onDestroy()
    }

    companion object {
        const val ACTION_STOP = "com.tarnished.grace.STOP"
        private const val CHANNEL = "grace"
        private const val NOTIFICATION_ID = 7
    }
}
