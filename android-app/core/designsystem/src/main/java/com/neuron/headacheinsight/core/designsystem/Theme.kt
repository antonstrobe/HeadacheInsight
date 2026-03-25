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
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.isUnspecified
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

private val HeadacheInsightTypographyBase = Typography(
    headlineLarge = TextStyle(fontSize = 29.sp, lineHeight = 34.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = (-0.01).em),
    headlineMedium = TextStyle(fontSize = 24.sp, lineHeight = 29.sp, fontWeight = FontWeight.Bold, letterSpacing = (-0.01).em),
    headlineSmall = TextStyle(fontSize = 19.sp, lineHeight = 24.sp, fontWeight = FontWeight.Bold),
    titleLarge = TextStyle(fontSize = 17.sp, lineHeight = 21.sp, fontWeight = FontWeight.SemiBold),
    titleMedium = TextStyle(fontSize = 15.sp, lineHeight = 19.sp, fontWeight = FontWeight.SemiBold),
    bodyLarge = TextStyle(fontSize = 15.sp, lineHeight = 20.sp),
    bodyMedium = TextStyle(fontSize = 13.sp, lineHeight = 18.sp),
    labelLarge = TextStyle(fontSize = 13.sp, lineHeight = 16.sp, fontWeight = FontWeight.Medium),
)

val HeadacheInsightShapes = Shapes(
    small = androidx.compose.foundation.shape.RoundedCornerShape(18.dp),
    medium = androidx.compose.foundation.shape.RoundedCornerShape(24.dp),
    large = androidx.compose.foundation.shape.RoundedCornerShape(32.dp),
)

@Composable
fun HeadacheInsightTheme(
    comfortMode: Boolean = true,
    textScale: Float = 1.0f,
    content: @Composable () -> Unit,
) {
    val dark = comfortMode || isSystemInDarkTheme()
    MaterialTheme(
        colorScheme = if (dark) HeadacheInsightDarkScheme else HeadacheInsightLightScheme,
        typography = HeadacheInsightTypographyBase.scaled(textScale.coerceIn(0.85f, 1.35f)),
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
    fun elevated(
        containerColor: Color? = null,
    ) = CardDefaults.elevatedCardColors(
        containerColor = containerColor ?: MaterialTheme.colorScheme.surfaceVariant,
    )
}

private fun Typography.scaled(scale: Float): Typography = copy(
    headlineLarge = headlineLarge.scaled(scale),
    headlineMedium = headlineMedium.scaled(scale),
    headlineSmall = headlineSmall.scaled(scale),
    titleLarge = titleLarge.scaled(scale),
    titleMedium = titleMedium.scaled(scale),
    bodyLarge = bodyLarge.scaled(scale),
    bodyMedium = bodyMedium.scaled(scale),
    labelLarge = labelLarge.scaled(scale),
)

private fun TextStyle.scaled(scale: Float): TextStyle = copy(
    fontSize = fontSize.scaled(scale),
    lineHeight = lineHeight.scaled(scale),
)

private fun TextUnit.scaled(scale: Float): TextUnit =
    if (isUnspecified) this else (value * scale).sp
