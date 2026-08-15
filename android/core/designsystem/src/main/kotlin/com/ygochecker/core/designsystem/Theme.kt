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
    primary = Color(0xFFE8C45C),
    onPrimary = Color(0xFF3A2F00),
    primaryContainer = Color(0xFF5A4700),
    onPrimaryContainer = Color(0xFFFFE08A),
    inversePrimary = Color(0xFF725B00),
    secondary = Color(0xFF99CBEA),
    onSecondary = Color(0xFF083548),
    secondaryContainer = Color(0xFF1B4A61),
    onSecondaryContainer = Color(0xFFCDE8F8),
    tertiary = Color(0xFFFFB4A6),
    onTertiary = Color(0xFF5F160D),
    tertiaryContainer = Color(0xFF7B2E20),
    onTertiaryContainer = Color(0xFFFFDAD2),
    background = Color(0xFF151823),
    onBackground = Color(0xFFECE8DC),
    surface = Color(0xFF151823),
    onSurface = Color(0xFFECE8DC),
    surfaceVariant = Color(0xFF343945),
    onSurfaceVariant = Color(0xFFC7C6CC),
    surfaceTint = Color(0xFFE8C45C),
    inverseSurface = Color(0xFFE8E6DE),
    inverseOnSurface = Color(0xFF2E303A),
    error = Color(0xFFFFB4AB),
    onError = Color(0xFF690005),
    errorContainer = Color(0xFF93000A),
    onErrorContainer = Color(0xFFFFDAD6),
    outline = Color(0xFF92919A),
    outlineVariant = Color(0xFF454853),
    scrim = Color(0x99000000),
    surfaceDim = Color(0xFF151823),
    surfaceBright = Color(0xFF3A3E49),
    surfaceContainerLowest = Color(0xFF10131D),
    surfaceContainerLow = Color(0xFF191C27),
    surfaceContainer = Color(0xFF1E222D),
    surfaceContainerHigh = Color(0xFF292D38),
    surfaceContainerHighest = Color(0xFF343945),
)

val DuelLightColorScheme = lightColorScheme(
    primary = Color(0xFF725B00),
    onPrimary = Color(0xFFFFF8E1),
    primaryContainer = Color(0xFFFFE08A),
    onPrimaryContainer = Color(0xFF241A00),
    inversePrimary = Color(0xFFE8C45C),
    secondary = Color(0xFF2F617D),
    onSecondary = Color(0xFFF7FBFF),
    secondaryContainer = Color(0xFFCDE8F8),
    onSecondaryContainer = Color(0xFF0A3448),
    tertiary = Color(0xFF984737),
    onTertiary = Color(0xFFFFF8F5),
    tertiaryContainer = Color(0xFFFFDAD2),
    onTertiaryContainer = Color(0xFF3D0801),
    background = Color(0xFFFAF8F2),
    onBackground = Color(0xFF1C1B17),
    surface = Color(0xFFFAF8F2),
    onSurface = Color(0xFF1C1B17),
    surfaceVariant = Color(0xFFE4E2DC),
    onSurfaceVariant = Color(0xFF474743),
    surfaceTint = Color(0xFF725B00),
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
)

private val DuelLightExtendedColors = DuelExtendedColors(
    success = Color(0xFF356A2C),
    onSuccess = Color(0xFFF7FFF2),
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
)

private val DuelDarkExtendedColors = DuelExtendedColors(
    success = Color(0xFF9AD67D),
    onSuccess = Color(0xFF11380B),
    successContainer = Color(0xFF1D511F),
    onSuccessContainer = Color(0xFFB8F2A4),
    warning = Color(0xFFFFB95C),
    onWarning = Color(0xFF472A00),
    warningContainer = Color(0xFF633B00),
    onWarningContainer = Color(0xFFFFDDB8),
    info = Color(0xFF99CBEA),
    onInfo = Color(0xFF083548),
    infoContainer = Color(0xFF1B4A61),
    onInfoContainer = Color(0xFFCDE8F8),
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
