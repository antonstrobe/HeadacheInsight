package com.neuron.headacheinsight.domain

import com.neuron.headacheinsight.core.model.AnalysisResponse
import com.neuron.headacheinsight.core.model.AnalysisSnapshot
import com.neuron.headacheinsight.core.model.AppSettings
import com.neuron.headacheinsight.core.model.Attachment
import com.neuron.headacheinsight.core.model.BaselineQuestionAnswer
import com.neuron.headacheinsight.core.model.CloudCredentials
import com.neuron.headacheinsight.core.model.Episode
import com.neuron.headacheinsight.core.model.EpisodeContext
import com.neuron.headacheinsight.core.model.EpisodeDetail
import com.neuron.headacheinsight.core.model.EpisodeMedication
import com.neuron.headacheinsight.core.model.EpisodeStatus
import com.neuron.headacheinsight.core.model.EpisodeSymptom
import com.neuron.headacheinsight.core.model.EpisodeTranscript
import com.neuron.headacheinsight.core.model.ExportArtifact
import com.neuron.headacheinsight.core.model.HomeDashboard
import com.neuron.headacheinsight.core.model.InsightSummary
import com.neuron.headacheinsight.core.model.LocalRedFlagEvaluation
import com.neuron.headacheinsight.core.model.QuestionAnswer
import com.neuron.headacheinsight.core.model.QuestionStage
import com.neuron.headacheinsight.core.model.QuestionTemplate
import com.neuron.headacheinsight.core.model.RedFlagEvent
import com.neuron.headacheinsight.core.model.ReportBundle
import com.neuron.headacheinsight.core.model.SyncQueueItem
import com.neuron.headacheinsight.core.model.UserProfile
import kotlinx.coroutines.flow.Flow

interface ProfileRepository {
    fun observeProfile(): Flow<UserProfile?>
    suspend fun upsertProfile(profile: UserProfile)
    suspend fun upsertBaselineAnswers(answers: List<BaselineQuestionAnswer>)
    fun observeBaselineAnswers(): Flow<List<BaselineQuestionAnswer>>
}

interface SettingsRepository {
    fun observeSettings(): Flow<AppSettings>
    suspend fun updateSettings(transform: (AppSettings) -> AppSettings)
}

interface CloudCredentialsRepository {
    fun observeCredentials(): Flow<CloudCredentials>
    suspend fun saveCredentials(credentials: CloudCredentials)
}

interface EpisodeRepository {
    fun observeEpisodes(): Flow<List<Episode>>
    fun observeActiveEpisode(): Flow<Episode?>
    fun observeEpisodeDetail(episodeId: String): Flow<EpisodeDetail?>
    suspend fun createEpisode(episode: Episode)
    suspend fun updateEpisode(episode: Episode)
    suspend fun setEpisodeContext(context: EpisodeContext)
    suspend fun replaceSymptoms(episodeId: String, symptoms: List<EpisodeSymptom>)
    suspend fun upsertMedication(medication: EpisodeMedication)
    suspend fun upsertTranscript(transcript: EpisodeTranscript)
    suspend fun completeEpisode(episodeId: String)
    suspend fun saveQuestionAnswers(answers: List<QuestionAnswer>)
    suspend fun saveRedFlagEvents(events: List<RedFlagEvent>)
    suspend fun upsertAnalysis(snapshot: AnalysisSnapshot)
}

interface QuestionRepository {
    fun observeActiveQuestions(stage: QuestionStage): Flow<List<QuestionTemplate>>
    fun observeQuestionsForEpisode(episodeId: String): Flow<List<QuestionTemplate>>
    suspend fun replaceSeedQuestions(questions: List<QuestionTemplate>, seedVersion: String)
    suspend fun upsertGeneratedQuestions(questions: List<QuestionTemplate>)
    suspend fun deactivateQuestions(ids: List<String>)
}

interface AttachmentRepository {
    fun observeAttachments(): Flow<List<Attachment>>
    fun observeAttachmentsForOwner(ownerId: String): Flow<List<Attachment>>
    suspend fun upsertAttachment(attachment: Attachment)
}

interface AnalysisRepository {
    fun observeLatestAnalysis(ownerId: String): Flow<AnalysisSnapshot?>
    suspend fun analyzeEpisode(ownerId: String): Result<AnalysisResponse>
    suspend fun generateFollowUpQuestions(ownerId: String): Result<List<QuestionTemplate>>
    suspend fun analyzeAttachments(ownerId: String): Result<List<String>>
}

interface SyncRepository {
    fun observeQueue(): Flow<List<SyncQueueItem>>
    suspend fun enqueue(item: SyncQueueItem)
    suspend fun markRunning(id: String)
    suspend fun markFailed(id: String, reason: String)
    suspend fun markComplete(id: String)
}

interface ReportRepository {
    suspend fun buildReports(): ReportBundle
    suspend fun exportReports(bundle: ReportBundle): List<ExportArtifact>
}

interface InsightRepository {
    fun observeHomeDashboard(): Flow<HomeDashboard>
    fun observeInsightSummary(): Flow<InsightSummary>
}

interface SafetyRepository {
    suspend fun evaluateAndStore(episodeId: String, evaluation: LocalRedFlagEvaluation): List<RedFlagEvent>
}
