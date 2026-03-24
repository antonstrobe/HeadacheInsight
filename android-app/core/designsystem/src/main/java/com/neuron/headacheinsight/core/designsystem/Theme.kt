package com.neuron.headacheinsight.core.designsystem

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private val DeepNight = Color(0xFF0E1117)
private val NightPanel = Color(0xFF171B24)
private val CalmSlate = Color(0xFF98A2B3)
private val SoftText = Color(0xFFF2F4F7)
private val EmergencyRed = Color(0xFFD14343)
private val RecoveryGreen = Color(0xFF4DAA57)
private val ActionMauve = Color(0xFF7C788F)
private val ActionMauveContainer = Color(0xFF2A2637)
private val ActionFog = Color(0xFFE4E0F1)
private val AnalysisViolet = Color(0xFF8A84B5)
private val QueueGray = Color(0xFF667085)
private val WarningOrange = Color(0xFFF79009)
private val Mist = Color(0xFFE8ECF3)

val HeadacheInsightDarkScheme: ColorScheme = darkColorScheme(
    primary = ActionMauve,
    onPrimary = Color.White,
    secondary = AnalysisViolet,
    secondaryContainer = ActionMauveContainer,
    onSecondaryContainer = SoftText,
    tertiary = RecoveryGreen,
    background = DeepNight,
    surface = NightPanel,
    onBackground = SoftText,
    onSurface = SoftText,
    outline = CalmSlate,
)

val HeadacheInsightLightScheme: ColorScheme = lightColorScheme(
    primary = ActionMauve,
    onPrimary = Color.White,
    secondary = AnalysisViolet,
    secondaryContainer = ActionFog,
    onSecondaryContainer = DeepNight,
    tertiary = RecoveryGreen,
    background = Mist,
    surface = Color.White,
    onBackground = DeepNight,
    onSurface = DeepNight,
    outline = CalmSlate,
)

val HeadacheInsightTypography = Typography(
    headlineLarge = TextStyle(fontSize = 30.sp, lineHeight = 36.sp, fontWeight = FontWeight.Bold),
    headlineMedium = TextStyle(fontSize = 24.sp, lineHeight = 30.sp, fontWeight = FontWeight.Bold),
    titleLarge = TextStyle(fontSize = 20.sp, lineHeight = 26.sp, fontWeight = FontWeight.SemiBold),
    bodyLarge = TextStyle(fontSize = 16.sp, lineHeight = 22.sp),
    bodyMedium = TextStyle(fontSize = 14.sp, lineHeight = 20.sp),
    labelLarge = TextStyle(fontSize = 15.sp, lineHeight = 20.sp, fontWeight = FontWeight.Medium),
)

val HeadacheInsightShapes = Shapes(
    small = androidx.compose.foundation.shape.RoundedCornerShape(16.dp),
    medium = androidx.compose.foundation.shape.RoundedCornerShape(20.dp),
    large = androidx.compose.foundation.shape.RoundedCornerShape(28.dp),
)

@Composable
fun HeadacheInsightTheme(
    comfortMode: Boolean = true,
    content: @Composable () -> Unit,
) {
    val dark = comfortMode || isSystemInDarkTheme()
    MaterialTheme(
        colorScheme = if (dark) HeadacheInsightDarkScheme else HeadacheInsightLightScheme,
        typography = HeadacheInsightTypography,
        shapes = HeadacheInsightShapes,
        content = content,
    )
}

object HeadacheInsightStatusColors {
    val LocalComplete = RecoveryGreen
    val CloudAnalyzed = AnalysisViolet
    val Queued = QueueGray
    val Warning = WarningOrange
    val Emergency = EmergencyRed
}

object HeadacheInsightCardDefaults {
    @Composable
    fun elevated() = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.surface)
}
