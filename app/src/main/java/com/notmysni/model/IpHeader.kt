package com.notmysni.model

data class IpHeader(
    val version: Int,
    val headerLength: Int,
    val totalLength: Int,
    val protocol: Int,
    val sourceAddress: IpAddress,
    val destinationAddress: IpAddress,
    /** Step 6.2 — TTL-based desync technique */
    val ttl: Int = 64
) {
    val transportOffset: Int get() = headerLength

    // Step 6.2 — TTL-based desync technique
    //
    // fun asDecoyOnly(): IpHeader = copy(ttl = 1)
}
