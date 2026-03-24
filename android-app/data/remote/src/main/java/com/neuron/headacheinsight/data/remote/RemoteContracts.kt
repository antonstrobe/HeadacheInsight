package com.neuron.headacheinsight.data.remote

import com.neuron.headacheinsight.core.model.Attachment
import com.neuron.headacheinsight.core.model.EpisodeDetail
import com.neuron.headacheinsight.core.model.QuestionTemplate
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

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
data class VoiceIntakeDraftRequest(
    @SerialName("owner_id") val ownerId: String,
    val locale: String,
    @SerialName("transcript_text") val transcriptText: String,
)

@Serializable
data class TranscribeMetadata(
    @SerialName("episode_id") val episodeId: String,
    val locale: String,
    @SerialName("language_hint") val languageHint: String? = null,
)

@Serializable
data class OpenAiChatCompletionRequest(
    val model: String,
    val messages: List<OpenAiChatMessage>,
    @SerialName("response_format") val responseFormat: OpenAiResponseFormat = OpenAiResponseFormat(),
    val temperature: Double? = null,
)

@Serializable
data class OpenAiChatMessage(
    val role: String,
    val content: String,
)

@Serializable
data class OpenAiResponseFormat(
    val type: String = "json_object",
)

@Serializable
data class OpenAiChatCompletionResponse(
    val id: String? = null,
    val choices: List<OpenAiChoice> = emptyList(),
)

@Serializable
data class OpenAiChoice(
    val message: OpenAiAssistantMessage,
)

@Serializable
data class OpenAiAssistantMessage(
    val content: String? = null,
)

@Serializable
data class OpenAiModelListResponse(
    val data: List<OpenAiModelSummary> = emptyList(),
)

@Serializable
data class OpenAiModelSummary(
    val id: String,
)

@Serializable
data class OpenAiTranscriptionResponse(
    val text: String,
    val language: String? = null,
)
