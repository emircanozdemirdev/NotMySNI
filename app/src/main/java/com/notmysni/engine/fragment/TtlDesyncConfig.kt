package com.notmysni.engine.fragment

/**
 * Step 6.2 — TTL-based desync technique
 */
data class TtlDesyncConfig(
    /** TTL written on the decoy (first) segment; should expire before reaching the remote host. */
    val fakeSegmentTtl: Int = 1,
    /** TTL restored on the real segment(s) forwarded to the remote host. */
    val realSegmentTtl: Int = 64,
    val enabled: Boolean = false
) {
    init {
        require(fakeSegmentTtl in 1..255) { "fakeSegmentTtl must be 1..255" }
        require(realSegmentTtl in 1..255) { "realSegmentTtl must be 1..255" }
    }
}
