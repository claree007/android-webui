package com.androidwebui.android.core.security

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import com.androidwebui.android.core.prefs.KeyValueStorage
import java.security.KeyStore
import java.util.Base64
import java.util.UUID
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * Credential storage (D5). The database only ever holds the returned `keystore:<id>`
 * reference. Secrets must never be logged.
 */
interface SecretStore {
    fun put(secret: String): String
    fun get(reference: String): String?
    fun delete(reference: String)

    companion object {
        const val REFERENCE_PREFIX = "keystore:"
        fun isReference(value: String) = value.startsWith(REFERENCE_PREFIX)
    }
}

/** AES-GCM encryption with a key from [keyProvider]; ciphertext lives in [storage]. */
class EncryptedSecretStore(
    private val keyProvider: () -> SecretKey,
    private val storage: KeyValueStorage,
) : SecretStore {

    override fun put(secret: String): String {
        val id = UUID.randomUUID().toString()
        val cipher = Cipher.getInstance(TRANSFORMATION).apply { init(Cipher.ENCRYPT_MODE, keyProvider()) }
        val payload = cipher.iv + cipher.doFinal(secret.toByteArray(Charsets.UTF_8))
        storage.putString(storageKey(id), Base64.getEncoder().encodeToString(payload))
        return SecretStore.REFERENCE_PREFIX + id
    }

    override fun get(reference: String): String? {
        val id = idOf(reference) ?: return null
        val payload = storage.getString(storageKey(id))?.let { Base64.getDecoder().decode(it) } ?: return null
        val cipher = Cipher.getInstance(TRANSFORMATION).apply {
            init(Cipher.DECRYPT_MODE, keyProvider(), GCMParameterSpec(TAG_BITS, payload, 0, IV_BYTES))
        }
        return String(cipher.doFinal(payload, IV_BYTES, payload.size - IV_BYTES), Charsets.UTF_8)
    }

    override fun delete(reference: String) {
        idOf(reference)?.let { storage.remove(storageKey(it)) }
    }

    private fun idOf(reference: String): String? =
        if (SecretStore.isReference(reference)) reference.removePrefix(SecretStore.REFERENCE_PREFIX) else null

    private fun storageKey(id: String) = "secret.$id"

    private companion object {
        const val TRANSFORMATION = "AES/GCM/NoPadding"
        const val IV_BYTES = 12
        const val TAG_BITS = 128
    }
}

/** Non-exportable AES key held by the Android Keystore. */
object AndroidKeystoreKey {
    private const val PROVIDER = "AndroidKeyStore"
    private const val ALIAS = "android_webui_secret_key"

    fun get(): SecretKey {
        val keyStore = KeyStore.getInstance(PROVIDER).apply { load(null) }
        (keyStore.getKey(ALIAS, null) as? SecretKey)?.let { return it }
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, PROVIDER)
        generator.init(
            KeyGenParameterSpec.Builder(ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build(),
        )
        return generator.generateKey()
    }
}
