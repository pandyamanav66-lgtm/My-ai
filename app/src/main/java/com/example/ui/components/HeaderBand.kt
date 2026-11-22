package com.example.ui.components

import android.app.DatePickerDialog
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.LocalFireDepartment
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.*
import com.example.ui.theme.InterFontFamily
import com.example.ui.theme.LocalDrstColors
import com.example.ui.theme.SpaceGroteskFontFamily
import java.util.Calendar
import java.util.Locale

/**
 * Ultra-clean, minimal floating Prism Liquid Glass Header.
 * No heavy dark solid slab — floats directly over the living aurora mesh with
 * compact greeting, streak flame pill, sleek date capsule, Edit & Profile glass buttons.
 */
@Composable
fun HeaderBand(
    profile: UserProfile,
    selectedDate: String,
    streakDays: Int,
    isRestDay: Boolean,
    onPrevDay: () -> Unit,
    onNextDay: () -> Unit,
    onPickDate: (String) -> Unit,
    onJumpToday: () -> Unit,
    onOpenEditRoutine: () -> Unit,
    onOpenProfile: () -> Unit
) {
    val c = LocalDrstColors.current
    val context = LocalContext.current
    val isToday = selectedDate == todayKey()

    val glassFill = remember(c.isDark) {
        if (c.isDark) {
            Brush.verticalGradient(listOf(Color(0x2EFFFFFF), Color(0x14FFFFFF)))
        } else {
            Brush.verticalGradient(listOf(Color(0xE6FFFFFF), Color(0xB8F3F8FF)))
        }
    }
    val glassRim = remember(c.isDark, c.glassBorderTop, c.glassBorderBottom) {
        Brush.linearGradient(listOf(c.glassBorderTop, c.glassBorderBottom))
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .widthIn(max = 960.dp)
            .padding(horizontal = 16.dp)
            .padding(top = 8.dp, bottom = 6.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        // Row 1: Minimal Brand Mark + Greeting/Title + Streak Flame + Edit + Profile Avatar
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(glassFill)
                    .border(1.1.dp, glassRim, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                BrandLogoMark(
                    size = 26.dp,
                    arcColor = c.prismCyan,
                    checkColor = c.ink
                )
            }

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "${greetingForTimeOfDay()}, ${profile.name}",
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    style = TextStyle(
                        fontFamily = InterFontFamily,
                        fontWeight = FontWeight.Medium,
                        fontSize = 11.sp,
                        color = c.muted
                    )
                )
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = "Study Tracker",
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        style = TextStyle(
                            fontFamily = SpaceGroteskFontFamily,
                            fontWeight = FontWeight.Bold,
                            fontSize = 18.sp,
                            letterSpacing = (-0.3).sp,
                            color = c.ink
                        )
                    )
                    // Playful compact inline streak flame pill
                    Row(
                        modifier = Modifier
                            .clip(RoundedCornerShape(99.dp))
                            .background(
                                Brush.horizontalGradient(
                                    listOf(c.gold.copy(alpha = 0.22f), c.prismCoral.copy(alpha = 0.18f))
                                )
                            )
                            .border(1.dp, c.gold.copy(alpha = 0.55f), RoundedCornerShape(99.dp))
                            .padding(horizontal = 7.dp, vertical = 2.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.LocalFireDepartment,
                            contentDescription = "Streak",
                            tint = c.gold,
                            modifier = Modifier.size(12.dp)
                        )
                        Text(
                            text = "${streakDays}d",
                            style = TextStyle(
                                fontFamily = SpaceGroteskFontFamily,
                                fontWeight = FontWeight.Bold,
                                fontSize = 11.sp,
                                color = c.goldInk
                            )
                        )
                    }
                    if (isRestDay) {
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(99.dp))
                                .background(c.prismViolet.copy(alpha = 0.20f))
                                .border(1.dp, c.prismViolet.copy(alpha = 0.5f), RoundedCornerShape(99.dp))
                                .padding(horizontal = 7.dp, vertical = 2.dp)
                        ) {
                            Text(
                                text = "Rest",
                                style = TextStyle(
                                    fontFamily = InterFontFamily,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 10.sp,
                                    color = c.prismViolet
                                )
                            )
                        }
                    }
                }
            }

            // Minimal Edit Routine glass button
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(glassFill)
                    .border(1.1.dp, glassRim, CircleShape)
                    .clickable(onClick = onOpenEditRoutine)
                    .testTag("edit_routine_button"),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Rounded.Edit,
                    contentDescription = "Edit routine",
                    tint = c.ink,
                    modifier = Modifier.size(19.dp)
                )
            }

            // Luminous Initials Profile Avatar glass button
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(
                        Brush.linearGradient(
                            listOf(c.prismCyan.copy(alpha = 0.28f), c.prismViolet.copy(alpha = 0.30f))
                        )
                    )
                    .border(
                        1.2.dp,
                        Brush.linearGradient(listOf(c.glassBorderTop, c.prismCyan.copy(alpha = 0.8f))),
                        CircleShape
                    )
                    .clickable(onClick = onOpenProfile)
                    .testTag("profile_avatar_button"),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = profile.initials,
                    style = TextStyle(
                        fontFamily = SpaceGroteskFontFamily,
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp,
                        color = c.ink
                    )
                )
            }
        }

        // Row 2: Slim Floating Liquid Glass Date Capsule
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(99.dp))
                .background(glassFill)
                .border(1.1.dp, glassRim, RoundedCornerShape(99.dp))
                .padding(horizontal = 4.dp, vertical = 3.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(38.dp)
                    .clip(CircleShape)
                    .clickable(onClick = onPrevDay)
                    .testTag("prev_day_button"),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Rounded.KeyboardArrowLeft,
                    contentDescription = "Previous day",
                    tint = c.ink,
                    modifier = Modifier.size(22.dp)
                )
            }

            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(38.dp)
                    .clip(RoundedCornerShape(99.dp))
                    .clickable {
                        val cal = keyToCalendar(selectedDate)
                        DatePickerDialog(
                            context,
                            { _, y, m, d ->
                                val key = String.format(Locale.US, "%04d-%02d-%02d", y, m + 1, d)
                                onPickDate(key)
                            },
                            cal.get(Calendar.YEAR),
                            cal.get(Calendar.MONTH),
                            cal.get(Calendar.DAY_OF_MONTH)
                        ).show()
                    }
                    .testTag("date_picker_button"),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = formatFriendlyDate(selectedDate),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    style = TextStyle(
                        fontFamily = SpaceGroteskFontFamily,
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp,
                        color = c.ink,
                        fontFeatureSettings = "tnum"
                    )
                )
            }

            Box(
                modifier = Modifier
                    .size(38.dp)
                    .clip(CircleShape)
                    .clickable(onClick = onNextDay)
                    .testTag("next_day_button"),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Rounded.KeyboardArrowRight,
                    contentDescription = "Next day",
                    tint = c.ink,
                    modifier = Modifier.size(22.dp)
                )
            }

            Box(
                modifier = Modifier
                    .height(36.dp)
                    .clip(RoundedCornerShape(99.dp))
                    .background(
                        if (isToday) {
                            Brush.horizontalGradient(
                                listOf(c.prismCyan.copy(alpha = 0.22f), c.prismViolet.copy(alpha = 0.20f))
                            )
                        } else {
                            Brush.horizontalGradient(listOf(c.prismCyan, c.prismViolet))
                        }
                    )
                    .border(
                        1.dp,
                        if (isToday) c.prismCyan.copy(alpha = 0.45f) else Color(0x99FFFFFF),
                        RoundedCornerShape(99.dp)
                    )
                    .clickable(enabled = !isToday, onClick = onJumpToday)
                    .padding(horizontal = 13.dp)
                    .testTag("today_button"),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "Today",
                    style = TextStyle(
                        fontFamily = SpaceGroteskFontFamily,
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.sp,
                        color = if (isToday) c.ink else Color.White
                    )
                )
            }
        }
    }
}
