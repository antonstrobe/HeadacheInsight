package com.neuron.headacheinsight.core.designsystem

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import com.neuron.headacheinsight.core.model.HandPreference

val LocalHandPreference = staticCompositionLocalOf { HandPreference.RIGHT }

@Composable
fun preferredHorizontalAlignment(): Alignment.Horizontal =
    when (LocalHandPreference.current) {
        HandPreference.RIGHT -> Alignment.End
        HandPreference.LEFT -> Alignment.Start
        HandPreference.CENTER -> Alignment.CenterHorizontally
    }

@Composable
fun preferredTextAlign(): TextAlign =
    when (LocalHandPreference.current) {
        HandPreference.RIGHT -> TextAlign.End
        HandPreference.LEFT -> TextAlign.Start
        HandPreference.CENTER -> TextAlign.Center
    }

@Composable
fun preferredHorizontalArrangement(): Arrangement.Horizontal =
    when (LocalHandPreference.current) {
        HandPreference.RIGHT -> Arrangement.End
        HandPreference.LEFT -> Arrangement.Start
        HandPreference.CENTER -> Arrangement.Center
    }

@Composable
fun preferredSpacedArrangement(space: Dp): Arrangement.Horizontal =
    Arrangement.spacedBy(space, preferredHorizontalAlignment())
