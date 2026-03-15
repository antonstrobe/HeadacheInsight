package com.neuron.headacheinsight.domain

import com.google.common.truth.Truth.assertThat
import com.neuron.headacheinsight.core.model.LocalRedFlagInput
import com.neuron.headacheinsight.core.model.RedFlagStatus
import org.junit.Test

class RedFlagEngineTest {
    private val subject = RedFlagEngine()

    @Test
    fun `emergency flags interrupt normal flow`() {
        val result = subject.evaluate(
            LocalRedFlagInput(
                worstSuddenPain = true,
                speechDifficulty = true,
            ),
        )

        assertThat(result.status).isEqualTo(RedFlagStatus.EMERGENCY)
        assertThat(result.shouldInterruptFlow).isTrue()
    }

    @Test
    fun `jaw pain alone becomes discuss soon`() {
        val result = subject.evaluate(LocalRedFlagInput(jawPain = true))

        assertThat(result.status).isEqualTo(RedFlagStatus.DISCUSS_SOON)
        assertThat(result.shouldInterruptFlow).isFalse()
    }
}
