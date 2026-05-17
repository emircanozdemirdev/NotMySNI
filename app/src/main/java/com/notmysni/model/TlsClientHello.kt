package com.notmysni.model

/**
 * Parsed TLS ClientHello metadata. [serverName] is null when the hello has no SNI extension.
 * Offsets refer to the buffer passed to [com.notmysni.engine.parser.TlsClientHelloParser.parse].
 */
data class TlsClientHello(
    val serverName: String?,
    val hostnameOffset: Int,
    val hostnameLength: Int
) {
    val hasServerName: Boolean get() = serverName != null
}
