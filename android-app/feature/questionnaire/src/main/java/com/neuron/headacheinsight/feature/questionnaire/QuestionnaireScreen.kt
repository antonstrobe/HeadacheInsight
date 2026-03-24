package com.neuron.headacheinsight.feature.questionnaire

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.neuron.headacheinsight.core.designsystem.HeadacheInsightSectionCard
import com.neuron.headacheinsight.core.designsystem.HeadacheInsightStatusBadge
import com.neuron.headacheinsight.core.designsystem.HeadacheInsightStatusColors
import com.neuron.headacheinsight.core.designsystem.KeyValueLine
import com.neuron.headacheinsight.core.designsystem.headacheInsightActionButtonColors
import com.neuron.headacheinsight.core.model.AnalysisResponse
import com.neuron.headacheinsight.core.model.AnswerType
import com.neuron.headacheinsight.core.model.EpisodeDetail
import com.neuron.headacheinsight.core.model.QuestionTemplate
import com.neuron.headacheinsight.core.ui.BottomMenuActions
import com.neuron.headacheinsight.core.ui.EmptyState
import com.neuron.headacheinsight.core.ui.SectionActionRow
import com.neuron.headacheinsight.domain.AnalysisRepository
import com.neuron.headacheinsight.domain.EpisodeRepository
import com.neuron.headacheinsight.domain.ObserveQuestionSetUseCase
import com.neuron.headacheinsight.domain.SaveQuestionAnswerUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonPrimitive

private data class QuestionnaireViewDraft(
    val isAnalyzing: Boolean = false,
    val analysisError: String? = null,
    val generatedQuestionCount: Int? = null,
)

data class QuestionnaireUiState(
    val episodeDetail: EpisodeDetail? = null,
    val questions: List<QuestionTemplate> = emptyList(),
    val latestAnalysis: AnalysisResponse? = null,
    val isAnalyzing: Boolean = false,
    val analysisError: String? = null,
    val generatedQuestionCount: Int? = null,
)

@HiltViewModel
class QuestionnaireViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    observeQuestionSetUseCase: ObserveQuestionSetUseCase,
    episodeRepository: EpisodeRepository,
    private val saveQuestionAnswerUseCase: SaveQuestionAnswerUseCase,
    private val analysisRepository: AnalysisRepository,
    private val json: Json,
) : androidx.lifecycle.ViewModel() {
    private val episodeId: String = checkNotNull(savedStateHandle["episodeId"])
    private val draft = MutableStateFlow(QuestionnaireViewDraft())

    val state: StateFlow<QuestionnaireUiState> = combine(
        observeQuestionSetUseCase.forEpisode(episodeId),
        episodeRepository.observeEpisodeDetail(episodeId),
        draft,
    ) { questions, detail, uiDraft ->
        QuestionnaireUiState(
            episodeDetail = detail,
            questions = questions,
            latestAnalysis = detail
                ?.analyses
                ?.firstOrNull()
                ?.responsePayloadJson
                ?.let(::decodeAnalysisOrNull),
            isAnalyzing = uiDraft.isAnalyzing,
            analysisError = uiDraft.analysisError,
            generatedQuestionCount = uiDraft.generatedQuestionCount,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), QuestionnaireUiState())

    fun saveAnswer(questionId: String, payload: JsonElement) {
        viewModelScope.launch {
            saveQuestionAnswerUseCase(
                episodeId = episodeId,
                profileId = null,
                questionId = questionId,
                payload = payload,
            )
        }
    }

    fun analyzeEpisode() {
        viewModelScope.launch {
            draft.emit(QuestionnaireViewDraft(isAnalyzing = true))
            analysisRepository.analyzeEpisode(episodeId).fold(
                onSuccess = { response ->
                    val generatedCount = if (response.nextQuestions.isNotEmpty()) {
                        response.nextQuestions.size
                    } else {
                        analysisRepository.generateFollowUpQuestions(episodeId).getOrDefault(emptyList()).size
                    }
                    draft.emit(
                        QuestionnaireViewDraft(
                            isAnalyzing = false,
                            generatedQuestionCount = generatedCount.takeIf { it > 0 },
                        ),
                    )
                },
                onFailure = { error ->
                    draft.emit(
                        QuestionnaireViewDraft(
                            isAnalyzing = false,
                            analysisError = error.message,
                        ),
                    )
                },
            )
        }
    }

    private fun decodeAnalysisOrNull(rawJson: String): AnalysisResponse? = runCatching {
        json.decodeFromString(AnalysisResponse.serializer(), rawJson)
    }.getOrNull()
}

@Composable
fun QuestionnaireRoute(
    onBack: () -> Unit,
    onHome: () -> Unit,
    viewModel: QuestionnaireViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    QuestionnaireScreen(
        state = state,
        onSaveAnswer = viewModel::saveAnswer,
        onAnalyze = viewModel::analyzeEpisode,
        onBack = onBack,
        onHome = onHome,
    )
}

@Composable
fun QuestionnaireScreen(
    state: QuestionnaireUiState,
    onSaveAnswer: (String, JsonElement) -> Unit,
    onAnalyze: () -> Unit,
    onBack: () -> Unit,
    onHome: () -> Unit,
) {
    androidx.compose.material3.Scaffold(
        bottomBar = {
            Surface {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .imePadding()
                        .navigationBarsPadding()
                        .padding(horizontal = 20.dp, vertical = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Button(
                        onClick = onAnalyze,
                        enabled = state.episodeDetail != null && !state.isAnalyzing,
                        colors = headacheInsightActionButtonColors(),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        if (state.isAnalyzing) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(18.dp),
                                strokeWidth = 2.dp,
                            )
                            Text(
                                text = stringResource(R.string.questionnaire_analyzing),
                                modifier = Modifier.padding(start = 10.dp),
                            )
                        } else {
                            Text(stringResource(R.string.questionnaire_analyze))
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
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            item {
                HeadacheInsightSectionCard(
                    title = stringResource(R.string.questionnaire_title),
                    supportingText = stringResource(R.string.questionnaire_subtitle),
                ) {
                    Text(stringResource(R.string.questionnaire_answer_help))
                    state.generatedQuestionCount?.let {
                        HeadacheInsightStatusBadge(
                            label = stringResource(R.string.questionnaire_generated_count, it),
                            color = HeadacheInsightStatusColors.CloudAnalyzed,
                        )
                    }
                    state.analysisError?.let {
                        Text(
                            text = stringResource(R.string.questionnaire_analysis_error, it),
                            color = MaterialTheme.colorScheme.error,
                        )
                    }
                }
            }

            state.episodeDetail?.let { detail ->
                item {
                    HeadacheInsightSectionCard(
                        title = stringResource(R.string.questionnaire_episode_title),
                        supportingText = stringResource(R.string.questionnaire_episode_subtitle),
                    ) {
                        KeyValueLine(
                            label = stringResource(R.string.questionnaire_episode_status),
                            value = detail.episode.status.name,
                        )
                        KeyValueLine(
                            label = stringResource(R.string.questionnaire_episode_severity),
                            value = (detail.episode.currentSeverity ?: "-").toString(),
                        )
                        detail.episode.summaryText?.takeIf(String::isNotBlank)?.let {
                            Text(it)
                        }
                    }
                }
            }

            state.latestAnalysis?.let { analysis ->
                item {
                    AnalysisSummaryCard(analysis = analysis)
                }
            }

            if (state.questions.isEmpty()) {
                item {
                    EmptyState(
                        title = stringResource(R.string.questionnaire_empty_title),
                        subtitle = stringResource(R.string.questionnaire_empty_subtitle),
                    )
                }
            }

            items(state.questions, key = { it.id }) { question ->
                QuestionEditorCard(
                    question = question,
                    onSaveAnswer = onSaveAnswer,
                )
            }
        }
    }
}

@Composable
private fun AnalysisSummaryCard(
    analysis: AnalysisResponse,
) {
    HeadacheInsightSectionCard(
        title = stringResource(R.string.questionnaire_analysis_title),
        supportingText = stringResource(R.string.questionnaire_analysis_subtitle),
    ) {
        if (analysis.urgentAction.userMessage.isNotBlank()) {
            Text(
                text = analysis.urgentAction.userMessage,
                color = if (analysis.urgentAction.level.name == "NONE") {
                    MaterialTheme.colorScheme.onSurface
                } else {
                    MaterialTheme.colorScheme.error
                },
            )
        }
        if (analysis.userSummary.plainLanguageSummary.isNotBlank()) {
            Text(analysis.userSummary.plainLanguageSummary)
        }
        if (analysis.userSummary.keyObservations.isNotEmpty()) {
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                analysis.userSummary.keyObservations.take(8).forEach { observation ->
                    FilterChip(
                        selected = true,
                        onClick = { },
                        label = { Text(observation) },
                        colors = FilterChipDefaults.filterChipColors(),
                    )
                }
            }
        }
        if (analysis.nextQuestions.isNotEmpty()) {
            HeadacheInsightStatusBadge(
                label = stringResource(R.string.questionnaire_analysis_next_questions, analysis.nextQuestions.size),
                color = HeadacheInsightStatusColors.CloudAnalyzed,
            )
        }
    }
}

@Composable
private fun QuestionEditorCard(
    question: QuestionTemplate,
    onSaveAnswer: (String, JsonElement) -> Unit,
) {
    var textValue by remember(question.id) { mutableStateOf("") }
    var booleanValue by remember(question.id) { mutableStateOf<Boolean?>(null) }
    var singleValue by remember(question.id) { mutableStateOf<String?>(null) }
    val multiValue = remember(question.id) { mutableStateListOf<String>() }
    var scaleValue by remember(question.id) { mutableStateOf(5f) }

    val canSave = when (question.answerType) {
        AnswerType.BOOLEAN -> booleanValue != null || !question.required
        AnswerType.SINGLE_SELECT -> singleValue != null || !question.required
        AnswerType.MULTI_SELECT -> multiValue.isNotEmpty() || !question.required
        AnswerType.SCALE -> true
        else -> textValue.isNotBlank() || !question.required
    }

    fun saveCurrentAnswer() {
        val payload = when (question.answerType) {
            AnswerType.BOOLEAN -> booleanValue?.let(::JsonPrimitive) ?: JsonNull
            AnswerType.SCALE -> JsonPrimitive(scaleValue.toInt())
            AnswerType.SINGLE_SELECT -> singleValue?.let(::JsonPrimitive) ?: JsonNull
            AnswerType.MULTI_SELECT -> JsonArray(multiValue.distinct().map(::JsonPrimitive))
            AnswerType.NUMBER -> textValue.toIntOrNull()?.let(::JsonPrimitive) ?: JsonPrimitive(textValue)
            else -> JsonPrimitive(textValue.trim())
        }
        onSaveAnswer(question.id, payload)
    }

    HeadacheInsightSectionCard(
        title = question.shortLabel,
        supportingText = question.prompt,
    ) {
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            HeadacheInsightStatusBadge(
                label = if (question.required) {
                    stringResource(R.string.questionnaire_required)
                } else {
                    stringResource(R.string.questionnaire_optional)
                },
                color = if (question.required) {
                    HeadacheInsightStatusColors.CloudAnalyzed
                } else {
                    HeadacheInsightStatusColors.Queued
                },
            )
            HeadacheInsightStatusBadge(
                label = question.answerType.name.replace('_', ' '),
                color = HeadacheInsightStatusColors.LocalComplete,
            )
        }

        question.helpText?.let {
            Text(
                text = it,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.outline,
            )
        }

        when (question.answerType) {
            AnswerType.BOOLEAN -> {
                ChoiceChipRow(
                    options = listOf(
                        stringResource(R.string.questionnaire_yes) to true,
                        stringResource(R.string.questionnaire_no) to false,
                    ),
                    selected = booleanValue,
                    onSelect = { booleanValue = it },
                )
            }

            AnswerType.SCALE -> {
                Text(stringResource(R.string.questionnaire_scale_value, scaleValue.toInt()))
                Slider(
                    value = scaleValue,
                    onValueChange = { scaleValue = it },
                    valueRange = 0f..10f,
                    steps = 9,
                )
            }

            AnswerType.SINGLE_SELECT -> {
                if (question.options.isEmpty()) {
                    OutlinedTextField(
                        value = textValue,
                        onValueChange = { textValue = it },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text(stringResource(R.string.questionnaire_answer_label)) },
                    )
                } else {
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        question.options.forEach { option ->
                            FilterChip(
                                selected = singleValue == option,
                                onClick = { singleValue = option },
                                label = { Text(option) },
                            )
                        }
                    }
                }
            }

            AnswerType.MULTI_SELECT -> {
                if (question.options.isEmpty()) {
                    OutlinedTextField(
                        value = textValue,
                        onValueChange = { textValue = it },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text(stringResource(R.string.questionnaire_answer_label)) },
                    )
                } else {
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        question.options.forEach { option ->
                            val selected = option in multiValue
                            FilterChip(
                                selected = selected,
                                onClick = {
                                    if (selected) {
                                        multiValue.remove(option)
                                    } else {
                                        multiValue.add(option)
                                    }
                                },
                                label = { Text(option) },
                            )
                        }
                    }
                }
            }

            AnswerType.NUMBER -> {
                OutlinedTextField(
                    value = textValue,
                    onValueChange = { textValue = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text(stringResource(R.string.questionnaire_number_label)) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                )
            }

            else -> {
                OutlinedTextField(
                    value = textValue,
                    onValueChange = { textValue = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text(question.helpText ?: stringResource(R.string.questionnaire_answer_label)) },
                    minLines = if (question.answerType == AnswerType.FREE_TEXT || question.answerType == AnswerType.MEDICATION_LIST) 3 else 1,
                    keyboardOptions = KeyboardOptions(
                        keyboardType = when (question.answerType) {
                            AnswerType.DATE_TIME -> KeyboardType.Text
                            AnswerType.DURATION -> KeyboardType.Text
                            else -> KeyboardType.Text
                        },
                    ),
                )
            }
        }

        SectionActionRow {
            TextButton(onClick = { saveCurrentAnswer() }, enabled = canSave) {
                Text(stringResource(R.string.questionnaire_save))
            }
        }
    }
}

@Composable
private fun ChoiceChipRow(
    options: List<Pair<String, Boolean>>,
    selected: Boolean?,
    onSelect: (Boolean) -> Unit,
) {
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        options.forEach { (label, value) ->
            FilterChip(
                selected = selected == value,
                onClick = { onSelect(value) },
                label = { Text(label) },
            )
        }
    }
}
