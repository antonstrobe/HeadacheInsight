package com.neuron.headacheinsight.data.local

import android.content.Context
import com.neuron.headacheinsight.core.model.QuestionTemplate
import com.neuron.headacheinsight.domain.QuestionSeedSource
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SeedQuestionAssetSource @Inject constructor(
    @ApplicationContext private val context: Context,
    private val json: Json,
) : QuestionSeedSource {
    override fun observeSeedVersion(): Flow<String?> = flowOf("v2")

    override suspend fun loadSeedQuestions(languageTag: String): List<QuestionTemplate> {
        val englishQuestions = loadFromAsset("seed_questions_en.json")
        if (!languageTag.startsWith("ru", ignoreCase = true)) {
            return englishQuestions
        }

        val russianQuestions = loadFromAsset("seed_questions_ru.json")
        val englishById = englishQuestions.associateBy { it.id }
        return russianQuestions.map { question ->
            val englishFallback = englishById[question.id] ?: return@map question
            question.copy(
                prompt = question.prompt.sanitizedOrFallback(englishFallback.prompt),
                shortLabel = question.shortLabel.sanitizedOrFallback(englishFallback.shortLabel),
                helpText = question.helpText.sanitizedOrFallbackNullable(englishFallback.helpText),
            )
        }
    }

    private fun loadFromAsset(assetName: String): List<QuestionTemplate> {
        val raw = context.assets.open(assetName).bufferedReader().use { it.readText() }
        return json.decodeFromString(ListSerializer(QuestionTemplate.serializer()), raw)
    }

    private fun String.sanitizedOrFallback(fallback: String): String {
        val candidate = trim()
        if (candidate.isEmpty()) return fallback
        val questionMarks = candidate.count { it == '?' }
        val mostlyUnknown = questionMarks >= 3 && questionMarks * 2 >= candidate.length.coerceAtLeast(1)
        return if (mostlyUnknown) fallback else candidate
    }

    private fun String?.sanitizedOrFallbackNullable(fallback: String?): String? {
        val candidate = this?.trim()
        if (candidate.isNullOrEmpty()) return fallback
        val questionMarks = candidate.count { it == '?' }
        val mostlyUnknown = questionMarks >= 3 && questionMarks * 2 >= candidate.length.coerceAtLeast(1)
        return if (mostlyUnknown) fallback else candidate
    }
}
