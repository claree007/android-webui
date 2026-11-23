package com.androidwebui.android.core.provider

import org.junit.Assert.assertEquals
import org.junit.Test

class EndpointPolicyTest {
    private fun assess(url: String) = EndpointPolicy.assess(url)

    @Test
    fun httpsIsAlwaysSecure() {
        assertEquals(EndpointAssessment.Secure, assess("https://api.openai.com/v1"))
        assertEquals(EndpointAssessment.Secure, assess(" https://192.168.1.5:8443/v1 "))
    }

    @Test
    fun plainHttpToPrivateHostsNeedsAWarning() {
        listOf(
            "http://localhost:11434" to "localhost",
            "http://127.0.0.1:8080/v1" to "127.0.0.1",
            "http://10.0.2.2:11434" to "10.0.2.2",
            "http://172.16.4.1" to "172.16.4.1",
            "http://172.31.255.255" to "172.31.255.255",
            "http://192.168.0.20:1234/v1" to "192.168.0.20",
            "http://169.254.1.1" to "169.254.1.1",
            "http://100.101.102.103:11434" to "100.101.102.103",
            "http://[::1]:11434" to "[::1]",
            "http://[fd12:3456::1]:11434" to "[fd12:3456::1]",
            "http://[fe80::1]" to "[fe80::1]",
            "http://gpu-box:11434" to "gpu-box",
            "http://nas.local:11434" to "nas.local",
            "http://llm.home.arpa" to "llm.home.arpa",
        ).forEach { (url, _) ->
            val result = assess(url)
            assertEquals("$url should need a warning", true, result is EndpointAssessment.PrivateNetworkHttp)
        }
    }

    @Test
    fun plainHttpToPublicHostsIsRejected() {
        listOf(
            "http://api.openai.com/v1",
            "http://8.8.8.8",
            "http://172.32.0.1",
            "http://172.15.0.1",
            "http://100.128.0.1",
            "http://100.63.0.1",
            "http://example.com:11434",
            "http://134744072", // 8.8.8.8 written as one number
            "http://[2001:4860:4860::8888]",
        ).forEach { url ->
            assertEquals("$url", EndpointAssessment.Rejected(RejectReason.PUBLIC_HTTP), assess(url))
        }
    }

    @Test
    fun credentialsInTheUrlAreRejectedEvenOverHttps() {
        assertEquals(EndpointAssessment.Rejected(RejectReason.EMBEDDED_CREDENTIALS), assess("https://user:pass@example.com/v1"))
        assertEquals(EndpointAssessment.Rejected(RejectReason.EMBEDDED_CREDENTIALS), assess("http://token@192.168.1.2"))
    }

    @Test
    fun otherSchemesAndGarbageAreInvalid() {
        listOf("", "   ", "ftp://example.com", "file:///etc/passwd", "javascript:alert(1)", "not a url", "example.com/v1").forEach {
            assertEquals("'$it'", EndpointAssessment.Rejected(RejectReason.INVALID_URL), assess(it))
        }
    }
}
