package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.SportsEsports
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
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
import com.example.ui.theme.categoryColor
import java.util.Locale
import kotlin.math.roundToInt

@Composable
fun DashboardScreen(
    profile: UserProfile,
    selectedDate: String,
    onOpenGame: () -> Unit = {}
) {
    val c = LocalDrstColors.current
    val dayLog = profile.days[selectedDate] ?: DayLog()
    val total = profile.routine.size
    val completed = Analytics.completedCount(profile, selectedDate)
    val pct = Analytics.completionPercent(profile, selectedDate)
    val studyHrs = Analytics.studyHoursForDay(profile, selectedDate)
    val goalPct = if (profile.dailyStudyGoalHours > 0) {
        ((studyHrs / profile.dailyStudyGoalHours) * 100).roundToInt().coerceAtMost(999)
    } else 0
    val streak = Analytics.calculateStreak(profile, todayKey())
    val weekStart = startOfWeekMonday(selectedDate)
    val weekDays = remember(weekStart) { (0..6).map { shiftDateKey(weekStart, it) } }
    val weekAvg = weekDays.map { Analytics.completionPercent(profile, it) }.average().roundToInt()
    val focusMin7d = profile.focusSessions.filter { it.date in weekDays }.sumOf { it.minutes }
    val totalXp = Analytics.totalXp(profile)
    val (level, levelTitle, _) = Analytics.levelInfo(totalXp)

    val studyTasks = remember(profile.routine) { profile.routine.filter { it.isStudyTask } }
    val categoryGroups = remember(profile.routine) {
        profile.routine.groupBy { it.category }.entries.sortedByDescending { it.value.size }
    }
    val weekSubjectHours = remember(profile, weekDays) {
        Analytics.subjectHoursInDateRange(profile, weekDays)
    }

    val kpis = listOf(
        Triple("Completion", "$pct%", if (pct >= 75) c.okInk else c.warnInk),
        Triple("Tasks Done", "$completed / $total", c.ink),
        Triple("Study Today", String.format(Locale.US, "%.1f h", studyHrs), c.goldInk),
        Triple("Goal Met", "$goalPct%", if (goalPct >= 100) c.goldInk else c.ink),
        Triple("Streak (75%+)", "$streak days", c.okInk),
        Triple("Week Avg", "$weekAvg%", if (weekAvg >= 75) c.okInk else c.warnInk),
        Triple("Focus (Week)", "${focusMin7d}m", c.ink),
        Triple("Level $level", "$totalXp XP", c.goldInk)
    )

    LazyColumn(
        modifier = Modifier.fillMaxSize().testTag("dashboard_list"),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 112.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // 1. 8 KPI Tiles (2x4 on compact, 4x2 on wide)
        item(key = "kpi_grid") {
            BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
                val cols = if (maxWidth >= 600.dp) 4 else 2
                val rows = kpis.chunked(cols)
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    rows.forEach { rowItems ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            rowItems.forEach { (label, value, color) ->
                                KpiTileCard(
                                    label = label,
                                    value = value,
                                    valueColor = color,
                                    subtitle = if (label.startsWith("Level")) levelTitle else null,
                                    modifier = Modifier.weight(1f)
                                )
                            }
                            repeat(cols - rowItems.size) {
                                Spacer(Modifier.weight(1f))
                            }
                        }
                    }
                }
            }
        }

        // 2. Study Break Arcade • Orbit Pulse (placed cleanly in Dashboard & Focus, away from top/bottom bars)
        item(key = "dashboard_orbit_pulse_card") {
            val shape = RoundedCornerShape(24.dp)
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(shape)
                    .background(
                        Brush.linearGradient(
                            colors = listOf(c.band.copy(alpha = 0.90f), c.band2.copy(alpha = 0.82f))
                        )
                    )
                    .border(
                        1.1.dp,
                        Brush.linearGradient(listOf(Color(0x88FFFFFF), c.gold.copy(alpha = 0.72f))),
                        shape
                    )
                    .clickable(onClick = onOpenGame)
                    .padding(16.dp)
                    .testTag("dashboard_play_game_button")
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(48.dp)
                            .clip(CircleShape)
                            .background(c.gold.copy(alpha = 0.22f))
                            .border(1.dp, c.gold.copy(alpha = 0.75f), CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.SportsEsports,
                            contentDescription = "Orbit Pulse study break game",
                            tint = c.gold,
                            modifier = Modifier.size(24.dp)
                        )
                    }

                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "STUDY BREAK ARCADE",
                            style = TextStyle(
                                fontFamily = InterFontFamily,
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 10.sp,
                                letterSpacing = 0.9.sp,
                                color = c.gold
                            )
                        )
                        Text(
                            text = "Orbit Pulse",
                            style = TextStyle(
                                fontFamily = SpaceGroteskFontFamily,
                                fontWeight = FontWeight.Bold,
                                fontSize = 17.sp,
                                color = c.onBand
                            )
                        )
                        Text(
                            text = "Best: ${profile.bestGameScore} • Wave ${profile.bestGameWave.coerceAtLeast(1)} • Earn XP",
                            style = TextStyle(
                                fontFamily = InterFontFamily,
                                fontSize = 11.sp,
                                color = c.onBandMuted
                            )
                        )
                    }

                    Row(
                        modifier = Modifier
                            .clip(RoundedCornerShape(99.dp))
                            .background(c.gold)
                            .padding(horizontal = 14.dp, vertical = 9.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.PlayArrow,
                            contentDescription = null,
                            tint = c.band,
                            modifier = Modifier.size(16.dp)
                        )
                        Text(
                            text = "Play",
                            style = TextStyle(
                                fontFamily = SpaceGroteskFontFamily,
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp,
                                color = c.band
                            )
                        )
                    }
                }
            }
        }

        // 2. Daily Completion Ring Card
        item(key = "daily_ring_card") {
            val statusText = when {
                pct >= 80 -> "Strong Day"
                pct >= 50 -> "On Track"
                else -> "Needs Momentum"
            }
            val statusBg = when {
                pct >= 80 -> c.okBg
                pct >= 50 -> c.warnBg
                else -> c.badBg
            }
            val statusInk = when {
                pct >= 80 -> c.okInk
                pct >= 50 -> c.warnInk
                else -> c.badInk
            }

            DrstCard(title = "Daily Completion") {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    CompletionRing(
                        percent = pct,
                        size = 176.dp,
                        strokeWidth = 13.dp,
                        label = "COMPLETED"
                    )
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(99.dp))
                            .background(statusBg)
                            .padding(horizontal = 14.dp, vertical = 5.dp)
                    ) {
                        Text(
                            text = statusText,
                            style = TextStyle(
                                fontFamily = SpaceGroteskFontFamily,
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp,
                                color = statusInk
                            )
                        )
                    }
                }
            }
        }

        // 3. Study Sessions Bars
        item(key = "study_sessions_card") {
            DrstCard(title = "Study Sessions (${formatCompactDate(selectedDate)})") {
                if (studyTasks.isEmpty()) {
                    Text(
                        text = "No study blocks in your routine yet.",
                        style = TextStyle(fontFamily = InterFontFamily, fontSize = 13.sp, color = c.muted)
                    )
                } else {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        studyTasks.forEach { task ->
                            val done = task.id in dayLog.completedIds
                            HorizontalBarRow(
                                label = task.subject.ifBlank { task.title },
                                fraction = if (done) 1f else 0.12f,
                                valueText = if (done) formatDuration(task.durationMinutes) else "Pending",
                                barColor = if (done) c.ok else c.gold.copy(alpha = 0.4f)
                            )
                        }
                    }
                }
            }
        }

        // 4. By-Category Bars
        item(key = "by_category_card") {
            DrstCard(title = "By Category") {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    categoryGroups.forEach { (cat, tasks) ->
                        val catDone = tasks.count { it.id in dayLog.completedIds }
                        val frac = if (tasks.isEmpty()) 0f else catDone.toFloat() / tasks.size
                        HorizontalBarRow(
                            label = cat,
                            fraction = frac,
                            valueText = "$catDone/${tasks.size}",
                            barColor = categoryColor(cat),
                            dotColor = categoryColor(cat)
                        )
                    }
                }
            }
        }

        // 5. This-Week Daily Columns
        item(key = "week_columns_card") {
            val dayLabels = listOf("Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun")
            DrstCard(title = "This Week Daily Completion") {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(140.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.Bottom
                ) {
                    weekDays.forEachIndexed { idx, dKey ->
                        val dPct = Analytics.completionPercent(profile, dKey)
                        val isRest = profile.days[dKey]?.isRestDay == true
                        val isSelected = dKey == selectedDate
                        val barColor = when {
                            isRest -> c.gold
                            dPct >= 75 -> c.ok
                            dPct > 0 -> c.warn
                            else -> c.track
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
                                    fontWeight = FontWeight.SemiBold,
                                    fontSize = 10.sp,
                                    color = if (isSelected) c.ink else c.muted
                                )
                            )
                            Spacer(Modifier.height(4.dp))
                            Box(
                                modifier = Modifier
                                    .widthIn(max = 32.dp)
                                    .fillMaxWidth()
                                    .weight(1f),
                                contentAlignment = Alignment.BottomCenter
                            ) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .fillMaxHeight((dPct / 100f).coerceAtLeast(0.06f))
                                        .clip(RoundedCornerShape(topStart = 8.dp, topEnd = 8.dp, bottomStart = 4.dp, bottomEnd = 4.dp))
                                        .background(barColor)
                                )
                            }
                            Spacer(Modifier.height(6.dp))
                            Text(
                                text = dayLabels[idx],
                                style = TextStyle(
                                    fontFamily = InterFontFamily,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                    fontSize = 11.sp,
                                    color = if (isSelected) c.goldInk else c.muted
                                )
                            )
                        }
                    }
                }
            }
        }

        // 6. Hours by Subject This Week
        item(key = "subject_hours_week_card") {
            DrstCard(title = "Hours by Subject This Week") {
                if (weekSubjectHours.isEmpty()) {
                    Text(
                        text = "Tick study blocks or complete Focus sessions to see your subject breakdown here.",
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
                                barColor = c.gold
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun KpiTileCard(
    label: String,
    value: String,
    valueColor: Color,
    subtitle: String? = null,
    modifier: Modifier = Modifier
) {
    val c = LocalDrstColors.current
    val shape = RoundedCornerShape(20.dp)
    val rimBrush = remember(c.glassBorderTop, c.glassBorderBottom) {
        Brush.linearGradient(listOf(c.glassBorderTop, c.glassBorderBottom))
    }
    Box(
        modifier = modifier
            .clip(shape)
            .background(c.glassFill)
            .background(Brush.verticalGradient(listOf(c.glassHighlight, Color.Transparent)))
            .border(1.1.dp, rimBrush, shape)
    ) {
        Column(modifier = Modifier.padding(13.dp)) {
            Text(
                text = label.uppercase(),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = TextStyle(
                    fontFamily = InterFontFamily,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 10.sp,
                    letterSpacing = 0.8.sp,
                    color = c.muted
                )
            )
            Spacer(Modifier.height(2.dp))
            Text(
                text = value,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = TextStyle(
                    fontFamily = SpaceGroteskFontFamily,
                    fontWeight = FontWeight.Bold,
                    fontSize = 22.sp,
                    color = valueColor,
                    fontFeatureSettings = "tnum"
                )
            )
            if (subtitle != null) {
                Text(
                    text = subtitle,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    style = TextStyle(fontFamily = InterFontFamily, fontSize = 11.sp, color = c.muted)
                )
            }
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
                .height(10.dp)
                .clip(RoundedCornerShape(99.dp))
                .background(c.track)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .fillMaxWidth(fraction.coerceIn(0f, 1f))
                    .clip(RoundedCornerShape(99.dp))
                    .background(barColor)
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
