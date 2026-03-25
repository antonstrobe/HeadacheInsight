package com.neuron.headacheinsight.feature.reports

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.neuron.headacheinsight.core.designsystem.HeadacheInsightSectionCard
import com.neuron.headacheinsight.core.designsystem.HeadacheInsightStatusBadge
import com.neuron.headacheinsight.core.designsystem.HeadacheInsightStatusColors
import com.neuron.headacheinsight.core.designsystem.headacheInsightActionButtonColors
import com.neuron.headacheinsight.core.designsystem.preferredHorizontalAlignment
import com.neuron.headacheinsight.core.designsystem.preferredTextAlign
import com.neuron.headacheinsight.core.model.AllDataAnalysisOwnerId
import com.neuron.headacheinsight.core.model.AnalysisResponse
import com.neuron.headacheinsight.core.model.AnalysisRunPreview
import com.neuron.headacheinsight.core.model.AnalysisSnapshot
import com.neuron.headacheinsight.core.ui.AnalysisSnapshotCard
import com.neuron.headacheinsight.core.ui.BottomMenuActions
import com.neuron.headacheinsight.domain.AnalysisRepository
import com.neuron.headacheinsight.domain.BuildReportsUseCase
import com.neuron.headacheinsight.domain.SettingsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class AllDataAnalysisUiState(
    val analysisPreview: AnalysisRunPreview? = null,
    val analysis: AnalysisResponse? = null,
    val isAnalyzing: Boolean = false,
    val errorMessage: String? = null,
)

@HiltViewModel
class AllDataAnalysisViewModel @Inject constructor(
    private val buildReportsUseCase: BuildReportsUseCase,
    private val analysisRepository: AnalysisRepository,
    private val settingsRepository: SettingsRepository,
) : androidx.lifecycle.ViewModel() {
    private val _state = MutableStateFlow(AllDataAnalysisUiState())
    val state: StateFlow<AllDataAnalysisUiState> = _state
    val history: StateFlow<List<AnalysisSnapshot>> = analysisRepository.observeAnalysisHistory(AllDataAnalysisOwnerId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun analyze() {
        if (_state.value.isAnalyzing) return
        viewModelScope.launch {
            _state.emit(_state.value.copy(isAnalyzing = true, errorMessage = null))
            val settings = settingsRepository.observeSettings().first()
            val bundle = buildReportsUseCase()
            val preview = analysisRepository.previewAllDataAnalysis(
                payloadJson = bundle.json,
                locale = settings.languageTag,
            ).getOrNull()
            analysisRepository.analyzeAllData(
                payloadJson = bundle.json,
                locale = settings.languageTag,
            ).fold(
                onSuccess = { response ->
                    _state.emit(
                        AllDataAnalysisUiState(
                            analysisPreview = preview,
                            analysis = response,
                            isAnalyzing = false,
                        ),
                    )
                },
                onFailure = { error ->
                    _state.emit(
                        AllDataAnalysisUiState(
                            analysisPreview = preview,
                            analysis = null,
                            isAnalyzing = false,
                            errorMessage = error.message,
                        ),
                    )
                },
            )
        }
    }
}

@Composable
fun AllDataAnalysisRoute(
    onBack: () -> Unit,
    onHome: () -> Unit,
    viewModel: AllDataAnalysisViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val history by viewModel.history.collectAsStateWithLifecycle()
    LaunchedEffect(Unit) {
        if (state.analysis == null && state.errorMessage == null) {
            viewModel.analyze()
        }
    }
    AllDataAnalysisScreen(
        state = state,
        history = history,
        onAnalyze = viewModel::analyze,
        onBack = onBack,
        onHome = onHome,
    )
}

@Composable
fun AllDataAnalysisScreen(
    state: AllDataAnalysisUiState,
    history: List<AnalysisSnapshot>,
    onAnalyze: () -> Unit,
    onBack: () -> Unit,
    onHome: () -> Unit,
) {
    Scaffold(
        bottomBar = {
            Surface {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .imePadding()
                        .navigationBarsPadding()
                        .padding(horizontal = 20.dp, vertical = 12.dp),
                    horizontalAlignment = preferredHorizontalAlignment(),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Button(
                        onClick = onAnalyze,
                        enabled = !state.isAnalyzing,
                        colors = headacheInsightActionButtonColors(),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        if (state.isAnalyzing) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(18.dp),
                                strokeWidth = 2.dp,
                            )
                            Text(
                                text = stringResource(R.string.reports_all_analysis_running),
                                modifier = Modifier.padding(start = 10.dp),
                            )
                        } else {
                            Text(stringResource(R.string.reports_all_analysis_action))
                        }
                    }
                    BottomMenuActions(
                        onBack = onBack,
                        onHome = onHome,
                    )
                }
            }
        },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(20.dp)
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = preferredHorizontalAlignment(),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            HeadacheInsightSectionCard(
                title = stringResource(R.string.reports_all_analysis_title),
                supportingText = stringResource(R.string.reports_all_analysis_subtitle),
            ) {
                state.analysisPreview?.let { preview ->
                    Text(
                        text = stringResource(
                            R.string.reports_all_analysis_tokens,
                            preview.estimatedInputTokens,
                        ),
                        modifier = Modifier.fillMaxWidth(),
                        textAlign = preferredTextAlign(),
                    )
                    Text(
                        text = if (preview.automaticSelection) {
                            stringResource(
                                R.string.reports_all_analysis_auto_model,
                                preview.effectiveModel,
                            )
                        } else {
                            stringResource(
                                R.string.reports_all_analysis_selected_model,
                                preview.effectiveModel,
                            )
                        },
                        modifier = Modifier.fillMaxWidth(),
                        textAlign = preferredTextAlign(),
                    )
                    if (preview.shouldSuggestRecommendedModel) {
                        Text(
                            text = stringResource(
                                R.string.reports_all_analysis_recommendation,
                                preview.recommendedModel,
                            ),
                            modifier = Modifier.fillMaxWidth(),
                            color = MaterialTheme.colorScheme.tertiary,
                            textAlign = preferredTextAlign(),
                        )
                    }
                }
                state.errorMessage?.let { message ->
                    Text(
                        text = stringResource(R.string.reports_all_analysis_error, message),
                        modifier = Modifier.fillMaxWidth(),
                        color = MaterialTheme.colorScheme.error,
                        textAlign = preferredTextAlign(),
                    )
                }
            }

            state.analysis?.let { analysis ->
                HeadacheInsightSectionCard(
                    title = stringResource(R.string.reports_all_analysis_result_title),
                    supportingText = stringResource(R.string.reports_all_analysis_result_subtitle),
                ) {
                    if (analysis.urgentAction.userMessage.isNotBlank()) {
                        Text(
                            text = analysis.urgentAction.userMessage,
                            modifier = Modifier.fillMaxWidth(),
                            color = if (analysis.urgentAction.level.name == "NONE") {
                                MaterialTheme.colorScheme.onSurface
                            } else {
                                MaterialTheme.colorScheme.error
                            },
                            textAlign = preferredTextAlign(),
                        )
                    }
                    if (analysis.userSummary.plainLanguageSummary.isNotBlank()) {
                        Text(
                            text = analysis.userSummary.plainLanguageSummary,
                            modifier = Modifier.fillMaxWidth(),
                            textAlign = preferredTextAlign(),
                        )
                    }
                    if (analysis.userSummary.keyObservations.isNotEmpty()) {
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            analysis.userSummary.keyObservations.take(10).forEach { observation ->
                                HeadacheInsightStatusBadge(
                                    label = observation,
                                    color = HeadacheInsightStatusColors.CloudAnalyzed,
                                )
                            }
                        }
                    }
                    if (analysis.hypotheses.isNotEmpty()) {
                        Text(
                            text = stringResource(R.string.reports_all_analysis_hypotheses),
                            modifier = Modifier.fillMaxWidth(),
                            style = MaterialTheme.typography.titleMedium,
                            textAlign = preferredTextAlign(),
                        )
                        analysis.hypotheses.take(4).forEach { hypothesis ->
                            Text(
                                text = "- ${hypothesis.label}: ${hypothesis.rationale}",
                                modifier = Modifier.fillMaxWidth(),
                                textAlign = preferredTextAlign(),
                            )
                        }
                    }
                    if (analysis.suggestedDoctorDiscussionPoints.isNotEmpty()) {
                        Text(
                            text = stringResource(R.string.reports_all_analysis_doctor_points),
                            modifier = Modifier.fillMaxWidth(),
                            style = MaterialTheme.typography.titleMedium,
                            textAlign = preferredTextAlign(),
                        )
                        analysis.suggestedDoctorDiscussionPoints.take(6).forEach { point ->
                            Text(
                                text = "- $point",
                                modifier = Modifier.fillMaxWidth(),
                                textAlign = preferredTextAlign(),
                            )
                        }
                    }
                    if (analysis.selfCareGeneral.isNotEmpty()) {
                        Text(
                            text = stringResource(R.string.reports_all_analysis_self_care),
                            modifier = Modifier.fillMaxWidth(),
                            style = MaterialTheme.typography.titleMedium,
                            textAlign = preferredTextAlign(),
                        )
                        analysis.selfCareGeneral.take(6).forEach { item ->
                            Text(
                                text = "- $item",
                                modifier = Modifier.fillMaxWidth(),
                                textAlign = preferredTextAlign(),
                            )
                        }
                    }
                }
            }

            if (history.isNotEmpty()) {
                HeadacheInsightSectionCard(
                    title = stringResource(R.string.reports_all_analysis_history_title),
                    supportingText = stringResource(R.string.reports_all_analysis_history_subtitle),
                ) {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        history.forEach { snapshot ->
                            AnalysisSnapshotCard(snapshot = snapshot)
                        }
                    }
                }
            }
        }
    }
}
