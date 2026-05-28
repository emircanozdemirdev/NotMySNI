package com.notmysni.engine.forward

import java.nio.channels.SocketChannel

/**
 * Step 8.1 — TCP routing engine
 *
 * Represents one outbound TCP mapping keyed by 4-tuple.
 */
internal data class TcpConnection(
    val key: ConnectionKey,
    val destinationHost: String,
    val destinationPort: Int,
    val channel: SocketChannel
)
