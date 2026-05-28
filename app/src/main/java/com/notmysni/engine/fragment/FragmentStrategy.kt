package com.notmysni.engine.fragment

/**
 * TCP payload fragmentation strategies used before forwarding to the remote host.
 */
sealed class FragmentStrategy {

    /** Splits the TLS ClientHello immediately before the SNI hostname bytes. */
    data object SplitAtSni : FragmentStrategy()

    /**
     * Sends a tiny first segment (1–5 bytes) followed by the remainder.
     * [firstSegmentSize] must be in 1..5.
     */
    data class TinyFirst(val firstSegmentSize: Int = 1) : FragmentStrategy() {
        init {
            require(firstSegmentSize in 1..5) {
                "firstSegmentSize must be between 1 and 5, got $firstSegmentSize"
            }
        }
    }

    /** Splits the payload into [chunkCount] roughly equal segments (minimum 2). */
    data class MultiSplit(val chunkCount: Int) : FragmentStrategy() {
        init {
            require(chunkCount >= 2) {
                "chunkCount must be at least 2, got $chunkCount"
            }
        }
    }
}
