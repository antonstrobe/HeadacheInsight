package com.neuron.headacheinsight.feature.quicklog

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.neuron.headacheinsight.core.designsystem.HeadacheInsightSectionCard
import com.neuron.headacheinsight.core.ui.SelectableChipGroup
import com.neuron.headacheinsight.core.ui.SeveritySlider
import com.neuron.headacheinsight.core.model.Episode
import com.neuron.headacheinsight.core.model.EpisodeSymptom
import com.neuron.headacheinsight.core.model.LocalRedFlagInput
import com.neuron.headacheinsight.domain.AudioRecorder
import com.neuron.headacheinsight.domain.CreateEpisodeUseCase
import com.neuron.headacheinsight.domain.EpisodeRepository
import com.neuron.headacheinsight.domain.RedFlagEngine
import com.neuron.headacheinsight.domain.SaveQuickLogUseCase
import com.neuron.headacheinsight.domain.SyncScheduler
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class QuickLogUiState(
    val episode: Episode? = null,
    val severity: Int = 5,
    val selectedSymptoms: Set<String> = emptySet(),
    val suddenWorstPain: Boolean = false,
    val confusion: Boolean = false,
    val speechDifficulty: Boolean = false,
    val oneSidedWeakness: Boolean = false,
    val medicineNotes: String = "",
    val interruptedBySafety: Boolean = false,
    val urgentMessage: String = "",
    val isRecording: Boolean = false,
    val lastAudioPath: String? = null,
)

@HiltViewModel
class QuickLogViewModel @Inject constructor(
    private val createEpisodeUseCase: CreateEpisodeUseCase,
    episodeRepository: EpisodeRepository,
    private val saveQuickLogUseCase: SaveQuickLogUseCase,
    private val redFlagEngine: RedFlagEngine,
    private val audioRecorder: AudioRecorder,
    private val syncScheduler: SyncScheduler,
) : androidx.lifecycle.ViewModel() {
    private val draft = MutableStateFlow(QuickLogUiState())

    val state: StateFlow<QuickLogUiState> = combine(
        episodeRepository.observeActiveEpisode(),
        draft,
    ) { activeEpisode, current ->
        val episode = activeEpisode ?: createEpisodeUseCase()
        current.copy(episode = episode)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), QuickLogUiState())

    fun setSeverity(value: Int) = draft.tryEmit(state.value.copy(severity = value))

    fun toggleSymptom(symptom: String) {
        val selected = state.value.selectedSymptoms.toMutableSet()
        if (!selected.add(symptom)) selected.remove(symptom)
        draft.tryEmit(state.value.copy(selectedSymptoms = selected))
    }

    fun setSuddenWorstPain(value: Boolean) = draft.tryEmit(state.value.copy(suddenWorstPain = value))
    fun setConfusion(value: Boolean) = draft.tryEmit(state.value.copy(confusion = value))
    fun setSpeechDifficulty(value: Boolean) = draft.tryEmit(state.value.copy(speechDifficulty = value))
    fun setOneSidedWeakness(value: Boolean) = draft.tryEmit(state.value.copy(oneSidedWeakness = value))
    fun setMedicineNotes(value: String) = draft.tryEmit(state.value.copy(medicineNotes = value))

    fun toggleRecording() {
        val episode = state.value.episode ?: return
        viewModelScope.launch {
            if (state.value.isRecording) {
                val result = audioRecorder.stop()
                val path = result.getOrNull()
                if (path != null) {
                    syncScheduler.enqueueTranscription(path, episode.id)
                }
                draft.emit(state.value.copy(isRecording = false, lastAudioPath = path))
            } else {
                val result = audioRecorder.start(episode.id)
                draft.emit(state.value.copy(isRecording = result.isSuccess, lastAudioPath = result.getOrNull()))
            }
        }
    }

    fun save(onSaved: (String) -> Unit) {
        val episode = state.value.episode ?: return
        val evaluation = redFlagEngine.evaluate(
            LocalRedFlagInput(
                worstSuddenPain = state.value.suddenWorstPain,
                confusion = state.value.confusion,
                speechDifficulty = state.value.speechDifficulty,
                oneSidedWeakness = state.value.oneSidedWeakness,
            ),
        )
        viewModelScope.launch {
            saveQuickLogUseCase(
                episode = episode,
                severity = state.value.severity,
                symptoms = state.value.selectedSymptoms.map {
                    EpisodeSymptom(
                        episodeId = episode.id,
                        symptomCode = it,
                        present = true,
                        intensity = state.value.severity,
                    )
                },
                redFlagEvaluation = evaluation,
            )
            draft.emit(
                state.value.copy(
                    interruptedBySafety = evaluation.shouldInterruptFlow,
                    urgentMessage = evaluation.emergencyMessage,
                ),
            )
            onSaved(episode.id)
        }
    }
}

@Composable
fun QuickLogRoute(
    onOpenEpisode: (String) -> Unit,
    onRequestVoiceRecording: (String) -> Unit,
    viewModel: QuickLogViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    QuickLogScreen(
        state = state,
        onSeverityChanged = viewModel::setSeverity,
        onToggleSymptom = viewModel::toggleSymptom,
        onSuddenWorstPainChanged = viewModel::setSuddenWorstPain,
        onConfusionChanged = viewModel::setConfusion,
        onSpeechDifficultyChanged = viewModel::setSpeechDifficulty,
        onOneSidedWeaknessChanged = viewModel::setOneSidedWeakness,
        onMedicineNotesChanged = viewModel::setMedicineNotes,
        onToggleVoiceRecording = viewModel::toggleRecording,
        onSave = { viewModel.save(onOpenEpisode) },
    )
}

@Composable
fun QuickLogScreen(
    state: QuickLogUiState,
    onSeverityChanged: (Int) -> Unit,
    onToggleSymptom: (String) -> Unit,
    onSuddenWorstPainChanged: (Boolean) -> Unit,
    onConfusionChanged: (Boolean) -> Unit,
    onSpeechDifficultyChanged: (Boolean) -> Unit,
    onOneSidedWeaknessChanged: (Boolean) -> Unit,
    onMedicineNotesChanged: (String) -> Unit,
    onToggleVoiceRecording: () -> Unit,
    onSave: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        HeadacheInsightSectionCard(
            title = stringResource(R.string.quicklog_title),
            supportingText = stringResource(R.string.quicklog_subtitle),
        ) {
            Text(
                stringResource(
                    R.string.quicklog_episode_id,
                    state.episode?.id ?: stringResource(R.string.quicklog_episode_creating),
                ),
            )
            SeveritySlider(severity = state.severity, onSeverityChanged = onSeverityChanged)
        }

        SelectableChipGroup(
            title = stringResource(R.string.quicklog_symptoms_title),
            options = listOf("nausea", "photophobia", "phonophobia", "dizziness", "aura", "neck pain"),
            selected = state.selectedSymptoms,
            onToggle = onToggleSymptom,
        )

        HeadacheInsightSectionCard(title = stringResource(R.string.quicklog_red_flags_title)) {
            RedFlagToggle(
                title = stringResource(R.string.quicklog_red_flag_sudden),
                value = state.suddenWorstPain,
                onValueChange = onSuddenWorstPainChanged,
            )
            RedFlagToggle(
                title = stringResource(R.string.quicklog_red_flag_confusion),
                value = state.confusion,
                onValueChange = onConfusionChanged,
            )
            RedFlagToggle(
                title = stringResource(R.string.quicklog_red_flag_speech),
                value = state.speechDifficulty,
                onValueChange = onSpeechDifficultyChanged,
            )
            RedFlagToggle(
                title = stringResource(R.string.quicklog_red_flag_weakness),
                value = state.oneSidedWeakness,
                onValueChange = onOneSidedWeaknessChanged,
            )
        }

        HeadacheInsightSectionCard(title = stringResource(R.string.quicklog_medication_title)) {
            OutlinedTextField(
                value = state.medicineNotes,
                onValueChange = onMedicineNotesChanged,
                modifier = Modifier.fillMaxWidth(),
                label = { Text(stringResource(R.string.quicklog_medication_label)) },
            )
        }

        Button(onClick = onToggleVoiceRecording, modifier = Modifier.fillMaxWidth()) {
            Text(
                if (state.isRecording) {
                    stringResource(R.string.quicklog_voice_stop)
                } else {
                    stringResource(R.string.quicklog_voice_start)
                },
            )
        }
        Button(onClick = onSave, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.quicklog_save_now))
        }
        state.lastAudioPath?.let {
            Text(stringResource(R.string.quicklog_audio_saved, it))
        }
        if (state.urgentMessage.isNotBlank()) {
            Text(state.urgentMessage)
        }
    }
}

@Composable
private fun RedFlagToggle(
    title: String,
    value: Boolean,
    onValueChange: (Boolean) -> Unit,
) {
    HeadacheInsightSectionCard(title = title) {
        Switch(checked = value, onCheckedChange = onValueChange)
    }
}
