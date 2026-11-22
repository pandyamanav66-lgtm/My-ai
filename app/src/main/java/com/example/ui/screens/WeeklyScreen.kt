package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.graphics.Brush
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.SaveAlt
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.*
import com.example.ui.components.DrstCard
import com.example.ui.theme.InterFontFamily
import com.example.ui.theme.LocalDrstColors
import com.example.ui.theme.SpaceGroteskFontFamily
import java.util.Locale
import kotlin.math.roundToInt

@Composable
fun WeeklyScreen(
    profile: UserProfile,
    weekStartMonday: String,
    onPrevWeek: () -> Unit,
    onNextWeek: () -> Unit,
    onJumpCurrentWeek: () -> Unit,
    onOpenDay: (String) -> Unit,
    onCopyReport: () -> Unit,
    onShareWhatsApp: () -> Unit,
    onSaveReportFile: () -> Unit
) {
    val c = LocalDrstColors.current
    val weekDays = remember(weekStartMonday) { (0..6).map { shiftDateKey(weekStartMonday, it) } }
    val weekEnd = weekDays.last()
    val dayPcts = weekDays.map { Analytics.completionPercent(profile, it) }
    val dayStudyHrs = weekDays.map { Analytics.studyHoursForDay(profile, it) }

    val avgPct = dayPcts.average().roundToInt()
    val totalStudyHrs = dayStudyHrs.sum()
    val totalDone = weekDays.sumOf { Analytics.completedCount(profile, it) }
    val strongDays = weekDays.count {
        Analytics.completionPercent(profile, it) >= 75 || profile.days[it]?.isRestDay == true
    }

    // 12-week heatmap = 84 days ending at the Sunday of the current/selected week
    val heatmapDates = remember(weekEnd) {
        (83 downTo 0).map { shiftDateKey(weekEnd, -it) }
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 114.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // 1. Week Navigation
        item(key = "week_nav") {
            DrstCard {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    FilledTonalIconButton(
                        onClick = onPrevWeek,
                        modifier = Modifier.size(42.dp).testTag("prev_week_button"),
                        colors = IconButtonDefaults.filledTonalIconButtonColors(
                            containerColor = c.surface2,
                            contentColor = c.ink
                        )
                    ) {
                        Icon(Icons.AutoMirrored.Rounded.KeyboardArrowLeft, contentDescription = "Previous week")
                    }

                    Column(
                        modifier = Modifier.weight(1f),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = "${formatCompactDate(weekStartMonday)} – ${formatCompactDate(weekEnd)}",
                            style = TextStyle(
                                fontFamily = SpaceGroteskFontFamily,
                                fontWeight = FontWeight.Bold,
                                fontSize = 15.sp,
                                color = c.ink
                            )
                        )
                        Text(
                            text = "Weekly Summary",
                            style = TextStyle(fontFamily = InterFontFamily, fontSize = 11.sp, color = c.muted)
                        )
                    }

                    FilledTonalIconButton(
                        onClick = onNextWeek,
                        modifier = Modifier.size(42.dp).testTag("next_week_button"),
                        colors = IconButtonDefaults.filledTonalIconButtonColors(
                            containerColor = c.surface2,
                            contentColor = c.ink
                        )
                    ) {
                        Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, contentDescription = "Next week")
                    }

                    TextButton(onClick = onJumpCurrentWeek) {
                        Text("Now", color = c.goldInk, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }

        // 2. 4 Weekly KPIs
        item(key = "weekly_kpis") {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                WeeklyKpiPill("AVG %", "$avgPct%", if (avgPct >= 75) c.okInk else c.warnInk, Modifier.weight(1f))
                WeeklyKpiPill("STUDY", String.format(Locale.US, "%.1fh", totalStudyHrs), c.goldInk, Modifier.weight(1f))
                WeeklyKpiPill("DONE", "$totalDone", c.ink, Modifier.weight(1f))
                WeeklyKpiPill("75%+", "$strongDays/7", c.okInk, Modifier.weight(1f))
            }
        }

        // 3. Combo Chart (Completion Bars + Study-Hours Line)
        item(key = "combo_chart") {
            val shortDays = listOf("Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun")
            val maxStudy = (dayStudyHrs.maxOrNull() ?: 6.0).coerceAtLeast(profile.dailyStudyGoalHours).coerceAtLeast(2.0)

            DrstCard(title = "Completion % & Study Hours") {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    LegendDot("Completion %", c.ok)
                    LegendDot("Study Hours", c.gold)
                }

                Canvas(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(155.dp)
                ) {
                    val w = size.width
                    val h = size.height - 8.dp.toPx()
                    val step = w / 7f
                    val barW = (step * 0.44f).coerceAtMost(32.dp.toPx())

                    // Subtle gridlines at 25%, 50%, 75%, 100%
                    listOf(0.25f, 0.5f, 0.75f, 1f).forEach { frac ->
                        val y = h * (1f - frac)
                        drawLine(
                            color = c.line.copy(alpha = 0.65f),
                            start = Offset(0f, y),
                            end = Offset(w, y),
                            strokeWidth = 1.dp.toPx()
                        )
                    }

                    // Bars for completion %
                    val linePoints = mutableListOf<Offset>()
                    for (i in 0..6) {
                        val cx = step * i + step / 2f
                        val pctFrac = (dayPcts[i] / 100f).coerceIn(0f, 1f)
                        val barH = (h * pctFrac).coerceAtLeast(4.dp.toPx())
                        drawRoundRect(
                            brush = if (dayPcts[i] >= 75) {
                                Brush.verticalGradient(listOf(c.prismCyan, c.ok))
                            } else {
                                Brush.verticalGradient(listOf(c.prismViolet.copy(alpha = 0.85f), c.gold.copy(alpha = 0.80f)))
                            },
                            topLeft = Offset(cx - barW / 2f, h - barH),
                            size = Size(barW, barH),
                            cornerRadius = CornerRadius(8.dp.toPx(), 8.dp.toPx())
                        )

                        val studyFrac = (dayStudyHrs[i] / maxStudy).toFloat().coerceIn(0f, 1f)
                        val ly = h - (h * studyFrac)
                        linePoints.add(Offset(cx, ly.coerceIn(6.dp.toPx(), h - 4.dp.toPx())))
                    }

                    // Line for study hours
                    val path = Path().apply {
                        linePoints.forEachIndexed { i, pt ->
                            if (i == 0) moveTo(pt.x, pt.y) else lineTo(pt.x, pt.y)
                        }
                    }
                    drawPath(
                        path = path,
                        color = c.gold,
                        style = Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round)
                    )
                    linePoints.forEach { pt ->
                        drawCircle(color = c.surface, radius = 5.dp.toPx(), center = pt)
                        drawCircle(color = c.gold, radius = 3.5.dp.toPx(), center = pt)
                    }
                }

                Spacer(Modifier.height(6.dp))

                Row(modifier = Modifier.fillMaxWidth()) {
                    shortDays.forEachIndexed { i, lbl ->
                        Column(
                            modifier = Modifier.weight(1f),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                text = lbl,
                                style = TextStyle(fontFamily = InterFontFamily, fontSize = 11.sp, color = c.muted)
                            )
                            Text(
                                text = String.format(Locale.US, "%.1fh", dayStudyHrs[i]),
                                style = TextStyle(
                                    fontFamily = SpaceGroteskFontFamily,
                                    fontWeight = FontWeight.SemiBold,
                                    fontSize = 10.sp,
                                    color = c.goldInk
                                )
                            )
                        }
                    }
                }
            }
        }

        // 4. 7 Day Rows (tap opens that day on Checklist)
        item(key = "week_day_rows") {
            DrstCard(title = "Daily Breakdown (Tap a Day to Open)") {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    weekDays.forEachIndexed { idx, dKey ->
                        val p = dayPcts[idx]
                        val hrs = dayStudyHrs[idx]
                        val rest = profile.days[dKey]?.isRestDay == true
                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onOpenDay(dKey) }
                                .testTag("weekly_day_row_$dKey"),
                            shape = RoundedCornerShape(12.dp),
                            color = c.surface2,
                            border = BorderStroke(1.dp, c.line)
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Column(modifier = Modifier.width(104.dp)) {
                                    Text(
                                        text = formatFriendlyDate(dKey).substringBeforeLast(" "),
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        style = TextStyle(
                                            fontFamily = InterFontFamily,
                                            fontWeight = FontWeight.SemiBold,
                                            fontSize = 13.sp,
                                            color = c.ink
                                        )
                                    )
                                    if (rest) {
                                        Text(
                                            text = "Rest day",
                                            style = TextStyle(fontFamily = InterFontFamily, fontSize = 10.sp, color = c.goldInk)
                                        )
                                    }
                                }

                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(8.dp)
                                        .clip(RoundedCornerShape(99.dp))
                                        .background(c.track)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxHeight()
                                            .fillMaxWidth((p / 100f).coerceIn(0f, 1f))
                                            .clip(RoundedCornerShape(99.dp))
                                            .background(if (rest) c.gold else if (p >= 75) c.ok else c.warn)
                                    )
                                }

                                Text(
                                    text = "$p% • ${String.format(Locale.US, "%.1fh", hrs)}",
                                    style = TextStyle(
                                        fontFamily = SpaceGroteskFontFamily,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 12.sp,
                                        color = c.ink,
                                        fontFeatureSettings = "tnum"
                                    )
                                )
                            }
                        }
                    }
                }
            }
        }

        // 5. 12-Week Heatmap (84 cells — 12 columns x 7 rows)
        item(key = "heatmap_12_week") {
            DrstCard(title = "12-Week Consistency Heatmap (84 Days)") {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    // 12 columns (weeks), each column has 7 rows (Mon..Sun)
                    for (weekCol in 0 until 12) {
                        Column(
                            modifier = Modifier.weight(1f),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            for (dayRow in 0 until 7) {
                                val cellIndex = weekCol * 7 + dayRow
                                val dKey = heatmapDates[cellIndex]
                                val pct = Analytics.completionPercent(profile, dKey)
                                val rest = profile.days[dKey]?.isRestDay == true
                                val cellColor = when {
                                    rest -> c.gold
                                    pct >= 90 -> c.ok
                                    pct >= 75 -> c.ok.copy(alpha = 0.72f)
                                    pct >= 40 -> c.warn.copy(alpha = 0.75f)
                                    pct > 0 -> c.warn.copy(alpha = 0.35f)
                                    else -> c.track
                                }
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .aspectRatio(1f)
                                        .clip(RoundedCornerShape(4.dp))
                                        .background(cellColor)
                                        .clickable { onOpenDay(dKey) }
                                        .testTag("heatmap_cell_$cellIndex")
                                )
                            }
                        }
                    }
                }
                Spacer(Modifier.height(10.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Tap any cell to open that day",
                        style = TextStyle(fontFamily = InterFontFamily, fontSize = 11.sp, color = c.muted)
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
                        listOf(c.track, c.warn.copy(alpha = 0.6f), c.ok.copy(alpha = 0.7f), c.ok, c.gold).forEach { sw ->
                            Box(Modifier.size(10.dp).clip(RoundedCornerShape(2.dp)).background(sw))
                        }
                    }
                }
            }
        }

        // 6. Share / Export Weekly Report
        item(key = "weekly_export_actions") {
            DrstCard(title = "Share Weekly Report") {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(
                        onClick = onCopyReport,
                        modifier = Modifier.weight(1f).height(46.dp).testTag("copy_report_button"),
                        shape = RoundedCornerShape(12.dp),
                        contentPadding = PaddingValues(horizontal = 8.dp),
                        border = BorderStroke(1.dp, c.line)
                    ) {
                        Icon(Icons.Rounded.ContentCopy, contentDescription = null, modifier = Modifier.size(16.dp), tint = c.ink)
                        Spacer(Modifier.width(4.dp))
                        Text("Copy", fontSize = 12.sp, color = c.ink, maxLines = 1)
                    }

                    Button(
                        onClick = onShareWhatsApp,
                        modifier = Modifier.weight(1f).height(46.dp).testTag("whatsapp_report_button"),
                        shape = RoundedCornerShape(12.dp),
                        contentPadding = PaddingValues(horizontal = 8.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = c.ok, contentColor = Color.White)
                    ) {
                        Icon(Icons.Rounded.Share, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("WhatsApp", fontSize = 12.sp, maxLines = 1)
                    }

                    OutlinedButton(
                        onClick = onSaveReportFile,
                        modifier = Modifier.weight(1f).height(46.dp).testTag("save_report_button"),
                        shape = RoundedCornerShape(12.dp),
                        contentPadding = PaddingValues(horizontal = 8.dp),
                        border = BorderStroke(1.dp, c.gold)
                    ) {
                        Icon(Icons.Rounded.SaveAlt, contentDescription = null, modifier = Modifier.size(16.dp), tint = c.goldInk)
                        Spacer(Modifier.width(4.dp))
                        Text("Save", fontSize = 12.sp, color = c.ink, maxLines = 1)
                    }
                }
            }
        }
    }
}

@Composable
private fun WeeklyKpiPill(label: String, value: String, color: Color, modifier: Modifier = Modifier) {
    val c = LocalDrstColors.current
    val shape = RoundedCornerShape(18.dp)
    val rimBrush = remember(c.glassBorderTop, c.glassBorderBottom) {
        Brush.linearGradient(listOf(c.glassBorderTop, c.glassBorderBottom))
    }
    Box(
        modifier = modifier
            .clip(shape)
            .background(c.glassFill)
            .background(Brush.verticalGradient(listOf(c.glassHighlight, Color.Transparent)))
            .border(1.dp, rimBrush, shape),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 10.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = label,
                maxLines = 1,
                style = TextStyle(fontFamily = InterFontFamily, fontWeight = FontWeight.SemiBold, fontSize = 9.sp, letterSpacing = 0.7.sp, color = c.muted)
            )
            Text(
                text = value,
                maxLines = 1,
                style = TextStyle(fontFamily = SpaceGroteskFontFamily, fontWeight = FontWeight.Bold, fontSize = 17.sp, color = color, fontFeatureSettings = "tnum")
            )
        }
    }
}

@Composable
private fun LegendDot(label: String, color: Color) {
    val c = LocalDrstColors.current
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
        Box(Modifier.size(8.dp).clip(CircleShape).background(color))
        Text(text = label, style = TextStyle(fontFamily = InterFontFamily, fontSize = 11.sp, color = c.muted))
    }
}
