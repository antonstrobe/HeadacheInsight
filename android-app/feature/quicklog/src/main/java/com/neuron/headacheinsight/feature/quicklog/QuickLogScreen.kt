package com.neuron.headacheinsight.feature.quicklog

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.animateColorAsState
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
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
import com.neuron.headacheinsight.core.designsystem.preferredHorizontalAlignment
import com.neuron.headacheinsight.core.designsystem.preferredSpacedArrangement
import com.neuron.headacheinsight.core.designsystem.preferredTextAlign
import com.neuron.headacheinsight.core.model.Episode
import com.neuron.headacheinsight.core.model.EpisodeMedication
import com.neuron.headacheinsight.core.model.EpisodeSymptom
import com.neuron.headacheinsight.core.model.EpisodeTranscript
import com.neuron.headacheinsight.core.model.LocalRedFlagInput
import com.neuron.headacheinsight.core.model.TranscriptStatus
import com.neuron.headacheinsight.core.model.VoiceIntakeDraft
import com.neuron.headacheinsight.core.ui.BottomMenuActions
import com.neuron.headacheinsight.core.ui.SelectableChipGroup
import com.neuron.headacheinsight.core.ui.SeveritySlider
import com.neuron.headacheinsight.core.ui.ToggleSectionCard
import com.neuron.headacheinsight.domain.AnalysisRepository
import com.neuron.headacheinsight.domain.AudioRecorder
import com.neuron.headacheinsight.domain.CloudSpeechRecognizerEngine
import com.neuron.headacheinsight.domain.CreateEpisodeUseCase
import com.neuron.headacheinsight.domain.EpisodeRepository
import com.neuron.headacheinsight.domain.RedFlagEngine
import com.neuron.headacheinsight.domain.SaveQuickLogUseCase
import com.neuron.headacheinsight.domain.SaveTranscriptUseCase
import com.neuron.headacheinsight.domain.SyncScheduler
import com.neuron.headacheinsight.domain.VoiceIntakeRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import java.io.File
import java.io.RandomAccessFile
import java.util.UUID
import javax.inject.Inject
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
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
    val isPreparingFollowUp: Boolean = false,
    val preparedQuestionCount: Int? = null,
    val followUpErrorMessage: String? = null,
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
    private val cloudSpeechRecognizerEngine: CloudSpeechRecognizerEngine,
    private val syncScheduler: SyncScheduler,
    private val timeProvider: TimeProvider,
    private val voiceIntakeRepository: VoiceIntakeRepository,
    private val analysisRepository: AnalysisRepository,
) : androidx.lifecycle.ViewModel() {
    private val draft = MutableStateFlow(QuickLogUiState())
    private val transcriptForStructuring = MutableStateFlow("")
    private var cloudPreviewJob: Job? = null

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
                cloudPreviewJob?.cancel()
                cloudPreviewJob = null
                val currentState = state.value
                val result = audioRecorder.stop()
                val path = result.getOrNull()
                draft.emit(
                    currentState.copy(
                        isRecording = false,
                        lastAudioPath = path,
                        isVoiceProcessing = path != null && result.isSuccess,
                        voiceErrorMessage = result.exceptionOrNull()?.message,
                    ),
                )
                if (path != null && result.isSuccess) {
                    transcribeRecordedAudio(audioPath = path, episode = episode, prepareFollowUp = true)
                }
            } else {
                val result = audioRecorder.start(episode.id)
                val path = result.getOrNull()
                draft.emit(
                    state.value.copy(
                        isRecording = result.isSuccess,
                        lastAudioPath = path,
                        liveTranscriptPreview = "",
                        isVoiceProcessing = false,
                        isPreparingFollowUp = false,
                        preparedQuestionCount = null,
                        followUpErrorMessage = null,
                        voiceErrorMessage = result.exceptionOrNull()?.message,
                    ),
                )
                if (result.isSuccess && path != null) {
                    startCloudPreviewLoop(audioPath = path, episode = episode)
                }
            }
        }
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
            persistMedicationNotes(
                episodeId = episode.id,
                rawNotes = state.value.medicineNotes,
                additionalItems = state.value.voiceDraft?.medications.orEmpty(),
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

    private suspend fun applyVoiceDraft(voiceDraft: VoiceIntakeDraft) {
        val current = state.value
        val updated = current.copy(
            severity = voiceDraft.severity ?: current.severity,
            selectedSymptoms = current.selectedSymptoms + voiceDraft.symptoms,
            detectedSymptoms = current.detectedSymptoms + voiceDraft.symptoms,
            suddenWorstPain = current.suddenWorstPain || voiceDraft.redFlags.contains("suddenWorstPain"),
            confusion = current.confusion || voiceDraft.redFlags.contains("confusion"),
            speechDifficulty = current.speechDifficulty || voiceDraft.redFlags.contains("speechDifficulty"),
            oneSidedWeakness = current.oneSidedWeakness || voiceDraft.redFlags.contains("oneSidedWeakness"),
            notesText = mergeTextBlocks(current.notesText, buildVoiceNarrative(voiceDraft)),
            voiceDraft = voiceDraft,
            isVoiceProcessing = false,
            voiceErrorMessage = null,
        )
        draft.emit(updated)
        current.episode?.let { persistAutoFilledFields(it, updated) }
    }

    private suspend fun persistAutoFilledFields(
        episode: Episode,
        uiState: QuickLogUiState,
    ) {
        val now = timeProvider.now()
        val summaryText = uiState.notesText
            .ifBlank { uiState.voiceDraft?.summaryText.orEmpty() }
            .ifBlank { mergeTranscript(uiState.transcriptHistory, uiState.liveTranscriptPreview) }
            .takeIf { it.isNotBlank() }
        episodeRepository.updateEpisode(
            episode.copy(
                currentSeverity = uiState.severity,
                peakSeverity = maxOf(uiState.severity, episode.peakSeverity ?: uiState.severity),
                summaryText = summaryText ?: episode.summaryText,
                updatedAt = now,
            ),
        )
        episodeRepository.replaceSymptoms(
            episode.id,
            uiState.selectedSymptoms.map { symptom ->
                EpisodeSymptom(
                    episodeId = episode.id,
                    symptomCode = symptom,
                    intensity = uiState.severity,
                    present = true,
                )
            },
        )
        persistMedicationNotes(
            episodeId = episode.id,
            rawNotes = uiState.medicineNotes,
            additionalItems = uiState.voiceDraft?.medications.orEmpty(),
        )
    }

    private suspend fun persistMedicationNotes(
        episodeId: String,
        rawNotes: String,
        additionalItems: List<String> = emptyList(),
    ) {
        (rawNotes
            .split('\n', ',') + additionalItems)
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

    private fun buildVoiceNarrative(voiceDraft: VoiceIntakeDraft): String =
        listOf(
            voiceDraft.summaryText,
            voiceDraft.medications.joinToString("\n"),
            voiceDraft.liveNotes.joinToString("\n"),
        ).filter { it.isNotBlank() }
            .joinToString("\n")

    private suspend fun prepareFollowUpQuestions(episodeId: String) {
        draft.emit(
            state.value.copy(
                isPreparingFollowUp = true,
                followUpErrorMessage = null,
            ),
        )
        analysisRepository.generateFollowUpQuestions(episodeId).fold(
            onSuccess = { questions ->
                draft.emit(
                    state.value.copy(
                        isPreparingFollowUp = false,
                        preparedQuestionCount = questions.size.takeIf { it > 0 },
                        followUpErrorMessage = null,
                    ),
                )
            },
            onFailure = { error ->
                draft.emit(
                    state.value.copy(
                        isPreparingFollowUp = false,
                        followUpErrorMessage = error.message,
                    ),
                )
            },
        )
    }

    private fun mergeTranscript(history: List<String>, preview: String): String =
        (history + preview.takeIf { it.isNotBlank() })
            .joinToString("\n")
            .trim()

    private fun mergeTranscriptHistory(history: List<String>, incoming: String): List<String> {
        val normalized = incoming.trim()
        if (normalized.isBlank()) return history
        val items = history.toMutableList()
        val lastIndex = items.indexOfLast { isSameSegment(it, normalized) }
        if (lastIndex >= 0) {
            items[lastIndex] = pickMoreComplete(items[lastIndex], normalized)
        } else {
            items += normalized
        }
        return items
            .map(String::trim)
            .filter { it.isNotBlank() }
            .takeLast(6)
    }

    private fun isSameSegment(existing: String?, incoming: String): Boolean {
        val left = existing?.trim()?.lowercase().orEmpty()
        val right = incoming.trim().lowercase()
        if (left.isBlank() || right.isBlank()) return false
        return left == right || left.contains(right) || right.contains(left)
    }

    private fun pickMoreComplete(existing: String, incoming: String): String =
        if (incoming.trim().length >= existing.trim().length) incoming.trim() else existing.trim()

    private fun mergeTextBlocks(existing: String, incoming: String): String =
        (existing.lines() + incoming.lines())
            .map(String::trim)
            .filter { it.isNotBlank() }
            .distinct()
            .joinToString("\n")

    private fun startCloudPreviewLoop(
        audioPath: String,
        episode: Episode,
    ) {
        cloudPreviewJob?.cancel()
        cloudPreviewJob = viewModelScope.launch {
            delay(4_000)
            while (state.value.isRecording && state.value.lastAudioPath == audioPath) {
                transcribeRecordingPreview(audioPath = audioPath, episode = episode)
                delay(5_000)
            }
        }
    }

    private suspend fun transcribeRecordingPreview(
        audioPath: String,
        episode: Episode,
    ) {
        val snapshotPath = createAudioSnapshot(audioPath) ?: return
        draft.emit(state.value.copy(isVoiceProcessing = true, voiceErrorMessage = null))
        try {
            cloudSpeechRecognizerEngine.transcribeAudio(snapshotPath, episode.locale).fold(
                onSuccess = { transcript ->
                    if (!state.value.isRecording || state.value.lastAudioPath != audioPath) {
                        return@fold
                    }
                    val normalized = transcript.transcriptText?.trim().orEmpty()
                    if (normalized.isBlank()) {
                        draft.emit(state.value.copy(isVoiceProcessing = false))
                        return@fold
                    }
                    val current = state.value
                    draft.emit(
                        current.copy(
                            liveTranscriptPreview = normalized,
                            isVoiceProcessing = false,
                            voiceErrorMessage = null,
                        ),
                    )
                    transcriptForStructuring.emit(mergeTranscript(current.transcriptHistory, normalized))
                },
                onFailure = {
                    if (state.value.isRecording && state.value.lastAudioPath == audioPath) {
                        draft.emit(state.value.copy(isVoiceProcessing = false))
                    }
                },
            )
        } finally {
            File(snapshotPath).delete()
        }
    }

    private fun createAudioSnapshot(sourcePath: String): String? {
        val source = File(sourcePath)
        if (!source.exists() || source.length() <= 44L) return null
        val target = File.createTempFile("voice-preview-", ".wav", source.parentFile)
        source.copyTo(target, overwrite = true)
        repairWavHeader(target)
        return target.absolutePath
    }

    private fun repairWavHeader(file: File) {
        RandomAccessFile(file, "rw").use { raf ->
            val fileLength = raf.length().toInt()
            if (fileLength <= 44) return
            raf.seek(4)
            raf.writeInt(Integer.reverseBytes(fileLength - 8))
            raf.seek(40)
            raf.writeInt(Integer.reverseBytes(fileLength - 44))
        }
    }

    private suspend fun transcribeRecordedAudio(
        audioPath: String,
        episode: Episode,
        prepareFollowUp: Boolean,
    ) {
        draft.emit(state.value.copy(isVoiceProcessing = true, voiceErrorMessage = null))
        cloudSpeechRecognizerEngine.transcribeAudio(audioPath, episode.locale).fold(
            onSuccess = { transcript ->
                val normalized = transcript.transcriptText?.trim().orEmpty()
                if (normalized.isBlank()) {
                    draft.emit(
                        state.value.copy(
                            isVoiceProcessing = false,
                            voiceErrorMessage = localizedMessage(
                                locale = episode.locale,
                                ru = "OpenAI вернул пустую расшифровку.",
                                en = "OpenAI returned an empty transcript.",
                            ),
                        ),
                    )
                    syncScheduler.enqueueTranscription(audioPath, episode.id)
                    return@fold
                }
                val now = timeProvider.now()
                episodeRepository.updateEpisode(
                    episode.copy(
                        summaryText = normalized,
                        transcriptStatus = TranscriptStatus.MERGED_READY,
                        updatedAt = now,
                    ),
                )
                saveTranscriptUseCase(
                    transcript.copy(
                        episodeId = episode.id,
                        rawAudioPath = audioPath,
                        language = transcript.language ?: episode.locale,
                        createdAt = now,
                    ),
                    shouldCloudRetry = false,
                )
                draft.emit(
                    state.value.copy(
                        transcriptHistory = mergeTranscriptHistory(state.value.transcriptHistory, normalized),
                        liveTranscriptPreview = "",
                        lastTranscriptText = normalized,
                        lastAudioPath = audioPath,
                        isVoiceProcessing = false,
                        voiceErrorMessage = null,
                    ),
                )
                transcriptForStructuring.emit(mergeTranscript(state.value.transcriptHistory, ""))
                if (prepareFollowUp) {
                    prepareFollowUpQuestions(episode.id)
                }
            },
            onFailure = { error ->
                draft.emit(
                    state.value.copy(
                        isVoiceProcessing = false,
                        voiceErrorMessage = error.message,
                    ),
                )
                syncScheduler.enqueueTranscription(audioPath, episode.id)
            },
        )
    }

    private fun localizedMessage(
        locale: String,
        ru: String,
        en: String,
    ): String = if (locale.startsWith("ru", ignoreCase = true)) ru else en
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
    onToggleVoiceRecording: () -> Unit,
    onVoiceError: (String?) -> Unit,
    onSave: () -> Unit,
    onBack: () -> Unit,
    onHome: () -> Unit,
) {
    val context = LocalContext.current
    val currentState by rememberUpdatedState(state)
    var permissionDenied by remember { mutableStateOf(false) }
    val isVoiceSessionActive = state.isRecording
    val voiceLevel = if (state.isRecording) 0.7f else 0f

    DisposableEffect(Unit) {
        onDispose {
            if (currentState.isRecording) {
                onToggleVoiceRecording()
            }
        }
    }

    val audioPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
    ) { granted ->
        permissionDenied = !granted
        if (granted) {
            onVoiceError(null)
            onToggleVoiceRecording()
        }
    }

    fun ensureMicrophonePermission(onGranted: () -> Unit) {
        val hasPermission =
            ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
        if (hasPermission) {
            permissionDenied = false
            onGranted()
        } else {
            audioPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
        }
    }

    fun handleVoiceSessionButton() {
        if (state.isRecording) {
            onToggleVoiceRecording()
        } else {
            ensureMicrophonePermission {
                onVoiceError(null)
                onToggleVoiceRecording()
            }
        }
    }

    val transcriptFeed = (state.transcriptHistory + state.liveTranscriptPreview.takeIf { it.isNotBlank() })
        .filterNotNull()
        .distinct()
        .takeLast(6)
    val currentLiveText = state.liveTranscriptPreview.ifBlank {
        transcriptFeed.lastOrNull().orEmpty()
    }
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
                    horizontalAlignment = preferredHorizontalAlignment(),
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
                        enabled = state.isRecording || !state.isVoiceProcessing,
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
            horizontalAlignment = preferredHorizontalAlignment(),
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
                    modifier = Modifier.fillMaxWidth(),
                    textAlign = preferredTextAlign(),
                )
                VoiceSessionStatus(
                    isListening = isVoiceSessionActive,
                    isProcessing = state.isVoiceProcessing || state.isPreparingFollowUp,
                )
                VoiceActivityVisualizer(
                    isListening = isVoiceSessionActive,
                    isProcessing = state.isVoiceProcessing || state.isPreparingFollowUp,
                    level = voiceLevel,
                )
                LiveTranscriptHero(
                    transcript = currentLiveText,
                    isListening = isVoiceSessionActive,
                )
                if (state.isPreparingFollowUp) {
                    HeadacheInsightStatusBadge(
                        label = stringResource(R.string.quicklog_follow_up_preparing),
                        color = HeadacheInsightStatusColors.CloudAnalyzed,
                    )
                }
                state.preparedQuestionCount?.let {
                    HeadacheInsightStatusBadge(
                        label = stringResource(R.string.quicklog_follow_up_ready, it),
                        color = HeadacheInsightStatusColors.CloudAnalyzed,
                    )
                    Text(
                        text = stringResource(R.string.quicklog_follow_up_hint),
                        modifier = Modifier.fillMaxWidth(),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.outline,
                        textAlign = preferredTextAlign(),
                    )
                }
                state.followUpErrorMessage?.let {
                    Text(
                        text = stringResource(R.string.quicklog_follow_up_error, it),
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.fillMaxWidth(),
                        textAlign = preferredTextAlign(),
                    )
                }
                SeveritySlider(severity = state.severity, onSeverityChanged = onSeverityChanged)
            }

            HeadacheInsightSectionCard(
                title = stringResource(R.string.quicklog_live_title),
                supportingText = stringResource(R.string.quicklog_live_subtitle),
            ) {
                if (transcriptFeed.isEmpty()) {
                    Text(
                        text = stringResource(R.string.quicklog_live_empty),
                        modifier = Modifier.fillMaxWidth(),
                        textAlign = preferredTextAlign(),
                    )
                } else {
                    transcriptFeed.forEachIndexed { index, item ->
                        Text(
                            text = item,
                            modifier = Modifier
                                .fillMaxWidth()
                                .alpha(
                                    if (index == transcriptFeed.lastIndex && state.liveTranscriptPreview.isNotBlank()) 0.86f else 1f,
                                ),
                            style = if (index == transcriptFeed.lastIndex) {
                                MaterialTheme.typography.bodyLarge
                            } else {
                                MaterialTheme.typography.bodyMedium
                            },
                            textAlign = preferredTextAlign(),
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
                    textStyle = MaterialTheme.typography.bodyLarge.copy(textAlign = preferredTextAlign()),
                    label = { Text(stringResource(R.string.quicklog_notes_label)) },
                    minLines = 2,
                )
                OutlinedTextField(
                    value = state.medicineNotes,
                    onValueChange = onMedicineNotesChanged,
                    modifier = Modifier.fillMaxWidth(),
                    textStyle = MaterialTheme.typography.bodyLarge.copy(textAlign = preferredTextAlign()),
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
                            Text(
                                text = note,
                                modifier = Modifier.fillMaxWidth(),
                                textAlign = preferredTextAlign(),
                            )
                        }
                    }
                }
            }

            if (permissionDenied) {
                Text(
                    text = stringResource(R.string.quicklog_audio_permission_required),
                    modifier = Modifier.fillMaxWidth(),
                    textAlign = preferredTextAlign(),
                )
            }
            state.lastAudioPath?.let {
                Text(
                    text = stringResource(R.string.quicklog_audio_saved, it),
                    modifier = Modifier.fillMaxWidth(),
                    textAlign = preferredTextAlign(),
                )
            }
            state.lastTranscriptText?.let {
                Text(
                    text = stringResource(R.string.quicklog_dictation_saved, it),
                    modifier = Modifier.fillMaxWidth(),
                    textAlign = preferredTextAlign(),
                )
            }
            state.voiceErrorMessage?.let {
                Text(
                    text = stringResource(R.string.quicklog_voice_error, it),
                    modifier = Modifier.fillMaxWidth(),
                    color = MaterialTheme.colorScheme.error,
                    textAlign = preferredTextAlign(),
                )
            }
            if (state.urgentMessage.isNotBlank()) {
                Text(
                    text = state.urgentMessage,
                    modifier = Modifier.fillMaxWidth(),
                    textAlign = preferredTextAlign(),
                )
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
        isListening -> HeadacheInsightStatusColors.CloudAnalyzed
        isProcessing -> HeadacheInsightStatusColors.CloudAnalyzed
        else -> HeadacheInsightStatusColors.Queued
    }
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = preferredSpacedArrangement(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        PulsingDot(active = isListening, color = HeadacheInsightStatusColors.CloudAnalyzed)
        HeadacheInsightStatusBadge(label = label, color = color)
    }
}

@Composable
private fun LiveTranscriptHero(
    transcript: String,
    isListening: Boolean,
) {
    HeadacheInsightSectionCard(
        title = stringResource(R.string.quicklog_live_now_title),
        supportingText = stringResource(R.string.quicklog_live_now_subtitle),
    ) {
        Text(
            text = transcript.ifBlank { stringResource(R.string.quicklog_live_empty) },
            style = MaterialTheme.typography.headlineSmall,
            color = if (transcript.isBlank()) {
                MaterialTheme.colorScheme.outline
            } else {
                MaterialTheme.colorScheme.onSurface
            },
            modifier = Modifier
                .fillMaxWidth()
                .alpha(if (isListening && transcript.isNotBlank()) 0.96f else 1f),
            textAlign = preferredTextAlign(),
        )
    }
}

@Composable
private fun VoiceActivityVisualizer(
    isListening: Boolean,
    isProcessing: Boolean,
    level: Float,
) {
    val transition = rememberInfiniteTransition(label = "voice-bars")
    val pulse by transition.animateFloat(
        initialValue = 0.2f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 900),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "voice-bars-pulse",
    )
    val color by animateColorAsState(
        targetValue = when {
            isListening -> HeadacheInsightStatusColors.CloudAnalyzed
            isProcessing -> HeadacheInsightStatusColors.CloudAnalyzed
            else -> HeadacheInsightStatusColors.Queued
        },
        label = "voice-bars-color",
    )
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.Bottom,
    ) {
        repeat(5) { index ->
            val activity = when {
                isListening -> ((level * 0.72f) + (pulse * (0.28f + index * 0.03f))).coerceIn(0.18f, 1f)
                isProcessing -> (0.26f + pulse * 0.25f).coerceIn(0.2f, 0.7f)
                else -> 0.16f
            }
            Box(
                modifier = Modifier
                    .width(10.dp)
                    .height((18 + activity * 28).dp)
                    .background(
                        color = color.copy(alpha = 0.26f + activity * 0.58f),
                        shape = RoundedCornerShape(14.dp),
                    ),
            )
        }
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
