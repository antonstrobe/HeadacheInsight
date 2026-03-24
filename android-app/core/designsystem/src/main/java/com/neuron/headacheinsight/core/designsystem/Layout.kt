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
    if (LocalHandPreference.current == HandPreference.RIGHT) Alignment.End else Alignment.Start

@Composable
fun preferredTextAlign(): TextAlign =
    if (LocalHandPreference.current == HandPreference.RIGHT) TextAlign.End else TextAlign.Start

@Composable
fun preferredHorizontalArrangement(): Arrangement.Horizontal =
    if (LocalHandPreference.current == HandPreference.RIGHT) Arrangement.End else Arrangement.Start

@Composable
fun preferredSpacedArrangement(space: Dp): Arrangement.Horizontal =
    Arrangement.spacedBy(space, preferredHorizontalAlignment())
