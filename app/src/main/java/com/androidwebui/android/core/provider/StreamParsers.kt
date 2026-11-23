package com.androidwebui.android.core.provider

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import okio.BufferedSource

data class SseEvent(val event: String?, val data: String, val id: String?)

private const val LF = '\n'.code.toByte()

/**
 * Reads one line. Returns null at end of stream. Decoding happens per line, so a multi-byte
 * character split across network chunks is always whole by the time it is decoded.
 * An unterminated final line is returned only when [includeUnterminated] is true.
 */
internal fun BufferedSource.readLineOrNull(maxBytes: Int, includeUnterminated: Boolean): String? {
    val newline = indexOf(LF, 0, maxBytes + 1L)
    if (newline >= 0) {
        val line = readUtf8(newline)
        skip(1)
        return line.removeSuffix("\r")
    }
    if (buffer.size > maxBytes) throw ProviderException(ProviderErrorKind.MALFORMED, "Stream line is longer than $maxBytes bytes")
    if (exhausted()) return null
    if (includeUnterminated) return readUtf8()
    skip(buffer.size)
    return null
}

/**
 * Server-sent events (WHATWG). Comments and unknown fields are ignored. A complete event that is
 * not followed by a blank line before the stream ends is still delivered, because several
 * compatible servers close right after `data: [DONE]`. A half-received line is dropped.
 */
object SseParser {
    const val MAX_LINE_BYTES = 1 shl 20
    const val MAX_EVENT_CHARS = 8 shl 20

    fun parse(source: BufferedSource): Sequence<SseEvent> = sequence {
        var event: String? = null
        var id: String? = null
        val data = StringBuilder()
        var hasData = false
        while (true) {
            val line = source.readLineOrNull(MAX_LINE_BYTES, includeUnterminated = false) ?: break
            if (line.isEmpty()) {
                if (hasData) yield(SseEvent(event, data.toString(), id))
                event = null
                data.setLength(0)
                hasData = false
                continue
            }
            if (line.startsWith(":")) continue
            val colon = line.indexOf(':')
            val field = if (colon < 0) line else line.substring(0, colon)
            val value = (if (colon < 0) "" else line.substring(colon + 1)).removePrefix(" ")
            when (field) {
                "data" -> {
                    if (hasData) data.append('\n')
                    data.append(value)
                    hasData = true
                    if (data.length > MAX_EVENT_CHARS) {
                        throw ProviderException(ProviderErrorKind.MALFORMED, "Stream event is larger than $MAX_EVENT_CHARS characters")
                    }
                }
                "event" -> event = value
                "id" -> id = value
            }
        }
        if (hasData) yield(SseEvent(event, data.toString(), id))
    }
}

/** Newline-delimited JSON objects, as used by Ollama. Blank lines are skipped. */
object NdjsonParser {
    const val MAX_LINE_BYTES = 4 shl 20

    fun parse(source: BufferedSource): Sequence<JsonObject> = sequence {
        var lineNumber = 0
        while (true) {
            val line = source.readLineOrNull(MAX_LINE_BYTES, includeUnterminated = true) ?: break
            lineNumber++
            if (line.isBlank()) continue
            val parsed = runCatching { Json.parseToJsonElement(line) }.getOrNull() as? JsonObject
                ?: throw ProviderException(ProviderErrorKind.MALFORMED, "Line $lineNumber of the response is not a JSON object")
            yield(parsed)
        }
    }
}
