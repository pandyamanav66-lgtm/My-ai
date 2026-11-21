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

    // iOS 26 frosted translucent sapphire Liquid Glass top bar
    val headerGradient = remember(c.band, c.band2) {
        Brush.verticalGradient(
            colors = listOf(
                c.band.copy(alpha = 0.90f),
                c.band2.copy(alpha = 0.78f)
            )
        )
    }
    val bottomSpecularRim = remember {
        Brush.horizontalGradient(
            colors = listOf(Color(0x38FFFFFF), Color(0x88FFFFFF), Color(0x38FFFFFF))
        )
    }
    val glassCapsuleFill = remember {
        Brush.verticalGradient(
            colors = listOf(Color(0x36FFFFFF), Color(0x18FFFFFF))
        )
    }
    val glassCapsuleRim = remember {
        Brush.verticalGradient(
            colors = listOf(Color(0x78FFFFFF), Color(0x28FFFFFF))
        )
    }
    val headerShape = RoundedCornerShape(bottomStart = 30.dp, bottomEnd = 30.dp)

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(headerShape)
            .background(headerGradient)
            .border(
                width = 1.1.dp,
                brush = Brush.verticalGradient(
                    listOf(Color.Transparent, Color(0x26FFFFFF), Color(0x6EFFFFFF))
                ),
                shape = headerShape
            )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .widthIn(max = 960.dp)
                .align(Alignment.Center)
                .padding(horizontal = 16.dp)
                .padding(top = 10.dp, bottom = 14.dp)
        ) {
            // Clean, uncluttered top bar: Logo + Full Title + Edit Pencil + Profile Avatar
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                BrandLogoMark(size = 38.dp)

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "${greetingForTimeOfDay()}, ${profile.name}",
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        style = TextStyle(
                            fontFamily = InterFontFamily,
                            fontWeight = FontWeight.Medium,
                            fontSize = 11.sp,
                            color = c.onBandMuted
                        )
                    )
                    Text(
                        text = "Daily Routine & Study Tracker",
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        style = TextStyle(
                            fontFamily = SpaceGroteskFontFamily,
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp,
                            lineHeight = 20.sp,
                            letterSpacing = (-0.2).sp,
                            color = c.onBand
                        )
                    )
                    Text(
                        text = "by Manav Pandya",
                        style = TextStyle(
                            fontFamily = InterFontFamily,
                            fontWeight = FontWeight.Normal,
                            fontSize = 10.sp,
                            color = c.gold
                        )
                    )
                }

                // Edit Routine glass button (48dp touch target)
                Box(
                    modifier = Modifier
                        .size(46.dp)
                        .clip(CircleShape)
                        .background(glassCapsuleFill)
                        .border(1.dp, glassCapsuleRim, CircleShape)
                        .clickable(onClick = onOpenEditRoutine)
                        .testTag("edit_routine_button"),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Edit,
                        contentDescription = "Edit routine",
                        tint = c.onBand,
                        modifier = Modifier.size(19.dp)
                    )
                }

                // Initials Avatar glass button (opens Profile sheet)
                Box(
                    modifier = Modifier
                        .size(46.dp)
                        .clip(CircleShape)
                        .background(
                            Brush.verticalGradient(
                                listOf(c.gold.copy(alpha = 0.34f), c.gold.copy(alpha = 0.16f))
                            )
                        )
                        .border(
                            1.1.dp,
                            Brush.verticalGradient(listOf(Color(0xA6FFFFFF), c.gold.copy(alpha = 0.85f))),
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
                            color = c.gold
                        )
                    )
                }
            }

            Spacer(Modifier.height(12.dp))

            // Liquid Glass Date Navigator Pill Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .clip(CircleShape)
                        .background(glassCapsuleFill)
                        .border(1.dp, glassCapsuleRim, CircleShape)
                        .clickable(onClick = onPrevDay)
                        .testTag("prev_day_button"),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Rounded.KeyboardArrowLeft,
                        contentDescription = "Previous day",
                        tint = c.onBand
                    )
                }

                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(44.dp)
                        .clip(RoundedCornerShape(99.dp))
                        .background(glassCapsuleFill)
                        .border(1.dp, glassCapsuleRim, RoundedCornerShape(99.dp))
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
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 14.sp,
                            color = c.onBand,
                            fontFeatureSettings = "tnum"
                        )
                    )
                }

                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .clip(CircleShape)
                        .background(glassCapsuleFill)
                        .border(1.dp, glassCapsuleRim, CircleShape)
                        .clickable(onClick = onNextDay)
                        .testTag("next_day_button"),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Rounded.KeyboardArrowRight,
                        contentDescription = "Next day",
                        tint = c.onBand
                    )
                }

                Box(
                    modifier = Modifier
                        .height(44.dp)
                        .clip(RoundedCornerShape(99.dp))
                        .background(
                            if (isToday) {
                                Brush.verticalGradient(listOf(c.gold.copy(alpha = 0.50f), c.gold.copy(alpha = 0.34f)))
                            } else {
                                Brush.verticalGradient(listOf(c.gold, c.gold.copy(alpha = 0.85f)))
                            }
                        )
                        .border(1.dp, Color(0x88FFFFFF), RoundedCornerShape(99.dp))
                        .clickable(enabled = !isToday, onClick = onJumpToday)
                        .padding(horizontal = 14.dp)
                        .testTag("today_button"),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "Today",
                        style = TextStyle(
                            fontFamily = InterFontFamily,
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp,
                            color = c.band
                        )
                    )
                }
            }

            Spacer(Modifier.height(10.dp))

            // Streak pill + Rest day pill
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(99.dp))
                        .background(glassCapsuleFill)
                        .border(1.dp, glassCapsuleRim, RoundedCornerShape(99.dp))
                        .padding(horizontal = 11.dp, vertical = 5.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .clip(CircleShape)
                            .background(c.gold)
                    )
                    Text(
                        text = "$streakDays day${if (streakDays == 1) "" else "s"} streak (75%+)",
                        style = TextStyle(
                            fontFamily = InterFontFamily,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 11.sp,
                            color = c.onBand
                        )
                    )
                }

                if (isRestDay) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(99.dp))
                            .background(c.gold.copy(alpha = 0.25f))
                            .border(1.dp, c.gold, RoundedCornerShape(99.dp))
                            .padding(horizontal = 10.dp, vertical = 5.dp)
                    ) {
                        Text(
                            text = "Rest day",
                            style = TextStyle(
                                fontFamily = InterFontFamily,
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 11.sp,
                                color = c.gold
                            )
                        )
                    }
                }
            }
        }

        // Subtle luminous specular glint along bottom edge
        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth(0.55f)
                .height(1.dp)
                .background(bottomSpecularRim)
        )
    }
}
