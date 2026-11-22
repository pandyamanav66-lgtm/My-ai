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
    // Prism iOS 26 Liquid Glass tokens
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
    val prismCyan: Color,
    val prismViolet: Color,
    val prismCoral: Color,
    val isDark: Boolean
)

/**
 * Airy Glacier Prism — Minimal, luminous, joyful light Liquid Glass palette.
 */
val LightDrstColors = DrstColors(
    bg = Color(0xFFF2F6FF),
    surface = Color(0xD6FFFFFF),
    surface2 = Color(0xA6F5F8FF),
    ink = Color(0xFF0F172A),
    muted = Color(0xFF5A6B86),
    line = Color(0x99FFFFFF),
    band = Color(0xFF1A2352),
    band2 = Color(0xFF33418F),
    onBand = Color(0xFFFAFBFD),
    onBandMuted = Color(0xFFC9D6FA),
    gold = Color(0xFFFF9F2E),
    goldInk = Color(0xFFC25E00),
    track = Color(0x1E0F172A),
    ok = Color(0xFF10B981),
    okBg = Color(0xB8D8FAEC),
    okInk = Color(0xFF047857),
    warn = Color(0xFFF59E0B),
    warnBg = Color(0xB8FEF3C7),
    warnInk = Color(0xFFB45309),
    badBg = Color(0xCCFFE4E6),
    badInk = Color(0xFFBE123C),
    // Airy crystal frost glass
    glassFill = Color(0xB8FFFFFF),
    glassFillStrong = Color(0xE6FFFFFF),
    glassBorderTop = Color(0xFFFFFFFF),
    glassBorderBottom = Color(0x66B8CCF0),
    glassHighlight = Color(0xA6FFFFFF),
    bgGradTop = Color(0xFFE7F1FF),
    bgGradMid = Color(0xFFF7F2FF),
    bgGradBottom = Color(0xFFE6FAF6),
    orbPrimary = Color(0x6638BDF8),
    orbGold = Color(0x52FB923C),
    orbAccent = Color(0x52A78BFA),
    prismCyan = Color(0xFF0EA5E9),
    prismViolet = Color(0xFF7C3AED),
    prismCoral = Color(0xFFFF6B6B),
    isDark = false
)

/**
 * Midnight Prism Glass — Ultra-clean, luminous dark iOS 26 Liquid Glass palette
 * with vibrant aurora reflections and razor-sharp crystal edges.
 */
val DarkDrstColors = DrstColors(
    bg = Color(0xFF070916),
    surface = Color(0x88151C38),
    surface2 = Color(0x6E1E284D),
    ink = Color(0xFFF8FAFF),
    muted = Color(0xFF9EB0D0),
    line = Color(0x3DFFFFFF),
    band = Color(0xFF131938),
    band2 = Color(0xFF283470),
    onBand = Color(0xFFFAFBFD),
    onBandMuted = Color(0xFFC2D0EE),
    gold = Color(0xFFFFB84D),
    goldInk = Color(0xFFFFCB77),
    track = Color(0x2EFFFFFF),
    ok = Color(0xFF2EE6A6),
    okBg = Color(0x59104A3B),
    okInk = Color(0xFF7FFFD0),
    warn = Color(0xFFFBBF24),
    warnBg = Color(0x594A3512),
    warnInk = Color(0xFFFDE68A),
    badBg = Color(0x6E5B1A2B),
    badInk = Color(0xFFFFA8B8),
    // Ultra-clear frosted dark glass with bright specular rim
    glassFill = Color(0x21FFFFFF),
    glassFillStrong = Color(0x36FFFFFF),
    glassBorderTop = Color(0x7AFFFFFF),
    glassBorderBottom = Color(0x297DD3FC),
    glassHighlight = Color(0x26FFFFFF),
    bgGradTop = Color(0xFF060814),
    bgGradMid = Color(0xFF0D112B),
    bgGradBottom = Color(0xFF061322),
    orbPrimary = Color(0x6638BDF8),
    orbGold = Color(0x4DFB923C),
    orbAccent = Color(0x5E818CF8),
    prismCyan = Color(0xFF38BDF8),
    prismViolet = Color(0xFFA78BFA),
    prismCoral = Color(0xFFFF7A90),
    isDark = true
)

val LocalDrstColors = staticCompositionLocalOf { LightDrstColors }

fun categoryColor(category: String): Color = when (category) {
    "Study" -> Color(0xFF38BDF8)
    "Revision" -> Color(0xFFA78BFA)
    "Practice" -> Color(0xFF2EE6A6)
    "Classes" -> Color(0xFF6366F1)
    "Morning" -> Color(0xFFFFB84D)
    "Fitness" -> Color(0xFF34D399)
    "Meals" -> Color(0xFFFB923C)
    "Break" -> Color(0xFF22D3EE)
    "Reading" -> Color(0xFFC084FC)
    "Planning" -> Color(0xFFF472B6)
    "Personal" -> Color(0xFFFF7A90)
    "Sleep" -> Color(0xFF818CF8)
    else -> Color(0xFF94A3B8)
}
