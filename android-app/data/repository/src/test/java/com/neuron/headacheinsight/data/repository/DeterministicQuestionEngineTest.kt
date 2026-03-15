package com.neuron.headacheinsight.data.repository

import com.google.common.truth.Truth.assertThat
import com.neuron.headacheinsight.core.model.CloudAnalysisStatus
import com.neuron.headacheinsight.core.model.Episode
import com.neuron.headacheinsight.core.model.EpisodeStatus
import com.neuron.headacheinsight.core.model.RedFlagStatus
import com.neuron.headacheinsight.core.model.StartConfidence
import com.neuron.headacheinsight.core.model.TranscriptStatus
import com.neuron.headacheinsight.data.local.QuestionDao
import com.neuron.headacheinsight.data.local.QuestionTemplateEntity
import com.neuron.headacheinsight.data.local.toModel
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.Instant
import kotlinx.serialization.json.Json
import org.junit.Test

class DeterministicQuestionEngineTest {
    @Test
    fun `high severity active episode selects acute detail questions`() = runTest {
        val dao = object : QuestionDao {
            override fun observeActiveQuestions(stage: String) = flowOf(
                listOf(
                    QuestionTemplateEntity(
                        id = "q1",
                        schemaVersion = "v1",
                        source = "LOCAL_RULE",
                        category = "symptoms_and_aura",
                        stage = stage,
                        prompt = "Aura?",
                        shortLabel = "Aura",
                        helpText = null,
                        answerType = "BOOLEAN",
                        optionsJson = "[]",
                        priority = 10,
                        required = false,
                        skippable = true,
                        voiceAllowed = true,
                        visibleIf = null,
                        exportToClinician = true,
                        redFlagWeight = 0,
                        aiEligible = true,
                        createdAtEpochMs = 0L,
                        active = true,
                    ),
                ),
            )

            override fun observeAllActiveQuestions() = flowOf(emptyList<QuestionTemplateEntity>())
            override suspend fun upsertAll(items: List<QuestionTemplateEntity>) = Unit
            override suspend fun deactivate(ids: List<String>) = Unit
            override suspend fun replaceSeed(items: List<QuestionTemplateEntity>) = Unit
        }
        val subject = DeterministicLocalRuleQuestionEngine(dao, Json { ignoreUnknownKeys = true })

        val result = subject.selectQuestionsForEpisode(
            Episode(
                id = "episode-1",
                status = EpisodeStatus.ACTIVE,
                startedAt = Instant.parse("2026-03-15T10:00:00Z"),
                timezone = "Europe/Moscow",
                locale = "ru-RU",
                startConfidence = StartConfidence.EXACT,
                currentSeverity = 8,
                peakSeverity = 9,
                transcriptStatus = TranscriptStatus.NONE,
                cloudAnalysisStatus = CloudAnalysisStatus.NOT_REQUESTED,
                redFlagStatus = RedFlagStatus.CLEAR,
                createdAt = Instant.parse("2026-03-15T10:00:00Z"),
                updatedAt = Instant.parse("2026-03-15T10:00:00Z"),
            ),
        )

        assertThat(result).hasSize(1)
        assertThat(result.first().id).isEqualTo("q1")
    }
}
