package com.androidwebui.android.core.database

import java.io.File
import java.sql.DriverManager
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class LocalProfileTest {
    @get:Rule val tmp = TemporaryFolder()

    private fun freshStore() = JdbcSqlStore.inMemory().also { SchemaInstaller.install(it, JdbcSqlStore.schemaScript()) }

    @Test
    fun createsSingleUserProfileAndIsIdempotent() {
        val store = freshStore()
        val first = LocalProfileRepository.ensure(store, nowSeconds = 100)
        val second = LocalProfileRepository.ensure(store, nowSeconds = 200)
        assertEquals("User", first.name)
        assertEquals(first, second)
        assertEquals(1L, store.queryLong("""SELECT COUNT(*) FROM "user""""))
    }

    @Test
    fun reusesExistingProfile() {
        val store = freshStore()
        store.execute(
            """INSERT INTO "user" (id, name, email, role, profile_image_url, created_at, updated_at, last_active_at)
               VALUES ('u1', 'Someone', 'a@b', 'admin', '', 1, 1, 1)""",
        )
        assertEquals(LocalProfile("u1", "Someone"), LocalProfileRepository.ensure(store))
        assertEquals(1L, store.queryLong("""SELECT COUNT(*) FROM "user""""))
    }

    @Test
    fun openWebUiShapedDatabaseOpensReadOnlyAndIsInspectable() {
        val file = File(tmp.root, "open-webui.db")
        val writer = JdbcSqlStore(DriverManager.getConnection("jdbc:sqlite:${file.absolutePath}"))
        SchemaInstaller.install(writer, JdbcSqlStore.schemaScript())
        LocalProfileRepository.ensure(writer)
        writer.connection.close()

        val ro = JdbcSqlStore.readOnly(file)
        val info = OpenWebUiDbInspector.inspect(ro)
        assertNotNull(info.alembicRevision)
        assertEquals(1L, info.tableRowCounts["user"])
        assertTrue(info.tableRowCounts.containsKey("chat_message"))
        assertTrue("read-only connection must reject writes", runCatching { ro.execute("""DELETE FROM "user"""") }.isFailure)
    }
}
