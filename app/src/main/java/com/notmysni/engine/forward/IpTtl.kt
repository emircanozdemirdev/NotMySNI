package com.notmysni.engine.forward

import android.system.Os
import android.system.OsConstants
import java.io.FileDescriptor
import java.net.Socket

/**
 * Step 6.2 — TTL-based desync technique
 */
internal object IpTtl {

    fun setSocketTtl(socket: Socket, ttl: Int) {
        require(ttl in 1..255) { "ttl must be 1..255, got $ttl" }
        setIpTtl(socket.fileDescriptor(), ttl)
    }

    private fun setIpTtl(fd: FileDescriptor, ttl: Int) {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            Os.setsockoptInt(fd, OsConstants.IPPROTO_IP, OsConstants.IP_TTL, ttl)
            return
        }

        val libcore = Class.forName("libcore.io.Libcore")
        val os = libcore.getDeclaredField("os").get(null)
        os.javaClass.getMethod(
            "setsockoptInt",
            FileDescriptor::class.java,
            Int::class.javaPrimitiveType,
            Int::class.javaPrimitiveType,
            Int::class.javaPrimitiveType
        ).invoke(os, fd, OsConstants.IPPROTO_IP, OsConstants.IP_TTL, ttl)
    }

    private fun Socket.fileDescriptor(): FileDescriptor {
        val implField = Socket::class.java.getDeclaredField("impl")
        implField.isAccessible = true
        val socketImpl = implField.get(this)
        val fdField = Class.forName("java.net.SocketImpl").getDeclaredField("fd")
        fdField.isAccessible = true
        return fdField.get(socketImpl) as FileDescriptor
    }
}
