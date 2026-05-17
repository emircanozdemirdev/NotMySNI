package com.notmysni.model

data class IpHeader(
    val version: Int,
    val headerLength: Int,
    val totalLength: Int,
    val protocol: Int,
    val sourceAddress: IpAddress,
    val destinationAddress: IpAddress
) {
    val transportOffset: Int get() = headerLength
}
