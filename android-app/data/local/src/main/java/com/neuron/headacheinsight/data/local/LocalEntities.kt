package com.neuron.headacheinsight.data.local

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(tableName = "user_profile")
data class UserProfileEntity(
    @PrimaryKey val id: String,
    val displayName: String?,
    val locale: String,
    val timezone: String,
    val cityRegion: String?,
    val sleepSchedule: String?,
    val wakeSchedule: String?,
    val workSchedule: String?,
    val baselineNotes: String?,
    val cloudAnalysisEnabled: Boolean,
    val locationConsent: Boolean,
    val attachmentUploadConsent: Boolean,
    val createdAtEpochMs: Long,
    val updatedAtEpochMs: Long,
)

@Entity(
    tableName = "baseline_question_answer",
    indices = [Index("questionId")],
)
data class BaselineQuestionAnswerEntity(
    @PrimaryKey val id: String,
    val questionId: String,
    val answerPayloadJson: String,
    val source: String,
    val answeredAtEpochMs: Long,
)

@Entity(
    tableName = "episode",
    indices = [Index("status"), Index("startedAtEpochMs")],
)
data class EpisodeEntity(
    @PrimaryKey val id: String,
    val status: String,
    val startedAtEpochMs: Long,
    val endedAtEpochMs: Long?,
    val timezone: String,
    val locale: String,
    val cityRegionSnapshot: String?,
    val startConfidence: String,
    val currentSeverity: Int?,
    val peakSeverity: Int?,
    val painType: String?,
    val painLocationsJson: String,
    val summaryText: String?,
    val transcriptStatus: String,
    val cloudAnalysisStatus: String,
    val redFlagStatus: String,
    val networkConnectedAtStart: Boolean,
    val createdAtEpochMs: Long,
    val updatedAtEpochMs: Long,
)

@Entity(
    tableName = "episode_symptom",
    primaryKeys = ["episodeId", "symptomCode"],
    indices = [Index("episodeId")],
)
data class EpisodeSymptomEntity(
    val episodeId: String,
    val symptomCode: String,
    val intensity: Int?,
    val present: Boolean,
    val notes: String?,
)

@Entity(
    tableName = "episode_context",
    primaryKeys = ["episodeId"],
)
data class EpisodeContextEntity(
    val episodeId: String,
    val sleepHours: Double?,
    val hydration: String?,
    val mealsSkipped: Boolean?,
    val caffeine: String?,
    val alcohol: String?,
    val stressLevel: Int?,
    val screenExposure: String?,
    val physicalExertion: String?,
    val weatherNote: String?,
    val menstrualOrHormonalContext: String?,
    val infectionFever: Boolean?,
    val headInjuryRecent: Boolean?,
    val environmentNotes: String?,
)

@Entity(
    tableName = "episode_medication",
    indices = [Index("episodeId")],
)
data class EpisodeMedicationEntity(
    @PrimaryKey val id: String,
    val episodeId: String,
    val medicineName: String,
    val dose: String?,
    val takenAtEpochMs: Long?,
    val reliefLevel: Int?,
    val sideEffects: String?,
    val source: String,
)

@Entity(
    tableName = "episode_transcript",
    indices = [Index("episodeId"), Index(value = ["episodeId", "variant"], unique = true)],
)
data class EpisodeTranscriptEntity(
    @PrimaryKey val id: String,
    val episodeId: String,
    val rawAudioPath: String?,
    val transcriptText: String?,
    val language: String?,
    val engineType: String,
    val variant: String,
    val confidence: Double?,
    val createdAtEpochMs: Long,
)

@Entity(
    tableName = "question_template",
    indices = [Index("stage"), Index("active"), Index("category")],
)
data class QuestionTemplateEntity(
    @PrimaryKey val id: String,
    val schemaVersion: String,
    val source: String,
    val category: String,
    val stage: String,
    val prompt: String,
    val shortLabel: String,
    val helpText: String?,
    val answerType: String,
    val optionsJson: String,
    val priority: Int,
    val required: Boolean,
    val skippable: Boolean,
    val voiceAllowed: Boolean,
    val visibleIf: String?,
    val exportToClinician: Boolean,
    val redFlagWeight: Int,
    val aiEligible: Boolean,
    val createdAtEpochMs: Long,
    val active: Boolean,
)

@Entity(
    tableName = "question_answer",
    indices = [Index("episodeId"), Index("profileId"), Index("questionId")],
)
data class QuestionAnswerEntity(
    @PrimaryKey val id: String,
    val episodeId: String?,
    val profileId: String?,
    val questionId: String,
    val answerPayloadJson: String,
    val answeredAtEpochMs: Long,
    val completionStatus: String,
    val sentForAnalysis: Boolean,
)

@Entity(
    tableName = "attachment",
    indices = [Index("ownerId"), Index("ownerType"), Index(value = ["ownerId", "ownerType"])],
)
data class AttachmentEntity(
    @PrimaryKey val id: String,
    val ownerType: String,
    val ownerId: String,
    val type: String,
    val displayName: String,
    val localUriOrPath: String,
    val mimeType: String,
    val sizeBytes: Long,
    val extractedText: String?,
    val extractionStatus: String,
    val uploadStatus: String,
    val createdAtEpochMs: Long,
)

@Entity(
    tableName = "analysis_snapshot",
    indices = [Index("ownerId"), Index("ownerType")],
)
data class AnalysisSnapshotEntity(
    @PrimaryKey val id: String,
    val ownerType: String,
    val ownerId: String,
    val schemaVersion: String,
    val requestPayloadJson: String,
    val responsePayloadJson: String,
    val modelName: String,
    val createdAtEpochMs: Long,
    val source: String,
    val status: String,
)

@Entity(
    tableName = "sync_queue_item",
    indices = [Index("status"), Index("operationType")],
)
data class SyncQueueItemEntity(
    @PrimaryKey val id: String,
    val operationType: String,
    val payload: String,
    val retryCount: Int,
    val lastError: String?,
    val nextRetryAtEpochMs: Long?,
    val status: String,
    val createdAtEpochMs: Long,
)

@Entity(
    tableName = "red_flag_event",
    indices = [Index("episodeId"), Index("severity")],
)
data class RedFlagEventEntity(
    @PrimaryKey val id: String,
    val episodeId: String,
    val ruleCode: String,
    val severity: String,
    val triggeredAtEpochMs: Long,
    val acknowledged: Boolean,
)

@Entity(tableName = "export_artifact")
data class ExportArtifactEntity(
    @PrimaryKey val id: String,
    val type: String,
    val ownerScope: String,
    val localPath: String,
    val createdAtEpochMs: Long,
)
