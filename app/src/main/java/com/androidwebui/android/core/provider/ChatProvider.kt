package com.androidwebui.android.core.provider

import java.io.IOException
import kotlinx.coroutines.flow.Flow
import kotlinx.serialization.json.JsonObject

/** One message sent to a provider. Text only until attachments arrive (Phase 8). */
data class ProviderMessage(val role: String, val content: String)

/** Sampling options. A `null` or empty value is omitted from the request instead of being sent. */
data class SamplingParams(
    val temperature: Double? = null,
    val topP: Double? = null,
    val maxTokens: Int? = null,
    val seed: Int? = null,
    val stop: List<String> = emptyList(),
    val frequencyPenalty: Double? = null,
    val presencePenalty: Double? = null,
)

/** [model] is the provider's own id: the connection prefix has already been removed. */
data class ChatRequest(
    val model: String,
    val messages: List<ProviderMessage>,
    val params: SamplingParams = SamplingParams(),
)

data class DiscoveredModel(val id: String, val name: String? = null)

/** Normalised provider output (plan Phase 4). Tool calls and artifacts are added with Phase 7. */
sealed interface ChatEvent {
    data class Text(val delta: String) : ChatEvent

    /** [raw] is the provider's own usage object, kept so it can be stored with the message. */
    data class Usage(val promptTokens: Int?, val completionTokens: Int?, val totalTokens: Int?, val raw: JsonObject) : ChatEvent

    /** Always the last event of a successful stream. */
    data class Done(val finishReason: String?) : ChatEvent
}

enum class ProviderErrorKind {
    AUTH,
    QUOTA,
    BAD_REQUEST,
    SERVER,
    REDIRECT,
    CONNECTION,
    TIMEOUT,
    MALFORMED,

    /** The stream ended before the model finished. Text received so far is still valid. */
    INTERRUPTED,

    /** Refused locally by [EndpointPolicy] before anything was sent. */
    BLOCKED,
}

/** Failure from a provider. The message never contains the API key. */
class ProviderException(
    val kind: ProviderErrorKind,
    message: String,
    val status: Int? = null,
    val retryAfterSeconds: Long? = null,
    cause: Throwable? = null,
) : IOException(message, cause)

/** One adapter per `provider_type` (D17). */
interface ChatProvider {
    /** Cold flow. Each collection sends one request. Cancelling the collector cancels the HTTP call. */
    fun stream(request: ChatRequest): Flow<ChatEvent>

    /** Also serves as the connection test: it fails with [ProviderException] if the endpoint is not usable. */
    suspend fun listModels(): List<DiscoveredModel>
}
