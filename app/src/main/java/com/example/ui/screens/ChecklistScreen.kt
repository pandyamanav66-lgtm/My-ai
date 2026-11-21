package com.example.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
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

    // Live clock minute tick for NOW card
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

    val progressAnim by animateFloatAsState(
        targetValue = pct / 100f,
        animationSpec = tween(750, easing = FastOutSlowInEasing),
        label = "hero_bar"
    )

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 112.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // 1. Hero Card: Completion Ring + 4 Mini KPIs + Bar + Motivational Line
        item(key = "hero_card") {
            DrstCard {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    CompletionRing(
                        percent = pct,
                        size = 122.dp,
                        strokeWidth = 10.dp
                    )

                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            MiniKpiBox("TOTAL", "$total", c.ink, Modifier.weight(1f))
                            MiniKpiBox("DONE", "$completed", c.okInk, Modifier.weight(1f))
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            MiniKpiBox("PENDING", "$pending", c.warnInk, Modifier.weight(1f))
                            MiniKpiBox(
                                "STUDY HRS",
                                String.format(Locale.US, "%.1fh", studyHrs),
                                c.goldInk,
                                Modifier.weight(1f)
                            )
                        }
                    }
                }

                Spacer(Modifier.height(14.dp))

                // Horizontal progress bar
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(11.dp)
                        .clip(RoundedCornerShape(99.dp))
                        .background(c.track)
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxHeight()
                            .fillMaxWidth(progressAnim.coerceIn(0f, 1f))
                            .clip(RoundedCornerShape(99.dp))
                            .background(
                                Brush.horizontalGradient(
                                    listOf(c.ok, c.ok.copy(alpha = 0.72f))
                                )
                            )
                    )
                }

                Spacer(Modifier.height(10.dp))

                Text(
                    text = Analytics.motivationalLine(pct, dayLog.isRestDay, studyHrs, profile.dailyStudyGoalHours),
                    style = TextStyle(
                        fontFamily = InterFontFamily,
                        fontWeight = FontWeight.Medium,
                        fontSize = 13.sp,
                        color = c.muted
                    )
                )
            }
        }

        // 2. Study Goal Card (+/- 0.5h steps, fills gold when reached)
        item(key = "study_goal_card") {
            val goal = profile.dailyStudyGoalHours
            val goalRatio = if (goal > 0) (studyHrs / goal).toFloat().coerceIn(0f, 1f) else 0f
            val goalReached = studyHrs >= goal && goal > 0
            val animGoalRatio by animateFloatAsState(
                targetValue = goalRatio,
                animationSpec = tween(750, easing = FastOutSlowInEasing),
                label = "goal_bar"
            )
            val barColor by animateColorAsState(
                targetValue = if (goalReached) c.gold else c.ok,
                animationSpec = tween(400),
                label = "goal_bar_color"
            )

            DrstCard {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "DAILY STUDY GOAL",
                            style = TextStyle(
                                fontFamily = InterFontFamily,
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 11.sp,
                                letterSpacing = 0.9.sp,
                                color = c.muted
                            )
                        )
                        Spacer(Modifier.height(2.dp))
                        Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text(
                                text = String.format(Locale.US, "%.1f / %.1f h", studyHrs, goal),
                                style = TextStyle(
                                    fontFamily = SpaceGroteskFontFamily,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 20.sp,
                                    color = if (goalReached) c.goldInk else c.ink,
                                    fontFeatureSettings = "tnum"
                                )
                            )
                            if (goalReached) {
                                Text(
                                    text = "GOAL REACHED!",
                                    style = TextStyle(
                                        fontFamily = InterFontFamily,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 11.sp,
                                        color = c.goldInk
                                    ),
                                    modifier = Modifier.padding(bottom = 3.dp)
                                )
                            }
                        }
                    }

                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        FilledTonalIconButton(
                            onClick = { onAdjustStudyGoal(-0.5) },
                            modifier = Modifier.size(40.dp).testTag("study_goal_minus"),
                            colors = IconButtonDefaults.filledTonalIconButtonColors(
                                containerColor = c.surface2,
                                contentColor = c.ink
                            )
                        ) {
                            Icon(Icons.Rounded.Remove, contentDescription = "Decrease study goal 0.5h")
                        }
                        FilledTonalIconButton(
                            onClick = { onAdjustStudyGoal(0.5) },
                            modifier = Modifier.size(40.dp).testTag("study_goal_plus"),
                            colors = IconButtonDefaults.filledTonalIconButtonColors(
                                containerColor = c.surface2,
                                contentColor = c.ink
                            )
                        ) {
                            Icon(Icons.Rounded.Add, contentDescription = "Increase study goal 0.5h")
                        }
                    }
                }

                Spacer(Modifier.height(10.dp))

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(10.dp)
                        .clip(RoundedCornerShape(99.dp))
                        .background(c.track)
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxHeight()
                            .fillMaxWidth(animGoalRatio)
                            .clip(RoundedCornerShape(99.dp))
                            .background(barColor)
                    )
                }
            }
        }

        // 3. NOW Card (current task with live slot progress)
        if (currentNowTask != null) {
            item(key = "now_card") {
                val isNowDone = currentNowTask.id in dayLog.completedIds
                val slotSpan = (currentNowTask.endMinutes - currentNowTask.startMinutes).coerceAtLeast(1)
                val slotFrac = ((nowMinute - currentNowTask.startMinutes).toFloat() / slotSpan).coerceIn(0f, 1f)
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(18.dp),
                    color = c.band,
                    border = BorderStroke(1.5.dp, c.gold),
                    shadowElevation = 4.dp
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(99.dp))
                                        .background(c.gold)
                                        .padding(horizontal = 8.dp, vertical = 2.dp)
                                ) {
                                    Text(
                                        text = "NOW",
                                        style = TextStyle(
                                            fontFamily = SpaceGroteskFontFamily,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 10.sp,
                                            letterSpacing = 0.9.sp,
                                            color = c.band
                                        )
                                    )
                                }
                                Text(
                                    text = "${formatTime12h(currentNowTask.start)} – ${formatTime12h(currentNowTask.end)}",
                                    style = TextStyle(
                                        fontFamily = SpaceGroteskFontFamily,
                                        fontWeight = FontWeight.Medium,
                                        fontSize = 12.sp,
                                        color = c.onBandMuted
                                    )
                                )
                            }

                            TextButton(
                                onClick = { onToggleTask(currentNowTask.id) },
                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)
                            ) {
                                Text(
                                    text = if (isNowDone) "Done ✓" else "Mark Done",
                                    color = if (isNowDone) c.ok else c.gold,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }

                        Text(
                            text = currentNowTask.title,
                            style = TextStyle(
                                fontFamily = SpaceGroteskFontFamily,
                                fontWeight = FontWeight.Bold,
                                fontSize = 16.sp,
                                color = c.onBand
                            )
                        )
                        Spacer(Modifier.height(10.dp))
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(6.dp)
                                .clip(RoundedCornerShape(99.dp))
                                .background(c.band2)
                        ) {
                            Box(
                                modifier = Modifier
                                    .fillMaxHeight()
                                    .fillMaxWidth(slotFrac)
                                    .clip(RoundedCornerShape(99.dp))
                                    .background(c.gold)
                            )
                        }
                    }
                }
            }
        }

        // 4. Next-Exam Strip
        if (nextExam != null) {
            item(key = "next_exam_strip") {
                val (exam, daysLeft) = nextExam
                val countdownLabel = when (daysLeft) {
                    0 -> "TODAY"
                    1 -> "1 day left"
                    else -> "$daysLeft days left"
                }
                Surface(
                    modifier = Modifier.fillMaxWidth().testTag("next_exam_strip"),
                    shape = RoundedCornerShape(14.dp),
                    color = c.surface2,
                    border = BorderStroke(1.dp, c.gold.copy(alpha = 0.65f))
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
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
                                tint = c.goldInk,
                                modifier = Modifier.size(18.dp)
                            )
                            Text(
                                text = "Next exam: ${exam.name}",
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                style = TextStyle(
                                    fontFamily = InterFontFamily,
                                    fontWeight = FontWeight.SemiBold,
                                    fontSize = 13.sp,
                                    color = c.ink
                                )
                            )
                        }
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(99.dp))
                                .background(c.gold.copy(alpha = 0.22f))
                                .padding(horizontal = 10.dp, vertical = 4.dp)
                        ) {
                            Text(
                                text = countdownLabel,
                                style = TextStyle(
                                    fontFamily = SpaceGroteskFontFamily,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 12.sp,
                                    color = c.goldInk
                                )
                            )
                        }
                    }
                }
            }
        }

        // 5. Filter Chips + Section Header
        item(key = "list_header") {
            Column {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 2.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Today's Routine",
                        style = TextStyle(
                            fontFamily = SpaceGroteskFontFamily,
                            fontWeight = FontWeight.Bold,
                            fontSize = 17.sp,
                            color = c.ink
                        )
                    )
                    Text(
                        text = "${filteredTasks.size} of $total tasks",
                        style = TextStyle(
                            fontFamily = InterFontFamily,
                            fontSize = 12.sp,
                            color = c.muted
                        )
                    )
                }

                Spacer(Modifier.height(6.dp))

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    ChecklistFilter.entries.forEach { f ->
                        val selected = f == filter
                        FilterChip(
                            selected = selected,
                            onClick = { onSelectFilter(f) },
                            label = {
                                Text(
                                    text = f.label,
                                    fontFamily = InterFontFamily,
                                    fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium
                                )
                            },
                            modifier = Modifier.testTag("filter_${f.name.lowercase()}"),
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = c.band,
                                selectedLabelColor = c.onBand,
                                containerColor = c.surface,
                                labelColor = c.ink
                            ),
                            border = FilterChipDefaults.filterChipBorder(
                                enabled = true,
                                selected = selected,
                                borderColor = c.line,
                                selectedBorderColor = c.band
                            )
                        )
                    }
                }
            }
        }

        // 6. Task Rows
        if (filteredTasks.isEmpty()) {
            item(key = "empty_filter") {
                DrstCard {
                    Text(
                        text = "No tasks match \"${filter.label}\". Switch filter or tap the pencil icon above to edit your routine.",
                        style = TextStyle(fontFamily = InterFontFamily, fontSize = 13.sp, color = c.muted)
                    )
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

        // 7. Footer Actions: Mark as rest day + Reset this day (two-tap confirm)
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
                        .height(46.dp)
                        .testTag("rest_day_button"),
                    shape = RoundedCornerShape(14.dp),
                    border = BorderStroke(1.dp, if (dayLog.isRestDay) c.gold else c.line),
                    colors = ButtonDefaults.outlinedButtonColors(
                        containerColor = if (dayLog.isRestDay) c.gold.copy(alpha = 0.22f) else c.glassFill,
                        contentColor = c.ink
                    )
                ) {
                    Text(
                        text = if (dayLog.isRestDay) "Unmark rest day" else "Mark as rest day",
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
                        .height(46.dp)
                        .testTag("reset_day_button"),
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (confirmReset) c.badBg else c.warnBg,
                        contentColor = if (confirmReset) c.badInk else c.warnInk
                    )
                ) {
                    Text(
                        text = if (confirmReset) "Tap again to reset" else "Reset this day",
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
private fun MiniKpiBox(
    label: String,
    value: String,
    valueColor: Color,
    modifier: Modifier = Modifier
) {
    val c = LocalDrstColors.current
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(14.dp))
            .background(c.surface2)
            .border(1.dp, c.line, RoundedCornerShape(14.dp))
            .padding(horizontal = 10.dp, vertical = 9.dp)
    ) {
        Text(
            text = label,
            maxLines = 1,
            style = TextStyle(
                fontFamily = InterFontFamily,
                fontWeight = FontWeight.SemiBold,
                fontSize = 9.sp,
                letterSpacing = 0.8.sp,
                color = c.muted
            )
        )
        Text(
            text = value,
            maxLines = 1,
            style = TextStyle(
                fontFamily = SpaceGroteskFontFamily,
                fontWeight = FontWeight.Bold,
                fontSize = 20.sp,
                color = valueColor,
                fontFeatureSettings = "tnum"
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
    val bgColor = if (isDone) c.okBg else c.warnBg
    val tintColor = if (isDone) c.okInk else c.warnInk
    val shape = RoundedCornerShape(18.dp)

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(bgColor)
            .border(
                width = if (isNow) 2.dp else 1.dp,
                color = if (isNow) c.gold else c.glassBorderTop,
                shape = shape
            )
            .testTag("task_row_${task.id}")
    ) {
        Column(modifier = Modifier.padding(horizontal = 10.dp, vertical = 10.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                AnimatedTaskCheckbox(
                    checked = isDone,
                    borderColor = tintColor,
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
                                color = tintColor,
                                fontFeatureSettings = "tnum"
                            )
                        )
                        if (isNow) {
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(99.dp))
                                    .background(c.gold)
                                    .padding(horizontal = 6.dp, vertical = 1.dp)
                            ) {
                                Text(
                                    text = "NOW",
                                    style = TextStyle(
                                        fontFamily = SpaceGroteskFontFamily,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 9.sp,
                                        color = c.band
                                    )
                                )
                            }
                        }
                    }

                    Spacer(Modifier.height(2.dp))

                    Text(
                        text = task.title,
                        style = TextStyle(
                            fontFamily = InterFontFamily,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 15.sp,
                            color = c.ink.copy(alpha = if (isDone) 0.78f else 1f),
                            textDecoration = if (isDone) TextDecoration.LineThrough else TextDecoration.None
                        )
                    )

                    Spacer(Modifier.height(4.dp))

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        // Category chip with dot
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(7.dp)
                                    .clip(CircleShape)
                                    .background(categoryColor(task.category))
                            )
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

                        Text(
                            text = formatDuration(task.durationMinutes),
                            style = TextStyle(
                                fontFamily = SpaceGroteskFontFamily,
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 11.sp,
                                color = tintColor
                            )
                        )
                    }
                }

                Column(
                    horizontalAlignment = Alignment.End,
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(99.dp))
                            .background(tintColor.copy(alpha = 0.14f))
                            .padding(horizontal = 9.dp, vertical = 3.dp)
                    ) {
                        Text(
                            text = if (isDone) "Completed" else "Pending",
                            style = TextStyle(
                                fontFamily = InterFontFamily,
                                fontWeight = FontWeight.Bold,
                                fontSize = 10.sp,
                                color = tintColor
                            )
                        )
                    }

                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .clickable(onClick = onToggleNote),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Description,
                            contentDescription = "Task note",
                            tint = tintColor,
                            modifier = Modifier.size(18.dp)
                        )
                        if (note.isNotBlank()) {
                            Box(
                                modifier = Modifier
                                    .align(Alignment.TopEnd)
                                    .padding(6.dp)
                                    .size(7.dp)
                                    .clip(CircleShape)
                                    .background(c.gold)
                            )
                        }
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
                        .padding(top = 8.dp),
                    shape = RoundedCornerShape(12.dp),
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
