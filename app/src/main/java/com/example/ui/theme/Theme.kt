package com.example.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

@Immutable
data class ApptividadesExtendedColors(
    val isDark: Boolean = false,
    val ground: Color = Ground,
    val surface: Color = SurfaceWhite,
    val line: Color = LineBorder,
    val ink: Color = Ink,
    val ink2: Color = Ink2,
    val ink3: Color = Ink3,
    val muted: Color = Muted,
    val onDarkMuted: Color = OnDarkMuted,
    val heroCardBg: Color = Ink,
    val heroCardBorder: Color = Ink2,
    val heroCardSurface: Color = Ink2,
    val heroCardText: Color = SurfaceWhite,
    val accent: Color = Accent,
    val accentHover: Color = AccentHover,
    val accentSoft: Color = AccentSoft,
    val accentBright: Color = AccentBright,
    val danger: Color = Danger,
    val onDanger: Color = OnDangerWhite,
    val dangerText: Color = DangerText,
    val dangerBg: Color = DangerBg,
    val dangerBorder: Color = DangerBorder,
    val success: Color = Success,
    val successText: Color = SuccessText,
    val successBg: Color = SuccessBg,
    val info: Color = Info,
    val infoBg: Color = InfoBg,
    val neutralChipBg: Color = NeutralChipBg,
    val neutralChipText: Color = NeutralChipText,
    val sharePalette: List<Color> = SharePalette,
)

private val ApptividadesLightExtendedColors = ApptividadesExtendedColors()

private val ApptividadesDarkExtendedColors = ApptividadesExtendedColors(
    isDark = true,
    ground = DarkGround,
    surface = DarkSurface,
    line = DarkLineBorder,
    ink = DarkInk,
    ink2 = DarkInk2,
    ink3 = DarkInk3,
    muted = DarkMuted,
    onDarkMuted = DarkOnHeroMuted,
    heroCardBg = DarkHeroBg,
    heroCardBorder = DarkHeroBorder,
    heroCardSurface = DarkHeroSurface,
    heroCardText = DarkHeroText,
    accent = DarkAccent,
    accentHover = DarkAccentHover,
    accentSoft = DarkAccentSoft,
    accentBright = DarkAccentBright,
    danger = DarkDanger,
    onDanger = OnDangerWhite,
    dangerText = DarkDangerText,
    dangerBg = DarkDangerBg,
    dangerBorder = DarkDangerBorder,
    success = DarkSuccess,
    successText = DarkSuccessText,
    successBg = DarkSuccessBg,
    info = DarkInfo,
    infoBg = DarkInfoBg,
    neutralChipBg = DarkNeutralChipBg,
    neutralChipText = DarkNeutralChipText,
    sharePalette = DarkSharePalette,
)

val LocalApptividadesColors = staticCompositionLocalOf { ApptividadesLightExtendedColors }

private val ApptividadesLightColorScheme = lightColorScheme(
    primary = Accent,
    onPrimary = SurfaceWhite,
    primaryContainer = AccentSoft,
    onPrimaryContainer = AccentHover,
    secondary = Ink,
    onSecondary = SurfaceWhite,
    secondaryContainer = NeutralChipBg,
    onSecondaryContainer = Ink,
    tertiary = Success,
    onTertiary = SurfaceWhite,
    tertiaryContainer = SuccessBg,
    onTertiaryContainer = SuccessText,
    background = Ground,
    onBackground = Ink,
    surface = SurfaceWhite,
    onSurface = Ink,
    surfaceVariant = NeutralChipBg,
    onSurfaceVariant = Muted,
    outline = LineBorder,
    outlineVariant = LineBorder,
    error = Danger,
    onError = OnDangerWhite,
    errorContainer = DangerBg,
    onErrorContainer = DangerText,
)

private val ApptividadesDarkColorScheme = darkColorScheme(
    primary = DarkAccent,
    onPrimary = DarkSurface,
    primaryContainer = DarkAccentSoft,
    onPrimaryContainer = DarkAccentHover,
    secondary = DarkInk,
    onSecondary = DarkSurface,
    secondaryContainer = DarkNeutralChipBg,
    onSecondaryContainer = DarkInk,
    tertiary = DarkSuccess,
    onTertiary = DarkSurface,
    tertiaryContainer = DarkSuccessBg,
    onTertiaryContainer = DarkSuccessText,
    background = DarkGround,
    onBackground = DarkInk,
    surface = DarkSurface,
    onSurface = DarkInk,
    surfaceVariant = DarkNeutralChipBg,
    onSurfaceVariant = DarkMuted,
    outline = DarkLineBorder,
    outlineVariant = DarkLineBorder,
    error = DarkDanger,
    onError = OnDangerWhite,
    errorContainer = DarkDangerBg,
    onErrorContainer = DarkDangerText,
)

private val ApptividadesShapes = Shapes(
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(22.dp),
)

@Composable
fun ApptividadesTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    val extendedColors = if (darkTheme) ApptividadesDarkExtendedColors else ApptividadesLightExtendedColors
    val colorScheme = if (darkTheme) ApptividadesDarkColorScheme else ApptividadesLightColorScheme

    CompositionLocalProvider(
        LocalApptividadesColors provides extendedColors
    ) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = ApptividadesTypography,
            shapes = ApptividadesShapes,
            content = content,
        )
    }
}

object ApptividadesThemeDefaults {
    val colors: ApptividadesExtendedColors
        @Composable get() = LocalApptividadesColors.current
}
