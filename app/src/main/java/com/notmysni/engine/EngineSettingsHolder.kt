package com.notmysni.engine

/**
 * In-memory engine settings until Faz 9 DataStore wiring.
 * Updated from Settings UI; read when the VPN tunnel starts.
 */
object EngineSettingsHolder {
    @Volatile
    var config: DpiEngineConfig = DpiEngineConfig.Default
}
