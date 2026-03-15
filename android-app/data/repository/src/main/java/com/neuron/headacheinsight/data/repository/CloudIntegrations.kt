package com.neuron.headacheinsight.data.repository

import android.content.Context
import com.neuron.headacheinsight.core.common.TimeProvider
import com.neuron.headacheinsight.core.model.AnalysisResponse
import com.neuron.headacheinsight.core.model.AnalysisSnapshot
import com.neuron.headacheinsight.core.model.AnalysisSource
import com.neuron.headacheinsight.core.model.AnalysisStatus
import com.neuron.headacheinsight.core.model.EpisodeDetail
import com.neuron.headacheinsight.core.model.EpisodeTranscript
import com.neuron.headacheinsight.core.model.OwnerType
import com.neuron.headacheinsight.core.model.QuestionTemplate
import com.neuron.headacheinsight.core.model.TranscriptVariant
import com.neuron.headacheinsight.data.remote.AnalyzeAttachmentsRequest
import com.neuron.headacheinsight.data.remote.AnalyzeEpisodeRequest
import com.neuron.headacheinsight.data.remote.BackendApi
import com.neuron.headacheinsight.data.remote.GenerateFollowUpQuestionsRequest
import com.neuron.headacheinsight.data.remote.TranscribeMetadata
import com.neuron.headacheinsight.domain.AnalysisRepository
import com.neuron.headacheinsight.domain.AttachmentRepository
import com.neuron.headacheinsight.domain.CloudSpeechRecognizerEngine
import com.neuron.headacheinsight.domain.EpisodeRepository
import com.neuron.headacheinsight.domain.LocalSpeechRecognizerEngine
import com.neuron.headacheinsight.domain.ProfileRepository
import com.neuron.headacheinsight.domain.QuestionRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.flow.first
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.File
import java.security.MessageDigest
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DefaultAnalysisRepository @Inject constructor(
    private val backendApi: BackendApi,
    private val episodeRepository: EpisodeRepository,
    private val profileRepository: ProfileRepository,
    private val questionRepository: QuestionRepository,
    private val attachmentRepository: AttachmentRepository,
    private val timeProvider: TimeProvider,
    private val json: Json,
) : AnalysisRepository {
    override fun observeLatestAnalysis(ownerId: String) = kotlinx.coroutines.flow.flow {
        emit(episodeRepository.observeEpisodeDetail(ownerId).first()?.analyses?.firstOrNull())
    }

    override suspend fun analyzeEpisode(ownerId: String): Result<AnalysisResponse> = runCatching {
        val episode = requireNotNull(episodeRepository.observeEpisodeDetail(ownerId).first()) { "Episode not found" }
        val locale = episode.episode.locale
        val response = backendApi.analyzeEpisode(
            AnalyzeEpisodeRequest(
                ownerId = ownerId,
                locale = locale,
                episode = episode,
            ),
        )
        persistAnalysis(ownerId, response)
        if (response.nextQuestions.isNotEmpty()) {
            questionRepository.upsertGeneratedQuestions(response.nextQuestions)
        }
        response
    }

    override suspend fun generateFollowUpQuestions(ownerId: String): Result<List<QuestionTemplate>> = runCatching {
        val episode = requireNotNull(episodeRepository.observeEpisodeDetail(ownerId).first()) { "Episode not found" }
        val response = backendApi.generateFollowUpQuestions(
            GenerateFollowUpQuestionsRequest(
                ownerId = ownerId,
                locale = episode.episode.locale,
                episode = episode,
            ),
        )
        questionRepository.upsertGeneratedQuestions(response.questions)
        response.questions
    }

    override suspend fun analyzeAttachments(ownerId: String): Result<List<String>> = runCatching {
        val attachments = attachmentRepository.observeAttachmentsForOwner(ownerId).first()
        val locale = episodeRepository.observeEpisodeDetail(ownerId).first()?.episode?.locale ?: "en-US"
        backendApi.analyzeAttachments(
            AnalyzeAttachmentsRequest(
                ownerId = ownerId,
                locale = locale,
                attachments = attachments,
            ),
        ).summary
    }

    private suspend fun persistAnalysis(ownerId: String, response: AnalysisResponse) {
        val snapshot = AnalysisSnapshot(
            id = response.analysisId,
            ownerType = OwnerType.EPISODE,
            ownerId = ownerId,
            schemaVersion = response.schemaVersion,
            requestPayloadJson = "{}",
            responsePayloadJson = json.encodeToString(AnalysisResponse.serializer(), response),
            modelName = "gpt-4.1",
            createdAt = timeProvider.now(),
            source = AnalysisSource.CLOUD,
            status = AnalysisStatus.COMPLETE,
        )
        episodeRepository.upsertAnalysis(snapshot)
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
    private val timeProvider: TimeProvider,
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
    private val timeProvider: TimeProvider,
    private val json: Json,
) : CloudSpeechRecognizerEngine {
    override val engineName: String = "backend-openai"

    override suspend fun isAvailable(): Boolean = true

    override suspend fun transcribeAudio(audioPath: String, languageHint: String?): Result<EpisodeTranscript> = runCatching {
        val audioFile = File(audioPath)
        val episodeId = audioFile.nameWithoutExtension.substringBefore("_").ifBlank { audioFile.nameWithoutExtension }
        val metadata = json.encodeToString(
            TranscribeMetadata.serializer(),
            TranscribeMetadata(
                episodeId = episodeId,
                locale = languageHint ?: "auto",
                languageHint = languageHint,
            ),
        ).toRequestBody("application/json".toMediaType())
        val response = backendApi.transcribe(
            file = MultipartBody.Part.createFormData(
                name = "file",
                filename = audioFile.name,
                body = audioFile.asRequestBody("audio/wav".toMediaType()),
            ),
            metadata = metadata,
        )
        EpisodeTranscript(
            id = MessageDigest.getInstance("SHA-256").digest(audioFile.absolutePath.encodeToByteArray()).joinToString("") { "%02x".format(it) },
            episodeId = response.episodeId,
            rawAudioPath = audioPath,
            transcriptText = response.transcriptText,
            language = response.language,
            engineType = response.engineType,
            variant = TranscriptVariant.CLOUD,
            confidence = response.confidence,
            createdAt = timeProvider.now(),
        )
    }
}

@Module
@InstallIn(SingletonComponent::class)
abstract class CloudBindingsModule {
    @Binds
    abstract fun bindAnalysisRepository(impl: DefaultAnalysisRepository): AnalysisRepository

    @Binds
    abstract fun bindLocalSpeechRecognizer(impl: SherpaOnnxLocalEngine): LocalSpeechRecognizerEngine

    @Binds
    abstract fun bindCloudSpeechRecognizer(impl: BackendCloudSpeechEngine): CloudSpeechRecognizerEngine
}
