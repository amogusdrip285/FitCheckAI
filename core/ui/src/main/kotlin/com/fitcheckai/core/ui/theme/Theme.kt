package com.fitcheckai.core.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

// Hướng thị giác (bản đầu, Track D tinh chỉnh): editorial thời trang — giấy ấm, mực đậm, nhấn đất nung.
private object Palette {
    val Paper = Color(0xFFF6F1EA)
    val PaperDim = Color(0xFFEAE2D6)
    val Ink = Color(0xFF1E1B18)
    val InkSoft = Color(0xFF5B544C)
    val Terracotta = Color(0xFFB85A3C)
    val Olive = Color(0xFF6B7148)
    val Night = Color(0xFF141210)
    val NightSurface = Color(0xFF221F1B)
    val Error = Color(0xFFB3261E)
}

private val LightColors = lightColorScheme(
    primary = Palette.Ink,
    onPrimary = Palette.Paper,
    secondary = Palette.Terracotta,
    onSecondary = Color.White,
    tertiary = Palette.Olive,
    background = Palette.Paper,
    onBackground = Palette.Ink,
    surface = Palette.Paper,
    onSurface = Palette.Ink,
    surfaceVariant = Palette.PaperDim,
    onSurfaceVariant = Palette.InkSoft,
    error = Palette.Error,
)

private val DarkColors = darkColorScheme(
    primary = Palette.Paper,
    onPrimary = Palette.Ink,
    secondary = Palette.Terracotta,
    onSecondary = Color.White,
    tertiary = Palette.Olive,
    background = Palette.Night,
    onBackground = Palette.Paper,
    surface = Palette.Night,
    onSurface = Palette.Paper,
    surfaceVariant = Palette.NightSurface,
    onSurfaceVariant = Palette.PaperDim,
    error = Palette.Error,
)

private val Display = FontFamily.Serif
private val Body = FontFamily.SansSerif

private val AppTypography = Typography(
    displaySmall = TextStyle(fontFamily = Display, fontWeight = FontWeight.Medium, fontSize = 36.sp, lineHeight = 40.sp),
    headlineMedium = TextStyle(fontFamily = Display, fontWeight = FontWeight.Medium, fontSize = 28.sp, lineHeight = 34.sp),
    titleLarge = TextStyle(fontFamily = Display, fontWeight = FontWeight.Medium, fontSize = 22.sp, lineHeight = 28.sp),
    titleMedium = TextStyle(fontFamily = Body, fontWeight = FontWeight.SemiBold, fontSize = 16.sp, lineHeight = 22.sp),
    bodyLarge = TextStyle(fontFamily = Body, fontSize = 16.sp, lineHeight = 24.sp),
    bodyMedium = TextStyle(fontFamily = Body, fontSize = 14.sp, lineHeight = 20.sp),
    labelLarge = TextStyle(fontFamily = Body, fontWeight = FontWeight.SemiBold, fontSize = 14.sp, letterSpacing = 0.4.sp),
    labelSmall = TextStyle(fontFamily = Body, fontWeight = FontWeight.Medium, fontSize = 11.sp, letterSpacing = 1.2.sp),
)

private val AppShapes = Shapes(
    small = RoundedCornerShape(6.dp),
    medium = RoundedCornerShape(14.dp),
    large = RoundedCornerShape(28.dp),
)

/** Khoảng cách chuẩn — dùng thay cho số cứng. */
object Spacing {
    val xs = 4.dp
    val sm = 8.dp
    val md = 16.dp
    val lg = 24.dp
    val xl = 40.dp
}

@Composable
fun FitCheckTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        typography = AppTypography,
        shapes = AppShapes,
        content = content,
    )
}
