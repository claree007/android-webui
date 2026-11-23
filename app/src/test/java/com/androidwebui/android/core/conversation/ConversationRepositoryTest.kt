package com.androidwebui.android.core.conversation

import com.androidwebui.android.core.database.JdbcSqlStore
import com.androidwebui.android.core.database.LocalProfileRepository
import com.androidwebui.android.core.database.SchemaInstaller
import com.androidwebui.android.core.database.queryLong
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ConversationRepositoryTest {
    private val store = JdbcSqlStore.inMemory().also { SchemaInstaller.install(it, JdbcSqlStore.schemaScript()) }
    private val user = LocalProfileRepository.ensure(store).id
    private var now = 1_000L
    private val repo = ConversationRepository(store) { now }

    private fun chatJson(id: String): JsonObject =
        Json.parseToJsonElement(store.query("SELECT chat FROM chat WHERE id = ?", listOf(id)) { it.getString(0)!! }.single()).jsonObject

    private fun rowContent(chatId: String, messageId: String): String =
        store.query("SELECT content FROM chat_message WHERE id = ?", listOf("$chatId-$messageId")) { it.getString(0)!! }.single()

    @Test
    fun newChatIsEmptyListedAndLoadable() {
        val id = repo.create(user, listOf("gpt-x"))

        val chat = repo.load(id)!!
        assertEquals("New Chat", chat.title)
        assertEquals(listOf("gpt-x"), chat.models)
        assertTrue(chat.messages.isEmpty())
        assertEquals(listOf(id), repo.list(user).map { it.id })
        assertNull(repo.load("missing"))
    }

    @Test
    fun userAndAssistantMessagesFormTheOpenWebUiGraph() {
        val chatId = repo.create(user, listOf("gpt-x"))
        val u = repo.addUserMessage(chatId, "Hello", listOf("gpt-x"))
        val a = repo.beginReply(chatId, u.id, "gpt-x", "GPT X")

        val json = chatJson(chatId)
        val history = json.getValue("history").jsonObject
        assertEquals(a.id, history.getValue("currentId").jsonPrimitive.content)
        val messages = history.getValue("messages").jsonObject
        val userJson = messages.getValue(u.id).jsonObject
        assertEquals("user", userJson.getValue("role").jsonPrimitive.content)
        assertEquals(listOf(a.id), userJson.getValue("childrenIds").jsonArray.map { it.jsonPrimitive.content })
        assertEquals("gpt-x", userJson.getValue("models").jsonArray.single().jsonPrimitive.content)
        val assistant = messages.getValue(a.id).jsonObject
        assertEquals(u.id, assistant.getValue("parentId").jsonPrimitive.content)
        assertEquals("gpt-x", assistant.getValue("model").jsonPrimitive.content)
        assertEquals("false", assistant.getValue("done").jsonPrimitive.content)
        assertEquals("flat messages list mirrors the active branch", 2, json.getValue("messages").jsonArray.size)
    }

    @Test
    fun chatMessageRowsUseTheCompositeIdAndParentReference() {
        val chatId = repo.create(user, listOf("m"))
        val u = repo.addUserMessage(chatId, "Hi", listOf("m"))
        val a = repo.beginReply(chatId, u.id, "m", null)

        val rows = store.query("SELECT id, role, parent_id, done FROM chat_message WHERE chat_id = ? ORDER BY created_at, role DESC", listOf(chatId)) {
            listOf(it.getString(0), it.getString(1), it.getString(2), it.getLong(3).toString())
        }
        assertEquals(2, rows.size)
        val userRow = rows.single { it[1] == "user" }
        val assistantRow = rows.single { it[1] == "assistant" }
        assertEquals("$chatId-${u.id}", userRow[0])
        assertNull(userRow[2])
        assertEquals("1", userRow[3])
        assertEquals("$chatId-${a.id}", assistantRow[0])
        assertEquals("$chatId-${u.id}", assistantRow[2])
        assertEquals("0", assistantRow[3])
        assertEquals("\"Hi\"", rowContent(chatId, u.id))
    }

    @Test
    fun streamingWritesOnlyTheRowNotTheChatJson() {
        val chatId = repo.create(user, listOf("m"))
        val u = repo.addUserMessage(chatId, "Hi", listOf("m"))
        val a = repo.beginReply(chatId, u.id, "m", null)
        val jsonBefore = store.query("SELECT chat FROM chat WHERE id = ?", listOf(chatId)) { it.getString(0) }.single()
        val updatedBefore = store.queryLong("SELECT updated_at FROM chat WHERE id = ?", listOf(chatId))

        now = 2_000
        repo.updateInFlight(chatId, a.id, "Hel")
        repo.updateInFlight(chatId, a.id, "Hello 😀 \"quoted\"\nline")

        assertEquals("chat JSON must not be rewritten per token", jsonBefore, store.query("SELECT chat FROM chat WHERE id = ?", listOf(chatId)) { it.getString(0) }.single())
        assertEquals(updatedBefore, store.queryLong("SELECT updated_at FROM chat WHERE id = ?", listOf(chatId)))
        assertEquals("Hello 😀 \"quoted\"\nline", Json.parseToJsonElement(rowContent(chatId, a.id)).jsonPrimitive.content)
    }

    @Test
    fun loadingAnUnfinishedReplyShowsTheNewerRowText() {
        val chatId = repo.create(user, listOf("m"))
        val u = repo.addUserMessage(chatId, "Hi", listOf("m"))
        val a = repo.beginReply(chatId, u.id, "m", null)
        repo.updateInFlight(chatId, a.id, "partial answer")

        val loaded = repo.load(chatId)!!.messages.last()

        assertEquals("partial answer", loaded.content)
        assertFalse(loaded.done)
    }

    @Test
    fun finishReplyCheckpointsJsonAndRowTogether() {
        val chatId = repo.create(user, listOf("m"))
        val u = repo.addUserMessage(chatId, "Hi", listOf("m"))
        val a = repo.beginReply(chatId, u.id, "m", null)
        repo.updateInFlight(chatId, a.id, "Hel")

        now = 3_000
        val usage = JsonObject(mapOf("total_tokens" to JsonPrimitive(9)))
        repo.finishReply(chatId, a.id, "Hello", ReplyOutcome.Complete, usage)

        val message = chatJson(chatId).getValue("history").jsonObject.getValue("messages").jsonObject.getValue(a.id).jsonObject
        assertEquals("Hello", message.getValue("content").jsonPrimitive.content)
        assertEquals("true", message.getValue("done").jsonPrimitive.content)
        assertEquals(usage, message.getValue("usage"))
        assertEquals(1L, store.queryLong("SELECT done FROM chat_message WHERE id = ?", listOf("$chatId-${a.id}")))
        assertEquals(3_000L, store.queryLong("SELECT updated_at FROM chat WHERE id = ?", listOf(chatId)))
        val loaded = repo.load(chatId)!!.messages.last()
        assertEquals("Hello", loaded.content)
        assertTrue(loaded.done)
        assertEquals(9, loaded.usage!!.getValue("total_tokens").jsonPrimitive.content.toInt())
    }

    @Test
    fun failedAndStoppedRepliesKeepTheirTextAndAreMarked() {
        val chatId = repo.create(user, listOf("m"))
        val u = repo.addUserMessage(chatId, "Hi", listOf("m"))
        val failed = repo.beginReply(chatId, u.id, "m", null)
        repo.finishReply(chatId, failed.id, "partial", ReplyOutcome.Failed("HTTP 429"))
        val stopped = repo.beginReply(chatId, u.id, "m", null)
        repo.finishReply(chatId, stopped.id, "cut", ReplyOutcome.Stopped)

        val f = repo.load(chatId)!!.messages.last().takeIf { it.id == stopped.id }!!
        assertTrue(f.stopped)
        assertEquals("cut", f.content)
        assertNull(f.error)
        val reloadedFailed = chatJson(chatId).getValue("history").jsonObject.getValue("messages").jsonObject.getValue(failed.id).jsonObject
        assertEquals("HTTP 429", reloadedFailed.getValue("error").jsonObject.getValue("content").jsonPrimitive.content)
        assertEquals("partial", reloadedFailed.getValue("content").jsonPrimitive.content)
        assertNotNull(store.query("SELECT error FROM chat_message WHERE id = ?", listOf("$chatId-${failed.id}")) { it.getString(0) }.single())
    }

    @Test
    fun regeneratingAddsASiblingAndNeverDeletes() {
        val chatId = repo.create(user, listOf("m"))
        val u = repo.addUserMessage(chatId, "Hi", listOf("m"))
        val first = repo.beginReply(chatId, u.id, "m", null)
        repo.finishReply(chatId, first.id, "one", ReplyOutcome.Complete)
        val second = repo.beginReply(chatId, u.id, "m", null)
        repo.finishReply(chatId, second.id, "two", ReplyOutcome.Complete)

        val chat = repo.load(chatId)!!
        assertEquals(listOf(u.id, second.id), chat.messages.map { it.id })
        assertEquals(listOf(first.id, second.id), chat.messages.last().siblingIds)
        val graph = chatJson(chatId).getValue("history").jsonObject.getValue("messages").jsonObject
        assertEquals("one", graph.getValue(first.id).jsonObject.getValue("content").jsonPrimitive.content)
    }

    @Test
    fun unknownKeysInTheChatJsonSurviveEveryWrite() {
        val chatId = repo.create(user, listOf("m"))
        val json = chatJson(chatId)
        val extended = JsonObject(json + mapOf("custom" to JsonPrimitive("keep"), "tags" to Json.parseToJsonElement("""["a"]""")))
        store.execute("UPDATE chat SET chat = ? WHERE id = ?", listOf(Json.encodeToString(JsonObject.serializer(), extended), chatId))

        val u = repo.addUserMessage(chatId, "Hi", listOf("m"))
        val a = repo.beginReply(chatId, u.id, "m", null)
        repo.finishReply(chatId, a.id, "x", ReplyOutcome.Complete)
        repo.rename(chatId, "Renamed")
        repo.setParams(chatId, JsonObject(mapOf("temperature" to JsonPrimitive(0.3))))

        val after = chatJson(chatId)
        assertEquals("keep", after.getValue("custom").jsonPrimitive.content)
        assertEquals("""["a"]""", after.getValue("tags").toString())
        assertEquals("Renamed", after.getValue("title").jsonPrimitive.content)
        assertEquals("0.3", after.getValue("params").jsonObject.getValue("temperature").jsonPrimitive.content)
        assertEquals("Renamed", store.query("SELECT title FROM chat WHERE id = ?", listOf(chatId)) { it.getString(0) }.single())
    }

    @Test
    fun readsAChatWrittenByOpenWebUi() {
        val webChat = """{"id":"","title":"Imported","models":["gpt-5"],"params":{"system":"be brief"},
            "history":{"currentId":"a1","messages":{
              "u1":{"id":"u1","parentId":null,"childrenIds":["a1"],"role":"user","content":"Q","timestamp":5,"models":["gpt-5"]},
              "a1":{"id":"a1","parentId":"u1","childrenIds":[],"role":"assistant","content":"A","model":"gpt-5","modelName":"GPT-5","modelIdx":0,"timestamp":6,"done":true,
                    "output":[{"type":"message","id":"x","status":"completed","role":"assistant","content":[{"type":"output_text","text":"A"}]}],"followUps":["f"]}}},
            "messages":[],"tags":[],"timestamp":5,"files":[]}"""
        store.execute(
            """INSERT INTO chat (id, user_id, title, archived, created_at, updated_at, chat, pinned, meta)
               VALUES ('web1', ?, 'Imported', 0, 5, 9, ?, 1, '{}')""",
            listOf(user, webChat),
        )

        val chat = repo.load("web1")!!

        assertEquals("Imported", chat.title)
        assertTrue(chat.pinned)
        assertEquals(listOf("Q", "A"), chat.messages.map { it.content })
        assertEquals("GPT-5", chat.messages.last().modelName)
        assertEquals("be brief", chat.params.getValue("system").jsonPrimitive.content)
        assertEquals("web1", repo.list(user).single().id)
    }

    @Test
    fun corruptOrMissingChatJsonDoesNotCrashLoading() {
        store.execute(
            "INSERT INTO chat (id, user_id, title, archived, created_at, updated_at, chat, pinned, meta) VALUES ('bad', ?, 'Bad', 0, 1, 1, 'not json', 0, '{}')",
            listOf(user),
        )
        store.execute(
            "INSERT INTO chat (id, user_id, title, archived, created_at, updated_at, chat, pinned, meta) VALUES ('nul', ?, 'Null', 0, 1, 1, NULL, 0, '{}')",
            listOf(user),
        )
        assertTrue(repo.load("bad")!!.messages.isEmpty())
        assertTrue(repo.load("nul")!!.messages.isEmpty())
    }

    @Test
    fun cyclicParentLinksTerminate() {
        val cyc = """{"history":{"currentId":"a","messages":{
            "a":{"id":"a","parentId":"b","role":"user","content":"1"},
            "b":{"id":"b","parentId":"a","role":"assistant","content":"2"}}}}"""
        store.execute(
            "INSERT INTO chat (id, user_id, title, archived, created_at, updated_at, chat, pinned, meta) VALUES ('cyc', ?, 'C', 0, 1, 1, ?, 0, '{}')",
            listOf(user, cyc),
        )
        assertEquals(2, repo.load("cyc")!!.messages.size)
    }

    @Test
    fun listIsNewestFirstAndHidesArchivedAndOtherUsers() {
        val old = repo.create(user, listOf("m"))
        now = 5_000
        val recent = repo.create(user, listOf("m"))
        store.execute("INSERT INTO chat (id, user_id, title, archived, created_at, updated_at, chat, pinned, meta) VALUES ('arch', ?, 'A', 1, 1, 9999, '{}', 0, '{}')", listOf(user))
        store.execute("INSERT INTO chat (id, user_id, title, archived, created_at, updated_at, chat, pinned, meta) VALUES ('other', 'someone-else', 'O', 0, 1, 9999, '{}', 0, '{}')")

        assertEquals(listOf(recent, old), repo.list(user).map { it.id })
    }

    @Test
    fun deleteRemovesChatAndItsRows() {
        val chatId = repo.create(user, listOf("m"))
        repo.addUserMessage(chatId, "Hi", listOf("m"))

        repo.delete(chatId)

        assertNull(repo.load(chatId))
        assertEquals(0L, store.queryLong("SELECT COUNT(*) FROM chat_message WHERE chat_id = ?", listOf(chatId)))
    }

    @Test
    fun addingToAnUnknownParentFailsWithoutWriting() {
        val chatId = repo.create(user, listOf("m"))
        assertTrue(runCatching { repo.beginReply(chatId, "nope", "m", null) }.isFailure)
        assertEquals(0L, store.queryLong("SELECT COUNT(*) FROM chat_message WHERE chat_id = ?", listOf(chatId)))
    }
}
