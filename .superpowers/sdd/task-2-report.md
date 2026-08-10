# Task 2 — Room 5 AI Storage Report

## Implementation

- Upgraded `HabitDatabase` to schema version 5 and exported `app/schemas/com.habit.app.data.local.HabitDatabase/5.json`.
- Added the required Room entities and constraints: model configs (unique `externalId`), nullable model feature bindings (`SET_NULL`), weekly reports (unique `startEpochDay`), and one calorie estimate per meal (`mealRecordId` primary key with `CASCADE`).
- Added `AiDao`, including the specified observable queries, binding/estimate upserts, model CRUD helpers, and weekly report operations.
- Added domain/entity JSON mappers for weekly report lists/coverage and calorie-estimate items.
- Added `RoomAiModelRepository` and `RoomAiWeeklyReportRepository`; model deletion is wrapped in `database.withTransaction`, and saving a report for an existing week updates that row rather than creating a duplicate.
- Registered `MIGRATION_4_5` and AI repositories in `AppContainer`.

## Migration Safety

`MIGRATION_4_5` consists only of `CREATE TABLE IF NOT EXISTS` and required `CREATE INDEX IF NOT EXISTS` statements for the four new AI tables. It does not drop, delete, alter, or issue a data-update statement against any pre-v5 table. The only `ON UPDATE NO ACTION` text is a foreign-key declaration, not an update operation.

The new migration instrumentation test creates a v4 category, habit, check-in, meal, and meal photo, migrates to v5, and verifies all five old-table counts stay at one plus empty new AI tables.

## TDD

- RED: added migration and repository behavior instrumentation tests before production code. The directed run failed in `compileDebugAndroidTestKotlin` because `MIGRATION_4_5`, AI entities/DAO, and repositories did not yet exist.
- GREEN: after implementation, the directed suite passed all 6 selected tests. A later full JVM build found a Kotlin generic-list JVM signature clash in a newly added internal mapper; root cause was type erasure of two `List<T>` extension names. Renaming the calorie helper resolved it, then both verification suites passed.

## Commands and Results

- `connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.habit.app.data.local.HabitDatabaseMigrationTest` — RED as expected: missing v5 migration/AI storage symbols.
- `connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.habit.app.data.local.HabitDatabaseMigrationTest,com.habit.app.data.repository.RoomAiRepositoryTest` — PASS, 6 tests on `Small_Phone_API_35`.
- `testDebugUnitTest` — PASS.
- `git diff --check` — PASS (no whitespace errors).

## Files

- Modified: Room entities/database/mappers, `AppContainer`, and migration instrumentation coverage.
- Added: `AiDao`, both AI repositories, AI repository instrumentation coverage, and Room schema 5 export.

## Self-review and Follow-up

- Confirmed exact entity columns, unique indexes, FK actions, migration registration, model-binding preservation, weekly replacement, and meal-estimate cascade through schema and instrumentation coverage.
- No API keys are stored in Room. Backup import/export and application UI/service consumers remain intentionally out of this task’s scope.
