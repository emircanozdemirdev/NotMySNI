package com.notmysni.sni

import com.notmysni.engine.parser.TlsClientHelloParser
import com.notmysni.model.TlsClientHello

/**
 * Replaces the SNI hostname in a TLS ClientHello and updates all affected length fields.
 */
object SniSpoofer {

    /**
     * Returns a new ClientHello buffer with [spoofedHostname] as the SNI host_name entry,
     * or null when the input is not a ClientHello with SNI.
     */
    fun spoof(
        clientHelloBuffer: ByteArray,
        offset: Int,
        length: Int,
        spoofedHostname: String
    ): ByteArray? {
        val parsed = TlsClientHelloParser.parse(clientHelloBuffer, offset, length) ?: return null
        return spoof(clientHelloBuffer, offset, length, spoofedHostname, parsed)
    }

    /**
     * Same as [spoof] but reuses a prior [TlsClientHello] parse result.
     */
    fun spoof(
        clientHelloBuffer: ByteArray,
        offset: Int,
        length: Int,
        spoofedHostname: String,
        parsed: TlsClientHello
    ): ByteArray? {
        if (!parsed.hasServerName) return null

        val newHostnameBytes = spoofedHostname.toByteArray(Charsets.US_ASCII)
        if (newHostnameBytes.isEmpty()) return null

        val oldHostnameLength = parsed.hostnameLength
        val delta = newHostnameBytes.size - oldHostnameLength

        val result = if (delta == 0) {
            clientHelloBuffer.copyOfRange(offset, offset + length)
        } else {
            resizeClientHelloRegion(
                source = clientHelloBuffer,
                regionStart = offset,
                regionEnd = offset + length,
                hostnameOffset = parsed.hostnameOffset,
                oldHostnameLength = oldHostnameLength,
                newHostnameBytes = newHostnameBytes
            )
        }

        val hostnameIndexInResult = parsed.hostnameOffset - offset
        newHostnameBytes.copyInto(result, hostnameIndexInResult)

        applyLengthUpdates(
            buffer = result,
            parsed = parsed,
            regionStart = offset,
            delta = delta,
            newNameLength = newHostnameBytes.size
        )
        return result
    }

    private fun resizeClientHelloRegion(
        source: ByteArray,
        regionStart: Int,
        regionEnd: Int,
        hostnameOffset: Int,
        oldHostnameLength: Int,
        newHostnameBytes: ByteArray
    ): ByteArray {
        val regionLength = regionEnd - regionStart
        val hostnameIndexInRegion = hostnameOffset - regionStart
        val tailStartInSource = hostnameOffset + oldHostnameLength

        val result = ByteArray(regionLength + newHostnameBytes.size - oldHostnameLength)
        source.copyInto(result, 0, regionStart, hostnameOffset)
        val tailDest = hostnameIndexInRegion + newHostnameBytes.size
        source.copyInto(result, tailDest, tailStartInSource, regionEnd)
        return result
    }

    private fun applyLengthUpdates(
        buffer: ByteArray,
        parsed: TlsClientHello,
        regionStart: Int,
        delta: Int,
        newNameLength: Int
    ) {
        fun rel(absoluteOffset: Int): Int = absoluteOffset - regionStart

        if (delta != 0) {
            val recordLengthIndex = rel(parsed.recordLengthOffset)
            writeUInt16(
                buffer,
                recordLengthIndex,
                readUInt16(buffer, recordLengthIndex) + delta
            )

            val handshakeLengthIndex = rel(parsed.handshakeLengthOffset)
            writeUInt24(
                buffer,
                handshakeLengthIndex,
                readUInt24(buffer, handshakeLengthIndex) + delta
            )

            val extensionsLengthIndex = rel(parsed.extensionsLengthOffset)
            writeUInt16(
                buffer,
                extensionsLengthIndex,
                readUInt16(buffer, extensionsLengthIndex) + delta
            )

            val sniListLengthIndex = rel(parsed.sniListLengthOffset)
            writeUInt16(
                buffer,
                sniListLengthIndex,
                readUInt16(buffer, sniListLengthIndex) + delta
            )
        }

        writeUInt16(buffer, rel(parsed.sniNameLengthOffset), newNameLength)
    }

    private fun readUInt16(buffer: ByteArray, offset: Int): Int =
        ((buffer[offset].toInt() and 0xFF) shl 8) or (buffer[offset + 1].toInt() and 0xFF)

    private fun readUInt24(buffer: ByteArray, offset: Int): Int =
        ((buffer[offset].toInt() and 0xFF) shl 16) or
            ((buffer[offset + 1].toInt() and 0xFF) shl 8) or
            (buffer[offset + 2].toInt() and 0xFF)

    private fun writeUInt16(buffer: ByteArray, offset: Int, value: Int) {
        buffer[offset] = ((value shr 8) and 0xFF).toByte()
        buffer[offset + 1] = (value and 0xFF).toByte()
    }

    private fun writeUInt24(buffer: ByteArray, offset: Int, value: Int) {
        buffer[offset] = ((value shr 16) and 0xFF).toByte()
        buffer[offset + 1] = ((value shr 8) and 0xFF).toByte()
        buffer[offset + 2] = (value and 0xFF).toByte()
    }
}
