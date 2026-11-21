package com.example.ui.sheets

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Restore
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.*
import com.example.ui.components.LiquidGlassBackdrop
import com.example.ui.theme.InterFontFamily
import com.example.ui.theme.LocalDrstColors
import com.example.ui.theme.SpaceGroteskFontFamily
import kotlinx.coroutines.delay

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditRoutineSheet(
    profile: UserProfile,
    onSaveRoutine: (List<RoutineTask>) -> Unit,
    onRestoreDefaults: () -> Unit,
    onDismiss: () -> Unit
) {
    BackHandler(onBack = onDismiss)
    val c = LocalDrstColors.current
    var draftTasks by remember(profile.routine) { mutableStateOf(profile.routine) }
    var confirmRestore by remember { mutableStateOf(false) }

    LaunchedEffect(confirmRestore) {
        if (confirmRestore) {
            delay(3500L)
            confirmRestore = false
        }
    }

    LiquidGlassBackdrop {
        Column(modifier = Modifier.fillMaxSize().safeDrawingPadding()) {
            // Top bar
            Surface(color = c.band.copy(alpha = 0.90f), modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column {
                        Text(
                            text = "Edit Routine",
                            style = TextStyle(fontFamily = SpaceGroteskFontFamily, fontWeight = FontWeight.Bold, fontSize = 20.sp, color = c.onBand)
                        )
                        Text(
                            text = "${draftTasks.size} daily tasks",
                            style = TextStyle(fontFamily = InterFontFamily, fontSize = 12.sp, color = c.onBandMuted)
                        )
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Button(
                            onClick = { onSaveRoutine(draftTasks) },
                            colors = ButtonDefaults.buttonColors(containerColor = c.gold, contentColor = c.band),
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.testTag("save_routine_button")
                        ) {
                            Text("Save", fontWeight = FontWeight.Bold)
                        }
                        IconButton(onClick = onDismiss) {
                            Icon(Icons.Rounded.Close, contentDescription = "Close routine editor", tint = c.onBand)
                        }
                    }
                }
            }

            LazyColumn(
                modifier = Modifier.weight(1f).fillMaxWidth(),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                itemsIndexed(draftTasks, key = { _, t -> t.id }) { index, task ->
                    RoutineEditorRow(
                        task = task,
                        subjects = profile.subjects,
                        onUpdate = { updated ->
                            draftTasks = draftTasks.toMutableList().also { it[index] = updated }
                        },
                        onDelete = {
                            draftTasks = draftTasks.filterNot { it.id == task.id }
                        }
                    )
                }

                item(key = "add_and_restore") {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Button(
                            onClick = {
                                val lastEnd = draftTasks.lastOrNull()?.end ?: "20:00"
                                val nextMin = (parseTimeToMinutes(lastEnd) + 60).coerceAtMost(23 * 60 + 45)
                                val nextEnd = String.format(java.util.Locale.US, "%02d:%02d", nextMin / 60, nextMin % 60)
                                draftTasks = draftTasks + RoutineTask(
                                    start = lastEnd,
                                    end = nextEnd,
                                    title = "New Study Block",
                                    category = "Study",
                                    subject = profile.subjects.firstOrNull() ?: "Maths"
                                )
                            },
                            modifier = Modifier.fillMaxWidth().height(48.dp).testTag("add_routine_task_button"),
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = c.band, contentColor = c.onBand)
                        ) {
                            Icon(Icons.Rounded.Add, contentDescription = null)
                            Spacer(Modifier.width(6.dp))
                            Text("Add Task", fontWeight = FontWeight.Bold)
                        }

                        OutlinedButton(
                            onClick = {
                                if (confirmRestore) {
                                    confirmRestore = false
                                    draftTasks = defaultRoutine19Tasks()
                                    onRestoreDefaults()
                                } else {
                                    confirmRestore = true
                                }
                            },
                            modifier = Modifier.fillMaxWidth().height(46.dp).testTag("restore_default_routine_button"),
                            shape = RoundedCornerShape(12.dp),
                            border = BorderStroke(1.dp, if (confirmRestore) c.badInk else c.line)
                        ) {
                            Icon(Icons.Rounded.Restore, contentDescription = null, tint = if (confirmRestore) c.badInk else c.ink, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text(
                                text = if (confirmRestore) "Tap again to restore 19 default tasks" else "Restore default 19-task routine",
                                color = if (confirmRestore) c.badInk else c.ink,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun RoutineEditorRow(
    task: RoutineTask,
    subjects: List<String>,
    onUpdate: (RoutineTask) -> Unit,
    onDelete: () -> Unit
) {
    val c = LocalDrstColors.current
    var catMenuExpanded by remember { mutableStateOf(false) }
    var subjMenuExpanded by remember { mutableStateOf(false) }

    val rowShape = RoundedCornerShape(20.dp)
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(rowShape)
            .background(c.glassFill)
            .border(1.dp, Brush.linearGradient(listOf(c.glassBorderTop, c.glassBorderBottom)), rowShape)
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedTextField(
                    value = task.start,
                    onValueChange = { onUpdate(task.copy(start = it.take(5))) },
                    label = { Text("Start (HH:mm)", fontSize = 11.sp) },
                    singleLine = true,
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(10.dp)
                )
                OutlinedTextField(
                    value = task.end,
                    onValueChange = { onUpdate(task.copy(end = it.take(5))) },
                    label = { Text("End (HH:mm)", fontSize = 11.sp) },
                    singleLine = true,
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(10.dp)
                )
                IconButton(onClick = onDelete, modifier = Modifier.size(48.dp)) {
                    Icon(Icons.Rounded.DeleteOutline, contentDescription = "Delete task", tint = c.badInk)
                }
            }

            OutlinedTextField(
                value = task.title,
                onValueChange = { onUpdate(task.copy(title = it)) },
                label = { Text("Task name", fontSize = 11.sp) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(10.dp)
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Category dropdown (12 categories)
                ExposedDropdownMenuBox(
                    expanded = catMenuExpanded,
                    onExpandedChange = { catMenuExpanded = !catMenuExpanded },
                    modifier = Modifier.weight(1f)
                ) {
                    OutlinedTextField(
                        value = task.category,
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Category", fontSize = 11.sp) },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = catMenuExpanded) },
                        modifier = Modifier.menuAnchor().fillMaxWidth(),
                        shape = RoundedCornerShape(10.dp)
                    )
                    ExposedDropdownMenu(
                        expanded = catMenuExpanded,
                        onDismissRequest = { catMenuExpanded = false },
                        modifier = Modifier.background(c.surface)
                    ) {
                        ALL_CATEGORIES.forEach { cat ->
                            DropdownMenuItem(
                                text = { Text(cat, color = c.ink) },
                                onClick = {
                                    val newSubj = if (cat in STUDY_CATEGORIES) {
                                        task.subject.ifBlank { subjects.firstOrNull() ?: "Maths" }
                                    } else ""
                                    onUpdate(task.copy(category = cat, subject = newSubj))
                                    catMenuExpanded = false
                                }
                            )
                        }
                    }
                }

                // Subject dropdown for Study/Revision/Practice/Classes tasks
                if (task.isStudyTask) {
                    ExposedDropdownMenuBox(
                        expanded = subjMenuExpanded,
                        onExpandedChange = { subjMenuExpanded = !subjMenuExpanded },
                        modifier = Modifier.weight(1f)
                    ) {
                        OutlinedTextField(
                            value = task.subject.ifBlank { subjects.firstOrNull() ?: "Maths" },
                            onValueChange = {},
                            readOnly = true,
                            label = { Text("Subject", fontSize = 11.sp) },
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = subjMenuExpanded) },
                            modifier = Modifier.menuAnchor().fillMaxWidth().testTag("subject_dropdown_${task.id}"),
                            shape = RoundedCornerShape(10.dp)
                        )
                        ExposedDropdownMenu(
                            expanded = subjMenuExpanded,
                            onDismissRequest = { subjMenuExpanded = false },
                            modifier = Modifier.background(c.surface)
                        ) {
                            subjects.forEach { subj ->
                                DropdownMenuItem(
                                    text = { Text(subj, color = c.ink) },
                                    onClick = {
                                        onUpdate(task.copy(subject = subj))
                                        subjMenuExpanded = false
                                    }
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
