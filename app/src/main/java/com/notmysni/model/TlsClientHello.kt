package com.notmysni.model

/**
 * Parsed TLS ClientHello metadata. Length-field offsets are valid only when [hasServerName] is true.
 * All offsets are absolute positions in the buffer passed to the parser.
 */
data class TlsClientHello(
    val serverName: String?,
    val hostnameOffset: Int,
    val hostnameLength: Int,
    val recordLengthOffset: Int,
    val handshakeLengthOffset: Int,
    val extensionsLengthOffset: Int,
    val sniListLengthOffset: Int,
    val sniNameLengthOffset: Int
) {
    val hasServerName: Boolean get() = serverName != null
}
