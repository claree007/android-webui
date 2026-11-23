package com.androidwebui.android.core.provider

import kotlinx.coroutines.flow.Flow
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import okhttp3.OkHttpClient

/**
 * Ollama native `/api/chat` (NDJSON) and `/api/tags`. [baseUrl] is the server root, for example
 * `http://192.168.1.20:11434`. A key is optional and sent as a Bearer token for proxied servers.
 */
class OllamaProvider(
    private val baseUrl: String,
    private val apiKey: String?,
    private val client: OkHttpClient = ProviderHttp.newClient(),
) : ChatProvider {

    override fun stream(request: ChatRequest): Flow<ChatEvent> = ProviderHttp.streamingFlow(
        client = client,
        secret = apiKey,
        request = { ProviderHttp.request(baseUrl, "/api/chat", apiKey, requestBody(request), accept = "application/x-ndjson") },
        decode = { response -> decode(NdjsonParser.parse(response.body.source())) },
    )

    override suspend fun listModels(): List<DiscoveredModel> {
        val text = ProviderHttp.fetchText(client, apiKey, ProviderHttp.request(baseUrl, "/api/tags", apiKey, null, accept = "application/json"))
        val root = runCatching { Json.parseToJsonElement(text) }.getOrNull() as? JsonObject
            ?: throw ProviderException(ProviderErrorKind.MALFORMED, "The model list is not a JSON object")
        val models = root["models"] as? JsonArray
            ?: throw ProviderException(ProviderErrorKind.MALFORMED, "The model list has no \"models\" array")
        return models.mapNotNull { item ->
            val obj = item as? JsonObject ?: return@mapNotNull null
            val id = (obj.text("model") ?: obj.text("name"))?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
            DiscoveredModel(id, obj.text("name")?.takeIf { it != id })
        }.distinctBy { it.id }
    }

    internal fun requestBody(request: ChatRequest): JsonObject {
        val p = request.params
        val options = buildMap<String, kotlinx.serialization.json.JsonElement> {
            p.temperature?.let { put("temperature", JsonPrimitive(it)) }
            p.topP?.let { put("top_p", JsonPrimitive(it)) }
            p.maxTokens?.let { put("num_predict", JsonPrimitive(it)) }
            p.seed?.let { put("seed", JsonPrimitive(it)) }
            p.frequencyPenalty?.let { put("frequency_penalty", JsonPrimitive(it)) }
            p.presencePenalty?.let { put("presence_penalty", JsonPrimitive(it)) }
            if (p.stop.isNotEmpty()) put("stop", JsonArray(p.stop.map { JsonPrimitive(it) }))
        }
        return JsonObject(
            buildMap {
                put("model", JsonPrimitive(request.model))
                put("stream", JsonPrimitive(true))
                put(
                    "messages",
                    JsonArray(request.messages.map { JsonObject(mapOf("role" to JsonPrimitive(it.role), "content" to JsonPrimitive(it.content))) }),
                )
                if (options.isNotEmpty()) put("options", JsonObject(options))
            },
        )
    }

    /** The final object has `done: true`. A stream that ends without it is reported as interrupted. */
    internal fun decode(lines: Sequence<JsonObject>): Sequence<ChatEvent> = sequence {
        for (line in lines) {
            line["error"]?.takeIf { it !is JsonNull }?.let { error ->
                val message = (error as? JsonPrimitive)?.contentOrNull ?: (error as? JsonObject)?.text("message")
                throw ProviderException(ProviderErrorKind.SERVER, message?.take(300) ?: "The server reported an error")
            }
            (line["message"] as? JsonObject)?.text("content")?.takeIf { it.isNotEmpty() }?.let { yield(ChatEvent.Text(it)) }
            if ((line["done"] as? JsonPrimitive)?.booleanOrNull == true) {
                val prompt = (line["prompt_eval_count"] as? JsonPrimitive)?.intOrNull
                val completion = (line["eval_count"] as? JsonPrimitive)?.intOrNull
                if (prompt != null || completion != null) {
                    yield(ChatEvent.Usage(prompt, completion, if (prompt != null && completion != null) prompt + completion else null, line.usageFields()))
                }
                yield(ChatEvent.Done(line.text("done_reason")))
                return@sequence
            }
        }
        throw ProviderException(ProviderErrorKind.INTERRUPTED, "The response ended before the model finished")
    }

    /** Keeps Ollama's own timing and count fields so they can be stored with the message. */
    private fun JsonObject.usageFields() = JsonObject(
        filterKeys { it.endsWith("_count") || it.endsWith("_duration") },
    )

    private fun JsonObject.text(key: String): String? = (this[key] as? JsonPrimitive)?.takeIf { it !is JsonNull }?.contentOrNull
}
