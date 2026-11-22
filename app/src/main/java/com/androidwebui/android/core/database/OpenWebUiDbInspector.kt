package com.androidwebui.android.core.database

data class OpenWebUiDbInfo(val alembicRevision: String?, val tableRowCounts: Map<String, Long>)

/** Read-only inspection of a foreign `webui.db`; the foundation for merge import (D2, Phase 12). */
object OpenWebUiDbInspector {
    fun inspect(store: SqlStore): OpenWebUiDbInfo {
        val tables = store.query(
            "SELECT name FROM sqlite_master WHERE type = 'table' AND name NOT LIKE 'sqlite_%' ORDER BY name",
        ) { it.getString(0)!! }
        val revision = if ("alembic_version" in tables) {
            store.query("SELECT version_num FROM alembic_version") { it.getString(0) }.firstOrNull()
        } else null
        val counts = tables.associateWith { table ->
            store.queryLong("""SELECT COUNT(*) FROM "$table"""") ?: 0L
        }
        return OpenWebUiDbInfo(revision, counts)
    }
}
