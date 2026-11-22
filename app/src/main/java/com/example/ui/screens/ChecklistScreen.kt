package com.example.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.*
import com.example.ui.components.AnimatedTaskCheckbox
import com.example.ui.components.CompletionRing
import com.example.ui.components.DrstCard
import com.example.ui.theme.InterFontFamily
import com.example.ui.theme.LocalDrstColors
import com.example.ui.theme.SpaceGroteskFontFamily
import com.example.ui.theme.categoryColor
import kotlinx.coroutines.delay
import java.util.Locale

@Composable
fun ChecklistScreen(
    profile: UserProfile,
    selectedDate: String,
    filter: ChecklistFilter,
    onSelectFilter: (ChecklistFilter) -> Unit,
    onToggleTask: (String) -> Unit,
    onUpdateNote: (String, String) -> Unit,
    onAdjustStudyGoal: (Double) -> Unit,
    onToggleRestDay: () -> Unit,
    onResetDay: () -> Unit
) {
    val c = LocalDrstColors.current
    val dayLog = profile.days[selectedDate] ?: DayLog()
    val total = profile.routine.size
    val completed = Analytics.completedCount(profile, selectedDate)
    val pending = (total - completed).coerceAtLeast(0)
    val pct = Analytics.completionPercent(profile, selectedDate)
    val studyHrs = Analytics.studyHoursForDay(profile, selectedDate)
    val isToday = selectedDate == todayKey()

    var nowMinute by remember { mutableIntStateOf(currentMinutesOfDay()) }
    LaunchedEffect(isToday) {
        if (isToday) {
            while (true) {
                nowMinute = currentMinutesOfDay()
                delay(30_000L)
            }
        }
    }

    val currentNowTask = remember(profile.routine, isToday, nowMinute) {
        if (!isToday) null
        else profile.routine.firstOrNull { task ->
            nowMinute >= task.startMinutes && nowMinute < task.endMinutes
        }
    }

    val nextExam = remember(profile.exams) {
        Analytics.nextUpcomingExam(profile, todayKey())
    }

    val filteredTasks = remember(profile.routine, dayLog.completedIds, filter) {
        when (filter) {
            ChecklistFilter.ALL -> profile.routine
            ChecklistFilter.PENDING -> profile.routine.filter { it.id !in dayLog.completedIds }
            ChecklistFilter.DONE -> profile.routine.filter { it.id in dayLog.completedIds }
            ChecklistFilter.STUDY -> profile.routine.filter { it.isStudyTask }
        }
    }

    var expandedNoteIds by remember(selectedDate) { mutableStateOf(setOf<String>()) }
    var confirmReset by remember(selectedDate) { mutableStateOf(false) }

    LaunchedEffect(confirmReset) {
        if (confirmReset) {
            delay(3500L)
            confirmReset = false
        }
    }

    val goal = profile.dailyStudyGoalHours
    val goalRatio = if (goal > 0) (studyHrs / goal).toFloat().coerceIn(0f, 1f) else 0f
    val goalReached = studyHrs >= goal && goal > 0
    val animGoalRatio by animateFloatAsState(
        targetValue = goalRatio,
        animationSpec = tween(700, easing = FastOutSlowInEasing),
        label = "goal_bar"
    )

    LazyColumn(
        modifier = Modifier.fillMaxSize().testTag("checklist_list"),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 114.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // 1. Unified Minimal Hero Progress + Study Goal Liquid Glass Card
        item(key = "hero_card") {
            DrstCard {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    CompletionRing(
                        percent = pct,
                        size = 114.dp,
                        strokeWidth = 10.dp
                    )

                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Text(
                            text = Analytics.motivationalLine(pct, dayLog.isRestDay, studyHrs, goal),
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                            style = TextStyle(
                                fontFamily = SpaceGroteskFontFamily,
                                fontWeight = FontWeight.Bold,
                                fontSize = 15.sp,
                                lineHeight = 20.sp,
                                color = c.ink
                            )
                        )

                        // Minimal 3-stat frosted pill strip
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            MiniStatPill("Done", "$completed/$total", c.okInk, Modifier.weight(1f))
                            MiniStatPill("Left", "$pending", c.prismViolet, Modifier.weight(1f))
                            MiniStatPill(
                                "Study",
                                String.format(Locale.US, "%.1fh", studyHrs),
                                c.goldInk,
                                Modifier.weight(1f)
                            )
                        }
                    }
                }

                Spacer(Modifier.height(14.dp))

                // Integrated sleek Daily Study Goal bar with clean +/- controls
                val goalTrackShape = RoundedCornerShape(16.dp)
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(goalTrackShape)
                        .background(if (c.isDark) Color(0x17FFFFFF) else Color(0x99F3F7FF))
                        .border(1.dp, c.line, goalTrackShape)
                        .padding(horizontal = 12.dp, vertical = 10.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = if (goalReached) "STUDY GOAL REACHED!" else "DAILY STUDY GOAL",
                                style = TextStyle(
                                    fontFamily = InterFontFamily,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 10.sp,
                                    letterSpacing = 0.9.sp,
                                    color = if (goalReached) c.goldInk else c.muted
                                )
                            )
                            Text(
                                text = String.format(Locale.US, "%.1f / %.1f hrs", studyHrs, goal),
                                style = TextStyle(
                                    fontFamily = SpaceGroteskFontFamily,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 16.sp,
                                    color = c.ink,
                                    fontFeatureSettings = "tnum"
                                )
                            )
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            GlassStepperButton(
                                icon = Icons.Rounded.Remove,
                                contentDescription = "Decrease study goal 0.5h",
                                onClick = { onAdjustStudyGoal(-0.5) },
                                modifier = Modifier.testTag("study_goal_minus")
                            )
                            GlassStepperButton(
                                icon = Icons.Rounded.Add,
                                contentDescription = "Increase study goal 0.5h",
                                onClick = { onAdjustStudyGoal(0.5) },
                                modifier = Modifier.testTag("study_goal_plus")
                            )
                        }
                    }

                    Spacer(Modifier.height(8.dp))

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
                                .fillMaxWidth(animGoalRatio)
                                .clip(RoundedCornerShape(99.dp))
                                .background(
                                    Brush.horizontalGradient(
                                        if (goalReached) listOf(c.gold, c.prismCoral)
                                        else listOf(c.prismCyan, c.ok)
                                    )
                                )
                        )
                    }
                }
            }
        }

        // 2. Live NOW Spotlight Capsule (luminous prism glass)
        if (currentNowTask != null) {
            item(key = "now_card") {
                val isNowDone = currentNowTask.id in dayLog.completedIds
                val slotSpan = (currentNowTask.endMinutes - currentNowTask.startMinutes).coerceAtLeast(1)
                val slotFrac = ((nowMinute - currentNowTask.startMinutes).toFloat() / slotSpan).coerceIn(0f, 1f)
                val pulse = rememberInfiniteTransition(label = "now_dot")
                val dotAlpha by pulse.animateFloat(
                    initialValue = 0.35f,
                    targetValue = 1f,
                    animationSpec = infiniteRepeatable(tween(900), RepeatMode.Reverse),
                    label = "now_dot_alpha"
                )
                val nowShape = RoundedCornerShape(24.dp)

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(nowShape)
                        .background(
                            Brush.linearGradient(
                                listOf(
                                    c.band.copy(alpha = 0.92f),
                                    c.band2.copy(alpha = 0.86f)
                                )
                            )
                        )
                        .border(
                            1.25.dp,
                            Brush.linearGradient(listOf(c.prismCyan.copy(alpha = 0.85f), c.prismViolet.copy(alpha = 0.7f))),
                            nowShape
                        )
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Row(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(99.dp))
                                        .background(c.prismCyan.copy(alpha = 0.22f))
                                        .border(1.dp, c.prismCyan.copy(alpha = 0.65f), RoundedCornerShape(99.dp))
                                        .padding(horizontal = 8.dp, vertical = 3.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(5.dp)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(6.dp)
                                            .alpha(dotAlpha)
                                            .clip(CircleShape)
                                            .background(c.prismCyan)
                                    )
                                    Text(
                                        text = "LIVE NOW",
                                        style = TextStyle(
                                            fontFamily = SpaceGroteskFontFamily,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 10.sp,
                                            letterSpacing = 0.8.sp,
                                            color = c.onBand
                                        )
                                    )
                                }
                                Text(
                                    text = "${formatTime12h(currentNowTask.start)} – ${formatTime12h(currentNowTask.end)}",
                                    style = TextStyle(
                                        fontFamily = InterFontFamily,
                                        fontWeight = FontWeight.Medium,
                                        fontSize = 12.sp,
                                        color = c.onBandMuted
                                    )
                                )
                            }

                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(99.dp))
                                    .background(if (isNowDone) c.ok else c.gold)
                                    .clickable { onToggleTask(currentNowTask.id) }
                                    .padding(horizontal = 12.dp, vertical = 6.dp)
                            ) {
                                Text(
                                    text = if (isNowDone) "Done ✓" else "Mark Done",
                                    style = TextStyle(
                                        fontFamily = SpaceGroteskFontFamily,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 12.sp,
                                        color = Color(0xFF0A1024)
                                    )
                                )
                            }
                        }

                        Spacer(Modifier.height(8.dp))

                        Text(
                            text = currentNowTask.title,
                            style = TextStyle(
                                fontFamily = SpaceGroteskFontFamily,
                                fontWeight = FontWeight.Bold,
                                fontSize = 17.sp,
                                color = c.onBand
                            )
                        )
                        Spacer(Modifier.height(10.dp))
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(6.dp)
                                .clip(RoundedCornerShape(99.dp))
                                .background(Color(0x2EFFFFFF))
                        ) {
                            Box(
                                modifier = Modifier
                                    .fillMaxHeight()
                                    .fillMaxWidth(slotFrac)
                                    .clip(RoundedCornerShape(99.dp))
                                    .background(Brush.horizontalGradient(listOf(c.prismCyan, c.gold)))
                            )
                        }
                    }
                }
            }
        }

        // 3. Minimal Next-Exam Pill Strip
        if (nextExam != null) {
            item(key = "next_exam_strip") {
                val (exam, daysLeft) = nextExam
                val countdownLabel = when (daysLeft) {
                    0 -> "TODAY"
                    1 -> "1 day left"
                    else -> "$daysLeft days left"
                }
                val shape = RoundedCornerShape(18.dp)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(shape)
                        .background(c.glassFill)
                        .border(1.dp, Brush.horizontalGradient(listOf(c.gold.copy(alpha = 0.65f), c.prismCoral.copy(alpha = 0.45f))), shape)
                        .padding(horizontal = 14.dp, vertical = 11.dp)
                        .testTag("next_exam_strip"),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(
                            Icons.Rounded.Event,
                            contentDescription = null,
                            tint = c.gold,
                            modifier = Modifier.size(18.dp)
                        )
                        Text(
                            text = exam.name,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            style = TextStyle(
                                fontFamily = SpaceGroteskFontFamily,
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp,
                                color = c.ink
                            )
                        )
                    }
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(99.dp))
                            .background(c.gold.copy(alpha = 0.20f))
                            .padding(horizontal = 10.dp, vertical = 4.dp)
                    ) {
                        Text(
                            text = countdownLabel,
                            style = TextStyle(
                                fontFamily = SpaceGroteskFontFamily,
                                fontWeight = FontWeight.Bold,
                                fontSize = 11.sp,
                                color = c.goldInk
                            )
                        )
                    }
                }
            }
        }

        // 4. Minimal Segmented Glass Filter Pill Bar + Task Count
        item(key = "list_header") {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Today's Flow",
                        style = TextStyle(
                            fontFamily = SpaceGroteskFontFamily,
                            fontWeight = FontWeight.Bold,
                            fontSize = 18.sp,
                            color = c.ink
                        )
                    )
                    Text(
                        text = "${filteredTasks.size} of $total",
                        style = TextStyle(
                            fontFamily = SpaceGroteskFontFamily,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 12.sp,
                            color = c.muted
                        )
                    )
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    ChecklistFilter.entries.forEach { f ->
                        val selected = f == filter
                        val pillShape = RoundedCornerShape(99.dp)
                        Box(
                            modifier = Modifier
                                .clip(pillShape)
                                .background(
                                    if (selected) {
                                        Brush.horizontalGradient(listOf(c.prismCyan, c.prismViolet))
                                    } else {
                                        Brush.verticalGradient(listOf(c.glassHighlight, c.glassFill))
                                    }
                                )
                                .border(
                                    width = 1.dp,
                                    color = if (selected) Color(0x99FFFFFF) else c.glassBorderTop.copy(alpha = 0.55f),
                                    shape = pillShape
                                )
                                .clickable { onSelectFilter(f) }
                                .padding(horizontal = 15.dp, vertical = 9.dp)
                                .testTag("filter_${f.name.lowercase()}"),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = f.label,
                                style = TextStyle(
                                    fontFamily = InterFontFamily,
                                    fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                                    fontSize = 13.sp,
                                    color = if (selected) Color.White else c.ink
                                )
                            )
                        }
                    }
                }
            }
        }

        // 5. Minimal Liquid Glass Task Cards
        if (filteredTasks.isEmpty()) {
            item(key = "empty_filter") {
                DrstCard {
                    Column(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 10.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.TaskAlt,
                            contentDescription = null,
                            tint = c.prismCyan,
                            modifier = Modifier.size(30.dp)
                        )
                        Text(
                            text = "Nothing in \"${filter.label}\"",
                            style = TextStyle(
                                fontFamily = SpaceGroteskFontFamily,
                                fontWeight = FontWeight.Bold,
                                fontSize = 15.sp,
                                color = c.ink
                            )
                        )
                        Text(
                            text = "Switch filter or tap the pencil icon above to customize your routine.",
                            style = TextStyle(fontFamily = InterFontFamily, fontSize = 12.sp, color = c.muted)
                        )
                    }
                }
            }
        } else {
            items(filteredTasks, key = { it.id }, contentType = { "task_row" }) { task ->
                val isDone = task.id in dayLog.completedIds
                val isNow = currentNowTask?.id == task.id
                val note = dayLog.notes[task.id].orEmpty()
                val noteExpanded = task.id in expandedNoteIds

                TaskRowItem(
                    task = task,
                    isDone = isDone,
                    isNow = isNow,
                    note = note,
                    noteExpanded = noteExpanded,
                    onToggleDone = { onToggleTask(task.id) },
                    onToggleNote = {
                        expandedNoteIds = if (noteExpanded) expandedNoteIds - task.id else expandedNoteIds + task.id
                    },
                    onNoteChange = { onUpdateNote(task.id, it) }
                )
            }
        }

        // 6. Clean Footer Actions: Rest Day + Reset Day
        item(key = "footer_actions") {
            Spacer(Modifier.height(4.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedButton(
                    onClick = onToggleRestDay,
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp)
                        .testTag("rest_day_button"),
                    shape = RoundedCornerShape(16.dp),
                    border = BorderStroke(1.dp, if (dayLog.isRestDay) c.prismViolet else c.glassBorderTop.copy(alpha = 0.55f)),
                    colors = ButtonDefaults.outlinedButtonColors(
                        containerColor = if (dayLog.isRestDay) c.prismViolet.copy(alpha = 0.22f) else c.glassFill,
                        contentColor = c.ink
                    )
                ) {
                    Text(
                        text = if (dayLog.isRestDay) "Unmark rest day" else "Mark rest day",
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        style = TextStyle(fontFamily = InterFontFamily, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                    )
                }

                Button(
                    onClick = {
                        if (confirmReset) {
                            confirmReset = false
                            onResetDay()
                        } else {
                            confirmReset = true
                        }
                    },
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp)
                        .testTag("reset_day_button"),
                    shape = RoundedCornerShape(16.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (confirmReset) c.badBg else c.glassFill,
                        contentColor = if (confirmReset) c.badInk else c.muted
                    ),
                    border = BorderStroke(1.dp, if (confirmReset) c.badInk.copy(alpha = 0.55f) else c.line)
                ) {
                    Text(
                        text = if (confirmReset) "Tap to confirm" else "Reset day",
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        style = TextStyle(fontFamily = InterFontFamily, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                    )
                }
            }
        }
    }
}

@Composable
private fun GlassStepperButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val c = LocalDrstColors.current
    Box(
        modifier = modifier
            .size(40.dp)
            .clip(CircleShape)
            .background(if (c.isDark) Color(0x24FFFFFF) else Color(0xE6FFFFFF))
            .border(1.dp, c.glassBorderTop.copy(alpha = 0.65f), CircleShape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = c.ink,
            modifier = Modifier.size(18.dp)
        )
    }
}

@Composable
private fun MiniStatPill(
    label: String,
    value: String,
    valueColor: Color,
    modifier: Modifier = Modifier
) {
    val c = LocalDrstColors.current
    val shape = RoundedCornerShape(14.dp)
    Column(
        modifier = modifier
            .clip(shape)
            .background(if (c.isDark) Color(0x1CFFFFFF) else Color(0xB8F5F8FF))
            .border(1.dp, c.line, shape)
            .padding(horizontal = 8.dp, vertical = 7.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = value,
            maxLines = 1,
            style = TextStyle(
                fontFamily = SpaceGroteskFontFamily,
                fontWeight = FontWeight.Bold,
                fontSize = 15.sp,
                color = valueColor,
                fontFeatureSettings = "tnum"
            )
        )
        Text(
            text = label,
            maxLines = 1,
            style = TextStyle(
                fontFamily = InterFontFamily,
                fontWeight = FontWeight.Medium,
                fontSize = 10.sp,
                color = c.muted
            )
        )
    }
}

@Composable
private fun TaskRowItem(
    task: RoutineTask,
    isDone: Boolean,
    isNow: Boolean,
    note: String,
    noteExpanded: Boolean,
    onToggleDone: () -> Unit,
    onToggleNote: () -> Unit,
    onNoteChange: (String) -> Unit
) {
    val c = LocalDrstColors.current
    val catColor = categoryColor(task.category)
    val shape = RoundedCornerShape(22.dp)

    val cardBg by animateColorAsState(
        targetValue = when {
            isDone -> c.okBg
            c.isDark -> Color(0x6B121936)
            else -> Color(0xCCFFFFFF)
        },
        animationSpec = tween(250),
        label = "task_card_bg"
    )
    val borderColor by animateColorAsState(
        targetValue = when {
            isNow -> c.prismCyan
            isDone -> c.ok.copy(alpha = 0.55f)
            else -> c.glassBorderTop.copy(alpha = if (c.isDark) 0.42f else 0.9f)
        },
        animationSpec = tween(250),
        label = "task_card_border"
    )

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(cardBg)
            .border(
                width = if (isNow) 1.6.dp else 1.dp,
                color = borderColor,
                shape = shape
            )
            .testTag("task_row_${task.id}")
    ) {
        Column(modifier = Modifier.padding(start = 10.dp, end = 10.dp, top = 11.dp, bottom = 11.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Sleek vertical category color glow accent bar
                Box(
                    modifier = Modifier
                        .width(4.dp)
                        .height(42.dp)
                        .clip(RoundedCornerShape(99.dp))
                        .background(if (isDone) c.ok else catColor)
                )

                AnimatedTaskCheckbox(
                    checked = isDone,
                    borderColor = catColor,
                    onToggle = onToggleDone,
                    modifier = Modifier.testTag("task_cb_${task.id}")
                )

                Column(modifier = Modifier.weight(1f)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            text = "${formatTime12h(task.start)} – ${formatTime12h(task.end)}",
                            style = TextStyle(
                                fontFamily = InterFontFamily,
                                fontWeight = FontWeight.Medium,
                                fontSize = 11.sp,
                                color = if (isDone) c.okInk else c.muted,
                                fontFeatureSettings = "tnum"
                            )
                        )
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(99.dp))
                                .background((if (isDone) c.ok else catColor).copy(alpha = 0.16f))
                                .padding(horizontal = 6.dp, vertical = 1.dp)
                        ) {
                            Text(
                                text = formatDuration(task.durationMinutes),
                                style = TextStyle(
                                    fontFamily = SpaceGroteskFontFamily,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 10.sp,
                                    color = if (isDone) c.okInk else catColor
                                )
                            )
                        }
                        if (isNow) {
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(99.dp))
                                    .background(c.prismCyan)
                                    .padding(horizontal = 6.dp, vertical = 1.dp)
                            ) {
                                Text(
                                    text = "NOW",
                                    style = TextStyle(
                                        fontFamily = SpaceGroteskFontFamily,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 9.sp,
                                        color = Color(0xFF061322)
                                    )
                                )
                            }
                        }
                    }

                    Spacer(Modifier.height(3.dp))

                    Text(
                        text = task.title,
                        style = TextStyle(
                            fontFamily = SpaceGroteskFontFamily,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 15.sp,
                            color = c.ink.copy(alpha = if (isDone) 0.65f else 1f),
                            textDecoration = if (isDone) TextDecoration.LineThrough else TextDecoration.None
                        )
                    )

                    Spacer(Modifier.height(3.dp))

                    Text(
                        text = if (task.subject.isNotBlank()) "${task.category} • ${task.subject}" else task.category,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        style = TextStyle(
                            fontFamily = InterFontFamily,
                            fontSize = 11.sp,
                            color = c.muted
                        )
                    )
                }

                // Minimal Note button with gold dot indicator
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .clip(CircleShape)
                        .background(if (note.isNotBlank()) c.gold.copy(alpha = 0.16f) else Color.Transparent)
                        .clickable(onClick = onToggleNote),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Description,
                        contentDescription = "Task note",
                        tint = if (note.isNotBlank()) c.gold else c.muted,
                        modifier = Modifier.size(19.dp)
                    )
                    if (note.isNotBlank()) {
                        Box(
                            modifier = Modifier
                                .align(Alignment.TopEnd)
                                .padding(8.dp)
                                .size(7.dp)
                                .clip(CircleShape)
                                .background(c.gold)
                        )
                    }
                }
            }

            AnimatedVisibility(visible = noteExpanded) {
                OutlinedTextField(
                    value = note,
                    onValueChange = onNoteChange,
                    placeholder = { Text("Add a quick note for this task...", fontSize = 13.sp) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 10.dp),
                    shape = RoundedCornerShape(14.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = c.surface,
                        unfocusedContainerColor = c.surface
                    ),
                    minLines = 2
                )
            }
        }
    }
}
