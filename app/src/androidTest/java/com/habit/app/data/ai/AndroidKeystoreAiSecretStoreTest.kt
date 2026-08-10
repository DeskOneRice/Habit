package com.habit.app.data.ai

import android.content.Context
import android.util.Base64
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AndroidKeystoreAiSecretStoreTest {
    private lateinit var context: Context
    private lateinit var store: AiSecretStore

    @Before
    fun setUp() {
        context = InstrumentationRegistry.getInstrumentation().targetContext
        preferences().edit().clear().commit()
        store = AndroidKeystoreAiSecretStore(context)
    }

    @After
    fun tearDown() {
        preferences().edit().clear().commit()
    }

    @Test
    fun storesCiphertextAndNeverPlaintext() = runTest {
        store.put("model-uuid", "sk-secret-value")

        assertEquals("sk-secret-value", store.get("model-uuid"))
        assertEquals("alue", store.maskedSuffix("model-uuid"))
        val raw = preferences().all.toString()
        assertFalse(raw.contains("sk-secret-value"))
    }

    @Test
    fun replacingAKeyUsesFreshCiphertext() = runTest {
        store.put("model-uuid", "sk-first-value")
        val firstCiphertext = storedValue("model-uuid")

        store.put("model-uuid", "sk-replacement-value")

        assertEquals("sk-replacement-value", store.get("model-uuid"))
        assertNotEquals(firstCiphertext, storedValue("model-uuid"))
    }

    @Test
    fun repeatedPlaintextUsesFreshTwelveByteIv() = runTest {
        store.put("model-uuid", "same-secret")
        val first = Base64.decode(storedValue("model-uuid"), Base64.NO_WRAP)

        store.put("model-uuid", "same-secret")
        val second = Base64.decode(storedValue("model-uuid"), Base64.NO_WRAP)

        assertFalse(first.copyOfRange(0, IV_SIZE_BYTES).contentEquals(second.copyOfRange(0, IV_SIZE_BYTES)))
    }

    @Test
    fun removeDeletesOnlyTheSelectedKey() = runTest {
        store.put("first-model", "first-secret")
        store.put("second-model", "second-secret")

        store.remove("first-model")

        assertNull(store.get("first-model"))
        assertEquals("second-secret", store.get("second-model"))
    }

    @Test
    fun clearAllDeletesEveryKey() = runTest {
        store.put("first-model", "first-secret")
        store.put("second-model", "second-secret")

        store.clearAll()

        assertNull(store.get("first-model"))
        assertNull(store.get("second-model"))
        assertEquals(emptyMap<String, Any>(), preferences().all)
    }

    @Test
    fun blankApiKeyIsRejectedWithoutChangingStoredValue() = runTest {
        store.put("model-uuid", "existing-secret")

        val failure = runCatching { store.put("model-uuid", "   ") }.exceptionOrNull()

        assertTrue(failure is IllegalArgumentException)
        assertEquals("existing-secret", store.get("model-uuid"))
    }

    @Test
    fun corruptedCiphertextReturnsNull() = runTest {
        store.put("model-uuid", "sk-secret-value")
        val corrupted = Base64.decode(storedValue("model-uuid"), Base64.NO_WRAP).apply {
            this[lastIndex] = (this[lastIndex].toInt() xor 1).toByte()
        }
        preferences().edit()
            .putString("model-uuid", Base64.encodeToString(corrupted, Base64.NO_WRAP))
            .commit()

        assertNull(store.get("model-uuid"))
        assertNull(store.maskedSuffix("model-uuid"))
    }

    @Test
    fun malformedCiphertextReturnsNull() = runTest {
        preferences().edit().putString("model-uuid", "not-valid-base64***").commit()

        assertNull(store.get("model-uuid"))
        assertNull(store.maskedSuffix("model-uuid"))
    }

    private fun preferences() = context.getSharedPreferences(FILE, Context.MODE_PRIVATE)

    private fun storedValue(externalId: String): String =
        requireNotNull(preferences().getString(externalId, null))

    private companion object {
        const val FILE = "habit_ai_secrets"
        const val IV_SIZE_BYTES = 12
    }
}
