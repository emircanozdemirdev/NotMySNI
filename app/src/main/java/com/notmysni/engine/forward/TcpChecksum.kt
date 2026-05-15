package com.notmysni.engine.forward

internal object TcpChecksum {

    fun apply(packet: ByteArray, totalLength: Int, tcpOffset: Int, tcpLength: Int) {
        packet[tcpOffset + 16] = 0
        packet[tcpOffset + 17] = 0

        var sum = 0
        sum += ((packet[12].toInt() and 0xFF) shl 8) or (packet[13].toInt() and 0xFF)
        sum += ((packet[14].toInt() and 0xFF) shl 8) or (packet[15].toInt() and 0xFF)
        sum += ((packet[16].toInt() and 0xFF) shl 8) or (packet[17].toInt() and 0xFF)
        sum += ((packet[18].toInt() and 0xFF) shl 8) or (packet[19].toInt() and 0xFF)
        sum += Ipv4Packet.PROTOCOL_TCP
        sum += tcpLength

        var i = tcpOffset
        val end = tcpOffset + tcpLength
        while (i < end) {
            val high = packet[i].toInt() and 0xFF
            val low = if (i + 1 < end) packet[i + 1].toInt() and 0xFF else 0
            sum += (high shl 8) or low
            i += 2
        }

        while (sum shr 16 != 0) {
            sum = (sum and 0xFFFF) + (sum shr 16)
        }
        val checksum = sum.inv() and 0xFFFF
        packet[tcpOffset + 16] = ((checksum shr 8) and 0xFF).toByte()
        packet[tcpOffset + 17] = (checksum and 0xFF).toByte()
    }
}
