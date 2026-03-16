package com.neuron.headacheinsight.feature.quicklog

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.speech.RecognizerIntent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.neuron.headacheinsight.core.common.TimeProvider
import com.neuron.headacheinsight.core.designsystem.HeadacheInsightSectionCard
import com.neuron.headacheinsight.core.ui.BottomMenuActions
import com.neuron.headacheinsight.core.model.Episode
import com.neuron.headacheinsight.core.model.EpisodeSymptom
import com.neuron.headacheinsight.core.model.EpisodeTranscript
import com.neuron.headacheinsight.core.model.LocalRedFlagInput
import com.neuron.headacheinsight.core.model.TranscriptStatus
import com.neuron.headacheinsight.core.model.TranscriptVariant
import com.neuron.headacheinsight.core.ui.SelectableChipGroup
import com.neuron.headacheinsight.core.ui.SeveritySlider
import com.neuron.headacheinsight.domain.AudioRecorder
import com.neuron.headacheinsight.domain.CreateEpisodeUseCase
import com.neuron.headacheinsight.domain.EpisodeRepository
import com.neuron.headacheinsight.domain.RedFlagEngine
import com.neuron.headacheinsight.domain.SaveQuickLogUseCase
import com.neuron.headacheinsight.domain.SaveTranscriptUseCase
import com.neuron.headacheinsight.domain.SyncScheduler
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.util.UUID
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
    val lastTranscriptText: String? = null,
    val voiceErrorMessage: String? = null,
)

@HiltViewModel
class QuickLogViewModel @Inject constructor(
    private val createEpisodeUseCase: CreateEpisodeUseCase,
    private val episodeRepository: EpisodeRepository,
    private val saveQuickLogUseCase: SaveQuickLogUseCase,
    private val saveTranscriptUseCase: SaveTranscriptUseCase,
    private val redFlagEngine: RedFlagEngine,
    private val audioRecorder: AudioRecorder,
    private val syncScheduler: SyncScheduler,
    private val timeProvider: TimeProvider,
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
    fun setVoiceError(value: String?) = draft.tryEmit(state.value.copy(voiceErrorMessage = value))

    fun toggleRecording() {
        val episode = state.value.episode ?: return
        viewModelScope.launch {
            if (state.value.isRecording) {
                val result = audioRecorder.stop()
                val path = result.getOrNull()
                if (path != null) {
                    syncScheduler.enqueueTranscription(path, episode.id)
                }
                draft.emit(
                    state.value.copy(
                        isRecording = false,
                        lastAudioPath = path,
                        voiceErrorMessage = result.exceptionOrNull()?.message,
                    ),
                )
            } else {
                val result = audioRecorder.start(episode.id)
                draft.emit(
                    state.value.copy(
                        isRecording = result.isSuccess,
                        lastAudioPath = result.getOrNull(),
                        voiceErrorMessage = result.exceptionOrNull()?.message,
                    ),
                )
            }
        }
    }

    fun saveOfflineDictation(transcriptText: String) {
        val episode = state.value.episode ?: return
        val normalized = transcriptText.trim()
        if (normalized.isBlank()) return
        viewModelScope.launch {
            val now = timeProvider.now()
            episodeRepository.updateEpisode(
                episode.copy(
                    summaryText = normalized,
                    transcriptStatus = TranscriptStatus.LOCAL_READY,
                    updatedAt = now,
                ),
            )
            saveTranscriptUseCase(
                EpisodeTranscript(
                    id = UUID.randomUUID().toString(),
                    episodeId = episode.id,
                    rawAudioPath = null,
                    transcriptText = normalized,
                    language = episode.locale,
                    engineType = "android-on-device-dictation",
                    variant = TranscriptVariant.LOCAL,
                    confidence = null,
                    createdAt = now,
                ),
                shouldCloudRetry = false,
            )
            draft.emit(
                state.value.copy(
                    lastTranscriptText = normalized,
                    voiceErrorMessage = null,
                ),
            )
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
    onBack: () -> Unit,
    onHome: () -> Unit,
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
        onSaveOfflineDictation = viewModel::saveOfflineDictation,
        onToggleVoiceRecording = viewModel::toggleRecording,
        onVoiceError = viewModel::setVoiceError,
        onSave = { viewModel.save(onOpenEpisode) },
        onBack = onBack,
        onHome = onHome,
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
    onSaveOfflineDictation: (String) -> Unit,
    onToggleVoiceRecording: () -> Unit,
    onVoiceError: (String?) -> Unit,
    onSave: () -> Unit,
    onBack: () -> Unit,
    onHome: () -> Unit,
) {
    val context = LocalContext.current
    val localeTag = context.resources.configuration.locales[0]?.toLanguageTag() ?: "ru-RU"
    var permissionDenied by remember { mutableStateOf(false) }
    val audioPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
    ) { granted ->
        permissionDenied = !granted
        if (granted) {
            onToggleVoiceRecording()
        }
    }
    val dictationLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult(),
    ) { result ->
        val transcript = result.data
            ?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)
            ?.firstOrNull()
            ?.trim()
            .orEmpty()
        when {
            result.resultCode == Activity.RESULT_OK && transcript.isNotBlank() -> onSaveOfflineDictation(transcript)
            result.resultCode == Activity.RESULT_CANCELED -> onVoiceError(null)
            else -> onVoiceError(context.getString(R.string.quicklog_dictation_unavailable))
        }
    }

    fun handleVoiceButton() {
        if (state.isRecording || ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
            permissionDenied = false
            onToggleVoiceRecording()
        } else {
            audioPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
        }
    }

    fun handleOfflineDictation() {
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, localeTag)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, localeTag)
            putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, true)
            putExtra(RecognizerIntent.EXTRA_PROMPT, context.getString(R.string.quicklog_dictation_prompt))
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
        }
        if (intent.resolveActivity(context.packageManager) == null) {
            onVoiceError(context.getString(R.string.quicklog_dictation_unavailable))
            return
        }
        onVoiceError(null)
        dictationLauncher.launch(intent)
    }

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

        OutlinedButton(onClick = ::handleOfflineDictation, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.quicklog_dictation_start))
        }
        Button(onClick = ::handleVoiceButton, modifier = Modifier.fillMaxWidth()) {
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
        if (permissionDenied) {
            Text(stringResource(R.string.quicklog_audio_permission_required))
        }
        state.lastAudioPath?.let {
            Text(stringResource(R.string.quicklog_audio_saved, it))
        }
        state.lastTranscriptText?.let {
            Text(stringResource(R.string.quicklog_dictation_saved, it))
        }
        state.voiceErrorMessage?.let {
            Text(stringResource(R.string.quicklog_voice_error, it))
        }
        if (state.urgentMessage.isNotBlank()) {
            Text(state.urgentMessage)
        }
        Spacer(modifier = Modifier.height(8.dp))
        BottomMenuActions(
            onBack = onBack,
            onHome = onHome,
        )
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
