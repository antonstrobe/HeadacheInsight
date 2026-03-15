package com.neuron.headacheinsight.feature.questionnaire

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.neuron.headacheinsight.core.designsystem.HeadacheInsightSectionCard
import com.neuron.headacheinsight.core.model.QuestionTemplate
import com.neuron.headacheinsight.domain.ObserveQuestionSetUseCase
import com.neuron.headacheinsight.domain.SaveQuestionAnswerUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonPrimitive
import javax.inject.Inject

@HiltViewModel
class QuestionnaireViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    observeQuestionSetUseCase: ObserveQuestionSetUseCase,
    private val saveQuestionAnswerUseCase: SaveQuestionAnswerUseCase,
) : androidx.lifecycle.ViewModel() {
    private val episodeId: String = checkNotNull(savedStateHandle["episodeId"])

    val state: StateFlow<List<QuestionTemplate>> = observeQuestionSetUseCase.forEpisode(episodeId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun saveAnswer(questionId: String, value: String) {
        viewModelScope.launch {
            saveQuestionAnswerUseCase(
                episodeId = episodeId,
                profileId = null,
                questionId = questionId,
                payload = JsonPrimitive(value),
            )
        }
    }
}

@Composable
fun QuestionnaireRoute(
    viewModel: QuestionnaireViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    QuestionnaireScreen(questions = state, onSaveAnswer = viewModel::saveAnswer)
}

@Composable
fun QuestionnaireScreen(
    questions: List<QuestionTemplate>,
    onSaveAnswer: (String, String) -> Unit,
) {
    val answers = remember { mutableStateMapOf<String, String>() }
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        items(questions, key = { it.id }) { question ->
            HeadacheInsightSectionCard(
                title = question.shortLabel,
                supportingText = question.prompt,
            ) {
                OutlinedTextField(
                    value = answers[question.id].orEmpty(),
                    onValueChange = { answers[question.id] = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text(question.helpText ?: stringResource(R.string.questionnaire_answer_label)) },
                )
                Button(onClick = { onSaveAnswer(question.id, answers[question.id].orEmpty()) }) {
                    Text(stringResource(R.string.questionnaire_save))
                }
            }
        }
    }
}
