# Task 7 Report — Weekly Report Screens, History, and Workbench Card

## Status

Implemented the complete AI weekly report UI flow for Habit 0.6.0: report generation confirmation, loading overlays, ordered preview, save and same-week replacement confirmation, history, read-only detail, navigation, and a workbench summary card.

## TDD Evidence

- Workbench RED: `WorkbenchViewModelTest` initially failed compilation because `buildWeeklyInsightSummary` and `WeeklyInsightStatus` did not exist.
- Workbench GREEN: added `NEEDS_MODEL`, `READY_TO_GENERATE`, and `SAVED` summaries with a previous Monday–Sunday range calculated at the Beijing date boundary.
- Compose RED: `compileDebugAndroidTestKotlin` initially failed only on missing `AiWeeklyReportScreen`, `AiReportHistoryScreen`, and `AiWeeklyReportDetailScreen` symbols after test-fixture errors were removed.
- Device RED/GREEN: initial device failures showed off-screen `LazyColumn` nodes were not composed; the document was made one ordered scrollable layout and the test switched from clipped semantics bounds to layout coordinates.
- Navigation regression RED/GREEN: a test proved that automatic navigation from `Saved` would fire after save and could re-fire after Back; the automatic side effect was removed so saving remains on the root report page while workbench/history use explicit detail routes.

## Implementation

- Added the report root screen with a standard lightweight top bar and history action.
- Added explicit confirmation before generation and before same-week replacement.
- Added generating/saving overlays and safe retry/configuration error states.
- Rendered the approved report order exactly:
  1. period, title, overview
  2. local metrics
  3. habit analysis
  4. diet analysis
  5. correlation finding
  6. exactly three numbered suggestions
  7. cautions, coverage, and disclaimer
- Used white cards, the existing low-saturation sky-blue theme accents, near-white background, and 48 dp minimum primary controls; no chat-bubble treatment was introduced.
- Added history sorted by week descending and a read-only detail screen without generate/save actions.
- Replaced the `AiReports` placeholder and added root/history/detail routes with Back behavior.
- Added `WeeklyInsightSummary` to the workbench with `NEEDS_MODEL`, `READY_TO_GENERATE`, and `SAVED` states.
- The saved workbench state shows title, week range, overview, and Beijing generation time.
- Kept the existing recent-seven-days horizontal timeline and all prior workbench content.
- `WorkbenchViewModel` consumes only `Flow<WeeklyInsightSummary>`; model binding/repository combination is owned by `AppContainer`. The workbench does not build prompts, read keys, or call the AI client.

## Verification

- `:app:testDebugUnitTest --tests com.habit.app.ui.workbench.WorkbenchViewModelTest` — PASS.
- `:app:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.habit.app.ui.ai.AiWeeklyReportFlowTest,com.habit.app.ui.AdaptivePrimaryActionsTest` — PASS, 9/9 on `Small_Phone_API_35`. This includes successful replacement with one retained row, stable ID/created time, advanced updated time, failed replacement preserving the old row, saved Back-loop prevention, Beijing generation time, and 48 dp adaptive actions.
- `:app:testDebugUnitTest` — PASS.
- `git diff --check` — PASS.

## Concerns

- AI response generation/parsing/persistence remains delegated to the Task 6 ViewModel and repository; this task adds no new network or secret-handling path.
- History and detail identify the unique weekly report by `startEpochDay`, matching the repository's one-report-per-week replacement policy.
- Independent review found no Critical issues. Its two Important findings (saved-card generation time and missing successful replacement UI coverage) and one Minor finding (duplicated suggestion numbering) were fixed and covered before final verification.
