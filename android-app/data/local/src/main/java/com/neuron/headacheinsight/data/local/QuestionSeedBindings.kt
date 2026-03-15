package com.neuron.headacheinsight.data.local

import com.neuron.headacheinsight.domain.QuestionSeedSource
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@Module
@InstallIn(SingletonComponent::class)
abstract class QuestionSeedBindingsModule {
    @Binds
    abstract fun bindQuestionSeedSource(impl: SeedQuestionAssetSource): QuestionSeedSource
}
