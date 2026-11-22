package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.Icon
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
import com.example.ui.theme.categoryColor
import java.util.Locale
import kotlin.math.roundToInt

private data class BentoStat(
    val label: String,
    val value: String,
    val accent: Color,
    val icon: ImageVector
)

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
    val (level, levelTitle, levelProgress) = Analytics.levelInfo(totalXp)

    val studyTasks = remember(profile.routine) { profile.routine.filter { it.isStudyTask } }
    val categoryGroups = remember(profile.routine) {
        profile.routine.groupBy { it.category }.entries.sortedByDescending { it.value.size }
    }
    val weekSubjectHours = remember(profile, weekDays) {
        Analytics.subjectHoursInDateRange(profile, weekDays)
    }

    val bentoStats = listOf(
        BentoStat("Tasks Done", "$completed/$total", c.prismCyan, Icons.Rounded.TaskAlt),
        BentoStat("Study Today", String.format(Locale.US, "%.1fh", studyHrs), c.gold, Icons.Rounded.MenuBook),
        BentoStat("Goal Met", "$goalPct%", if (goalPct >= 100) c.ok else c.prismViolet, Icons.Rounded.Flag),
        BentoStat("Streak", "$streak days", c.prismCoral, Icons.Rounded.LocalFireDepartment),
        BentoStat("Week Avg", "$weekAvg%", if (weekAvg >= 75) c.ok else c.warn, Icons.Rounded.TrendingUp),
        BentoStat("Focus 7d", "${focusMin7d}m", c.prismViolet, Icons.Rounded.Bolt)
    )

    LazyColumn(
        modifier = Modifier.fillMaxSize().testTag("dashboard_list"),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 114.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // 1. Hero Bento Card: Completion Ring + Level XP + Momentum Badge
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
                        size = 126.dp,
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

                        Column {
                            Text(
                                text = "Level $level • $levelTitle",
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                style = TextStyle(
                                    fontFamily = SpaceGroteskFontFamily,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 17.sp,
                                    color = c.ink
                                )
                            )
                            Text(
                                text = "$totalXp XP earned",
                                style = TextStyle(
                                    fontFamily = InterFontFamily,
                                    fontSize = 12.sp,
                                    color = c.muted
                                )
                            )
                        }

                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(8.dp)
                                .clip(RoundedCornerShape(99.dp))
                                .background(c.track)
                        ) {
                            Box(
                                modifier = Modifier
                                    .fillMaxHeight()
                                    .fillMaxWidth(levelProgress.coerceIn(0.06f, 1f))
                                    .clip(RoundedCornerShape(99.dp))
                                    .background(
                                        Brush.horizontalGradient(listOf(c.prismCyan, c.prismViolet, c.gold))
                                    )
                            )
                        }
                    }
                }
            }
        }

        // 2. Playful Bento Glass Stats Grid (2x3 compact, 3x2 wide)
        item(key = "kpi_grid") {
            BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
                val cols = if (maxWidth >= 600.dp) 3 else 2
                val rows = bentoStats.chunked(cols)
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    rows.forEach { rowItems ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            rowItems.forEach { stat ->
                                BentoStatTile(stat = stat, modifier = Modifier.weight(1f))
                            }
                            repeat(cols - rowItems.size) {
                                Spacer(Modifier.weight(1f))
                            }
                        }
                    }
                }
            }
        }

        // 3. Vibrant Prism "Mind Lab" Study Focus Trainer Card (cleanly off top/bottom bars)
        item(key = "dashboard_mind_game_card") {
            val shape = RoundedCornerShape(26.dp)
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(shape)
                    .background(
                        Brush.linearGradient(
                            colors = listOf(
                                c.band.copy(alpha = 0.90f),
                                c.band2.copy(alpha = 0.84f)
                            )
                        )
                    )
                    .border(
                        1.2.dp,
                        Brush.linearGradient(listOf(c.prismCyan.copy(alpha = 0.85f), c.prismViolet.copy(alpha = 0.75f), c.gold.copy(alpha = 0.75f))),
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
                            .clip(RoundedCornerShape(16.dp))
                            .background(
                                Brush.linearGradient(listOf(c.prismCyan.copy(alpha = 0.30f), c.prismViolet.copy(alpha = 0.35f)))
                            )
                            .border(1.dp, Color(0x80FFFFFF), RoundedCornerShape(16.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Psychology,
                            contentDescription = "Mind Lab study focus game",
                            tint = c.prismCyan,
                            modifier = Modifier.size(26.dp)
                        )
                    }

                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "MIND LAB • FOCUS TRAINER",
                            style = TextStyle(
                                fontFamily = InterFontFamily,
                                fontWeight = FontWeight.Bold,
                                fontSize = 10.sp,
                                letterSpacing = 1.sp,
                                color = c.prismCyan
                            )
                        )
                        Text(
                            text = "Memory • Stroop • Math",
                            style = TextStyle(
                                fontFamily = SpaceGroteskFontFamily,
                                fontWeight = FontWeight.Bold,
                                fontSize = 17.sp,
                                color = c.onBand
                            )
                        )
                        Text(
                            text = "Best ${profile.bestGameScore} • Lvl ${profile.bestGameWave.coerceAtLeast(1)} • Earn Study XP",
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
                            .background(Brush.horizontalGradient(listOf(c.prismCyan, c.ok)))
                            .padding(horizontal = 15.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.PlayArrow,
                            contentDescription = null,
                            tint = Color(0xFF061322),
                            modifier = Modifier.size(16.dp)
                        )
                        Text(
                            text = "Train",
                            style = TextStyle(
                                fontFamily = SpaceGroteskFontFamily,
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp,
                                color = Color(0xFF061322)
                            )
                        )
                    }
                }
            }
        }

        // 4. This-Week Daily Columns (crystal prism pillars)
        item(key = "week_columns_card") {
            val dayLabels = listOf("Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun")
            DrstCard(title = "This Week Momentum") {
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

        // 5. Study Sessions Bars
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
                                fraction = if (done) 1f else 0.14f,
                                valueText = if (done) formatDuration(task.durationMinutes) else "Pending",
                                barColor = if (done) c.ok else c.prismViolet.copy(alpha = 0.45f)
                            )
                        }
                    }
                }
            }
        }

        // 6. By-Category Bars
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

        // 7. Hours by Subject This Week
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
                                barColor = c.prismCyan
                            )
                        }
                    }
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
