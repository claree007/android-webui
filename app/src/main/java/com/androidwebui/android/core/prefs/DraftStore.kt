package com.androidwebui.android.core.prefs

/**
 * Unsent composer text, kept per chat so rotation, navigation and process death do not lose it.
 * Device-only (D7), never exported. A blank draft is removed rather than stored.
 */
class DraftStore(private val storage: KeyValueStorage) {
    fun get(chatId: String?): String = storage.getString(key(chatId)).orEmpty()

    fun put(chatId: String?, text: String) {
        if (text.isBlank()) storage.remove(key(chatId)) else storage.putString(key(chatId), text)
    }

    fun clear(chatId: String?) = storage.remove(key(chatId))

    /** The draft of a chat that has not been created yet (the New Chat screen). */
    private fun key(chatId: String?) = "draft.${chatId ?: NEW_CHAT}"

    private companion object {
        const val NEW_CHAT = "new"
    }
}
