package com.example.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.graphics.Color

private val DarkColorScheme =
  darkColorScheme(
    primary = ElectricBlue,
    secondary = MintGreen,
    tertiary = AmberYellow,
    background = Slate900,
    surface = Slate800,
    onPrimary = Slate50,
    onSecondary = Slate50,
    onTertiary = Slate50,
    onBackground = Slate50,
    onSurface = Slate50,
    error = CoralRed
  )

private val LightColorScheme =
  lightColorScheme(
    primary = ElectricBlue,
    secondary = MintGreen,
    tertiary = AmberYellow,
    background = Slate50,
    surface = Color(0xFFFFFFFF),
    onPrimary = Color.White,
    onSecondary = Color.White,
    onTertiary = Color.White,
    onBackground = Slate900,
    onSurface = Slate900,
    error = CoralRed
  )

@Composable
fun MyApplicationTheme(
  darkTheme: Boolean = true, // Force premium dark mode by default for ReturnRadar
  dynamicColor: Boolean = false, // Disable dynamic colors to maintain custom brand identity
  content: @Composable () -> Unit,
) {
  val colorScheme =
    when {
      dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
        val context = LocalContext.current
        if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
      }

      darkTheme -> DarkColorScheme
      else -> LightColorScheme
    }

  MaterialTheme(colorScheme = colorScheme, typography = Typography, content = content)
}
