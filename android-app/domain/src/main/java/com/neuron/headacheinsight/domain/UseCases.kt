package com.neuron.headacheinsight.domain

import com.neuron.headacheinsight.core.common.DeviceContextProvider
import com.neuron.headacheinsight.core.common.TimeProvider
import com.neuron.headacheinsight.core.model.AppSettings
import com.neuron.headacheinsight.core.model.BaselineQuestionAnswer
import com.neuron.headacheinsight.core.model.CloudAnalysisStatus
import com.neuron.headacheinsight.core.model.Episode
import com.neuron.headacheinsight.core.model.EpisodeStatus
import com.neuron.headacheinsight.core.model.EpisodeSymptom
import com.neuron.headacheinsight.core.model.EpisodeTranscript
import com.neuron.headacheinsight.core.model.HomeDashboard
import com.neuron.headacheinsight.core.model.LocalRedFlagEvaluation
import com.neuron.headacheinsight.core.model.LocalRedFlagInput
import com.neuron.headacheinsight.core.model.OwnerScope
import com.neuron.headacheinsight.core.model.QuestionAnswer
import com.neuron.headacheinsight.core.model.QuestionCompletionStatus
import com.neuron.headacheinsight.core.model.QuestionStage
import com.neuron.headacheinsight.core.model.RedFlagStatus
import com.neuron.headacheinsight.core.model.StartConfidence
import com.neuron.headacheinsight.core.model.TranscriptStatus
import com.neuron.headacheinsight.core.model.UserProfile
import java.util.Locale
import java.util.UUID
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.serialization.json.JsonElement

class RedFlagEngine @Inject constructor() {
    fun evaluate(input: LocalRedFlagInput): LocalRedFlagEvaluation {
        val russian = Locale.getDefault().language.equals("ru", ignoreCase = true)
        val emergencyReasons = buildList {
            if (input.worstSuddenPain) add(if (russian) "Внезапная или самая сильная боль" else "Sudden or worst pain")
            if (input.confusion) add(if (russian) "Спутанность сознания" else "Confusion")
            if (input.fainting) add(if (russian) "Обморок" else "Fainting")
            if (input.oneSidedWeakness) add(if (russian) "Слабость или онемение с одной стороны" else "One-sided weakness or numbness")
            if (input.speechDifficulty) add(if (russian) "Нарушение речи" else "Speech difficulty")
            if (input.walkingDifficulty) add(if (russian) "Нарушение ходьбы" else "Walking difficulty")
            if (input.recentHeadInjury) add(if (russian) "Недавняя травма головы" else "Recent head injury")
        }
        if (emergencyReasons.isNotEmpty()) {
            return LocalRedFlagEvaluation(
                status = RedFlagStatus.EMERGENCY,
                reasons = emergencyReasons,
                emergencyMessage = if (russian) {
                    "Нужна срочная медицинская помощь. Не полагайтесь только на приложение."
                } else {
                    "Seek urgent medical care. Do not rely on the app alone."
                },
                shouldInterruptFlow = true,
            )
        }

        val urgentReasons = buildList {
            if (input.fever) add(if (russian) "Высокая температура" else "High fever")
            if (input.stiffNeck) add(if (russian) "Ригидность шеи" else "Stiff neck")
            if (input.visionChange) add(if (russian) "Нарушение зрения" else "Vision changes")
            if (input.severeVomiting) add(if (russian) "Тяжелая рвота" else "Severe vomiting")
            if (input.patternWorseThanUsual) add(if (russian) "Резкое ухудшение привычного паттерна" else "Sudden worsening of the usual pattern")
        }
        if (urgentReasons.isNotEmpty()) {
            return LocalRedFlagEvaluation(
                status = RedFlagStatus.URGENT,
                reasons = urgentReasons,
                emergencyMessage = if (russian) {
                    "Эти симптомы требуют срочного обсуждения с врачом или обращения за неотложной помощью."
                } else {
                    "These symptoms need urgent clinician review or emergency care."
                },
                shouldInterruptFlow = true,
            )
        }

        val discussSoon = buildList {
            if (input.jawPain) {
                add(
                    if (russian) {
                        "Боль в челюсти или височной области требует отдельного обсуждения"
                    } else {
                        "Jaw pain or temple pain deserves separate discussion"
                    },
                )
            }
        }
        return LocalRedFlagEvaluation(
            status = if (discussSoon.isEmpty()) RedFlagStatus.CLEAR else RedFlagStatus.DISCUSS_SOON,
            reasons = discussSoon,
            emergencyMessage = if (discussSoon.isEmpty()) {
                ""
            } else if (russian) {
                "Этот эпизод стоит обсудить с врачом в ближайшее время."
            } else {
                "This episode is worth discussing with a clinician soon."
            },
            shouldInterruptFlow = false,
        )
    }
}

class CreateEpisodeUseCase @Inject constructor(
    private val episodeRepository: EpisodeRepository,
    private val deviceContextProvider: DeviceContextProvider,
    private val timeProvider: TimeProvider,
) {
    suspend operator fun invoke(): Episode {
        val device = deviceContextProvider.snapshot()
        val episode = Episode(
            id = UUID.randomUUID().toString(),
            status = EpisodeStatus.ACTIVE,
            startedAt = timeProvider.now(),
            timezone = device.timezone,
            locale = device.locale,
            cityRegionSnapshot = null,
            startConfidence = StartConfidence.EXACT,
            transcriptStatus = TranscriptStatus.NONE,
            cloudAnalysisStatus = CloudAnalysisStatus.NOT_REQUESTED,
            redFlagStatus = RedFlagStatus.CLEAR,
            networkConnectedAtStart = device.network.connected,
            createdAt = timeProvider.now(),
            updatedAt = timeProvider.now(),
        )
        episodeRepository.createEpisode(episode)
        return episode
    }
}

class SaveQuickLogUseCase @Inject constructor(
    private val episodeRepository: EpisodeRepository,
    private val safetyRepository: SafetyRepository,
) {
    suspend operator fun invoke(
        episode: Episode,
        severity: Int,
        symptoms: List<EpisodeSymptom>,
        redFlagEvaluation: LocalRedFlagEvaluation,
    ) {
        episodeRepository.updateEpisode(
            episode.copy(
                currentSeverity = severity,
                peakSeverity = maxOf(severity, episode.peakSeverity ?: severity),
                redFlagStatus = redFlagEvaluation.status,
            ),
        )
        episodeRepository.replaceSymptoms(episode.id, symptoms)
        safetyRepository.evaluateAndStore(episode.id, redFlagEvaluation)
    }
}

class SaveQuestionAnswerUseCase @Inject constructor(
    private val episodeRepository: EpisodeRepository,
) {
    suspend operator fun invoke(
        episodeId: String?,
        profileId: String?,
        questionId: String,
        payload: JsonElement,
    ) {
        require(episodeId != null || profileId != null) {
            "QuestionAnswer requires either episodeId or profileId"
        }
        episodeRepository.saveQuestionAnswers(
            listOf(
                QuestionAnswer(
                    id = UUID.randomUUID().toString(),
                    episodeId = episodeId,
                    profileId = profileId,
                    questionId = questionId,
                    answerPayload = payload,
                    answeredAt = kotlinx.datetime.Clock.System.now(),
                    completionStatus = QuestionCompletionStatus.COMPLETE,
                    sentForAnalysis = false,
                ),
            ),
        )
    }
}

class EnsureSeedQuestionsUseCase @Inject constructor(
    private val questionSeedSource: QuestionSeedSource,
    private val questionRepository: QuestionRepository,
    private val settingsRepository: SettingsRepository,
) {
    suspend operator fun invoke(settings: AppSettings) {
        val seedVersion = "v3:${settings.languageTag}"
        if (settings.lastSeedVersion == seedVersion) return
        val questions = questionSeedSource.loadSeedQuestions(settings.languageTag)
        questionRepository.replaceSeedQuestions(questions, seedVersion)
        settingsRepository.updateSettings { it.copy(lastSeedVersion = seedVersion) }
    }
}

class ObserveHomeDashboardUseCase @Inject constructor(
    private val insightRepository: InsightRepository,
) {
    operator fun invoke(): Flow<HomeDashboard> = insightRepository.observeHomeDashboard()
}

class ObserveQuestionSetUseCase @Inject constructor(
    private val questionRepository: QuestionRepository,
    private val questionEngine: LocalRuleQuestionEngine,
    private val episodeRepository: EpisodeRepository,
) {
    fun forStage(stage: QuestionStage): Flow<List<com.neuron.headacheinsight.core.model.QuestionTemplate>> =
        questionRepository.observeActiveQuestions(stage)

    fun forEpisode(episodeId: String): Flow<List<com.neuron.headacheinsight.core.model.QuestionTemplate>> =
        combine(
            questionRepository.observeQuestionsForEpisode(episodeId),
            episodeRepository.observeEpisodeDetail(episodeId),
        ) { remote, detail ->
            if (detail == null) {
                remote
            } else {
                (remote + questionEngine.selectQuestionsForEpisode(detail.episode)).distinctBy { it.id }
            }
        }
}

class UpsertProfileUseCase @Inject constructor(
    private val profileRepository: ProfileRepository,
    private val settingsRepository: SettingsRepository,
    private val timeProvider: TimeProvider,
) {
    suspend operator fun invoke(
        existing: UserProfile?,
        displayName: String?,
        cityRegion: String?,
        cloudEnabled: Boolean,
        locationConsent: Boolean,
        attachmentConsent: Boolean,
    ) {
        val now = timeProvider.now()
        val profile = UserProfile(
            id = existing?.id ?: UUID.randomUUID().toString(),
            displayName = displayName,
            locale = existing?.locale ?: timeProvider.localeTag(),
            timezone = existing?.timezone ?: timeProvider.timeZoneId(),
            cityRegion = cityRegion,
            sleepSchedule = existing?.sleepSchedule,
            wakeSchedule = existing?.wakeSchedule,
            workSchedule = existing?.workSchedule,
            baselineNotes = existing?.baselineNotes,
            cloudAnalysisEnabled = true,
            locationConsent = locationConsent,
            attachmentUploadConsent = attachmentConsent,
            createdAt = existing?.createdAt ?: now,
            updatedAt = now,
        )
        profileRepository.upsertProfile(profile)
        settingsRepository.updateSettings {
            it.copy(
                cloudAnalysisEnabled = true,
                attachmentUploadConsent = attachmentConsent,
                locationConsent = locationConsent,
                onboardingCompleted = true,
            )
        }
    }
}

class SaveBaselineAnswersUseCase @Inject constructor(
    private val profileRepository: ProfileRepository,
) {
    suspend operator fun invoke(answers: List<BaselineQuestionAnswer>) {
        profileRepository.upsertBaselineAnswers(answers)
    }
}

class BuildReportsUseCase @Inject constructor(
    private val reportRepository: ReportRepository,
) {
    suspend operator fun invoke(ownerScope: OwnerScope = OwnerScope.REPORTS) = reportRepository.buildReports()
}

class QueueCloudAnalysisUseCase @Inject constructor(
    private val syncScheduler: SyncScheduler,
) {
    suspend operator fun invoke(episodeId: String) {
        syncScheduler.enqueueEpisodeSync(episodeId)
    }
}

class SaveTranscriptUseCase @Inject constructor(
    private val episodeRepository: EpisodeRepository,
    private val syncScheduler: SyncScheduler,
) {
    suspend operator fun invoke(transcript: EpisodeTranscript, shouldCloudRetry: Boolean) {
        episodeRepository.upsertTranscript(transcript)
        val rawAudioPath = transcript.rawAudioPath
        if (shouldCloudRetry && rawAudioPath != null) {
            syncScheduler.enqueueTranscription(rawAudioPath, transcript.episodeId)
        }
    }
}
