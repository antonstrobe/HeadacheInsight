package com.neuron.headacheinsight.data.repository

import android.content.Context
import com.neuron.headacheinsight.core.common.TimeProvider
import com.neuron.headacheinsight.core.model.AnalysisResponse
import com.neuron.headacheinsight.core.model.AnalysisSnapshot
import com.neuron.headacheinsight.core.model.AnalysisSource
import com.neuron.headacheinsight.core.model.AnalysisStatus
import com.neuron.headacheinsight.core.model.AnswerType
import com.neuron.headacheinsight.core.model.BackendConnectionStatus
import com.neuron.headacheinsight.core.model.ClinicianSummary
import com.neuron.headacheinsight.core.model.EpisodeDetail
import com.neuron.headacheinsight.core.model.EpisodeTranscript
import com.neuron.headacheinsight.core.model.Hypothesis
import com.neuron.headacheinsight.core.model.HypothesisConfidence
import com.neuron.headacheinsight.core.model.OwnerType
import com.neuron.headacheinsight.core.model.QuestionSource
import com.neuron.headacheinsight.core.model.QuestionStage
import com.neuron.headacheinsight.core.model.QuestionTemplate
import com.neuron.headacheinsight.core.model.SuspectedPattern
import com.neuron.headacheinsight.core.model.TranscriptVariant
import com.neuron.headacheinsight.core.model.UrgentAction
import com.neuron.headacheinsight.core.model.UrgentActionLevel
import com.neuron.headacheinsight.core.model.UserSummary
import com.neuron.headacheinsight.core.model.VoiceIntakeDraft
import com.neuron.headacheinsight.core.model.VoiceIntakeField
import com.neuron.headacheinsight.data.remote.AnalyzeEpisodeRequest
import com.neuron.headacheinsight.data.remote.BackendApi
import com.neuron.headacheinsight.data.remote.GenerateFollowUpQuestionsRequest
import com.neuron.headacheinsight.data.remote.OpenAiChatCompletionRequest
import com.neuron.headacheinsight.data.remote.OpenAiChatMessage
import com.neuron.headacheinsight.data.remote.VoiceIntakeDraftRequest
import com.neuron.headacheinsight.domain.AnalysisRepository
import com.neuron.headacheinsight.domain.AttachmentRepository
import com.neuron.headacheinsight.domain.BackendStatusRepository
import com.neuron.headacheinsight.domain.CloudCredentialsRepository
import com.neuron.headacheinsight.domain.CloudSpeechRecognizerEngine
import com.neuron.headacheinsight.domain.EpisodeRepository
import com.neuron.headacheinsight.domain.LocalSpeechRecognizerEngine
import com.neuron.headacheinsight.domain.QuestionRepository
import com.neuron.headacheinsight.domain.SettingsRepository
import com.neuron.headacheinsight.domain.VoiceIntakeRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import java.io.File
import java.security.MessageDigest
import java.util.Locale
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.first
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody

@Singleton
class DefaultAnalysisRepository @Inject constructor(
    private val backendApi: BackendApi,
    private val episodeRepository: EpisodeRepository,
    private val questionRepository: QuestionRepository,
    private val attachmentRepository: AttachmentRepository,
    private val settingsRepository: SettingsRepository,
    private val cloudCredentialsRepository: CloudCredentialsRepository,
    private val timeProvider: TimeProvider,
    private val json: Json,
) : AnalysisRepository {
    override fun observeLatestAnalysis(ownerId: String) = kotlinx.coroutines.flow.flow {
        emit(episodeRepository.observeEpisodeDetail(ownerId).first()?.analyses?.firstOrNull())
    }

    override suspend fun analyzeEpisode(ownerId: String): Result<AnalysisResponse> = runCatching {
        val episodeDetail = requireNotNull(episodeRepository.observeEpisodeDetail(ownerId).first()) { "Episode not found" }
        val now = timeProvider.now()
        val credentials = cloudCredentialsOrNull()
        val (response, usedCloudResponse) = if (credentials == null) {
            buildFallbackAnalysis(ownerId, episodeDetail, now.toString()) to false
        } else {
            runCatching {
                backendApi.completeJson<OpenAiAnalysisDraft>(
                    model = credentials.analysisModel,
                    systemPrompt = buildEpisodeAnalysisSystemPrompt(episodeDetail.episode.locale),
                    userPayload = json.encodeToString(
                        AnalyzeEpisodeRequest.serializer(),
                        AnalyzeEpisodeRequest(
                            ownerId = ownerId,
                            locale = episodeDetail.episode.locale,
                            episode = episodeDetail,
                        ),
                    ),
                    json = json,
                    temperature = 0.2,
                ).toAnalysisResponse(
                    ownerId = ownerId,
                    generatedAt = now.toString(),
                    createdAt = now,
                    locale = episodeDetail.episode.locale,
                )
            }.map { it to true }.getOrElse {
                buildFallbackAnalysis(ownerId, episodeDetail, now.toString()) to false
            }
        }
        persistAnalysis(
            ownerId = ownerId,
            response = response,
            modelName = if (usedCloudResponse) credentials?.analysisModel ?: "local-fallback" else "local-fallback",
            source = if (usedCloudResponse) AnalysisSource.CLOUD else AnalysisSource.LOCAL_RULE,
        )
        if (response.nextQuestions.isNotEmpty()) {
            questionRepository.upsertGeneratedQuestions(response.nextQuestions)
        }
        response
    }

    override suspend fun generateFollowUpQuestions(ownerId: String): Result<List<QuestionTemplate>> = runCatching {
        val episodeDetail = requireNotNull(episodeRepository.observeEpisodeDetail(ownerId).first()) { "Episode not found" }
        val credentials = cloudCredentialsOrNull() ?: return@runCatching emptyList()
        val createdAt = timeProvider.now()
        val questions = runCatching {
            backendApi.completeJson<OpenAiQuestionDraftResponse>(
                model = credentials.questionModel,
                systemPrompt = buildFollowUpQuestionSystemPrompt(episodeDetail.episode.locale),
                userPayload = json.encodeToString(
                    GenerateFollowUpQuestionsRequest.serializer(),
                    GenerateFollowUpQuestionsRequest(
                        ownerId = ownerId,
                        locale = episodeDetail.episode.locale,
                        episode = episodeDetail,
                    ),
                ),
                json = json,
                temperature = 0.3,
            ).questions.mapIndexed { index, draft ->
                draft.toQuestionTemplate(
                    locale = episodeDetail.episode.locale,
                    createdAt = createdAt,
                    position = index,
                )
            }
        }.getOrDefault(emptyList())
        if (questions.isNotEmpty()) {
            questionRepository.upsertGeneratedQuestions(questions)
        }
        questions
    }

    override suspend fun analyzeAttachments(ownerId: String): Result<List<String>> = runCatching {
        val attachments = attachmentRepository.observeAttachmentsForOwner(ownerId).first()
        val locale = episodeRepository.observeEpisodeDetail(ownerId).first()?.episode?.locale ?: "en-US"
        val credentials = cloudCredentialsOrNull()
        if (credentials == null || attachments.isEmpty()) {
            attachments.mapNotNull { attachment ->
                attachment.extractedText
                    ?.lineSequence()
                    ?.map(String::trim)
                    ?.firstOrNull { it.isNotBlank() }
            }
        } else {
            runCatching {
                backendApi.completeJson<OpenAiAttachmentSummary>(
                    model = credentials.analysisModel,
                    systemPrompt = buildAttachmentSummarySystemPrompt(locale),
                    userPayload = json.encodeToString(
                        AttachmentAnalysisPayload.serializer(),
                        AttachmentAnalysisPayload(
                            ownerId = ownerId,
                            locale = locale,
                            attachments = attachments.map {
                                AttachmentDigest(
                                    displayName = it.displayName,
                                    mimeType = it.mimeType,
                                    extractedText = it.extractedText,
                                )
                            },
                        ),
                    ),
                    json = json,
                    temperature = 0.2,
                ).summary
            }.getOrDefault(
                attachments.mapNotNull { attachment ->
                    attachment.extractedText
                        ?.lineSequence()
                        ?.map(String::trim)
                        ?.firstOrNull { it.isNotBlank() }
                },
            )
        }
    }

    private suspend fun persistAnalysis(
        ownerId: String,
        response: AnalysisResponse,
        modelName: String,
        source: AnalysisSource,
    ) {
        episodeRepository.upsertAnalysis(
            AnalysisSnapshot(
                id = response.analysisId,
                ownerType = OwnerType.EPISODE,
                ownerId = ownerId,
                schemaVersion = response.schemaVersion,
                requestPayloadJson = "{}",
                responsePayloadJson = json.encodeToString(AnalysisResponse.serializer(), response),
                modelName = modelName,
                createdAt = timeProvider.now(),
                source = source,
                status = AnalysisStatus.COMPLETE,
            ),
        )
    }

    private suspend fun cloudCredentialsOrNull() =
        if (!settingsRepository.observeSettings().first().cloudAnalysisEnabled) {
            null
        } else {
            cloudCredentialsRepository.observeCredentials().first().takeIf { it.apiKey.isNotBlank() }
        }
}

@Singleton
class DefaultBackendStatusRepository @Inject constructor(
    private val backendApi: BackendApi,
    private val cloudCredentialsRepository: CloudCredentialsRepository,
) : BackendStatusRepository {
    override suspend fun testConnection(): Result<BackendConnectionStatus> = runCatching {
        val credentials = cloudCredentialsRepository.observeCredentials().first()
        require(credentials.apiKey.isNotBlank()) { "OpenAI API key is empty" }
        backendApi.listModels()
        BackendConnectionStatus(
            serviceName = "OpenAI API",
            apiKeyPresent = true,
            analysisModel = credentials.analysisModel,
            questionModel = credentials.questionModel,
            transcribeModel = credentials.transcribeModel,
        )
    }
}

@Singleton
class LocalVoiceIntakeExtractor @Inject constructor() {
    fun buildDraft(ownerId: String, locale: String, transcriptText: String): VoiceIntakeDraft {
        val russian = locale.startsWith("ru", ignoreCase = true)
        val normalized = transcriptText.trim()
        val lower = normalized.lowercase(Locale.ROOT)
        val symptoms = linkedSetOf<String>()

        fun addSymptom(labelRu: String, labelEn: String, vararg keywords: String) {
            if (keywords.any(lower::contains)) {
                symptoms += if (russian) labelRu else labelEn
            }
        }

        addSymptom("Тошнота", "Nausea", "тошнот", "nausea", "sick to my stomach")
        addSymptom("Свет мешает", "Light sensitivity", "свет", "photophobia", "light hurts")
        addSymptom("Звук мешает", "Sound sensitivity", "звук", "noise hurts", "phonophobia")
        addSymptom("Головокружение", "Dizziness", "головокруж", "dizz")
        addSymptom("Аура", "Aura", "аур", "aura")
        addSymptom("Боль в шее", "Neck pain", "ше", "neck pain")
        addSymptom("Боль справа", "Right-sided pain", "прав")
        addSymptom("Боль слева", "Left-sided pain", "лев")
        addSymptom("Боль в ухе", "Ear pain", "ухо", "ear pain")
        addSymptom("Боль в челюсти", "Jaw pain", "челюст", "jaw pain")
        addSymptom("Зрение ухудшилось", "Vision changes", "зрени", "blurred vision", "vision")
        addSymptom("Читать невозможно", "Reading is hard", "читать невозможно", "can't read")
        addSymptom("Писать невозможно", "Writing is hard", "писать невозможно", "can't write")

        val redFlags = linkedSetOf<String>()
        if (listOf("внезап", "самая сильная", "sudden", "worst headache").any(lower::contains)) {
            redFlags += "suddenWorstPain"
        }
        if (listOf("спутан", "confusion", "не понимаю").any(lower::contains)) {
            redFlags += "confusion"
        }
        if (listOf("говорить тяжело", "речь", "speech").any(lower::contains)) {
            redFlags += "speechDifficulty"
        }
        if (listOf("слабость", "онем", "weakness", "numb").any(lower::contains)) {
            redFlags += "oneSidedWeakness"
        }

        val medications = buildSet {
            Regex("""(?:принял|приняла|выпил|выпила|took|taking)\s+([^,.;\n]+)""")
                .findAll(normalized)
                .mapTo(this) { it.groupValues[1].trim() }
            listOf("ибупрофен", "парацетамол", "цитрамон", "ibuprofen", "paracetamol", "acetaminophen")
                .filter(lower::contains)
                .forEach { add(it.replaceFirstChar { ch -> ch.titlecase(Locale.ROOT) }) }
        }.toList()

        val severity = Regex("""(?:^|\D)(10|[0-9])(?:\D|$)""")
            .find(lower)
            ?.groupValues
            ?.getOrNull(1)
            ?.toIntOrNull()
            ?: when {
                listOf("невыносим", "очень сильно", "unbearable", "severe").any(lower::contains) -> 9
                listOf("сильно", "very painful", "strong pain").any(lower::contains) -> 8
                listOf("умеренн", "moderate").any(lower::contains) -> 5
                listOf("слегка", "mild").any(lower::contains) -> 2
                else -> null
            }

        val liveNotes = normalized
            .split('\n', '.', '!', '?')
            .map(String::trim)
            .filter { it.isNotBlank() }
            .distinct()
            .takeLast(4)

        val dynamicFields = buildList {
            severity?.let {
                add(
                    VoiceIntakeField(
                        section = "severity",
                        label = if (russian) "Сила боли" else "Pain severity",
                        value = it.toString(),
                    ),
                )
            }
            symptoms.forEach { symptom ->
                add(
                    VoiceIntakeField(
                        section = "symptoms",
                        label = symptom,
                        value = if (russian) "распознано" else "detected",
                    ),
                )
            }
            medications.forEach { medication ->
                add(
                    VoiceIntakeField(
                        section = "medications",
                        label = if (russian) "Лекарство" else "Medication",
                        value = medication,
                    ),
                )
            }
        }

        return VoiceIntakeDraft(
            ownerId = ownerId,
            transcriptText = normalized,
            summaryText = normalized,
            severity = severity,
            symptoms = symptoms.toList(),
            redFlags = redFlags.toList(),
            medications = medications,
            liveNotes = liveNotes,
            dynamicFields = dynamicFields,
            engineName = "local-rule",
        )
    }
}

@Singleton
class DefaultVoiceIntakeRepository @Inject constructor(
    private val backendApi: BackendApi,
    private val settingsRepository: SettingsRepository,
    private val cloudCredentialsRepository: CloudCredentialsRepository,
    private val episodeRepository: EpisodeRepository,
    private val timeProvider: TimeProvider,
    private val localVoiceIntakeExtractor: LocalVoiceIntakeExtractor,
    private val json: Json,
) : VoiceIntakeRepository {
    override suspend fun structureVoiceIntake(
        ownerId: String,
        locale: String,
        transcriptText: String,
    ): Result<VoiceIntakeDraft> {
        val normalized = transcriptText.trim()
        if (normalized.isBlank()) {
            return Result.failure(IllegalArgumentException("Transcript is empty"))
        }
        val settings = settingsRepository.observeSettings().first()
        if (!settings.cloudAnalysisEnabled) {
            val localDraft = localVoiceIntakeExtractor.buildDraft(ownerId, locale, normalized)
            persistDraft(localDraft, AnalysisSource.LOCAL_RULE)
            return Result.success(localDraft)
        }

        return runCatching {
            val credentials = cloudCredentialsRepository.observeCredentials().first()
            require(credentials.apiKey.isNotBlank()) { "OpenAI API key is empty" }
            val response = backendApi.completeJson<OpenAiVoiceIntakeDraft>(
                model = credentials.analysisModel,
                systemPrompt = buildVoiceIntakeSystemPrompt(locale),
                userPayload = json.encodeToString(
                    VoiceIntakeDraftRequest.serializer(),
                    VoiceIntakeDraftRequest(
                        ownerId = ownerId,
                        locale = locale,
                        transcriptText = normalized,
                    ),
                ),
                json = json,
                temperature = 0.2,
            )
            val draft = response.toVoiceIntakeDraft(
                ownerId = ownerId,
                transcriptText = normalized,
                engineName = "openai:${credentials.analysisModel}",
            )
            persistDraft(draft, AnalysisSource.CLOUD)
            draft
        }.recoverCatching {
            val localDraft = localVoiceIntakeExtractor.buildDraft(ownerId, locale, normalized)
            persistDraft(localDraft, AnalysisSource.LOCAL_RULE)
            localDraft
        }
    }

    private suspend fun persistDraft(
        draft: VoiceIntakeDraft,
        source: AnalysisSource,
    ) {
        episodeRepository.upsertAnalysis(
            AnalysisSnapshot(
                id = UUID.randomUUID().toString(),
                ownerType = OwnerType.EPISODE,
                ownerId = draft.ownerId,
                schemaVersion = draft.schemaVersion,
                requestPayloadJson = json.encodeToString(
                    VoiceIntakeDraftRequest.serializer(),
                    VoiceIntakeDraftRequest(
                        ownerId = draft.ownerId,
                        locale = "auto",
                        transcriptText = draft.transcriptText,
                    ),
                ),
                responsePayloadJson = json.encodeToString(VoiceIntakeDraft.serializer(), draft),
                modelName = draft.engineName,
                createdAt = timeProvider.now(),
                source = source,
                status = AnalysisStatus.COMPLETE,
            ),
        )
    }
}

@Singleton
class SherpaOnnxProvisioningManager @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    val modelDirectory: File
        get() = File(context.filesDir, "sherpa-onnx")

    fun isProvisioned(): Boolean = File(modelDirectory, "manifest.json").exists()

    fun markProvisioned(metadata: String) {
        modelDirectory.mkdirs()
        File(modelDirectory, "manifest.json").writeText(metadata)
    }
}

@Singleton
class SherpaOnnxLocalEngine @Inject constructor(
    private val provisioningManager: SherpaOnnxProvisioningManager,
) : LocalSpeechRecognizerEngine {
    override val engineName: String = "sherpa-onnx"

    override suspend fun isAvailable(): Boolean = provisioningManager.isProvisioned()

    override suspend fun transcribeAudio(audioPath: String, languageHint: String?): Result<EpisodeTranscript> {
        return if (!provisioningManager.isProvisioned()) {
            Result.failure(IllegalStateException("sherpa-onnx model pack is not provisioned"))
        } else {
            Result.failure(IllegalStateException("sherpa-onnx inference bridge is not bundled in this environment"))
        }
    }
}

@Singleton
class BackendCloudSpeechEngine @Inject constructor(
    private val backendApi: BackendApi,
    private val cloudCredentialsRepository: CloudCredentialsRepository,
    private val timeProvider: TimeProvider,
) : CloudSpeechRecognizerEngine {
    override val engineName: String = "openai-transcribe"

    override suspend fun isAvailable(): Boolean = true

    override suspend fun transcribeAudio(audioPath: String, languageHint: String?): Result<EpisodeTranscript> = runCatching {
        val credentials = cloudCredentialsRepository.observeCredentials().first()
        require(credentials.apiKey.isNotBlank()) { "OpenAI API key is empty" }
        val audioFile = File(audioPath)
        val episodeId = audioFile.nameWithoutExtension.substringBefore("_").ifBlank { audioFile.nameWithoutExtension }
        val response = backendApi.transcribe(
            file = MultipartBody.Part.createFormData(
                name = "file",
                filename = audioFile.name,
                body = audioFile.asRequestBody("audio/wav".toMediaType()),
            ),
            model = credentials.transcribeModel.toRequestBody("text/plain".toMediaType()),
            language = languageHint
                ?.substringBefore('-')
                ?.lowercase(Locale.ROOT)
                ?.takeIf(String::isNotBlank)
                ?.toRequestBody("text/plain".toMediaType()),
            responseFormat = "verbose_json".toRequestBody("text/plain".toMediaType()),
        )
        EpisodeTranscript(
            id = MessageDigest.getInstance("SHA-256").digest(audioFile.absolutePath.encodeToByteArray()).joinToString("") { "%02x".format(it) },
            episodeId = episodeId,
            rawAudioPath = audioPath,
            transcriptText = response.text,
            language = response.language,
            engineType = engineName,
            variant = TranscriptVariant.CLOUD,
            confidence = null,
            createdAt = timeProvider.now(),
        )
    }
}

@Serializable
private data class OpenAiAnalysisDraft(
    @SerialName("urgent_action") val urgentAction: OpenAiUrgentActionDraft? = null,
    @SerialName("user_summary") val userSummary: OpenAiUserSummaryDraft? = null,
    @SerialName("clinician_summary") val clinicianSummary: OpenAiClinicianSummaryDraft? = null,
    val hypotheses: List<OpenAiHypothesisDraft> = emptyList(),
    @SerialName("suspected_patterns") val suspectedPatterns: List<OpenAiPatternDraft> = emptyList(),
    @SerialName("next_questions") val nextQuestions: List<OpenAiQuestionDraft> = emptyList(),
    @SerialName("suggested_tracking_fields") val suggestedTrackingFields: List<String> = emptyList(),
    @SerialName("suggested_attachments") val suggestedAttachments: List<String> = emptyList(),
    @SerialName("suggested_doctor_discussion_points") val suggestedDoctorDiscussionPoints: List<String> = emptyList(),
    @SerialName("suggested_specialists") val suggestedSpecialists: List<String> = emptyList(),
    @SerialName("suggested_tests_or_evaluations") val suggestedTestsOrEvaluations: List<String> = emptyList(),
    @SerialName("self_care_general") val selfCareGeneral: List<String> = emptyList(),
    val disclaimer: String? = null,
    @SerialName("needs_human_clinician_review") val needsHumanClinicianReview: Boolean = false,
)

@Serializable
private data class OpenAiUrgentActionDraft(
    val level: String? = null,
    val reasons: List<String> = emptyList(),
    @SerialName("user_message") val userMessage: String? = null,
)

@Serializable
private data class OpenAiUserSummaryDraft(
    @SerialName("plain_language_summary") val plainLanguageSummary: String? = null,
    @SerialName("key_observations") val keyObservations: List<String> = emptyList(),
)

@Serializable
private data class OpenAiClinicianSummaryDraft(
    @SerialName("concise_medical_context") val conciseMedicalContext: String? = null,
    @SerialName("headache_day_estimate") val headacheDayEstimate: String? = null,
    @SerialName("acute_medication_use_estimate") val acuteMedicationUseEstimate: String? = null,
    @SerialName("functional_impact_summary") val functionalImpactSummary: String? = null,
)

@Serializable
private data class OpenAiHypothesisDraft(
    val label: String,
    val confidence: String? = null,
    val rationale: String = "",
    @SerialName("supporting_signals") val supportingSignals: List<String> = emptyList(),
    @SerialName("missing_information") val missingInformation: List<String> = emptyList(),
    @SerialName("discussion_with_doctor") val discussionWithDoctor: List<String> = emptyList(),
)

@Serializable
private data class OpenAiPatternDraft(
    val type: String,
    val description: String,
    val evidence: List<String> = emptyList(),
)

@Serializable
private data class OpenAiQuestionDraftResponse(
    val questions: List<OpenAiQuestionDraft> = emptyList(),
)

@Serializable
private data class OpenAiQuestionDraft(
    val category: String? = null,
    val stage: String? = null,
    val prompt: String,
    @SerialName("short_label") val shortLabel: String? = null,
    @SerialName("help_text") val helpText: String? = null,
    @SerialName("answer_type") val answerType: String? = null,
    val options: List<String> = emptyList(),
    val priority: Int? = null,
    val required: Boolean? = null,
    val skippable: Boolean? = null,
    @SerialName("voice_allowed") val voiceAllowed: Boolean? = null,
    @SerialName("export_to_clinician") val exportToClinician: Boolean? = null,
    @SerialName("red_flag_weight") val redFlagWeight: Int? = null,
)

@Serializable
private data class OpenAiAttachmentSummary(
    val summary: List<String> = emptyList(),
)

@Serializable
private data class AttachmentAnalysisPayload(
    @SerialName("owner_id") val ownerId: String,
    val locale: String,
    val attachments: List<AttachmentDigest>,
)

@Serializable
private data class AttachmentDigest(
    @SerialName("display_name") val displayName: String,
    @SerialName("mime_type") val mimeType: String,
    @SerialName("extracted_text") val extractedText: String? = null,
)

@Serializable
private data class OpenAiVoiceIntakeDraft(
    @SerialName("summary_text") val summaryText: String? = null,
    val severity: Int? = null,
    val symptoms: List<String> = emptyList(),
    @SerialName("red_flags") val redFlags: List<String> = emptyList(),
    val medications: List<String> = emptyList(),
    @SerialName("live_notes") val liveNotes: List<String> = emptyList(),
    @SerialName("dynamic_fields") val dynamicFields: List<VoiceIntakeField> = emptyList(),
)

private suspend inline fun <reified T> BackendApi.completeJson(
    model: String,
    systemPrompt: String,
    userPayload: String,
    json: Json,
    temperature: Double? = null,
): T {
    val response = createChatCompletion(
        OpenAiChatCompletionRequest(
            model = model,
            messages = listOf(
                OpenAiChatMessage(role = "system", content = systemPrompt),
                OpenAiChatMessage(role = "user", content = userPayload),
            ),
            temperature = temperature,
        ),
    )
    val content = response.choices.firstOrNull()?.message?.content?.trim().orEmpty()
    require(content.isNotBlank()) { "OpenAI returned empty content" }
    return json.decodeFromString(content)
}

private fun buildEpisodeAnalysisSystemPrompt(locale: String): String = """
You are a headache intake analysis assistant.
Return one JSON object only.
Use the input locale when writing human-readable text.
Keep the result clinically careful, concise, and non-diagnostic.
Allowed urgent_action.level values: NONE, MONITOR, DISCUSS_SOON, URGENT, EMERGENCY.
Allowed hypothesis confidence values: LOW, MEDIUM, HIGH.
For next_questions, use uppercase enum strings for stage and answer_type.
Prefer small focused question lists.
""".trimIndent()

private fun buildFollowUpQuestionSystemPrompt(locale: String): String = """
You generate follow-up headache intake questions.
Return one JSON object only with {"questions":[...]}.
Use the input locale for prompt text.
Each question should be short, useful, and safe for patients.
Allowed stage values: ACUTE_FAST, ACUTE_DETAIL, PROFILE, DOCTOR, ATTACHMENTS, REVIEW.
Allowed answer_type values: BOOLEAN, SCALE, SINGLE_SELECT, MULTI_SELECT, FREE_TEXT, NUMBER, DATE_TIME, DURATION, ATTACHMENT_REQUEST, VOICE_NOTE, MEDICATION_LIST.
""".trimIndent()

private fun buildAttachmentSummarySystemPrompt(locale: String): String = """
You summarize attachment text for a headache diary app.
Return one JSON object only with {"summary":[...]}.
Use the input locale.
Each bullet should be short and factual.
""".trimIndent()

private fun buildVoiceIntakeSystemPrompt(locale: String): String = """
You convert live patient dictation into a structured headache intake draft.
Return one JSON object only.
Use the input locale for summary_text, symptoms, live_notes, and dynamic_fields labels.
Allowed red_flags values: suddenWorstPain, confusion, speechDifficulty, oneSidedWeakness.
Preserve custom symptoms when they are explicitly mentioned.
Keep severity in range 0..10 when present.
""".trimIndent()

private fun OpenAiAnalysisDraft.toAnalysisResponse(
    ownerId: String,
    generatedAt: String,
    createdAt: kotlinx.datetime.Instant,
    locale: String,
): AnalysisResponse {
    val questions = nextQuestions.mapIndexed { index, draft ->
        draft.toQuestionTemplate(locale = locale, createdAt = createdAt, position = index)
    }
    return AnalysisResponse(
        schemaVersion = "v1",
        analysisId = UUID.randomUUID().toString(),
        ownerType = OwnerType.EPISODE,
        ownerId = ownerId,
        generatedAt = generatedAt,
        urgentAction = UrgentAction(
            level = urgentAction?.level.toUrgentActionLevel(),
            reasons = urgentAction?.reasons.orEmpty(),
            userMessage = urgentAction?.userMessage ?: "",
        ),
        userSummary = UserSummary(
            plainLanguageSummary = userSummary?.plainLanguageSummary ?: "",
            keyObservations = userSummary?.keyObservations.orEmpty(),
        ),
        clinicianSummary = ClinicianSummary(
            conciseMedicalContext = clinicianSummary?.conciseMedicalContext ?: "",
            headacheDayEstimate = clinicianSummary?.headacheDayEstimate,
            acuteMedicationUseEstimate = clinicianSummary?.acuteMedicationUseEstimate,
            functionalImpactSummary = clinicianSummary?.functionalImpactSummary,
        ),
        hypotheses = hypotheses.map {
            Hypothesis(
                id = UUID.randomUUID().toString(),
                label = it.label,
                confidence = it.confidence.toHypothesisConfidence(),
                rationale = it.rationale,
                supportingSignals = it.supportingSignals,
                missingInformation = it.missingInformation,
                discussionWithDoctor = it.discussionWithDoctor,
            )
        },
        suspectedPatterns = suspectedPatterns.map {
            SuspectedPattern(
                type = it.type,
                description = it.description,
                evidence = it.evidence,
            )
        },
        nextQuestions = questions,
        suggestedTrackingFields = suggestedTrackingFields,
        suggestedAttachments = suggestedAttachments,
        suggestedDoctorDiscussionPoints = suggestedDoctorDiscussionPoints,
        suggestedSpecialists = suggestedSpecialists,
        suggestedTestsOrEvaluations = suggestedTestsOrEvaluations,
        selfCareGeneral = selfCareGeneral,
        disclaimer = disclaimer ?: "",
        needsHumanClinicianReview = needsHumanClinicianReview,
    )
}

private fun OpenAiQuestionDraft.toQuestionTemplate(
    locale: String,
    createdAt: kotlinx.datetime.Instant,
    position: Int,
): QuestionTemplate = QuestionTemplate(
    id = UUID.randomUUID().toString(),
    schemaVersion = "v1",
    source = QuestionSource.API,
    category = category?.takeIf(String::isNotBlank) ?: "headache",
    stage = stage.toQuestionStage(),
    prompt = prompt.trim(),
    shortLabel = shortLabel?.takeIf(String::isNotBlank) ?: prompt.take(48),
    helpText = helpText?.takeIf(String::isNotBlank),
    answerType = answerType.toAnswerType(),
    options = options.distinct(),
    priority = (priority ?: (100 - position)).coerceIn(1, 100),
    required = required ?: false,
    skippable = skippable ?: true,
    voiceAllowed = voiceAllowed ?: true,
    visibleIf = null,
    exportToClinician = exportToClinician ?: true,
    redFlagWeight = redFlagWeight ?: 0,
    createdAt = createdAt,
    active = true,
)

private fun OpenAiVoiceIntakeDraft.toVoiceIntakeDraft(
    ownerId: String,
    transcriptText: String,
    engineName: String,
): VoiceIntakeDraft = VoiceIntakeDraft(
    ownerId = ownerId,
    transcriptText = transcriptText,
    summaryText = summaryText ?: transcriptText,
    severity = severity?.coerceIn(0, 10),
    symptoms = symptoms.distinct(),
    redFlags = redFlags.filter {
        it == "suddenWorstPain" || it == "confusion" || it == "speechDifficulty" || it == "oneSidedWeakness"
    }.distinct(),
    medications = medications.distinct(),
    liveNotes = liveNotes.distinct(),
    dynamicFields = dynamicFields.distinctBy { "${it.section}:${it.label}:${it.value}" },
    engineName = engineName,
)

private fun buildFallbackAnalysis(
    ownerId: String,
    episodeDetail: EpisodeDetail,
    generatedAt: String,
): AnalysisResponse {
    val summary = episodeDetail.episode.summaryText?.takeIf(String::isNotBlank)
        ?: episodeDetail.transcripts.lastOrNull()?.transcriptText?.takeIf(String::isNotBlank)
        ?: ""
    return AnalysisResponse(
        schemaVersion = "v1",
        analysisId = UUID.randomUUID().toString(),
        ownerType = OwnerType.EPISODE,
        ownerId = ownerId,
        generatedAt = generatedAt,
        urgentAction = UrgentAction(
            level = UrgentActionLevel.MONITOR,
            reasons = emptyList(),
            userMessage = "",
        ),
        userSummary = UserSummary(
            plainLanguageSummary = summary,
            keyObservations = episodeDetail.symptoms.map { it.symptomCode }.distinct(),
        ),
        clinicianSummary = ClinicianSummary(
            conciseMedicalContext = summary,
            headacheDayEstimate = null,
            acuteMedicationUseEstimate = null,
            functionalImpactSummary = null,
        ),
        hypotheses = emptyList(),
        suspectedPatterns = emptyList(),
        nextQuestions = emptyList(),
        suggestedTrackingFields = emptyList(),
        suggestedAttachments = emptyList(),
        suggestedDoctorDiscussionPoints = emptyList(),
        suggestedSpecialists = emptyList(),
        suggestedTestsOrEvaluations = emptyList(),
        selfCareGeneral = emptyList(),
        disclaimer = "",
        needsHumanClinicianReview = false,
    )
}

private fun String?.toUrgentActionLevel(): UrgentActionLevel = when (this?.uppercase(Locale.ROOT)) {
    "MONITOR" -> UrgentActionLevel.MONITOR
    "DISCUSS_SOON" -> UrgentActionLevel.DISCUSS_SOON
    "URGENT" -> UrgentActionLevel.URGENT
    "EMERGENCY" -> UrgentActionLevel.EMERGENCY
    else -> UrgentActionLevel.NONE
}

private fun String?.toHypothesisConfidence(): HypothesisConfidence = when (this?.uppercase(Locale.ROOT)) {
    "HIGH" -> HypothesisConfidence.HIGH
    "MEDIUM" -> HypothesisConfidence.MEDIUM
    else -> HypothesisConfidence.LOW
}

private fun String?.toQuestionStage(): QuestionStage = when (this?.uppercase(Locale.ROOT)) {
    "ACUTE_FAST" -> QuestionStage.ACUTE_FAST
    "PROFILE" -> QuestionStage.PROFILE
    "DOCTOR" -> QuestionStage.DOCTOR
    "ATTACHMENTS" -> QuestionStage.ATTACHMENTS
    "REVIEW" -> QuestionStage.REVIEW
    else -> QuestionStage.ACUTE_DETAIL
}

private fun String?.toAnswerType(): AnswerType = when (this?.uppercase(Locale.ROOT)) {
    "BOOLEAN" -> AnswerType.BOOLEAN
    "SCALE" -> AnswerType.SCALE
    "SINGLE_SELECT" -> AnswerType.SINGLE_SELECT
    "MULTI_SELECT" -> AnswerType.MULTI_SELECT
    "NUMBER" -> AnswerType.NUMBER
    "DATE_TIME" -> AnswerType.DATE_TIME
    "DURATION" -> AnswerType.DURATION
    "ATTACHMENT_REQUEST" -> AnswerType.ATTACHMENT_REQUEST
    "VOICE_NOTE" -> AnswerType.VOICE_NOTE
    "MEDICATION_LIST" -> AnswerType.MEDICATION_LIST
    else -> AnswerType.FREE_TEXT
}

@Module
@InstallIn(SingletonComponent::class)
abstract class CloudBindingsModule {
    @Binds
    abstract fun bindAnalysisRepository(impl: DefaultAnalysisRepository): AnalysisRepository

    @Binds
    abstract fun bindBackendStatusRepository(impl: DefaultBackendStatusRepository): BackendStatusRepository

    @Binds
    abstract fun bindVoiceIntakeRepository(impl: DefaultVoiceIntakeRepository): VoiceIntakeRepository

    @Binds
    abstract fun bindLocalSpeechRecognizer(impl: SherpaOnnxLocalEngine): LocalSpeechRecognizerEngine

    @Binds
    abstract fun bindCloudSpeechRecognizer(impl: BackendCloudSpeechEngine): CloudSpeechRecognizerEngine
}
