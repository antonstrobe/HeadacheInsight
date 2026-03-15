package com.neuron.headacheinsight.data.local

import com.neuron.headacheinsight.core.model.AnalysisSnapshot
import com.neuron.headacheinsight.core.model.AnalysisSource
import com.neuron.headacheinsight.core.model.AnalysisStatus
import com.neuron.headacheinsight.core.model.Attachment
import com.neuron.headacheinsight.core.model.AttachmentType
import com.neuron.headacheinsight.core.model.BaselineQuestionAnswer
import com.neuron.headacheinsight.core.model.CloudAnalysisStatus
import com.neuron.headacheinsight.core.model.Episode
import com.neuron.headacheinsight.core.model.EpisodeContext
import com.neuron.headacheinsight.core.model.EpisodeMedication
import com.neuron.headacheinsight.core.model.EpisodeStatus
import com.neuron.headacheinsight.core.model.EpisodeSymptom
import com.neuron.headacheinsight.core.model.EpisodeTranscript
import com.neuron.headacheinsight.core.model.ExportArtifact
import com.neuron.headacheinsight.core.model.ExportType
import com.neuron.headacheinsight.core.model.ExtractionStatus
import com.neuron.headacheinsight.core.model.OwnerScope
import com.neuron.headacheinsight.core.model.OwnerType
import com.neuron.headacheinsight.core.model.AnswerType
import com.neuron.headacheinsight.core.model.QuestionAnswer
import com.neuron.headacheinsight.core.model.QuestionCompletionStatus
import com.neuron.headacheinsight.core.model.QuestionSource
import com.neuron.headacheinsight.core.model.QuestionStage
import com.neuron.headacheinsight.core.model.QuestionTemplate
import com.neuron.headacheinsight.core.model.RedFlagEvent
import com.neuron.headacheinsight.core.model.RedFlagStatus
import com.neuron.headacheinsight.core.model.StartConfidence
import com.neuron.headacheinsight.core.model.SyncOperationType
import com.neuron.headacheinsight.core.model.SyncQueueItem
import com.neuron.headacheinsight.core.model.SyncStatus
import com.neuron.headacheinsight.core.model.TranscriptStatus
import com.neuron.headacheinsight.core.model.TranscriptVariant
import com.neuron.headacheinsight.core.model.UploadStatus
import com.neuron.headacheinsight.core.model.UserProfile
import kotlinx.datetime.Instant
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.serializer

fun UserProfileEntity.toModel(): UserProfile = UserProfile(
    id = id,
    displayName = displayName,
    locale = locale,
    timezone = timezone,
    cityRegion = cityRegion,
    sleepSchedule = sleepSchedule,
    wakeSchedule = wakeSchedule,
    workSchedule = workSchedule,
    baselineNotes = baselineNotes,
    cloudAnalysisEnabled = cloudAnalysisEnabled,
    locationConsent = locationConsent,
    attachmentUploadConsent = attachmentUploadConsent,
    createdAt = Instant.fromEpochMilliseconds(createdAtEpochMs),
    updatedAt = Instant.fromEpochMilliseconds(updatedAtEpochMs),
)

fun UserProfile.toEntity(): UserProfileEntity = UserProfileEntity(
    id = id,
    displayName = displayName,
    locale = locale,
    timezone = timezone,
    cityRegion = cityRegion,
    sleepSchedule = sleepSchedule,
    wakeSchedule = wakeSchedule,
    workSchedule = workSchedule,
    baselineNotes = baselineNotes,
    cloudAnalysisEnabled = cloudAnalysisEnabled,
    locationConsent = locationConsent,
    attachmentUploadConsent = attachmentUploadConsent,
    createdAtEpochMs = createdAt.toEpochMilliseconds(),
    updatedAtEpochMs = updatedAt.toEpochMilliseconds(),
)

fun BaselineQuestionAnswerEntity.toModel(json: Json): BaselineQuestionAnswer = BaselineQuestionAnswer(
    id = id,
    questionId = questionId,
    answerPayload = json.parseToJsonElement(answerPayloadJson),
    source = enumValueOf<QuestionSource>(source),
    answeredAt = Instant.fromEpochMilliseconds(answeredAtEpochMs),
)

fun BaselineQuestionAnswer.toEntity(json: Json): BaselineQuestionAnswerEntity = BaselineQuestionAnswerEntity(
    id = id,
    questionId = questionId,
    answerPayloadJson = json.encodeToString(JsonElement.serializer(), answerPayload),
    source = source.name,
    answeredAtEpochMs = answeredAt.toEpochMilliseconds(),
)

fun EpisodeEntity.toModel(json: Json): Episode = Episode(
    id = id,
    status = enumValueOf<EpisodeStatus>(status),
    startedAt = Instant.fromEpochMilliseconds(startedAtEpochMs),
    endedAt = endedAtEpochMs?.let(Instant::fromEpochMilliseconds),
    timezone = timezone,
    locale = locale,
    cityRegionSnapshot = cityRegionSnapshot,
    startConfidence = enumValueOf<StartConfidence>(startConfidence),
    currentSeverity = currentSeverity,
    peakSeverity = peakSeverity,
    painType = painType,
    painLocations = json.decodeFromString(ListSerializerProvider.stringList, painLocationsJson),
    summaryText = summaryText,
    transcriptStatus = enumValueOf<TranscriptStatus>(transcriptStatus),
    cloudAnalysisStatus = enumValueOf<CloudAnalysisStatus>(cloudAnalysisStatus),
    redFlagStatus = enumValueOf<RedFlagStatus>(redFlagStatus),
    networkConnectedAtStart = networkConnectedAtStart,
    createdAt = Instant.fromEpochMilliseconds(createdAtEpochMs),
    updatedAt = Instant.fromEpochMilliseconds(updatedAtEpochMs),
)

fun Episode.toEntity(json: Json): EpisodeEntity = EpisodeEntity(
    id = id,
    status = status.name,
    startedAtEpochMs = startedAt.toEpochMilliseconds(),
    endedAtEpochMs = endedAt?.toEpochMilliseconds(),
    timezone = timezone,
    locale = locale,
    cityRegionSnapshot = cityRegionSnapshot,
    startConfidence = startConfidence.name,
    currentSeverity = currentSeverity,
    peakSeverity = peakSeverity,
    painType = painType,
    painLocationsJson = json.encodeToString(ListSerializerProvider.stringList, painLocations),
    summaryText = summaryText,
    transcriptStatus = transcriptStatus.name,
    cloudAnalysisStatus = cloudAnalysisStatus.name,
    redFlagStatus = redFlagStatus.name,
    networkConnectedAtStart = networkConnectedAtStart,
    createdAtEpochMs = createdAt.toEpochMilliseconds(),
    updatedAtEpochMs = updatedAt.toEpochMilliseconds(),
)

fun EpisodeSymptomEntity.toModel(): EpisodeSymptom = EpisodeSymptom(
    episodeId = episodeId,
    symptomCode = symptomCode,
    intensity = intensity,
    present = present,
    notes = notes,
)

fun EpisodeSymptom.toEntity(): EpisodeSymptomEntity = EpisodeSymptomEntity(
    episodeId = episodeId,
    symptomCode = symptomCode,
    intensity = intensity,
    present = present,
    notes = notes,
)

fun EpisodeContextEntity.toModel(): EpisodeContext = EpisodeContext(
    episodeId = episodeId,
    sleepHours = sleepHours,
    hydration = hydration,
    mealsSkipped = mealsSkipped,
    caffeine = caffeine,
    alcohol = alcohol,
    stressLevel = stressLevel,
    screenExposure = screenExposure,
    physicalExertion = physicalExertion,
    weatherNote = weatherNote,
    menstrualOrHormonalContext = menstrualOrHormonalContext,
    infectionFever = infectionFever,
    headInjuryRecent = headInjuryRecent,
    environmentNotes = environmentNotes,
)

fun EpisodeContext.toEntity(): EpisodeContextEntity = EpisodeContextEntity(
    episodeId = episodeId,
    sleepHours = sleepHours,
    hydration = hydration,
    mealsSkipped = mealsSkipped,
    caffeine = caffeine,
    alcohol = alcohol,
    stressLevel = stressLevel,
    screenExposure = screenExposure,
    physicalExertion = physicalExertion,
    weatherNote = weatherNote,
    menstrualOrHormonalContext = menstrualOrHormonalContext,
    infectionFever = infectionFever,
    headInjuryRecent = headInjuryRecent,
    environmentNotes = environmentNotes,
)

fun EpisodeMedicationEntity.toModel(): EpisodeMedication = EpisodeMedication(
    id = id,
    episodeId = episodeId,
    medicineName = medicineName,
    dose = dose,
    takenAt = takenAtEpochMs?.let(Instant::fromEpochMilliseconds),
    reliefLevel = reliefLevel,
    sideEffects = sideEffects,
    source = source,
)

fun EpisodeMedication.toEntity(): EpisodeMedicationEntity = EpisodeMedicationEntity(
    id = id,
    episodeId = episodeId,
    medicineName = medicineName,
    dose = dose,
    takenAtEpochMs = takenAt?.toEpochMilliseconds(),
    reliefLevel = reliefLevel,
    sideEffects = sideEffects,
    source = source,
)

fun EpisodeTranscriptEntity.toModel(): EpisodeTranscript = EpisodeTranscript(
    id = id,
    episodeId = episodeId,
    rawAudioPath = rawAudioPath,
    transcriptText = transcriptText,
    language = language,
    engineType = engineType,
    variant = enumValueOf<TranscriptVariant>(variant),
    confidence = confidence,
    createdAt = Instant.fromEpochMilliseconds(createdAtEpochMs),
)

fun EpisodeTranscript.toEntity(): EpisodeTranscriptEntity = EpisodeTranscriptEntity(
    id = id,
    episodeId = episodeId,
    rawAudioPath = rawAudioPath,
    transcriptText = transcriptText,
    language = language,
    engineType = engineType,
    variant = variant.name,
    confidence = confidence,
    createdAtEpochMs = createdAt.toEpochMilliseconds(),
)

fun QuestionTemplateEntity.toModel(json: Json): QuestionTemplate = QuestionTemplate(
    id = id,
    schemaVersion = schemaVersion,
    source = enumValueOf<QuestionSource>(source),
    category = category,
    stage = enumValueOf<QuestionStage>(stage),
    prompt = prompt,
    shortLabel = shortLabel,
    helpText = helpText,
    answerType = enumValueOf<AnswerType>(answerType),
    options = json.decodeFromString(ListSerializerProvider.stringList, optionsJson),
    priority = priority,
    required = required,
    skippable = skippable,
    voiceAllowed = voiceAllowed,
    visibleIf = visibleIf,
    exportToClinician = exportToClinician,
    redFlagWeight = redFlagWeight,
    aiEligible = aiEligible,
    createdAt = Instant.fromEpochMilliseconds(createdAtEpochMs),
    active = active,
)

fun QuestionTemplate.toEntity(json: Json): QuestionTemplateEntity = QuestionTemplateEntity(
    id = id,
    schemaVersion = schemaVersion,
    source = source.name,
    category = category,
    stage = stage.name,
    prompt = prompt,
    shortLabel = shortLabel,
    helpText = helpText,
    answerType = answerType.name,
    optionsJson = json.encodeToString(ListSerializerProvider.stringList, options),
    priority = priority,
    required = required,
    skippable = skippable,
    voiceAllowed = voiceAllowed,
    visibleIf = visibleIf,
    exportToClinician = exportToClinician,
    redFlagWeight = redFlagWeight,
    aiEligible = aiEligible,
    createdAtEpochMs = createdAt.toEpochMilliseconds(),
    active = active,
)

fun QuestionAnswerEntity.toModel(json: Json): QuestionAnswer = QuestionAnswer(
    id = id,
    episodeId = episodeId,
    profileId = profileId,
    questionId = questionId,
    answerPayload = json.parseToJsonElement(answerPayloadJson),
    answeredAt = Instant.fromEpochMilliseconds(answeredAtEpochMs),
    completionStatus = enumValueOf<QuestionCompletionStatus>(completionStatus),
    sentForAnalysis = sentForAnalysis,
)

fun QuestionAnswer.toEntity(json: Json): QuestionAnswerEntity = QuestionAnswerEntity(
    id = id,
    episodeId = episodeId,
    profileId = profileId,
    questionId = questionId,
    answerPayloadJson = json.encodeToString(JsonElement.serializer(), answerPayload),
    answeredAtEpochMs = answeredAt.toEpochMilliseconds(),
    completionStatus = completionStatus.name,
    sentForAnalysis = sentForAnalysis,
)

fun AttachmentEntity.toModel(): Attachment = Attachment(
    id = id,
    ownerType = enumValueOf<OwnerType>(ownerType),
    ownerId = ownerId,
    type = enumValueOf<AttachmentType>(type),
    displayName = displayName,
    localUriOrPath = localUriOrPath,
    mimeType = mimeType,
    sizeBytes = sizeBytes,
    extractedText = extractedText,
    extractionStatus = enumValueOf<ExtractionStatus>(extractionStatus),
    uploadStatus = enumValueOf<UploadStatus>(uploadStatus),
    createdAt = Instant.fromEpochMilliseconds(createdAtEpochMs),
)

fun Attachment.toEntity(): AttachmentEntity = AttachmentEntity(
    id = id,
    ownerType = ownerType.name,
    ownerId = ownerId,
    type = type.name,
    displayName = displayName,
    localUriOrPath = localUriOrPath,
    mimeType = mimeType,
    sizeBytes = sizeBytes,
    extractedText = extractedText,
    extractionStatus = extractionStatus.name,
    uploadStatus = uploadStatus.name,
    createdAtEpochMs = createdAt.toEpochMilliseconds(),
)

fun AnalysisSnapshotEntity.toModel(): AnalysisSnapshot = AnalysisSnapshot(
    id = id,
    ownerType = enumValueOf<OwnerType>(ownerType),
    ownerId = ownerId,
    schemaVersion = schemaVersion,
    requestPayloadJson = requestPayloadJson,
    responsePayloadJson = responsePayloadJson,
    modelName = modelName,
    createdAt = Instant.fromEpochMilliseconds(createdAtEpochMs),
    source = enumValueOf<AnalysisSource>(source),
    status = enumValueOf<AnalysisStatus>(status),
)

fun AnalysisSnapshot.toEntity(): AnalysisSnapshotEntity = AnalysisSnapshotEntity(
    id = id,
    ownerType = ownerType.name,
    ownerId = ownerId,
    schemaVersion = schemaVersion,
    requestPayloadJson = requestPayloadJson,
    responsePayloadJson = responsePayloadJson,
    modelName = modelName,
    createdAtEpochMs = createdAt.toEpochMilliseconds(),
    source = source.name,
    status = status.name,
)

fun SyncQueueItemEntity.toModel(): SyncQueueItem = SyncQueueItem(
    id = id,
    operationType = enumValueOf<SyncOperationType>(operationType),
    payload = payload,
    retryCount = retryCount,
    lastError = lastError,
    nextRetryAt = nextRetryAtEpochMs?.let(Instant::fromEpochMilliseconds),
    status = enumValueOf<SyncStatus>(status),
    createdAt = Instant.fromEpochMilliseconds(createdAtEpochMs),
)

fun SyncQueueItem.toEntity(): SyncQueueItemEntity = SyncQueueItemEntity(
    id = id,
    operationType = operationType.name,
    payload = payload,
    retryCount = retryCount,
    lastError = lastError,
    nextRetryAtEpochMs = nextRetryAt?.toEpochMilliseconds(),
    status = status.name,
    createdAtEpochMs = createdAt.toEpochMilliseconds(),
)

fun RedFlagEventEntity.toModel(): RedFlagEvent = RedFlagEvent(
    id = id,
    episodeId = episodeId,
    ruleCode = ruleCode,
    severity = enumValueOf<RedFlagStatus>(severity),
    triggeredAt = Instant.fromEpochMilliseconds(triggeredAtEpochMs),
    acknowledged = acknowledged,
)

fun RedFlagEvent.toEntity(): RedFlagEventEntity = RedFlagEventEntity(
    id = id,
    episodeId = episodeId,
    ruleCode = ruleCode,
    severity = severity.name,
    triggeredAtEpochMs = triggeredAt.toEpochMilliseconds(),
    acknowledged = acknowledged,
)

fun ExportArtifactEntity.toModel(): ExportArtifact = ExportArtifact(
    id = id,
    type = enumValueOf<ExportType>(type),
    ownerScope = enumValueOf<OwnerScope>(ownerScope),
    localPath = localPath,
    createdAt = Instant.fromEpochMilliseconds(createdAtEpochMs),
)

fun ExportArtifact.toEntity(): ExportArtifactEntity = ExportArtifactEntity(
    id = id,
    type = type.name,
    ownerScope = ownerScope.name,
    localPath = localPath,
    createdAtEpochMs = createdAt.toEpochMilliseconds(),
)

internal object ListSerializerProvider {
    val stringList = ListSerializer(serializer<String>())
}
