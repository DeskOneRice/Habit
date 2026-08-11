package com.habit.app.ui.diet

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.habit.app.domain.model.DietCategoryScope
import com.habit.app.domain.model.AiCalorieEstimate
import com.habit.app.domain.model.DietRecordType
import com.habit.app.domain.model.MealRecord
import com.habit.app.domain.repository.DietCategoryRepository
import com.habit.app.domain.repository.DietRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class DietRecordDetailUiState(
    val loading: Boolean = true,
    val record: MealRecord? = null,
    val categoryName: String = "",
    val aiEvidence: AiCalorieEstimate? = null,
    val notFound: Boolean = false,
)

@OptIn(ExperimentalCoroutinesApi::class)
class DietRecordDetailViewModel(
    private val recordId: Long,
    private val repository: DietRepository,
    categoryRepository: DietCategoryRepository,
) : ViewModel() {
    val state: StateFlow<DietRecordDetailUiState> = repository.observeRecord(recordId)
        .flatMapLatest { record ->
            if (record == null) {
                flowOf(DietRecordDetailUiState(loading = false, notFound = true))
            } else {
                val scope = if (record.recordType == DietRecordType.BEVERAGE) {
                    DietCategoryScope.BEVERAGE
                } else {
                    DietCategoryScope.MEAL
                }
                categoryRepository.observeAll(scope).map { categories ->
                    DietRecordDetailUiState(
                        loading = false,
                        record = record,
                        categoryName = categories.firstOrNull { it.id == record.dietCategoryId }?.name.orEmpty(),
                        aiEvidence = record.aiCalorieEstimate,
                    )
                }
            }
        }
        .stateIn(
            viewModelScope,
            SharingStarted.Eagerly,
            DietRecordDetailUiState(),
        )

    fun delete(onDeleted: () -> Unit) {
        viewModelScope.launch {
            repository.delete(recordId)
            onDeleted()
        }
    }
}
