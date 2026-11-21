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
import androidx.compose.material.icons.rounded.SportsEsports
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
    onOpenGame: () -> Unit,
    onOpenEditRoutine: () -> Unit,
    onOpenProfile: () -> Unit
) {
    val c = LocalDrstColors.current
    val context = LocalContext.current
    val isToday = selectedDate == todayKey()

    val headerGradient = remember(c.band, c.band2) {
        Brush.verticalGradient(
            colors = listOf(c.band.copy(alpha = 0.96f), c.band2.copy(alpha = 0.88f))
        )
    }
    val glassBtnBg = Color(0x22FFFFFF)
    val glassBtnBorder = Color(0x38FFFFFF)

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(bottomStart = 28.dp, bottomEnd = 28.dp))
            .background(headerGradient)
            .border(
                width = 1.dp,
                brush = Brush.verticalGradient(
                    listOf(Color.Transparent, Color(0x45FFFFFF))
                ),
                shape = RoundedCornerShape(bottomStart = 28.dp, bottomEnd = 28.dp)
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
            // Top bar: Logo + Greeting/Title + Game Arcade + Edit Pencil + Profile Avatar
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                BrandLogoMark(size = 36.dp)

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
                            fontSize = 17.sp,
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

                // Study Break Arcade Game button
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(glassBtnBg)
                        .border(1.dp, glassBtnBorder, RoundedCornerShape(14.dp))
                        .clickable(onClick = onOpenGame)
                        .testTag("open_game_button"),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Rounded.SportsEsports,
                        contentDescription = "Play Orbit Pulse study break game",
                        tint = c.gold,
                        modifier = Modifier.size(20.dp)
                    )
                }

                // Edit Routine pencil button
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(glassBtnBg)
                        .border(1.dp, glassBtnBorder, RoundedCornerShape(14.dp))
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

                // Initials Avatar (opens Profile sheet)
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(c.gold.copy(alpha = 0.24f))
                        .border(1.dp, c.gold.copy(alpha = 0.8f), RoundedCornerShape(14.dp))
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

            // Date Bar: Prev, Date Picker, Next, Today
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(glassBtnBg)
                        .border(1.dp, glassBtnBorder, RoundedCornerShape(14.dp))
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
                        .clip(RoundedCornerShape(14.dp))
                        .background(glassBtnBg)
                        .border(1.dp, glassBtnBorder, RoundedCornerShape(14.dp))
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
                        .clip(RoundedCornerShape(14.dp))
                        .background(glassBtnBg)
                        .border(1.dp, glassBtnBorder, RoundedCornerShape(14.dp))
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
                        .clip(RoundedCornerShape(14.dp))
                        .background(if (isToday) c.gold.copy(alpha = 0.45f) else c.gold)
                        .clickable(enabled = !isToday, onClick = onJumpToday)
                        .padding(horizontal = 14.dp)
                        .testTag("today_button"),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "Today",
                        style = TextStyle(
                            fontFamily = InterFontFamily,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 13.sp,
                            color = c.band
                        )
                    )
                }
            }

            Spacer(Modifier.height(10.dp))

            // Streak pill + Cloud status pill + Rest day pill
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(99.dp))
                        .background(glassBtnBg)
                        .border(1.dp, glassBtnBorder, RoundedCornerShape(99.dp))
                        .padding(horizontal = 10.dp, vertical = 4.dp),
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
                            .padding(horizontal = 9.dp, vertical = 4.dp)
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
    }
}
