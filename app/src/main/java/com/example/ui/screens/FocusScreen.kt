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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.example.data.*
import com.example.ui.components.DrstCard
import com.example.ui.theme.InterFontFamily
import com.example.ui.theme.LocalDrstColors
import com.example.ui.theme.SpaceGroteskFontFamily
import com.example.viewmodel.FocusTimerState
import java.util.Calendar
import java.util.Locale

/**
 * Clean, laser-focused Focus tab:
 * 1. Pomodoro Focus Timer (with subjects & presets)
 * 2. Mind Lab • Study Focus Trainer (Memory Matrix, Stroop Focus, Speed Math)
 * 3. Exam Countdown
 */
@Composable
fun FocusScreen(
    profile: UserProfile,
    timer: FocusTimerState,
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
    onOpenGame: () -> Unit,
    onSelectSoundMode: (FocusSoundMode) -> Unit = {}
) {
    val c = LocalDrstColors.current
    val context = LocalContext.current

    var showSubjectsDialog by remember { mutableStateOf(false) }
    var examName by remember { mutableStateOf("") }
    var examDate by remember { mutableStateOf(shiftDateKey(todayKey(), 14)) }

    val totalPlannedSec = (timer.durationMinutes * 60).coerceAtLeast(1)
    val ringFraction = 1f - (timer.remainingSeconds.toFloat() / totalPlannedSec.toFloat()).coerceIn(0f, 1f)
    val animRing by animateFloatAsState(
        targetValue = ringFraction,
        animationSpec = tween(250, easing = FastOutSlowInEasing),
        label = "timer_ring"
    )

    LazyColumn(
        modifier = Modifier.fillMaxSize().testTag("focus_list"),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 114.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // 1. Focus Timer Card
        item(key = "focus_timer_card") {
            DrstCard(title = "Pomodoro Focus Timer") {
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

                // Luminous Prism Timer Ring
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 6.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Box(modifier = Modifier.size(200.dp), contentAlignment = Alignment.Center) {
                        Canvas(modifier = Modifier.fillMaxSize()) {
                            val sw = 13.dp.toPx()
                            val inset = sw / 2f + 4.dp.toPx()
                            val arcSize = Size(size.width - inset * 2f, size.height - inset * 2f)
                            drawArc(
                                color = c.track,
                                startAngle = -90f,
                                sweepAngle = 360f,
                                useCenter = false,
                                topLeft = Offset(inset, inset),
                                size = arcSize,
                                style = Stroke(width = sw, cap = StrokeCap.Round)
                            )
                            val sweep = (animRing * 360f).coerceAtLeast(if (timer.isRunning) 4f else 0f)
                            if (sweep > 0.5f) {
                                drawArc(
                                    color = (if (timer.isRunning) c.ok else c.prismCyan).copy(alpha = 0.24f),
                                    startAngle = -90f,
                                    sweepAngle = sweep,
                                    useCenter = false,
                                    topLeft = Offset(inset, inset),
                                    size = arcSize,
                                    style = Stroke(width = sw + 6.dp.toPx(), cap = StrokeCap.Round)
                                )
                                drawArc(
                                    brush = Brush.sweepGradient(
                                        if (timer.isRunning) listOf(c.prismCyan, c.ok, c.gold, c.prismCyan)
                                        else listOf(c.prismViolet, c.prismCyan, c.gold, c.prismViolet)
                                    ),
                                    startAngle = -90f,
                                    sweepAngle = sweep,
                                    useCenter = false,
                                    topLeft = Offset(inset, inset),
                                    size = arcSize,
                                    style = Stroke(width = sw, cap = StrokeCap.Round)
                                )
                            }
                        }
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            val mm = timer.remainingSeconds / 60
                            val ss = timer.remainingSeconds % 60
                            Text(
                                text = String.format(Locale.US, "%02d:%02d", mm, ss),
                                style = TextStyle(
                                    fontFamily = SpaceGroteskFontFamily,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 42.sp,
                                    letterSpacing = (-1).sp,
                                    color = c.ink,
                                    fontFeatureSettings = "tnum"
                                ),
                                modifier = Modifier.testTag("timer_readout")
                            )
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(99.dp))
                                    .background((if (timer.isRunning) c.ok else c.prismCyan).copy(alpha = 0.16f))
                                    .padding(horizontal = 10.dp, vertical = 3.dp)
                            ) {
                                Text(
                                    text = timer.selectedSubject,
                                    style = TextStyle(
                                        fontFamily = InterFontFamily,
                                        fontWeight = FontWeight.SemiBold,
                                        fontSize = 11.sp,
                                        color = if (timer.isRunning) c.okInk else c.ink
                                    )
                                )
                            }
                        }
                    }
                }

                Spacer(Modifier.height(12.dp))

                // Clean Presets: 15 / 25 / 45 / 60m
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    listOf(15, 25, 45, 60).forEach { mins ->
                        val selected = timer.durationMinutes == mins && !timer.isRunning && !timer.isPaused
                        OutlinedButton(
                            onClick = { onSetPresetMinutes(mins) },
                            modifier = Modifier.weight(1f).height(42.dp).testTag("preset_${mins}m"),
                            contentPadding = PaddingValues(0.dp),
                            shape = RoundedCornerShape(12.dp),
                            border = BorderStroke(1.dp, if (selected) c.prismCyan else c.line),
                            colors = ButtonDefaults.outlinedButtonColors(
                                containerColor = if (selected) c.prismCyan.copy(alpha = 0.16f) else c.surface2,
                                contentColor = c.ink
                            )
                        ) {
                            Text("${mins}m", fontFamily = SpaceGroteskFontFamily, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                        }
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
                        shape = RoundedCornerShape(14.dp),
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
                            shape = RoundedCornerShape(14.dp),
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
                            shape = RoundedCornerShape(14.dp),
                            border = BorderStroke(1.dp, c.line)
                        ) {
                            Icon(Icons.Rounded.Refresh, contentDescription = "Reset timer", tint = c.ink)
                        }
                    }
                }

                Spacer(Modifier.height(14.dp))

                // Offline Focus Soundscape Pill Bar (auto-plays with timer)
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(5.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Headphones,
                                contentDescription = null,
                                tint = if (timer.soundMode != FocusSoundMode.OFF) c.prismCyan else c.muted,
                                modifier = Modifier.size(15.dp)
                            )
                            Text(
                                text = "Focus Soundscape",
                                style = TextStyle(
                                    fontFamily = InterFontFamily,
                                    fontWeight = FontWeight.SemiBold,
                                    fontSize = 12.sp,
                                    color = c.muted
                                )
                            )
                        }
                        if (timer.soundMode != FocusSoundMode.OFF) {
                            Text(
                                text = if (timer.isRunning) "Playing • Offline" else "Plays on Start",
                                style = TextStyle(
                                    fontFamily = InterFontFamily,
                                    fontWeight = FontWeight.SemiBold,
                                    fontSize = 11.sp,
                                    color = c.prismCyan
                                )
                            )
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        FocusSoundMode.entries.forEach { mode ->
                            val active = timer.soundMode == mode
                            val pillShape = RoundedCornerShape(12.dp)
                            val icon = when (mode) {
                                FocusSoundMode.OFF -> Icons.Rounded.VolumeOff
                                FocusSoundMode.RAIN -> Icons.Rounded.WaterDrop
                                FocusSoundMode.BROWN_NOISE -> Icons.Rounded.GraphicEq
                                FocusSoundMode.LOFI_PAD -> Icons.Rounded.MusicNote
                            }
                            Column(
                                modifier = Modifier
                                    .weight(1f)
                                    .clip(pillShape)
                                    .background(
                                        if (active) c.prismCyan.copy(alpha = if (c.isDark) 0.22f else 0.16f)
                                        else c.surface2
                                    )
                                    .border(
                                        width = if (active) 1.2.dp else 1.dp,
                                        color = if (active) c.prismCyan else c.line,
                                        shape = pillShape
                                    )
                                    .clickable { onSelectSoundMode(mode) }
                                    .padding(vertical = 8.dp, horizontal = 4.dp)
                                    .testTag("sound_mode_${mode.name.lowercase()}"),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(3.dp)
                            ) {
                                Icon(
                                    imageVector = icon,
                                    contentDescription = mode.label,
                                    tint = if (active) (if (c.isDark) c.prismCyan else c.band) else c.muted,
                                    modifier = Modifier.size(16.dp)
                                )
                                Text(
                                    text = mode.label,
                                    maxLines = 1,
                                    style = TextStyle(
                                        fontFamily = InterFontFamily,
                                        fontWeight = if (active) FontWeight.Bold else FontWeight.Medium,
                                        fontSize = 11.sp,
                                        color = if (active) c.ink else c.muted
                                    )
                                )
                            }
                        }
                    }
                }
            }
        }

        // 2. Mind Lab • Study Focus Trainer Card (single clean entry point)
        item(key = "mind_lab_card") {
            DrstCard(title = "Mind Lab • Study Focus Trainer") {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(50.dp)
                            .clip(RoundedCornerShape(16.dp))
                            .background(Brush.linearGradient(listOf(c.prismCyan.copy(alpha = 0.22f), c.prismViolet.copy(alpha = 0.26f))))
                            .border(1.dp, c.prismCyan.copy(alpha = 0.45f), RoundedCornerShape(16.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Psychology,
                            contentDescription = null,
                            tint = c.prismCyan,
                            modifier = Modifier.size(27.dp)
                        )
                    }
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "2-Min Focus Warm-Up",
                            style = TextStyle(
                                fontFamily = SpaceGroteskFontFamily,
                                fontWeight = FontWeight.Bold,
                                fontSize = 15.sp,
                                color = c.ink
                            )
                        )
                        Text(
                            text = "Memory Matrix • Stroop • Speed Math",
                            style = TextStyle(fontFamily = InterFontFamily, fontSize = 12.sp, color = c.muted)
                        )
                    }
                    Button(
                        onClick = onOpenGame,
                        modifier = Modifier.height(42.dp).testTag("focus_play_mind_lab"),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = c.prismCyan, contentColor = Color(0xFF061322)),
                        contentPadding = PaddingValues(horizontal = 14.dp)
                    ) {
                        Icon(Icons.Rounded.PlayArrow, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Train", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }

        // 3. Exam Countdown Card
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
                        placeholder = { Text("Exam name (e.g. CET, JEE)", fontSize = 13.sp) },
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
                                shape = RoundedCornerShape(14.dp),
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

    Dialog(onDismissRequest = onDismiss) {
        val shape = RoundedCornerShape(28.dp)
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(shape)
                .background(if (c.isDark) Color(0xD90E1633) else Color(0xEBFFFFFF))
                .background(Brush.verticalGradient(listOf(c.glassHighlight, c.glassFill)))
                .border(
                    1.25.dp,
                    Brush.linearGradient(listOf(c.glassBorderTop, c.prismCyan.copy(alpha = 0.45f), c.glassBorderBottom)),
                    shape
                )
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        "Manage Subjects",
                        fontFamily = SpaceGroteskFontFamily,
                        fontWeight = FontWeight.Bold,
                        fontSize = 19.sp,
                        color = c.ink
                    )
                    IconButton(onClick = onDismiss, modifier = Modifier.size(36.dp)) {
                        Icon(Icons.Rounded.Close, contentDescription = "Close dialog", tint = c.muted)
                    }
                }

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
                        shape = RoundedCornerShape(14.dp)
                    )
                    Button(
                        onClick = {
                            if (newSubject.isNotBlank()) {
                                onAdd(newSubject)
                                newSubject = ""
                            }
                        },
                        shape = RoundedCornerShape(14.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = c.prismCyan, contentColor = Color(0xFF061322))
                    ) {
                        Text("Add", fontWeight = FontWeight.Bold)
                    }
                }

                Column(
                    modifier = Modifier
                        .heightIn(max = 260.dp)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
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
                                    modifier = Modifier.weight(1f),
                                    shape = RoundedCornerShape(12.dp)
                                )
                                TextButton(onClick = {
                                    onRename(subj, editDraft)
                                    editingSubject = null
                                }) { Text("Save", color = c.okInk, fontWeight = FontWeight.Bold) }
                            }
                        } else {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(14.dp))
                                    .background(if (c.isDark) Color(0x38FFFFFF) else Color(0xA6FFFFFF))
                                    .border(1.dp, c.glassBorderTop.copy(alpha = 0.5f), RoundedCornerShape(14.dp))
                                    .padding(horizontal = 12.dp, vertical = 7.dp),
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

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    Button(
                        onClick = onDismiss,
                        shape = RoundedCornerShape(14.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = c.band, contentColor = c.onBand)
                    ) {
                        Text("Done", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}
