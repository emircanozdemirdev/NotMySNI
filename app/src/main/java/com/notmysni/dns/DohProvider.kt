package com.notmysni.dns

/**
 * Step 7.1 — DNS-over-HTTPS resolver
 */
enum class DohProvider(val endpointUrl: String) {
    CLOUDFLARE("https://cloudflare-dns.com/dns-query"),
    GOOGLE("https://dns.google/dns-query")
}
