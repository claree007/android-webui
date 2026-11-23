package com.androidwebui.android.core.database

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject

/**
 * The global `config` row (D16). Open WebUI keeps one row (`id = 1`) whose `data` column is a JSON
 * document. Callers change only the keys they own; every other key is written back untouched so the
 * document keeps round-tripping with the Open WebUI web app (D1, D2).
 */
class ConfigRepository(private val store: SqlStore) {

    fun read(): JsonObject = readRow() ?: JsonObject(emptyMap())

    /** Applies [change] to the current document and stores the result atomically. */
    fun update(change: (JsonObject) -> JsonObject): JsonObject = store.transaction {
        val existing = readRow()
        val updated = change(existing ?: JsonObject(emptyMap()))
        val text = Json.encodeToString(JsonObject.serializer(), updated)
        if (existing == null) {
            store.execute("INSERT INTO config (id, data, version) VALUES (?, ?, 0)", listOf(ROW_ID, text))
        } else {
            store.execute("UPDATE config SET data = ?, updated_at = CURRENT_TIMESTAMP WHERE id = ?", listOf(text, ROW_ID))
        }
        updated
    }

    private fun readRow(): JsonObject? =
        store.query("SELECT data FROM config WHERE id = ?", listOf(ROW_ID)) { it.getString(0) }
            .firstOrNull()
            ?.let { Json.parseToJsonElement(it) as? JsonObject }

    private companion object {
        const val ROW_ID = 1L
    }
}
