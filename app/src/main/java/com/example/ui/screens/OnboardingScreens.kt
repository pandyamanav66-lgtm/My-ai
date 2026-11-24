package com.example.ui.screens

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.Analytics
import com.example.data.RoutinePreset
import com.example.data.UserProfile
import com.example.ui.components.BrandLogoMark
import com.example.ui.components.LiquidGlassBackdrop
import com.example.ui.theme.InterFontFamily
import com.example.ui.theme.LocalDrstColors
import com.example.ui.theme.SpaceGroteskFontFamily
import kotlinx.coroutines.delay

@Composable
fun SplashScreen(onFinished: () -> Unit) {
    val c = LocalDrstColors.current
    val arcAnim = remember { Animatable(0f) }
    val checkAnim = remember { Animatable(0f) }
    val textAlpha = remember { Animatable(0f) }

    LaunchedEffect(Unit) {
        arcAnim.animateTo(1f, tween(480, easing = FastOutSlowInEasing))
        checkAnim.animateTo(1f, tween(320, easing = FastOutSlowInEasing))
        textAlpha.animateTo(1f, tween(280))
        delay(320L)
        onFinished()
    }

    LiquidGlassBackdrop {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .safeDrawingPadding(),
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.padding(horizontal = 32.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(116.dp)
                        .clip(CircleShape)
                        .background(c.glassFill)
                        .border(
                            1.3.dp,
                            Brush.linearGradient(listOf(c.glassBorderTop, c.prismCyan.copy(alpha = 0.7f))),
                            CircleShape
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    BrandLogoMark(
                        size = 76.dp,
                        arcProgress = arcAnim.value,
                        checkProgress = checkAnim.value,
                        arcColor = c.prismCyan,
                        checkColor = c.ink
                    )
                }
                Spacer(Modifier.height(22.dp))
                Text(
                    text = "Study Tracker",
                    textAlign = TextAlign.Center,
                    modifier = Modifier.alpha(textAlpha.value),
                    style = TextStyle(
                        fontFamily = SpaceGroteskFontFamily,
                        fontWeight = FontWeight.Bold,
                        fontSize = 28.sp,
                        letterSpacing = (-0.5).sp,
                        color = c.ink
                    )
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    text = "Daily Routine & Focus • by Manav Pandya",
                    modifier = Modifier.alpha(textAlpha.value),
                    style = TextStyle(
                        fontFamily = InterFontFamily,
                        fontWeight = FontWeight.Medium,
                        fontSize = 13.sp,
                        color = c.muted
                    )
                )
            }
        }
    }
}

@Composable
fun NamePromptScreen(
    isCreatingAdditional: Boolean,
    onCreateProfile: (String, RoutinePreset) -> Unit,
    onCancel: (() -> Unit)? = null
) {
    val c = LocalDrstColors.current
    var name by remember { mutableStateOf("") }
    var selectedPreset by remember { mutableStateOf(RoutinePreset.MINIMAL) }
    val cardShape = RoundedCornerShape(28.dp)
    val rimBrush = remember(c.glassBorderTop, c.glassBorderBottom) {
        Brush.linearGradient(listOf(c.glassBorderTop, c.glassBorderBottom))
    }

    LiquidGlassBackdrop {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .safeDrawingPadding()
                .padding(20.dp),
            contentAlignment = Alignment.Center
        ) {
            Column(
                modifier = Modifier
                    .widthIn(max = 420.dp)
                    .fillMaxWidth()
                    .clip(cardShape)
                    .background(c.glassFillStrong)
                    .border(1.1.dp, rimBrush, cardShape)
                    .verticalScroll(rememberScrollState())
                    .padding(22.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Box(
                    modifier = Modifier
                        .size(64.dp)
                        .clip(CircleShape)
                        .background(c.band)
                        .border(1.5.dp, c.gold.copy(alpha = 0.75f), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    BrandLogoMark(size = 40.dp, arcColor = c.gold, checkColor = c.onBand)
                }
                Spacer(Modifier.height(14.dp))
                Text(
                    text = if (isCreatingAdditional) "Create New Profile" else "Welcome!",
                    style = TextStyle(
                        fontFamily = SpaceGroteskFontFamily,
                        fontWeight = FontWeight.Bold,
                        fontSize = 24.sp,
                        color = c.ink
                    )
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = "Daily Routine & Study Tracker • by Manav Pandya",
                    textAlign = TextAlign.Center,
                    style = TextStyle(
                        fontFamily = InterFontFamily,
                        fontSize = 12.sp,
                        color = c.muted
                    )
                )
                Spacer(Modifier.height(14.dp))
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Your name") },
                    placeholder = { Text("e.g. Manav") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(
                        onDone = { if (name.isNotBlank()) onCreateProfile(name, selectedPreset) }
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("onboarding_name_input"),
                    shape = RoundedCornerShape(14.dp)
                )
                Spacer(Modifier.height(16.dp))

                Text(
                    text = "CHOOSE STARTER ROUTINE",
                    modifier = Modifier.fillMaxWidth(),
                    style = TextStyle(
                        fontFamily = InterFontFamily,
                        fontWeight = FontWeight.Bold,
                        fontSize = 10.sp,
                        letterSpacing = 0.9.sp,
                        color = c.muted
                    )
                )
                Spacer(Modifier.height(8.dp))

                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    RoutinePreset.entries.forEach { preset ->
                        val active = preset == selectedPreset
                        val presetShape = RoundedCornerShape(16.dp)
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(presetShape)
                                .background(
                                    if (active) c.prismCyan.copy(alpha = if (c.isDark) 0.18f else 0.14f)
                                    else c.glassFill
                                )
                                .border(
                                    width = if (active) 1.4.dp else 1.dp,
                                    color = if (active) c.prismCyan else c.line,
                                    shape = presetShape
                                )
                                .clickable { selectedPreset = preset }
                                .padding(horizontal = 14.dp, vertical = 11.dp)
                                .testTag("preset_option_${preset.name.lowercase()}"),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = preset.label,
                                    style = TextStyle(
                                        fontFamily = SpaceGroteskFontFamily,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 14.sp,
                                        color = c.ink
                                    )
                                )
                                Text(
                                    text = preset.subtitle,
                                    style = TextStyle(
                                        fontFamily = InterFontFamily,
                                        fontSize = 12.sp,
                                        color = c.muted
                                    )
                                )
                            }
                            if (active) {
                                Icon(
                                    imageVector = Icons.Rounded.CheckCircle,
                                    contentDescription = "Selected preset",
                                    tint = c.prismCyan,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                    }
                }

                Spacer(Modifier.height(18.dp))
                Button(
                    onClick = { onCreateProfile(name, selectedPreset) },
                    enabled = name.isNotBlank(),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(50.dp)
                        .testTag("onboarding_continue_button"),
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = c.band,
                        contentColor = c.onBand
                    )
                ) {
                    Text(
                        text = "Start Tracking",
                        style = TextStyle(
                            fontFamily = SpaceGroteskFontFamily,
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp
                        )
                    )
                }
                if (onCancel != null) {
                    Spacer(Modifier.height(8.dp))
                    TextButton(onClick = onCancel) {
                        Text("Back to profiles", color = c.muted)
                    }
                }
            }
        }
    }
}

@Composable
fun ProfilePickerScreen(
    profiles: List<UserProfile>,
    onSelectProfile: (String) -> Unit,
    onNewProfile: () -> Unit
) {
    val c = LocalDrstColors.current
    val rimBrush = remember(c.glassBorderTop, c.glassBorderBottom) {
        Brush.linearGradient(listOf(c.glassBorderTop, c.glassBorderBottom))
    }

    LiquidGlassBackdrop {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .safeDrawingPadding()
                .padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(Modifier.height(24.dp))
            Box(
                modifier = Modifier
                    .size(68.dp)
                    .clip(CircleShape)
                    .background(c.band)
                    .border(1.5.dp, c.gold.copy(alpha = 0.75f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                BrandLogoMark(size = 42.dp, arcColor = c.gold, checkColor = c.onBand)
            }
            Spacer(Modifier.height(14.dp))
            Text(
                text = "Choose Profile",
                style = TextStyle(
                    fontFamily = SpaceGroteskFontFamily,
                    fontWeight = FontWeight.Bold,
                    fontSize = 26.sp,
                    color = c.ink
                )
            )
            Text(
                text = "Daily Routine & Study Tracker • by Manav Pandya",
                style = TextStyle(
                    fontFamily = InterFontFamily,
                    fontSize = 12.sp,
                    color = c.muted
                )
            )
            Spacer(Modifier.height(24.dp))

            LazyColumn(
                modifier = Modifier.widthIn(max = 480.dp).fillMaxWidth().weight(1f),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(profiles, key = { it.id }) { profile ->
                    val streak = Analytics.calculateStreak(profile)
                    val xp = Analytics.totalXp(profile)
                    val shape = RoundedCornerShape(18.dp)
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(shape)
                            .background(c.glassFill)
                            .border(1.dp, rimBrush, shape)
                            .clickable { onSelectProfile(profile.id) }
                            .testTag("profile_pick_${profile.id}")
                    ) {
                        Row(
                            modifier = Modifier.padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(14.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(48.dp)
                                    .clip(CircleShape)
                                    .background(c.band),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = profile.initials,
                                    style = TextStyle(
                                        fontFamily = SpaceGroteskFontFamily,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 17.sp,
                                        color = c.gold
                                    )
                                )
                            }
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = profile.name,
                                    style = TextStyle(
                                        fontFamily = SpaceGroteskFontFamily,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 17.sp,
                                        color = c.ink
                                    )
                                )
                                Text(
                                    text = "$streak-day streak • $xp XP • ${profile.routine.size} tasks",
                                    style = TextStyle(
                                        fontFamily = InterFontFamily,
                                        fontSize = 12.sp,
                                        color = c.muted
                                    )
                                )
                            }
                            Icon(
                                imageVector = Icons.Rounded.ChevronRight,
                                contentDescription = "Open profile",
                                tint = c.muted
                            )
                        }
                    }
                }

                item {
                    Spacer(Modifier.height(6.dp))
                    OutlinedButton(
                        onClick = onNewProfile,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(52.dp)
                            .testTag("new_profile_button"),
                        shape = RoundedCornerShape(16.dp),
                        border = BorderStroke(1.5.dp, c.gold),
                        colors = ButtonDefaults.outlinedButtonColors(containerColor = c.glassFill)
                    ) {
                        Icon(Icons.Rounded.Add, contentDescription = null, tint = c.goldInk)
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = "New profile",
                            style = TextStyle(
                                fontFamily = SpaceGroteskFontFamily,
                                fontWeight = FontWeight.Bold,
                                fontSize = 15.sp,
                                color = c.ink
                            )
                        )
                    }
                }
            }
        }
    }
}
