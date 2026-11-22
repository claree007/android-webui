package com.androidwebui.android.core.database

import java.io.File
import java.sql.Connection
import java.sql.DriverManager

/** JVM [SqlStore] backed by sqlite-jdbc, used by tests. */
class JdbcSqlStore(val connection: Connection) : SqlStore {
    override fun execute(sql: String, args: List<Any?>) {
        connection.prepareStatement(sql).use { st ->
            args.forEachIndexed { i, a -> st.setObject(i + 1, a) }
            st.execute()
        }
    }

    override fun <T> query(sql: String, args: List<Any?>, map: (SqlRow) -> T): List<T> =
        connection.prepareStatement(sql).use { st ->
            args.forEachIndexed { i, a -> st.setObject(i + 1, a) }
            st.executeQuery().use { rs ->
                val row = object : SqlRow {
                    override fun getString(index: Int): String? = rs.getString(index + 1)
                    override fun getLong(index: Int): Long = rs.getLong(index + 1)
                }
                val out = ArrayList<T>()
                while (rs.next()) out += map(row)
                out
            }
        }

    override fun <T> transaction(block: () -> T): T {
        val previous = connection.autoCommit
        connection.autoCommit = false
        try {
            val result = block()
            connection.commit()
            return result
        } catch (t: Throwable) {
            connection.rollback()
            throw t
        } finally {
            connection.autoCommit = previous
        }
    }

    companion object {
        fun inMemory() = JdbcSqlStore(DriverManager.getConnection("jdbc:sqlite::memory:"))

        fun readOnly(file: File) =
            JdbcSqlStore(DriverManager.getConnection("jdbc:sqlite:file:${file.absolutePath}?mode=ro"))

        fun schemaScript(): String = File("src/main/assets/${SchemaInstaller.ASSET_NAME}").readText()
    }
}
