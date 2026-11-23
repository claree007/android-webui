package com.androidwebui.android.core.connections

/** The two connection lists in the global config (D16). */
enum class ConnectionKind(val section: String, val urlsKey: String) {
    OPENAI("openai", "api_base_urls"),
    OLLAMA("ollama", "base_urls"),
    ;

    /** A missing `provider_type` means this type (D17). */
    val defaultProviderType: String get() = section
}

/** One saved connection. [keyReference] is empty or a `keystore:<id>` reference, never a secret (D5). */
data class Connection(
    val kind: ConnectionKind,
    val index: Int,
    val baseUrl: String,
    val keyReference: String,
    val enabled: Boolean,
    val tags: List<String>,
    val prefixId: String,
    val modelIds: List<String>,
    val providerType: String,
) {
    val hasKey: Boolean get() = keyReference.isNotEmpty()
}

/** Values for creating or editing a connection. [apiKey] `null` keeps the stored key; blank removes it. */
data class ConnectionDraft(
    val baseUrl: String,
    val apiKey: String? = null,
    val enabled: Boolean = true,
    val tags: List<String> = emptyList(),
    val prefixId: String = "",
    val modelIds: List<String> = emptyList(),
    val providerType: String? = null,
)
