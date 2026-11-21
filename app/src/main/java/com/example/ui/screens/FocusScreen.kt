package com.example.ui.screens

import android.app.DatePickerDialog
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.*
import com.example.ui.components.DrstCard
import com.example.ui.theme.InterFontFamily
import com.example.ui.theme.LocalDrstColors
import com.example.ui.theme.SpaceGroteskFontFamily
import com.example.viewmodel.FocusTimerState
import java.util.Calendar
import java.util.Locale

@Composable
fun FocusScreen(
    profile: UserProfile,
    timer: FocusTimerState,
    doubtFilter: DoubtFilter,
    onSelectSubject: (String) -> Unit,
    onAddSubject: (String) -> Unit,
    onRenameSubject: (String, String) -> Unit,
    onDeleteSubject: (String) -> Unit,
    onSetPresetMinutes: (Int) -> Unit,
    onStartOrResume: () -> Unit,
    onPause: () -> Unit,
    onSaveAndStop: () -> Unit,
    onResetTimer: () -> Unit,
    onAddExam: (String, String) -> Unit,
    onDeleteExam: (String) -> Unit,
    onSetDoubtFilter: (DoubtFilter) -> Unit,
    onAddDoubt: (String, String) -> Unit,
    onToggleDoubtSolved: (String) -> Unit,
    onDeleteDoubt: (String) -> Unit,
    onAskAiDoubt: (DoubtItem) -> Unit,
    onOpenGame: () -> Unit
) {
    val c = LocalDrstColors.current
    val context = LocalContext.current

    var showSubjectsDialog by remember { mutableStateOf(false) }
    var customMinutesText by remember { mutableStateOf("") }

    var examName by remember { mutableStateOf("") }
    var examDate by remember { mutableStateOf(shiftDateKey(todayKey(), 14)) }

    var doubtSubject by remember(profile.subjects) {
        mutableStateOf(profile.subjects.firstOrNull() ?: "Maths")
    }
    var doubtText by remember { mutableStateOf("") }

    val last7 = remember { (0..6).map { shiftDateKey(todayKey(), -it) } }
    val subjectHours7d = remember(profile, last7) {
        Analytics.subjectHoursInDateRange(profile, last7)
    }

    val filteredDoubts = remember(profile.doubts, doubtFilter) {
        when (doubtFilter) {
            DoubtFilter.OPEN -> profile.doubts.filter { !it.solved }
            DoubtFilter.SOLVED -> profile.doubts.filter { it.solved }
            DoubtFilter.ALL -> profile.doubts
        }
    }

    val totalXp = Analytics.totalXp(profile)
    val (level, levelTitle, levelProgress) = Analytics.levelInfo(totalXp)

    val totalPlannedSec = (timer.durationMinutes * 60).coerceAtLeast(1)
    val ringFraction = 1f - (timer.remainingSeconds.toFloat() / totalPlannedSec.toFloat()).coerceIn(0f, 1f)
    val animRing by animateFloatAsState(
        targetValue = ringFraction,
        animationSpec = tween(250, easing = FastOutSlowInEasing),
        label = "timer_ring"
    )

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 112.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // 1. Focus Timer Card
        item(key = "focus_timer_card") {
            DrstCard(title = "Pomodoro Focus Timer") {
                // Subject chips + Manage button
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "Subject",
                        style = TextStyle(fontFamily = InterFontFamily, fontWeight = FontWeight.SemiBold, fontSize = 12.sp, color = c.muted)
                    )
                    TextButton(
                        onClick = { showSubjectsDialog = true },
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                        modifier = Modifier.testTag("manage_subjects_button")
                    ) {
                        Icon(Icons.Rounded.Tune, contentDescription = null, modifier = Modifier.size(15.dp), tint = c.goldInk)
                        Spacer(Modifier.width(4.dp))
                        Text("Edit subjects", fontSize = 12.sp, color = c.goldInk, fontWeight = FontWeight.SemiBold)
                    }
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    profile.subjects.forEach { subj ->
                        val active = subj == timer.selectedSubject
                        FilterChip(
                            selected = active,
                            onClick = { onSelectSubject(subj) },
                            label = { Text(subj, fontSize = 12.sp) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = c.band,
                                selectedLabelColor = c.onBand,
                                containerColor = c.surface2,
                                labelColor = c.ink
                            )
                        )
                    }
                }

                Spacer(Modifier.height(14.dp))

                // Timer Ring
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Box(modifier = Modifier.size(188.dp), contentAlignment = Alignment.Center) {
                        Canvas(modifier = Modifier.fillMaxSize()) {
                            val sw = 12.dp.toPx()
                            val inset = sw / 2f
                            val arcSize = Size(size.width - sw, size.height - sw)
                            drawArc(
                                color = c.track,
                                startAngle = -90f,
                                sweepAngle = 360f,
                                useCenter = false,
                                topLeft = Offset(inset, inset),
                                size = arcSize,
                                style = Stroke(width = sw, cap = StrokeCap.Round)
                            )
                            drawArc(
                                color = if (timer.isRunning) c.ok else c.gold,
                                startAngle = -90f,
                                sweepAngle = animRing * 360f,
                                useCenter = false,
                                topLeft = Offset(inset, inset),
                                size = arcSize,
                                style = Stroke(width = sw, cap = StrokeCap.Round)
                            )
                        }
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            val mm = timer.remainingSeconds / 60
                            val ss = timer.remainingSeconds % 60
                            Text(
                                text = String.format(Locale.US, "%02d:%02d", mm, ss),
                                style = TextStyle(
                                    fontFamily = SpaceGroteskFontFamily,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 38.sp,
                                    color = c.ink,
                                    fontFeatureSettings = "tnum"
                                ),
                                modifier = Modifier.testTag("timer_readout")
                            )
                            Text(
                                text = timer.selectedSubject,
                                style = TextStyle(fontFamily = InterFontFamily, fontSize = 12.sp, color = c.muted)
                            )
                        }
                    }
                }

                Spacer(Modifier.height(12.dp))

                // Presets 15/25/45/60 + Custom
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    listOf(15, 25, 45, 60).forEach { mins ->
                        val selected = timer.durationMinutes == mins && !timer.isRunning && !timer.isPaused
                        OutlinedButton(
                            onClick = { onSetPresetMinutes(mins) },
                            modifier = Modifier.weight(1f).height(40.dp).testTag("preset_${mins}m"),
                            contentPadding = PaddingValues(0.dp),
                            shape = RoundedCornerShape(10.dp),
                            border = BorderStroke(1.dp, if (selected) c.gold else c.line),
                            colors = ButtonDefaults.outlinedButtonColors(
                                containerColor = if (selected) c.gold.copy(alpha = 0.18f) else c.surface2,
                                contentColor = c.ink
                            )
                        ) {
                            Text("${mins}m", fontFamily = SpaceGroteskFontFamily, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                        }
                    }
                }

                Spacer(Modifier.height(8.dp))

                // Custom minutes input (e.g. 1 min)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedTextField(
                        value = customMinutesText,
                        onValueChange = { customMinutesText = it.filter { ch -> ch.isDigit() }.take(3) },
                        placeholder = { Text("Custom min (e.g. 1, 30)", fontSize = 13.sp) },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.weight(1f).testTag("custom_minutes_input"),
                        shape = RoundedCornerShape(12.dp)
                    )
                    OutlinedButton(
                        onClick = {
                            val m = customMinutesText.toIntOrNull()
                            if (m != null && m >= 1) {
                                onSetPresetMinutes(m)
                                customMinutesText = ""
                            }
                        },
                        modifier = Modifier.height(52.dp).testTag("set_custom_minutes_button"),
                        shape = RoundedCornerShape(12.dp),
                        border = BorderStroke(1.dp, c.gold)
                    ) {
                        Text("Set", color = c.ink, fontWeight = FontWeight.Bold)
                    }
                }

                Spacer(Modifier.height(12.dp))

                // Start / Pause / Resume / Save & Stop
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        onClick = { if (timer.isRunning) onPause() else onStartOrResume() },
                        modifier = Modifier.weight(1f).height(48.dp).testTag("timer_start_pause_button"),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (timer.isRunning) c.warn else c.band,
                            contentColor = if (timer.isRunning) c.band else c.onBand
                        )
                    ) {
                        Icon(
                            if (timer.isRunning) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(Modifier.width(6.dp))
                        Text(
                            text = when {
                                timer.isRunning -> "Pause"
                                timer.isPaused -> "Resume"
                                else -> "Start Focus"
                            },
                            fontWeight = FontWeight.Bold
                        )
                    }

                    if (timer.isRunning || timer.isPaused) {
                        Button(
                            onClick = onSaveAndStop,
                            modifier = Modifier.weight(1f).height(48.dp).testTag("timer_save_stop_button"),
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = c.ok,
                                contentColor = Color.White
                            )
                        ) {
                            Text("Save & stop", fontWeight = FontWeight.Bold)
                        }
                    } else {
                        OutlinedButton(
                            onClick = onResetTimer,
                            modifier = Modifier.height(48.dp),
                            shape = RoundedCornerShape(12.dp),
                            border = BorderStroke(1.dp, c.line)
                        ) {
                            Icon(Icons.Rounded.Refresh, contentDescription = "Reset timer", tint = c.ink)
                        }
                    }
                }
            }
        }

        // 1.5 Study Break Arcade: Orbit Pulse Launcher Card
        item(key = "orbit_pulse_card") {
            DrstCard(title = "Study Break Arcade • Orbit Pulse") {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(50.dp)
                            .clip(RoundedCornerShape(16.dp))
                            .background(c.band),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.SportsEsports,
                            contentDescription = null,
                            tint = c.gold,
                            modifier = Modifier.size(26.dp)
                        )
                    }
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Take a 2-Minute Reflex Break",
                            style = TextStyle(
                                fontFamily = SpaceGroteskFontFamily,
                                fontWeight = FontWeight.Bold,
                                fontSize = 15.sp,
                                color = c.ink
                            )
                        )
                        Text(
                            text = "Best Score: ${profile.bestGameScore} • Wave ${profile.bestGameWave} • Earns Study XP",
                            style = TextStyle(fontFamily = InterFontFamily, fontSize = 12.sp, color = c.muted)
                        )
                    }
                    Button(
                        onClick = onOpenGame,
                        modifier = Modifier.height(42.dp).testTag("focus_play_orbit_pulse"),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = c.gold, contentColor = c.band),
                        contentPadding = PaddingValues(horizontal = 14.dp)
                    ) {
                        Icon(Icons.Rounded.PlayArrow, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Play", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }

        // 2. Exam Countdown Card
        item(key = "exam_countdown_card") {
            DrstCard(title = "Exam Countdown") {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedTextField(
                        value = examName,
                        onValueChange = { examName = it },
                        placeholder = { Text("Exam name (e.g. JEE Mains)", fontSize = 13.sp) },
                        singleLine = true,
                        modifier = Modifier.weight(1f).testTag("exam_name_input"),
                        shape = RoundedCornerShape(12.dp)
                    )
                    OutlinedButton(
                        onClick = {
                            val cal = keyToCalendar(examDate)
                            DatePickerDialog(
                                context,
                                { _, y, m, d ->
                                    examDate = String.format(Locale.US, "%04d-%02d-%02d", y, m + 1, d)
                                },
                                cal.get(Calendar.YEAR),
                                cal.get(Calendar.MONTH),
                                cal.get(Calendar.DAY_OF_MONTH)
                            ).show()
                        },
                        modifier = Modifier.height(52.dp).testTag("exam_date_button"),
                        shape = RoundedCornerShape(12.dp),
                        contentPadding = PaddingValues(horizontal = 10.dp),
                        border = BorderStroke(1.dp, c.line)
                    ) {
                        Text(formatCompactDate(examDate), fontSize = 12.sp, color = c.ink, fontFamily = SpaceGroteskFontFamily, fontWeight = FontWeight.Bold)
                    }
                }
                Spacer(Modifier.height(8.dp))
                Button(
                    onClick = {
                        if (examName.isNotBlank()) {
                            onAddExam(examName, examDate)
                            examName = ""
                        }
                    },
                    enabled = examName.isNotBlank(),
                    modifier = Modifier.fillMaxWidth().height(44.dp).testTag("add_exam_button"),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = c.band, contentColor = c.onBand)
                ) {
                    Icon(Icons.Rounded.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Add Exam", fontWeight = FontWeight.SemiBold)
                }

                if (profile.exams.isNotEmpty()) {
                    Spacer(Modifier.height(12.dp))
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        profile.exams.forEach { exam ->
                            val dLeft = daysBetween(todayKey(), exam.date)
                            val badgeText = when {
                                dLeft < 0 -> "Passed"
                                dLeft == 0 -> "Today!"
                                dLeft == 1 -> "1 day left"
                                else -> "$dLeft days left"
                            }
                            Surface(
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(12.dp),
                                color = c.surface2,
                                border = BorderStroke(1.dp, c.line)
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = exam.name,
                                            style = TextStyle(fontFamily = SpaceGroteskFontFamily, fontWeight = FontWeight.Bold, fontSize = 14.sp, color = c.ink)
                                        )
                                        Text(
                                            text = formatFriendlyDate(exam.date),
                                            style = TextStyle(fontFamily = InterFontFamily, fontSize = 11.sp, color = c.muted)
                                        )
                                    }
                                    Box(
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(99.dp))
                                            .background(if (dLeft >= 0) c.gold.copy(alpha = 0.22f) else c.track)
                                            .padding(horizontal = 10.dp, vertical = 4.dp)
                                    ) {
                                        Text(
                                            text = badgeText,
                                            style = TextStyle(
                                                fontFamily = SpaceGroteskFontFamily,
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 12.sp,
                                                color = if (dLeft >= 0) c.goldInk else c.muted
                                            )
                                        )
                                    }
                                    IconButton(
                                        onClick = { onDeleteExam(exam.id) },
                                        modifier = Modifier.size(36.dp)
                                    ) {
                                        Icon(Icons.Rounded.DeleteOutline, contentDescription = "Delete exam", tint = c.muted, modifier = Modifier.size(18.dp))
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        // 3. Hours by Subject (Last 7 Days)
        item(key = "hours_by_subject_7d") {
            DrstCard(title = "Hours by Subject (Last 7 Days)") {
                if (subjectHours7d.isEmpty()) {
                    Text(
                        text = "Complete a focus session or tick study tasks to track subject hours.",
                        style = TextStyle(fontFamily = InterFontFamily, fontSize = 13.sp, color = c.muted)
                    )
                } else {
                    val maxH = subjectHours7d.values.maxOrNull()?.coerceAtLeast(0.5) ?: 1.0
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        subjectHours7d.forEach { (s, h) ->
                            HorizontalBarRow(
                                label = s,
                                fraction = (h / maxH).toFloat().coerceIn(0f, 1f),
                                valueText = String.format(Locale.US, "%.1f h", h),
                                barColor = c.gold
                            )
                        }
                    }
                }
            }
        }

        // 4. Mistake & Doubt Notebook
        item(key = "doubt_notebook_card") {
            DrstCard(title = "Mistake & Doubt Notebook") {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    profile.subjects.forEach { s ->
                        val active = s == doubtSubject
                        FilterChip(
                            selected = active,
                            onClick = { doubtSubject = s },
                            label = { Text(s, fontSize = 12.sp) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = c.gold.copy(alpha = 0.22f),
                                selectedLabelColor = c.ink,
                                containerColor = c.surface2,
                                labelColor = c.muted
                            )
                        )
                    }
                }

                Spacer(Modifier.height(8.dp))

                OutlinedTextField(
                    value = doubtText,
                    onValueChange = { doubtText = it },
                    placeholder = { Text("Log a mistake, tricky formula, or concept doubt...", fontSize = 13.sp) },
                    modifier = Modifier.fillMaxWidth().testTag("doubt_text_input"),
                    shape = RoundedCornerShape(12.dp),
                    minLines = 2
                )

                Spacer(Modifier.height(8.dp))

                Button(
                    onClick = {
                        if (doubtText.isNotBlank()) {
                            onAddDoubt(doubtSubject, doubtText)
                            doubtText = ""
                        }
                    },
                    enabled = doubtText.isNotBlank(),
                    modifier = Modifier.fillMaxWidth().height(44.dp).testTag("add_doubt_button"),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = c.band, contentColor = c.onBand)
                ) {
                    Text("Add to Notebook", fontWeight = FontWeight.SemiBold)
                }

                Spacer(Modifier.height(12.dp))

                // Filter chips: Open / Solved / All
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    DoubtFilter.entries.forEach { df ->
                        val selected = df == doubtFilter
                        FilterChip(
                            selected = selected,
                            onClick = { onSetDoubtFilter(df) },
                            label = { Text(df.label, fontSize = 12.sp) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = c.band,
                                selectedLabelColor = c.onBand,
                                containerColor = c.surface2,
                                labelColor = c.ink
                            )
                        )
                    }
                }

                Spacer(Modifier.height(8.dp))

                if (filteredDoubts.isEmpty()) {
                    Text(
                        text = "No ${doubtFilter.label.lowercase()} doubts yet.",
                        style = TextStyle(fontFamily = InterFontFamily, fontSize = 13.sp, color = c.muted)
                    )
                } else {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        filteredDoubts.forEach { doubt ->
                            Surface(
                                modifier = Modifier.fillMaxWidth().testTag("doubt_item_${doubt.id}"),
                                shape = RoundedCornerShape(14.dp),
                                color = if (doubt.solved) c.okBg else c.surface2,
                                border = BorderStroke(1.dp, c.line)
                            ) {
                                Column(modifier = Modifier.padding(12.dp)) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .clip(RoundedCornerShape(99.dp))
                                                .background(c.gold.copy(alpha = 0.22f))
                                                .padding(horizontal = 8.dp, vertical = 2.dp)
                                        ) {
                                            Text(
                                                text = doubt.subject,
                                                style = TextStyle(fontFamily = InterFontFamily, fontWeight = FontWeight.SemiBold, fontSize = 11.sp, color = c.goldInk)
                                            )
                                        }
                                        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                            TextButton(
                                                onClick = { onAskAiDoubt(doubt) },
                                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                                                modifier = Modifier.testTag("ask_ai_doubt_${doubt.id}")
                                            ) {
                                                Icon(Icons.Rounded.AutoAwesome, contentDescription = null, modifier = Modifier.size(15.dp), tint = c.goldInk)
                                                Spacer(Modifier.width(4.dp))
                                                Text("Ask AI", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = c.goldInk)
                                            }
                                            IconButton(
                                                onClick = { onToggleDoubtSolved(doubt.id) },
                                                modifier = Modifier.size(34.dp)
                                            ) {
                                                Icon(
                                                    if (doubt.solved) Icons.Rounded.CheckCircle else Icons.Rounded.RadioButtonUnchecked,
                                                    contentDescription = if (doubt.solved) "Mark open" else "Mark solved",
                                                    tint = if (doubt.solved) c.ok else c.muted,
                                                    modifier = Modifier.size(20.dp)
                                                )
                                            }
                                            IconButton(
                                                onClick = { onDeleteDoubt(doubt.id) },
                                                modifier = Modifier.size(34.dp)
                                            ) {
                                                Icon(Icons.Rounded.DeleteOutline, contentDescription = "Delete doubt", tint = c.muted, modifier = Modifier.size(18.dp))
                                            }
                                        }
                                    }
                                    Spacer(Modifier.height(4.dp))
                                    Text(
                                        text = doubt.text,
                                        style = TextStyle(
                                            fontFamily = InterFontFamily,
                                            fontSize = 14.sp,
                                            color = c.ink,
                                            textDecoration = if (doubt.solved) TextDecoration.LineThrough else TextDecoration.None
                                        )
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        // 5. Level & 8 Badges Card
        item(key = "level_badges_card") {
            DrstCard(title = "Level & Badges") {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column {
                        Text(
                            text = "Level $level • $levelTitle",
                            style = TextStyle(
                                fontFamily = SpaceGroteskFontFamily,
                                fontWeight = FontWeight.Bold,
                                fontSize = 18.sp,
                                color = c.ink
                            )
                        )
                        Text(
                            text = "$totalXp XP (10 XP/task + 2 XP/focus min)",
                            style = TextStyle(fontFamily = InterFontFamily, fontSize = 12.sp, color = c.muted)
                        )
                    }
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(99.dp))
                            .background(c.gold.copy(alpha = 0.22f))
                            .padding(horizontal = 10.dp, vertical = 4.dp)
                    ) {
                        Text(
                            text = "${profile.unlockedBadges.size}/${ALL_BADGES.size}",
                            style = TextStyle(fontFamily = SpaceGroteskFontFamily, fontWeight = FontWeight.Bold, fontSize = 13.sp, color = c.goldInk)
                        )
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
                            .fillMaxWidth(levelProgress.coerceIn(0.03f, 1f))
                            .clip(RoundedCornerShape(99.dp))
                            .background(c.gold)
                    )
                }

                Spacer(Modifier.height(14.dp))

                // 8 Badges in 2-column grid
                val badgeRows = ALL_BADGES.chunked(2)
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    badgeRows.forEach { pair ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            pair.forEach { badge ->
                                val unlocked = badge.id in profile.unlockedBadges
                                Surface(
                                    modifier = Modifier.weight(1f),
                                    shape = RoundedCornerShape(14.dp),
                                    color = if (unlocked) c.gold.copy(alpha = 0.16f) else c.surface2,
                                    border = BorderStroke(1.dp, if (unlocked) c.gold else c.line)
                                ) {
                                    Row(
                                        modifier = Modifier.padding(10.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .size(34.dp)
                                                .clip(CircleShape)
                                                .background(if (unlocked) c.gold else c.track),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Icon(
                                                imageVector = badgeIconFor(badge.iconKey),
                                                contentDescription = null,
                                                tint = if (unlocked) c.band else c.muted,
                                                modifier = Modifier.size(18.dp)
                                            )
                                        }
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                text = badge.title,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis,
                                                style = TextStyle(
                                                    fontFamily = SpaceGroteskFontFamily,
                                                    fontWeight = FontWeight.Bold,
                                                    fontSize = 13.sp,
                                                    color = if (unlocked) c.ink else c.muted
                                                )
                                            )
                                            Text(
                                                text = badge.subtitle,
                                                maxLines = 2,
                                                overflow = TextOverflow.Ellipsis,
                                                style = TextStyle(fontFamily = InterFontFamily, fontSize = 10.sp, color = c.muted)
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (showSubjectsDialog) {
        ManageSubjectsDialog(
            subjects = profile.subjects,
            onAdd = onAddSubject,
            onRename = onRenameSubject,
            onDelete = onDeleteSubject,
            onDismiss = { showSubjectsDialog = false }
        )
    }
}

private fun badgeIconFor(key: String) = when (key) {
    "check" -> Icons.Rounded.CheckCircle
    "star" -> Icons.Rounded.Star
    "flame" -> Icons.Rounded.LocalFireDepartment
    "trophy" -> Icons.Rounded.EmojiEvents
    "timer" -> Icons.Rounded.Timer
    "bolt" -> Icons.Rounded.Bolt
    "bulb" -> Icons.Rounded.Lightbulb
    else -> Icons.Rounded.WorkspacePremium
}

@Composable
private fun ManageSubjectsDialog(
    subjects: List<String>,
    onAdd: (String) -> Unit,
    onRename: (String, String) -> Unit,
    onDelete: (String) -> Unit,
    onDismiss: () -> Unit
) {
    val c = LocalDrstColors.current
    var newSubject by remember { mutableStateOf("") }
    var editingSubject by remember { mutableStateOf<String?>(null) }
    var editDraft by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = c.surface,
        title = {
            Text(
                "Manage Subjects",
                fontFamily = SpaceGroteskFontFamily,
                fontWeight = FontWeight.Bold,
                color = c.ink
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedTextField(
                        value = newSubject,
                        onValueChange = { newSubject = it },
                        placeholder = { Text("New subject", fontSize = 13.sp) },
                        singleLine = true,
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(10.dp)
                    )
                    Button(
                        onClick = {
                            if (newSubject.isNotBlank()) {
                                onAdd(newSubject)
                                newSubject = ""
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = c.band, contentColor = c.onBand)
                    ) {
                        Text("Add")
                    }
                }

                Column(
                    modifier = Modifier.heightIn(max = 260.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    subjects.forEach { subj ->
                        if (editingSubject == subj) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                OutlinedTextField(
                                    value = editDraft,
                                    onValueChange = { editDraft = it },
                                    singleLine = true,
                                    modifier = Modifier.weight(1f)
                                )
                                TextButton(onClick = {
                                    onRename(subj, editDraft)
                                    editingSubject = null
                                }) { Text("Save", color = c.okInk) }
                            }
                        } else {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(c.surface2)
                                    .padding(horizontal = 10.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(subj, color = c.ink, fontSize = 14.sp, modifier = Modifier.weight(1f))
                                Row {
                                    IconButton(
                                        onClick = {
                                            editingSubject = subj
                                            editDraft = subj
                                        },
                                        modifier = Modifier.size(34.dp)
                                    ) {
                                        Icon(Icons.Rounded.Edit, contentDescription = "Rename subject", tint = c.muted, modifier = Modifier.size(16.dp))
                                    }
                                    if (subjects.size > 1) {
                                        IconButton(
                                            onClick = { onDelete(subj) },
                                            modifier = Modifier.size(34.dp)
                                        ) {
                                            Icon(Icons.Rounded.DeleteOutline, contentDescription = "Delete subject", tint = c.badInk, modifier = Modifier.size(16.dp))
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Done", fontWeight = FontWeight.Bold, color = c.goldInk)
            }
        }
    )
}
