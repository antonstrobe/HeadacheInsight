package com.neuron.headacheinsight.data.repository

import com.google.common.truth.Truth.assertThat
import com.neuron.headacheinsight.core.model.OpenAiAutoModelId
import com.neuron.headacheinsight.core.model.OpenAiModelTask
import com.neuron.headacheinsight.core.model.filterSupportedOpenAiModels
import com.neuron.headacheinsight.core.model.resolveConfiguredOpenAiModel
import org.junit.Test

class OpenAiModelSelectionTest {
    @Test
    fun `analysis catalog keeps only suitable text models`() {
        val models = listOf(
            "gpt-5.4",
            "gpt-5.4-mini",
            "gpt-4o-transcribe",
            "gpt-image-1",
            "dall-e-3",
            "gpt-4.1-mini",
            "gpt-4.1",
        )

        val filtered = filterSupportedOpenAiModels(models, OpenAiModelTask.ANALYSIS)

        assertThat(filtered).containsExactly(
            "gpt-5.4",
            "gpt-5.4-mini",
            "gpt-4.1",
        ).inOrder()
    }

    @Test
    fun `auto analysis prefers flagship model when available`() {
        val resolved = resolveConfiguredOpenAiModel(
            configuredModel = OpenAiAutoModelId,
            availableModels = listOf("gpt-5.4-mini", "gpt-5.4", "gpt-4.1"),
            task = OpenAiModelTask.ANALYSIS,
            estimatedInputTokens = 18_000,
        )

        assertThat(resolved).isEqualTo("gpt-5.4")
    }

    @Test
    fun `auto transcription keeps dedicated speech model`() {
        val resolved = resolveConfiguredOpenAiModel(
            configuredModel = OpenAiAutoModelId,
            availableModels = listOf("gpt-4o-mini-transcribe", "gpt-4o-transcribe"),
            task = OpenAiModelTask.TRANSCRIPTION,
        )

        assertThat(resolved).isEqualTo("gpt-4o-transcribe")
    }
}
