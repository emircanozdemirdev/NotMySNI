package com.notmysni.engine.forward

import java.util.concurrent.ConcurrentHashMap

/**
 * Step 8.1 — TCP routing engine
 *
 * Thread-safe lookup table for active TCP connections by 4-tuple.
 */
internal class ConnectionTable {
    private val connections = ConcurrentHashMap<ConnectionKey, TcpConnection>()

    fun get(key: ConnectionKey): TcpConnection? = connections[key]

    fun putIfAbsent(connection: TcpConnection): TcpConnection? =
        connections.putIfAbsent(connection.key, connection)

    fun remove(key: ConnectionKey): TcpConnection? = connections.remove(key)

    fun values(): Collection<TcpConnection> = connections.values

    fun clear() {
        connections.clear()
    }
}
