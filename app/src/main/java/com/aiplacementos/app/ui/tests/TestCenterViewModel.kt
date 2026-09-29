package com.aiplacementos.app.ui.tests

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.aiplacementos.app.data.remote.AssessmentHistoryItemDto
import com.aiplacementos.app.domain.repository.AssessmentRepository
import com.aiplacementos.app.domain.repository.ProfileRepository
import com.aiplacementos.app.domain.repository.ProgressRepository
import com.aiplacementos.app.ui.common.UiState
import com.aiplacementos.app.ui.common.toUserMessage
import kotlin.math.roundToInt
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import retrofit2.HttpException

data class TestCategory(val label: String, val assessmentType: String?)

/** null assessmentType (Full Length) sends no category filter -- today's existing mixed-
 * blueprint behavior. Topic Tests is deliberately not a tile here -- it's only ever started
 * from a specific roadmap topic (PlacementPathTopicDetailScreen's existing Practice button),
 * which already reuses the same practice-blueprint machinery; duplicating it here as a
 * standalone, topic-less "category" would have nothing real to scope it to. */
val TEST_CATEGORIES = listOf(
    TestCategory("DSA", "coding"),
    TestCategory("SQL", "sql"),
    TestCategory("Aptitude", "aptitude"),
    TestCategory("Programming", "coding"),
    TestCategory("Core CS", "technical"),
    TestCategory("Technical", "technical"),
    TestCategory("Full Length", null),
)

data class TestCenterData(
    val averageScorePercent: Int?,
    val testsCompleted: Int,
    val accuracyPercent: Int?,
    val weakAreas: List<String>,
    val recentResults: List<AssessmentHistoryItemDto>,
    val targetRoleId: Int?,
)

class TestCenterViewModel(
    private val assessmentRepository: AssessmentRepository,
    private val progressRepository: ProgressRepository,
    private val profileRepository: ProfileRepository,
) : ViewModel() {
    private val _uiState = MutableStateFlow<UiState<TestCenterData>>(UiState.Loading)
    val uiState: StateFlow<UiState<TestCenterData>> = _uiState.asStateFlow()

    private val _startingCategory = MutableStateFlow<String?>(null)
    val startingCategory: StateFlow<String?> = _startingCategory.asStateFlow()

    private val _startError = MutableStateFlow<String?>(null)
    val startError: StateFlow<String?> = _startError.asStateFlow()

    init {
        load()
    }

    fun load() {
        viewModelScope.launch {
            _uiState.value = UiState.Loading
            try {
                // Three independent sources, fetched concurrently -- same pattern as
                // HomeViewModel.load(), each degrading gracefully instead of blanking the
                // whole dashboard if one call fails.
                val (history, weakAreas, targetRoleId) = coroutineScope {
                    val historyDeferred = async { runCatching { assessmentRepository.getHistory() }.getOrDefault(emptyList()) }
                    val weakAreasDeferred = async {
                        runCatching { progressRepository.getDashboard().topWeaknesses }.getOrDefault(emptyList())
                    }
                    val roleDeferred = async { runCatching { profileRepository.getMyProfile().targetRoleId }.getOrNull() }
                    Triple(historyDeferred.await(), weakAreasDeferred.await(), roleDeferred.await())
                }

                _uiState.value = UiState.Success(
                    TestCenterData(
                        averageScorePercent = history.takeIf { it.isNotEmpty() }
                            ?.let { (it.map(AssessmentHistoryItemDto::overallScore).average() * 100).roundToInt() },
                        testsCompleted = history.size,
                        accuracyPercent = history.takeIf { it.isNotEmpty() }
                            ?.let { (it.map(AssessmentHistoryItemDto::accuracy).average() * 100).roundToInt() },
                        weakAreas = weakAreas,
                        recentResults = history.take(5),
                        targetRoleId = targetRoleId,
                    )
                )
            } catch (e: Exception) {
                _uiState.value = UiState.Error(e.toUserMessage("load your test stats"))
            }
        }
    }

    fun startCategory(category: TestCategory, onBlueprintReady: (blueprintId: Int) -> Unit) {
        val roleId = (_uiState.value as? UiState.Success<TestCenterData>)?.data?.targetRoleId
        if (roleId == null) {
            _startError.value = "Set a target role in your profile to start a test."
            return
        }
        viewModelScope.launch {
            _startError.value = null
            _startingCategory.value = category.label
            try {
                val blueprint = assessmentRepository.createBlueprint(roleId, null, category.assessmentType)
                onBlueprintReady(blueprint.id)
            } catch (e: HttpException) {
                _startError.value = if (e.code() == 422) {
                    "Not enough ${category.label} content for your role yet -- try another category."
                } else {
                    "Couldn't start that test (${e.code()})."
                }
            } catch (e: Exception) {
                _startError.value = e.toUserMessage("start that test")
            } finally {
                _startingCategory.value = null
            }
        }
    }
}
