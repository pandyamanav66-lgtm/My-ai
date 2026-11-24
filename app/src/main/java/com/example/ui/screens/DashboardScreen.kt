package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.*
import com.example.ui.components.CompletionRing
import com.example.ui.components.DrstCard
import com.example.ui.theme.InterFontFamily
import com.example.ui.theme.LocalDrstColors
import com.example.ui.theme.SpaceGroteskFontFamily
import java.util.Locale
import kotlin.math.roundToInt

private data class BentoStat(
    val label: String,
    val value: String,
    val accent: Color,
    val icon: ImageVector
)

/**
 * Clean, minimal Progress tab combining Today's overview + Weekly momentum + Subject hours
 * in the exact Prism Liquid Glass design system (without redundant charts or clutter).
 */
@Composable
fun DashboardScreen(
    profile: UserProfile,
    selectedDate: String,
    onShareWeeklyReport: () -> Unit = {}
) {
    val c = LocalDrstColors.current
    val total = profile.routine.size
    val completed = Analytics.completedCount(profile, selectedDate)
    val pct = Analytics.completionPercent(profile, selectedDate)
    val studyHrs = Analytics.studyHoursForDay(profile, selectedDate)
    val streak = Analytics.calculateStreak(profile, todayKey())
    val weekStart = startOfWeekMonday(selectedDate)
    val weekDays = remember(weekStart) { (0..6).map { shiftDateKey(weekStart, it) } }
    val weekAvg = weekDays.map { Analytics.completionPercent(profile, it) }.average().roundToInt()
    val weekStudyHrs = weekDays.sumOf { Analytics.studyHoursForDay(profile, it) }
    val weekSubjectHours = remember(profile, weekDays) {
        Analytics.subjectHoursInDateRange(profile, weekDays)
    }

    val bentoStats = listOf(
        BentoStat("Tasks Done", "$completed/$total", c.prismCyan, Icons.Rounded.TaskAlt),
        BentoStat("Study Today", String.format(Locale.US, "%.1fh", studyHrs), c.gold, Icons.Rounded.MenuBook),
        BentoStat("Streak", "$streak days", c.prismCoral, Icons.Rounded.LocalFireDepartment),
        BentoStat("Week Avg", "$weekAvg%", if (weekAvg >= 75) c.ok else c.prismViolet, Icons.Rounded.TrendingUp)
    )

    LazyColumn(
        modifier = Modifier.fillMaxSize().testTag("dashboard_list"),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 114.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // 1. Minimal Hero Progress Ring + Weekly Study Summary
        item(key = "hero_bento") {
            val statusText = when {
                pct >= 80 -> "Strong Momentum"
                pct >= 50 -> "On Track"
                else -> "Let's Roll!"
            }
            val statusColor = when {
                pct >= 80 -> c.ok
                pct >= 50 -> c.gold
                else -> c.prismCyan
            }

            DrstCard {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    CompletionRing(
                        percent = pct,
                        size = 122.dp,
                        strokeWidth = 11.dp,
                        label = "TODAY"
                    )

                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(99.dp))
                                .background(statusColor.copy(alpha = 0.18f))
                                .border(1.dp, statusColor.copy(alpha = 0.5f), RoundedCornerShape(99.dp))
                                .padding(horizontal = 10.dp, vertical = 4.dp)
                        ) {
                            Text(
                                text = statusText,
                                style = TextStyle(
                                    fontFamily = SpaceGroteskFontFamily,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 11.sp,
                                    color = if (c.isDark) statusColor else c.ink
                                )
                            )
                        }

                        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Text(
                                text = String.format(Locale.US, "%.1f hrs this week", weekStudyHrs),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                style = TextStyle(
                                    fontFamily = SpaceGroteskFontFamily,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 18.sp,
                                    color = c.ink
                                )
                            )
                            Text(
                                text = "Daily goal: ${String.format(Locale.US, "%.1f", profile.dailyStudyGoalHours)}h",
                                style = TextStyle(
                                    fontFamily = InterFontFamily,
                                    fontSize = 12.sp,
                                    color = c.muted
                                )
                            )
                        }
                    }
                }
            }
        }

        // 2. Crisp 2x2 Bento Glass Stats Grid
        item(key = "kpi_grid") {
            val rows = bentoStats.chunked(2)
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                rows.forEach { rowItems ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        rowItems.forEach { stat ->
                            BentoStatTile(stat = stat, modifier = Modifier.weight(1f))
                        }
                    }
                }
            }
        }

        // 3. This-Week Daily Columns (crystal prism pillars)
        item(key = "week_columns_card") {
            val dayLabels = listOf("Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun")
            DrstCard(title = "This Week") {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(144.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.Bottom
                ) {
                    weekDays.forEachIndexed { idx, dKey ->
                        val dPct = Analytics.completionPercent(profile, dKey)
                        val isRest = profile.days[dKey]?.isRestDay == true
                        val isSelected = dKey == selectedDate
                        val barBrush = when {
                            isRest -> Brush.verticalGradient(listOf(c.prismViolet, c.prismViolet.copy(alpha = 0.55f)))
                            dPct >= 75 -> Brush.verticalGradient(listOf(c.prismCyan, c.ok))
                            dPct > 0 -> Brush.verticalGradient(listOf(c.gold, c.prismCoral))
                            else -> Brush.verticalGradient(listOf(c.track, c.track))
                        }
                        Column(
                            modifier = Modifier.weight(1f).fillMaxHeight(),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Bottom
                        ) {
                            Text(
                                text = "$dPct%",
                                style = TextStyle(
                                    fontFamily = SpaceGroteskFontFamily,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 10.sp,
                                    color = if (isSelected) c.ink else c.muted
                                )
                            )
                            Spacer(Modifier.height(5.dp))
                            Box(
                                modifier = Modifier
                                    .widthIn(max = 28.dp)
                                    .fillMaxWidth()
                                    .weight(1f)
                                    .clip(RoundedCornerShape(99.dp))
                                    .background(c.track.copy(alpha = 0.45f)),
                                contentAlignment = Alignment.BottomCenter
                            ) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .fillMaxHeight((dPct / 100f).coerceAtLeast(0.08f))
                                        .clip(RoundedCornerShape(99.dp))
                                        .background(barBrush)
                                )
                            }
                            Spacer(Modifier.height(6.dp))
                            Text(
                                text = dayLabels[idx],
                                style = TextStyle(
                                    fontFamily = InterFontFamily,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                    fontSize = 11.sp,
                                    color = if (isSelected) c.prismCyan else c.muted
                                )
                            )
                        }
                    }
                }
            }
        }

        // 3.5. 30-Day Crystal Streak Heatmap (Liquid Glass Consistency Grid)
        item(key = "streak_heatmap_card") {
            val today = todayKey()
            val last30Keys = remember(today) { (29 downTo 0).map { shiftDateKey(today, -it) } }
            val activeDays = remember(profile, last30Keys) {
                last30Keys.count { dKey ->
                    Analytics.completionPercent(profile, dKey) >= 50 || profile.days[dKey]?.isRestDay == true
                }
            }
            DrstCard(title = "30-Day Streak Heatmap") {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "$activeDays / 30 active days",
                        style = TextStyle(
                            fontFamily = SpaceGroteskFontFamily,
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp,
                            color = c.ink
                        )
                    )
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text("Less", fontFamily = InterFontFamily, fontSize = 10.sp, color = c.muted)
                        listOf(
                            c.track.copy(alpha = 0.5f),
                            c.gold.copy(alpha = 0.55f),
                            c.prismCyan.copy(alpha = 0.65f),
                            c.ok
                        ).forEach { dotColor ->
                            Box(
                                modifier = Modifier
                                    .size(9.dp)
                                    .clip(RoundedCornerShape(3.dp))
                                    .background(dotColor)
                            )
                        }
                        Text("More", fontFamily = InterFontFamily, fontSize = 10.sp, color = c.muted)
                    }
                }

                Spacer(Modifier.height(10.dp))

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("streak_heatmap_grid"),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    last30Keys.chunked(6).forEach { rowKeys ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            rowKeys.forEach { dKey ->
                                val dPct = Analytics.completionPercent(profile, dKey)
                                val isRest = profile.days[dKey]?.isRestDay == true
                                val isTodayCell = dKey == today
                                val cellShape = RoundedCornerShape(9.dp)
                                val cellColor = when {
                                    isRest -> c.prismViolet.copy(alpha = 0.72f)
                                    dPct >= 75 -> c.ok.copy(alpha = 0.88f)
                                    dPct >= 45 -> c.prismCyan.copy(alpha = 0.65f)
                                    dPct > 0 -> c.gold.copy(alpha = 0.55f)
                                    else -> c.track.copy(alpha = 0.45f)
                                }
                                val dayNum = dKey.takeLast(2).trimStart('0').ifEmpty { "0" }
                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(30.dp)
                                        .clip(cellShape)
                                        .background(cellColor)
                                        .border(
                                            width = if (isTodayCell) 1.5.dp else 0.8.dp,
                                            color = if (isTodayCell) c.ink else c.glassBorderTop.copy(alpha = 0.35f),
                                            shape = cellShape
                                        ),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = dayNum,
                                        style = TextStyle(
                                            fontFamily = SpaceGroteskFontFamily,
                                            fontWeight = if (isTodayCell || dPct >= 75) FontWeight.Bold else FontWeight.Medium,
                                            fontSize = 10.sp,
                                            color = if (dPct >= 45 || isRest) Color(0xFF061322) else c.muted,
                                            fontFeatureSettings = "tnum"
                                        )
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        // 4. Hours by Subject This Week + 1-tap Share Weekly Summary
        item(key = "subject_hours_week_card") {
            DrstCard(title = "Study Hours by Subject") {
                if (weekSubjectHours.isEmpty()) {
                    Text(
                        text = "Complete study tasks or Focus sessions to see your subject hours here.",
                        style = TextStyle(fontFamily = InterFontFamily, fontSize = 13.sp, color = c.muted)
                    )
                } else {
                    val maxHrs = weekSubjectHours.values.maxOrNull()?.coerceAtLeast(0.5) ?: 1.0
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        weekSubjectHours.forEach { (subject, hours) ->
                            HorizontalBarRow(
                                label = subject,
                                fraction = (hours / maxHrs).toFloat().coerceIn(0f, 1f),
                                valueText = String.format(Locale.US, "%.1f h", hours),
                                barColor = c.prismCyan
                            )
                        }
                    }
                }

                Spacer(Modifier.height(14.dp))

                OutlinedButton(
                    onClick = onShareWeeklyReport,
                    modifier = Modifier.fillMaxWidth().height(44.dp).testTag("share_weekly_report_button"),
                    shape = RoundedCornerShape(14.dp),
                    border = BorderStroke(1.dp, c.glassBorderTop),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = c.ink)
                ) {
                    Icon(Icons.Rounded.Share, contentDescription = null, modifier = Modifier.size(16.dp), tint = c.prismCyan)
                    Spacer(Modifier.width(8.dp))
                    Text("Share Weekly Summary", fontFamily = SpaceGroteskFontFamily, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                }
            }
        }
    }
}

@Composable
private fun BentoStatTile(
    stat: BentoStat,
    modifier: Modifier = Modifier
) {
    val c = LocalDrstColors.current
    val shape = RoundedCornerShape(22.dp)
    val rimBrush = remember(c.glassBorderTop, c.glassBorderBottom) {
        Brush.linearGradient(listOf(c.glassBorderTop, c.glassBorderBottom))
    }
    Row(
        modifier = modifier
            .clip(shape)
            .background(if (c.isDark) Color(0x660D132B) else Color(0xCCFFFFFF))
            .background(Brush.verticalGradient(listOf(c.glassHighlight, c.glassFill)))
            .border(1.dp, rimBrush, shape)
            .padding(horizontal = 13.dp, vertical = 13.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(11.dp)
    ) {
        Box(
            modifier = Modifier
                .size(38.dp)
                .clip(CircleShape)
                .background(stat.accent.copy(alpha = 0.18f))
                .border(1.dp, stat.accent.copy(alpha = 0.45f), CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = stat.icon,
                contentDescription = null,
                tint = stat.accent,
                modifier = Modifier.size(19.dp)
            )
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = stat.value,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = TextStyle(
                    fontFamily = SpaceGroteskFontFamily,
                    fontWeight = FontWeight.Bold,
                    fontSize = 19.sp,
                    color = c.ink,
                    fontFeatureSettings = "tnum"
                )
            )
            Text(
                text = stat.label,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = TextStyle(
                    fontFamily = InterFontFamily,
                    fontWeight = FontWeight.Medium,
                    fontSize = 11.sp,
                    color = c.muted
                )
            )
        }
    }
}

@Composable
fun HorizontalBarRow(
    label: String,
    fraction: Float,
    valueText: String,
    barColor: Color,
    dotColor: Color? = null
) {
    val c = LocalDrstColors.current
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Row(
            modifier = Modifier.width(92.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            if (dotColor != null) {
                Box(
                    modifier = Modifier
                        .size(7.dp)
                        .clip(CircleShape)
                        .background(dotColor)
                )
            }
            Text(
                text = label,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = TextStyle(
                    fontFamily = InterFontFamily,
                    fontWeight = FontWeight.Medium,
                    fontSize = 13.sp,
                    color = c.ink
                )
            )
        }

        Box(
            modifier = Modifier
                .weight(1f)
                .height(9.dp)
                .clip(RoundedCornerShape(99.dp))
                .background(c.track)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .fillMaxWidth(fraction.coerceIn(0f, 1f))
                    .clip(RoundedCornerShape(99.dp))
                    .background(
                        Brush.horizontalGradient(listOf(barColor, barColor.copy(alpha = 0.72f)))
                    )
            )
        }

        Text(
            text = valueText,
            modifier = Modifier.widthIn(min = 46.dp),
            style = TextStyle(
                fontFamily = SpaceGroteskFontFamily,
                fontWeight = FontWeight.SemiBold,
                fontSize = 12.sp,
                color = c.muted,
                fontFeatureSettings = "tnum"
            )
        )
    }
}
