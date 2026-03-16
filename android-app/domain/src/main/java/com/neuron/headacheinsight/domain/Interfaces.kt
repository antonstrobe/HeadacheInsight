package com.neuron.headacheinsight.domain

import com.neuron.headacheinsight.core.model.Attachment
import com.neuron.headacheinsight.core.model.Episode
import com.neuron.headacheinsight.core.model.EpisodeTranscript
import com.neuron.headacheinsight.core.model.LocalSpeechPackState
import com.neuron.headacheinsight.core.model.QuestionTemplate
import kotlinx.coroutines.flow.Flow

interface SpeechRecognizerEngine {
    val engineName: String
    suspend fun isAvailable(): Boolean
    suspend fun transcribeAudio(audioPath: String, languageHint: String?): Result<EpisodeTranscript>
}

interface LocalSpeechRecognizerEngine : SpeechRecognizerEngine

interface CloudSpeechRecognizerEngine : SpeechRecognizerEngine

interface LocalSpeechPackManager {
    fun observeState(): Flow<LocalSpeechPackState>
    suspend fun refresh(languageTag: String)
    suspend fun install(languageTag: String)
}

interface QuestionEngine {
    suspend fun selectQuestionsForEpisode(episode: Episode): List<QuestionTemplate>
}

interface LocalRuleQuestionEngine : QuestionEngine

interface AttachmentExtractor {
    suspend fun extract(attachment: Attachment): Result<Attachment>
}

interface AnalysisService {
    suspend fun analyzeEpisode(episodeId: String): Result<String>
}

interface SyncScheduler {
    suspend fun enqueueEpisodeSync(episodeId: String)
    suspend fun enqueueTranscription(audioPath: String, episodeId: String)
}

interface AudioRecorder {
    suspend fun start(episodeId: String): Result<String>
    suspend fun stop(): Result<String?>
}

interface ClientIdentityProvider {
    suspend fun installId(): String
    suspend fun publicKey(): String
    suspend fun signature(payload: String): String
}

interface LocationSnapshotProvider {
    suspend fun coarseLocation(): String?
}

interface ReportExporter {
    suspend fun exportReports(): Result<List<String>>
}

interface AuthProvider

interface HealthConnectAdapter

interface IntegrationAdapter

interface QuestionSeedSource {
    fun observeSeedVersion(): Flow<String?>
    suspend fun loadSeedQuestions(languageTag: String): List<QuestionTemplate>
}
