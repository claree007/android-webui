package com.androidwebui.android.core.provider

import java.util.concurrent.TimeUnit
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class OllamaProviderTest {
    private lateinit var server: MockWebServer
    private val request = ChatRequest("llama3:latest", listOf(ProviderMessage("user", "hi")))

    @Before fun start() {
        server = MockWebServer().apply { start() }
    }

    @After fun stop() {
        server.close()
    }

    private fun provider(key: String? = null) = OllamaProvider(server.url("/").toString().trimEnd('/'), key)

    private fun ndjson(body: String, chunk: Int? = null) = MockResponse.Builder()
        .addHeader("Content-Type", "application/x-ndjson")
        .apply { if (chunk != null) chunkedBody(body, chunk) else body(body) }
        .build()

    private fun collect(p: OllamaProvider = provider()): Pair<List<ChatEvent>, Throwable?> = runBlocking {
        withTimeout(15_000) {
            val events = mutableListOf<ChatEvent>()
            var failure: Throwable? = null
            p.stream(request).catch { failure = it }.toList(events)
            events to failure
        }
    }

    private fun line(content: String) = """{"model":"m","message":{"role":"assistant","content":${kotlinx.serialization.json.JsonPrimitive(content)}},"done":false}""" + "\n"

    private val doneLine = """{"model":"m","message":{"role":"assistant","content":""},"done":true,"done_reason":"stop","prompt_eval_count":4,"eval_count":6,"total_duration":123}""" + "\n"

    @Test
    fun streamsTextThenUsageThenDone() {
        server.enqueue(ndjson(line("Hel") + line("lo 😀") + doneLine))

        val (events, failure) = collect()

        assertNull(failure)
        assertEquals(listOf("Hel", "lo 😀"), events.filterIsInstance<ChatEvent.Text>().map { it.delta })
        val usage = events.filterIsInstance<ChatEvent.Usage>().single()
        assertEquals(10, usage.totalTokens)
        assertTrue(usage.raw.containsKey("total_duration"))
        assertEquals(ChatEvent.Done("stop"), events.last())
    }

    @Test
    fun survivesTinyNetworkChunks() {
        server.enqueue(ndjson(line("héllo 世界 😀") + doneLine, chunk = 2))
        val (events, failure) = collect()
        assertNull(failure)
        assertEquals("héllo 世界 😀", events.filterIsInstance<ChatEvent.Text>().joinToString("") { it.delta })
    }

    @Test
    fun endWithoutDoneIsInterruptedAndKeepsText() {
        server.enqueue(ndjson(line("partial")))
        val (events, failure) = collect()
        assertEquals(listOf("partial"), events.filterIsInstance<ChatEvent.Text>().map { it.delta })
        assertEquals(ProviderErrorKind.INTERRUPTED, (failure as ProviderException).kind)
    }

    @Test
    fun finalLineWithoutTrailingNewlineIsStillRead() {
        server.enqueue(ndjson(line("a") + doneLine.trimEnd()))
        val (events, failure) = collect()
        assertNull(failure)
        assertEquals(ChatEvent.Done("stop"), events.last())
    }

    @Test
    fun malformedLineFails() {
        server.enqueue(ndjson(line("a") + "{oops\n" + line("b")))
        val (events, failure) = collect()
        assertEquals(listOf("a"), events.filterIsInstance<ChatEvent.Text>().map { it.delta })
        assertEquals(ProviderErrorKind.MALFORMED, (failure as ProviderException).kind)
    }

    @Test
    fun errorLineInsideTheStreamIsReported() {
        server.enqueue(ndjson(line("a") + """{"error":"model requires more system memory"}""" + "\n"))
        val (_, failure) = collect()
        val error = failure as ProviderException
        assertEquals(ProviderErrorKind.SERVER, error.kind)
        assertTrue(error.message!!.contains("system memory"))
    }

    @Test
    fun httpErrorsAreClassified() {
        server.enqueue(MockResponse.Builder().code(404).body("""{"error":"model 'nope' not found"}""").build())
        server.enqueue(MockResponse.Builder().code(401).body("""{"error":"unauthorized"}""").build())
        val notFound = collect().second as ProviderException
        val auth = collect().second as ProviderException
        assertEquals(ProviderErrorKind.BAD_REQUEST, notFound.kind)
        assertTrue(notFound.message!!.contains("not found"))
        assertEquals(ProviderErrorKind.AUTH, auth.kind)
    }

    @Test
    fun keyIsSentAsBearerOnlyWhenConfiguredAndNeverEchoed() {
        server.enqueue(ndjson(doneLine))
        server.enqueue(ndjson(doneLine))
        server.enqueue(MockResponse.Builder().code(401).body("""{"error":"bad token sekret-1"}""").build())
        collect(provider(key = null))
        collect(provider(key = "sekret-1"))
        val error = collect(provider(key = "sekret-1")).second as ProviderException

        assertNull(server.takeRequest(5, TimeUnit.SECONDS)!!.headers["Authorization"])
        assertEquals("Bearer sekret-1", server.takeRequest(5, TimeUnit.SECONDS)!!.headers["Authorization"])
        assertFalse(error.message!!.contains("sekret-1"))
    }

    @Test
    fun redirectsAreNotFollowed() {
        MockWebServer().use { other ->
            other.start()
            server.enqueue(MockResponse.Builder().code(301).addHeader("Location", other.url("/api/chat").toString()).build())
            val error = collect(provider(key = "k")).second as ProviderException
            assertEquals(ProviderErrorKind.REDIRECT, error.kind)
            assertEquals(0, other.requestCount)
        }
    }

    @Test
    fun requestBodyUsesOllamaOptionNames() {
        val body = provider().requestBody(
            request.copy(params = SamplingParams(temperature = 0.2, maxTokens = 64, topP = 0.9, seed = 7, stop = listOf("X"))),
        ).toString()
        assertTrue(body.contains("\"num_predict\":64"))
        assertTrue(body.contains("\"options\":{"))
        assertTrue(body.contains("\"stream\":true"))
        assertFalse(provider().requestBody(request).toString().contains("options"))
    }

    @Test
    fun postsToApiChat() {
        server.enqueue(ndjson(doneLine))
        collect()
        assertEquals("/api/chat", server.takeRequest(5, TimeUnit.SECONDS)!!.target)
    }

    @Test
    fun listsModelsFromTags() = runBlocking {
        server.enqueue(
            MockResponse.Builder().body("""{"models":[{"name":"llama3:latest","model":"llama3:latest"},{"name":"phi","model":"phi:2"},{"name":"llama3:latest"},{"x":1}]}""").build(),
        )
        assertEquals(listOf(DiscoveredModel("llama3:latest"), DiscoveredModel("phi:2", "phi")), provider().listModels())
        assertEquals("/api/tags", server.takeRequest(5, TimeUnit.SECONDS)!!.target)
    }

    @Test
    fun modelListWithoutModelsIsMalformed() = runBlocking {
        server.enqueue(MockResponse.Builder().body("""{"data":[]}""").build())
        val error = runCatching { provider().listModels() }.exceptionOrNull() as ProviderException
        assertEquals(ProviderErrorKind.MALFORMED, error.kind)
    }
}
