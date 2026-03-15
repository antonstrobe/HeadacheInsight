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
    override fun observeSeedVersion(): Flow<String?> = flowOf("v1")

    override suspend fun loadSeedQuestions(languageTag: String): List<QuestionTemplate> {
        val assetName = if (languageTag.startsWith("ru", ignoreCase = true)) {
            "seed_questions_ru.json"
        } else {
            "seed_questions_en.json"
        }
        val raw = context.assets.open(assetName).bufferedReader().use { it.readText() }
        return json.decodeFromString(ListSerializer(QuestionTemplate.serializer()), raw)
    }
}
