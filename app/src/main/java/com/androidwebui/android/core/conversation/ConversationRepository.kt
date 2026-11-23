package com.androidwebui.android.core.conversation

import com.androidwebui.android.core.database.SqlStore
import java.util.UUID
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.longOrNull

enum class StoredRole(val wire: String) { User("user"), Assistant("assistant") }

/** One message of the active branch. [done] is false only while a reply is being generated or was cut off. */
data class StoredMessage(
    val id: String,
    val parentId: String?,
    val role: StoredRole,
    val content: String,
    val done: Boolean,
    val error: String?,
    val stopped: Boolean,
    val model: String?,
    val modelName: String?,
    val timestamp: Long,
    val usage: JsonObject?,
    val siblingIds: List<String>,
)

data class StoredChat(
    val id: String,
    val title: String,
    val models: List<String>,
    val params: JsonObject,
    /** The active branch, root first, ending at `history.currentId`. */
    val messages: List<StoredMessage>,
    val updatedAt: Long,
    val pinned: Boolean,
)

data class ChatListItem(val id: String, val title: String, val updatedAt: Long, val pinned: Boolean)

sealed interface ReplyOutcome {
    data object Complete : ReplyOutcome
    data object Stopped : ReplyOutcome
    data class Failed(val message: String) : ReplyOutcome
}

/**
 * Conversation storage in the Open WebUI layout (D1, D3, D18). `chat.chat` holds the message graph;
 * `chat_message` is the derived row index. While a reply streams only its `chat_message` row is
 * written ([updateInFlight]); the full JSON is written when the reply ends ([finishReply]) and when
 * messages are added. Keys this class does not own are written back unchanged.
 *
 * All methods block on SQLite, so call them off the main thread.
 */
class ConversationRepository(
    private val store: SqlStore,
    private val clock: () -> Long = { System.currentTimeMillis() / 1000 },
) {

    fun create(userId: String, models: List<String>, params: JsonObject = JsonObject(emptyMap()), title: String = DEFAULT_TITLE): String {
        val id = UUID.randomUUID().toString()
        val now = clock()
        val chat = JsonObject(
            mapOf(
                "id" to JsonPrimitive(""),
                "title" to JsonPrimitive(title),
                "models" to JsonArray(models.map { JsonPrimitive(it) }),
                "params" to params,
                "history" to JsonObject(mapOf("messages" to JsonObject(emptyMap()), "currentId" to JsonNull)),
                "messages" to JsonArray(emptyList()),
                "tags" to JsonArray(emptyList()),
                "timestamp" to JsonPrimitive(now * 1000),
                "files" to JsonArray(emptyList()),
            ),
        )
        store.execute(
            """INSERT INTO chat (id, user_id, title, archived, created_at, updated_at, chat, pinned, meta)
               VALUES (?, ?, ?, 0, ?, ?, ?, 0, '{}')""",
            listOf(id, userId, title, now, now, encode(chat)),
        )
        return id
    }

    fun list(userId: String): List<ChatListItem> = store.query(
        "SELECT id, title, updated_at, pinned FROM chat WHERE user_id = ? AND archived = 0 ORDER BY updated_at DESC",
        listOf(userId),
    ) { ChatListItem(it.getString(0)!!, it.getString(1).orEmpty(), it.getLong(2), it.getLong(3) != 0L) }

    fun load(chatId: String): StoredChat? {
        val row = store.query("SELECT title, chat, updated_at, pinned FROM chat WHERE id = ?", listOf(chatId)) {
            ChatRow(it.getString(0), it.getString(1), it.getLong(2), it.getLong(3) != 0L)
        }.firstOrNull() ?: return null
        val chat = parse(row.json)
        val history = chat.obj("history")
        val graph = history.obj("messages")
        val inFlight = inFlightRows(chatId)
        val messages = pathToRoot(graph, history.text("currentId")).map { toStored(it, graph, inFlight[it.text("id")]) }
        return StoredChat(
            id = chatId,
            title = row.title ?: DEFAULT_TITLE,
            models = chat.array("models").mapNotNull { (it as? JsonPrimitive)?.contentOrNull },
            params = chat.obj("params"),
            messages = messages,
            updatedAt = row.updatedAt,
            pinned = row.pinned,
        )
    }

    /** Adds a user message under the current leaf (or at the root) and makes it the new leaf. */
    fun addUserMessage(chatId: String, text: String, models: List<String>): StoredMessage =
        addMessage(chatId, parentId = null, useCurrentLeaf = true, role = StoredRole.User, models = models, model = null, modelName = null, content = text)

    /** Adds an empty, unfinished assistant message under [parentId] and makes it the new leaf. */
    fun beginReply(chatId: String, parentId: String, model: String, modelName: String?): StoredMessage =
        addMessage(chatId, parentId, useCurrentLeaf = false, role = StoredRole.Assistant, models = null, model = model, modelName = modelName, content = "")

    /** Small write for streaming progress: only the derived `chat_message` row changes (D18). */
    fun updateInFlight(chatId: String, messageId: String, content: String) {
        store.execute(
            "UPDATE chat_message SET content = ?, updated_at = ? WHERE id = ?",
            listOf(encode(JsonPrimitive(content)), clock(), rowId(chatId, messageId)),
        )
    }

    /** Checkpoint: writes the final text and status to both the JSON and the row. */
    fun finishReply(chatId: String, messageId: String, content: String, outcome: ReplyOutcome, usage: JsonObject? = null) {
        store.transaction {
            val now = clock()
            val chat = parse(chatJson(chatId))
            val history = chat.obj("history")
            val graph = history.obj("messages")
            val message = graph[messageId] as? JsonObject ?: error("Unknown message $messageId")
            val error = (outcome as? ReplyOutcome.Failed)?.let { JsonObject(mapOf("content" to JsonPrimitive(it.message))) }
            val updated = JsonObject(
                message.toMutableMap().apply {
                    put("content", JsonPrimitive(content))
                    put("done", JsonPrimitive(true))
                    if (error != null) put("error", error) else remove("error")
                    if (outcome is ReplyOutcome.Stopped) put("stopped", JsonPrimitive(true)) else remove("stopped")
                    if (usage != null) put("usage", usage)
                },
            )
            saveGraph(chatId, chat, history, JsonObject(graph + (messageId to updated)), history.text("currentId"), now)
            store.execute(
                "UPDATE chat_message SET content = ?, done = 1, error = ?, usage = ?, updated_at = ? WHERE id = ?",
                listOf(encode(JsonPrimitive(content)), error?.let(::encode), usage?.let(::encode), now, rowId(chatId, messageId)),
            )
        }
    }

    fun rename(chatId: String, title: String) = store.transaction {
        val chat = parse(chatJson(chatId))
        store.execute(
            "UPDATE chat SET title = ?, chat = ?, updated_at = ? WHERE id = ?",
            listOf(title, encode(JsonObject(chat + ("title" to JsonPrimitive(title)))), clock(), chatId),
        )
    }

    fun setModels(chatId: String, models: List<String>) = store.transaction {
        val chat = parse(chatJson(chatId))
        store.execute(
            "UPDATE chat SET chat = ? WHERE id = ?",
            listOf(encode(JsonObject(chat + ("models" to JsonArray(models.map { JsonPrimitive(it) })))), chatId),
        )
    }

    fun setParams(chatId: String, params: JsonObject) = store.transaction {
        val chat = parse(chatJson(chatId))
        store.execute("UPDATE chat SET chat = ? WHERE id = ?", listOf(encode(JsonObject(chat + ("params" to params))), chatId))
    }

    /** Deletes the chat. Its `chat_message` rows go with it (foreign key cascade). */
    fun delete(chatId: String) {
        store.transaction {
            store.execute("DELETE FROM chat_message WHERE chat_id = ?", listOf(chatId))
            store.execute("DELETE FROM chat WHERE id = ?", listOf(chatId))
        }
    }

    private fun addMessage(
        chatId: String,
        parentId: String?,
        useCurrentLeaf: Boolean,
        role: StoredRole,
        models: List<String>?,
        model: String?,
        modelName: String?,
        content: String,
    ): StoredMessage = store.transaction {
        val now = clock()
        val chat = parse(chatJson(chatId))
        val history = chat.obj("history")
        val graph = history.obj("messages")
        val parent = if (useCurrentLeaf) history.text("currentId") else parentId
        require(parent == null || graph.containsKey(parent)) { "Unknown parent message $parent" }
        val id = UUID.randomUUID().toString()
        val fields = buildMap<String, JsonElement> {
            put("id", JsonPrimitive(id))
            put("parentId", parent?.let { JsonPrimitive(it) } ?: JsonNull)
            put("childrenIds", JsonArray(emptyList()))
            put("role", JsonPrimitive(role.wire))
            put("content", JsonPrimitive(content))
            put("timestamp", JsonPrimitive(now))
            if (models != null) put("models", JsonArray(models.map { JsonPrimitive(it) }))
            if (role == StoredRole.Assistant) {
                put("done", JsonPrimitive(false))
                model?.let { put("model", JsonPrimitive(it)) }
                modelName?.let { put("modelName", JsonPrimitive(it)) }
                put("modelIdx", JsonPrimitive(0))
            }
        }
        val withChild = if (parent != null) {
            val parentObj = graph.getValue(parent) as JsonObject
            val children = parentObj.array("childrenIds") + JsonPrimitive(id)
            graph + (parent to JsonObject(parentObj + ("childrenIds" to JsonArray(children))))
        } else {
            graph
        }
        saveGraph(chatId, chat, history, JsonObject(withChild + (id to JsonObject(fields))), id, now)
        store.execute(
            """INSERT INTO chat_message (id, chat_id, user_id, role, parent_id, content, model_id, done, created_at, updated_at)
               VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)""",
            listOf(
                rowId(chatId, id), chatId, ownerOf(chatId), role.wire, parent?.let { rowId(chatId, it) },
                encode(JsonPrimitive(content)), model, if (role == StoredRole.User) 1 else 0, now, now,
            ),
        )
        toStored(JsonObject(fields), JsonObject(withChild + (id to JsonObject(fields))), null)
    }

    /** Writes the graph, the flat `messages` list of the active branch, and the chat row together. */
    private fun saveGraph(chatId: String, chat: JsonObject, history: JsonObject, graph: JsonObject, currentId: String?, now: Long) {
        val newHistory = JsonObject(history + mapOf("messages" to graph, "currentId" to (currentId?.let { JsonPrimitive(it) } ?: JsonNull)))
        val flat = JsonArray(pathToRoot(graph, currentId))
        val newChat = JsonObject(chat + mapOf("history" to newHistory, "messages" to flat))
        store.execute("UPDATE chat SET chat = ?, updated_at = ? WHERE id = ?", listOf(encode(newChat), now, chatId))
    }

    private fun pathToRoot(graph: JsonObject, leaf: String?): List<JsonObject> {
        val path = ArrayDeque<JsonObject>()
        var id = leaf
        val seen = HashSet<String>()
        while (id != null && seen.add(id)) {
            val message = graph[id] as? JsonObject ?: break
            path.addFirst(message)
            id = message.text("parentId")
        }
        return path.toList()
    }

    private fun toStored(message: JsonObject, graph: JsonObject, inFlight: String?): StoredMessage {
        val role = if (message.text("role") == StoredRole.Assistant.wire) StoredRole.Assistant else StoredRole.User
        val done = (message["done"] as? JsonPrimitive)?.booleanOrNull ?: true
        val parentId = message.text("parentId")
        val siblings = parentId?.let { (graph[it] as? JsonObject)?.array("childrenIds") }.orEmpty()
        return StoredMessage(
            id = message.text("id").orEmpty(),
            parentId = parentId,
            role = role,
            // An unfinished reply's newest text is in its row (D18).
            content = if (!done && inFlight != null) inFlight else message.text("content").orEmpty(),
            done = done,
            error = (message["error"] as? JsonObject)?.text("content"),
            stopped = (message["stopped"] as? JsonPrimitive)?.booleanOrNull ?: false,
            model = message.text("model"),
            modelName = message.text("modelName"),
            timestamp = (message["timestamp"] as? JsonPrimitive)?.longOrNull ?: 0,
            usage = message["usage"] as? JsonObject,
            siblingIds = siblings.mapNotNull { (it as? JsonPrimitive)?.contentOrNull },
        )
    }

    /** Content of unfinished replies from their rows, decoded from the JSON string stored there. */
    private fun inFlightRows(chatId: String): Map<String, String> =
        store.query("SELECT id, content FROM chat_message WHERE chat_id = ? AND role = 'assistant' AND done = 0", listOf(chatId)) {
            it.getString(0)!! to it.getString(1)
        }.associate { (rowId, raw) ->
            rowId.removePrefix("$chatId-") to (raw?.let { runCatching { (Json.parseToJsonElement(it) as? JsonPrimitive)?.contentOrNull }.getOrNull() } ?: "")
        }

    private fun chatJson(chatId: String): String? =
        store.query("SELECT chat FROM chat WHERE id = ?", listOf(chatId)) { it.getString(0) }.firstOrNull()
            ?: error("Unknown chat $chatId")

    private fun ownerOf(chatId: String): String? =
        store.query("SELECT user_id FROM chat WHERE id = ?", listOf(chatId)) { it.getString(0) }.firstOrNull()

    private fun parse(text: String?): JsonObject =
        text?.let { runCatching { Json.parseToJsonElement(it) }.getOrNull() as? JsonObject } ?: JsonObject(emptyMap())

    private fun encode(element: JsonElement): String = Json.encodeToString(JsonElement.serializer(), element)

    private fun rowId(chatId: String, messageId: String) = "$chatId-$messageId"

    private fun JsonObject.obj(key: String): JsonObject = this[key] as? JsonObject ?: JsonObject(emptyMap())
    private fun JsonObject.array(key: String): JsonArray = this[key] as? JsonArray ?: JsonArray(emptyList())
    private fun JsonObject.text(key: String): String? = (this[key] as? JsonPrimitive)?.takeIf { it !is JsonNull }?.contentOrNull

    private class ChatRow(val title: String?, val json: String?, val updatedAt: Long, val pinned: Boolean)

    companion object {
        const val DEFAULT_TITLE = "New Chat"
    }
}
