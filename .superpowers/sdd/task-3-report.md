# Task 3 — Android Keystore Secret Store Report

## Implementation

- Added the `AiSecretStore` boundary with `put`, `get`, `maskedSuffix`, `remove`, and `clearAll`; it has no Room dependency.
- Added `AndroidKeystoreAiSecretStore`, backed by the private `habit_ai_secrets.xml` preferences file and an Android Keystore AES key under the exact alias `habit_ai_api_key_v1`.
- Every write uses `AES/GCM/NoPadding` with a newly generated and verified 12-byte IV. The stored value is Base64 of `iv + ciphertext` (including the GCM authentication tag).
- Blank API keys are rejected before encryption or persistence. Replacement is atomic at the preferences-entry level; removal and `clearAll` commit synchronously.
- Added `AppContainer.aiSecretStore` as the sole application-level provider for later network and UI consumers.
- Explicitly excluded `habit_ai_secrets.xml` in legacy full-backup rules and in both Android 12+ cloud-backup and device-transfer sections, while retaining every existing whole-domain exclusion.

## TDD Evidence

- RED: added instrumentation coverage for round-trip/masked suffix, replacement, deletion, `clearAll`, blank-key rejection, non-plaintext persistence, fresh 12-byte IVs, authenticated-ciphertext corruption, malformed Base64, and explicit backup exclusions before production code.
- The first valid directed run reached `compileDebugAndroidTestKotlin` and failed because `AiSecretStore` and `AndroidKeystoreAiSecretStore` did not exist. This was the expected feature-missing failure.
- GREEN: after the minimum interface, Keystore implementation, DI registration, and XML exclusions were added, the directed device suite passed all 9 selected tests (8 secret-store tests plus `ManifestPolicyTest`) on `Small_Phone_API_35`.

## Commands and Results

- `gradle.bat connectedDebugAndroidTest '-Pandroid.testInstrumentationRunnerArguments.class=com.habit.app.data.ai.AndroidKeystoreAiSecretStoreTest'` — RED as expected at `compileDebugAndroidTestKotlin`: missing secret-store symbols.
- `gradle.bat connectedDebugAndroidTest '-Pandroid.testInstrumentationRunnerArguments.class=com.habit.app.data.ai.AndroidKeystoreAiSecretStoreTest,com.habit.app.ManifestPolicyTest'` — PASS, 9 tests, 0 failures/errors on `Small_Phone_API_35`.
- `gradle.bat testDebugUnitTest` — PASS, 115 tests, 0 failures.
- `git diff --check` — PASS.

The Gradle commands used the supplied Gradle 9.5.0 executable, Android SDK, and shared Gradle cache paths.

## Security Self-review

- Confirmed the Keystore alias, provider, transformation, IV length, payload layout, GCM tag length, and randomized-encryption requirement are exact and centralized constants.
- `AEADBadTagException`, permanently invalidated/otherwise unusable keys, and other cryptographic failures are covered by the `GeneralSecurityException` read path and return `null`. Invalid Base64, undersized payloads, missing aliases, and non-string preference values also return `null`.
- Neither production code nor the secure-store boundary logs API keys or ciphertext. Storage failures use a fixed generic message; validation messages never interpolate caller data.
- A repository/schema scan found no API-key, secret, credential, or token fields in Room source or exported schemas. The store depends only on `Context`, `SharedPreferences`, Android Keystore, and cryptography APIs.
- The preference filename has explicit exclusions in both Android backup rule formats in addition to the existing whole-domain local-only policy.

## Scope

- No network or UI consumer was added in this task.
- No task-external behavior or Room schema was changed.
