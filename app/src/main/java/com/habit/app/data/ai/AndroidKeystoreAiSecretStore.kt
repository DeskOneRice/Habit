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
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

class AndroidKeystoreAiSecretStore(
    context: Context,
    private val dispatcher: CoroutineDispatcher = Dispatchers.IO,
    private val preferenceCommit: (SharedPreferences.Editor) -> Boolean = { it.commit() },
    private val preferenceApply: (SharedPreferences.Editor) -> Unit = { it.apply() },
) : AiSecretStore {
    private val preferences: SharedPreferences = context.applicationContext.getSharedPreferences(
        PREFERENCES_FILE,
        Context.MODE_PRIVATE,
    )

    override suspend fun put(externalId: String, apiKey: String) = withContext(dispatcher) {
        require(apiKey.isNotBlank()) { "API key must not be blank." }

        MUTATION_MUTEX.withLock {
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

            commitOrRollback { putString(externalId, encoded) }
        }
    }

    override suspend fun get(externalId: String): String? = withContext(dispatcher) {
        MUTATION_MUTEX.withLock {
            decrypt(externalId)
        }
    }

    override suspend fun maskedSuffix(externalId: String): String? = withContext(dispatcher) {
        MUTATION_MUTEX.withLock {
            decrypt(externalId)?.takeLast(MASKED_SUFFIX_LENGTH)
        }
    }

    override suspend fun remove(externalId: String) = withContext(dispatcher) {
        MUTATION_MUTEX.withLock {
            commitOrRollback { remove(externalId) }
        }
    }

    override suspend fun clearAll() = withContext(dispatcher) {
        MUTATION_MUTEX.withLock {
            commitOrRollback { clear() }
        }
    }

    private fun decrypt(externalId: String): String? {
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

    private inline fun commitOrRollback(edit: SharedPreferences.Editor.() -> Unit) {
        val snapshot = try {
            snapshotPreferences()
        } catch (_: RuntimeException) {
            throw IllegalStateException(STORAGE_FAILURE_MESSAGE)
        }
        val committed = try {
            preferenceCommit(preferences.edit().apply(edit))
        } catch (_: Exception) {
            false
        }
        if (!committed) {
            val message = if (restoreVisibleSnapshot(snapshot)) {
                STORAGE_FAILURE_MESSAGE
            } else {
                STORAGE_RECOVERY_FAILURE_MESSAGE
            }
            throw IllegalStateException(message)
        }
    }

    private fun snapshotPreferences(): Map<String, Any?> = preferences.all.mapValues { (_, value) ->
        if (value is Set<*>) value.filterIsInstance<String>().toSet() else value
    }

    private fun restoreVisibleSnapshot(snapshot: Map<String, Any?>): Boolean {
        val synchronousRestoreSucceeded = try {
            preferenceCommit(snapshotEditor(snapshot))
        } catch (_: Exception) {
            false
        }
        if (!synchronousRestoreSucceeded || !visibleSnapshotMatches(snapshot)) {
            try {
                preferenceApply(snapshotEditor(snapshot))
            } catch (_: Exception) {
                // Verification below determines whether the visible snapshot was restored.
            }
        }
        return visibleSnapshotMatches(snapshot)
    }

    private fun snapshotEditor(snapshot: Map<String, Any?>): SharedPreferences.Editor =
        preferences.edit().clear().also { editor ->
            snapshot.forEach { (key, value) ->
                when (value) {
                    null -> editor.remove(key)
                    is String -> editor.putString(key, value)
                    is Set<*> -> editor.putStringSet(key, value.filterIsInstance<String>().toSet())
                    is Int -> editor.putInt(key, value)
                    is Long -> editor.putLong(key, value)
                    is Float -> editor.putFloat(key, value)
                    is Boolean -> editor.putBoolean(key, value)
                }
            }
        }

    private fun visibleSnapshotMatches(snapshot: Map<String, Any?>): Boolean = try {
        snapshotPreferences() == snapshot
    } catch (_: RuntimeException) {
        false
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
        const val STORAGE_RECOVERY_FAILURE_MESSAGE = "Secure API key state recovery failed."
        val KEY_LOCK = Any()
        val MUTATION_MUTEX = Mutex()
    }
}
