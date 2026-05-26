package com.notmysni.vpn

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
import com.notmysni.MainActivity
import com.notmysni.R
import com.notmysni.engine.EngineSettingsHolder
import com.notmysni.engine.forward.UserSpacePacketForwarder
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import java.io.FileInputStream
import java.io.FileOutputStream

class LocalVpnService : VpnService() {

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var packetForwarder: UserSpacePacketForwarder? = null
    private var tunInterface: ParcelFileDescriptor? = null

    override fun onCreate() {
        super.onCreate()
        createNotificationChannelIfNeeded()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> startVpnTunnel()
            ACTION_STOP -> stopVpnTunnel()
        }
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        stopPacketLoop()
        serviceScope.cancel()
        super.onDestroy()
    }

    private fun startVpnTunnel() {
        if (tunInterface != null) return

        val connectingNotification = buildNotification(
            getString(R.string.vpn_notification_connecting)
        )
        startForegroundWithType(connectingNotification)

        val pfd = try {
            Builder()
                .setSession(getString(R.string.app_name))
                .addAddress(VPN_ADDRESS, VPN_PREFIX_LENGTH)
                .addRoute(DEFAULT_ROUTE, DEFAULT_ROUTE_PREFIX)
                .setMtu(VPN_MTU_BYTES)
                .establish()
        } catch (_: Exception) {
            null
        }

        if (pfd == null) {
            ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
            stopSelf()
            return
        }

        tunInterface = pfd
        startPacketLoop(pfd)
        val nm = getSystemService(NotificationManager::class.java)
        nm.notify(NOTIFICATION_ID, buildNotification(getString(R.string.vpn_notification_connected)))
    }

    private fun startPacketLoop(pfd: ParcelFileDescriptor) {
        val forwarder = UserSpacePacketForwarder(
            scope = serviceScope,
            protector = VpnProtector(this),
            mtu = VPN_MTU_BYTES,
            dpiEngineConfig = EngineSettingsHolder.config
        )
        packetForwarder = forwarder
        val tunInput = FileInputStream(pfd.fileDescriptor)
        val tunOutput = FileOutputStream(pfd.fileDescriptor)
        forwarder.start(tunInput, tunOutput)
    }

    private fun stopPacketLoop() {
        packetForwarder?.stop()
        packetForwarder = null
        tunInterface?.close()
        tunInterface = null
    }

    private fun stopVpnTunnel() {
        stopPacketLoop()
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun startForegroundWithType(notification: Notification) {
        val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
        } else {
            0
        }
        ServiceCompat.startForeground(this, NOTIFICATION_ID, notification, type)
    }

    private fun createNotificationChannelIfNeeded() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val channel = NotificationChannel(
            CHANNEL_ID,
            getString(R.string.vpn_notification_channel_name),
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = getString(R.string.vpn_notification_channel_description)
        }
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(channel)
    }

    private fun buildNotification(contentText: String): Notification {
        val launchPendingIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_vpn)
            .setContentTitle(getString(R.string.vpn_notification_title))
            .setContentText(contentText)
            .setContentIntent(launchPendingIntent)
            .setOngoing(true)
            .setCategory(Notification.CATEGORY_SERVICE)
            .build()
    }

    companion object {
        const val ACTION_START = "com.notmysni.vpn.action.START"
        const val ACTION_STOP = "com.notmysni.vpn.action.STOP"

        private const val CHANNEL_ID = "vpn_foreground"
        private const val NOTIFICATION_ID = 1

        private const val VPN_ADDRESS = "10.0.0.2"
        private const val VPN_PREFIX_LENGTH = 32
        private const val DEFAULT_ROUTE = "0.0.0.0"
        private const val DEFAULT_ROUTE_PREFIX = 0
        private const val VPN_MTU_BYTES = 1500

        fun buildStartIntent(context: Context): Intent =
            Intent(context, LocalVpnService::class.java).setAction(ACTION_START)

        fun buildStopIntent(context: Context): Intent =
            Intent(context, LocalVpnService::class.java).setAction(ACTION_STOP)
    }
}
