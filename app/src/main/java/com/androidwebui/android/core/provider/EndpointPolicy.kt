package com.androidwebui.android.core.provider

import java.net.Inet4Address
import java.net.InetAddress
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

enum class RejectReason { INVALID_URL, EMBEDDED_CREDENTIALS, PUBLIC_HTTP }

sealed interface EndpointAssessment {
    /** HTTPS. Nothing to warn about. */
    data object Secure : EndpointAssessment

    /** Plain HTTP to a local or private host. Allowed only after the user accepts an explicit warning. */
    data class PrivateNetworkHttp(val host: String) : EndpointAssessment

    data class Rejected(val reason: RejectReason) : EndpointAssessment
}

/**
 * Decides whether an endpoint may receive requests carrying an API key. There is no
 * "trust all certificates" option anywhere: HTTPS is always verified by the platform.
 *
 * The check is syntactic. It never resolves names, so a public DNS name that points at a private
 * address is treated as public, and plain HTTP to it is rejected.
 */
object EndpointPolicy {
    private val IPV4 = Regex("""\d{1,3}(\.\d{1,3}){3}""")
    private val LOCAL_SUFFIXES = listOf(".local", ".lan", ".internal", ".home.arpa", ".localhost")

    fun assess(rawUrl: String): EndpointAssessment {
        val url = rawUrl.trim().toHttpUrlOrNull() ?: return EndpointAssessment.Rejected(RejectReason.INVALID_URL)
        if (url.username.isNotEmpty() || url.password.isNotEmpty()) {
            return EndpointAssessment.Rejected(RejectReason.EMBEDDED_CREDENTIALS)
        }
        if (url.isHttps) return EndpointAssessment.Secure
        return if (isPrivateHost(url.host)) {
            EndpointAssessment.PrivateNetworkHttp(url.host)
        } else {
            EndpointAssessment.Rejected(RejectReason.PUBLIC_HTTP)
        }
    }

    private fun isPrivateHost(host: String): Boolean {
        if (host.contains(':') || IPV4.matches(host)) return isPrivateAddress(InetAddress.getByName(host))
        // Numeric or hex-looking names (for example 134744072) can be an address in disguise.
        val lastLabel = host.substringAfterLast('.')
        if (lastLabel.all { it.isDigit() } || lastLabel.startsWith("0x")) return false
        return host == "localhost" || !host.contains('.') || LOCAL_SUFFIXES.any { host.endsWith(it) }
    }

    private fun isPrivateAddress(address: InetAddress): Boolean {
        if (address.isLoopbackAddress || address.isSiteLocalAddress || address.isLinkLocalAddress || address.isAnyLocalAddress) {
            return true
        }
        val bytes = address.address
        return if (address is Inet4Address) {
            // 100.64.0.0/10 carrier-grade NAT, used by Tailscale.
            (bytes[0].toInt() and 0xFF) == 100 && (bytes[1].toInt() and 0xC0) == 0x40
        } else {
            // fc00::/7 unique local addresses.
            (bytes[0].toInt() and 0xFE) == 0xFC
        }
    }
}
