package com.androidwebui.android.core.security

import com.androidwebui.android.core.prefs.InMemoryStorage
import javax.crypto.KeyGenerator
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SecretStoreTest {
    private val key = KeyGenerator.getInstance("AES").apply { init(256) }.generateKey()
    private val storage = InMemoryStorage()
    private val store = EncryptedSecretStore({ key }, storage)

    @Test
    fun roundTripsAndReturnsKeystoreReference() {
        val ref = store.put("sk-secret")
        assertTrue(SecretStore.isReference(ref))
        assertEquals("sk-secret", store.get(ref))
    }

    @Test
    fun plaintextIsNeverStored() {
        store.put("sk-secret")
        assertFalse(storage.values.values.any { it.contains("sk-secret") })
    }

    @Test
    fun sameSecretGetsDistinctReferences() {
        assertNotEquals(store.put("x"), store.put("x"))
    }

    @Test
    fun deleteRemovesSecretAndUnknownReferencesReturnNull() {
        val ref = store.put("x")
        store.delete(ref)
        assertNull(store.get(ref))
        assertNull(store.get("keystore:missing"))
        assertNull(store.get("sk-plain-value"))
    }
}
