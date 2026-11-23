package com.androidwebui.android.core.provider

import java.util.concurrent.TimeUnit
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import okhttp3.OkHttpClient
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class OpenAiProviderTest {
    private lateinit var server: MockWebServer
    private val secret = "sk-test-SECRET-123"
    private val request = ChatRequest("gpt-x", listOf(ProviderMessage("user", "hi")))

    @Before fun start() {
        server = MockWebServer().apply { start() }
    }

    @After fun stop() {
        server.close()
    }

    private fun provider(client: OkHttpClient = ProviderHttp.newClient(), key: String? = secret, base: String = server.url("/v1").toString()) =
        OpenAiProvider(base, key, client)

    private fun sseResponse(body: String, chunk: Int? = null) = MockResponse.Builder()
        .addHeader("Content-Type", "text/event-stream")
        .apply { if (chunk != null) chunkedBody(body, chunk) else body(body) }
        .build()

    /** Collects the stream and returns the events seen before any failure, plus the failure. */
    private fun collect(p: OpenAiProvider = provider()): Pair<List<ChatEvent>, Throwable?> = runBlocking {
        withTimeout(15_000) {
            val events = mutableListOf<ChatEvent>()
            var failure: Throwable? = null
            p.stream(request).catch { failure = it }.toList(events)
            events to failure
        }
    }

    private fun chunk(content: String) = """data: {"choices":[{"delta":{"content":${kotlinx.serialization.json.JsonPrimitive(content)}}}]}""" + "\n\n"

    @Test
    fun streamsTextUsageAndDone() {
        server.enqueue(
            sseResponse(
                chunk("Hel") + chunk("lo 😀") +
                    """data: {"choices":[{"delta":{},"finish_reason":"stop"}]}""" + "\n\n" +
                    """data: {"choices":[],"usage":{"prompt_tokens":3,"completion_tokens":2,"total_tokens":5}}""" + "\n\n" +
                    "data: [DONE]\n\n",
            ),
        )

        val (events, failure) = collect()

        assertNull(failure)
        assertEquals(listOf("Hel", "lo 😀"), events.filterIsInstance<ChatEvent.Text>().map { it.delta })
        val usage = events.filterIsInstance<ChatEvent.Usage>().single()
        assertEquals(5, usage.totalTokens)
        assertEquals(ChatEvent.Done("stop"), events.last())
    }

    @Test
    fun sendsKeyOnlyAsBearerAndBodyHasRequestedFields() {
        server.enqueue(sseResponse("data: [DONE]\n\n"))
        collect(provider())

        val recorded = server.takeRequest(5, TimeUnit.SECONDS)!!
        assertEquals("Bearer $secret", recorded.headers["Authorization"])
        assertEquals("/v1/chat/completions", recorded.target)
        val body = recorded.body!!.utf8()
        assertTrue(body.contains("\"model\":\"gpt-x\"") && body.contains("\"stream\":true"))
        assertFalse(body.contains(secret))
    }

    @Test
    fun omitsAuthorizationWithoutKeyAndUnsetParameters() {
        server.enqueue(sseResponse("data: [DONE]\n\n"))
        collect(provider(key = null))
        val recorded = server.takeRequest(5, TimeUnit.SECONDS)!!
        assertNull(recorded.headers["Authorization"])
        assertFalse(recorded.body!!.utf8().contains("temperature"))

        val body = provider().requestBody(request.copy(params = SamplingParams(temperature = 0.5, stop = listOf("END")))).toString()
        assertTrue(body.contains("\"temperature\":0.5") && body.contains("\"stop\":[\"END\"]"))
    }

    @Test
    fun survivesNetworkChunkingAndSplitCharacters() {
        val text = chunk("héllo 😀 世界") + "data: [DONE]\n\n"
        server.enqueue(sseResponse(text, chunk = 3))
        val (events, failure) = collect()
        assertNull(failure)
        assertEquals("héllo 😀 世界", events.filterIsInstance<ChatEvent.Text>().joinToString("") { it.delta })
    }

    @Test
    fun endWithoutDoneOrFinishReasonIsInterruptedButKeepsTextSoFar() {
        server.enqueue(sseResponse(chunk("partial")))
        val (events, failure) = collect()
        assertEquals(listOf("partial"), events.filterIsInstance<ChatEvent.Text>().map { it.delta })
        assertEquals(ProviderErrorKind.INTERRUPTED, (failure as ProviderException).kind)
    }

    @Test
    fun serverEndingAfterFinishReasonWithoutDoneIsAccepted() {
        server.enqueue(sseResponse(chunk("ok") + """data: {"choices":[{"delta":{},"finish_reason":"length"}]}""" + "\n\n"))
        val (events, failure) = collect()
        assertNull(failure)
        assertEquals(ChatEvent.Done("length"), events.last())
    }

    @Test
    fun connectionDroppedMidBodyIsInterrupted() {
        server.enqueue(
            MockResponse.Builder()
                .addHeader("Content-Type", "text/event-stream")
                .addHeader("Content-Length", "100000")
                .body(chunk("start"))
                .build(),
        )
        val (events, failure) = collect()
        assertEquals(listOf("start"), events.filterIsInstance<ChatEvent.Text>().map { it.delta })
        assertEquals(ProviderErrorKind.INTERRUPTED, (failure as ProviderException).kind)
    }

    @Test
    fun malformedEventFailsWithTextKept() {
        server.enqueue(sseResponse(chunk("a") + "data: {not json\n\n" + chunk("b")))
        val (events, failure) = collect()
        assertEquals(listOf("a"), events.filterIsInstance<ChatEvent.Text>().map { it.delta })
        assertEquals(ProviderErrorKind.MALFORMED, (failure as ProviderException).kind)
    }

    @Test
    fun unknownFieldsKeepaliveCommentsAndEmptyChoicesAreIgnored() {
        server.enqueue(
            sseResponse(
                ": keep-alive\n\n" + """data: {"id":"x","choices":[],"extra":{"a":1}}""" + "\n\n" +
                    """data: {"choices":[{"delta":{"role":"assistant","content":null}}]}""" + "\n\n" +
                    chunk("x") + "data: [DONE]\n\n",
            ),
        )
        val (events, failure) = collect()
        assertNull(failure)
        assertEquals(listOf("x"), events.filterIsInstance<ChatEvent.Text>().map { it.delta })
    }

    @Test
    fun errorObjectInsideTheStreamIsReported() {
        server.enqueue(sseResponse(chunk("a") + """data: {"error":{"message":"You exceeded your quota","type":"insufficient_quota"}}""" + "\n\n"))
        val (events, failure) = collect()
        assertEquals(1, events.size)
        val error = failure as ProviderException
        assertEquals(ProviderErrorKind.QUOTA, error.kind)
        assertTrue(error.message!!.contains("quota"))
    }

    @Test
    fun authErrorShowsProviderMessageButNeverTheKey() {
        server.enqueue(
            MockResponse.Builder().code(401).addHeader("Content-Type", "application/json")
                .body("""{"error":{"message":"Incorrect API key provided: $secret","type":"invalid_request_error"}}""").build(),
        )
        val (_, failure) = collect()
        val error = failure as ProviderException
        assertEquals(ProviderErrorKind.AUTH, error.kind)
        assertEquals(401, error.status)
        assertTrue(error.message!!.contains("Incorrect API key"))
    }

    @Test
    fun errorMessagesAreRedactedOfTheConfiguredKey() {
        server.enqueue(MockResponse.Builder().code(401).body("""{"error":{"message":"bad key $secret"}}""").build())
        val error = collect().second as ProviderException
        assertFalse("the API key must not be echoed into an error that may be shown or logged", error.message!!.contains(secret))
    }

    @Test
    fun quotaAndRateLimitCarryRetryAfter() {
        server.enqueue(MockResponse.Builder().code(429).addHeader("Retry-After", "30").body("""{"error":{"message":"Rate limit"}}""").build())
        val error = collect().second as ProviderException
        assertEquals(ProviderErrorKind.QUOTA, error.kind)
        assertEquals(30L, error.retryAfterSeconds)
    }

    @Test
    fun serverAndBadRequestErrorsAreClassified() {
        server.enqueue(MockResponse.Builder().code(503).body("<html>Service Unavailable</html>").build())
        server.enqueue(MockResponse.Builder().code(400).body("plain text problem").build())
        val first = collect().second as ProviderException
        val second = collect().second as ProviderException
        assertEquals(ProviderErrorKind.SERVER, first.kind)
        assertEquals("HTML bodies are not shown", "HTTP 503", first.message)
        assertEquals(ProviderErrorKind.BAD_REQUEST, second.kind)
        assertTrue(second.message!!.contains("plain text problem"))
    }

    @Test
    fun redirectsAreNeverFollowedSoTheKeyCannotLeaveTheOrigin() {
        MockWebServer().use { other ->
            other.start()
            other.enqueue(sseResponse("data: [DONE]\n\n"))
            server.enqueue(MockResponse.Builder().code(307).addHeader("Location", other.url("/steal").toString()).build())

            val error = collect().second as ProviderException

            assertEquals(ProviderErrorKind.REDIRECT, error.kind)
            assertEquals("the redirect target must receive nothing", 0, other.requestCount)
            assertFalse(error.message!!.contains("/steal"))
        }
    }

    @Test
    fun redirectOnModelListIsAlsoRefused() = runBlocking {
        MockWebServer().use { other ->
            other.start()
            server.enqueue(MockResponse.Builder().code(302).addHeader("Location", other.url("/models").toString()).build())
            val error = runCatching { provider().listModels() }.exceptionOrNull() as ProviderException
            assertEquals(ProviderErrorKind.REDIRECT, error.kind)
            assertEquals(0, other.requestCount)
        }
    }

    @Test
    fun slowServerBecomesTimeout() {
        server.enqueue(MockResponse.Builder().headersDelay(5, TimeUnit.SECONDS).body("data: [DONE]\n\n").build())
        val client = ProviderHttp.newClient(readTimeoutSeconds = 1)
        val error = collect(provider(client)).second as ProviderException
        assertEquals(ProviderErrorKind.TIMEOUT, error.kind)
    }

    @Test
    fun unreachableServerIsAConnectionError() {
        val url = server.url("/v1").toString()
        server.close()
        val error = collect(provider(base = url)).second as ProviderException
        assertEquals(ProviderErrorKind.CONNECTION, error.kind)
    }

    @Test
    fun publicHttpIsBlockedBeforeAnythingIsSent() {
        val error = collect(provider(base = "http://api.example.com/v1")).second as ProviderException
        assertEquals(ProviderErrorKind.BLOCKED, error.kind)
        assertEquals(0, server.requestCount)
    }

    @Test
    fun eachCollectionSendsExactlyOneRequest() {
        server.enqueue(sseResponse("data: [DONE]\n\n"))
        val flow = provider().stream(request)
        assertEquals("building the flow must not start a request", 0, server.requestCount)
        runBlocking { flow.toList() }
        assertEquals(1, server.requestCount)
    }

    @Test
    fun listsModelsDedupesAndSkipsBadEntries() = runBlocking {
        server.enqueue(
            MockResponse.Builder().body("""{"object":"list","data":[{"id":"a"},{"id":"b","name":"Bee"},{"id":"a"},{"foo":1},"x",{"id":""}]}""").build(),
        )
        val models = provider().listModels()
        assertEquals(listOf(DiscoveredModel("a"), DiscoveredModel("b", "Bee")), models)
        assertEquals("/v1/models", server.takeRequest(5, TimeUnit.SECONDS)!!.target)
    }

    @Test
    fun modelListWithoutDataIsMalformed() = runBlocking {
        server.enqueue(MockResponse.Builder().body("""{"models":[]}""").build())
        server.enqueue(MockResponse.Builder().body("not json").build())
        repeat(2) {
            val error = runCatching { provider().listModels() }.exceptionOrNull() as ProviderException
            assertEquals(ProviderErrorKind.MALFORMED, error.kind)
        }
        assertNotNull(server.takeRequest(5, TimeUnit.SECONDS))
    }
}
