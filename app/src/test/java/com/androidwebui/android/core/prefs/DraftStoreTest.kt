package com.androidwebui.android.core.prefs

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DraftStoreTest {
    private val storage = InMemoryStorage()
    private val drafts = DraftStore(storage)

    @Test
    fun draftsAreKeptPerChatAndSurviveANewInstance() {
        drafts.put("a", "hello")
        drafts.put(null, "new chat text")

        val reopened = DraftStore(storage)
        assertEquals("hello", reopened.get("a"))
        assertEquals("new chat text", reopened.get(null))
        assertEquals("", reopened.get("b"))
    }

    @Test
    fun blankDraftIsRemovedNotStored() {
        drafts.put("a", "x")
        drafts.put("a", "   ")
        assertEquals("", drafts.get("a"))
        assertTrue(storage.values.isEmpty())
    }

    @Test
    fun clearRemovesTheDraft() {
        drafts.put("a", "x")
        drafts.clear("a")
        assertEquals("", drafts.get("a"))
    }
}
