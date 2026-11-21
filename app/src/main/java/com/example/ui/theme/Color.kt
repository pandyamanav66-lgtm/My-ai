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
    // Authentic iOS 26 Liquid Glass tokens
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
    bg = Color(0xFFE9F0FA),
    surface = Color(0xCCFFFFFF),
    surface2 = Color(0x99F3F7FD),
    ink = Color(0xFF0A1B33),
    muted = Color(0xFF4F627D),
    line = Color(0x8AFFFFFF),
    band = Color(0xFF0C2240),
    band2 = Color(0xFF1A3D6B),
    onBand = Color(0xFFF8FAFC),
    onBandMuted = Color(0xFFBDD0E8),
    gold = Color(0xFFD2A44F),
    goldInk = Color(0xFF8C641E),
    track = Color(0x260A1B33),
    ok = Color(0xFF1EA86B),
    okBg = Color(0xB8D9F7E8),
    okInk = Color(0xFF0E5C38),
    warn = Color(0xFFE5A100),
    warnBg = Color(0xB8FFF1C2),
    warnInk = Color(0xFF7D5600),
    badBg = Color(0xC2FDE0D2),
    badInk = Color(0xFF9E3311),
    // iOS 26 Liquid Glass Light (luminous glacial frost + crystal white specular edges)
    glassFill = Color(0xA6FFFFFF),
    glassFillStrong = Color(0xD9FFFFFF),
    glassBorderTop = Color(0xF5FFFFFF),
    glassBorderBottom = Color(0x5CA9C0E0),
    glassHighlight = Color(0x94FFFFFF),
    bgGradTop = Color(0xFFDCEBFF),
    bgGradMid = Color(0xFFF4EFF8),
    bgGradBottom = Color(0xFFDCEBF9),
    orbPrimary = Color(0x524A90FF),
    orbGold = Color(0x4DE5BE72),
    orbAccent = Color(0x4226C6B8),
    isDark = false
)

val DarkDrstColors = DrstColors(
    bg = Color(0xFF050D1A),
    surface = Color(0x88142C4F),
    surface2 = Color(0x701B3863),
    ink = Color(0xFFF5F7FB),
    muted = Color(0xFFA7BCD6),
    line = Color(0x47FFFFFF),
    band = Color(0xFF071428),
    band2 = Color(0xFF15345E),
    onBand = Color(0xFFF7F9FC),
    onBandMuted = Color(0xFFB3C7E0),
    gold = Color(0xFFE6C382),
    goldInk = Color(0xFFEFD199),
    track = Color(0x38FFFFFF),
    ok = Color(0xFF3CD993),
    okBg = Color(0x8F124032),
    okInk = Color(0xFF9CF4C7),
    warn = Color(0xFFF7C948),
    warnBg = Color(0x8F423615),
    warnInk = Color(0xFFFBE18F),
    badBg = Color(0x9E572419),
    badInk = Color(0xFFFFB294),
    // iOS 26 Liquid Glass Dark (deep sapphire crystal + high-clarity specular rim)
    glassFill = Color(0x7A163159),
    glassFillStrong = Color(0xB8142E54),
    glassBorderTop = Color(0x75FFFFFF),
    glassBorderBottom = Color(0x337DB4FF),
    glassHighlight = Color(0x36FFFFFF),
    bgGradTop = Color(0xFF040B17),
    bgGradMid = Color(0xFF091D39),
    bgGradBottom = Color(0xFF051022),
    orbPrimary = Color(0x593B82F6),
    orbGold = Color(0x3DE0B76A),
    orbAccent = Color(0x4714B8A6),
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
