package com.neuron.headacheinsight.data.repository

import android.content.Context
import android.graphics.pdf.PdfDocument
import androidx.datastore.core.DataStore
import com.neuron.headacheinsight.core.common.TimeProvider
import com.neuron.headacheinsight.core.model.AnalysisSnapshot
import com.neuron.headacheinsight.core.model.AppSettings
import com.neuron.headacheinsight.core.model.Attachment
import com.neuron.headacheinsight.core.model.BaselineQuestionAnswer
import com.neuron.headacheinsight.core.model.Episode
import com.neuron.headacheinsight.core.model.EpisodeContext
import com.neuron.headacheinsight.core.model.EpisodeDetail
import com.neuron.headacheinsight.core.model.EpisodeMedication
import com.neuron.headacheinsight.core.model.EpisodeStatus
import com.neuron.headacheinsight.core.model.EpisodeSymptom
import com.neuron.headacheinsight.core.model.EpisodeTranscript
import com.neuron.headacheinsight.core.model.ExportArtifact
import com.neuron.headacheinsight.core.model.ExportType
import com.neuron.headacheinsight.core.model.HomeDashboard
import com.neuron.headacheinsight.core.model.InsightSummary
import com.neuron.headacheinsight.core.model.LocalRedFlagEvaluation
import com.neuron.headacheinsight.core.model.OwnerScope
import com.neuron.headacheinsight.core.model.QuestionSource
import com.neuron.headacheinsight.core.model.QuestionStage
import com.neuron.headacheinsight.core.model.QuestionTemplate
import com.neuron.headacheinsight.core.model.RedFlagEvent
import com.neuron.headacheinsight.core.model.RedFlagStatus
import com.neuron.headacheinsight.core.model.ReportBundle
import com.neuron.headacheinsight.core.model.SyncQueueItem
import com.neuron.headacheinsight.core.model.SyncStatus
import com.neuron.headacheinsight.core.model.UserProfile
import com.neuron.headacheinsight.data.local.AnalysisDao
import com.neuron.headacheinsight.data.local.AnalysisSnapshotEntity
import com.neuron.headacheinsight.data.local.AttachmentDao
import com.neuron.headacheinsight.data.local.AttachmentEntity
import com.neuron.headacheinsight.data.local.EpisodeContextEntity
import com.neuron.headacheinsight.data.local.EpisodeContextDao
import com.neuron.headacheinsight.data.local.EpisodeDao
import com.neuron.headacheinsight.data.local.EpisodeMedicationDao
import com.neuron.headacheinsight.data.local.EpisodeMedicationEntity
import com.neuron.headacheinsight.data.local.EpisodeSymptomEntity
import com.neuron.headacheinsight.data.local.EpisodeSymptomDao
import com.neuron.headacheinsight.data.local.EpisodeTranscriptEntity
import com.neuron.headacheinsight.data.local.EpisodeTranscriptDao
import com.neuron.headacheinsight.data.local.EpisodeEntity
import com.neuron.headacheinsight.data.local.ExportDao
import com.neuron.headacheinsight.data.local.ProfileDao
import com.neuron.headacheinsight.data.local.QuestionAnswerEntity
import com.neuron.headacheinsight.data.local.QuestionAnswerDao
import com.neuron.headacheinsight.data.local.QuestionDao
import com.neuron.headacheinsight.data.local.RedFlagEventEntity
import com.neuron.headacheinsight.data.local.RedFlagDao
import com.neuron.headacheinsight.data.local.SyncQueueDao
import com.neuron.headacheinsight.data.local.toEntity
import com.neuron.headacheinsight.data.local.toModel
import com.neuron.headacheinsight.domain.AttachmentRepository
import com.neuron.headacheinsight.domain.EpisodeRepository
import com.neuron.headacheinsight.domain.InsightRepository
import com.neuron.headacheinsight.domain.LocalRuleQuestionEngine
import com.neuron.headacheinsight.domain.ProfileRepository
import com.neuron.headacheinsight.domain.QuestionRepository
import com.neuron.headacheinsight.domain.ReportRepository
import com.neuron.headacheinsight.domain.SafetyRepository
import com.neuron.headacheinsight.domain.SettingsRepository
import com.neuron.headacheinsight.domain.SyncRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Serializable
private data class AllDataExportSnapshot(
    val exportedAt: String,
    val settings: AppSettings,
    val profile: UserProfile?,
    val insights: InsightSummary,
    val episodes: List<EpisodeDetail>,
    val attachments: List<Attachment>,
    val reports: ReportBundle,
)

@Singleton
class DefaultProfileRepository @Inject constructor(
    private val profileDao: ProfileDao,
    private val json: Json,
) : ProfileRepository {
    override fun observeProfile(): Flow<UserProfile?> = profileDao.observeProfile().map { it?.toModel() }

    override suspend fun upsertProfile(profile: UserProfile) {
        profileDao.upsertProfile(profile.toEntity())
    }

    override suspend fun upsertBaselineAnswers(answers: List<BaselineQuestionAnswer>) {
        profileDao.upsertBaselineAnswers(answers.map { it.toEntity(json) })
    }

    override fun observeBaselineAnswers(): Flow<List<BaselineQuestionAnswer>> =
        profileDao.observeBaselineAnswers().map { list -> list.map { it.toModel(json) } }
}

@Singleton
class DefaultSettingsRepository @Inject constructor(
    private val dataStore: DataStore<AppSettings>,
) : SettingsRepository {
    override fun observeSettings(): Flow<AppSettings> = dataStore.data

    override suspend fun updateSettings(transform: (AppSettings) -> AppSettings) {
        dataStore.updateData { current -> transform(current) }
    }
}

@Singleton
class DefaultEpisodeRepository @Inject constructor(
    private val episodeDao: EpisodeDao,
    private val contextDao: EpisodeContextDao,
    private val symptomDao: EpisodeSymptomDao,
    private val medicationDao: EpisodeMedicationDao,
    private val transcriptDao: EpisodeTranscriptDao,
    private val questionAnswerDao: QuestionAnswerDao,
    private val attachmentDao: AttachmentDao,
    private val analysisDao: AnalysisDao,
    private val redFlagDao: RedFlagDao,
    private val json: Json,
    private val timeProvider: TimeProvider,
) : EpisodeRepository {
    override fun observeEpisodes(): Flow<List<Episode>> = episodeDao.observeEpisodes().map { list ->
        list.map { it.toModel(json) }
    }

    override fun observeActiveEpisode(): Flow<Episode?> = episodeDao.observeActiveEpisode().map { it?.toModel(json) }

    override fun observeEpisodeDetail(episodeId: String): Flow<EpisodeDetail?> = combine(
        episodeDao.observeEpisode(episodeId),
        contextDao.observeContext(episodeId),
        symptomDao.observeSymptoms(episodeId),
        medicationDao.observeByEpisode(episodeId),
        transcriptDao.observeByEpisode(episodeId),
        questionAnswerDao.observeByEpisode(episodeId),
        attachmentDao.observeByOwner(episodeId),
        analysisDao.observeByOwner(episodeId),
        redFlagDao.observeByEpisode(episodeId),
    ) { values ->
        val episode = values[0] as EpisodeEntity?
        val context = values[1] as EpisodeContextEntity?
        val symptoms = values[2] as List<EpisodeSymptomEntity>
        val meds = values[3] as List<EpisodeMedicationEntity>
        val transcripts = values[4] as List<EpisodeTranscriptEntity>
        val answers = values[5] as List<QuestionAnswerEntity>
        val attachments = values[6] as List<AttachmentEntity>
        val analyses = values[7] as List<AnalysisSnapshotEntity>
        val redFlags = values[8] as List<RedFlagEventEntity>
        episode?.let {
            EpisodeDetail(
                episode = it.toModel(json),
                symptoms = symptoms.map { symptom -> symptom.toModel() },
                context = context?.toModel(),
                medications = meds.map { med -> med.toModel() },
                transcripts = transcripts.map { transcript -> transcript.toModel() },
                answers = answers.map { answer -> answer.toModel(json) },
                attachments = attachments.map { attachment -> attachment.toModel() },
                analyses = analyses.map { analysis -> analysis.toModel() },
                redFlags = redFlags.map { flag -> flag.toModel() },
            )
        }
    }

    override suspend fun createEpisode(episode: Episode) {
        episodeDao.upsertEpisode(episode.toEntity(json))
    }

    override suspend fun updateEpisode(episode: Episode) {
        episodeDao.upsertEpisode(episode.copy(updatedAt = timeProvider.now()).toEntity(json))
    }

    override suspend fun setEpisodeContext(context: EpisodeContext) {
        contextDao.upsertContext(context.toEntity())
    }

    override suspend fun replaceSymptoms(episodeId: String, symptoms: List<EpisodeSymptom>) {
        symptomDao.deleteByEpisode(episodeId)
        symptomDao.insertAll(symptoms.map { it.toEntity() })
    }

    override suspend fun upsertMedication(medication: EpisodeMedication) {
        medicationDao.upsert(medication.toEntity())
    }

    override suspend fun upsertTranscript(transcript: EpisodeTranscript) {
        transcriptDao.upsert(transcript.toEntity())
    }

    override suspend fun completeEpisode(episodeId: String) {
        episodeDao.completeEpisode(episodeId, timeProvider.now().toEpochMilliseconds())
    }

    override suspend fun saveQuestionAnswers(answers: List<com.neuron.headacheinsight.core.model.QuestionAnswer>) {
        questionAnswerDao.upsertAll(answers.map { it.toEntity(json) })
    }

    override suspend fun saveRedFlagEvents(events: List<RedFlagEvent>) {
        redFlagDao.upsertAll(events.map { it.toEntity() })
    }

    override suspend fun upsertAnalysis(snapshot: AnalysisSnapshot) {
        analysisDao.upsert(snapshot.toEntity())
    }
}

@Singleton
class DefaultQuestionRepository @Inject constructor(
    private val questionDao: QuestionDao,
    private val questionAnswerDao: QuestionAnswerDao,
    private val json: Json,
) : QuestionRepository {
    override fun observeActiveQuestions(stage: QuestionStage): Flow<List<QuestionTemplate>> =
        questionDao.observeActiveQuestions(stage.name).map { list -> list.map { it.toModel(json) } }

    override fun observeQuestionsForEpisode(episodeId: String): Flow<List<QuestionTemplate>> =
        combine(
            questionDao.observeAllActiveQuestions(),
            questionAnswerDao.observeByEpisode(episodeId),
        ) { questions, answers ->
            val answered = answers.map { it.questionId }.toSet()
            questions
                .map { it.toModel(json) }
                .filter { it.id !in answered }
        }

    override suspend fun replaceSeedQuestions(questions: List<QuestionTemplate>, seedVersion: String) {
        questionDao.replaceSeed(questions.map { it.copy(schemaVersion = seedVersion).toEntity(json) })
    }

    override suspend fun upsertGeneratedQuestions(questions: List<QuestionTemplate>) {
        questionDao.upsertAll(questions.map { it.toEntity(json) })
    }

    override suspend fun deactivateQuestions(ids: List<String>) {
        if (ids.isNotEmpty()) {
            questionDao.deactivate(ids)
        }
    }
}

@Singleton
class DeterministicLocalRuleQuestionEngine @Inject constructor(
    private val questionDao: QuestionDao,
    private val json: Json,
) : LocalRuleQuestionEngine {
    override suspend fun selectQuestionsForEpisode(episode: Episode): List<QuestionTemplate> {
        val stage = if (episode.status == EpisodeStatus.ACTIVE) QuestionStage.ACUTE_DETAIL else QuestionStage.REVIEW
        val candidates = questionDao.observeActiveQuestions(stage.name).first().map { it.toModel(json) }
        val currentSeverity = episode.currentSeverity
        return candidates.filter { question ->
            when {
                question.category == "symptoms_and_aura" && currentSeverity != null && currentSeverity >= 6 -> true
                question.category == "triggers_and_context" && episode.status == EpisodeStatus.ACTIVE -> true
                question.category == "review_and_followup" && episode.status != EpisodeStatus.ACTIVE -> true
                question.source == QuestionSource.LOCAL_RULE -> true
                else -> question.priority >= 7
            }
        }.take(8)
    }
}

@Singleton
class DefaultAttachmentRepository @Inject constructor(
    private val attachmentDao: AttachmentDao,
) : AttachmentRepository {
    override fun observeAttachments(): Flow<List<Attachment>> = attachmentDao.observeAll().map { list ->
        list.map { it.toModel() }
    }

    override fun observeAttachmentsForOwner(ownerId: String): Flow<List<Attachment>> = attachmentDao.observeByOwner(ownerId).map { list ->
        list.map { it.toModel() }
    }

    override suspend fun upsertAttachment(attachment: Attachment) {
        attachmentDao.upsert(attachment.toEntity())
    }
}

@Singleton
class DefaultSyncRepository @Inject constructor(
    private val syncQueueDao: SyncQueueDao,
) : SyncRepository {
    override fun observeQueue(): Flow<List<SyncQueueItem>> = syncQueueDao.observeQueue().map { list ->
        list.map { it.toModel() }
    }

    override suspend fun enqueue(item: SyncQueueItem) {
        syncQueueDao.upsert(item.toEntity())
    }

    override suspend fun markRunning(id: String) {
        syncQueueDao.updateStatus(id, SyncStatus.RUNNING.name, null)
    }

    override suspend fun markFailed(id: String, reason: String) {
        syncQueueDao.updateStatus(id, SyncStatus.FAILED.name, reason)
    }

    override suspend fun markComplete(id: String) {
        syncQueueDao.updateStatus(id, SyncStatus.COMPLETE.name, null)
    }
}

@Singleton
class DefaultSafetyRepository @Inject constructor(
    private val redFlagDao: RedFlagDao,
    private val timeProvider: TimeProvider,
) : SafetyRepository {
    override suspend fun evaluateAndStore(
        episodeId: String,
        evaluation: LocalRedFlagEvaluation,
    ): List<RedFlagEvent> {
        if (evaluation.reasons.isEmpty()) return emptyList()
        val events = evaluation.reasons.mapIndexed { index, reason ->
            RedFlagEvent(
                id = UUID.randomUUID().toString(),
                episodeId = episodeId,
                ruleCode = "RF_${evaluation.status.name}_$index",
                severity = evaluation.status,
                triggeredAt = timeProvider.now(),
                acknowledged = false,
            )
        }
        redFlagDao.upsertAll(events.map { it.toEntity() })
        return events
    }
}

@Singleton
class DefaultInsightRepository @Inject constructor(
    private val episodeRepository: EpisodeRepository,
    private val settingsRepository: SettingsRepository,
    private val syncRepository: SyncRepository,
) : InsightRepository {
    override fun observeHomeDashboard(): Flow<HomeDashboard> = combine(
        episodeRepository.observeActiveEpisode(),
        episodeRepository.observeEpisodes(),
        settingsRepository.observeSettings(),
        syncRepository.observeQueue(),
    ) { active, episodes, settings, queue ->
        HomeDashboard(
            pendingEpisode = active,
            lastEpisode = episodes.firstOrNull(),
            monthlyHeadacheDays = episodes.take(30).count(),
            cloudEnabled = settings.cloudAnalysisEnabled,
            queueCount = queue.count { it.status != SyncStatus.COMPLETE },
        )
    }

    override fun observeInsightSummary(): Flow<InsightSummary> = episodeRepository.observeEpisodes().map { episodes ->
        val completed = episodes.filter { it.status != EpisodeStatus.ARCHIVED }
        InsightSummary(
            monthlyHeadacheDays = completed.take(30).count(),
            averageSeverity = completed.mapNotNull { it.currentSeverity }.average().takeIf { !it.isNaN() } ?: 0.0,
            medicationDays = completed.count { it.summaryText?.contains("med", ignoreCase = true) == true },
            functionalImpactDays = completed.count { (it.currentSeverity ?: 0) >= 7 },
            suspectedTriggers = listOf("sleep_disruption", "stress", "hydration"),
        )
    }
}

@Singleton
class DefaultReportRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val episodeRepository: EpisodeRepository,
    private val insightRepository: InsightRepository,
    private val attachmentRepository: AttachmentRepository,
    private val profileRepository: ProfileRepository,
    private val settingsRepository: SettingsRepository,
    private val exportDao: ExportDao,
    private val timeProvider: TimeProvider,
    private val json: Json,
) : ReportRepository {
    override suspend fun buildReports(): ReportBundle {
        val settings = settingsRepository.observeSettings().first()
        val russian = settings.languageTag.startsWith("ru", ignoreCase = true)
        val episodes = episodeRepository.observeEpisodes().first()
        val episodeDetails = episodes.mapNotNull { episodeRepository.observeEpisodeDetail(it.id).first() }
        val insights = insightRepository.observeInsightSummary().first()
        val attachments = attachmentRepository.observeAttachments().first()
        val profile = profileRepository.observeProfile().first()
        val patientText = buildString {
            if (russian) {
                appendLine("HeadacheInsight: отчёт для пациента")
                appendLine("Дней с головной болью за месяц: ${insights.monthlyHeadacheDays}")
                appendLine("Средний уровень боли: ${"%.1f".format(insights.averageSeverity)}")
                appendLine("Предполагаемые триггеры: ${insights.suspectedTriggers.joinToString { localizeTrigger(it, true) }}")
            } else {
                appendLine("HeadacheInsight Patient Report")
                appendLine("Headache days this month: ${insights.monthlyHeadacheDays}")
                appendLine("Average severity: ${"%.1f".format(insights.averageSeverity)}")
                appendLine("Potential triggers: ${insights.suspectedTriggers.joinToString { localizeTrigger(it, false) }}")
            }
        }
        val clinicianText = buildString {
            if (russian) {
                appendLine("HeadacheInsight: отчёт для врача")
                appendLine("Записано эпизодов: ${episodes.size}")
                appendLine("Дней приёма лекарств: ${insights.medicationDays}")
                appendLine("Дней с функциональным влиянием: ${insights.functionalImpactDays}")
            } else {
                appendLine("HeadacheInsight Clinician Report")
                appendLine("Episodes recorded: ${episodes.size}")
                appendLine("Medication days: ${insights.medicationDays}")
                appendLine("Functional impact days: ${insights.functionalImpactDays}")
            }
        }
        val csv = buildString {
            appendLine("episode_id,status,started_at,severity,red_flag")
            episodes.forEach { episode ->
                appendLine("${episode.id},${episode.status},${episode.startedAt},${episode.currentSeverity ?: ""},${episode.redFlagStatus}")
            }
        }
        val jsonPayload = json.encodeToString(
            AllDataExportSnapshot.serializer(),
            AllDataExportSnapshot(
                exportedAt = timeProvider.now().toString(),
                settings = settings,
                profile = profile,
                insights = insights,
                episodes = episodeDetails,
                attachments = attachments,
                reports = ReportBundle(patientText, clinicianText, csv, ""),
            ),
        )
        return ReportBundle(
            patientText = patientText,
            clinicianText = clinicianText,
            csv = csv,
            json = jsonPayload,
        )
    }

    override suspend fun exportReports(bundle: ReportBundle): List<ExportArtifact> {
        val exportDir = File(context.filesDir, "exports").apply { mkdirs() }
        val timestamp = timeProvider.now().toEpochMilliseconds()
        val patientPdf = File(exportDir, "patient_report_$timestamp.pdf")
        val csvFile = File(exportDir, "clinician_report_$timestamp.csv")
        val jsonFile = File(exportDir, "report_bundle_$timestamp.json")

        writePdf(patientPdf, bundle.patientText + "\n\n" + bundle.clinicianText)
        csvFile.writeText(bundle.csv)
        jsonFile.writeText(bundle.json)

        val artifacts = listOf(
            ExportArtifact(UUID.randomUUID().toString(), ExportType.PDF, OwnerScope.REPORTS, patientPdf.absolutePath, timeProvider.now()),
            ExportArtifact(UUID.randomUUID().toString(), ExportType.CSV, OwnerScope.REPORTS, csvFile.absolutePath, timeProvider.now()),
            ExportArtifact(UUID.randomUUID().toString(), ExportType.JSON, OwnerScope.REPORTS, jsonFile.absolutePath, timeProvider.now()),
        )
        exportDao.upsertAll(artifacts.map { it.toEntity() })
        return artifacts
    }

    private fun writePdf(target: File, content: String) {
        val document = PdfDocument()
        val pageInfo = PdfDocument.PageInfo.Builder(595, 842, 1).create()
        val page = document.startPage(pageInfo)
        val canvas = page.canvas
        val lines = content.lines()
        var y = 48f
        lines.forEach { line ->
            canvas.drawText(line, 32f, y, android.graphics.Paint().apply { textSize = 14f })
            y += 20f
        }
        document.finishPage(page)
        target.outputStream().use(document::writeTo)
        document.close()
    }

    private fun localizeTrigger(code: String, russian: Boolean): String = when (code) {
        "sleep_disruption" -> if (russian) "сбой сна" else "sleep disruption"
        "stress" -> if (russian) "стресс" else "stress"
        "hydration" -> if (russian) "гидратация" else "hydration"
        else -> code
    }
}

@Module
@InstallIn(SingletonComponent::class)
abstract class RepositoryBindingsModule {
    @Binds
    abstract fun bindProfileRepository(impl: DefaultProfileRepository): ProfileRepository

    @Binds
    abstract fun bindSettingsRepository(impl: DefaultSettingsRepository): SettingsRepository

    @Binds
    abstract fun bindEpisodeRepository(impl: DefaultEpisodeRepository): EpisodeRepository

    @Binds
    abstract fun bindQuestionRepository(impl: DefaultQuestionRepository): QuestionRepository

    @Binds
    abstract fun bindAttachmentRepository(impl: DefaultAttachmentRepository): AttachmentRepository

    @Binds
    abstract fun bindSyncRepository(impl: DefaultSyncRepository): SyncRepository

    @Binds
    abstract fun bindInsightRepository(impl: DefaultInsightRepository): InsightRepository

    @Binds
    abstract fun bindSafetyRepository(impl: DefaultSafetyRepository): SafetyRepository

    @Binds
    abstract fun bindReportRepository(impl: DefaultReportRepository): ReportRepository

    @Binds
    abstract fun bindLocalRuleQuestionEngine(impl: DeterministicLocalRuleQuestionEngine): LocalRuleQuestionEngine
}
