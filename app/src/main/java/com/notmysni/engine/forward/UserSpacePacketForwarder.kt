package com.notmysni.engine.forward

import com.notmysni.dns.DohResolver
import com.notmysni.engine.DpiEngineConfig
import com.notmysni.engine.fragment.TcpDesyncPipeline
import com.notmysni.engine.parser.IpHeaderParser
import com.notmysni.engine.parser.TcpHeaderParser
import com.notmysni.model.IpHeader
import com.notmysni.model.TransportProtocol
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
 *
 * Step 6.2 — TTL-based desync technique
 */
class UserSpacePacketForwarder(
    private val scope: CoroutineScope,
    private val protector: VpnProtector,
    private val mtu: Int,
    private val dpiEngineConfig: DpiEngineConfig = DpiEngineConfig.Default,
    @Suppress("unused") private val dohResolver: DohResolver? = null
) {
    // Step 6.2 — TTL-based desync technique
    //
    // private fun sendWholePacketWithLowTtl(packet: ByteArray, length: Int, tunOut: TunOutput) {
    //     val copy = packet.copyOf(length)
    //     copy[8] = 1
    //     tunOut.write(copy, length)
    // }

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
        val ipHeader = IpHeaderParser.parse(packet, length) ?: return
        when (ipHeader.protocol) {
            TransportProtocol.TCP -> handleTcp(packet, length, ipHeader, tunOut)
            TransportProtocol.UDP -> handleUdp(packet, length, ipHeader, tunOut)
        }
    }

    private fun handleTcp(
        packet: ByteArray,
        length: Int,
        ipHeader: IpHeader,
        tunOut: TunOutput
    ) {
        val tcpHeader = TcpHeaderParser.parse(packet, length, ipHeader.headerLength) ?: return

        val srcIp = ipHeader.sourceAddress.bytes
        val dstIp = ipHeader.destinationAddress.bytes
        val key = ConnectionKey(
            srcIp,
            tcpHeader.sourcePort,
            dstIp,
            tcpHeader.destinationPort
        )

        if (tcpHeader.flags.fin || tcpHeader.flags.rst) {
            tcpConnections.remove(key)?.close()
            return
        }

        var session = tcpConnections[key]
        if (session == null) {
            if (!tcpHeader.flags.syn) return
            session = TcpSession(
                scope = scope,
                protector = protector,
                key = key,
                tunOut = tunOut,
                onClosed = { tcpConnections.remove(key) }
            ).also { newSession ->
                tcpConnections[key] = newSession
                newSession.connect(
                    ipHeader.destinationAddress.toDisplayString(),
                    tcpHeader.destinationPort
                )
            }
        }

        val payloadLength = tcpHeader.payloadLength(length)
        if (payloadLength > 0) {
            session.sendToRemote(packet, length)
        }
    }

    private fun handleUdp(
        packet: ByteArray,
        length: Int,
        ipHeader: IpHeader,
        tunOut: TunOutput
    ) {
        val udpHeaderLength = 8
        val payloadOffset = ipHeader.headerLength + udpHeaderLength
        if (payloadOffset >= length) return

        val payloadLength = length - payloadOffset
        if (payloadLength <= 0) return

        val srcIp = ipHeader.sourceAddress.bytes
        val dstIp = ipHeader.destinationAddress.bytes
        val srcPort = readUInt16(packet, ipHeader.headerLength)
        val dstPort = readUInt16(packet, ipHeader.headerLength + 2)

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
                    ipHeader.destinationAddress.toDisplayString(),
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

    private fun readUInt16(packet: ByteArray, offset: Int): Int =
        ((packet[offset].toInt() and 0xFF) shl 8) or (packet[offset + 1].toInt() and 0xFF)

    private inner class TcpSession(
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
                            val responsePacket = TcpResponseBuilder.build(
                                sourceIp = key.destinationIp,
                                sourcePort = key.destinationPort,
                                destinationIp = key.sourceIp,
                                destinationPort = key.sourcePort,
                                payload = buffer,
                                payloadLength = read
                            )
                            tunOut.write(responsePacket, responsePacket.size)
                        }
                    }
                } catch (_: Exception) {
                    close()
                }
            }
        }

        fun sendToRemote(packet: ByteArray, length: Int) {
            scope.launch(Dispatchers.IO) {
                try {
                    val socketChannel = channel ?: return@launch
                    val segments = TcpDesyncPipeline.prepareOutbound(
                        packet = packet,
                        length = length,
                        fragmentStrategy = dpiEngineConfig.fragmentStrategy,
                        ttlConfig = dpiEngineConfig.ttlDesync
                    ) ?: return@launch

                    val socket = socketChannel.socket()
                    val ttlConfig = dpiEngineConfig.ttlDesync

                    segments.decoyPayload?.let { decoy ->
                        IpTtl.setSocketTtl(socket, ttlConfig.fakeSegmentTtl)
                        socketChannel.write(ByteBuffer.wrap(decoy))
                    }

                    IpTtl.setSocketTtl(socket, ttlConfig.realSegmentTtl)
                    for (payload in segments.payloadsForRemote) {
                        socketChannel.write(ByteBuffer.wrap(payload))
                    }
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
