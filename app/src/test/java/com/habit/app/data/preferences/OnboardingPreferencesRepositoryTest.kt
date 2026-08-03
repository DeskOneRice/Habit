package com.habit.app.data.preferences

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import java.io.File
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class OnboardingPreferencesRepositoryTest {
    @get:Rule
    val temporaryFolder = TemporaryFolder()

    @Test
    fun completionIsPersisted() = runTest {
        val dataStore = PreferenceDataStoreFactory.create(
            scope = backgroundScope,
            produceFile = { File(temporaryFolder.root, "onboarding.preferences_pb") },
        )
        val repository = OnboardingPreferencesRepository(dataStore)

        assertFalse(repository.completed.first())
        repository.complete()
        assertTrue(repository.completed.first())
    }
}
