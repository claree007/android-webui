package com.androidwebui.android.core.connections

import com.androidwebui.android.core.database.ConfigRepository
import com.androidwebui.android.core.security.SecretStore
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull

/**
 * Reads and writes connections in the global `config` (D16, D17). API keys are stored through
 * [SecretStore] and only `keystore:<id>` references reach the database (D5). Keys of the config
 * document this class does not own are written back unchanged.
 */
class ConnectionRepository(
    private val config: ConfigRepository,
    private val secrets: SecretStore,
) {
    fun list(kind: ConnectionKind): List<Connection> =
        load(config.read(), kind).mapIndexed { index, entry -> entry.toConnection(kind, index) }

    fun add(kind: ConnectionKind, draft: ConnectionDraft): Connection {
        var added: Connection? = null
        config.update { root ->
            val entries = load(root, kind).toMutableList()
            entries += Entry("", "", JsonObject(emptyMap())).applying(draft)
            added = entries.last().toConnection(kind, entries.lastIndex)
            save(root, kind, entries)
        }
        return added!!
    }

    fun update(kind: ConnectionKind, index: Int, draft: ConnectionDraft): Connection {
        var updated: Connection? = null
        var replacedKey = ""
        config.update { root ->
            val entries = load(root, kind).toMutableList()
            require(index in entries.indices) { "No ${kind.section} connection at index $index" }
            val old = entries[index]
            entries[index] = old.applying(draft)
            if (entries[index].key != old.key) replacedKey = old.key
            updated = entries[index].toConnection(kind, index)
            save(root, kind, entries)
        }
        deleteSecret(replacedKey)
        return updated!!
    }

    fun remove(kind: ConnectionKind, index: Int) {
        var removedKey = ""
        config.update { root ->
            val entries = load(root, kind).toMutableList()
            require(index in entries.indices) { "No ${kind.section} connection at index $index" }
            removedKey = entries.removeAt(index).key
            save(root, kind, entries)
        }
        deleteSecret(removedKey)
    }

    /** The key to send. Falls back to a plain value so a connection merged from another database still works. */
    fun resolveKey(connection: Connection): String? =
        when {
            !connection.hasKey -> null
            SecretStore.isReference(connection.keyReference) -> secrets.get(connection.keyReference)
            else -> connection.keyReference
        }

    /** Moves keys that are still plain text (for example from a merge import) into the [SecretStore]. */
    fun migratePlaintextKeys() {
        config.update { root ->
            var result = root
            for (kind in ConnectionKind.entries) {
                val entries = load(result, kind)
                if (entries.none { it.hasPlaintextKey() }) continue
                result = save(result, kind, entries.map { if (it.hasPlaintextKey()) it.copy(key = secrets.put(it.key)) else it })
            }
            result
        }
    }

    private fun deleteSecret(reference: String) {
        if (SecretStore.isReference(reference)) secrets.delete(reference)
    }

    /** One position in the url/key/config lists. [config] keeps keys this class does not manage. */
    private data class Entry(val url: String, val key: String, val config: JsonObject) {
        fun hasPlaintextKey() = key.isNotEmpty() && !SecretStore.isReference(key)
    }

    private fun Entry.applying(draft: ConnectionDraft): Entry {
        val newKey = when {
            draft.apiKey == null -> key
            draft.apiKey.isBlank() -> ""
            else -> secrets.put(draft.apiKey.trim())
        }
        val existingTags = (config[TAGS] as? JsonArray).orEmpty().filterIsInstance<JsonObject>()
        val tags = draft.tags.map { name ->
            existingTags.firstOrNull { it[NAME]?.text() == name } ?: JsonObject(mapOf(NAME to JsonPrimitive(name)))
        }
        val updated = config.toMutableMap().apply {
            put(ENABLE, JsonPrimitive(draft.enabled))
            put(TAGS, JsonArray(tags))
            put(PREFIX_ID, JsonPrimitive(draft.prefixId))
            put(MODEL_IDS, JsonArray(draft.modelIds.map { JsonPrimitive(it) }))
            draft.providerType?.let { put(PROVIDER_TYPE, JsonPrimitive(it)) }
        }
        return Entry(draft.baseUrl.trim(), newKey, JsonObject(updated))
    }

    private fun Entry.toConnection(kind: ConnectionKind, index: Int) = Connection(
        kind = kind,
        index = index,
        baseUrl = url,
        keyReference = key,
        enabled = (config[ENABLE] as? JsonPrimitive)?.booleanOrNull ?: true,
        tags = (config[TAGS] as? JsonArray).orEmpty().mapNotNull { tag ->
            if (tag is JsonObject) tag[NAME]?.text() else tag.text()
        },
        prefixId = config[PREFIX_ID]?.text().orEmpty(),
        modelIds = (config[MODEL_IDS] as? JsonArray).orEmpty().mapNotNull { it.text() },
        providerType = config[PROVIDER_TYPE]?.text() ?: kind.defaultProviderType,
    )

    private fun load(root: JsonObject, kind: ConnectionKind): List<Entry> {
        val section = root[kind.section] as? JsonObject ?: return emptyList()
        val urls = (section[kind.urlsKey] as? JsonArray).orEmpty().map { it.text().orEmpty() }
        val keys = (section[API_KEYS] as? JsonArray).orEmpty().map { it.text().orEmpty() }
        val configs = section[API_CONFIGS] as? JsonObject
        return urls.mapIndexed { index, url ->
            val entryConfig = configs?.get(index.toString()) as? JsonObject ?: JsonObject(emptyMap())
            val key = when (kind) {
                ConnectionKind.OPENAI -> keys.getOrNull(index).orEmpty()
                ConnectionKind.OLLAMA -> entryConfig[KEY]?.text().orEmpty()
            }
            Entry(url, key, JsonObject(entryConfig - KEY))
        }
    }

    private fun save(root: JsonObject, kind: ConnectionKind, entries: List<Entry>): JsonObject {
        val section = (root[kind.section] as? JsonObject)?.toMutableMap() ?: mutableMapOf()
        section[kind.urlsKey] = JsonArray(entries.map { JsonPrimitive(it.url) })
        if (kind == ConnectionKind.OPENAI) section[API_KEYS] = JsonArray(entries.map { JsonPrimitive(it.key) })
        // Ollama keeps its key inside the per-connection config; OpenAI keeps a parallel api_keys list.
        section[API_CONFIGS] = JsonObject(
            entries.mapIndexed { index, entry ->
                val withKey = if (kind == ConnectionKind.OLLAMA) entry.config + (KEY to JsonPrimitive(entry.key)) else entry.config
                index.toString() to JsonObject(withKey)
            }.toMap(),
        )
        return JsonObject(root + (kind.section to JsonObject(section)))
    }

    private fun JsonElement.text(): String? = (this as? JsonPrimitive)?.contentOrNull

    private companion object {
        const val API_KEYS = "api_keys"
        const val API_CONFIGS = "api_configs"
        const val ENABLE = "enable"
        const val TAGS = "tags"
        const val NAME = "name"
        const val PREFIX_ID = "prefix_id"
        const val MODEL_IDS = "model_ids"
        const val PROVIDER_TYPE = "provider_type"
        const val KEY = "key"
    }
}
