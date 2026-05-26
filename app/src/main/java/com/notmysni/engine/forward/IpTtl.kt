package com.notmysni.engine.forward

import android.system.Os
import android.system.OsConstants
import java.net.Socket

/**
 * Step 6.2 — TTL-based desync technique
 */
internal object IpTtl {

    fun setSocketTtl(socket: Socket, ttl: Int) {
        require(ttl in 1..255) { "ttl must be 1..255, got $ttl" }
        Os.setsockopt(
            socket.fileDescriptor,
            OsConstants.IPPROTO_IP,
            OsConstants.IP_TTL,
            byteArrayOf(ttl.toByte())
        )
    }
}
