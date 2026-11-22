package com.androidwebui.android.core.database

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper

/** The app database: a `webui.db` layout compatible with the Open WebUI web app on plain SQLite (D1, D4). */
class AppDatabase(private val context: Context) :
    SQLiteOpenHelper(context, FILE_NAME, null, VERSION) {

    override fun onConfigure(db: SQLiteDatabase) {
        db.setForeignKeyConstraintsEnabled(true)
        db.enableWriteAheadLogging()
    }

    override fun onCreate(db: SQLiteDatabase) {
        val script = context.assets.open(SchemaInstaller.ASSET_NAME).bufferedReader().use { it.readText() }
        SchemaInstaller.install(AndroidSqlStore(db), script)
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        error("No migrations exist yet (from $oldVersion to $newVersion)")
    }

    fun store(): SqlStore = AndroidSqlStore(writableDatabase)

    companion object {
        const val FILE_NAME = "webui.db"
        const val VERSION = 1
    }
}
