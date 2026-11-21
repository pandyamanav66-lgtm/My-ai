package com.example.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

@Immutable
data class DrstColors(
    val bg: Color,
    val surface: Color,
    val surface2: Color,
    val ink: Color,
    val muted: Color,
    val line: Color,
    val band: Color,
    val band2: Color,
    val onBand: Color,
    val onBandMuted: Color,
    val gold: Color,
    val goldInk: Color,
    val track: Color,
    val ok: Color,
    val okBg: Color,
    val okInk: Color,
    val warn: Color,
    val warnBg: Color,
    val warnInk: Color,
    val badBg: Color,
    val badInk: Color,
    // Liquid Glass tokens
    val glassFill: Color,
    val glassFillStrong: Color,
    val glassBorderTop: Color,
    val glassBorderBottom: Color,
    val glassHighlight: Color,
    val bgGradTop: Color,
    val bgGradMid: Color,
    val bgGradBottom: Color,
    val orbPrimary: Color,
    val orbGold: Color,
    val orbAccent: Color,
    val isDark: Boolean
)

val LightDrstColors = DrstColors(
    bg = Color(0xFFEDF1F7),
    surface = Color(0xCCFFFFFF),
    surface2 = Color(0xB8F7F3EA),
    ink = Color(0xFF0B1F3A),
    muted = Color(0xFF52647D),
    line = Color(0x55FFFFFF),
    band = Color(0xFF0B1F3A),
    band2 = Color(0xFF1A365D),
    onBand = Color(0xFFF6F2E9),
    onBandMuted = Color(0xFFB5C4DA),
    gold = Color(0xFFC59B52),
    goldInk = Color(0xFF8A6526),
    track = Color(0x280B1F3A),
    ok = Color(0xFF239B62),
    okBg = Color(0xC2DCF5E7),
    okInk = Color(0xFF125A36),
    warn = Color(0xFFDF9F00),
    warnBg = Color(0xC4FFF2C6),
    warnInk = Color(0xFF7A5600),
    badBg = Color(0xCCFDE0CE),
    badInk = Color(0xFF9A3412),
    // Liquid Glass light palette (luminous champagne + glacial glass)
    glassFill = Color(0xB8FFFFFF),
    glassFillStrong = Color(0xDCFFFFFF),
    glassBorderTop = Color(0xF0FFFFFF),
    glassBorderBottom = Color(0x52B8955A),
    glassHighlight = Color(0x70FFFFFF),
    bgGradTop = Color(0xFFE7EFFA),
    bgGradMid = Color(0xFFF5EFE3),
    bgGradBottom = Color(0xFFE4ECF7),
    orbPrimary = Color(0x3D4C8BF5),
    orbGold = Color(0x47D4AF6A),
    orbAccent = Color(0x302FA36B),
    isDark = false
)

val DarkDrstColors = DrstColors(
    bg = Color(0xFF071324),
    surface = Color(0xB5102542),
    surface2 = Color(0x99163156),
    ink = Color(0xFFF4F1E8),
    muted = Color(0xFFA2B5CE),
    line = Color(0x36FFFFFF),
    band = Color(0xFF061121),
    band2 = Color(0xFF153057),
    onBand = Color(0xFFF4F1E8),
    onBandMuted = Color(0xFFA2B5CE),
    gold = Color(0xFFDFBC7E),
    goldInk = Color(0xFFE6C88F),
    track = Color(0x3DFFFFFF),
    ok = Color(0xFF45CE8D),
    okBg = Color(0xA6133B2E),
    okInk = Color(0xFF95F0BE),
    warn = Color(0xFFF5C53A),
    warnBg = Color(0xA63B3215),
    warnInk = Color(0xFFF7DA84),
    badBg = Color(0xB34E2619),
    badInk = Color(0xFFFFA885),
    // Liquid Glass dark palette (deep midnight sapphire + specular crystal glass)
    glassFill = Color(0x94112949),
    glassFillStrong = Color(0xCC132E52),
    glassBorderTop = Color(0x4DFFFFFF),
    glassBorderBottom = Color(0x2ED4B27A),
    glassHighlight = Color(0x26FFFFFF),
    bgGradTop = Color(0xFF050E1C),
    bgGradMid = Color(0xFF0B1E38),
    bgGradBottom = Color(0xFF071427),
    orbPrimary = Color(0x422563EB),
    orbGold = Color(0x33D4B27A),
    orbAccent = Color(0x2E14B8A6),
    isDark = true
)

val LocalDrstColors = staticCompositionLocalOf { LightDrstColors }

fun categoryColor(category: String): Color = when (category) {
    "Study" -> Color(0xFF3B82F6)
    "Revision" -> Color(0xFF8B5CF6)
    "Practice" -> Color(0xFF14B8A6)
    "Classes" -> Color(0xFF6366F1)
    "Morning" -> Color(0xFFF59E0B)
    "Fitness" -> Color(0xFF22C55E)
    "Meals" -> Color(0xFFF97316)
    "Break" -> Color(0xFF06B6D4)
    "Reading" -> Color(0xFFA855F7)
    "Planning" -> Color(0xFFC59B52)
    "Personal" -> Color(0xFFEC4899)
    "Sleep" -> Color(0xFF64748B)
    else -> Color(0xFF64748B)
}
