package com.androidwebui.android.core.connections

import com.androidwebui.android.core.database.ConfigRepository
import com.androidwebui.android.core.database.JdbcSqlStore
import com.androidwebui.android.core.database.SchemaInstaller
import com.androidwebui.android.core.database.SqlStore
import com.androidwebui.android.core.prefs.InMemoryStorage
import com.androidwebui.android.core.security.EncryptedSecretStore
import com.androidwebui.android.core.security.SecretStore
import javax.crypto.KeyGenerator
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ConnectionRepositoryTest {
    private val store: SqlStore = JdbcSqlStore.inMemory().also { SchemaInstaller.install(it, JdbcSqlStore.schemaScript()) }
    private val storage = InMemoryStorage()
    private val key = KeyGenerator.getInstance("AES").apply { init(256) }.generateKey()
    private val secrets = EncryptedSecretStore({ key }, storage)
    private val config = ConfigRepository(store)
    private val repo = ConnectionRepository(config, secrets)

    private fun rawConfig(): String = store.query("SELECT data FROM config WHERE id = 1") { it.getString(0)!! }.single()

    private fun seed(json: String) = store.execute("INSERT INTO config (id, data, version) VALUES (1, ?, 0)", listOf(json))

    @Test
    fun startsEmptyWithoutAConfigRow() {
        assertTrue(repo.list(ConnectionKind.OPENAI).isEmpty())
        assertTrue(repo.list(ConnectionKind.OLLAMA).isEmpty())
    }

    @Test
    fun addStoresKeyOnlyAsReferenceAndNeverInTheDatabase() {
        val added = repo.add(ConnectionKind.OPENAI, ConnectionDraft("https://api.example.com/v1 ", apiKey = "sk-secret"))

        assertEquals("https://api.example.com/v1", added.baseUrl)
        assertTrue(SecretStore.isReference(added.keyReference))
        assertEquals("sk-secret", repo.resolveKey(added))
        assertFalse(rawConfig().contains("sk-secret"))
        assertFalse(storage.values.values.any { it.contains("sk-secret") })
    }

    @Test
    fun usesTheOpenWebUiLayoutForBothKinds() {
        repo.add(ConnectionKind.OPENAI, ConnectionDraft("https://a/v1", apiKey = "k", tags = listOf("fast"), prefixId = "a", modelIds = listOf("m1")))
        repo.add(ConnectionKind.OLLAMA, ConnectionDraft("http://10.0.0.2:11434", apiKey = "ok"))

        val root = Json.parseToJsonElement(rawConfig()).jsonObject
        val openai = root.getValue("openai").jsonObject
        assertEquals(1, openai.getValue("api_base_urls").toString().split("https").size - 1)
        assertEquals("""[{"name":"fast"}]""", openai.getValue("api_configs").jsonObject.getValue("0").jsonObject.getValue("tags").toString())
        assertTrue(openai.containsKey("api_keys"))

        val ollama = root.getValue("ollama").jsonObject
        assertTrue(ollama.containsKey("base_urls"))
        assertFalse("Ollama keeps its key in api_configs, not api_keys", ollama.containsKey("api_keys"))
        assertTrue(ollama.getValue("api_configs").jsonObject.getValue("0").jsonObject.containsKey("key"))
    }

    @Test
    fun readsAnUnmodifiedOpenWebUiConfig() {
        seed(
            """{"openai":{"enable":true,"api_base_urls":["https://a/v1","https://b/v1"],"api_keys":["plain-key",""],
               "api_configs":{"0":{"enable":false,"tags":[{"name":"t"}],"prefix_id":"p","model_ids":["x"]}}},
               "ollama":{"base_urls":["http://h:11434"],"api_configs":{"0":{"enable":true,"key":"","prefix_id":"","model_ids":[]}}}}""",
        )

        val openai = repo.list(ConnectionKind.OPENAI)
        assertEquals(2, openai.size)
        assertEquals(false, openai[0].enabled)
        assertEquals(listOf("t"), openai[0].tags)
        assertEquals("p", openai[0].prefixId)
        assertEquals(listOf("x"), openai[0].modelIds)
        assertEquals("openai", openai[0].providerType)
        assertEquals("plain-key", repo.resolveKey(openai[0]))
        assertNull(repo.resolveKey(openai[1]))
        assertTrue("A missing api_configs entry defaults to enabled", openai[1].enabled)
        assertEquals("ollama", repo.list(ConnectionKind.OLLAMA).single().providerType)
    }

    @Test
    fun unknownConfigKeysAndProviderTypeSurviveAnEdit() {
        seed(
            """{"ui":{"x":1},"openai":{"enable":true,"extra":"keep","api_base_urls":["https://a/v1"],"api_keys":[""],
               "api_configs":{"0":{"enable":true,"prefix_id":"","model_ids":[],"provider_type":"gemini","custom":7}}}}""",
        )

        val before = repo.list(ConnectionKind.OPENAI).single()
        assertEquals("gemini", before.providerType)
        repo.update(ConnectionKind.OPENAI, 0, ConnectionDraft("https://a/v2", enabled = false))

        val root: JsonObject = Json.parseToJsonElement(rawConfig()).jsonObject
        assertEquals("""{"x":1}""", root.getValue("ui").toString())
        val openai = root.getValue("openai").jsonObject
        assertEquals("\"keep\"", openai.getValue("extra").toString())
        val entry = openai.getValue("api_configs").jsonObject.getValue("0").jsonObject
        assertEquals("\"gemini\"", entry.getValue("provider_type").toString())
        assertEquals("7", entry.getValue("custom").toString())
        assertEquals("gemini", repo.list(ConnectionKind.OPENAI).single().providerType)
    }

    @Test
    fun explicitProviderTypeIsWrittenAndDefaultIsNot() {
        repo.add(ConnectionKind.OPENAI, ConnectionDraft("https://a/v1"))
        repo.add(ConnectionKind.OPENAI, ConnectionDraft("https://b/v1", providerType = "anthropic"))

        val entries = Json.parseToJsonElement(rawConfig()).jsonObject.getValue("openai").jsonObject.getValue("api_configs").jsonObject
        assertFalse(entries.getValue("0").jsonObject.containsKey("provider_type"))
        assertEquals("anthropic", repo.list(ConnectionKind.OPENAI)[1].providerType)
    }

    @Test
    fun updateKeepsKeyWhenNullReplacesAndDeletesOldSecret() {
        val added = repo.add(ConnectionKind.OPENAI, ConnectionDraft("https://a/v1", apiKey = "one"))

        val kept = repo.update(ConnectionKind.OPENAI, 0, ConnectionDraft("https://a/v1", apiKey = null, prefixId = "p"))
        assertEquals(added.keyReference, kept.keyReference)
        assertEquals("one", repo.resolveKey(kept))

        val replaced = repo.update(ConnectionKind.OPENAI, 0, ConnectionDraft("https://a/v1", apiKey = "two"))
        assertEquals("two", repo.resolveKey(replaced))
        assertNull("the replaced secret is deleted", secrets.get(added.keyReference))

        val cleared = repo.update(ConnectionKind.OPENAI, 0, ConnectionDraft("https://a/v1", apiKey = " "))
        assertFalse(cleared.hasKey)
        assertNull(secrets.get(replaced.keyReference))
    }

    @Test
    fun removeShiftsIndexesAndDeletesTheSecret() {
        val first = repo.add(ConnectionKind.OLLAMA, ConnectionDraft("http://one", apiKey = "a"))
        repo.add(ConnectionKind.OLLAMA, ConnectionDraft("http://two", apiKey = "b", tags = listOf("t")))

        repo.remove(ConnectionKind.OLLAMA, 0)

        val remaining = repo.list(ConnectionKind.OLLAMA).single()
        assertEquals("http://two", remaining.baseUrl)
        assertEquals(0, remaining.index)
        assertEquals("b", repo.resolveKey(remaining))
        assertNull(secrets.get(first.keyReference))
    }

    @Test
    fun migratesPlaintextKeysIntoTheSecretStore() {
        seed(
            """{"openai":{"api_base_urls":["https://a/v1"],"api_keys":["sk-old"],"api_configs":{}},
               "ollama":{"base_urls":["http://h"],"api_configs":{"0":{"key":"ollama-old"}}}}""",
        )

        repo.migratePlaintextKeys()

        assertFalse(rawConfig().contains("sk-old"))
        assertFalse(rawConfig().contains("ollama-old"))
        assertEquals("sk-old", repo.resolveKey(repo.list(ConnectionKind.OPENAI).single()))
        assertEquals("ollama-old", repo.resolveKey(repo.list(ConnectionKind.OLLAMA).single()))
        val after = rawConfig()
        repo.migratePlaintextKeys()
        assertEquals("a second run changes nothing", after, rawConfig())
    }

    @Test
    fun updatingAnUnknownIndexFails() {
        assertTrue(runCatching { repo.update(ConnectionKind.OPENAI, 3, ConnectionDraft("https://a")) }.isFailure)
        assertTrue(runCatching { repo.remove(ConnectionKind.OPENAI, 0) }.isFailure)
    }
}
