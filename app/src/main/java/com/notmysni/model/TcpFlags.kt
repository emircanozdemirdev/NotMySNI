package com.notmysni.model

data class TcpFlags(val raw: Int) {
    val fin: Boolean get() = raw and 0x01 != 0
    val syn: Boolean get() = raw and 0x02 != 0
    val rst: Boolean get() = raw and 0x04 != 0
    val psh: Boolean get() = raw and 0x08 != 0
    val ack: Boolean get() = raw and 0x10 != 0
    val urg: Boolean get() = raw and 0x20 != 0
}
