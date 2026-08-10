package com.habit.app.data.ai

import android.content.Context
import android.content.SharedPreferences
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.nio.charset.StandardCharsets
import java.security.GeneralSecurityException
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

class AndroidKeystoreAiSecretStore(context: Context) : AiSecretStore {
    private val preferences: SharedPreferences = context.applicationContext.getSharedPreferences(
        PREFERENCES_FILE,
        Context.MODE_PRIVATE,
    )

    override suspend fun put(externalId: String, apiKey: String) {
        require(apiKey.isNotBlank()) { "API key must not be blank." }

        val encoded = try {
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.ENCRYPT_MODE, getOrCreateSecretKey())
            val iv = cipher.iv
            check(iv.size == IV_SIZE_BYTES) { STORAGE_FAILURE_MESSAGE }
            val ciphertext = cipher.doFinal(apiKey.toByteArray(StandardCharsets.UTF_8))
            Base64.encodeToString(iv + ciphertext, Base64.NO_WRAP)
        } catch (_: GeneralSecurityException) {
            throw IllegalStateException(STORAGE_FAILURE_MESSAGE)
        }

        commitOrThrow { putString(externalId, encoded) }
    }

    override suspend fun get(externalId: String): String? {
        val encoded = preferences.all[externalId] as? String ?: return null
        return try {
            val payload = Base64.decode(encoded, Base64.NO_WRAP)
            if (payload.size < IV_SIZE_BYTES + GCM_TAG_SIZE_BYTES) return null

            val key = getSecretKey() ?: return null
            val iv = payload.copyOfRange(0, IV_SIZE_BYTES)
            val ciphertext = payload.copyOfRange(IV_SIZE_BYTES, payload.size)
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(GCM_TAG_SIZE_BITS, iv))
            String(cipher.doFinal(ciphertext), StandardCharsets.UTF_8)
        } catch (_: GeneralSecurityException) {
            null
        } catch (_: IllegalArgumentException) {
            null
        }
    }

    override suspend fun maskedSuffix(externalId: String): String? =
        get(externalId)?.takeLast(MASKED_SUFFIX_LENGTH)

    override suspend fun remove(externalId: String) {
        commitOrThrow { remove(externalId) }
    }

    override suspend fun clearAll() {
        commitOrThrow { clear() }
    }

    private fun getOrCreateSecretKey(): SecretKey = synchronized(KEY_LOCK) {
        getSecretKey() ?: KeyGenerator.getInstance(
            KeyProperties.KEY_ALGORITHM_AES,
            KEYSTORE_PROVIDER,
        ).run {
            init(
                KeyGenParameterSpec.Builder(
                    KEY_ALIAS,
                    KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
                )
                    .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                    .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                    .setRandomizedEncryptionRequired(true)
                    .build(),
            )
            generateKey()
        }
    }

    private fun getSecretKey(): SecretKey? {
        val keyStore = KeyStore.getInstance(KEYSTORE_PROVIDER).apply { load(null) }
        return keyStore.getKey(KEY_ALIAS, null) as? SecretKey
    }

    private inline fun commitOrThrow(edit: SharedPreferences.Editor.() -> Unit) {
        val committed = try {
            preferences.edit().apply(edit).commit()
        } catch (_: RuntimeException) {
            false
        }
        check(committed) { STORAGE_FAILURE_MESSAGE }
    }

    private companion object {
        const val KEY_ALIAS = "habit_ai_api_key_v1"
        const val KEYSTORE_PROVIDER = "AndroidKeyStore"
        const val TRANSFORMATION = "AES/GCM/NoPadding"
        const val PREFERENCES_FILE = "habit_ai_secrets"
        const val IV_SIZE_BYTES = 12
        const val GCM_TAG_SIZE_BITS = 128
        const val GCM_TAG_SIZE_BYTES = GCM_TAG_SIZE_BITS / Byte.SIZE_BITS
        const val MASKED_SUFFIX_LENGTH = 4
        const val STORAGE_FAILURE_MESSAGE = "Secure API key storage failed."
        val KEY_LOCK = Any()
    }
}
