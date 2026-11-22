package com.androidwebui.android.core.prefs

import android.annotation.SuppressLint
import android.content.Context

/** Minimal string key-value storage so preference logic can be tested on the JVM. */
interface KeyValueStorage {
    fun getString(key: String): String?
    fun putString(key: String, value: String)
    fun remove(key: String)
}

// KTX's SharedPreferences.edit would add core-ktx only for this; avoided per D8.
@SuppressLint("UseKtx")
class SharedPreferencesStorage(context: Context, name: String) : KeyValueStorage {
    private val prefs = context.getSharedPreferences(name, Context.MODE_PRIVATE)
    override fun getString(key: String): String? = prefs.getString(key, null)
    override fun putString(key: String, value: String) = prefs.edit().putString(key, value).apply()
    override fun remove(key: String) = prefs.edit().remove(key).apply()
}
