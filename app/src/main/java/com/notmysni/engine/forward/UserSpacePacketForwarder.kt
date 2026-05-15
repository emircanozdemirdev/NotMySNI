package com.notmysni.engine.forward

import com.notmysni.vpn.TunOutput
import com.notmysni.vpn.VpnProtector
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.FileInputStream
import java.io.FileOutputStream
import java.net.InetSocketAddress
import java.nio.ByteBuffer
import java.nio.channels.DatagramChannel
import java.nio.channels.SocketChannel
import java.util.concurrent.ConcurrentHashMap

/**
 * Reads IPv4 packets from TUN, forwards TCP/UDP via protected sockets, writes replies to TUN.
 * Full routing and DPI logic will extend this in later phases.
 */
class UserSpacePacketForwarder(
    private val scope: CoroutineScope,
    private val protector: VpnProtector,
    private val mtu: Int
) {
    private val tcpConnections = ConcurrentHashMap<ConnectionKey, TcpSession>()
    private val udpChannels = ConcurrentHashMap<ConnectionKey, DatagramChannel>()

    private var readJob: Job? = null

    fun start(tunInput: FileInputStream, tunOutputStream: FileOutputStream) {
        val tunOut = TunOutput(tunOutputStream)
        readJob = scope.launch(Dispatchers.IO) {
            val packetBuffer = ByteBuffer.allocate(mtu)
            val packet = ByteArray(mtu)
            while (isActive) {
                packetBuffer.clear()
                val read = tunInput.read(packet)
                if (read <= 0) continue
                packetBuffer.put(packet, 0, read)
                packetBuffer.flip()
                handlePacket(packet, read, tunOut)
            }
        }
    }

    fun stop() {
        readJob?.cancel()
        readJob = null
        tcpConnections.values.forEach { it.close() }
        tcpConnections.clear()
        udpChannels.values.forEach { channel ->
            runCatching { channel.close() }
        }
        udpChannels.clear()
    }

    private fun handlePacket(packet: ByteArray, length: Int, tunOut: TunOutput) {
        if (Ipv4Packet.version(packet, length) != 4) return
        when (Ipv4Packet.protocol(packet, length)) {
            Ipv4Packet.PROTOCOL_TCP -> handleTcp(packet, length, tunOut)
            Ipv4Packet.PROTOCOL_UDP -> handleUdp(packet, length, tunOut)
        }
    }

    private fun handleTcp(packet: ByteArray, length: Int, tunOut: TunOutput) {
        val srcIp = Ipv4Packet.sourceAddress(packet, length) ?: return
        val dstIp = Ipv4Packet.destinationAddress(packet, length) ?: return
        val srcPort = Ipv4Packet.sourcePort(packet, length)
        val dstPort = Ipv4Packet.destinationPort(packet, length)
        if (srcPort < 0 || dstPort < 0) return

        val key = ConnectionKey(srcIp, srcPort, dstIp, dstPort)
        val flags = Ipv4Packet.tcpFlags(packet, length)
        val isSyn = (flags and 0x02) != 0
        val isFin = (flags and 0x01) != 0
        val isRst = (flags and 0x04) != 0

        if (isFin || isRst) {
            tcpConnections.remove(key)?.close()
            return
        }

        var session = tcpConnections[key]
        if (session == null) {
            if (!isSyn) return
            session = TcpSession(
                scope = scope,
                protector = protector,
                key = key,
                tunOut = tunOut,
                onClosed = { tcpConnections.remove(key) }
            ).also { newSession ->
                tcpConnections[key] = newSession
                newSession.connect(
                    Ipv4Packet.addressToString(dstIp),
                    dstPort
                )
            }
        }

        val payloadOffset = Ipv4Packet.tcpPayloadOffset(packet, length)
        if (payloadOffset < 0 || payloadOffset >= length) return
        val payloadLength = length - payloadOffset
        if (payloadLength > 0) {
            session.sendToRemote(packet, payloadOffset, payloadLength)
        }
    }

    private fun handleUdp(packet: ByteArray, length: Int, tunOut: TunOutput) {
        val srcIp = Ipv4Packet.sourceAddress(packet, length) ?: return
        val dstIp = Ipv4Packet.destinationAddress(packet, length) ?: return
        val srcPort = Ipv4Packet.sourcePort(packet, length)
        val dstPort = Ipv4Packet.destinationPort(packet, length)
        if (srcPort < 0 || dstPort < 0) return

        val payloadOffset = Ipv4Packet.udpPayloadOffset(packet, length)
        if (payloadOffset < 0 || payloadOffset >= length) return
        val payloadLength = length - payloadOffset
        if (payloadLength <= 0) return

        val key = ConnectionKey(srcIp, srcPort, dstIp, dstPort)
        scope.launch(Dispatchers.IO) {
            try {
                val channel = udpChannels.getOrPut(key) {
                    DatagramChannel.open().also { ch ->
                        ch.configureBlocking(true)
                        protector.protect(ch)
                    }
                }
                val dstAddress = InetSocketAddress(
                    Ipv4Packet.addressToString(dstIp),
                    dstPort
                )
                val sendBuffer = ByteBuffer.wrap(packet, payloadOffset, payloadLength)
                channel.send(sendBuffer, dstAddress)

                val receiveBuffer = ByteBuffer.allocate(mtu)
                channel.receive(receiveBuffer) ?: return@launch
                receiveBuffer.flip()
                val responseLength = receiveBuffer.remaining()
                if (responseLength <= 0) return@launch
                val responsePayload = ByteArray(responseLength)
                receiveBuffer.get(responsePayload)
                val responsePacket = UdpResponseBuilder.build(
                    sourceIp = dstIp,
                    sourcePort = dstPort,
                    destinationIp = srcIp,
                    destinationPort = srcPort,
                    payload = responsePayload
                )
                tunOut.write(responsePacket, responsePacket.size)
            } catch (_: Exception) {
                udpChannels.remove(key)
            }
        }
    }

    private class TcpSession(
        private val scope: CoroutineScope,
        private val protector: VpnProtector,
        private val key: ConnectionKey,
        private val tunOut: TunOutput,
        private val onClosed: () -> Unit
    ) {
        private var channel: SocketChannel? = null
        private var remoteToTunJob: Job? = null

        fun connect(host: String, port: Int) {
            scope.launch(Dispatchers.IO) {
                try {
                    val socketChannel = SocketChannel.open()
                    protector.protect(socketChannel)
                    socketChannel.configureBlocking(true)
                    socketChannel.connect(InetSocketAddress(host, port))
                    channel = socketChannel
                    remoteToTunJob = scope.launch(Dispatchers.IO) {
                        val buffer = ByteArray(32768)
                        val socket = socketChannel.socket().getInputStream()
                        while (isActive) {
                            val read = socket.read(buffer)
                            if (read <= 0) break
                            val packet = TcpResponseBuilder.build(
                                sourceIp = key.destinationIp,
                                sourcePort = key.destinationPort,
                                destinationIp = key.sourceIp,
                                destinationPort = key.sourcePort,
                                payload = buffer,
                                payloadLength = read
                            )
                            tunOut.write(packet, packet.size)
                        }
                    }
                } catch (_: Exception) {
                    close()
                }
            }
        }

        fun sendToRemote(packet: ByteArray, offset: Int, length: Int) {
            scope.launch(Dispatchers.IO) {
                try {
                    channel?.write(ByteBuffer.wrap(packet, offset, length))
                } catch (_: Exception) {
                    close()
                }
            }
        }

        fun close() {
            remoteToTunJob?.cancel()
            runCatching { channel?.close() }
            channel = null
            onClosed()
        }
    }
}
