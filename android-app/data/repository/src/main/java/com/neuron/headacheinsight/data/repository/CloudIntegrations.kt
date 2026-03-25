@file:OptIn(kotlinx.serialization.ExperimentalSerializationApi::class)

package com.neuron.headacheinsight.data.repository

import android.content.Context
import com.neuron.headacheinsight.core.model.AnalysisRunPreview
import com.neuron.headacheinsight.core.common.TimeProvider
import com.neuron.headacheinsight.core.model.AnalysisResponse
import com.neuron.headacheinsight.core.model.AnalysisSnapshot
import com.neuron.headacheinsight.core.model.AnalysisSource
import com.neuron.headacheinsight.core.model.AnalysisStatus
import com.neuron.headacheinsight.core.model.AllDataAnalysisOwnerId
import com.neuron.headacheinsight.core.model.AnswerType
import com.neuron.headacheinsight.core.model.BackendConnectionStatus
import com.neuron.headacheinsight.core.model.ClinicianSummary
import com.neuron.headacheinsight.core.model.EpisodeDetail
import com.neuron.headacheinsight.core.model.EpisodeTranscript
import com.neuron.headacheinsight.core.model.Hypothesis
import com.neuron.headacheinsight.core.model.HypothesisConfidence
import com.neuron.headacheinsight.core.model.OpenAiModelTask
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
import com.neuron.headacheinsight.core.model.estimateOpenAiTokens
import com.neuron.headacheinsight.core.model.isOpenAiAutoModel
import com.neuron.headacheinsight.core.model.recommendedAnalysisModel
import com.neuron.headacheinsight.core.model.resolveConfiguredOpenAiModel
import com.neuron.headacheinsight.data.local.AnalysisDao
import com.neuron.headacheinsight.data.local.toModel
import com.neuron.headacheinsight.data.remote.AnalyzeEpisodeRequest
import com.neuron.headacheinsight.data.remote.BackendApi
import com.neuron.headacheinsight.data.remote.GenerateFollowUpQuestionsRequest
import com.neuron.headacheinsight.data.remote.OpenAiChatCompletionRequest
import com.neuron.headacheinsight.data.remote.OpenAiChatMessage
import com.neuron.headacheinsight.data.remote.OpenAiResponseFormat
import com.neuron.headacheinsight.data.remote.OpenAiResponseJsonSchema
import com.neuron.headacheinsight.data.remote.VoiceIntakeDraftRequest
import com.neuron.headacheinsight.domain.AnalysisRepository
import com.neuron.headacheinsight.domain.AttachmentRepository
import com.neuron.headacheinsight.domain.BackendStatusRepository
import com.neuron.headacheinsight.domain.CloudCredentialsRepository
import com.neuron.headacheinsight.domain.CloudSpeechRecognizerEngine
import com.neuron.headacheinsight.domain.EpisodeRepository
import com.neuron.headacheinsight.domain.LocalSpeechRecognizerEngine
import com.neuron.headacheinsight.domain.OpenAiModelRepository
import com.neuron.headacheinsight.domain.QuestionRepository
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
import kotlinx.coroutines.flow.map
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonNames
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject
import kotlinx.serialization.json.JsonPrimitive
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody

@Singleton
class OpenAiRuntimeModelResolver @Inject constructor(
    private val backendApi: BackendApi,
) {
    @Volatile
    private var cache: CachedModelList? = null

    suspend fun loadAvailableModels(): List<String> {
        val cached = cache
        val now = System.currentTimeMillis()
        if (cached != null && now - cached.loadedAtMillis <= CacheTtlMillis) {
            return cached.modelIds
        }

        val loaded = runCatching {
            backendApi.listModels()
                .data
                .map { it.id.trim() }
                .filter(String::isNotBlank)
                .distinct()
        }.getOrDefault(emptyList())

        if (loaded.isNotEmpty()) {
            cache = CachedModelList(
                loadedAtMillis = now,
                modelIds = loaded,
            )
        }

        return loaded
    }

    suspend fun resolveModel(
        configuredModel: String,
        task: OpenAiModelTask,
        estimatedInputTokens: Int = 0,
    ): String = resolveConfiguredOpenAiModel(
        configuredModel = configuredModel.trim().ifBlank { "auto" },
        availableModels = loadAvailableModels(),
        task = task,
        estimatedInputTokens = estimatedInputTokens,
    )

    suspend fun previewAnalysis(
        configuredModel: String,
        estimatedInputTokens: Int,
    ): AnalysisRunPreview {
        val normalizedConfigured = configuredModel.trim().ifBlank { "auto" }
        val availableModels = loadAvailableModels()
        val recommendedModel = recommendedAnalysisModel(
            availableModels = availableModels,
            estimatedInputTokens = estimatedInputTokens,
        )
        val automaticSelection = isOpenAiAutoModel(normalizedConfigured)
        val effectiveModel = if (automaticSelection) {
            recommendedModel
        } else {
            normalizedConfigured
        }
        val weakManualModel = effectiveModel.contains("mini", ignoreCase = true) ||
            effectiveModel.contains("nano", ignoreCase = true) ||
            effectiveModel.equals("gpt-4o", ignoreCase = true) ||
            effectiveModel.equals("gpt-4.1-mini", ignoreCase = true)

        return AnalysisRunPreview(
            estimatedInputTokens = estimatedInputTokens,
            configuredModel = normalizedConfigured,
            effectiveModel = effectiveModel,
            recommendedModel = recommendedModel,
            automaticSelection = automaticSelection,
            shouldSuggestRecommendedModel = !automaticSelection &&
                effectiveModel != recommendedModel &&
                (estimatedInputTokens >= 48_000 || weakManualModel),
        )
    }

    private data class CachedModelList(
        val loadedAtMillis: Long,
        val modelIds: List<String>,
    )

    private companion object {
        const val CacheTtlMillis: Long = 5 * 60 * 1000L
    }
}

@Singleton
class DefaultAnalysisRepository @Inject constructor(
    private val openAiRuntimeModelResolver: OpenAiRuntimeModelResolver,
    private val backendApi: BackendApi,
    private val analysisDao: AnalysisDao,
    private val episodeRepository: EpisodeRepository,
    private val questionRepository: QuestionRepository,
    private val attachmentRepository: AttachmentRepository,
    private val cloudCredentialsRepository: CloudCredentialsRepository,
    private val timeProvider: TimeProvider,
    private val json: Json,
) : AnalysisRepository {
    override fun observeLatestAnalysis(ownerId: String) = analysisDao.observeLatest(ownerId).map { it?.toModel() }

    override fun observeAnalysisHistory(ownerId: String) = analysisDao.observeByOwner(ownerId).map { list ->
        list.map { it.toModel() }
    }

    override fun observeAllAnalysisHistory() = analysisDao.observeAll().map { list ->
        list.map { it.toModel() }
    }

    override suspend fun previewEpisodeAnalysis(ownerId: String): Result<AnalysisRunPreview> = runCatching {
        val prepared = prepareEpisodeAnalysis(ownerId)
        openAiRuntimeModelResolver.previewAnalysis(
            configuredModel = prepared.configuredModel,
            estimatedInputTokens = prepared.estimatedInputTokens,
        )
    }

    override suspend fun analyzeEpisode(ownerId: String): Result<AnalysisResponse> = runCatching {
        val prepared = prepareEpisodeAnalysis(ownerId)
        val now = timeProvider.now()
        val response = backendApi.completeJson<OpenAiAnalysisDraft>(
            model = prepared.effectiveModel,
            systemPrompt = prepared.systemPrompt,
            userPayload = prepared.userPayload,
            json = json,
            schemaName = "headache_analysis",
            schema = HeadacheAnalysisSchema,
            reasoningEffort = prepared.reasoningEffort,
            temperature = prepared.temperature,
        ).toAnalysisResponse(
            ownerId = ownerId,
            generatedAt = now.toString(),
            createdAt = now,
            locale = prepared.locale,
        )
        persistAnalysis(
            ownerId = ownerId,
            ownerType = OwnerType.EPISODE,
            requestPayload = prepared.userPayload,
            response = response,
            modelName = prepared.effectiveModel,
            source = AnalysisSource.CLOUD,
        )
        if (response.nextQuestions.isNotEmpty()) {
            questionRepository.upsertGeneratedQuestions(response.nextQuestions)
        }
        response
    }

    override suspend fun previewAllDataAnalysis(
        payloadJson: String,
        locale: String,
    ): Result<AnalysisRunPreview> = runCatching {
        val prepared = prepareAllDataAnalysis(
            payloadJson = payloadJson,
            locale = locale,
        )
        openAiRuntimeModelResolver.previewAnalysis(
            configuredModel = prepared.configuredModel,
            estimatedInputTokens = prepared.estimatedInputTokens,
        )
    }

    override suspend fun analyzeAllData(
        payloadJson: String,
        locale: String,
    ): Result<AnalysisResponse> = runCatching {
        val prepared = prepareAllDataAnalysis(
            payloadJson = payloadJson,
            locale = locale,
        )
        val now = timeProvider.now()
        val response = backendApi.completeJson<OpenAiAnalysisDraft>(
            model = prepared.effectiveModel,
            systemPrompt = prepared.systemPrompt,
            userPayload = prepared.userPayload,
            json = json,
            schemaName = "headache_all_data_analysis",
            schema = HeadacheAnalysisSchema,
            reasoningEffort = prepared.reasoningEffort,
            temperature = prepared.temperature,
        ).toAnalysisResponse(
            ownerId = AllDataAnalysisOwnerId,
            generatedAt = now.toString(),
            createdAt = now,
            locale = prepared.locale,
            ownerType = OwnerType.PROFILE,
        )
        persistAnalysis(
            ownerId = AllDataAnalysisOwnerId,
            ownerType = OwnerType.PROFILE,
            requestPayload = prepared.userPayload,
            response = response,
            modelName = prepared.effectiveModel,
            source = AnalysisSource.CLOUD,
        )
        response
    }

    override suspend fun generateFollowUpQuestions(ownerId: String): Result<List<QuestionTemplate>> = runCatching {
        val episodeDetail = requireNotNull(episodeRepository.observeEpisodeDetail(ownerId).first()) { "Episode not found" }
        val credentials = requireCloudCredentials()
        val createdAt = timeProvider.now()
        val systemPrompt = buildFollowUpQuestionSystemPrompt(episodeDetail.episode.locale)
        val userPayload = json.encodeToString(
            GenerateFollowUpQuestionsRequest.serializer(),
            GenerateFollowUpQuestionsRequest(
                ownerId = ownerId,
                locale = episodeDetail.episode.locale,
                episode = episodeDetail,
            ),
        )
        val estimatedInputTokens = estimateOpenAiTokens(systemPrompt, userPayload)
        val model = openAiRuntimeModelResolver.resolveModel(
            configuredModel = credentials.questionModel,
            task = OpenAiModelTask.QUESTIONS,
            estimatedInputTokens = estimatedInputTokens,
        )
        val questions = backendApi.completeJson<OpenAiQuestionDraftResponse>(
            model = model,
            systemPrompt = systemPrompt,
            userPayload = userPayload,
            json = json,
            schemaName = "headache_follow_up_questions",
            schema = FollowUpQuestionListSchema,
            reasoningEffort = model.reasoningEffortFor(OpenAiModelTask.QUESTIONS),
            temperature = model.temperatureFor(OpenAiModelTask.QUESTIONS),
        ).questions.mapNotNull { draft ->
            draft.toQuestionTemplateOrNull(
                locale = episodeDetail.episode.locale,
                createdAt = createdAt,
            )
        }.mapIndexed { index, question ->
            question.copy(priority = question.priority.coerceAtMost((100 - index).coerceAtLeast(1)))
        }
        if (questions.isNotEmpty()) {
            questionRepository.upsertGeneratedQuestions(questions)
        }
        questions
    }

    override suspend fun analyzeAttachments(ownerId: String): Result<List<String>> = runCatching {
        val attachments = attachmentRepository.observeAttachmentsForOwner(ownerId).first()
        if (attachments.isEmpty()) {
            emptyList()
        } else {
            val locale = episodeRepository.observeEpisodeDetail(ownerId).first()?.episode?.locale ?: "en-US"
            val credentials = requireCloudCredentials()
            val systemPrompt = buildAttachmentSummarySystemPrompt(locale)
            val userPayload = json.encodeToString(
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
            )
            val estimatedInputTokens = estimateOpenAiTokens(systemPrompt, userPayload)
            val model = openAiRuntimeModelResolver.resolveModel(
                configuredModel = credentials.analysisModel,
                task = OpenAiModelTask.ANALYSIS,
                estimatedInputTokens = estimatedInputTokens,
            )
            backendApi.completeJson<OpenAiAttachmentSummary>(
                model = model,
                systemPrompt = systemPrompt,
                userPayload = userPayload,
                json = json,
                schemaName = "attachment_summary",
                schema = AttachmentSummarySchema,
                reasoningEffort = model.reasoningEffortFor(OpenAiModelTask.ANALYSIS),
                temperature = model.temperatureFor(OpenAiModelTask.ANALYSIS),
            ).summary
        }
    }

    private suspend fun prepareEpisodeAnalysis(ownerId: String): PreparedAnalysisRequest {
        val episodeDetail = requireNotNull(episodeRepository.observeEpisodeDetail(ownerId).first()) { "Episode not found" }
        val credentials = requireCloudCredentials()
        val systemPrompt = buildEpisodeAnalysisSystemPrompt(episodeDetail.episode.locale)
        val userPayload = json.encodeToString(
            AnalyzeEpisodeRequest.serializer(),
            AnalyzeEpisodeRequest(
                ownerId = ownerId,
                locale = episodeDetail.episode.locale,
                episode = episodeDetail,
            ),
        )
        val estimatedInputTokens = estimateOpenAiTokens(systemPrompt, userPayload)
        val effectiveModel = openAiRuntimeModelResolver.resolveModel(
            configuredModel = credentials.analysisModel,
            task = OpenAiModelTask.ANALYSIS,
            estimatedInputTokens = estimatedInputTokens,
        )
        return PreparedAnalysisRequest(
            locale = episodeDetail.episode.locale,
            configuredModel = credentials.analysisModel,
            effectiveModel = effectiveModel,
            systemPrompt = systemPrompt,
            userPayload = userPayload,
            estimatedInputTokens = estimatedInputTokens,
            reasoningEffort = effectiveModel.reasoningEffortFor(OpenAiModelTask.ANALYSIS),
            temperature = effectiveModel.temperatureFor(OpenAiModelTask.ANALYSIS),
        )
    }

    private suspend fun prepareAllDataAnalysis(
        payloadJson: String,
        locale: String,
    ): PreparedAnalysisRequest {
        val credentials = requireCloudCredentials()
        val systemPrompt = buildAllDataAnalysisSystemPrompt(locale)
        val estimatedInputTokens = estimateOpenAiTokens(systemPrompt, payloadJson)
        val effectiveModel = openAiRuntimeModelResolver.resolveModel(
            configuredModel = credentials.allDataAnalysisModel,
            task = OpenAiModelTask.ANALYSIS,
            estimatedInputTokens = estimatedInputTokens,
        )
        return PreparedAnalysisRequest(
            locale = locale,
            configuredModel = credentials.allDataAnalysisModel,
            effectiveModel = effectiveModel,
            systemPrompt = systemPrompt,
            userPayload = payloadJson,
            estimatedInputTokens = estimatedInputTokens,
            reasoningEffort = effectiveModel.reasoningEffortFor(OpenAiModelTask.ANALYSIS),
            temperature = effectiveModel.temperatureFor(OpenAiModelTask.ANALYSIS),
        )
    }

    private suspend fun persistAnalysis(
        ownerId: String,
        ownerType: OwnerType,
        requestPayload: String,
        response: AnalysisResponse,
        modelName: String,
        source: AnalysisSource,
    ) {
        episodeRepository.upsertAnalysis(
            AnalysisSnapshot(
                id = response.analysisId,
                ownerType = ownerType,
                ownerId = ownerId,
                schemaVersion = response.schemaVersion,
                requestPayloadJson = requestPayload,
                responsePayloadJson = json.encodeToString(AnalysisResponse.serializer(), response),
                modelName = modelName,
                createdAt = timeProvider.now(),
                source = source,
                status = AnalysisStatus.COMPLETE,
            ),
        )
    }

    private suspend fun requireCloudCredentials() =
        cloudCredentialsRepository.observeCredentials().first().also { credentials ->
            require(credentials.apiKey.isNotBlank()) { "OpenAI API key is empty" }
        }
}

@Singleton
class DefaultBackendStatusRepository @Inject constructor(
    private val openAiRuntimeModelResolver: OpenAiRuntimeModelResolver,
    private val backendApi: BackendApi,
    private val cloudCredentialsRepository: CloudCredentialsRepository,
) : BackendStatusRepository {
    override suspend fun testConnection(): Result<BackendConnectionStatus> = runCatching {
        val credentials = cloudCredentialsRepository.observeCredentials().first()
        require(credentials.apiKey.isNotBlank()) { "OpenAI API key is empty" }
        backendApi.listModels()
        val analysisModel = openAiRuntimeModelResolver.resolveModel(
            configuredModel = credentials.analysisModel,
            task = OpenAiModelTask.ANALYSIS,
        )
        val allDataAnalysisModel = openAiRuntimeModelResolver.resolveModel(
            configuredModel = credentials.allDataAnalysisModel,
            task = OpenAiModelTask.ANALYSIS,
        )
        val questionModel = openAiRuntimeModelResolver.resolveModel(
            configuredModel = credentials.questionModel,
            task = OpenAiModelTask.QUESTIONS,
        )
        val transcribeModel = openAiRuntimeModelResolver.resolveModel(
            configuredModel = credentials.transcribeModel,
            task = OpenAiModelTask.TRANSCRIPTION,
        )
        BackendConnectionStatus(
            serviceName = "OpenAI API",
            apiKeyPresent = true,
            analysisModel = analysisModel,
            allDataAnalysisModel = allDataAnalysisModel,
            questionModel = questionModel,
            transcribeModel = transcribeModel,
        )
    }
}

@Singleton
class DefaultOpenAiModelRepository @Inject constructor(
    private val backendApi: BackendApi,
) : OpenAiModelRepository {
    override suspend fun listModels(apiKey: String): Result<List<String>> = runCatching {
        val normalizedApiKey = apiKey.trim()
        require(normalizedApiKey.isNotBlank()) { "OpenAI API key is empty" }
        backendApi.listModels(authorization = normalizedApiKey.toBearerHeader())
            .data
            .map { it.id.trim() }
            .filter(String::isNotBlank)
            .distinct()
            .sortedBy { it.lowercase(Locale.ROOT) }
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
    private val openAiRuntimeModelResolver: OpenAiRuntimeModelResolver,
    private val backendApi: BackendApi,
    private val cloudCredentialsRepository: CloudCredentialsRepository,
    private val episodeRepository: EpisodeRepository,
    private val timeProvider: TimeProvider,
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
        return runCatching {
            val credentials = cloudCredentialsRepository.observeCredentials().first()
            require(credentials.apiKey.isNotBlank()) { "OpenAI API key is empty" }
            val systemPrompt = buildVoiceIntakeSystemPrompt(locale)
            val userPayload = json.encodeToString(
                VoiceIntakeDraftRequest.serializer(),
                VoiceIntakeDraftRequest(
                    ownerId = ownerId,
                    locale = locale,
                    transcriptText = normalized,
                ),
            )
            val estimatedInputTokens = estimateOpenAiTokens(systemPrompt, userPayload)
            val model = openAiRuntimeModelResolver.resolveModel(
                configuredModel = credentials.analysisModel,
                task = OpenAiModelTask.ANALYSIS,
                estimatedInputTokens = estimatedInputTokens,
            )
            val response = backendApi.completeJson<OpenAiVoiceIntakeDraft>(
                model = model,
                systemPrompt = systemPrompt,
                userPayload = userPayload,
                json = json,
                schemaName = "voice_intake_draft",
                schema = VoiceIntakeDraftSchema,
                reasoningEffort = model.reasoningEffortFor(OpenAiModelTask.ANALYSIS),
                temperature = model.temperatureFor(OpenAiModelTask.ANALYSIS),
            )
            val draft = response.toVoiceIntakeDraft(
                ownerId = ownerId,
                transcriptText = normalized,
                engineName = "openai:$model",
            )
            persistDraft(draft, AnalysisSource.CLOUD)
            draft
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
    private val openAiRuntimeModelResolver: OpenAiRuntimeModelResolver,
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
        val model = openAiRuntimeModelResolver.resolveModel(
            configuredModel = credentials.transcribeModel,
            task = OpenAiModelTask.TRANSCRIPTION,
        )
        val response = backendApi.transcribe(
            file = MultipartBody.Part.createFormData(
                name = "file",
                filename = audioFile.name,
                body = audioFile.asRequestBody("audio/wav".toMediaType()),
            ),
            model = model.toRequestBody("text/plain".toMediaType()),
            language = languageHint
                ?.substringBefore('-')
                ?.lowercase(Locale.ROOT)
                ?.takeIf(String::isNotBlank)
                ?.toRequestBody("text/plain".toMediaType()),
            responseFormat = "json".toRequestBody("text/plain".toMediaType()),
        )
        EpisodeTranscript(
            id = MessageDigest.getInstance("SHA-256").digest(audioFile.absolutePath.encodeToByteArray()).joinToString("") { "%02x".format(it) },
            episodeId = episodeId,
            rawAudioPath = audioPath,
            transcriptText = response.text,
            language = response.language,
            engineType = "openai-transcribe:$model",
            variant = TranscriptVariant.CLOUD,
            confidence = null,
            createdAt = timeProvider.now(),
        )
    }
}

private data class PreparedAnalysisRequest(
    val locale: String,
    val configuredModel: String,
    val effectiveModel: String,
    val systemPrompt: String,
    val userPayload: String,
    val estimatedInputTokens: Int,
    val reasoningEffort: String?,
    val temperature: Double?,
)

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
    @JsonNames("user_message", "userMessage") val userMessage: String? = null,
)

@Serializable
private data class OpenAiUserSummaryDraft(
    @JsonNames("plain_language_summary", "plainLanguageSummary") val plainLanguageSummary: String? = null,
    @JsonNames("key_observations", "keyObservations") val keyObservations: List<String> = emptyList(),
)

@Serializable
private data class OpenAiClinicianSummaryDraft(
    @JsonNames("concise_medical_context", "conciseMedicalContext") val conciseMedicalContext: String? = null,
    @JsonNames("headache_day_estimate", "headacheDayEstimate") val headacheDayEstimate: String? = null,
    @JsonNames("acute_medication_use_estimate", "acuteMedicationUseEstimate") val acuteMedicationUseEstimate: String? = null,
    @JsonNames("functional_impact_summary", "functionalImpactSummary") val functionalImpactSummary: String? = null,
)

@Serializable
private data class OpenAiHypothesisDraft(
    val label: String? = null,
    val confidence: String? = null,
    val rationale: String = "",
    @JsonNames("supporting_signals", "supportingSignals") val supportingSignals: List<String> = emptyList(),
    @JsonNames("missing_information", "missingInformation") val missingInformation: List<String> = emptyList(),
    @JsonNames("discussion_with_doctor", "discussionWithDoctor") val discussionWithDoctor: List<String> = emptyList(),
)

@Serializable
private data class OpenAiPatternDraft(
    val type: String? = null,
    val description: String? = null,
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
    val prompt: String? = null,
    @JsonNames("short_label", "shortLabel") val shortLabel: String? = null,
    @JsonNames("help_text", "helpText") val helpText: String? = null,
    @JsonNames("answer_type", "answerType") val answerType: String? = null,
    val options: List<String> = emptyList(),
    val priority: Int? = null,
    val required: Boolean? = null,
    val skippable: Boolean? = null,
    @JsonNames("voice_allowed", "voiceAllowed") val voiceAllowed: Boolean? = null,
    @JsonNames("export_to_clinician", "exportToClinician") val exportToClinician: Boolean? = null,
    @JsonNames("red_flag_weight", "redFlagWeight") val redFlagWeight: Int? = null,
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
    @JsonNames("summary_text", "summaryText") val summaryText: String? = null,
    val severity: Int? = null,
    val symptoms: List<String> = emptyList(),
    @JsonNames("red_flags", "redFlags") val redFlags: List<String> = emptyList(),
    val medications: List<String> = emptyList(),
    @JsonNames("live_notes", "liveNotes") val liveNotes: List<String> = emptyList(),
    @JsonNames("dynamic_fields", "dynamicFields") val dynamicFields: List<VoiceIntakeField> = emptyList(),
)

private suspend inline fun <reified T> BackendApi.completeJson(
    model: String,
    systemPrompt: String,
    userPayload: String,
    json: Json,
    schemaName: String,
    schema: JsonObject,
    reasoningEffort: String? = null,
    temperature: Double? = null,
): T {
    val response = createChatCompletion(
        OpenAiChatCompletionRequest(
            model = model,
            messages = listOf(
                OpenAiChatMessage(role = "system", content = systemPrompt),
                OpenAiChatMessage(role = "user", content = userPayload),
            ),
            responseFormat = OpenAiResponseFormat(
                type = "json_schema",
                jsonSchema = OpenAiResponseJsonSchema(
                    name = schemaName,
                    schema = schema,
                    strict = true,
                ),
            ),
            reasoningEffort = reasoningEffort,
            temperature = temperature,
        ),
    )
    val content = response.choices.firstOrNull()?.message?.content?.trim().orEmpty()
    require(content.isNotBlank()) { "OpenAI returned empty content" }
    return json.decodeFromString(content)
}

private fun buildEpisodeAnalysisSystemPrompt(locale: String): String = """
You are a headache intake analysis assistant.
Return exactly one JSON object that matches the provided schema.
Use the input locale when writing human-readable text.
Keep the result clinically careful, concise, and non-diagnostic.
Allowed urgent_action.level values: NONE, MONITOR, DISCUSS_SOON, URGENT, EMERGENCY.
Allowed hypothesis confidence values: LOW, MEDIUM, HIGH.
Treat custom additional_context answer objects as direct patient notes that may refine the episode.
For next_questions, use snake_case keys and uppercase enum strings for stage and answer_type.
Only include next_questions when they are genuinely useful. Otherwise return an empty array.
Prefer small focused question lists.
""".trimIndent()

private fun buildAllDataAnalysisSystemPrompt(locale: String): String = """
You analyze longitudinal headache diary data across many episodes.
Return exactly one JSON object that matches the provided schema.
Use the input locale when writing human-readable text.
Focus on patterns, changes over time, possible triggers, functional impact, and what should be clarified next.
Keep the result clinically careful, concise, and non-diagnostic.
The input payload is a full exported history snapshot for this patient.
Allowed urgent_action.level values: NONE, MONITOR, DISCUSS_SOON, URGENT, EMERGENCY.
Allowed hypothesis confidence values: LOW, MEDIUM, HIGH.
For next_questions, prefer an empty array unless one or two longitudinal follow-up questions would materially improve the analysis.
Do not generate acute triage questions unless the data strongly supports that need.
""".trimIndent()

private fun buildFollowUpQuestionSystemPrompt(locale: String): String = """
You generate follow-up headache intake questions.
Return exactly one JSON object with {"questions":[...]} that matches the provided schema.
Use the input locale for prompt text.
Each question should be short, useful, and safe for patients.
Allowed stage values: ACUTE_FAST, ACUTE_DETAIL, PROFILE, DOCTOR, ATTACHMENTS, REVIEW.
Allowed answer_type values: BOOLEAN, SCALE, SINGLE_SELECT, MULTI_SELECT, FREE_TEXT, NUMBER, DATE_TIME, DURATION, ATTACHMENT_REQUEST, VOICE_NOTE, MEDICATION_LIST.
Use snake_case keys exactly as defined by the schema.
""".trimIndent()

private fun buildAttachmentSummarySystemPrompt(locale: String): String = """
You summarize attachment text for a headache diary app.
Return exactly one JSON object with {"summary":[...]} that matches the provided schema.
Use the input locale.
Each bullet should be short and factual.
""".trimIndent()

private fun buildVoiceIntakeSystemPrompt(locale: String): String = """
You convert live patient dictation into a structured headache intake draft.
Return exactly one JSON object that matches the provided schema.
Use the input locale for summary_text, symptoms, live_notes, and dynamic_fields labels.
Allowed red_flags values: suddenWorstPain, confusion, speechDifficulty, oneSidedWeakness.
Preserve custom symptoms when they are explicitly mentioned.
Keep severity in range 0..10 when present.
""".trimIndent()

private fun String.reasoningEffortFor(task: OpenAiModelTask): String? =
    if (startsWith("gpt-5", ignoreCase = true)) {
        when (task) {
            OpenAiModelTask.ANALYSIS -> "medium"
            OpenAiModelTask.QUESTIONS -> "low"
            OpenAiModelTask.TRANSCRIPTION -> null
        }
    } else {
        null
    }

private fun String.temperatureFor(task: OpenAiModelTask): Double? =
    if (startsWith("gpt-5", ignoreCase = true)) {
        null
    } else {
        when (task) {
            OpenAiModelTask.ANALYSIS -> 0.2
            OpenAiModelTask.QUESTIONS -> 0.2
            OpenAiModelTask.TRANSCRIPTION -> null
        }
    }

private fun stringSchema(
    enum: List<String> = emptyList(),
): JsonObject = buildJsonObject {
    put("type", "string")
    if (enum.isNotEmpty()) {
        putJsonArray("enum") {
            enum.forEach { add(JsonPrimitive(it)) }
        }
    }
}

private fun nullableStringSchema(): JsonObject = buildJsonObject {
    putJsonArray("type") {
        add(JsonPrimitive("string"))
        add(JsonPrimitive("null"))
    }
}

private fun booleanSchema(): JsonObject = buildJsonObject {
    put("type", "boolean")
}

private fun nullableBooleanSchema(): JsonObject = buildJsonObject {
    putJsonArray("type") {
        add(JsonPrimitive("boolean"))
        add(JsonPrimitive("null"))
    }
}

private fun integerSchema(): JsonObject = buildJsonObject {
    put("type", "integer")
}

private fun nullableIntegerSchema(): JsonObject = buildJsonObject {
    putJsonArray("type") {
        add(JsonPrimitive("integer"))
        add(JsonPrimitive("null"))
    }
}

private fun arraySchema(items: JsonObject): JsonObject = buildJsonObject {
    put("type", "array")
    put("items", items)
}

private fun objectSchema(
    required: List<String>,
    properties: Map<String, JsonObject>,
): JsonObject = buildJsonObject {
    put("type", "object")
    put("additionalProperties", false)
    putJsonArray("required") {
        required.forEach { add(JsonPrimitive(it)) }
    }
    putJsonObject("properties") {
        properties.forEach { (key, value) ->
            put(key, value)
        }
    }
}

private val StringListSchema: JsonObject = arraySchema(stringSchema())

private val DynamicFieldSchema: JsonObject = objectSchema(
    required = listOf("section", "label", "value"),
    properties = linkedMapOf(
        "section" to stringSchema(),
        "label" to stringSchema(),
        "value" to stringSchema(),
    ),
)

private val QuestionDraftSchema: JsonObject = objectSchema(
    required = listOf("prompt"),
    properties = linkedMapOf(
        "category" to nullableStringSchema(),
        "stage" to nullableStringSchema(),
        "prompt" to stringSchema(),
        "short_label" to nullableStringSchema(),
        "help_text" to nullableStringSchema(),
        "answer_type" to nullableStringSchema(),
        "options" to StringListSchema,
        "priority" to nullableIntegerSchema(),
        "required" to nullableBooleanSchema(),
        "skippable" to nullableBooleanSchema(),
        "voice_allowed" to nullableBooleanSchema(),
        "export_to_clinician" to nullableBooleanSchema(),
        "red_flag_weight" to nullableIntegerSchema(),
    ),
)

private val HeadacheAnalysisSchema: JsonObject = objectSchema(
    required = listOf(
        "urgent_action",
        "user_summary",
        "clinician_summary",
        "hypotheses",
        "suspected_patterns",
        "next_questions",
        "suggested_tracking_fields",
        "suggested_attachments",
        "suggested_doctor_discussion_points",
        "suggested_specialists",
        "suggested_tests_or_evaluations",
        "self_care_general",
        "disclaimer",
        "needs_human_clinician_review",
    ),
    properties = linkedMapOf(
        "urgent_action" to objectSchema(
            required = listOf("level", "reasons", "user_message"),
            properties = linkedMapOf(
                "level" to stringSchema(listOf("NONE", "MONITOR", "DISCUSS_SOON", "URGENT", "EMERGENCY")),
                "reasons" to StringListSchema,
                "user_message" to stringSchema(),
            ),
        ),
        "user_summary" to objectSchema(
            required = listOf("plain_language_summary", "key_observations"),
            properties = linkedMapOf(
                "plain_language_summary" to stringSchema(),
                "key_observations" to StringListSchema,
            ),
        ),
        "clinician_summary" to objectSchema(
            required = listOf(
                "concise_medical_context",
                "headache_day_estimate",
                "acute_medication_use_estimate",
                "functional_impact_summary",
            ),
            properties = linkedMapOf(
                "concise_medical_context" to stringSchema(),
                "headache_day_estimate" to nullableStringSchema(),
                "acute_medication_use_estimate" to nullableStringSchema(),
                "functional_impact_summary" to nullableStringSchema(),
            ),
        ),
        "hypotheses" to arraySchema(
            objectSchema(
                required = listOf(
                    "label",
                    "confidence",
                    "rationale",
                    "supporting_signals",
                    "missing_information",
                    "discussion_with_doctor",
                ),
                properties = linkedMapOf(
                    "label" to stringSchema(),
                    "confidence" to stringSchema(listOf("LOW", "MEDIUM", "HIGH")),
                    "rationale" to stringSchema(),
                    "supporting_signals" to StringListSchema,
                    "missing_information" to StringListSchema,
                    "discussion_with_doctor" to StringListSchema,
                ),
            ),
        ),
        "suspected_patterns" to arraySchema(
            objectSchema(
                required = listOf("type", "description", "evidence"),
                properties = linkedMapOf(
                    "type" to stringSchema(),
                    "description" to stringSchema(),
                    "evidence" to StringListSchema,
                ),
            ),
        ),
        "next_questions" to arraySchema(QuestionDraftSchema),
        "suggested_tracking_fields" to StringListSchema,
        "suggested_attachments" to StringListSchema,
        "suggested_doctor_discussion_points" to StringListSchema,
        "suggested_specialists" to StringListSchema,
        "suggested_tests_or_evaluations" to StringListSchema,
        "self_care_general" to StringListSchema,
        "disclaimer" to stringSchema(),
        "needs_human_clinician_review" to booleanSchema(),
    ),
)

private val FollowUpQuestionListSchema: JsonObject = objectSchema(
    required = listOf("questions"),
    properties = linkedMapOf(
        "questions" to arraySchema(QuestionDraftSchema),
    ),
)

private val AttachmentSummarySchema: JsonObject = objectSchema(
    required = listOf("summary"),
    properties = linkedMapOf(
        "summary" to StringListSchema,
    ),
)

private val VoiceIntakeDraftSchema: JsonObject = objectSchema(
    required = listOf(
        "summary_text",
        "severity",
        "symptoms",
        "red_flags",
        "medications",
        "live_notes",
        "dynamic_fields",
    ),
    properties = linkedMapOf(
        "summary_text" to stringSchema(),
        "severity" to nullableIntegerSchema(),
        "symptoms" to StringListSchema,
        "red_flags" to StringListSchema,
        "medications" to StringListSchema,
        "live_notes" to StringListSchema,
        "dynamic_fields" to arraySchema(DynamicFieldSchema),
    ),
)

private fun OpenAiAnalysisDraft.toAnalysisResponse(
    ownerId: String,
    generatedAt: String,
    createdAt: kotlinx.datetime.Instant,
    locale: String,
    ownerType: OwnerType = OwnerType.EPISODE,
): AnalysisResponse {
    val questions = nextQuestions.mapNotNull { draft ->
        draft.toQuestionTemplateOrNull(locale = locale, createdAt = createdAt)
    }.mapIndexed { index, question ->
        question.copy(priority = question.priority.coerceAtMost((100 - index).coerceAtLeast(1)))
    }
    return AnalysisResponse(
        schemaVersion = "v1",
        analysisId = UUID.randomUUID().toString(),
        ownerType = ownerType,
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
        hypotheses = hypotheses.mapNotNull(OpenAiHypothesisDraft::toHypothesisOrNull),
        suspectedPatterns = suspectedPatterns.mapNotNull(OpenAiPatternDraft::toPatternOrNull),
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

private fun OpenAiHypothesisDraft.toHypothesisOrNull(): Hypothesis? {
    val normalizedLabel = label?.trim().orEmpty()
    if (normalizedLabel.isBlank()) return null
    return Hypothesis(
        id = UUID.randomUUID().toString(),
        label = normalizedLabel,
        confidence = confidence.toHypothesisConfidence(),
        rationale = rationale.trim(),
        supportingSignals = supportingSignals.distinct(),
        missingInformation = missingInformation.distinct(),
        discussionWithDoctor = discussionWithDoctor.distinct(),
    )
}

private fun OpenAiPatternDraft.toPatternOrNull(): SuspectedPattern? {
    val normalizedType = type?.trim().orEmpty()
    val normalizedDescription = description?.trim().orEmpty()
    if (normalizedType.isBlank() || normalizedDescription.isBlank()) return null
    return SuspectedPattern(
        type = normalizedType,
        description = normalizedDescription,
        evidence = evidence.distinct(),
    )
}

private fun OpenAiQuestionDraft.toQuestionTemplateOrNull(
    locale: String,
    createdAt: kotlinx.datetime.Instant,
): QuestionTemplate? {
    val normalizedPrompt = prompt?.trim().orEmpty()
    if (normalizedPrompt.isBlank()) return null
    return QuestionTemplate(
    id = UUID.randomUUID().toString(),
    schemaVersion = "v1",
    source = QuestionSource.API,
    category = category?.takeIf(String::isNotBlank) ?: "headache",
    stage = stage.toQuestionStage(),
    prompt = normalizedPrompt,
    shortLabel = shortLabel?.takeIf(String::isNotBlank) ?: normalizedPrompt.take(48),
    helpText = helpText?.takeIf(String::isNotBlank),
    answerType = answerType.toAnswerType(),
    options = options.distinct(),
    priority = (priority ?: 50).coerceIn(1, 100),
    required = required ?: false,
    skippable = skippable ?: true,
    voiceAllowed = voiceAllowed ?: true,
    visibleIf = null,
    exportToClinician = exportToClinician ?: true,
    redFlagWeight = redFlagWeight ?: 0,
    createdAt = createdAt,
    active = true,
)
}

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

private fun String.toBearerHeader(): String =
    if (startsWith("Bearer ", ignoreCase = true)) this else "Bearer $this"

@Module
@InstallIn(SingletonComponent::class)
abstract class CloudBindingsModule {
    @Binds
    abstract fun bindAnalysisRepository(impl: DefaultAnalysisRepository): AnalysisRepository

    @Binds
    abstract fun bindBackendStatusRepository(impl: DefaultBackendStatusRepository): BackendStatusRepository

    @Binds
    abstract fun bindOpenAiModelRepository(impl: DefaultOpenAiModelRepository): OpenAiModelRepository

    @Binds
    abstract fun bindVoiceIntakeRepository(impl: DefaultVoiceIntakeRepository): VoiceIntakeRepository

    @Binds
    abstract fun bindLocalSpeechRecognizer(impl: SherpaOnnxLocalEngine): LocalSpeechRecognizerEngine

    @Binds
    abstract fun bindCloudSpeechRecognizer(impl: BackendCloudSpeechEngine): CloudSpeechRecognizerEngine
}
