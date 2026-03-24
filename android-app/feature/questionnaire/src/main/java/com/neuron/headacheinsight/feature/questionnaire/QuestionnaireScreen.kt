package com.neuron.headacheinsight.feature.questionnaire

import android.Manifest
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.content.Intent
import android.content.pm.PackageManager
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
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
import java.util.Locale
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
    val voiceLanguageTag = state.episodeDetail?.episode?.locale
        ?.takeIf(String::isNotBlank)
        ?: Locale.getDefault().toLanguageTag()

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
                    voiceLanguageTag = voiceLanguageTag,
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
    voiceLanguageTag: String,
    onSaveAnswer: (String, JsonElement) -> Unit,
) {
    val context = LocalContext.current
    var textValue by remember(question.id) { mutableStateOf("") }
    var booleanValue by remember(question.id) { mutableStateOf<Boolean?>(null) }
    var singleValue by remember(question.id) { mutableStateOf<String?>(null) }
    val multiValue = remember(question.id) { mutableStateListOf<String>() }
    var scaleValue by remember(question.id) { mutableStateOf(5f) }
    var voiceError by remember(question.id) { mutableStateOf<String?>(null) }
    var voiceSessionActive by remember(question.id) { mutableStateOf(false) }
    var voiceListening by remember(question.id) { mutableStateOf(false) }
    var voiceReconnectAttempts by remember(question.id) { mutableStateOf(0) }
    var voiceStopRequested by remember(question.id) { mutableStateOf(false) }
    var voiceRecognizer by remember(question.id) { mutableStateOf<SpeechRecognizer?>(null) }
    var voiceLiveSegment by remember(question.id) { mutableStateOf("") }
    val voiceSegments = remember(question.id) { mutableStateListOf<String>() }
    val voiceHandler = remember(question.id) { Handler(Looper.getMainLooper()) }

    val voiceFillLabel = stringResource(R.string.questionnaire_voice_fill)
    val voiceStopLabel = stringResource(R.string.questionnaire_voice_stop)
    val voiceListeningLabel = stringResource(R.string.questionnaire_voice_listening)
    val voiceTranscriptLabel = stringResource(R.string.questionnaire_voice_transcript_label)
    val voicePermissionRequired = stringResource(R.string.questionnaire_voice_permission_required)
    val voiceUnavailable = stringResource(R.string.questionnaire_voice_unavailable)
    val voiceEmptyResult = stringResource(R.string.questionnaire_voice_empty_result)
    val voiceDisconnected = stringResource(R.string.questionnaire_voice_service_disconnected)
    val voiceBooleanError = stringResource(R.string.questionnaire_voice_boolean_error)
    val voiceOptionError = stringResource(R.string.questionnaire_voice_option_error)
    val voiceNumberError = stringResource(R.string.questionnaire_voice_number_error)
    val voiceScaleError = stringResource(R.string.questionnaire_voice_scale_error)
    val voiceErrorCode = stringResource(R.string.questionnaire_voice_error_code)
    val optionLabels = remember(question.options, voiceLanguageTag) {
        question.options.associateWith { option -> localizedQuestionOptionLabel(option, voiceLanguageTag) }
    }
    val hasPrimaryTextInput = when (question.answerType) {
        AnswerType.NUMBER,
        AnswerType.DATE_TIME,
        AnswerType.DURATION,
        AnswerType.FREE_TEXT,
        AnswerType.MEDICATION_LIST,
        AnswerType.ATTACHMENT_REQUEST,
        AnswerType.VOICE_NOTE -> true
        AnswerType.SINGLE_SELECT,
        AnswerType.MULTI_SELECT -> question.options.isEmpty()
        else -> false
    }
    val voiceTranscriptText = mergeVoiceTranscript(voiceSegments, voiceLiveSegment)
    val showVoiceTranscriptField = question.voiceAllowed && (!hasPrimaryTextInput || voiceTranscriptText.isNotBlank() || voiceSessionActive)

    fun destroyVoiceRecognizer() {
        voiceHandler.removeCallbacksAndMessages(null)
        voiceRecognizer?.cancel()
        voiceRecognizer?.destroy()
        voiceRecognizer = null
    }

    DisposableEffect(question.id) {
        onDispose {
            voiceHandler.removeCallbacksAndMessages(null)
            voiceRecognizer?.cancel()
            voiceRecognizer?.destroy()
        }
    }

    val canSave = when (question.answerType) {
        AnswerType.BOOLEAN -> booleanValue != null || !question.required
        AnswerType.SINGLE_SELECT -> {
            if (question.options.isEmpty()) textValue.isNotBlank() || !question.required else singleValue != null || !question.required
        }
        AnswerType.MULTI_SELECT -> {
            if (question.options.isEmpty()) textValue.isNotBlank() || !question.required else multiValue.isNotEmpty() || !question.required
        }
        AnswerType.SCALE -> true
        else -> textValue.isNotBlank() || !question.required
    }

    fun saveCurrentAnswer() {
        val payload = when (question.answerType) {
            AnswerType.BOOLEAN -> booleanValue?.let(::JsonPrimitive) ?: JsonNull
            AnswerType.SCALE -> JsonPrimitive(scaleValue.toInt())
            AnswerType.SINGLE_SELECT -> {
                if (question.options.isEmpty()) {
                    textValue.trim().takeIf(String::isNotBlank)?.let(::JsonPrimitive) ?: JsonNull
                } else {
                    singleValue?.let(::JsonPrimitive) ?: JsonNull
                }
            }
            AnswerType.MULTI_SELECT -> {
                if (question.options.isEmpty()) {
                    JsonArray(splitVoiceListItems(textValue).map(::JsonPrimitive))
                } else {
                    JsonArray(multiValue.distinct().map(::JsonPrimitive))
                }
            }
            AnswerType.NUMBER -> textValue.toIntOrNull()?.let(::JsonPrimitive) ?: JsonPrimitive(textValue)
            else -> JsonPrimitive(textValue.trim())
        }
        onSaveAnswer(question.id, payload)
    }

    fun applyVoiceTranscript(
        rawTranscript: String,
        isPartial: Boolean = false,
    ): String? {
        val transcript = rawTranscript.trim()
        if (transcript.isBlank()) return if (isPartial) null else voiceEmptyResult

        return when (question.answerType) {
            AnswerType.BOOLEAN -> {
                val parsed = parseBooleanVoiceAnswer(transcript)
                if (parsed == null) {
                    if (isPartial) null else voiceBooleanError
                } else {
                    booleanValue = parsed
                    null
                }
            }

            AnswerType.SCALE -> {
                val parsed = parseIntegerVoiceAnswer(transcript)?.takeIf { it in 0..10 }
                if (parsed == null) {
                    if (isPartial) null else voiceScaleError
                } else {
                    scaleValue = parsed.toFloat()
                    null
                }
            }

            AnswerType.SINGLE_SELECT -> {
                if (question.options.isEmpty()) {
                    textValue = transcript
                    singleValue = transcript
                    null
                } else {
                    val matched = matchSingleVoiceOption(transcript, question.options, voiceLanguageTag)
                    if (matched == null) {
                        if (isPartial) null else voiceOptionError
                    } else {
                        singleValue = matched
                        null
                    }
                }
            }

            AnswerType.MULTI_SELECT -> {
                if (question.options.isEmpty()) {
                    textValue = transcript
                    null
                } else {
                    val matched = matchMultiVoiceOptions(transcript, question.options, voiceLanguageTag)
                    if (matched.isEmpty()) {
                        if (isPartial) null else voiceOptionError
                    } else {
                        multiValue.clear()
                        multiValue.addAll(matched)
                        null
                    }
                }
            }

            AnswerType.NUMBER -> {
                val parsed = parseIntegerVoiceAnswer(transcript)
                textValue = parsed?.toString() ?: transcript
                if (!isPartial && parsed == null && transcript.none(Char::isDigit)) {
                    voiceNumberError
                } else {
                    null
                }
            }

            else -> {
                textValue = transcript
                null
            }
        }
    }

    fun applyCombinedVoiceTranscript(isPartial: Boolean): String? =
        applyVoiceTranscript(mergeVoiceTranscript(voiceSegments, voiceLiveSegment), isPartial = isPartial)

    fun updateVoiceTranscriptManually(value: String) {
        voiceLiveSegment = ""
        voiceSegments.clear()
        value.trim().takeIf(String::isNotBlank)?.let(voiceSegments::add)
        voiceError = if (value.isBlank()) null else applyCombinedVoiceTranscript(isPartial = false)
    }

    fun stopVoiceFill() {
        voiceStopRequested = true
        voiceSessionActive = false
        voiceListening = false
        voiceReconnectAttempts = 0
        voiceLiveSegment = ""
        destroyVoiceRecognizer()
    }

    lateinit var launchVoiceRecognition: () -> Unit

    val scheduleVoiceRestart: (Long) -> Unit = { delayMillis ->
        voiceHandler.removeCallbacksAndMessages(null)
        voiceHandler.postDelayed(
            {
                if (!voiceSessionActive || voiceStopRequested) return@postDelayed
                launchVoiceRecognition()
            },
            delayMillis,
        )
    }

    launchVoiceRecognition = launch@{
        val useOnDeviceRecognizer = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
            SpeechRecognizer.isOnDeviceRecognitionAvailable(context)
        val recognitionAvailable = useOnDeviceRecognizer || SpeechRecognizer.isRecognitionAvailable(context)
        if (!recognitionAvailable) {
            voiceSessionActive = false
            voiceListening = false
            voiceError = voiceUnavailable
            return@launch
        }

        destroyVoiceRecognizer()
        val recognizer = runCatching {
            if (useOnDeviceRecognizer) {
                SpeechRecognizer.createOnDeviceSpeechRecognizer(context)
            } else {
                SpeechRecognizer.createSpeechRecognizer(context)
            }
        }.getOrElse {
            voiceSessionActive = false
            voiceListening = false
            voiceError = voiceUnavailable
            return@launch
        }

        voiceRecognizer = recognizer
        voiceSessionActive = true
        voiceListening = true
        voiceStopRequested = false
        voiceError = null

        recognizer.setRecognitionListener(
            object : RecognitionListener {
                override fun onReadyForSpeech(params: Bundle?) {
                    voiceReconnectAttempts = 0
                }

                override fun onBeginningOfSpeech() = Unit

                override fun onRmsChanged(rmsdB: Float) = Unit

                override fun onBufferReceived(buffer: ByteArray?) = Unit

                override fun onEndOfSpeech() {
                    voiceListening = false
                }

                override fun onError(error: Int) {
                    if (voiceStopRequested || error == SpeechRecognizer.ERROR_CLIENT) {
                        voiceStopRequested = false
                        voiceSessionActive = false
                        voiceListening = false
                        voiceReconnectAttempts = 0
                        destroyVoiceRecognizer()
                        return
                    }
                    val canRestart =
                        voiceSessionActive &&
                            shouldRestartVoiceRecognition(error) &&
                            (error != SpeechRecognizer.ERROR_SERVER_DISCONNECTED ||
                                voiceReconnectAttempts < MaxVoiceReconnectAttempts)
                    if (canRestart) {
                        voiceListening = false
                        voiceError = null
                        if (error == SpeechRecognizer.ERROR_SERVER_DISCONNECTED) {
                            voiceReconnectAttempts += 1
                        }
                        destroyVoiceRecognizer()
                        scheduleVoiceRestart(
                            if (error == SpeechRecognizer.ERROR_SERVER_DISCONNECTED) {
                                VoiceReconnectDelayMillis
                            } else {
                                VoiceRestartDelayMillis
                            },
                        )
                        return
                    }
                    voiceSessionActive = false
                    voiceListening = false
                    voiceReconnectAttempts = 0
                    voiceError = mapVoiceRecognitionError(
                        errorCode = error,
                        unavailableLabel = voiceUnavailable,
                        emptyResultLabel = voiceEmptyResult,
                        disconnectedLabel = voiceDisconnected,
                        errorCodeLabel = voiceErrorCode,
                    )
                    destroyVoiceRecognizer()
                }

                override fun onResults(results: Bundle?) {
                    val transcript = extractVoiceTranscript(results)
                    voiceReconnectAttempts = 0
                    voiceLiveSegment = ""
                    if (transcript.isNotBlank()) {
                        val updatedSegments = mergeVoiceSegments(voiceSegments, transcript)
                        voiceSegments.clear()
                        voiceSegments.addAll(updatedSegments)
                    }
                    voiceError = applyCombinedVoiceTranscript(isPartial = false)
                    if (voiceSessionActive && !voiceStopRequested) {
                        voiceListening = false
                        destroyVoiceRecognizer()
                        scheduleVoiceRestart(VoiceRestartDelayMillis)
                    } else {
                        voiceSessionActive = false
                        voiceListening = false
                        voiceReconnectAttempts = 0
                        destroyVoiceRecognizer()
                    }
                }

                override fun onPartialResults(partialResults: Bundle?) {
                    voiceLiveSegment = extractVoiceTranscript(partialResults)
                    voiceError = applyCombinedVoiceTranscript(isPartial = true)
                }

                override fun onEvent(eventType: Int, params: Bundle?) = Unit
            },
        )

        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, voiceLanguageTag)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, voiceLanguageTag)
            putExtra(RecognizerIntent.EXTRA_PROMPT, question.prompt)
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, useOnDeviceRecognizer)
            putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, 1_500L)
            putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_POSSIBLY_COMPLETE_SILENCE_LENGTH_MILLIS, 1_000L)
            putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_MINIMUM_LENGTH_MILLIS, 30_000L)
        }

        runCatching { recognizer.startListening(intent) }
            .onFailure {
                voiceSessionActive = false
                voiceListening = false
                voiceError = voiceUnavailable
                destroyVoiceRecognizer()
            }
    }

    val voicePermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
    ) { granted ->
        if (granted) {
            voiceError = null
            voiceStopRequested = false
            launchVoiceRecognition()
        } else {
            voiceError = voicePermissionRequired
        }
    }

    fun startVoiceFill() {
        if (voiceSessionActive) {
            stopVoiceFill()
            return
        }
        val hasPermission =
            ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
        if (hasPermission) {
            voiceError = null
            voiceReconnectAttempts = 0
            voiceStopRequested = false
            launchVoiceRecognition()
        } else {
            voicePermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
        }
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
            if (question.voiceAllowed && voiceSessionActive) {
                HeadacheInsightStatusBadge(
                    label = voiceListeningLabel,
                    color = HeadacheInsightStatusColors.CloudAnalyzed,
                )
            }
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
                    onSelect = {
                        booleanValue = it
                        voiceError = null
                    },
                )
            }

            AnswerType.SCALE -> {
                Text(stringResource(R.string.questionnaire_scale_value, scaleValue.toInt()))
                Slider(
                    value = scaleValue,
                    onValueChange = {
                        scaleValue = it
                        voiceError = null
                    },
                    valueRange = 0f..10f,
                    steps = 9,
                )
            }

            AnswerType.SINGLE_SELECT -> {
                if (question.options.isEmpty()) {
                    OutlinedTextField(
                        value = textValue,
                        onValueChange = {
                            textValue = it
                            singleValue = it.takeIf(String::isNotBlank)
                            voiceError = null
                        },
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
                                onClick = {
                                    singleValue = option
                                    voiceError = null
                                },
                                label = { Text(optionLabels[option] ?: option) },
                            )
                        }
                    }
                }
            }

            AnswerType.MULTI_SELECT -> {
                if (question.options.isEmpty()) {
                    OutlinedTextField(
                        value = textValue,
                        onValueChange = {
                            textValue = it
                            voiceError = null
                        },
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
                                    voiceError = null
                                },
                                label = { Text(optionLabels[option] ?: option) },
                            )
                        }
                    }
                }
            }

            AnswerType.NUMBER -> {
                OutlinedTextField(
                    value = textValue,
                    onValueChange = {
                        textValue = it
                        voiceError = null
                    },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text(stringResource(R.string.questionnaire_number_label)) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                )
            }

            else -> {
                OutlinedTextField(
                    value = textValue,
                    onValueChange = {
                        textValue = it
                        voiceError = null
                    },
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

        if (showVoiceTranscriptField) {
            OutlinedTextField(
                value = voiceTranscriptText,
                onValueChange = ::updateVoiceTranscriptManually,
                modifier = Modifier.fillMaxWidth(),
                label = { Text(voiceTranscriptLabel) },
                minLines = if (hasPrimaryTextInput) 1 else 2,
            )
        }

        voiceError?.let {
            Text(
                text = it,
                color = MaterialTheme.colorScheme.error,
            )
        }

        SectionActionRow {
            if (question.voiceAllowed) {
                TextButton(
                    onClick = ::startVoiceFill,
                    modifier = Modifier.padding(end = 8.dp),
                ) {
                    Text(if (voiceSessionActive) voiceStopLabel else voiceFillLabel)
                }
            }
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

private val SpokenVoiceNumbers = mapOf(
    "zero" to 0,
    "one" to 1,
    "two" to 2,
    "three" to 3,
    "four" to 4,
    "five" to 5,
    "six" to 6,
    "seven" to 7,
    "eight" to 8,
    "nine" to 9,
    "ten" to 10,
    "none" to 0,
    "ноль" to 0,
    "один" to 1,
    "одна" to 1,
    "одно" to 1,
    "два" to 2,
    "две" to 2,
    "три" to 3,
    "четыре" to 4,
    "пять" to 5,
    "шесть" to 6,
    "семь" to 7,
    "восемь" to 8,
    "девять" to 9,
    "десять" to 10,
)

private val VoiceTrueWords = setOf(
    "yes",
    "yeah",
    "yep",
    "true",
    "да",
    "ага",
    "есть",
    "было",
    "была",
    "был",
)

private val VoiceFalseWords = setOf(
    "no",
    "nope",
    "false",
    "нет",
    "не",
    "отсутствует",
)

private fun parseBooleanVoiceAnswer(transcript: String): Boolean? {
    val normalized = normalizeVoiceText(transcript)
    if (normalized.isBlank()) return null
    if (normalized in VoiceTrueWords) return true
    if (normalized in VoiceFalseWords) return false

    val tokens = normalized.split(' ').filter(String::isNotBlank).toSet()
    return when {
        tokens.any(VoiceTrueWords::contains) -> true
        tokens.any { it == "нет" || it == "no" || it == "false" } -> false
        normalized.startsWith("не ") || normalized.startsWith("not ") -> false
        else -> null
    }
}

private fun parseIntegerVoiceAnswer(transcript: String): Int? {
    Regex("""-?\d+""").find(transcript)?.value?.toIntOrNull()?.let { return it }
    val tokens = normalizeVoiceText(transcript).split(' ').filter(String::isNotBlank)
    return tokens.firstNotNullOfOrNull(SpokenVoiceNumbers::get)
}

private fun matchSingleVoiceOption(
    transcript: String,
    options: List<String>,
    languageTag: String,
): String? {
    val spokenIndex = parseIntegerVoiceAnswer(transcript)
    if (spokenIndex != null && spokenIndex in 1..options.size) {
        return options[spokenIndex - 1]
    }

    val normalizedTranscript = normalizeVoiceText(transcript)
    val transcriptTokens = normalizedTranscript.split(' ').filter(String::isNotBlank).toSet()
    val scoredMatches = options
        .map { option -> option to scoreVoiceOptionMatch(normalizedTranscript, transcriptTokens, option, languageTag) }
        .filter { (_, score) -> score > 0 }
    val best = scoredMatches.maxByOrNull { (_, score) -> score } ?: return null
    val bestScore = best.second
    if (bestScore < 2 && scoredMatches.count { (_, score) -> score == bestScore } > 1) {
        return null
    }
    return best.first
}

private fun matchMultiVoiceOptions(
    transcript: String,
    options: List<String>,
    languageTag: String,
): List<String> {
    val normalizedTranscript = normalizeVoiceText(transcript)
    val transcriptTokens = normalizedTranscript.split(' ').filter(String::isNotBlank).toSet()
    return options.filter { option ->
        scoreVoiceOptionMatch(normalizedTranscript, transcriptTokens, option, languageTag) > 0
    }
}

private fun scoreVoiceOptionMatch(
    normalizedTranscript: String,
    transcriptTokens: Set<String>,
    option: String,
    languageTag: String,
): Int {
    return optionCandidatePhrases(option, languageTag)
        .map(::normalizeVoiceText)
        .filter(String::isNotBlank)
        .maxOfOrNull { normalizedOption ->
            when {
                normalizedTranscript == normalizedOption -> 4
                normalizedTranscript.contains(normalizedOption) -> 3
                normalizedOption.contains(normalizedTranscript) -> 2
                else -> {
                    val optionTokens = normalizedOption.split(' ').filter(String::isNotBlank)
                    if (optionTokens.isNotEmpty() && optionTokens.all(transcriptTokens::contains)) 1 else 0
                }
            }
        } ?: 0
}

private fun normalizeVoiceText(value: String): String =
    value
        .lowercase(Locale.ROOT)
        .replace(Regex("[^\\p{L}\\p{N}]+"), " ")
        .trim()

private fun splitVoiceListItems(value: String): List<String> =
    value
        .split('\n', ',', ';')
        .map(String::trim)
        .filter(String::isNotBlank)
        .distinct()

private fun mergeVoiceTranscript(
    committedSegments: List<String>,
    liveSegment: String,
): String =
    (committedSegments + liveSegment.takeIf { it.isNotBlank() })
        .filterNotNull()
        .map(String::trim)
        .filter(String::isNotBlank)
        .joinToString(" ")
        .trim()

private fun mergeVoiceSegments(
    existingSegments: List<String>,
    incomingSegment: String,
): List<String> {
    val normalized = incomingSegment.trim()
    if (normalized.isBlank()) return existingSegments
    val updated = existingSegments.toMutableList()
    val lastIndex = updated.indexOfLast { existing ->
        val left = normalizeVoiceText(existing)
        val right = normalizeVoiceText(normalized)
        left == right || left.contains(right) || right.contains(left)
    }
    if (lastIndex >= 0) {
        updated[lastIndex] =
            if (normalized.length >= updated[lastIndex].length) normalized else updated[lastIndex]
    } else {
        updated += normalized
    }
    return updated
        .map(String::trim)
        .filter(String::isNotBlank)
        .takeLast(8)
}

private fun shouldRestartVoiceRecognition(errorCode: Int): Boolean = when (errorCode) {
    SpeechRecognizer.ERROR_NO_MATCH,
    SpeechRecognizer.ERROR_SPEECH_TIMEOUT,
    SpeechRecognizer.ERROR_SERVER_DISCONNECTED -> true
    else -> false
}

private fun extractVoiceTranscript(results: Bundle?): String =
    results
        ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
        ?.firstOrNull()
        .orEmpty()

private fun mapVoiceRecognitionError(
    errorCode: Int,
    unavailableLabel: String,
    emptyResultLabel: String,
    disconnectedLabel: String,
    errorCodeLabel: String,
): String = when (errorCode) {
    SpeechRecognizer.ERROR_NO_MATCH,
    SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> emptyResultLabel
    SpeechRecognizer.ERROR_SERVER_DISCONNECTED -> disconnectedLabel
    SpeechRecognizer.ERROR_LANGUAGE_NOT_SUPPORTED,
    SpeechRecognizer.ERROR_LANGUAGE_UNAVAILABLE -> unavailableLabel
    else -> String.format(errorCodeLabel, errorCode)
}

private const val VoiceRestartDelayMillis = 250L
private const val VoiceReconnectDelayMillis = 700L
private const val MaxVoiceReconnectAttempts = 3

private fun localizedQuestionOptionLabel(
    option: String,
    languageTag: String,
): String {
    QuestionOptionLabels[option]?.get(languageTag.substringBefore('-').lowercase(Locale.ROOT))?.let { return it }

    val tokens = option.split('_').filter(String::isNotBlank)
    if (tokens.isEmpty()) return option
    if (!tokens.all { token -> token.all(Char::isLetterOrDigit) }) return option

    return if (languageTag.startsWith("ru", ignoreCase = true)) {
        tokens
            .map { token -> QuestionOptionRuTokens[token.lowercase(Locale.ROOT)] ?: token }
            .filter(String::isNotBlank)
            .joinToString(" ")
    } else {
        tokens.joinToString(" ")
    }
}

private fun optionCandidatePhrases(
    option: String,
    languageTag: String,
): List<String> {
    val localized = localizedQuestionOptionLabel(option, languageTag)
    return buildList {
        add(option)
        add(option.replace('_', ' '))
        add(localized)
    }.distinct()
}

private val QuestionOptionRuTokens = mapOf(
    "just" to "только",
    "now" to "сейчас",
    "earlier" to "раньше",
    "today" to "сегодня",
    "yesterday" to "вчера",
    "or" to "или",
    "before" to "раньше",
    "sudden" to "внезапно",
    "gradual" to "постепенно",
    "unsure" to "не уверен",
    "forehead" to "лоб",
    "behind" to "за",
    "eye" to "глазом",
    "temples" to "виски",
    "back" to "задняя часть",
    "of" to "",
    "neck" to "шея",
    "whole" to "вся",
    "head" to "голова",
    "left" to "слева",
    "right" to "справа",
    "both" to "с обеих сторон",
    "side" to "сторона",
    "throbbing" to "пульсирующая",
    "pressure" to "давящая",
    "stabbing" to "колющая",
    "burning" to "жгучая",
    "tight" to "сжимающая",
    "band" to "ободом",
    "usual" to "обычно",
    "other" to "другое",
    "unclear" to "неясно",
    "normal" to "нормально",
    "than" to "чем",
    "not" to "не",
    "applicable" to "применимо",
    "limited" to "ограниченно",
    "unable" to "не могу",
    "different" to "по другому",
    "1h" to "1 час",
)

private val QuestionOptionLabels = mapOf(
    "after_1h" to mapOf("ru" to "через 1 час или позже"),
    "arm" to mapOf("ru" to "рука"),
    "back_of_head" to mapOf("ru" to "затылок"),
    "before_pain" to mapOf("ru" to "до боли"),
    "behind_eye" to mapOf("ru" to "за глазом"),
    "both" to mapOf("ru" to "с обеих сторон"),
    "burning" to mapOf("ru" to "жгучая"),
    "caffeine" to mapOf("ru" to "кофеин"),
    "dark_room" to mapOf("ru" to "темная комната"),
    "earlier_today" to mapOf("ru" to "сегодня раньше"),
    "face" to mapOf("ru" to "лицо"),
    "food" to mapOf("ru" to "еда"),
    "forehead" to mapOf("ru" to "лоб"),
    "gradual" to mapOf("ru" to "постепенно"),
    "heat" to mapOf("ru" to "тепло"),
    "hydration" to mapOf("ru" to "вода"),
    "ice" to mapOf("ru" to "холод"),
    "just_now" to mapOf("ru" to "только сейчас"),
    "left" to mapOf("ru" to "слева"),
    "left_side" to mapOf("ru" to "левая сторона"),
    "leg" to mapOf("ru" to "нога"),
    "less_than_usual" to mapOf("ru" to "меньше обычного"),
    "limited" to mapOf("ru" to "ограниченно"),
    "more_than_usual" to mapOf("ru" to "больше обычного"),
    "much_later" to mapOf("ru" to "намного позже"),
    "neck" to mapOf("ru" to "шея"),
    "none" to mapOf("ru" to "нет"),
    "normal" to mapOf("ru" to "нормально"),
    "not_applicable" to mapOf("ru" to "неприменимо"),
    "other" to mapOf("ru" to "другое"),
    "pressure" to mapOf("ru" to "давящая"),
    "rest" to mapOf("ru" to "отдых"),
    "right" to mapOf("ru" to "справа"),
    "right_side" to mapOf("ru" to "правая сторона"),
    "safe" to mapOf("ru" to "безопасно"),
    "slower" to mapOf("ru" to "медленнее"),
    "somewhat_different" to mapOf("ru" to "немного иначе"),
    "stabbing" to mapOf("ru" to "колющая"),
    "stopped" to mapOf("ru" to "остановилась"),
    "stretching" to mapOf("ru" to "растяжка"),
    "sudden" to mapOf("ru" to "внезапно"),
    "temples" to mapOf("ru" to "виски"),
    "throbbing" to mapOf("ru" to "пульсирующая"),
    "tight_band" to mapOf("ru" to "сжимающая обручем"),
    "tongue" to mapOf("ru" to "язык"),
    "unable" to mapOf("ru" to "не могу"),
    "unclear" to mapOf("ru" to "неясно"),
    "unsafe" to mapOf("ru" to "небезопасно"),
    "unsure" to mapOf("ru" to "не уверен"),
    "usual" to mapOf("ru" to "обычно"),
    "very_different" to mapOf("ru" to "совсем иначе"),
    "whole_head" to mapOf("ru" to "вся голова"),
    "within_1h" to mapOf("ru" to "в течение часа"),
    "yesterday_or_before" to mapOf("ru" to "вчера или раньше"),
)
