package com.neuron.headacheinsight.data.remote

import com.neuron.headacheinsight.core.model.AnalysisResponse
import com.neuron.headacheinsight.core.model.Attachment
import com.neuron.headacheinsight.core.model.EpisodeDetail
import com.neuron.headacheinsight.core.model.QuestionTemplate
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class ClientRegisterRequest(
    @SerialName("install_id") val installId: String,
    @SerialName("public_key") val publicKey: String,
    @SerialName("device_locale") val deviceLocale: String,
    @SerialName("device_timezone") val deviceTimezone: String,
)

@Serializable
data class ClientRegisterResponse(
    @SerialName("client_id") val clientId: String,
    val accepted: Boolean,
)

@Serializable
data class AnalyzeEpisodeRequest(
    @SerialName("owner_id") val ownerId: String,
    @SerialName("schema_version") val schemaVersion: String = "v1",
    val locale: String,
    val episode: EpisodeDetail,
    @SerialName("include_follow_up_questions") val includeFollowUpQuestions: Boolean = true,
)

@Serializable
data class GenerateFollowUpQuestionsRequest(
    @SerialName("owner_id") val ownerId: String,
    val locale: String,
    val episode: EpisodeDetail,
)

@Serializable
data class GenerateFollowUpQuestionsResponse(
    @SerialName("schema_version") val schemaVersion: String,
    val questions: List<QuestionTemplate>,
)

@Serializable
data class AnalyzeAttachmentsRequest(
    @SerialName("owner_id") val ownerId: String,
    val locale: String,
    val attachments: List<Attachment>,
)

@Serializable
data class AnalyzeAttachmentsResponse(
    @SerialName("owner_id") val ownerId: String,
    val summary: List<String>,
)

@Serializable
data class TranscribeMetadata(
    @SerialName("episode_id") val episodeId: String,
    val locale: String,
    @SerialName("language_hint") val languageHint: String? = null,
)

@Serializable
data class TranscribeResponse(
    @SerialName("episode_id") val episodeId: String,
    @SerialName("language") val language: String? = null,
    @SerialName("transcript_text") val transcriptText: String,
    @SerialName("confidence") val confidence: Double? = null,
    @SerialName("engine_type") val engineType: String,
)

@Serializable
data class HealthResponse(
    val status: String,
    @SerialName("service") val serviceName: String,
)
