package com.habit.app.ui.ai

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.habit.app.data.ai.AUTH_MESSAGE
import com.habit.app.data.ai.AiCompletionClient
import com.habit.app.data.ai.AiFailureKind
import com.habit.app.data.ai.AiSecretStore
import com.habit.app.data.ai.AiServiceFailure
import com.habit.app.data.ai.MALFORMED_RESPONSE_MESSAGE
import com.habit.app.data.ai.NOT_FOUND_MESSAGE
import com.habit.app.data.ai.OFFLINE_MESSAGE
import com.habit.app.data.ai.QUOTA_MESSAGE
import com.habit.app.data.ai.SERVER_MESSAGE
import com.habit.app.data.ai.TIMEOUT_MESSAGE
import com.habit.app.domain.ai.WeeklyReportInput
import com.habit.app.domain.ai.WeeklyReportInputBuildResult
import com.habit.app.domain.ai.WeeklyReportInputLoader
import com.habit.app.domain.ai.WeeklyReportParseException
import com.habit.app.domain.ai.WeeklyReportParser
import com.habit.app.domain.ai.WeeklyReportPrompt
import com.habit.app.domain.model.AiFeature
import com.habit.app.domain.model.AiModelConfig
import com.habit.app.domain.model.AiTestStatus
import com.habit.app.domain.model.AiWeeklyReport
import com.habit.app.domain.model.AiWeeklyReportDraft
import com.habit.app.domain.repository.AiModelRepository
import com.habit.app.domain.repository.AiWeeklyReportRepository
import com.habit.app.domain.time.DeviceDateProvider
import java.time.Clock
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

sealed interface AiWeeklyReportFailure {
    data object NoAnalyzableData : AiWeeklyReportFailure
    data object LocalDataUnavailable : AiWeeklyReportFailure
    data object ModelUnavailable : AiWeeklyReportFailure
    data object KeyUnavailable : AiWeeklyReportFailure
    data object InvalidResponse : AiWeeklyReportFailure
    data object ServiceUnavailable : AiWeeklyReportFailure
    data object SaveFailed : AiWeeklyReportFailure
}

sealed interface AiWeeklyReportState {
    data object LoadingLocalData : AiWeeklyReportState
    data class ReadyToGenerate(
        val input: WeeklyReportInput,
        val existingReport: AiWeeklyReport?,
    ) : AiWeeklyReportState
    data class Generating(
        val input: WeeklyReportInput,
        val recoverTo: AiWeeklyReportState,
    ) : AiWeeklyReportState
    data class Preview(
        val input: WeeklyReportInput,
        val draft: AiWeeklyReportDraft,
        val existingReport: AiWeeklyReport?,
    ) : AiWeeklyReportState
    data class Saving(val preview: Preview) : AiWeeklyReportState
    data class Saved(val report: AiWeeklyReport) : AiWeeklyReportState
    data class Error(
        val failure: AiWeeklyReportFailure,
        val message: String,
        val recoverTo: AiWeeklyReportState,
    ) : AiWeeklyReportState
}

data class AiWeeklyReportReplacementRequest(val startEpochDay: Long)

class AiWeeklyReportViewModel internal constructor(
    private val inputLoader: WeeklyReportInputLoader,
    private val modelRepository: AiModelRepository,
    private val reportRepository: AiWeeklyReportRepository,
    private val secretStore: AiSecretStore,
    private val client: AiCompletionClient,
    private val dateProvider: DeviceDateProvider,
    private val clock: Clock = Clock.systemUTC(),
    private val coordinator: AiModelOperationCoordinator = AiModelOperationCoordinator(),
) : ViewModel() {
    private val mutableState = MutableStateFlow<AiWeeklyReportState>(AiWeeklyReportState.LoadingLocalData)
    val state: StateFlow<AiWeeklyReportState> = mutableState.asStateFlow()

    private val mutableReplacementRequests = MutableStateFlow<AiWeeklyReportReplacementRequest?>(null)
    val replacementRequests: StateFlow<AiWeeklyReportReplacementRequest?> = mutableReplacementRequests.asStateFlow()

    private val actionGate = AtomicBoolean(false)
    private var generationJob: Job? = null

    init {
        loadLocalData()
    }

    fun retry() {
        val error = mutableState.value as? AiWeeklyReportState.Error ?: return
        when (error.failure) {
            AiWeeklyReportFailure.NoAnalyzableData,
            AiWeeklyReportFailure.LocalDataUnavailable,
            -> loadLocalData()
            else -> mutableState.value = error.recoverTo
        }
    }

    fun generate(): Job {
        val stable = when (val current = mutableState.value) {
            is AiWeeklyReportState.ReadyToGenerate -> current
            is AiWeeklyReportState.Preview -> current
            else -> return viewModelScope.launch { }
        }
        if (!actionGate.compareAndSet(false, true)) return viewModelScope.launch { }
        mutableReplacementRequests.value = null
        mutableState.value = AiWeeklyReportState.Generating(stable.input(), stable)
        return viewModelScope.launch {
            generationJob = coroutineContext[Job]
            try {
                val bindingId = coordinator.withBindings {
                    modelRepository.observeBindings().first()
                        .firstOrNull { it.feature == AiFeature.WEEKLY_REPORT }
                        ?.modelConfigId
                } ?: throw GenerationFailure(AiWeeklyReportFailure.ModelUnavailable, MODEL_UNAVAILABLE_MESSAGE)
                val completion = coordinator.withModel(bindingId) {
                    val model = modelRepository.observeModel(bindingId).first()
                        ?: throw GenerationFailure(AiWeeklyReportFailure.ModelUnavailable, MODEL_UNAVAILABLE_MESSAGE)
                    coordinator.register(model.id, model.externalId)
                    coordinator.withBindings {
                        val latestBinding = modelRepository.observeBindings().first()
                            .firstOrNull { it.feature == AiFeature.WEEKLY_REPORT }
                            ?.modelConfigId
                        if (latestBinding != model.id || !model.isEligibleTextModel()) {
                            throw GenerationFailure(AiWeeklyReportFailure.ModelUnavailable, MODEL_UNAVAILABLE_MESSAGE)
                        }
                        val apiKey = try {
                            secretStore.get(model.externalId)
                        } catch (_: Exception) {
                            throw GenerationFailure(AiWeeklyReportFailure.KeyUnavailable, KEY_UNAVAILABLE_MESSAGE)
                        }
                        if (apiKey.isNullOrBlank()) {
                            throw GenerationFailure(AiWeeklyReportFailure.KeyUnavailable, KEY_UNAVAILABLE_MESSAGE)
                        }
                        CompletionResult(
                            model = model,
                            rawResponse = client.completeText(
                                model = model,
                                apiKey = apiKey,
                                systemPrompt = WeeklyReportPrompt.systemPrompt,
                                userPrompt = WeeklyReportPrompt.userPrompt(stable.input()),
                            ),
                        )
                    }
                }
                val draft = WeeklyReportParser.parse(
                    rawResponse = completion.rawResponse,
                    input = stable.input(),
                    model = completion.model,
                    generatedAt = clock.millis(),
                )
                mutableState.value = AiWeeklyReportState.Preview(
                    input = stable.input(),
                    draft = draft,
                    existingReport = stable.existingReport(),
                )
            } catch (cancelled: CancellationException) {
                mutableState.value = stable
                throw cancelled
            } catch (failure: GenerationFailure) {
                mutableState.value = AiWeeklyReportState.Error(failure.failure, failure.safeMessage, stable)
            } catch (_: WeeklyReportParseException) {
                mutableState.value = AiWeeklyReportState.Error(
                    AiWeeklyReportFailure.InvalidResponse,
                    MALFORMED_RESPONSE_MESSAGE,
                    stable,
                )
            } catch (failure: AiServiceFailure) {
                mutableState.value = AiWeeklyReportState.Error(
                    AiWeeklyReportFailure.ServiceUnavailable,
                    safeServiceMessage(failure.kind),
                    stable,
                )
            } catch (_: Exception) {
                mutableState.value = AiWeeklyReportState.Error(
                    AiWeeklyReportFailure.ServiceUnavailable,
                    GENERATION_FAILURE_MESSAGE,
                    stable,
                )
            } finally {
                generationJob = null
                actionGate.set(false)
            }
        }.also { generationJob = it }
    }

    fun cancelGeneration() {
        generationJob?.cancel()
    }

    fun save(): Job {
        val preview = mutableState.value as? AiWeeklyReportState.Preview ?: return viewModelScope.launch { }
        return savePreview(preview, allowReplacement = false)
    }

    fun confirmReplacement(): Job {
        val preview = mutableState.value as? AiWeeklyReportState.Preview ?: return viewModelScope.launch { }
        val request = mutableReplacementRequests.value ?: return viewModelScope.launch { }
        if (request.startEpochDay != preview.input.startEpochDay) return viewModelScope.launch { }
        mutableReplacementRequests.value = null
        return savePreview(preview, allowReplacement = true)
    }

    fun dismissReplacement() {
        mutableReplacementRequests.value = null
    }

    private fun loadLocalData(): Job {
        if (actionGate.get()) return viewModelScope.launch { }
        mutableState.value = AiWeeklyReportState.LoadingLocalData
        return viewModelScope.launch {
            try {
                when (val result = inputLoader.build(dateProvider.today())) {
                    WeeklyReportInputBuildResult.NoAnalyzableData -> {
                        mutableState.value = AiWeeklyReportState.Error(
                            AiWeeklyReportFailure.NoAnalyzableData,
                            NO_DATA_MESSAGE,
                            AiWeeklyReportState.LoadingLocalData,
                        )
                    }
                    is WeeklyReportInputBuildResult.Ready -> {
                        mutableState.value = AiWeeklyReportState.ReadyToGenerate(
                            result.input,
                            reportRepository.observeWeek(result.input.startEpochDay).first(),
                        )
                    }
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                mutableState.value = AiWeeklyReportState.Error(
                    AiWeeklyReportFailure.LocalDataUnavailable,
                    LOCAL_DATA_FAILURE_MESSAGE,
                    AiWeeklyReportState.LoadingLocalData,
                )
            }
        }
    }

    private fun savePreview(preview: AiWeeklyReportState.Preview, allowReplacement: Boolean): Job {
        if (!actionGate.compareAndSet(false, true)) return viewModelScope.launch { }
        return viewModelScope.launch {
            try {
                coordinator.withWeeklyReport(preview.input.startEpochDay) {
                    val existing = reportRepository.observeWeek(preview.input.startEpochDay).first()
                    if (existing != null && !allowReplacement) {
                        mutableReplacementRequests.value = AiWeeklyReportReplacementRequest(preview.input.startEpochDay)
                        return@withWeeklyReport
                    }
                    mutableState.value = AiWeeklyReportState.Saving(preview)
                    val now = clock.millis()
                    val report = preview.draft.toReport(existing?.id ?: 0, existing?.createdAt ?: now, now)
                    val id = reportRepository.save(report)
                    mutableState.value = AiWeeklyReportState.Saved(report.copy(id = id))
                }
            } catch (cancelled: CancellationException) {
                mutableState.value = preview
                throw cancelled
            } catch (_: Exception) {
                mutableState.value = AiWeeklyReportState.Error(
                    AiWeeklyReportFailure.SaveFailed,
                    SAVE_FAILURE_MESSAGE,
                    preview,
                )
            } finally {
                actionGate.set(false)
            }
        }
    }
}

private data class CompletionResult(val model: AiModelConfig, val rawResponse: String)

private class GenerationFailure(
    val failure: AiWeeklyReportFailure,
    val safeMessage: String,
) : IllegalStateException()

private fun AiWeeklyReportState.input(): WeeklyReportInput = when (this) {
    is AiWeeklyReportState.ReadyToGenerate -> input
    is AiWeeklyReportState.Preview -> input
    else -> error("State has no report input")
}

private fun AiWeeklyReportState.existingReport(): AiWeeklyReport? = when (this) {
    is AiWeeklyReportState.ReadyToGenerate -> existingReport
    is AiWeeklyReportState.Preview -> existingReport
    else -> null
}

private fun AiModelConfig.isEligibleTextModel(): Boolean {
    if (!enabled || !supportsText) return false
    val textStatus = if (lastTestMessage.startsWith(TEST_STATE_PREFIX)) {
        lastTestMessage.removePrefix(TEST_STATE_PREFIX)
            .split('|')
            .firstOrNull { it.startsWith("text=") }
            ?.substringAfter('=')
            ?.let { runCatching { AiTestStatus.valueOf(it) }.getOrNull() }
            ?: AiTestStatus.UNTESTED
    } else {
        lastTestStatus
    }
    return textStatus == AiTestStatus.PASSED
}

private fun AiWeeklyReportDraft.toReport(id: Long, createdAt: Long, updatedAt: Long) = AiWeeklyReport(
    id = id,
    startEpochDay = startEpochDay,
    endEpochDay = endEpochDay,
    generatedAt = generatedAt,
    modelNameSnapshot = modelNameSnapshot,
    modelIdSnapshot = modelIdSnapshot,
    title = title,
    overview = overview,
    habitAnalysis = habitAnalysis,
    dietAnalysis = dietAnalysis,
    correlationFinding = correlationFinding,
    suggestions = suggestions,
    cautions = cautions,
    coverage = coverage,
    createdAt = createdAt,
    updatedAt = updatedAt,
)

private fun safeServiceMessage(kind: AiFailureKind): String = when (kind) {
    AiFailureKind.AUTH -> AUTH_MESSAGE
    AiFailureKind.QUOTA -> QUOTA_MESSAGE
    AiFailureKind.NOT_FOUND -> NOT_FOUND_MESSAGE
    AiFailureKind.UNSUPPORTED -> TEXT_UNSUPPORTED_MESSAGE
    AiFailureKind.OFFLINE -> OFFLINE_MESSAGE
    AiFailureKind.TIMEOUT -> TIMEOUT_MESSAGE
    AiFailureKind.SERVER -> SERVER_MESSAGE
    AiFailureKind.INVALID_RESPONSE -> MALFORMED_RESPONSE_MESSAGE
}

private const val TEST_STATE_PREFIX = "habit-test-v1|"
private const val NO_DATA_MESSAGE = "上周没有可分析的习惯或饮食数据"
private const val LOCAL_DATA_FAILURE_MESSAGE = "读取上周数据失败，请重试"
private const val MODEL_UNAVAILABLE_MESSAGE = "请先绑定并通过文本能力测试的模型"
private const val KEY_UNAVAILABLE_MESSAGE = "模型 Key 不可用，请重新填写并测试"
private const val TEXT_UNSUPPORTED_MESSAGE = "当前模型不支持此分析请求"
private const val GENERATION_FAILURE_MESSAGE = "周报生成失败，请重试"
private const val SAVE_FAILURE_MESSAGE = "周报保存失败，请重试"
