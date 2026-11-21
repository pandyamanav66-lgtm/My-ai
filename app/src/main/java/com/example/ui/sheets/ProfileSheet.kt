package com.example.ui.sheets

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Logout
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.UserProfile
import com.example.ui.components.DrstCard
import com.example.ui.components.LiquidGlassBackdrop
import com.example.ui.theme.InterFontFamily
import com.example.ui.theme.LocalDrstColors
import com.example.ui.theme.SpaceGroteskFontFamily
import kotlinx.coroutines.delay

@Composable
fun ProfileSheet(
    profile: UserProfile,
    themeMode: String,
    onSaveDetails: (String, String, String, String) -> Unit,
    onSetThemeMode: (String) -> Unit,
    onSaveBackupFile: () -> Unit,
    onCopyBackupJson: () -> Unit,
    onRestoreBackupFile: () -> Unit,
    onRestoreBackupText: (String) -> Unit,
    onExportCsv: () -> Unit,
    onLogout: () -> Unit,
    onDeleteProfile: () -> Unit,
    onDismiss: () -> Unit
) {
    BackHandler(onBack = onDismiss)
    val c = LocalDrstColors.current

    var name by remember(profile.id) { mutableStateOf(profile.name) }
    var email by remember(profile.id) { mutableStateOf(profile.email) }
    var phone by remember(profile.id) { mutableStateOf(profile.phone) }
    var customDetails by remember(profile.id) { mutableStateOf(profile.customDetails) }

    var showPasteRestore by remember { mutableStateOf(false) }
    var pasteJson by remember { mutableStateOf("") }
    var confirmDelete by remember { mutableStateOf(false) }

    LaunchedEffect(confirmDelete) {
        if (confirmDelete) {
            delay(4000L)
            confirmDelete = false
        }
    }

    LiquidGlassBackdrop {
        Column(modifier = Modifier.fillMaxSize().safeDrawingPadding()) {
            // Top bar
            Surface(color = c.band.copy(alpha = 0.94f), modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Box(
                            modifier = Modifier
                                .size(42.dp)
                                .clip(CircleShape)
                                .background(c.gold.copy(alpha = 0.24f))
                                .border(1.dp, c.gold, CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = profile.initials,
                                style = TextStyle(fontFamily = SpaceGroteskFontFamily, fontWeight = FontWeight.Bold, fontSize = 16.sp, color = c.gold)
                            )
                        }
                        Column {
                            Text(
                                text = "Profile & Backup",
                                style = TextStyle(fontFamily = SpaceGroteskFontFamily, fontWeight = FontWeight.Bold, fontSize = 19.sp, color = c.onBand)
                            )
                            Text(
                                text = profile.name,
                                style = TextStyle(fontFamily = InterFontFamily, fontSize = 12.sp, color = c.onBandMuted)
                            )
                        }
                    }
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Rounded.Close, contentDescription = "Close profile", tint = c.onBand)
                    }
                }
            }

            LazyColumn(
                modifier = Modifier.weight(1f).fillMaxWidth(),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // 1. Personal Details
                item(key = "profile_details") {
                    DrstCard(title = "Student Profile") {
                        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            OutlinedTextField(
                                value = name,
                                onValueChange = { name = it },
                                label = { Text("Name") },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth().testTag("profile_name_input"),
                                shape = RoundedCornerShape(12.dp)
                            )
                            OutlinedTextField(
                                value = email,
                                onValueChange = { email = it },
                                label = { Text("Email (optional)") },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(12.dp)
                            )
                            OutlinedTextField(
                                value = phone,
                                onValueChange = { phone = it },
                                label = { Text("Phone (optional)") },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(12.dp)
                            )
                            OutlinedTextField(
                                value = customDetails,
                                onValueChange = { customDetails = it },
                                label = { Text("Target Exam / Class / Notes") },
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(12.dp),
                                minLines = 2
                            )
                            Button(
                                onClick = { onSaveDetails(name, email, phone, customDetails) },
                                enabled = name.isNotBlank(),
                                modifier = Modifier.fillMaxWidth().height(46.dp).testTag("save_profile_button"),
                                shape = RoundedCornerShape(12.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = c.band, contentColor = c.onBand)
                            ) {
                                Text("Save Details", fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }

                // 2. Theme Selector (Auto / Light / Dark)
                item(key = "theme_selector") {
                    DrstCard(title = "Liquid Glass Theme") {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            listOf("auto" to "Auto", "light" to "Light", "dark" to "Dark").forEach { (mode, label) ->
                                val active = themeMode == mode
                                OutlinedButton(
                                    onClick = { onSetThemeMode(mode) },
                                    modifier = Modifier.weight(1f).height(44.dp).testTag("theme_$mode"),
                                    shape = RoundedCornerShape(12.dp),
                                    border = BorderStroke(1.5.dp, if (active) c.gold else c.line),
                                    colors = ButtonDefaults.outlinedButtonColors(
                                        containerColor = if (active) c.band else c.surface2,
                                        contentColor = if (active) c.onBand else c.ink
                                    )
                                ) {
                                    Text(label, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }
                }

                // 3. Backup & Restore (drst.v2) + CSV Export
                item(key = "backup_restore") {
                    DrstCard(title = "Backup & Restore (drst.v2)") {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Button(
                                    onClick = onSaveBackupFile,
                                    modifier = Modifier.weight(1f).height(44.dp).testTag("save_backup_button"),
                                    shape = RoundedCornerShape(12.dp),
                                    colors = ButtonDefaults.buttonColors(containerColor = c.band, contentColor = c.onBand)
                                ) {
                                    Icon(Icons.Rounded.Download, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(Modifier.width(6.dp))
                                    Text("Save File", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                }
                                OutlinedButton(
                                    onClick = onCopyBackupJson,
                                    modifier = Modifier.weight(1f).height(44.dp).testTag("copy_backup_button"),
                                    shape = RoundedCornerShape(12.dp),
                                    border = BorderStroke(1.dp, c.line)
                                ) {
                                    Icon(Icons.Rounded.ContentCopy, contentDescription = null, modifier = Modifier.size(16.dp), tint = c.ink)
                                    Spacer(Modifier.width(6.dp))
                                    Text("Copy JSON", fontSize = 12.sp, color = c.ink, fontWeight = FontWeight.SemiBold)
                                }
                            }

                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                OutlinedButton(
                                    onClick = onRestoreBackupFile,
                                    modifier = Modifier.weight(1f).height(44.dp).testTag("restore_file_button"),
                                    shape = RoundedCornerShape(12.dp),
                                    border = BorderStroke(1.dp, c.gold)
                                ) {
                                    Icon(Icons.Rounded.UploadFile, contentDescription = null, modifier = Modifier.size(16.dp), tint = c.goldInk)
                                    Spacer(Modifier.width(6.dp))
                                    Text("Restore File", fontSize = 12.sp, color = c.ink, fontWeight = FontWeight.SemiBold)
                                }
                                OutlinedButton(
                                    onClick = { showPasteRestore = !showPasteRestore },
                                    modifier = Modifier.weight(1f).height(44.dp).testTag("restore_text_toggle"),
                                    shape = RoundedCornerShape(12.dp),
                                    border = BorderStroke(1.dp, c.line)
                                ) {
                                    Text("Paste JSON", fontSize = 12.sp, color = c.ink, fontWeight = FontWeight.SemiBold)
                                }
                            }

                            AnimatedVisibility(visible = showPasteRestore) {
                                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                    OutlinedTextField(
                                        value = pasteJson,
                                        onValueChange = { pasteJson = it },
                                        placeholder = { Text("Paste drst.v2 backup JSON here...", fontSize = 12.sp) },
                                        modifier = Modifier.fillMaxWidth().testTag("paste_backup_input"),
                                        minLines = 3,
                                        maxLines = 5,
                                        shape = RoundedCornerShape(12.dp)
                                    )
                                    Button(
                                        onClick = {
                                            if (pasteJson.isNotBlank()) {
                                                onRestoreBackupText(pasteJson)
                                                pasteJson = ""
                                                showPasteRestore = false
                                            }
                                        },
                                        modifier = Modifier.fillMaxWidth().height(42.dp).testTag("confirm_paste_restore"),
                                        colors = ButtonDefaults.buttonColors(containerColor = c.ok, contentColor = c.surface),
                                        shape = RoundedCornerShape(10.dp)
                                    ) {
                                        Text("Restore from Pasted JSON", fontWeight = FontWeight.Bold)
                                    }
                                }
                            }

                            OutlinedButton(
                                onClick = onExportCsv,
                                modifier = Modifier.fillMaxWidth().height(44.dp).testTag("export_csv_button"),
                                shape = RoundedCornerShape(12.dp),
                                border = BorderStroke(1.dp, c.line)
                            ) {
                                Icon(Icons.Rounded.TableChart, contentDescription = null, modifier = Modifier.size(16.dp), tint = c.ink)
                                Spacer(Modifier.width(6.dp))
                                Text("Export CSV Log", color = c.ink, fontWeight = FontWeight.SemiBold)
                            }
                        }
                    }
                }

                // 4. Local Profile Switch & Delete
                item(key = "account_actions") {
                    DrstCard(title = "Profile Actions") {
                        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            OutlinedButton(
                                onClick = onLogout,
                                modifier = Modifier.fillMaxWidth().height(46.dp).testTag("logout_button"),
                                shape = RoundedCornerShape(12.dp),
                                border = BorderStroke(1.dp, c.line)
                            ) {
                                Icon(Icons.AutoMirrored.Rounded.Logout, contentDescription = null, tint = c.ink, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(8.dp))
                                Text("Switch profile", color = c.ink, fontWeight = FontWeight.SemiBold)
                            }

                            Button(
                                onClick = {
                                    if (confirmDelete) {
                                        confirmDelete = false
                                        onDeleteProfile()
                                    } else {
                                        confirmDelete = true
                                    }
                                },
                                modifier = Modifier.fillMaxWidth().height(46.dp).testTag("delete_profile_button"),
                                shape = RoundedCornerShape(12.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = c.badBg,
                                    contentColor = c.badInk
                                )
                            ) {
                                Icon(Icons.Rounded.DeleteForever, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(8.dp))
                                Text(
                                    text = if (confirmDelete) "Tap again to permanently delete" else "Delete profile",
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
