package com.habit.app.data.ai

import android.content.Context
import android.util.Base64
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import java.nio.charset.StandardCharsets
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import kotlin.coroutines.CoroutineContext
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
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
        val apiKey = "sk-secret-value"
        store.put("model-uuid", apiKey)

        assertEquals(apiKey, store.get("model-uuid"))
        assertEquals("alue", store.maskedSuffix("model-uuid"))
        val raw = preferences().all.toString()
        assertFalse(raw.contains(apiKey))
        assertSecretFilesDoNotContain(apiKey)
    }

    @Test
    fun replacingAKeyUsesFreshCiphertext() = runTest {
        val firstApiKey = "sk-first-value"
        val replacementApiKey = "sk-replacement-value"
        store.put("model-uuid", firstApiKey)
        val firstCiphertext = storedValue("model-uuid")
        assertSecretFilesDoNotContain(firstApiKey)

        store.put("model-uuid", replacementApiKey)

        assertEquals(replacementApiKey, store.get("model-uuid"))
        assertNotEquals(firstCiphertext, storedValue("model-uuid"))
        assertSecretFilesDoNotContain(firstApiKey, replacementApiKey)
    }

    @Test
    fun repeatedPlaintextUsesFreshTwelveByteIv() = runTest {
        val apiKey = "same-secret"
        store.put("model-uuid", apiKey)
        val first = Base64.decode(storedValue("model-uuid"), Base64.NO_WRAP)

        store.put("model-uuid", apiKey)
        val second = Base64.decode(storedValue("model-uuid"), Base64.NO_WRAP)

        val expectedPayloadSize = IV_SIZE_BYTES +
            apiKey.toByteArray(StandardCharsets.UTF_8).size +
            GCM_TAG_SIZE_BYTES
        assertEquals(expectedPayloadSize, first.size)
        assertEquals(expectedPayloadSize, second.size)
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

    @Test
    fun decodablePayloadShorterThanIvAndTagReturnsNull() = runTest {
        val shortPayload = ByteArray(IV_SIZE_BYTES + GCM_TAG_SIZE_BYTES - 1)
        preferences().edit()
            .putString("model-uuid", Base64.encodeToString(shortPayload, Base64.NO_WRAP))
            .commit()

        assertNull(store.get("model-uuid"))
        assertNull(store.maskedSuffix("model-uuid"))
    }

    @Test
    fun everySuspendApiDispatchesWorkToTheInjectedDispatcher() = runTest {
        val dispatcher = RecordingDispatcher()
        val subject = AndroidKeystoreAiSecretStore(context, dispatcher)

        assertDispatches(dispatcher) { subject.put("model-uuid", "dispatcher-secret") }
        assertDispatches(dispatcher) { subject.get("model-uuid") }
        assertDispatches(dispatcher) { subject.maskedSuffix("model-uuid") }
        assertDispatches(dispatcher) { subject.remove("model-uuid") }
        assertDispatches(dispatcher) { subject.clearAll() }
    }

    @Test
    fun concurrentMutationsAreSerialized() = runTest {
        val activeCommits = AtomicInteger()
        val maxActiveCommits = AtomicInteger()
        val subject = AndroidKeystoreAiSecretStore(
            context = context,
            dispatcher = Dispatchers.IO,
            preferenceCommit = { editor ->
                val active = activeCommits.incrementAndGet()
                maxActiveCommits.updateAndGet { current -> maxOf(current, active) }
                try {
                    Thread.sleep(COMMIT_OVERLAP_WINDOW_MILLIS)
                    editor.commit()
                } finally {
                    activeCommits.decrementAndGet()
                }
            },
        )

        coroutineScope {
            (0 until CONCURRENT_WRITES).map { index ->
                async { subject.put("model-$index", "secret-$index") }
            }.awaitAll()
        }

        assertEquals(1, maxActiveCommits.get())
    }

    @Test
    fun getWaitsForFailedMutationRollbackAndReadsOnlyThePreviousValue() = runTest {
        store.put("model-uuid", "existing-secret")
        val candidatePublished = CountDownLatch(1)
        val releaseFailedCommit = CountDownLatch(1)
        val commitCalls = AtomicInteger()
        val dispatcher = ReadAttemptTrackingDispatcher()
        val subject = AndroidKeystoreAiSecretStore(
            context = context,
            dispatcher = dispatcher,
            preferenceCommit = { editor ->
                val committed = editor.commit()
                if (commitCalls.incrementAndGet() == 1) {
                    candidatePublished.countDown()
                    releaseFailedCommit.await(COMMIT_BLOCK_TIMEOUT_SECONDS, TimeUnit.SECONDS)
                    false
                } else {
                    committed
                }
            },
        )

        val mutation = async(start = CoroutineStart.UNDISPATCHED) {
            captureFailure { subject.put("model-uuid", "replacement-secret") }
        }
        assertTrue(withContext(Dispatchers.IO) {
            candidatePublished.await(COMMIT_BLOCK_TIMEOUT_SECONDS, TimeUnit.SECONDS)
        })
        val read = async(start = CoroutineStart.UNDISPATCHED) {
            subject.get("model-uuid")
        }

        assertTrue(withContext(Dispatchers.IO) {
            dispatcher.readAttemptReturned.await(COMMIT_BLOCK_TIMEOUT_SECONDS, TimeUnit.SECONDS)
        })
        val readReturnedBeforeRollback = read.isCompleted
        releaseFailedCommit.countDown()
        val failure = mutation.await()
        val value = read.await()

        assertFalse(readReturnedBeforeRollback)
        assertEquals(STORAGE_FAILURE_MESSAGE, failure.message)
        assertEquals("existing-secret", value)
    }

    @Test
    fun failedMutationsRestoreTheCompleteVisibleSnapshot() = runTest {
        store.put("first-model", "first-existing-secret")
        store.put("second-model", "second-existing-secret")
        val snapshot = preferences().all.toMap()
        val subject = AndroidKeystoreAiSecretStore(
            context = context,
            dispatcher = Dispatchers.Unconfined,
            preferenceCommit = { editor ->
                editor.commit()
                false
            },
        )

        val failures = listOf<suspend () -> Unit>(
            { subject.put("first-model", "replacement-secret") },
            { subject.remove("first-model") },
            { subject.clearAll() },
        ).map { mutation ->
            captureFailure(mutation).also {
                assertEquals(STORAGE_FAILURE_MESSAGE, it.message)
                assertEquals(snapshot, preferences().all)
                assertEquals("first-existing-secret", store.get("first-model"))
                assertEquals("second-existing-secret", store.get("second-model"))
            }
        }

        assertTrue(failures.all { it is IllegalStateException })
    }

    @Test
    fun commitExceptionAfterVisibleMutationRestoresSnapshotAndIsSanitized() = runTest {
        store.put("model-uuid", "existing-secret")
        val snapshot = preferences().all.toMap()
        val subject = AndroidKeystoreAiSecretStore(
            context = context,
            dispatcher = Dispatchers.Unconfined,
            preferenceCommit = { editor ->
                editor.commit()
                throw Exception("Injected persistence failure.")
            },
        )

        val failure = captureFailure {
            subject.put("model-uuid", "replacement-secret")
        }

        assertTrue(failure is IllegalStateException)
        assertEquals(STORAGE_FAILURE_MESSAGE, failure.message)
        assertEquals(snapshot, preferences().all)
        assertEquals("existing-secret", store.get("model-uuid"))
    }

    @Test
    fun rollbackCommitFalseUsesApplyFallbackToRestoreVisibleSnapshot() = runTest {
        store.put("model-uuid", "existing-secret")
        val snapshot = preferences().all.toMap()
        val commitCalls = AtomicInteger()
        val applyCalls = AtomicInteger()
        val subject = AndroidKeystoreAiSecretStore(
            context = context,
            dispatcher = Dispatchers.Unconfined,
            preferenceCommit = { editor ->
                if (commitCalls.incrementAndGet() == 1) editor.commit()
                false
            },
            preferenceApply = { editor ->
                applyCalls.incrementAndGet()
                editor.apply()
            },
        )

        val failure = captureFailure {
            subject.put("model-uuid", "replacement-secret")
        }

        assertEquals(STORAGE_FAILURE_MESSAGE, failure.message)
        assertEquals(2, commitCalls.get())
        assertEquals(1, applyCalls.get())
        assertEquals(snapshot, preferences().all)
        assertEquals("existing-secret", store.get("model-uuid"))
    }

    @Test
    fun rollbackCommitExceptionUsesApplyFallbackToRestoreVisibleSnapshot() = runTest {
        store.put("model-uuid", "existing-secret")
        val snapshot = preferences().all.toMap()
        val commitCalls = AtomicInteger()
        val applyCalls = AtomicInteger()
        val subject = AndroidKeystoreAiSecretStore(
            context = context,
            dispatcher = Dispatchers.Unconfined,
            preferenceCommit = { editor ->
                if (commitCalls.incrementAndGet() == 1) {
                    editor.commit()
                    false
                } else {
                    throw Exception("Injected rollback failure.")
                }
            },
            preferenceApply = { editor ->
                applyCalls.incrementAndGet()
                editor.apply()
            },
        )

        val failure = captureFailure {
            subject.put("model-uuid", "replacement-secret")
        }

        assertEquals(STORAGE_FAILURE_MESSAGE, failure.message)
        assertEquals(2, commitCalls.get())
        assertEquals(1, applyCalls.get())
        assertEquals(snapshot, preferences().all)
        assertEquals("existing-secret", store.get("model-uuid"))
    }

    @Test
    fun failedApplyFallbackReportsUncertainVisibleStateWithoutSensitiveText() = runTest {
        store.put("model-uuid", "existing-secret")
        val snapshot = preferences().all.toMap()
        val commitCalls = AtomicInteger()
        val subject = AndroidKeystoreAiSecretStore(
            context = context,
            dispatcher = Dispatchers.Unconfined,
            preferenceCommit = { editor ->
                if (commitCalls.incrementAndGet() == 1) editor.commit()
                false
            },
            preferenceApply = {
                throw Exception("Injected apply failure.")
            },
        )

        val failure = captureFailure {
            subject.put("model-uuid", "replacement-secret")
        }

        assertTrue(failure is IllegalStateException)
        assertEquals(STORAGE_RECOVERY_FAILURE_MESSAGE, failure.message)
        assertFalse(snapshot == preferences().all)
    }

    private fun preferences() = context.getSharedPreferences(FILE, Context.MODE_PRIVATE)

    private fun storedValue(externalId: String): String =
        requireNotNull(preferences().getString(externalId, null))

    private fun assertSecretFilesDoNotContain(vararg plaintexts: String) {
        val sharedPreferencesDirectory = File(context.applicationInfo.dataDir, "shared_prefs")
        val secretFiles = sharedPreferencesDirectory.listFiles()
            ?.filter { it.isFile && it.name.startsWith(FILE) }
            .orEmpty()
        assertTrue(secretFiles.any { it.name == "$FILE.xml" })

        val forbiddenBytes = plaintexts.map { it.toByteArray(StandardCharsets.UTF_8) }
        secretFiles.forEach { file ->
            val bytes = file.readBytes()
            forbiddenBytes.forEach { plaintext ->
                assertFalse(bytes.containsSubsequence(plaintext))
            }
        }
    }

    private suspend fun assertDispatches(
        dispatcher: RecordingDispatcher,
        block: suspend () -> Any?,
    ) {
        val previousDispatchCount = dispatcher.dispatchCount.get()
        block()
        assertTrue(dispatcher.dispatchCount.get() > previousDispatchCount)
    }

    private suspend fun captureFailure(block: suspend () -> Unit): Throwable =
        runCatching { block() }.exceptionOrNull() ?: error("Expected secure storage failure.")

    private companion object {
        const val FILE = "habit_ai_secrets"
        const val IV_SIZE_BYTES = 12
        const val GCM_TAG_SIZE_BYTES = 16
        const val CONCURRENT_WRITES = 6
        const val COMMIT_OVERLAP_WINDOW_MILLIS = 30L
        const val COMMIT_BLOCK_TIMEOUT_SECONDS = 5L
        const val STORAGE_FAILURE_MESSAGE = "Secure API key storage failed."
        const val STORAGE_RECOVERY_FAILURE_MESSAGE = "Secure API key state recovery failed."
    }
}

private class RecordingDispatcher : CoroutineDispatcher() {
    val dispatchCount = AtomicInteger()

    override fun dispatch(context: CoroutineContext, block: Runnable) {
        dispatchCount.incrementAndGet()
        Dispatchers.IO.dispatch(context, block)
    }
}

private class ReadAttemptTrackingDispatcher : CoroutineDispatcher() {
    private val dispatchCount = AtomicInteger()
    val readAttemptReturned = CountDownLatch(1)

    override fun dispatch(context: CoroutineContext, block: Runnable) {
        val dispatchNumber = dispatchCount.incrementAndGet()
        Dispatchers.IO.dispatch(context) {
            try {
                block.run()
            } finally {
                if (dispatchNumber == READ_DISPATCH_NUMBER) readAttemptReturned.countDown()
            }
        }
    }

    private companion object {
        const val READ_DISPATCH_NUMBER = 2
    }
}

private fun ByteArray.containsSubsequence(candidate: ByteArray): Boolean {
    if (candidate.isEmpty()) return true
    if (candidate.size > size) return false
    return (0..size - candidate.size).any { start ->
        candidate.indices.all { offset -> this[start + offset] == candidate[offset] }
    }
}
