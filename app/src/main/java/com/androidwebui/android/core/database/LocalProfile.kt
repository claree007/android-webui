package com.androidwebui.android.core.database

import java.util.UUID

data class LocalProfile(val id: String, val name: String)

/** The single local profile (**User**). There is no registration or login. */
object LocalProfileRepository {
    const val DEFAULT_NAME = "User"

    fun ensure(store: SqlStore, nowSeconds: Long = System.currentTimeMillis() / 1000): LocalProfile {
        existing(store)?.let { return it }
        val id = UUID.randomUUID().toString()
        store.execute(
            """INSERT INTO "user" (id, name, email, role, profile_image_url, created_at, updated_at, last_active_at, settings)
               VALUES (?, ?, ?, 'admin', '/user.png', ?, ?, ?, '{}')""",
            listOf(id, DEFAULT_NAME, "user@localhost", nowSeconds, nowSeconds, nowSeconds),
        )
        return LocalProfile(id, DEFAULT_NAME)
    }

    private fun existing(store: SqlStore): LocalProfile? =
        store.query("""SELECT id, name FROM "user" ORDER BY created_at LIMIT 1""") {
            LocalProfile(it.getString(0)!!, it.getString(1)!!)
        }.firstOrNull()
}
