package com.neuron.headacheinsight.data.remote

import com.neuron.headacheinsight.core.model.AnalysisResponse
import okhttp3.MultipartBody
import okhttp3.RequestBody
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.Multipart
import retrofit2.http.POST
import retrofit2.http.Part

interface BackendApi {
    @POST("/api/client/register")
    suspend fun registerClient(
        @Body request: ClientRegisterRequest,
    ): ClientRegisterResponse

    @POST("/api/analyze-episode")
    suspend fun analyzeEpisode(
        @Body request: AnalyzeEpisodeRequest,
    ): AnalysisResponse

    @POST("/api/generate-follow-up-questions")
    suspend fun generateFollowUpQuestions(
        @Body request: GenerateFollowUpQuestionsRequest,
    ): GenerateFollowUpQuestionsResponse

    @POST("/api/analyze-attachments")
    suspend fun analyzeAttachments(
        @Body request: AnalyzeAttachmentsRequest,
    ): AnalyzeAttachmentsResponse

    @Multipart
    @POST("/api/transcribe")
    suspend fun transcribe(
        @Part file: MultipartBody.Part,
        @Part("metadata") metadata: RequestBody,
    ): TranscribeResponse

    @GET("/health")
    suspend fun health(): HealthResponse
}
