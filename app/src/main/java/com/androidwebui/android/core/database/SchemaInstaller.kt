package com.androidwebui.android.core.database

/**
 * Creates the schema compatible with the Open WebUI web app (D1) from the generated asset
 * `webui_schema.sql`. Statements are separated by `-- ;;` marker lines.
 */
object SchemaInstaller {
    const val ASSET_NAME = "webui_schema.sql"
    private const val SEPARATOR = "-- ;;"

    fun parseStatements(script: String): List<String> {
        val statements = ArrayList<String>()
        val current = StringBuilder()
        var seenSeparator = false
        for (line in script.lineSequence()) {
            if (line.trim() == SEPARATOR) {
                if (seenSeparator && current.isNotBlank()) statements += current.toString().trim()
                current.clear()
                seenSeparator = true
            } else if (seenSeparator) {
                current.appendLine(line)
            }
        }
        if (current.isNotBlank()) statements += current.toString().trim()
        return statements
    }

    fun install(store: SqlStore, script: String) {
        val statements = parseStatements(script)
        require(statements.isNotEmpty()) { "schema script contains no statements" }
        store.transaction { statements.forEach { store.execute(it) } }
    }
}
