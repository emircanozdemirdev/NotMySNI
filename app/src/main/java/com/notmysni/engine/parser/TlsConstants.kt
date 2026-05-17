package com.notmysni.engine.parser

internal object TlsConstants {
    const val CONTENT_TYPE_HANDSHAKE = 0x16
    const val HANDSHAKE_CLIENT_HELLO = 0x01
    const val EXTENSION_SERVER_NAME = 0x0000
    const val NAME_TYPE_HOST_NAME = 0
}
