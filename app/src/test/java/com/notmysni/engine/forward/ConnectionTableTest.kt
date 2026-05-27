package com.notmysni.engine.forward

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.nio.channels.SocketChannel

class ConnectionTableTest {

    @Test
    fun putIfAbsent_andLookupByFourTuple() {
        val table = ConnectionTable()
        val key = ConnectionKey(
            sourceIp = byteArrayOf(10, 0, 0, 2),
            sourcePort = 50000,
            destinationIp = byteArrayOf(1, 1, 1, 1),
            destinationPort = 443
        )
        val connection = TcpConnection(
            key = key,
            destinationHost = "1.1.1.1",
            destinationPort = 443,
            channel = SocketChannel.open()
        )
        try {
            assertNull(table.putIfAbsent(connection))
            assertEquals(connection, table.get(key))
        } finally {
            connection.channel.close()
        }
    }

    @Test
    fun remove_deletesConnection() {
        val table = ConnectionTable()
        val key = ConnectionKey(
            sourceIp = byteArrayOf(10, 0, 0, 2),
            sourcePort = 40000,
            destinationIp = byteArrayOf(8, 8, 8, 8),
            destinationPort = 443
        )
        val connection = TcpConnection(
            key = key,
            destinationHost = "8.8.8.8",
            destinationPort = 443,
            channel = SocketChannel.open()
        )
        try {
            table.putIfAbsent(connection)
            assertEquals(connection, table.remove(key))
            assertNull(table.get(key))
        } finally {
            connection.channel.close()
        }
    }
}
