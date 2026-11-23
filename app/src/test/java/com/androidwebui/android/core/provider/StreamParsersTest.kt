package com.androidwebui.android.core.provider

import okio.Buffer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class StreamParsersTest {
    private fun sse(text: String, chunk: Int = 1_000_000) = SseParser.parse(chunked(text, chunk)).toList()

    private fun ndjson(text: String, chunk: Int = 1_000_000) = NdjsonParser.parse(chunked(text, chunk)).toList()

    @Test
    fun parsesPlainEvents() {
        val events = sse("data: one\n\ndata: two\n\n")
        assertEquals(listOf("one", "two"), events.map { it.data })
    }

    @Test
    fun resultIsTheSameForEveryChunkSize() {
        val text = "event: a\nid: 7\ndata: first\ndata: second\n\n: comment\ndata: héllo 😀 世界\n\ndata: [DONE]\n\n"
        val expected = sse(text)
        for (size in 1..text.length) assertEquals("chunk size $size", expected, sse(text, size))
        assertEquals(listOf("first\nsecond", "héllo 😀 世界", "[DONE]"), expected.map { it.data })
        assertEquals("a", expected[0].event)
        assertEquals("7", expected[0].id)
    }

    @Test
    fun multiByteCharactersSplitAcrossChunksAreIntact() {
        val emoji = "data: 😀é世\n\n"
        assertEquals("😀é世", sse(emoji, 1).single().data)
        assertEquals("😀é世", sse(emoji, 3).single().data)
    }

    @Test
    fun handlesCrLfAndCrLineEndingsOfCrLfStreams() {
        assertEquals(listOf("a", "b"), sse("data: a\r\n\r\ndata: b\r\n\r\n").map { it.data })
    }

    @Test
    fun ignoresCommentsKeepAlivesAndUnknownFields() {
        val events = sse(": ping\n\nretry: 100\nfoo: bar\ndata: x\n\n")
        assertEquals(listOf("x"), events.map { it.data })
    }

    @Test
    fun fieldWithoutColonAndOptionalSpace() {
        assertEquals("", sse("data\n\n").single().data)
        assertEquals(" two", sse("data:  two\n\n").single().data)
        assertEquals("nospace", sse("data:nospace\n\n").single().data)
    }

    @Test
    fun finalEventWithoutBlankLineIsDelivered() {
        assertEquals(listOf("a", "[DONE]"), sse("data: a\n\ndata: [DONE]\n").map { it.data })
    }

    @Test
    fun halfReceivedLineAtEofIsDropped() {
        assertEquals(listOf("a"), sse("data: a\n\ndata: {\"partial").map { it.data })
    }

    @Test
    fun oversizedLineFailsInsteadOfGrowingForever() {
        val huge = "data: " + "x".repeat(SseParser.MAX_LINE_BYTES + 10) + "\n\n"
        val error = runCatching { sse(huge, 64 * 1024) }.exceptionOrNull() as ProviderException
        assertEquals(ProviderErrorKind.MALFORMED, error.kind)
    }

    @Test
    fun networkErrorMidStreamSurfacesToTheCaller() {
        val source = chunked("data: a\n\ndata: b", chunkSize = 4, failAtEnd = true)
        val seen = mutableListOf<String>()
        val error = runCatching { SseParser.parse(source).forEach { seen += it.data } }.exceptionOrNull()
        assertEquals(listOf("a"), seen)
        assertTrue(error is java.io.IOException)
    }

    @Test
    fun parsesNdjsonAcrossChunksAndSkipsBlankLines() {
        val text = "{\"a\":1}\n\n{\"b\":\"é😀\"}\n{\"c\":3}"
        val expected = listOf("""{"a":1}""", """{"b":"é😀"}""", """{"c":3}""")
        for (size in listOf(1, 2, 5, 1000)) assertEquals("chunk $size", expected, ndjson(text, size).map { it.toString() })
    }

    @Test
    fun ndjsonRejectsNonObjectsAndGarbage() {
        listOf("[1,2]\n", "not json\n", "{\"a\":\n").forEach {
            val error = runCatching { ndjson(it) }.exceptionOrNull() as ProviderException
            assertEquals(it, ProviderErrorKind.MALFORMED, error.kind)
        }
    }

    @Test
    fun readLineHandlesEmptyInput() {
        assertTrue(sse("").isEmpty())
        assertTrue(ndjson("").isEmpty())
        assertEquals(0L, Buffer().size)
    }
}
