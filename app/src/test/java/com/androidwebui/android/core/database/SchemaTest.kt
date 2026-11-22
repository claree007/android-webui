package com.androidwebui.android.core.database

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SchemaTest {
    private fun fingerprint(store: JdbcSqlStore, revision: String): List<String> {
        val c = store.connection
        val out = mutableListOf("alembic $revision")
        val tables = store.query(
            "SELECT name FROM sqlite_master WHERE type='table' AND name NOT LIKE 'sqlite_%' ORDER BY name",
        ) { it.getString(0)!! }
        for (t in tables) {
            out += "table $t"
            c.createStatement().use { st ->
                st.executeQuery("pragma table_info(\"$t\")").use { rs ->
                    while (rs.next()) {
                        out += "  col ${rs.getInt("cid")} ${rs.getString("name")} ${rs.getString("type")} " +
                            "notnull=${rs.getInt("notnull")} default=${rs.getString("dflt_value") ?: "None"} pk=${rs.getInt("pk")}"
                    }
                }
            }
            val raw = mutableListOf<List<String>>()
            c.createStatement().use { st ->
                st.executeQuery("pragma index_list(\"$t\")").use { rs ->
                    while (rs.next()) raw += listOf(rs.getString("name"), rs.getInt("unique").toString(), rs.getString("origin"), rs.getInt("partial").toString())
                }
            }
            val idx = raw.map { (name, unique, origin, partial) ->
                val cols = mutableListOf<String>()
                c.createStatement().use { st ->
                    st.executeQuery("pragma index_info(\"$name\")").use { rs -> while (rs.next()) cols += rs.getString("name") }
                }
                val label = if (name.startsWith("sqlite_autoindex")) "auto" else name
                "  index $label unique=$unique origin=$origin partial=$partial cols=${cols.joinToString(",")}"
            }
            out += idx.sorted()
        }
        return out
    }

    @Test
    fun createdSchemaMatchesPinnedOpenWebUiFingerprint() {
        val store = JdbcSqlStore.inMemory()
        SchemaInstaller.install(store, JdbcSqlStore.schemaScript())
        val revision = store.query("SELECT version_num FROM alembic_version") { it.getString(0)!! }.single()
        val expected = File("src/test/resources/open-webui-schema-fingerprint.txt").readLines().filter { it.isNotEmpty() }
        assertEquals(expected, fingerprint(store, revision))
    }

    @Test
    fun includesMultiUserTablesToo() {
        val store = JdbcSqlStore.inMemory()
        SchemaInstaller.install(store, JdbcSqlStore.schemaScript())
        val tables = store.query("SELECT name FROM sqlite_master WHERE type='table'") { it.getString(0)!! }.toSet()
        assertTrue(tables.containsAll(listOf("chat", "chat_message", "user", "config", "model", "tool", "function", "skill", "group", "channel", "feedback", "file")))
    }

    @Test
    fun parseStatementsSplitsOnMarkerAndIgnoresHeader() {
        val parsed = SchemaInstaller.parseStatements("-- header\n-- ;;\nCREATE TABLE a (\n x INT\n)\n-- ;;\nINSERT INTO a VALUES (1)\n")
        assertEquals(listOf("CREATE TABLE a (\n x INT\n)", "INSERT INTO a VALUES (1)"), parsed)
    }
}
