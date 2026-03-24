package com.neuron.headacheinsight.core.model

import kotlinx.datetime.Instant
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

@Serializable
enum class EpisodeStatus { ACTIVE, COMPLETED, ARCHIVED }

@Serializable
enum class StartConfidence { EXACT, ESTIMATED }

@Serializable
enum class TranscriptStatus { NONE, RECORDED, LOCAL_PENDING, LOCAL_READY, CLOUD_PENDING, MERGED_READY, FAILED }

@Serializable
enum class CloudAnalysisStatus { DISABLED, NOT_REQUESTED, QUEUED, IN_PROGRESS, READY, FAILED }

@Serializable
enum class RedFlagStatus { CLEAR, MONITOR, DISCUSS_SOON, URGENT, EMERGENCY }

@Serializable
enum class QuestionSource { SEED, API, LOCAL_RULE, MANUAL }

@Serializable
enum class QuestionStage { ACUTE_FAST, ACUTE_DETAIL, PROFILE, DOCTOR, ATTACHMENTS, REVIEW }

@Serializable
enum class AnswerType { BOOLEAN, SCALE, SINGLE_SELECT, MULTI_SELECT, FREE_TEXT, NUMBER, DATE_TIME, DURATION, ATTACHMENT_REQUEST, VOICE_NOTE, MEDICATION_LIST }

@Serializable
enum class QuestionCompletionStatus { EMPTY, PARTIAL, COMPLETE, SKIPPED }

@Serializable
enum class OwnerType { EPISODE, PROFILE }

@Serializable
enum class AttachmentType { IMAGE, PDF, AUDIO, DOCUMENT, SCREENSHOT }

@Serializable
enum class ExtractionStatus { NOT_STARTED, LOCAL_EXTRACTED, CLOUD_QUEUED, CLOUD_COMPLETE, FAILED }

@Serializable
enum class UploadStatus { LOCAL_ONLY, QUEUED, UPLOADING, UPLOADED, FAILED }

@Serializable
enum class TranscriptVariant { LOCAL, CLOUD, MERGED }

@Serializable
enum class AnalysisSource { CLOUD, LOCAL_RULE }

@Serializable
enum class AnalysisStatus { QUEUED, COMPLETE, FAILED }

@Serializable
enum class SyncOperationType { REGISTER_CLIENT, TRANSCRIBE_AUDIO, ANALYZE_EPISODE, GENERATE_QUESTIONS, ANALYZE_ATTACHMENT, UPLOAD_ATTACHMENT }

@Serializable
enum class SyncStatus { PENDING, RUNNING, FAILED, COMPLETE }

@Serializable
enum class ExportType { PDF, CSV, JSON }

@Serializable
enum class OwnerScope { ALL_DATA, EPISODE, PROFILE, INSIGHTS, REPORTS }

@Serializable
enum class UrgentActionLevel { NONE, MONITOR, DISCUSS_SOON, URGENT, EMERGENCY }

@Serializable
enum class HypothesisConfidence { LOW, MEDIUM, HIGH }

@Serializable
data class UserProfile(
    val id: String,
    val displayName: String? = null,
    val locale: String,
    val timezone: String,
    val cityRegion: String? = null,
    val sleepSchedule: String? = null,
    val wakeSchedule: String? = null,
    val workSchedule: String? = null,
    val baselineNotes: String? = null,
    val cloudAnalysisEnabled: Boolean,
    val locationConsent: Boolean,
    val attachmentUploadConsent: Boolean,
    val createdAt: Instant,
    val updatedAt: Instant,
)

@Serializable
data class BaselineQuestionAnswer(
    val id: String,
    val questionId: String,
    val answerPayload: JsonElement,
    val source: QuestionSource,
    val answeredAt: Instant,
)

@Serializable
data class Episode(
    val id: String,
    val status: EpisodeStatus,
    val startedAt: Instant,
    val endedAt: Instant? = null,
    val timezone: String,
    val locale: String,
    val cityRegionSnapshot: String? = null,
    val startConfidence: StartConfidence,
    val currentSeverity: Int? = null,
    val peakSeverity: Int? = null,
    val painType: String? = null,
    val painLocations: List<String> = emptyList(),
    val summaryText: String? = null,
    val transcriptStatus: TranscriptStatus = TranscriptStatus.NONE,
    val cloudAnalysisStatus: CloudAnalysisStatus = CloudAnalysisStatus.NOT_REQUESTED,
    val redFlagStatus: RedFlagStatus = RedFlagStatus.CLEAR,
    val networkConnectedAtStart: Boolean = false,
    val createdAt: Instant,
    val updatedAt: Instant,
)

@Serializable
data class EpisodeSymptom(
    val episodeId: String,
    val symptomCode: String,
    val intensity: Int? = null,
    val present: Boolean,
    val notes: String? = null,
)

@Serializable
data class EpisodeContext(
    val episodeId: String,
    val sleepHours: Double? = null,
    val hydration: String? = null,
    val mealsSkipped: Boolean? = null,
    val caffeine: String? = null,
    val alcohol: String? = null,
    val stressLevel: Int? = null,
    val screenExposure: String? = null,
    val physicalExertion: String? = null,
    val weatherNote: String? = null,
    val menstrualOrHormonalContext: String? = null,
    val infectionFever: Boolean? = null,
    val headInjuryRecent: Boolean? = null,
    val environmentNotes: String? = null,
)

@Serializable
data class EpisodeMedication(
    val id: String,
    val episodeId: String,
    val medicineName: String,
    val dose: String? = null,
    val takenAt: Instant? = null,
    val reliefLevel: Int? = null,
    val sideEffects: String? = null,
    val source: String = "manual",
)

@Serializable
data class EpisodeTranscript(
    val id: String,
    val episodeId: String,
    val rawAudioPath: String? = null,
    val transcriptText: String? = null,
    val language: String? = null,
    val engineType: String,
    val variant: TranscriptVariant,
    val confidence: Double? = null,
    val createdAt: Instant,
)

@Serializable
data class QuestionTemplate(
    val id: String,
    val schemaVersion: String,
    val source: QuestionSource,
    val category: String,
    val stage: QuestionStage,
    val prompt: String,
    val shortLabel: String,
    val helpText: String? = null,
    val answerType: AnswerType,
    val options: List<String> = emptyList(),
    val priority: Int,
    val required: Boolean,
    val skippable: Boolean,
    val voiceAllowed: Boolean,
    val visibleIf: String? = null,
    val exportToClinician: Boolean,
    val redFlagWeight: Int = 0,
    val aiEligible: Boolean = true,
    val createdAt: Instant,
    val active: Boolean = true,
)

@Serializable
data class QuestionAnswer(
    val id: String,
    val episodeId: String? = null,
    val profileId: String? = null,
    val questionId: String,
    val answerPayload: JsonElement,
    val answeredAt: Instant,
    val completionStatus: QuestionCompletionStatus,
    val sentForAnalysis: Boolean = false,
)

@Serializable
data class Attachment(
    val id: String,
    val ownerType: OwnerType,
    val ownerId: String,
    val type: AttachmentType,
    val displayName: String,
    val localUriOrPath: String,
    val mimeType: String,
    val sizeBytes: Long,
    val extractedText: String? = null,
    val extractionStatus: ExtractionStatus = ExtractionStatus.NOT_STARTED,
    val uploadStatus: UploadStatus = UploadStatus.LOCAL_ONLY,
    val createdAt: Instant,
)

@Serializable
data class AnalysisSnapshot(
    val id: String,
    val ownerType: OwnerType,
    val ownerId: String,
    val schemaVersion: String,
    val requestPayloadJson: String,
    val responsePayloadJson: String,
    val modelName: String,
    val createdAt: Instant,
    val source: AnalysisSource,
    val status: AnalysisStatus,
)

@Serializable
data class SyncQueueItem(
    val id: String,
    val operationType: SyncOperationType,
    val payload: String,
    val retryCount: Int,
    val lastError: String? = null,
    val nextRetryAt: Instant? = null,
    val status: SyncStatus,
    val createdAt: Instant,
)

@Serializable
data class RedFlagEvent(
    val id: String,
    val episodeId: String,
    val ruleCode: String,
    val severity: RedFlagStatus,
    val triggeredAt: Instant,
    val acknowledged: Boolean,
)

@Serializable
data class ExportArtifact(
    val id: String,
    val type: ExportType,
    val ownerScope: OwnerScope,
    val localPath: String,
    val createdAt: Instant,
)

@Serializable
data class AppSettings(
    val cloudAnalysisEnabled: Boolean = true,
    val backendBaseUrl: String = "http://10.0.2.2:8000/",
    val languageTag: String = "ru-RU",
    val languageSelectionCompleted: Boolean = false,
    val attachmentUploadConsent: Boolean = false,
    val locationConsent: Boolean = false,
    val comfortModeEnabled: Boolean = true,
    val onboardingCompleted: Boolean = false,
    val lastSeedVersion: String? = null,
)

data class CloudCredentials(
    val apiKey: String = "",
    val analysisModel: String = "gpt-4.1",
    val questionModel: String = "gpt-4.1-mini",
    val transcribeModel: String = "gpt-4o-transcribe",
) {
    val hasApiKey: Boolean
        get() = apiKey.isNotBlank()
}

@Serializable
data class BackendConnectionStatus(
    val status: String = "ok",
    @SerialName("service") val serviceName: String,
    @SerialName("api_key_present") val apiKeyPresent: Boolean,
    @SerialName("analysis_model") val analysisModel: String,
    @SerialName("question_model") val questionModel: String,
    @SerialName("transcribe_model") val transcribeModel: String,
)

@Serializable
data class VoiceIntakeField(
    val section: String,
    val label: String,
    val value: String,
)

@Serializable
data class VoiceIntakeDraft(
    @SerialName("schema_version") val schemaVersion: String = "v1",
    @SerialName("owner_id") val ownerId: String,
    @SerialName("transcript_text") val transcriptText: String,
    @SerialName("summary_text") val summaryText: String,
    val severity: Int? = null,
    val symptoms: List<String> = emptyList(),
    @SerialName("red_flags") val redFlags: List<String> = emptyList(),
    val medications: List<String> = emptyList(),
    @SerialName("live_notes") val liveNotes: List<String> = emptyList(),
    @SerialName("dynamic_fields") val dynamicFields: List<VoiceIntakeField> = emptyList(),
    @SerialName("engine_name") val engineName: String = "local-rule",
)

enum class LocalSpeechPackStatus {
    UNKNOWN,
    CHECKING,
    READY,
    NOT_INSTALLED,
    INSTALLING,
    SCHEDULED,
    UNSUPPORTED,
    ERROR,
}

data class LocalSpeechPackState(
    val status: LocalSpeechPackStatus = LocalSpeechPackStatus.UNKNOWN,
    val progressPercent: Int? = null,
    val message: String? = null,
) {
    val isInstalled: Boolean
        get() = status == LocalSpeechPackStatus.READY

    val isBusy: Boolean
        get() = status == LocalSpeechPackStatus.CHECKING || status == LocalSpeechPackStatus.INSTALLING
}

@Serializable
data class UrgentAction(
    val level: UrgentActionLevel,
    val reasons: List<String>,
    val userMessage: String,
)

@Serializable
data class UserSummary(
    val plainLanguageSummary: String,
    val keyObservations: List<String>,
)

@Serializable
data class ClinicianSummary(
    val conciseMedicalContext: String,
    val headacheDayEstimate: String? = null,
    val acuteMedicationUseEstimate: String? = null,
    val functionalImpactSummary: String? = null,
)

@Serializable
data class Hypothesis(
    val id: String,
    val label: String,
    val confidence: HypothesisConfidence,
    val rationale: String,
    val supportingSignals: List<String>,
    val missingInformation: List<String>,
    val discussionWithDoctor: List<String>,
)

@Serializable
data class SuspectedPattern(
    val type: String,
    val description: String,
    val evidence: List<String>,
)

@Serializable
data class AnalysisResponse(
    @SerialName("schema_version") val schemaVersion: String,
    @SerialName("analysis_id") val analysisId: String,
    @SerialName("owner_type") val ownerType: OwnerType,
    @SerialName("owner_id") val ownerId: String,
    @SerialName("generated_at") val generatedAt: String,
    @SerialName("urgent_action") val urgentAction: UrgentAction,
    @SerialName("user_summary") val userSummary: UserSummary,
    @SerialName("clinician_summary") val clinicianSummary: ClinicianSummary,
    val hypotheses: List<Hypothesis>,
    @SerialName("suspected_patterns") val suspectedPatterns: List<SuspectedPattern>,
    @SerialName("next_questions") val nextQuestions: List<QuestionTemplate>,
    @SerialName("suggested_tracking_fields") val suggestedTrackingFields: List<String>,
    @SerialName("suggested_attachments") val suggestedAttachments: List<String>,
    @SerialName("suggested_doctor_discussion_points") val suggestedDoctorDiscussionPoints: List<String>,
    @SerialName("suggested_specialists") val suggestedSpecialists: List<String>,
    @SerialName("suggested_tests_or_evaluations") val suggestedTestsOrEvaluations: List<String>,
    @SerialName("self_care_general") val selfCareGeneral: List<String>,
    val disclaimer: String,
    @SerialName("needs_human_clinician_review") val needsHumanClinicianReview: Boolean,
)

@Serializable
data class HomeDashboard(
    val pendingEpisode: Episode? = null,
    val lastEpisode: Episode? = null,
    val monthlyHeadacheDays: Int = 0,
    val cloudEnabled: Boolean = true,
    val queueCount: Int = 0,
)

@Serializable
data class InsightSummary(
    val monthlyHeadacheDays: Int,
    val averageSeverity: Double,
    val medicationDays: Int,
    val functionalImpactDays: Int,
    val suspectedTriggers: List<String>,
)

@Serializable
data class EpisodeDetail(
    val episode: Episode,
    val symptoms: List<EpisodeSymptom> = emptyList(),
    val context: EpisodeContext? = null,
    val medications: List<EpisodeMedication> = emptyList(),
    val transcripts: List<EpisodeTranscript> = emptyList(),
    val answers: List<QuestionAnswer> = emptyList(),
    val attachments: List<Attachment> = emptyList(),
    val analyses: List<AnalysisSnapshot> = emptyList(),
    val redFlags: List<RedFlagEvent> = emptyList(),
)

@Serializable
data class ReportBundle(
    val patientText: String,
    val clinicianText: String,
    val csv: String,
    val json: String,
)

@Serializable
data class LocalRedFlagInput(
    val worstSuddenPain: Boolean = false,
    val confusion: Boolean = false,
    val fainting: Boolean = false,
    val fever: Boolean = false,
    val oneSidedWeakness: Boolean = false,
    val stiffNeck: Boolean = false,
    val visionChange: Boolean = false,
    val speechDifficulty: Boolean = false,
    val walkingDifficulty: Boolean = false,
    val severeVomiting: Boolean = false,
    val recentHeadInjury: Boolean = false,
    val patternWorseThanUsual: Boolean = false,
    val jawPain: Boolean = false,
)

@Serializable
data class LocalRedFlagEvaluation(
    val status: RedFlagStatus,
    val reasons: List<String>,
    val emergencyMessage: String,
    val shouldInterruptFlow: Boolean,
)
