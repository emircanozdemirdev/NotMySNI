package com.notmysni.engine

import com.notmysni.engine.fragment.FragmentStrategy
import com.notmysni.engine.fragment.TtlDesyncConfig

/**
 * Runtime DPI engine options (fragmentation + TTL desync).
 * Step 6.2 — TTL-based desync technique
 */
data class DpiEngineConfig(
    val fragmentStrategy: FragmentStrategy = FragmentStrategy.SplitAtSni,
    val ttlDesync: TtlDesyncConfig = TtlDesyncConfig(enabled = true)
) {
    companion object {
        val Default = DpiEngineConfig()
    }
}
