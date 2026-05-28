package com.notmysni.sni

import com.notmysni.engine.parser.TlsClientHelloParser
import com.notmysni.testutil.PacketFixtures
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SniSpooferTest {

    @Test
    fun spoof_sameLengthHostname_updatesSniBytesOnly() {
        val original = PacketFixtures.buildTlsClientHelloPayload("example.com")
        val parsed = TlsClientHelloParser.parse(original, 0, original.size)!!

        val spoofed = SniSpoofer.spoof(original, 0, original.size, "sample.test", parsed)!!

        assertEquals(original.size, spoofed.size)
        assertPrefixEquals(original, spoofed, parsed.hostnameOffset, 0)
        assertSuffixEquals(
            original = original,
            spoofed = spoofed,
            originalHostnameEnd = parsed.hostnameOffset + parsed.hostnameLength,
            spoofedHostnameEnd = parsed.hostnameOffset + "sample.test".length
        )
        assertHostnameEquals("sample.test", spoofed, parsed.hostnameOffset)

        val reparsed = TlsClientHelloParser.parse(spoofed, 0, spoofed.size)!!
        assertEquals("sample.test", reparsed.serverName)
    }

    @Test
    fun spoof_longerHostname_growsBufferAndUpdatesAllLengths() {
        val original = PacketFixtures.buildTlsClientHelloPayload("example.com")
        val parsed = TlsClientHelloParser.parse(original, 0, original.size)!!
        val newHostname = "v.whatsapp.net"
        val delta = newHostname.length - parsed.hostnameLength

        val spoofed = SniSpoofer.spoof(original, 0, original.size, newHostname, parsed)!!

        assertEquals(original.size + delta, spoofed.size)
        assertPrefixEquals(original, spoofed, parsed.hostnameOffset, 0)
        assertSuffixEquals(
            original = original,
            spoofed = spoofed,
            originalHostnameEnd = parsed.hostnameOffset + parsed.hostnameLength,
            spoofedHostnameEnd = parsed.hostnameOffset + newHostname.length
        )
        assertHostnameEquals(newHostname, spoofed, parsed.hostnameOffset)

        assertEquals(readUInt16(original, parsed.recordLengthOffset) + delta, readUInt16(spoofed, rel(parsed.recordLengthOffset)))
        assertEquals(readUInt24(original, parsed.handshakeLengthOffset) + delta, readUInt24(spoofed, rel(parsed.handshakeLengthOffset)))
        assertEquals(readUInt16(original, parsed.extensionsLengthOffset) + delta, readUInt16(spoofed, rel(parsed.extensionsLengthOffset)))
        assertEquals(readUInt16(original, parsed.sniListLengthOffset) + delta, readUInt16(spoofed, rel(parsed.sniListLengthOffset)))
        assertEquals(newHostname.length, readUInt16(spoofed, rel(parsed.sniNameLengthOffset)))

        val reparsed = TlsClientHelloParser.parse(spoofed, 0, spoofed.size)!!
        assertEquals(newHostname, reparsed.serverName)
    }

    @Test
    fun spoof_shorterHostname_shrinksBufferAndUpdatesAllLengths() {
        val original = PacketFixtures.buildTlsClientHelloPayload("www.google.com")
        val parsed = TlsClientHelloParser.parse(original, 0, original.size)!!
        val newHostname = "go.com"
        val delta = newHostname.length - parsed.hostnameLength

        val spoofed = SniSpoofer.spoof(original, 0, original.size, newHostname, parsed)!!

        assertEquals(original.size + delta, spoofed.size)
        assertTrue(delta < 0)
        assertHostnameEquals(newHostname, spoofed, parsed.hostnameOffset)

        assertEquals(readUInt16(original, parsed.recordLengthOffset) + delta, readUInt16(spoofed, rel(parsed.recordLengthOffset)))

        val reparsed = TlsClientHelloParser.parse(spoofed, 0, spoofed.size)!!
        assertEquals(newHostname, reparsed.serverName)
    }

    @Test
    fun spoof_returnsNullWithoutSni() {
        val payload = byteArrayOf(0x00, 0x01, 0x02, 0x03)
        assertNull(SniSpoofer.spoof(payload, 0, payload.size, "example.com"))
    }

    private fun rel(absoluteOffset: Int): Int = absoluteOffset

    private fun assertPrefixEquals(
        original: ByteArray,
        spoofed: ByteArray,
        hostnameOffset: Int,
        regionStart: Int
    ) {
        val prefixLength = hostnameOffset - regionStart
        assertArrayEquals(
            original.copyOfRange(regionStart, hostnameOffset),
            spoofed.copyOfRange(0, prefixLength)
        )
    }

    private fun assertSuffixEquals(
        original: ByteArray,
        spoofed: ByteArray,
        originalHostnameEnd: Int,
        spoofedHostnameEnd: Int
    ) {
        val originalTail = original.copyOfRange(originalHostnameEnd, original.size)
        val spoofedTail = spoofed.copyOfRange(spoofedHostnameEnd, spoofed.size)
        assertArrayEquals(originalTail, spoofedTail)
    }

    private fun assertHostnameEquals(hostname: String, buffer: ByteArray, hostnameOffset: Int) {
        val expected = hostname.toByteArray(Charsets.US_ASCII)
        assertArrayEquals(expected, buffer.copyOfRange(hostnameOffset, hostnameOffset + expected.size))
    }

    private fun readUInt16(buffer: ByteArray, offset: Int): Int =
        ((buffer[offset].toInt() and 0xFF) shl 8) or (buffer[offset + 1].toInt() and 0xFF)

    private fun readUInt24(buffer: ByteArray, offset: Int): Int =
        ((buffer[offset].toInt() and 0xFF) shl 16) or
            ((buffer[offset + 1].toInt() and 0xFF) shl 8) or
            (buffer[offset + 2].toInt() and 0xFF)
}
