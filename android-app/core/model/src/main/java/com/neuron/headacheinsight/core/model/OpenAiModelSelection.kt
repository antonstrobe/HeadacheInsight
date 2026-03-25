package com.neuron.headacheinsight.core.model

import kotlin.math.ceil

const val OpenAiAutoModelId = "auto"

enum class OpenAiModelTask {
    ANALYSIS,
    QUESTIONS,
    TRANSCRIPTION,
}

data class OpenAiModelCatalog(
    val analysisModels: List<String> = emptyList(),
    val questionModels: List<String> = emptyList(),
    val transcribeModels: List<String> = emptyList(),
)

data class AnalysisRunPreview(
    val estimatedInputTokens: Int,
    val configuredModel: String,
    val effectiveModel: String,
    val recommendedModel: String,
    val automaticSelection: Boolean,
    val shouldSuggestRecommendedModel: Boolean,
)

fun isOpenAiAutoModel(modelId: String): Boolean =
    modelId.trim().equals(OpenAiAutoModelId, ignoreCase = true)

fun estimateOpenAiTokens(vararg chunks: String): Int {
    val totalCharacters = chunks.sumOf { it.trim().length }
    if (totalCharacters <= 0) return 0
    return ceil(totalCharacters / 4.0).toInt()
}

fun buildOpenAiModelCatalog(availableModels: List<String>): OpenAiModelCatalog = OpenAiModelCatalog(
    analysisModels = filterSupportedOpenAiModels(availableModels, OpenAiModelTask.ANALYSIS),
    questionModels = filterSupportedOpenAiModels(availableModels, OpenAiModelTask.QUESTIONS),
    transcribeModels = filterSupportedOpenAiModels(availableModels, OpenAiModelTask.TRANSCRIPTION),
)

fun filterSupportedOpenAiModels(
    availableModels: List<String>,
    task: OpenAiModelTask,
): List<String> {
    val supportedBases = supportedBasesFor(task)
    return availableModels
        .asSequence()
        .map(String::trim)
        .filter(String::isNotBlank)
        .distinct()
        .filter { candidate -> supportedBases.any { base -> matchesSupportedBase(candidate, base) } }
        .sortedWith(
            compareBy<String> { candidate ->
                supportedBases.indexOfFirst { base -> matchesSupportedBase(candidate, base) }.let { index ->
                    if (index >= 0) index else Int.MAX_VALUE
                }
            }.thenBy { candidate ->
                val base = supportedBases.firstOrNull { supported -> matchesSupportedBase(candidate, supported) }
                if (candidate == base) 0 else 1
            }.thenByDescending { it },
        )
        .toList()
}

fun resolveConfiguredOpenAiModel(
    configuredModel: String,
    availableModels: List<String>,
    task: OpenAiModelTask,
    estimatedInputTokens: Int = 0,
): String {
    if (!isOpenAiAutoModel(configuredModel)) {
        return configuredModel.trim()
    }

    val filteredAvailable = filterSupportedOpenAiModels(availableModels, task)
    val preferredBases = preferredBasesFor(task, estimatedInputTokens)
    return preferredBases.firstNotNullOfOrNull { preferred ->
        filteredAvailable.firstOrNull { candidate -> matchesSupportedBase(candidate, preferred) }
    } ?: preferredBases.first()
}

fun recommendedAnalysisModel(
    availableModels: List<String>,
    estimatedInputTokens: Int,
): String = resolveConfiguredOpenAiModel(
    configuredModel = OpenAiAutoModelId,
    availableModels = availableModels,
    task = OpenAiModelTask.ANALYSIS,
    estimatedInputTokens = estimatedInputTokens,
)

private fun preferredBasesFor(
    task: OpenAiModelTask,
    estimatedInputTokens: Int,
): List<String> = when (task) {
    OpenAiModelTask.ANALYSIS -> when {
        estimatedInputTokens >= 180_000 -> listOf(
            "gpt-5.4",
            "gpt-5.2",
            "gpt-5.1",
            "gpt-5",
            "gpt-4.1",
            "gpt-5.4-mini",
            "gpt-5-mini",
            "gpt-4o",
        )
        estimatedInputTokens >= 48_000 -> listOf(
            "gpt-5.4",
            "gpt-5.2",
            "gpt-5.1",
            "gpt-5",
            "gpt-5.4-mini",
            "gpt-5-mini",
            "gpt-4.1",
            "gpt-4o",
        )
        else -> listOf(
            "gpt-5.4",
            "gpt-5.2",
            "gpt-5.1",
            "gpt-5",
            "gpt-5.4-mini",
            "gpt-5-mini",
            "gpt-4.1",
            "gpt-4o",
        )
    }
    OpenAiModelTask.QUESTIONS -> when {
        estimatedInputTokens >= 96_000 -> listOf(
            "gpt-5.4",
            "gpt-5.2",
            "gpt-5.1",
            "gpt-5",
            "gpt-5.4-mini",
            "gpt-5-mini",
            "gpt-4.1",
            "gpt-4o",
        )
        else -> listOf(
            "gpt-5.4-mini",
            "gpt-5-mini",
            "gpt-5.4",
            "gpt-5.2",
            "gpt-5.1",
            "gpt-5",
            "gpt-4.1",
            "gpt-4o",
        )
    }
    OpenAiModelTask.TRANSCRIPTION -> listOf(
        "gpt-4o-transcribe",
        "gpt-4o-mini-transcribe",
    )
}

private fun supportedBasesFor(task: OpenAiModelTask): List<String> = when (task) {
    OpenAiModelTask.ANALYSIS -> listOf(
        "gpt-5.4",
        "gpt-5.2",
        "gpt-5.1",
        "gpt-5",
        "gpt-5.4-mini",
        "gpt-5-mini",
        "gpt-4.1",
        "gpt-4o",
    )
    OpenAiModelTask.QUESTIONS -> listOf(
        "gpt-5.4-mini",
        "gpt-5-mini",
        "gpt-5.4",
        "gpt-5.2",
        "gpt-5.1",
        "gpt-5",
        "gpt-4.1",
        "gpt-4o",
    )
    OpenAiModelTask.TRANSCRIPTION -> listOf(
        "gpt-4o-transcribe",
        "gpt-4o-mini-transcribe",
    )
}

private fun matchesSupportedBase(
    candidate: String,
    supportedBase: String,
): Boolean = candidate == supportedBase || candidate.matches(Regex("${Regex.escape(supportedBase)}-\\d{4}-\\d{2}-\\d{2}.*"))
