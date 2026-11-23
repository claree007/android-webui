package com.androidwebui.android.core.provider

import java.io.IOException
import java.net.SocketTimeoutException
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response

/** Request building, error mapping and streaming glue shared by the provider adapters. */
internal object ProviderHttp {
    private val JSON_MEDIA = "application/json".toMediaType()
    private const val MAX_ERROR_BYTES = 64L * 1024
    private const val MAX_ERROR_DETAIL_CHARS = 300
    const val MAX_BODY_BYTES = 8L * 1024 * 1024

    /**
     * Redirects are never followed: an API endpoint that redirects is a misconfiguration, and
     * following it could send the API key to another origin. Streaming responses have no total
     * deadline; [readTimeoutSeconds] applies between bytes.
     */
    fun newClient(readTimeoutSeconds: Long = 120): OkHttpClient = OkHttpClient.Builder()
        .followRedirects(false)
        .followSslRedirects(false)
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(readTimeoutSeconds, TimeUnit.SECONDS)
        .build()

    fun request(baseUrl: String, path: String, apiKey: String?, body: JsonObject?, accept: String): Request {
        val rejected = EndpointPolicy.assess(baseUrl) as? EndpointAssessment.Rejected
        if (rejected != null) throw ProviderException(ProviderErrorKind.BLOCKED, "Endpoint not allowed (${rejected.reason})")
        val url = (baseUrl.trim().trimEnd('/') + path).toHttpUrlOrNull()
            ?: throw ProviderException(ProviderErrorKind.BLOCKED, "Endpoint not allowed (${RejectReason.INVALID_URL})")
        val builder = Request.Builder().url(url).header("Accept", accept)
        if (!apiKey.isNullOrBlank()) builder.header("Authorization", "Bearer ${apiKey.trim()}")
        if (body != null) builder.post(Json.encodeToString(JsonObject.serializer(), body).toRequestBody(JSON_MEDIA)) else builder.get()
        return builder.build()
    }

    /** Runs [request] and feeds the decoded events into a flow. Cancelling the collector cancels the call. */
    fun streamingFlow(
        client: OkHttpClient,
        secret: String?,
        request: () -> Request,
        decode: (Response) -> Sequence<ChatEvent>,
    ): Flow<ChatEvent> =
        callbackFlow {
            val call = client.newCall(request())
            launch(Dispatchers.IO) {
                var started = false
                try {
                    call.execute().use { response ->
                        started = true
                        response.requireSuccess(secret)
                        for (event in decode(response)) send(event)
                    }
                    close()
                } catch (e: CancellationException) {
                    throw e
                } catch (e: ProviderException) {
                    close(e)
                } catch (e: IOException) {
                    close(map(e, started))
                }
            }
            awaitClose { call.cancel() }
        }

    /** Runs [request] to completion and returns the body text, or throws a [ProviderException]. */
    suspend fun fetchText(client: OkHttpClient, secret: String?, request: Request): String = withContext(Dispatchers.IO) {
        try {
            client.newCall(request).execute().use { response ->
                response.requireSuccess(secret)
                response.readBodyCapped()
            }
        } catch (e: ProviderException) {
            throw e
        } catch (e: IOException) {
            throw map(e, started = false)
        }
    }

    fun Response.readBodyCapped(): String {
        val source = body.source()
        if (!source.request(MAX_BODY_BYTES + 1)) return source.readUtf8()
        throw ProviderException(ProviderErrorKind.MALFORMED, "Response is larger than $MAX_BODY_BYTES bytes")
    }

    /** Throws for non-2xx responses. Any occurrence of [secret] is removed from the message. */
    fun Response.requireSuccess(secret: String?) {
        if (isSuccessful) return
        val retryAfter = header("Retry-After")?.trim()?.toLongOrNull()
        if (isRedirect) {
            val target = header("Location")?.let { runCatching { request.url.resolve(it) }.getOrNull() }
                ?.let { "${it.scheme}://${it.host}" }
            throw ProviderException(
                ProviderErrorKind.REDIRECT,
                "The endpoint redirected to ${target ?: "another address"}. Redirects are not followed; update the connection URL.",
                status = code,
            )
        }
        val detail = errorDetail(peekErrorBody())?.let { if (secret.isNullOrBlank()) it else it.replace(secret.trim(), "[redacted]") }
        val kind = when (code) {
            401, 403 -> ProviderErrorKind.AUTH
            402, 429 -> ProviderErrorKind.QUOTA
            in 500..599 -> ProviderErrorKind.SERVER
            else -> ProviderErrorKind.BAD_REQUEST
        }
        throw ProviderException(kind, "HTTP $code${detail?.let { ": $it" }.orEmpty()}", status = code, retryAfterSeconds = retryAfter)
    }

    private fun Response.peekErrorBody(): String =
        runCatching { body.source().apply { request(MAX_ERROR_BYTES) }.buffer.snapshot().utf8() }.getOrDefault("")

    /** Pulls a human-readable message out of the common JSON error shapes; plain text is shortened. */
    fun errorDetail(body: String): String? {
        val text = body.trim().ifEmpty { return null }
        val json = runCatching { Json.parseToJsonElement(text) }.getOrNull() as? JsonObject
        val message = json?.let { root ->
            val error = root["error"]
            val fromError = when (error) {
                is JsonObject -> (error["message"] as? JsonPrimitive)?.contentOrNull
                is JsonPrimitive -> error.contentOrNull
                else -> null
            }
            fromError ?: (root["message"] as? JsonPrimitive)?.contentOrNull ?: (root["detail"] as? JsonPrimitive)?.contentOrNull
        } ?: if (json == null && !text.startsWith("<")) text else null
        return message?.replace(Regex("\\s+"), " ")?.take(MAX_ERROR_DETAIL_CHARS)
    }

    fun map(e: IOException, started: Boolean): ProviderException = when {
        e is ProviderException -> e
        e is SocketTimeoutException -> ProviderException(ProviderErrorKind.TIMEOUT, "The server did not respond in time", cause = e)
        started -> ProviderException(ProviderErrorKind.INTERRUPTED, "The connection was lost while receiving the response", cause = e)
        else -> ProviderException(ProviderErrorKind.CONNECTION, "Could not connect: ${e.javaClass.simpleName}", cause = e)
    }
}
