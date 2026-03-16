package com.neuron.headacheinsight.feature.episode

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.neuron.headacheinsight.core.designsystem.HeadacheInsightSectionCard
import com.neuron.headacheinsight.core.designsystem.KeyValueLine
import com.neuron.headacheinsight.core.model.EpisodeDetail
import com.neuron.headacheinsight.core.ui.BottomMenuActions
import com.neuron.headacheinsight.core.ui.localizedAttachmentType
import com.neuron.headacheinsight.core.ui.localizedRedFlagStatus
import com.neuron.headacheinsight.core.ui.localizedSymptomLabel
import com.neuron.headacheinsight.domain.EpisodeRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

@HiltViewModel
class EpisodeViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    episodeRepository: EpisodeRepository,
) : androidx.lifecycle.ViewModel() {
    private val episodeId: String = checkNotNull(savedStateHandle["episodeId"])

    val state: StateFlow<EpisodeDetail?> = episodeRepository.observeEpisodeDetail(episodeId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)
}

@Composable
fun EpisodeRoute(
    onBack: () -> Unit,
    onHome: () -> Unit,
    onOpenQuestions: (String) -> Unit,
    viewModel: EpisodeViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    EpisodeScreen(
        state = state,
        onBack = onBack,
        onHome = onHome,
        onOpenQuestions = onOpenQuestions,
    )
}

@Composable
fun EpisodeScreen(
    state: EpisodeDetail?,
    onBack: () -> Unit,
    onHome: () -> Unit,
    onOpenQuestions: (String) -> Unit,
) {
    if (state == null) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(20.dp),
        ) {
            Text(stringResource(R.string.episode_loading))
        }
        return
    }
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item {
            HeadacheInsightSectionCard(
                title = stringResource(R.string.episode_title),
                supportingText = stringResource(R.string.episode_subtitle),
            ) {
                KeyValueLine(stringResource(R.string.episode_started), state.episode.startedAt.toString())
                KeyValueLine(
                    stringResource(R.string.episode_severity),
                    (state.episode.currentSeverity ?: "-").toString(),
                )
                KeyValueLine(
                    stringResource(R.string.episode_red_flags),
                    localizedRedFlagStatus(state.episode.redFlagStatus),
                )
                state.episode.summaryText?.let { Text(it) }
            }
        }
        item {
            HeadacheInsightSectionCard(title = stringResource(R.string.episode_symptoms_title)) {
                state.symptoms.forEach { symptom ->
                    Text(
                        stringResource(
                            R.string.episode_symptom_line,
                            localizedSymptomLabel(symptom.symptomCode),
                            (symptom.intensity ?: "-").toString(),
                        ),
                    )
                }
            }
        }
        item {
            HeadacheInsightSectionCard(title = stringResource(R.string.episode_transcripts_title)) {
                state.transcripts.forEach { transcript ->
                    Text(
                        stringResource(
                            R.string.episode_transcript_line,
                            transcript.variant.name,
                            transcript.transcriptText.orEmpty(),
                        ),
                    )
                }
            }
        }
        item {
            HeadacheInsightSectionCard(title = stringResource(R.string.episode_attachments_title)) {
                state.attachments.forEach { attachment ->
                    Text(
                        stringResource(
                            R.string.episode_attachment_line,
                            attachment.displayName,
                            localizedAttachmentType(attachment.type),
                        ),
                    )
                }
            }
        }
        item {
            HeadacheInsightSectionCard(title = stringResource(R.string.episode_answers_title)) {
                state.answers.forEach { answer ->
                    Text(stringResource(R.string.episode_answer_line, answer.questionId, answer.answerPayload.toString()))
                }
            }
        }
        item {
            HeadacheInsightSectionCard(title = stringResource(R.string.episode_analysis_title)) {
                state.analyses.forEach { analysis ->
                    Text(stringResource(R.string.episode_analysis_line, analysis.modelName, analysis.status.name))
                }
            }
        }
        item {
            Button(
                onClick = { onOpenQuestions(state.episode.id) },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(stringResource(R.string.episode_open_questions))
            }
        }
        item {
            BottomMenuActions(
                onBack = onBack,
                onHome = onHome,
            )
        }
    }
}
