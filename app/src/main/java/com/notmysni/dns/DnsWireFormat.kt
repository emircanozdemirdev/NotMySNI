package com.notmysni.dns

/**
 * Step 7.1 — DNS-over-HTTPS resolver
 *
 * RFC 1035 wire-format encoder/decoder for A-record queries and responses.
 */
object DnsWireFormat {

    const val TYPE_A = 1
    const val CLASS_IN = 1

    fun encodeAQuery(hostname: String, queryId: Int = 0xABCD): ByteArray {
        val labels = hostname.trim().trimEnd('.').split('.').filter { it.isNotEmpty() }
        require(labels.isNotEmpty()) { "Hostname must contain at least one label" }
        labels.forEach { label ->
            require(label.length <= 63) { "DNS label exceeds 63 bytes: $label" }
            require(label.all { it.isLetterOrDigit() || it == '-' }) {
                "Invalid DNS label: $label"
            }
        }

        val questionLength = labels.sumOf { 1 + it.length } + 1 + 4
        val packet = ByteArray(12 + questionLength)
        var offset = 0

        writeUInt16(packet, offset, queryId and 0xFFFF)
        offset += 2
        writeUInt16(packet, offset, 0x0100) // RD=1
        offset += 2
        writeUInt16(packet, offset, 1) // QDCOUNT
        offset += 2
        writeUInt16(packet, offset, 0) // ANCOUNT
        offset += 2
        writeUInt16(packet, offset, 0) // NSCOUNT
        offset += 2
        writeUInt16(packet, offset, 0) // ARCOUNT
        offset += 2

        labels.forEach { label ->
            packet[offset++] = label.length.toByte()
            label.toByteArray(Charsets.US_ASCII).copyInto(packet, offset)
            offset += label.length
        }
        packet[offset++] = 0

        writeUInt16(packet, offset, TYPE_A)
        offset += 2
        writeUInt16(packet, offset, CLASS_IN)

        return packet
    }

    fun parseARecords(response: ByteArray): List<DnsAnswer> {
        if (response.size < 12) return emptyList()

        val answerCount = readUInt16(response, 6)
        if (answerCount == 0) return emptyList()

        var offset = 12
        offset = skipQuestionSection(response, offset) ?: return emptyList()

        val answers = ArrayList<DnsAnswer>(answerCount)
        repeat(answerCount) {
            offset = skipName(response, offset) ?: return@repeat
            if (offset + 10 > response.size) return@repeat

            val type = readUInt16(response, offset)
            offset += 2
            offset += 2 // QCLASS
            val ttl = readUInt32(response, offset)
            offset += 4
            val rdLength = readUInt16(response, offset)
            offset += 2

            if (type == TYPE_A && rdLength == 4 && offset + 4 <= response.size) {
                answers.add(
                    DnsAnswer(
                        ip = response.copyOfRange(offset, offset + 4),
                        ttl = ttl
                    )
                )
            }
            offset += rdLength
        }

        return answers
    }

    private fun skipQuestionSection(data: ByteArray, start: Int): Int? {
        var offset = skipName(data, start) ?: return null
        offset += 4 // QTYPE + QCLASS
        return offset.takeIf { it <= data.size }
    }

    private fun skipName(data: ByteArray, start: Int): Int? {
        var offset = start
        var jumps = 0

        while (offset < data.size && jumps < 16) {
            val length = data[offset].toInt() and 0xFF
            when {
                length == 0 -> return offset + 1
                length and 0xC0 == 0xC0 -> {
                    if (offset + 1 >= data.size) return null
                    return offset + 2
                }
                else -> {
                    offset += 1 + length
                    jumps++
                }
            }
        }

        return null
    }

    private fun readUInt16(data: ByteArray, offset: Int): Int =
        ((data[offset].toInt() and 0xFF) shl 8) or (data[offset + 1].toInt() and 0xFF)

    private fun readUInt32(data: ByteArray, offset: Int): Int =
        ((data[offset].toInt() and 0xFF) shl 24) or
            ((data[offset + 1].toInt() and 0xFF) shl 16) or
            ((data[offset + 2].toInt() and 0xFF) shl 8) or
            (data[offset + 3].toInt() and 0xFF)

    private fun writeUInt16(buffer: ByteArray, offset: Int, value: Int) {
        buffer[offset] = ((value shr 8) and 0xFF).toByte()
        buffer[offset + 1] = (value and 0xFF).toByte()
    }
}
