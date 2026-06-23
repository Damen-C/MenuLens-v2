package com.menulens.app.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

val Canvas = Color(0xFFFCFAF5)
val Paper = Color(0xFFFFFEFB)
val Ink = Color(0xFF211F1B)
val Vermilion = Color(0xFFB33A2D)
val Moss = Color(0xFF647653)
val Gold = Color(0xFFB28A45)
val MutedInk = Color(0xFF6C665D)
val Hairline = Color(0xFFDDD6CA)
val SoftFill = Color(0xFFF3EFE7)

private val MenuLensColors = lightColorScheme(
    primary = Vermilion,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFF5E5E1),
    onPrimaryContainer = Color(0xFF5D1B14),
    secondary = Moss,
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFE7EDDF),
    onSecondaryContainer = Color(0xFF2B3822),
    tertiary = Gold,
    onTertiary = Ink,
    background = Canvas,
    onBackground = Ink,
    surface = Paper,
    onSurface = Ink,
    surfaceVariant = SoftFill,
    onSurfaceVariant = MutedInk,
    outline = Hairline
)

private val MenuLensTypography = Typography(
    displayLarge = TextStyle(
        fontFamily = FontFamily.Serif,
        fontWeight = FontWeight.Medium,
        fontSize = 43.sp,
        lineHeight = 49.sp,
        letterSpacing = (-0.7).sp
    ),
    headlineLarge = TextStyle(
        fontFamily = FontFamily.Serif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 36.sp,
        lineHeight = 40.sp,
        letterSpacing = (-0.5).sp
    ),
    headlineMedium = TextStyle(
        fontFamily = FontFamily.Serif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 29.sp,
        lineHeight = 34.sp,
        letterSpacing = (-0.25).sp
    ),
    titleLarge = TextStyle(
        fontFamily = FontFamily.Serif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 23.sp,
        lineHeight = 28.sp
    ),
    titleMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 16.sp,
        lineHeight = 21.sp
    ),
    bodyLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Normal,
        fontSize = 16.sp,
        lineHeight = 23.sp
    ),
    bodyMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Normal,
        fontSize = 14.sp,
        lineHeight = 20.sp
    ),
    labelLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 14.sp,
        lineHeight = 18.sp,
        letterSpacing = 0.1.sp
    ),
    labelMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Medium,
        fontSize = 12.sp,
        lineHeight = 16.sp,
        letterSpacing = 0.15.sp
    )
)

private val MenuLensShapes = Shapes(
    small = RoundedCornerShape(6.dp),
    medium = RoundedCornerShape(10.dp),
    large = RoundedCornerShape(14.dp)
)

@Composable
fun MenuLensTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = MenuLensColors,
        typography = MenuLensTypography,
        shapes = MenuLensShapes,
        content = content
    )
}
