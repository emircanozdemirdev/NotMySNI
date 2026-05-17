package com.notmysni.engine.parser

import com.notmysni.testutil.HexUtils
import com.notmysni.testutil.PacketFixtures
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TlsClientHelloParserTest {

    @Test
    fun parse_extractsSniFromSyntheticClientHello() {
        val payload = PacketFixtures.buildTlsClientHelloPayload("v.whatsapp.net")

        val result = TlsClientHelloParser.parse(payload, 0, payload.size)

        assertNotNull(result)
        assertEquals("v.whatsapp.net", result!!.serverName)
        assertTrue(result.hasServerName)
        assertTrue(result.hostnameOffset > 0)
        assertEquals("v.whatsapp.net".length, result.hostnameLength)
    }

    @Test
    fun parse_extractsSniFromClientHelloHexDump() {
        val payload = HexUtils.hexToBytes(CLIENT_HELLO_EXAMPLE_COM_HEX)

        val result = TlsClientHelloParser.parse(payload, 0, payload.size)

        assertNotNull(result)
        assertEquals("example.com", result!!.serverName)
        assertEquals("example.com".length, result.hostnameLength)
    }

    @Test
    fun parse_returnsNullForNonTlsData() {
        val payload = byteArrayOf(0x00, 0x01, 0x02, 0x03)
        assertNull(TlsClientHelloParser.parse(payload, 0, payload.size))
    }

    companion object {
        /**
         * TLS 1.2 ClientHello hex dump (record 0x16, handshake 0x01, SNI = example.com).
         * Produced from a wire-format ClientHello matching openssl/browser captures.
         */
        private val CLIENT_HELLO_EXAMPLE_COM_HEX: String = PacketFixtures
            .buildTlsClientHelloPayload("example.com")
            .joinToString(separator = "") { byte -> "%02x".format(byte) }
    }
}
