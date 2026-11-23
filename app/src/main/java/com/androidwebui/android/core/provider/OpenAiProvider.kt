package com.androidwebui.android.core.provider

import kotlinx.coroutines.flow.Flow
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import okhttp3.OkHttpClient

/**
 * OpenAI-compatible `/chat/completions` and `/models` (D9, D16). [baseUrl] includes the version
 * segment, for example `https://api.openai.com/v1`. The key goes only to this origin, and redirects
 * are never followed (see [ProviderHttp.newClient]).
 */
class OpenAiProvider(
    private val baseUrl: String,
    private val apiKey: String?,
    private val client: OkHttpClient = ProviderHttp.newClient(),
) : ChatProvider {

    override fun stream(request: ChatRequest): Flow<ChatEvent> = ProviderHttp.streamingFlow(
        client = client,
        secret = apiKey,
        request = { ProviderHttp.request(baseUrl, "/chat/completions", apiKey, requestBody(request), accept = "text/event-stream") },
        decode = { response -> decode(SseParser.parse(response.body.source())) },
    )

    override suspend fun listModels(): List<DiscoveredModel> {
        val text = ProviderHttp.fetchText(client, apiKey, ProviderHttp.request(baseUrl, "/models", apiKey, null, accept = "application/json"))
        val root = runCatching { Json.parseToJsonElement(text) }.getOrNull() as? JsonObject
            ?: throw ProviderException(ProviderErrorKind.MALFORMED, "The model list is not a JSON object")
        val data = root["data"] as? JsonArray
            ?: throw ProviderException(ProviderErrorKind.MALFORMED, "The model list has no \"data\" array")
        return data.mapNotNull { item ->
            val obj = item as? JsonObject ?: return@mapNotNull null
            val id = obj.text("id")?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
            DiscoveredModel(id, obj.text("name"))
        }.distinctBy { it.id }
    }

    internal fun requestBody(request: ChatRequest): JsonObject {
        val p = request.params
        return JsonObject(
            buildMap {
                put("model", JsonPrimitive(request.model))
                put("stream", JsonPrimitive(true))
                put("stream_options", JsonObject(mapOf("include_usage" to JsonPrimitive(true))))
                put(
                    "messages",
                    JsonArray(request.messages.map { JsonObject(mapOf("role" to JsonPrimitive(it.role), "content" to JsonPrimitive(it.content))) }),
                )
                p.temperature?.let { put("temperature", JsonPrimitive(it)) }
                p.topP?.let { put("top_p", JsonPrimitive(it)) }
                p.maxTokens?.let { put("max_tokens", JsonPrimitive(it)) }
                p.seed?.let { put("seed", JsonPrimitive(it)) }
                p.frequencyPenalty?.let { put("frequency_penalty", JsonPrimitive(it)) }
                p.presencePenalty?.let { put("presence_penalty", JsonPrimitive(it)) }
                if (p.stop.isNotEmpty()) put("stop", JsonArray(p.stop.map { JsonPrimitive(it) }))
            },
        )
    }

    /**
     * Turns SSE events into [ChatEvent]s. `[DONE]` ends the stream. If the stream ends without it,
     * the response is reported as [ProviderErrorKind.INTERRUPTED] unless a finish reason was seen.
     */
    internal fun decode(events: Sequence<SseEvent>): Sequence<ChatEvent> = sequence {
        var finishReason: String? = null
        var usage: ChatEvent.Usage? = null
        var done = false
        for (event in events) {
            val data = event.data.trim()
            if (data == "[DONE]") {
                done = true
                break
            }
            val chunk = runCatching { Json.parseToJsonElement(data) }.getOrNull() as? JsonObject
                ?: throw ProviderException(ProviderErrorKind.MALFORMED, "A stream event was not valid JSON")
            chunk["error"]?.takeIf { it !is JsonNull }?.let { throw streamError(it) }
            (chunk["usage"] as? JsonObject)?.let { usage = parseUsage(it) }
            val choice = (chunk["choices"] as? JsonArray)?.firstOrNull() as? JsonObject ?: continue
            val delta = choice["delta"] as? JsonObject
            delta?.text("content")?.takeIf { it.isNotEmpty() }?.let { yield(ChatEvent.Text(it)) }
            choice.text("finish_reason")?.let { finishReason = it }
        }
        if (!done && finishReason == null) {
            throw ProviderException(ProviderErrorKind.INTERRUPTED, "The response ended before the model finished")
        }
        usage?.let { yield(it) }
        yield(ChatEvent.Done(finishReason))
    }

    private fun parseUsage(obj: JsonObject) = ChatEvent.Usage(
        promptTokens = (obj["prompt_tokens"] as? JsonPrimitive)?.intOrNull,
        completionTokens = (obj["completion_tokens"] as? JsonPrimitive)?.intOrNull,
        totalTokens = (obj["total_tokens"] as? JsonPrimitive)?.intOrNull,
        raw = obj,
    )

    private fun streamError(error: JsonElement): ProviderException {
        val message = when (error) {
            is JsonObject -> error.text("message")
            is JsonPrimitive -> error.contentOrNull
            else -> null
        }
        val type = (error as? JsonObject)?.text("type").orEmpty()
        val kind = when {
            type.contains("quota") || type.contains("rate") || type.contains("insufficient") -> ProviderErrorKind.QUOTA
            type.contains("auth") || type.contains("permission") -> ProviderErrorKind.AUTH
            else -> ProviderErrorKind.SERVER
        }
        return ProviderException(kind, message?.take(300) ?: "The provider reported an error")
    }

    private fun JsonObject.text(key: String): String? = (this[key] as? JsonPrimitive)?.takeIf { it !is JsonNull }?.contentOrNull
}
