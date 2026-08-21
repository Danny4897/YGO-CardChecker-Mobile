package com.ygochecker.core.designsystem

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

val DuelDarkColorScheme = darkColorScheme(
    primary = Color(0xFF00E5FF),
    onPrimary = Color(0xFF00323A),
    primaryContainer = Color(0xFF013642),
    onPrimaryContainer = Color(0xFF8FE9FF),
    inversePrimary = Color(0xFF00727F),
    secondary = Color(0xFFB14EFF),
    onSecondary = Color(0xFF2E0A47),
    secondaryContainer = Color(0xFF3D1259),
    onSecondaryContainer = Color(0xFFE9C9FF),
    tertiary = Color(0xFFE85AA0),
    onTertiary = Color(0xFF430021),
    tertiaryContainer = Color(0xFF5C1237),
    onTertiaryContainer = Color(0xFFFFD6E8),
    background = Color(0xFF121620),
    onBackground = Color(0xFFE8ECF5),
    surface = Color(0xFF121620),
    onSurface = Color(0xFFE8ECF5),
    surfaceVariant = Color(0xFF232838),
    onSurfaceVariant = Color(0xFFB7C0D1),
    surfaceTint = Color(0xFF00E5FF),
    inverseSurface = Color(0xFFE8ECF5),
    inverseOnSurface = Color(0xFF232838),
    error = Color(0xFFFFB4AB),
    onError = Color(0xFF690005),
    errorContainer = Color(0xFF93000A),
    onErrorContainer = Color(0xFFFFDAD6),
    outline = Color(0xFF6B7385),
    outlineVariant = Color(0xFF2A3040),
    scrim = Color(0x99000000),
    surfaceDim = Color(0xFF121620),
    surfaceBright = Color(0xFF2A3040),
    surfaceContainerLowest = Color(0xFF0B0E16),
    surfaceContainerLow = Color(0xFF151A24),
    surfaceContainer = Color(0xFF1A1F2C),
    surfaceContainerHigh = Color(0xFF212736),
    surfaceContainerHighest = Color(0xFF283044),
)

val DuelLightColorScheme = lightColorScheme(
    primary = Color(0xFF0089A3),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFB8F0FF),
    onPrimaryContainer = Color(0xFF001F26),
    inversePrimary = Color(0xFF00E5FF),
    secondary = Color(0xFF7B2FB0),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFF0D9FF),
    onSecondaryContainer = Color(0xFF2E0A47),
    tertiary = Color(0xFFB0286A),
    onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFFFFD6E8),
    onTertiaryContainer = Color(0xFF430021),
    background = Color(0xFFFAF8F2),
    onBackground = Color(0xFF1C1B17),
    surface = Color(0xFFFAF8F2),
    onSurface = Color(0xFF1C1B17),
    surfaceVariant = Color(0xFFE4E2DC),
    onSurfaceVariant = Color(0xFF474743),
    surfaceTint = Color(0xFF0089A3),
    inverseSurface = Color(0xFF30302E),
    inverseOnSurface = Color(0xFFF3F0E8),
    error = Color(0xFFBA1A1A),
    onError = Color(0xFFFFF8F6),
    errorContainer = Color(0xFFFFDAD6),
    onErrorContainer = Color(0xFF410002),
    outline = Color(0xFF787772),
    outlineVariant = Color(0xFFC9C6BF),
    scrim = Color(0x73000000),
    surfaceDim = Color(0xFFDBDAD3),
    surfaceBright = Color(0xFFFAF8F2),
    surfaceContainerLowest = Color(0xFFFFFDF8),
    surfaceContainerLow = Color(0xFFF5F2EB),
    surfaceContainer = Color(0xFFEFEEE7),
    surfaceContainerHigh = Color(0xFFE9E8E1),
    surfaceContainerHighest = Color(0xFFE3E2DB),
)

@Immutable
data class DuelExtendedColors(
    val success: Color,
    val onSuccess: Color,
    val successContainer: Color,
    val onSuccessContainer: Color,
    val warning: Color,
    val onWarning: Color,
    val warningContainer: Color,
    val onWarningContainer: Color,
    val info: Color,
    val onInfo: Color,
    val infoContainer: Color,
    val onInfoContainer: Color,
    val rarityGold: Color,
    val onRarityGold: Color,
)

val DuelLightExtendedColors = DuelExtendedColors(
    success = Color(0xFF256B3D),
    onSuccess = Color(0xFFFFFFFF),
    successContainer = Color(0xFFB8F2A4),
    onSuccessContainer = Color(0xFF0B390A),
    warning = Color(0xFF8B5000),
    onWarning = Color(0xFFFFF8F2),
    warningContainer = Color(0xFFFFDDB8),
    onWarningContainer = Color(0xFF2C1600),
    info = Color(0xFF2F617D),
    onInfo = Color(0xFFF7FBFF),
    infoContainer = Color(0xFFCDE8F8),
    onInfoContainer = Color(0xFF0A3448),
    rarityGold = Color(0xFF8B6F1F),
    onRarityGold = Color(0xFFFFFFFF),
)

val DuelDarkExtendedColors = DuelExtendedColors(
    success = Color(0xFF3DDC97),
    onSuccess = Color(0xFF00391F),
    successContainer = Color(0xFF00522D),
    onSuccessContainer = Color(0xFF8FF5C4),
    warning = Color(0xFFFFB95C),
    onWarning = Color(0xFF472A00),
    warningContainer = Color(0xFF633B00),
    onWarningContainer = Color(0xFFFFDDB8),
    info = Color(0xFF99CBEA),
    onInfo = Color(0xFF083548),
    infoContainer = Color(0xFF1B4A61),
    onInfoContainer = Color(0xFFCDE8F8),
    rarityGold = Color(0xFFE8C45C),
    onRarityGold = Color(0xFF3A2F00),
)

val LocalDuelExtendedColors = staticCompositionLocalOf { DuelLightExtendedColors }
val MaterialTheme.duelExtendedColors: DuelExtendedColors
    @Composable get() = LocalDuelExtendedColors.current

private val BrandFont = FontFamily(
    Font(R.font.rajdhani_semibold, FontWeight.SemiBold),
)
private val UiFont = FontFamily.SansSerif

val DuelTypography = Typography(
    displaySmall = TextStyle(fontFamily = BrandFont, fontWeight = FontWeight.SemiBold, fontSize = 36.sp, lineHeight = 44.sp),
    headlineLarge = TextStyle(fontFamily = BrandFont, fontWeight = FontWeight.SemiBold, fontSize = 32.sp, lineHeight = 40.sp),
    headlineMedium = TextStyle(fontFamily = BrandFont, fontWeight = FontWeight.SemiBold, fontSize = 28.sp, lineHeight = 36.sp),
    headlineSmall = TextStyle(fontFamily = BrandFont, fontWeight = FontWeight.SemiBold, fontSize = 24.sp, lineHeight = 32.sp),
    titleLarge = TextStyle(fontFamily = BrandFont, fontWeight = FontWeight.SemiBold, fontSize = 22.sp, lineHeight = 28.sp),
    titleMedium = TextStyle(fontFamily = UiFont, fontWeight = FontWeight.Medium, fontSize = 16.sp, lineHeight = 24.sp),
    titleSmall = TextStyle(fontFamily = UiFont, fontWeight = FontWeight.Medium, fontSize = 14.sp, lineHeight = 20.sp),
    bodyLarge = TextStyle(fontFamily = UiFont, fontWeight = FontWeight.Normal, fontSize = 16.sp, lineHeight = 24.sp),
    bodyMedium = TextStyle(fontFamily = UiFont, fontWeight = FontWeight.Normal, fontSize = 14.sp, lineHeight = 20.sp),
    bodySmall = TextStyle(fontFamily = UiFont, fontWeight = FontWeight.Normal, fontSize = 12.sp, lineHeight = 16.sp),
    labelLarge = TextStyle(fontFamily = UiFont, fontWeight = FontWeight.Medium, fontSize = 14.sp, lineHeight = 20.sp),
    labelMedium = TextStyle(fontFamily = UiFont, fontWeight = FontWeight.Medium, fontSize = 12.sp, lineHeight = 16.sp),
    labelSmall = TextStyle(fontFamily = UiFont, fontWeight = FontWeight.Medium, fontSize = 11.sp, lineHeight = 16.sp),
)

val DuelShapes = Shapes(
    extraSmall = RoundedCornerShape(4.dp),
    small = RoundedCornerShape(8.dp),
    medium = RoundedCornerShape(12.dp),
    large = RoundedCornerShape(16.dp),
    extraLarge = RoundedCornerShape(28.dp),
)

object DuelSpacing {
    val space0 = 0.dp
    val space1 = 4.dp
    val space2 = 8.dp
    val space3 = 12.dp
    val space4 = 16.dp
    val space5 = 20.dp
    val space6 = 24.dp
    val space8 = 32.dp
    val space10 = 40.dp
    val space12 = 48.dp
}

@Composable
fun YgoCheckerTheme(darkTheme: Boolean, content: @Composable () -> Unit) {
    CompositionLocalProvider(
        LocalDuelExtendedColors provides if (darkTheme) DuelDarkExtendedColors else DuelLightExtendedColors,
    ) {
        MaterialTheme(
            colorScheme = if (darkTheme) DuelDarkColorScheme else DuelLightColorScheme,
            typography = DuelTypography,
            shapes = DuelShapes,
            content = content,
        )
    }
}
