package com.notmysni.dns

import com.notmysni.testutil.HexUtils
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Step 7.1 — DNS-over-HTTPS resolver */
class DnsWireFormatTest {

    @Test
    fun encodeAQuery_exampleDotCom_buildsExpectedQuestionSection() {
        val query = DnsWireFormat.encodeAQuery("example.com", queryId = 0x1234)

        assertEquals(0x12, query[0].toInt() and 0xFF)
        assertEquals(0x34, query[1].toInt() and 0xFF)
        assertEquals(0x01, query[2].toInt() and 0xFF) // RD flag
        assertEquals(0x00, query[3].toInt() and 0xFF)
        assertEquals(0x00, query[4].toInt() and 0xFF) // QDCOUNT = 1
        assertEquals(0x01, query[5].toInt() and 0xFF)

        val questionStart = 12
        assertEquals(7, query[questionStart].toInt() and 0xFF)
        assertEquals("example", String(query, questionStart + 1, 7, Charsets.US_ASCII))
        assertEquals(3, query[questionStart + 8].toInt() and 0xFF)
        assertEquals("com", String(query, questionStart + 9, 3, Charsets.US_ASCII))
        assertEquals(0, query[questionStart + 12].toInt() and 0xFF)
        assertEquals(0x00, query[questionStart + 13].toInt() and 0xFF) // TYPE A
        assertEquals(0x01, query[questionStart + 14].toInt() and 0xFF)
        assertEquals(0x00, query[questionStart + 15].toInt() and 0xFF) // CLASS IN
        assertEquals(0x01, query[questionStart + 16].toInt() and 0xFF)
    }

    @Test
    fun parseARecords_singleAnswer_extractsIpAndTtl() {
        val response = buildARecordResponse(
            queryId = 0xABCD,
            hostname = "example.com",
            ttl = 300,
            ip = byteArrayOf(93, -72, -40, 34)
        )

        val answers = DnsWireFormat.parseARecords(response)

        assertEquals(1, answers.size)
        assertArrayEquals(byteArrayOf(93, -72, -40, 34), answers[0].ip)
        assertEquals(300, answers[0].ttl)
    }

    @Test
    fun parseARecords_multipleAnswers_returnsAllARecords() {
        val response = HexUtils.hexToBytes(
            """
            AB CD 81 80 00 01 00 02 00 00 00 00
            07 65 78 61 6D 70 6C 65 03 63 6F 6D 00 00 01 00 01
            C0 0C 00 01 00 01 00 00 00 3C 00 04 C0 A8 00 01
            C0 0C 00 01 00 01 00 00 00 3C 00 04 C0 A8 00 02
            """.trimIndent()
        )

        val answers = DnsWireFormat.parseARecords(response)

        assertEquals(2, answers.size)
        assertArrayEquals(byteArrayOf(192.toByte(), 168.toByte(), 0, 1), answers[0].ip)
        assertArrayEquals(byteArrayOf(192.toByte(), 168.toByte(), 0, 2), answers[1].ip)
        assertTrue(answers.all { it.ttl == 60 })
    }

    private fun buildARecordResponse(
        queryId: Int,
        hostname: String,
        ttl: Int,
        ip: ByteArray
    ): ByteArray {
        val query = DnsWireFormat.encodeAQuery(hostname, queryId = queryId)
        val answer = ByteArray(16)
        answer[0] = 0xC0.toByte()
        answer[1] = 0x0C
        answer[2] = 0x00
        answer[3] = 0x01 // TYPE A
        answer[4] = 0x00
        answer[5] = 0x01 // CLASS IN
        answer[6] = ((ttl shr 24) and 0xFF).toByte()
        answer[7] = ((ttl shr 16) and 0xFF).toByte()
        answer[8] = ((ttl shr 8) and 0xFF).toByte()
        answer[9] = (ttl and 0xFF).toByte()
        answer[10] = 0x00
        answer[11] = 0x04
        ip.copyInto(answer, 12)

        val header = query.copyOfRange(0, 12)
        header[2] = 0x81.toByte()
        header[3] = 0x80.toByte()
        header[6] = 0x00
        header[7] = 0x01 // ANCOUNT = 1

        return header + query.copyOfRange(12, query.size) + answer
    }
}
