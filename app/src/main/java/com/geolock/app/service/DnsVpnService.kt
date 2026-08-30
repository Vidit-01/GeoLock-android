package com.geolock.app.service

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.net.VpnService
import android.os.ParcelFileDescriptor
import androidx.core.app.NotificationCompat
import com.geolock.app.R
import com.geolock.app.domain.DomainFilter
import com.geolock.app.domain.DomainNames
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import java.io.FileInputStream
import java.io.FileOutputStream
import java.net.DatagramSocket
import java.util.concurrent.atomic.AtomicReference
import javax.inject.Inject

@AndroidEntryPoint
class DnsVpnService : VpnService() {
    @Inject lateinit var domainFilter: DomainFilter

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var tunnel: ParcelFileDescriptor? = null
    private var reader: Job? = null
    private val blocked = AtomicReference<Set<String>>(emptySet())

    override fun onCreate() {
        super.onCreate()
        createChannel()
        startForeground(
            NOTIFICATION_ID,
            NotificationCompat.Builder(this, CHANNEL)
                .setSmallIcon(R.drawable.ic_notification)
                .setContentTitle("GeoLock domain filter")
                .setContentText("Blocking listed sites inside your zones")
                .setOngoing(true)
                .setSilent(true)
                .build()
        )
        scope.launch {
            domainFilter.activeDomains.collectLatest { domains ->
                blocked.set(domains)
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            stopTunnel()
            stopSelf()
            return START_NOT_STICKY
        }
        startTunnel()
        return START_STICKY
    }

    private fun startTunnel() {
        if (tunnel != null) return
        val builder = Builder()
            .setSession("GeoLock")
            .setMtu(1500)
            .addAddress("10.8.0.2", 32)
            .addDnsServer("10.8.0.1")
            .addRoute("10.8.0.1", 32)
            .addRoute("1.1.1.1", 32)
            .addRoute("1.0.0.1", 32)
            .addRoute("8.8.8.8", 32)
            .addRoute("8.8.4.4", 32)
            .addRoute("9.9.9.9", 32)
            .setBlocking(true)
        tunnel = builder.establish() ?: return
        val descriptor = tunnel ?: return
        reader = scope.launch {
            val input = FileInputStream(descriptor.fileDescriptor)
            val output = FileOutputStream(descriptor.fileDescriptor)
            val packet = ByteArray(32767)
            while (tunnel != null) {
                val length = runCatching { input.read(packet) }.getOrDefault(-1)
                if (length <= 0) continue
                val query = DnsPacket.extractUdpPayload(packet, length) ?: continue
                val host = query.host
                val response = if (host != null && DomainNames.matches(host, blocked.get())) {
                    DnsPacket.sinkhole(query)
                } else {
                    DnsPacket.forward(query) { socket: DatagramSocket -> protect(socket) }
                }
                if (response != null) {
                    runCatching { output.write(response) }
                }
            }
        }
    }

    private fun stopTunnel() {
        reader?.cancel()
        reader = null
        runCatching { tunnel?.close() }
        tunnel = null
    }

    override fun onDestroy() {
        stopTunnel()
        scope.cancel()
        super.onDestroy()
    }

    private fun createChannel() {
        val manager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL, "Domain filter", NotificationManager.IMPORTANCE_LOW)
        )
    }

    companion object {
        private const val NOTIFICATION_ID = 41
        private const val CHANNEL = "geolock_dns"
        const val ACTION_STOP = "com.geolock.app.STOP_DNS_VPN"

        fun start(context: Context) {
            if (needsConsent(context)) return
            context.startForegroundService(Intent(context, DnsVpnService::class.java))
        }

        fun stop(context: Context) {
            context.startService(Intent(context, DnsVpnService::class.java).setAction(ACTION_STOP))
        }

        fun needsConsent(context: Context): Boolean = prepare(context) != null
    }
}
