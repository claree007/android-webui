package com.androidwebui.android.core.database

import android.database.sqlite.SQLiteDatabase

class AndroidSqlStore(private val db: SQLiteDatabase) : SqlStore {
    override fun execute(sql: String, args: List<Any?>) {
        if (args.isEmpty()) db.execSQL(sql) else db.execSQL(sql, args.toTypedArray())
    }

    override fun <T> query(sql: String, args: List<Any?>, map: (SqlRow) -> T): List<T> {
        val cursor = db.rawQuery(sql, args.map { it?.toString() }.toTypedArray())
        try {
            val row = object : SqlRow {
                override fun getString(index: Int): String? = if (cursor.isNull(index)) null else cursor.getString(index)
                override fun getLong(index: Int): Long = cursor.getLong(index)
            }
            val out = ArrayList<T>()
            while (cursor.moveToNext()) out += map(row)
            return out
        } finally {
            cursor.close()
        }
    }

    override fun <T> transaction(block: () -> T): T {
        db.beginTransaction()
        try {
            val result = block()
            db.setTransactionSuccessful()
            return result
        } finally {
            db.endTransaction()
        }
    }

    companion object {
        /** Opens a foreign database file strictly read-only, for inspection and merge import (D2). */
        fun openReadOnly(path: String): AndroidSqlStore =
            AndroidSqlStore(SQLiteDatabase.openDatabase(path, null, SQLiteDatabase.OPEN_READONLY))
    }
}
