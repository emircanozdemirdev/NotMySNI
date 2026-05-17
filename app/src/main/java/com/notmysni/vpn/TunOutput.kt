package com.notmysni.vpn

import java.io.FileOutputStream
import java.io.IOException

/**
 * Thread-safe writes of raw IP packets back into the TUN interface.
 */
class TunOutput(private val outputStream: FileOutputStream) {

    private val lock = Any()

    @Throws(IOException::class)
    fun write(packet: ByteArray, length: Int) {
        synchronized(lock) {
            outputStream.write(packet, 0, length)
        }
    }
}
