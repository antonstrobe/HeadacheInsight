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

private val MidnightInk = Color(0xFF091320)
private val OceanPanel = Color(0xFF122236)
private val OceanPanelRaised = Color(0xFF1B2E45)
private val HorizonLine = Color(0xFF8BA0B7)
private val FoamText = Color(0xFFF4F8FB)
private val CoralAlert = Color(0xFFE06A55)
private val Meadow = Color(0xFF50B784)
private val SignalAmber = Color(0xFFF3A63D)
private val TideCyan = Color(0xFF4AB8C4)
private val Ember = Color(0xFFEF8B4A)
private val Sand = Color(0xFFF2ECE2)
private val SandCard = Color(0xFFFFFBF6)
private val SandCardRaised = Color(0xFFE8DED0)
private val DeepSlate = Color(0xFF162331)
private val DeepSlateSoft = Color(0xFF526274)

val HeadacheInsightDarkScheme: ColorScheme = darkColorScheme(
    primary = SignalAmber,
    onPrimary = MidnightInk,
    primaryContainer = Ember,
    onPrimaryContainer = MidnightInk,
    secondary = TideCyan,
    onSecondary = MidnightInk,
    secondaryContainer = OceanPanelRaised,
    onSecondaryContainer = FoamText,
    tertiary = Meadow,
    onTertiary = MidnightInk,
    background = MidnightInk,
    surface = OceanPanel,
    surfaceVariant = OceanPanelRaised,
    onBackground = FoamText,
    onSurface = FoamText,
    onSurfaceVariant = HorizonLine,
    outline = HorizonLine,
    error = CoralAlert,
    onError = MidnightInk,
)

val HeadacheInsightLightScheme: ColorScheme = lightColorScheme(
    primary = DeepSlate,
    onPrimary = Color.White,
    primaryContainer = SignalAmber,
    onPrimaryContainer = MidnightInk,
    secondary = TideCyan,
    onSecondary = MidnightInk,
    secondaryContainer = SandCardRaised,
    onSecondaryContainer = DeepSlate,
    tertiary = Meadow,
    onTertiary = MidnightInk,
    background = Sand,
    surface = SandCard,
    surfaceVariant = SandCardRaised,
    onBackground = DeepSlate,
    onSurface = DeepSlate,
    onSurfaceVariant = DeepSlateSoft,
    outline = DeepSlateSoft,
    error = CoralAlert,
    onError = Color.White,
)

val HeadacheInsightTypography = Typography(
    headlineLarge = TextStyle(fontSize = 32.sp, lineHeight = 38.sp, fontWeight = FontWeight.ExtraBold),
    headlineMedium = TextStyle(fontSize = 26.sp, lineHeight = 32.sp, fontWeight = FontWeight.Bold),
    headlineSmall = TextStyle(fontSize = 21.sp, lineHeight = 28.sp, fontWeight = FontWeight.Bold),
    titleLarge = TextStyle(fontSize = 18.sp, lineHeight = 25.sp, fontWeight = FontWeight.SemiBold),
    titleMedium = TextStyle(fontSize = 16.sp, lineHeight = 22.sp, fontWeight = FontWeight.SemiBold),
    bodyLarge = TextStyle(fontSize = 16.sp, lineHeight = 24.sp),
    bodyMedium = TextStyle(fontSize = 14.sp, lineHeight = 21.sp),
    labelLarge = TextStyle(fontSize = 14.sp, lineHeight = 18.sp, fontWeight = FontWeight.Medium),
)

val HeadacheInsightShapes = Shapes(
    small = androidx.compose.foundation.shape.RoundedCornerShape(18.dp),
    medium = androidx.compose.foundation.shape.RoundedCornerShape(24.dp),
    large = androidx.compose.foundation.shape.RoundedCornerShape(32.dp),
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
    val LocalComplete = Meadow
    val CloudAnalyzed = TideCyan
    val Queued = HorizonLine
    val Warning = SignalAmber
    val Emergency = CoralAlert
}

object HeadacheInsightCardDefaults {
    @Composable
    fun elevated() = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
}
