package com.notmysni.engine.parser

import com.notmysni.model.TlsClientHello

object TlsClientHelloParser {

    /**
     * Parses a TLS handshake payload and returns [TlsClientHello] when the buffer starts with
     * a ClientHello record. Returns null if the data is not a ClientHello.
     */
    fun parse(buffer: ByteArray, offset: Int, length: Int): TlsClientHello? {
        if (length < 43) return null

        var pos = offset
        val end = offset + length

        if ((buffer[pos].toInt() and 0xFF) != TlsConstants.CONTENT_TYPE_HANDSHAKE) return null
        val recordLengthOffset = pos + 3
        pos += 1

        pos += 2 // record layer version (legacy)

        val recordLength = readUInt16(buffer, pos)
        pos += 2
        if (recordLength <= 0 || pos + recordLength > end) return null

        if ((buffer[pos].toInt() and 0xFF) != TlsConstants.HANDSHAKE_CLIENT_HELLO) return null
        val handshakeLengthOffset = pos + 1
        pos += 1

        val handshakeLength = readUInt24(buffer, pos)
        pos += 3
        val handshakeEnd = pos + handshakeLength
        if (handshakeLength <= 0 || handshakeEnd > end) return null

        pos += 2 // client_version
        pos += 32 // random

        if (pos >= handshakeEnd) return null
        val sessionIdLength = buffer[pos].toInt() and 0xFF
        pos += 1
        if (pos + sessionIdLength > handshakeEnd) return null
        pos += sessionIdLength

        if (pos + 2 > handshakeEnd) return null
        val cipherSuitesLength = readUInt16(buffer, pos)
        pos += 2
        if (pos + cipherSuitesLength > handshakeEnd) return null
        pos += cipherSuitesLength

        if (pos + 1 > handshakeEnd) return null
        val compressionMethodsLength = buffer[pos].toInt() and 0xFF
        pos += 1
        if (pos + compressionMethodsLength > handshakeEnd) return null
        pos += compressionMethodsLength

        if (pos + 2 > handshakeEnd) return null
        val extensionsLengthOffset = pos
        val extensionsLength = readUInt16(buffer, pos)
        pos += 2
        val extensionsEnd = pos + extensionsLength
        if (extensionsEnd > handshakeEnd) return null

        var sniHostname: String? = null
        var sniHostnameOffset = -1
        var sniHostnameLength = 0
        var sniListLengthOffset = -1
        var sniNameLengthOffset = -1

        while (pos + 4 <= extensionsEnd) {
            val extensionType = readUInt16(buffer, pos)
            val extensionLength = readUInt16(buffer, pos + 2)
            pos += 4
            if (pos + extensionLength > extensionsEnd) return null

            if (extensionType == TlsConstants.EXTENSION_SERVER_NAME) {
                val sni = parseServerNameExtension(buffer, pos, extensionLength)
                if (sni != null) {
                    sniHostname = sni.hostname
                    sniHostnameOffset = sni.hostnameOffset
                    sniHostnameLength = sni.hostnameLength
                    sniListLengthOffset = sni.listLengthOffset
                    sniNameLengthOffset = sni.nameLengthOffset
                }
            }
            pos += extensionLength
        }

        return TlsClientHello(
            serverName = sniHostname,
            hostnameOffset = sniHostnameOffset,
            hostnameLength = sniHostnameLength,
            recordLengthOffset = recordLengthOffset,
            handshakeLengthOffset = handshakeLengthOffset,
            extensionsLengthOffset = extensionsLengthOffset,
            sniListLengthOffset = sniListLengthOffset,
            sniNameLengthOffset = sniNameLengthOffset
        )
    }

    private data class ServerNameInfo(
        val hostname: String,
        val hostnameOffset: Int,
        val hostnameLength: Int,
        val listLengthOffset: Int,
        val nameLengthOffset: Int
    )

    private fun parseServerNameExtension(
        buffer: ByteArray,
        offset: Int,
        extensionLength: Int
    ): ServerNameInfo? {
        if (extensionLength < 5) return null
        val extensionEnd = offset + extensionLength
        var pos = offset

        val listLengthOffset = pos
        val listLength = readUInt16(buffer, pos)
        pos += 2
        val listEnd = pos + listLength
        if (listEnd > extensionEnd) return null

        while (pos + 3 <= listEnd) {
            val nameType = buffer[pos].toInt() and 0xFF
            val nameLengthOffset = pos + 1
            val nameLength = readUInt16(buffer, pos + 1)
            pos += 3
            if (pos + nameLength > listEnd) return null

            if (nameType == TlsConstants.NAME_TYPE_HOST_NAME && nameLength > 0) {
                val hostname = buffer.decodeToString(pos, pos + nameLength)
                return ServerNameInfo(
                    hostname = hostname,
                    hostnameOffset = pos,
                    hostnameLength = nameLength,
                    listLengthOffset = listLengthOffset,
                    nameLengthOffset = nameLengthOffset
                )
            }
            pos += nameLength
        }
        return null
    }

    private fun readUInt16(buffer: ByteArray, offset: Int): Int =
        ((buffer[offset].toInt() and 0xFF) shl 8) or (buffer[offset + 1].toInt() and 0xFF)

    private fun readUInt24(buffer: ByteArray, offset: Int): Int =
        ((buffer[offset].toInt() and 0xFF) shl 16) or
            ((buffer[offset + 1].toInt() and 0xFF) shl 8) or
            (buffer[offset + 2].toInt() and 0xFF)
}
