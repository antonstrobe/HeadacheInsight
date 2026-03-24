package com.neuron.headacheinsight.feature.quicklog

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.neuron.headacheinsight.core.common.TimeProvider
import com.neuron.headacheinsight.core.designsystem.HeadacheInsightSectionCard
import com.neuron.headacheinsight.core.designsystem.HeadacheInsightStatusBadge
import com.neuron.headacheinsight.core.designsystem.HeadacheInsightStatusColors
import com.neuron.headacheinsight.core.designsystem.KeyValueLine
import com.neuron.headacheinsight.core.designsystem.headacheInsightActionButtonColors
import com.neuron.headacheinsight.core.model.Episode
import com.neuron.headacheinsight.core.model.EpisodeMedication
import com.neuron.headacheinsight.core.model.EpisodeSymptom
import com.neuron.headacheinsight.core.model.EpisodeTranscript
import com.neuron.headacheinsight.core.model.LocalRedFlagInput
import com.neuron.headacheinsight.core.model.TranscriptStatus
import com.neuron.headacheinsight.core.model.TranscriptVariant
import com.neuron.headacheinsight.core.model.VoiceIntakeDraft
import com.neuron.headacheinsight.core.ui.BottomMenuActions
import com.neuron.headacheinsight.core.ui.SelectableChipGroup
import com.neuron.headacheinsight.core.ui.SeveritySlider
import com.neuron.headacheinsight.core.ui.ToggleSectionCard
import com.neuron.headacheinsight.domain.AudioRecorder
import com.neuron.headacheinsight.domain.CreateEpisodeUseCase
import com.neuron.headacheinsight.domain.EpisodeRepository
import com.neuron.headacheinsight.domain.RedFlagEngine
import com.neuron.headacheinsight.domain.SaveQuickLogUseCase
import com.neuron.headacheinsight.domain.SaveTranscriptUseCase
import com.neuron.headacheinsight.domain.SyncScheduler
import com.neuron.headacheinsight.domain.VoiceIntakeRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import java.util.UUID
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

private enum class PendingMicrophoneAction {
    VOICE_FILL,
}

private val DefaultSymptoms = listOf(
    "nausea",
    "photophobia",
    "phonophobia",
    "dizziness",
    "aura",
    "neck pain",
)

data class QuickLogUiState(
    val episode: Episode? = null,
    val severity: Int = 5,
    val selectedSymptoms: Set<String> = emptySet(),
    val detectedSymptoms: Set<String> = emptySet(),
    val suddenWorstPain: Boolean = false,
    val confusion: Boolean = false,
    val speechDifficulty: Boolean = false,
    val oneSidedWeakness: Boolean = false,
    val medicineNotes: String = "",
    val notesText: String = "",
    val interruptedBySafety: Boolean = false,
    val urgentMessage: String = "",
    val isRecording: Boolean = false,
    val lastAudioPath: String? = null,
    val lastTranscriptText: String? = null,
    val voiceErrorMessage: String? = null,
    val transcriptHistory: List<String> = emptyList(),
    val liveTranscriptPreview: String = "",
    val isVoiceProcessing: Boolean = false,
    val voiceDraft: VoiceIntakeDraft? = null,
)

@OptIn(kotlinx.coroutines.FlowPreview::class)
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
    private val voiceIntakeRepository: VoiceIntakeRepository,
) : androidx.lifecycle.ViewModel() {
    private val draft = MutableStateFlow(QuickLogUiState())
    private val transcriptForStructuring = MutableStateFlow("")

    val state: StateFlow<QuickLogUiState> = combine(
        episodeRepository.observeActiveEpisode(),
        draft,
    ) { activeEpisode, current ->
        val episode = activeEpisode ?: createEpisodeUseCase()
        current.copy(episode = episode)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), QuickLogUiState())

    init {
        viewModelScope.launch {
            transcriptForStructuring
                .map(String::trim)
                .filter { it.isNotBlank() }
                .debounce(1_100)
                .distinctUntilChanged()
                .collect { transcript ->
                    val episode = state.value.episode ?: return@collect
                    draft.emit(state.value.copy(isVoiceProcessing = true, voiceErrorMessage = null))
                    voiceIntakeRepository.structureVoiceIntake(
                        ownerId = episode.id,
                        locale = episode.locale,
                        transcriptText = transcript,
                    ).fold(
                        onSuccess = { applyVoiceDraft(it) },
                        onFailure = { error ->
                            draft.emit(
                                state.value.copy(
                                    isVoiceProcessing = false,
                                    voiceErrorMessage = error.message,
                                ),
                            )
                        },
                    )
                }
        }
    }

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
    fun setNotesText(value: String) = draft.tryEmit(state.value.copy(notesText = value))
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

    fun updateLiveTranscriptPreview(transcriptText: String) {
        val normalized = transcriptText.trim()
        val nextState = state.value.copy(liveTranscriptPreview = normalized)
        draft.tryEmit(nextState)
        transcriptForStructuring.tryEmit(mergeTranscript(nextState.transcriptHistory, normalized))
    }

    fun commitVoiceTranscriptSegment(transcriptText: String) {
        val episode = state.value.episode ?: return
        val normalized = transcriptText.trim()
        if (normalized.isBlank()) return
        viewModelScope.launch {
            val history = (state.value.transcriptHistory + normalized).distinct().takeLast(6)
            val mergedTranscript = mergeTranscript(history, "")
            val now = timeProvider.now()
            episodeRepository.updateEpisode(
                episode.copy(
                    summaryText = mergedTranscript,
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
                    engineType = "android-live-dictation",
                    variant = TranscriptVariant.LOCAL,
                    confidence = null,
                    createdAt = now,
                ),
                shouldCloudRetry = false,
            )
            draft.emit(
                state.value.copy(
                    transcriptHistory = history,
                    liveTranscriptPreview = "",
                    lastTranscriptText = normalized,
                    voiceErrorMessage = null,
                ),
            )
            transcriptForStructuring.emit(mergedTranscript)
        }
    }

    fun clearLiveTranscriptPreview() {
        val nextState = state.value.copy(liveTranscriptPreview = "")
        draft.tryEmit(nextState)
        transcriptForStructuring.tryEmit(mergeTranscript(nextState.transcriptHistory, ""))
    }

    fun save(onSaved: (String) -> Unit) {
        val episode = state.value.episode ?: return
        val summaryText = state.value.notesText
            .ifBlank { episode.summaryText.orEmpty() }
            .ifBlank { mergeTranscript(state.value.transcriptHistory, state.value.liveTranscriptPreview) }
            .takeIf { it.isNotBlank() }
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
                episode = episode.copy(summaryText = summaryText),
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
            persistMedicationNotes(episode.id, state.value.medicineNotes)
            draft.emit(
                state.value.copy(
                    interruptedBySafety = evaluation.shouldInterruptFlow,
                    urgentMessage = evaluation.emergencyMessage,
                ),
            )
            onSaved(episode.id)
        }
    }

    private suspend fun applyVoiceDraft(voiceDraft: VoiceIntakeDraft) {
        val current = state.value
        draft.emit(
            current.copy(
                severity = voiceDraft.severity ?: current.severity,
                selectedSymptoms = current.selectedSymptoms + voiceDraft.symptoms,
                detectedSymptoms = voiceDraft.symptoms.toSet(),
                suddenWorstPain = current.suddenWorstPain || voiceDraft.redFlags.contains("suddenWorstPain"),
                confusion = current.confusion || voiceDraft.redFlags.contains("confusion"),
                speechDifficulty = current.speechDifficulty || voiceDraft.redFlags.contains("speechDifficulty"),
                oneSidedWeakness = current.oneSidedWeakness || voiceDraft.redFlags.contains("oneSidedWeakness"),
                medicineNotes = mergeTextBlocks(current.medicineNotes, voiceDraft.medications.joinToString("\n")),
                notesText = mergeTextBlocks(current.notesText, voiceDraft.summaryText),
                voiceDraft = voiceDraft,
                isVoiceProcessing = false,
                voiceErrorMessage = null,
            ),
        )
    }

    private suspend fun persistMedicationNotes(episodeId: String, rawNotes: String) {
        rawNotes
            .split('\n', ',')
            .map(String::trim)
            .filter { it.isNotBlank() }
            .distinct()
            .forEach { medication ->
                episodeRepository.upsertMedication(
                    EpisodeMedication(
                        id = UUID.nameUUIDFromBytes("$episodeId:$medication".encodeToByteArray()).toString(),
                        episodeId = episodeId,
                        medicineName = medication,
                        takenAt = timeProvider.now(),
                        source = "voice-or-manual",
                    ),
                )
            }
    }

    private fun mergeTranscript(history: List<String>, preview: String): String =
        (history + preview.takeIf { it.isNotBlank() })
            .joinToString("\n")
            .trim()

    private fun mergeTextBlocks(existing: String, incoming: String): String =
        (existing.lines() + incoming.lines())
            .map(String::trim)
            .filter { it.isNotBlank() }
            .distinct()
            .joinToString("\n")
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
        onNotesTextChanged = viewModel::setNotesText,
        onVoicePreviewChanged = viewModel::updateLiveTranscriptPreview,
        onVoiceSegmentCommitted = viewModel::commitVoiceTranscriptSegment,
        onClearVoicePreview = viewModel::clearLiveTranscriptPreview,
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
    onNotesTextChanged: (String) -> Unit,
    onVoicePreviewChanged: (String) -> Unit,
    onVoiceSegmentCommitted: (String) -> Unit,
    onClearVoicePreview: () -> Unit,
    onToggleVoiceRecording: () -> Unit,
    onVoiceError: (String?) -> Unit,
    onSave: () -> Unit,
    onBack: () -> Unit,
    onHome: () -> Unit,
) {
    val context = LocalContext.current
    val localeTag = context.resources.configuration.locales[0]?.toLanguageTag() ?: "ru-RU"
    var permissionDenied by remember { mutableStateOf(false) }
    var isVoiceSessionActive by remember { mutableStateOf(false) }
    var pendingMicrophoneAction by remember { mutableStateOf<PendingMicrophoneAction?>(null) }
    var speechRecognizer by remember { mutableStateOf<SpeechRecognizer?>(null) }

    fun disposeSpeechRecognizer() {
        speechRecognizer?.destroy()
        speechRecognizer = null
    }

    fun stopVoiceSession(stopAudioRecording: Boolean) {
        isVoiceSessionActive = false
        speechRecognizer?.stopListening()
        disposeSpeechRecognizer()
        onClearVoicePreview()
        if (stopAudioRecording && state.isRecording) {
            onToggleVoiceRecording()
        }
    }

    fun startLiveRecognizer() {
        if (!SpeechRecognizer.isRecognitionAvailable(context)) {
            onVoiceError(context.getString(R.string.quicklog_dictation_unavailable))
            return
        }
        disposeSpeechRecognizer()
        val recognizer = runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && SpeechRecognizer.isOnDeviceRecognitionAvailable(context)) {
                SpeechRecognizer.createOnDeviceSpeechRecognizer(context)
            } else {
                SpeechRecognizer.createSpeechRecognizer(context)
            }
        }.getOrElse {
            onVoiceError(context.getString(R.string.quicklog_dictation_unavailable))
            return
        }
        speechRecognizer = recognizer
        recognizer.setRecognitionListener(
            object : RecognitionListener {
                override fun onReadyForSpeech(params: Bundle?) = Unit

                override fun onBeginningOfSpeech() = Unit

                override fun onRmsChanged(rmsdB: Float) = Unit

                override fun onBufferReceived(buffer: ByteArray?) = Unit

                override fun onEndOfSpeech() = Unit

                override fun onError(error: Int) {
                    if (!isVoiceSessionActive) {
                        disposeSpeechRecognizer()
                        return
                    }
                    when (error) {
                        SpeechRecognizer.ERROR_NO_MATCH,
                        SpeechRecognizer.ERROR_SPEECH_TIMEOUT,
                        SpeechRecognizer.ERROR_CLIENT
                        -> {
                            startLiveRecognizer()
                        }

                        SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> {
                            permissionDenied = true
                            stopVoiceSession(stopAudioRecording = true)
                            onVoiceError(context.getString(R.string.quicklog_audio_permission_required))
                        }

                        else -> {
                            stopVoiceSession(stopAudioRecording = true)
                            onVoiceError(context.getString(R.string.quicklog_voice_error_code, error))
                        }
                    }
                }

                override fun onResults(results: Bundle?) {
                    val transcript = results
                        ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                        ?.firstOrNull()
                        ?.trim()
                        .orEmpty()
                    if (transcript.isNotBlank()) {
                        onVoiceSegmentCommitted(transcript)
                    }
                    if (isVoiceSessionActive) {
                        startLiveRecognizer()
                    } else {
                        disposeSpeechRecognizer()
                    }
                }

                override fun onPartialResults(partialResults: Bundle?) {
                    val transcript = partialResults
                        ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                        ?.firstOrNull()
                        ?.trim()
                        .orEmpty()
                    onVoicePreviewChanged(transcript)
                }

                override fun onEvent(eventType: Int, params: Bundle?) = Unit
            },
        )
        onVoiceError(null)
        recognizer.startListening(buildLiveDictationIntent(context, localeTag))
    }

    DisposableEffect(context) {
        onDispose {
            stopVoiceSession(stopAudioRecording = true)
        }
    }

    val audioPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
    ) { granted ->
        permissionDenied = !granted
        val pendingAction = pendingMicrophoneAction
        pendingMicrophoneAction = null
        if (granted && pendingAction == PendingMicrophoneAction.VOICE_FILL) {
            if (!state.isRecording) {
                onToggleVoiceRecording()
            }
            isVoiceSessionActive = true
            startLiveRecognizer()
        }
    }

    fun ensureMicrophonePermission(onGranted: () -> Unit) {
        val hasPermission =
            ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
        if (hasPermission) {
            permissionDenied = false
            pendingMicrophoneAction = null
            onGranted()
        } else {
            pendingMicrophoneAction = PendingMicrophoneAction.VOICE_FILL
            audioPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
        }
    }

    fun handleVoiceSessionButton() {
        if (isVoiceSessionActive) {
            stopVoiceSession(stopAudioRecording = true)
        } else {
            ensureMicrophonePermission {
                if (!state.isRecording) {
                    onToggleVoiceRecording()
                }
                isVoiceSessionActive = true
                startLiveRecognizer()
            }
        }
    }

    val transcriptFeed = (state.transcriptHistory + state.liveTranscriptPreview.takeIf { it.isNotBlank() })
        .filterNotNull()
        .takeLast(5)
    val symptomOptions = (DefaultSymptoms + state.detectedSymptoms).distinct()

    Scaffold(
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
                        onClick = onSave,
                        colors = headacheInsightActionButtonColors(),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(stringResource(R.string.quicklog_save_now))
                    }
                    BottomMenuActions(
                        onBack = onBack,
                        onHome = onHome,
                    )
                    Button(
                        onClick = ::handleVoiceSessionButton,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(58.dp),
                    ) {
                        Text(
                            if (isVoiceSessionActive) {
                                stringResource(R.string.quicklog_voice_stop)
                            } else {
                                stringResource(R.string.quicklog_voice_start)
                            },
                        )
                    }
                }
            }
        },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
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
                VoiceSessionStatus(
                    isListening = isVoiceSessionActive,
                    isProcessing = state.isVoiceProcessing,
                )
                SeveritySlider(severity = state.severity, onSeverityChanged = onSeverityChanged)
            }

            HeadacheInsightSectionCard(
                title = stringResource(R.string.quicklog_live_title),
                supportingText = stringResource(R.string.quicklog_live_subtitle),
            ) {
                if (transcriptFeed.isEmpty()) {
                    Text(stringResource(R.string.quicklog_live_empty))
                } else {
                    transcriptFeed.forEachIndexed { index, item ->
                        Text(
                            text = item,
                            modifier = Modifier.alpha(
                                if (index == transcriptFeed.lastIndex && state.liveTranscriptPreview.isNotBlank()) 0.86f else 1f,
                            ),
                        )
                    }
                }
            }

            SelectableChipGroup(
                title = stringResource(R.string.quicklog_symptoms_title),
                options = symptomOptions,
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

            HeadacheInsightSectionCard(title = stringResource(R.string.quicklog_notes_title)) {
                OutlinedTextField(
                    value = state.notesText,
                    onValueChange = onNotesTextChanged,
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text(stringResource(R.string.quicklog_notes_label)) },
                    minLines = 2,
                )
                OutlinedTextField(
                    value = state.medicineNotes,
                    onValueChange = onMedicineNotesChanged,
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text(stringResource(R.string.quicklog_medication_label)) },
                    minLines = 2,
                )
            }

            state.voiceDraft?.let { voiceDraft ->
                HeadacheInsightSectionCard(
                    title = stringResource(R.string.quicklog_detected_title),
                    supportingText = stringResource(R.string.quicklog_detected_subtitle),
                ) {
                    voiceDraft.dynamicFields.forEach { field ->
                        KeyValueLine(label = field.label, value = field.value)
                    }
                    if (voiceDraft.liveNotes.isNotEmpty()) {
                        voiceDraft.liveNotes.forEach { note ->
                            Text(note)
                        }
                    }
                }
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
        }
    }
}

@Composable
private fun VoiceSessionStatus(
    isListening: Boolean,
    isProcessing: Boolean,
) {
    val label = when {
        isListening && isProcessing -> stringResource(R.string.quicklog_voice_status_listening_processing)
        isListening -> stringResource(R.string.quicklog_voice_status_listening)
        isProcessing -> stringResource(R.string.quicklog_voice_status_processing)
        else -> stringResource(R.string.quicklog_voice_status_idle)
    }
    val color = when {
        isListening -> HeadacheInsightStatusColors.LocalComplete
        isProcessing -> HeadacheInsightStatusColors.CloudAnalyzed
        else -> HeadacheInsightStatusColors.Queued
    }
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        PulsingDot(active = isListening, color = HeadacheInsightStatusColors.LocalComplete)
        HeadacheInsightStatusBadge(label = label, color = color)
    }
}

@Composable
private fun PulsingDot(
    active: Boolean,
    color: Color,
) {
    val transition = rememberInfiniteTransition(label = "voice-dot")
    val alpha by transition.animateFloat(
        initialValue = if (active) 0.35f else 1f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 900),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "voice-dot-alpha",
    )
    Box(
        modifier = Modifier
            .size(12.dp)
            .alpha(if (active) alpha else 0.55f)
            .background(color, CircleShape),
    )
}

@Composable
private fun RedFlagToggle(
    title: String,
    value: Boolean,
    onValueChange: (Boolean) -> Unit,
) {
    ToggleSectionCard(
        title = title,
        checked = value,
        onCheckedChange = onValueChange,
    )
}

private fun buildLiveDictationIntent(
    context: android.content.Context,
    localeTag: String,
): Intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
    putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
    putExtra(RecognizerIntent.EXTRA_LANGUAGE, localeTag)
    putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, localeTag)
    putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, true)
    putExtra(RecognizerIntent.EXTRA_PROMPT, context.getString(R.string.quicklog_dictation_prompt))
    putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 3)
    putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
}
