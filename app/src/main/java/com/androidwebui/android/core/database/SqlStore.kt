package com.androidwebui.android.core.database

/**
 * Thin SQL abstraction over plain SQLite (D4). The Android implementation wraps
 * SQLiteDatabase; tests use a JDBC implementation so repositories run on the JVM.
 */
interface SqlStore {
    fun execute(sql: String, args: List<Any?> = emptyList())
    fun <T> query(sql: String, args: List<Any?> = emptyList(), map: (SqlRow) -> T): List<T>
    fun <T> transaction(block: () -> T): T
}

interface SqlRow {
    fun getString(index: Int): String?
    fun getLong(index: Int): Long
}

fun SqlStore.queryLong(sql: String, args: List<Any?> = emptyList()): Long? =
    query(sql, args) { it.getLong(0) }.firstOrNull()
