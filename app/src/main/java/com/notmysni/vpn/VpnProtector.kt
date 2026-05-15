package com.notmysni.vpn

import android.net.VpnService
import java.net.DatagramSocket
import java.net.Socket
import java.nio.channels.DatagramChannel
import java.nio.channels.SocketChannel

/**
 * Ensures outbound sockets bypass the VPN tunnel (prevents routing loops).
 */
class VpnProtector(private val vpnService: VpnService) {

    fun protect(socket: Socket): Boolean = vpnService.protect(socket)

    fun protect(socket: DatagramSocket): Boolean = vpnService.protect(socket)

    fun protect(channel: SocketChannel): Boolean = vpnService.protect(channel.socket())

    fun protect(channel: DatagramChannel): Boolean = vpnService.protect(channel.socket())
}
