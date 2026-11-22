package com.example.data

import java.util.Calendar
import java.util.Locale
import java.util.UUID
import kotlin.math.max
import kotlin.math.roundToInt

enum class AppTab(val label: String) {
    CHECKLIST("Today"),
    FOCUS("Focus"),
    DASHBOARD("Progress"),
    AI_COACH("AI Coach")
}

enum class ChecklistFilter(val label: String) {
    ALL("All"),
    PENDING("Pending"),
    DONE("Done"),
    STUDY("Study")
}

enum class DoubtFilter(val label: String) {
    OPEN("Open"),
    SOLVED("Solved"),
    ALL("All")
}

enum class AiChatMode(val label: String, val modelName: String, val description: String) {
    BALANCED("Balanced", "gemini-3.8-flash", "Smart everyday study mentoring"),
    FAST("Fast Lite", "gemini-3.5-flash-lite", "Ultra-fast quick answers"),
    DEEP("Deep Study", "gemini-3.8-flash", "Step-by-step deep reasoning")
}

val ALL_CATEGORIES = listOf(
    "Study",
    "Revision",
    "Practice",
    "Classes",
    "Morning",
    "Fitness",
    "Meals",
    "Break",
    "Reading",
    "Planning",
    "Personal",
    "Sleep"
)

val STUDY_CATEGORIES = setOf("Study", "Revision", "Practice", "Classes")

val DEFAULT_SUBJECTS = listOf(
    "Maths",
    "Physics",
    "Chemistry",
    "Biology",
    "English",
    "Computer Science",
    "General Studies"
)

val AI_LANGUAGES = listOf("Hinglish", "English", "Hindi")

data class RoutineTask(
    val id: String = UUID.randomUUID().toString().replace("-", "").take(10),
    val start: String,
    val end: String,
    val title: String,
    val category: String,
    val subject: String = ""
) {
    val startMinutes: Int get() = parseTimeToMinutes(start)
    val endMinutes: Int get() = parseTimeToMinutes(end)
    val durationMinutes: Int
        get() {
            val diff = endMinutes - startMinutes
            return if (diff > 0) diff else if (diff < 0) diff + 1440 else 30
        }
    val durationHours: Double get() = durationMinutes / 60.0
    val isStudyTask: Boolean get() = category in STUDY_CATEGORIES
}

data class DayLog(
    val completedIds: Set<String> = emptySet(),
    val notes: Map<String, String> = emptyMap(),
    val isRestDay: Boolean = false
)

data class FocusSession(
    val id: String = UUID.randomUUID().toString().replace("-", "").take(10),
    val date: String,
    val subject: String,
    val minutes: Int,
    val timestamp: Long = System.currentTimeMillis()
)

data class ExamItem(
    val id: String = UUID.randomUUID().toString().replace("-", "").take(10),
    val name: String,
    val date: String
)

data class DoubtItem(
    val id: String = UUID.randomUUID().toString().replace("-", "").take(10),
    val subject: String,
    val text: String,
    val solved: Boolean = false,
    val createdAt: Long = System.currentTimeMillis()
)

data class ChatMessageItem(
    val id: String = UUID.randomUUID().toString().replace("-", "").take(12),
    val role: String, // "user" | "model"
    val text: String,
    val modelUsed: String = "gemini-3.8-flash",
    val timestamp: Long = System.currentTimeMillis()
)

data class BadgeDef(
    val id: String,
    val title: String,
    val subtitle: String,
    val iconKey: String
)

val ALL_BADGES = listOf(
    BadgeDef("first_step", "First Step", "Tick your very first task", "check"),
    BadgeDef("perfect_day", "Perfect Day", "Hit 100% completion in a day", "star"),
    BadgeDef("streak_3", "3-Day Spark", "Keep a 3-day 75%+ streak", "flame"),
    BadgeDef("streak_7", "Week Warrior", "Keep a 7-day 75%+ streak", "trophy"),
    BadgeDef("focus_starter", "Deep Focus", "Complete your first focus timer", "timer"),
    BadgeDef("focus_120", "Flow State", "Log 120+ total focus minutes", "bolt"),
    BadgeDef("doubt_crusher", "Doubt Crusher", "Solve 3+ notebook doubts", "bulb"),
    BadgeDef("xp_1000", "Rising Scholar", "Earn 1,000+ total XP", "crown")
)

data class UserProfile(
    val id: String = UUID.randomUUID().toString().replace("-", "").take(10),
    val name: String,
    val email: String = "",
    val phone: String = "",
    val customDetails: String = "",
    val dailyStudyGoalHours: Double = 6.0,
    val routine: List<RoutineTask> = defaultRoutine19Tasks(),
    val subjects: List<String> = DEFAULT_SUBJECTS,
    val days: Map<String, DayLog> = emptyMap(),
    val focusSessions: List<FocusSession> = emptyList(),
    val exams: List<ExamItem> = emptyList(),
    val doubts: List<DoubtItem> = emptyList(),
    val unlockedBadges: Set<String> = emptySet(),
    val aiLanguage: String = "Hinglish",
    val bestGameScore: Int = 0,
    val bestGameWave: Int = 0,
    val gameBonusXp: Int = 0,
    val chatHistory: List<ChatMessageItem> = emptyList(),
    val createdAt: Long = System.currentTimeMillis()
) {
    val initials: String
        get() {
            val parts = name.trim().split(Regex("\\s+")).filter { it.isNotEmpty() }
            return when {
                parts.isEmpty() -> "?"
                parts.size == 1 -> parts[0].take(2).uppercase(Locale.US)
                else -> (parts[0].take(1) + parts.last().take(1)).uppercase(Locale.US)
            }
        }
}

data class DrstStore(
    val version: Int = 2,
    val activeProfileId: String? = null,
    val theme: String = "auto",
    val profiles: List<UserProfile> = emptyList()
) {
    val activeProfile: UserProfile?
        get() = profiles.firstOrNull { it.id == activeProfileId }
}

fun defaultRoutine19Tasks(): List<RoutineTask> = listOf(
    RoutineTask("t01", "05:30", "06:00", "Wake up, hydrate & fresh up", "Morning"),
    RoutineTask("t02", "06:00", "06:30", "Morning walk, stretching & breathwork", "Fitness"),
    RoutineTask("t03", "06:30", "08:00", "Deep study block 1 — Core concepts", "Study", "Maths"),
    RoutineTask("t04", "08:00", "08:30", "Healthy breakfast & light break", "Meals"),
    RoutineTask("t05", "08:30", "10:00", "Deep study block 2 — Problem solving", "Study", "Physics"),
    RoutineTask("t06", "10:00", "10:15", "Short recharge & eye rest", "Break"),
    RoutineTask("t07", "10:15", "11:45", "Lectures / Classes & active notes", "Classes", "Chemistry"),
    RoutineTask("t08", "11:45", "12:30", "Topic revision & formula recall", "Revision", "Maths"),
    RoutineTask("t09", "12:30", "13:15", "Lunch & mindful screen-free rest", "Meals"),
    RoutineTask("t10", "13:15", "13:45", "Power nap / quiet relaxation", "Break"),
    RoutineTask("t11", "13:45", "15:15", "Deep study block 3 — New chapter", "Study", "Chemistry"),
    RoutineTask("t12", "15:15", "16:30", "Timed practice questions & PYQs", "Practice", "Physics"),
    RoutineTask("t13", "16:30", "17:00", "Evening snack & short walk", "Break"),
    RoutineTask("t14", "17:00", "18:00", "Workout / outdoor sports", "Fitness"),
    RoutineTask("t15", "18:00", "19:30", "Deep study block 4 — Weak areas", "Study", "English"),
    RoutineTask("t16", "19:30", "20:15", "Dinner with family", "Meals"),
    RoutineTask("t17", "20:15", "21:15", "Mistake review & daily revision", "Revision", "General Studies"),
    RoutineTask("t18", "21:15", "21:45", "Reading & next-day planning", "Planning"),
    RoutineTask("t19", "21:45", "22:30", "Wind down, digital sunset & sleep prep", "Sleep")
)

fun parseTimeToMinutes(timeStr: String): Int {
    val parts = timeStr.trim().split(":")
    val h = parts.getOrNull(0)?.toIntOrNull()?.coerceIn(0, 23) ?: 0
    val m = parts.getOrNull(1)?.toIntOrNull()?.coerceIn(0, 59) ?: 0
    return h * 60 + m
}

fun formatTime12h(timeStr: String): String {
    val total = parseTimeToMinutes(timeStr)
    val h24 = total / 60
    val m = total % 60
    val ampm = if (h24 < 12) "AM" else "PM"
    val h12 = when {
        h24 == 0 -> 12
        h24 > 12 -> h24 - 12
        else -> h24
    }
    return String.format(Locale.US, "%d:%02d %s", h12, m, ampm)
}

fun formatDuration(minutes: Int): String {
    val h = minutes / 60
    val m = minutes % 60
    return when {
        h > 0 && m > 0 -> "${h}h ${m}m"
        h > 0 -> "${h}h"
        else -> "${m}m"
    }
}

fun todayKey(): String = calendarToKey(Calendar.getInstance())

fun calendarToKey(cal: Calendar): String = String.format(
    Locale.US,
    "%04d-%02d-%02d",
    cal.get(Calendar.YEAR),
    cal.get(Calendar.MONTH) + 1,
    cal.get(Calendar.DAY_OF_MONTH)
)

fun keyToCalendar(key: String): Calendar {
    val parts = key.split("-")
    val y = parts.getOrNull(0)?.toIntOrNull() ?: Calendar.getInstance().get(Calendar.YEAR)
    val m = (parts.getOrNull(1)?.toIntOrNull() ?: 1) - 1
    val d = parts.getOrNull(2)?.toIntOrNull() ?: 1
    return Calendar.getInstance().apply {
        set(Calendar.YEAR, y)
        set(Calendar.MONTH, m.coerceIn(0, 11))
        set(Calendar.DAY_OF_MONTH, d.coerceIn(1, 31))
        set(Calendar.HOUR_OF_DAY, 12)
        set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
    }
}

fun shiftDateKey(key: String, deltaDays: Int): String {
    val cal = keyToCalendar(key)
    cal.add(Calendar.DAY_OF_YEAR, deltaDays)
    return calendarToKey(cal)
}

fun daysBetween(fromKey: String, toKey: String): Int {
    val c1 = keyToCalendar(fromKey)
    val c2 = keyToCalendar(toKey)
    return ((c2.timeInMillis - c1.timeInMillis) / 86_400_000.0).roundToInt()
}

fun formatFriendlyDate(key: String): String {
    val cal = keyToCalendar(key)
    val dayName = cal.getDisplayName(Calendar.DAY_OF_WEEK, Calendar.SHORT, Locale.US) ?: ""
    val monthName = cal.getDisplayName(Calendar.MONTH, Calendar.SHORT, Locale.US) ?: ""
    return "$dayName, ${cal.get(Calendar.DAY_OF_MONTH)} $monthName ${cal.get(Calendar.YEAR)}"
}

fun formatCompactDate(key: String): String {
    val cal = keyToCalendar(key)
    val monthName = cal.getDisplayName(Calendar.MONTH, Calendar.SHORT, Locale.US) ?: ""
    return "${cal.get(Calendar.DAY_OF_MONTH)} $monthName"
}

fun startOfWeekMonday(key: String): String {
    val cal = keyToCalendar(key)
    val dow = cal.get(Calendar.DAY_OF_WEEK)
    val offset = if (dow == Calendar.SUNDAY) -6 else Calendar.MONDAY - dow
    cal.add(Calendar.DAY_OF_YEAR, offset)
    return calendarToKey(cal)
}

fun greetingForTimeOfDay(): String {
    val hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
    return when {
        hour < 12 -> "Good morning"
        hour < 17 -> "Good afternoon"
        else -> "Good evening"
    }
}

fun currentMinutesOfDay(): Int {
    val cal = Calendar.getInstance()
    return cal.get(Calendar.HOUR_OF_DAY) * 60 + cal.get(Calendar.MINUTE)
}

object Analytics {
    fun completionPercent(profile: UserProfile, dateKey: String): Int {
        val total = profile.routine.size
        if (total == 0) return 0
        val log = profile.days[dateKey] ?: return 0
        val done = profile.routine.count { it.id in log.completedIds }
        return ((done * 100.0) / total).roundToInt().coerceIn(0, 100)
    }

    fun completedCount(profile: UserProfile, dateKey: String): Int {
        val log = profile.days[dateKey] ?: return 0
        return profile.routine.count { it.id in log.completedIds }
    }

    fun studyHoursForDay(profile: UserProfile, dateKey: String): Double {
        val log = profile.days[dateKey]
        val routineHrs = if (log != null) {
            profile.routine
                .filter { it.isStudyTask && it.id in log.completedIds }
                .sumOf { it.durationHours }
        } else 0.0
        val focusHrs = profile.focusSessions
            .filter { it.date == dateKey }
            .sumOf { it.minutes } / 60.0
        return routineHrs + focusHrs
    }

    fun calculateStreak(profile: UserProfile, upToDate: String = todayKey()): Int {
        var streak = 0
        var cursor = upToDate
        val todayPct = completionPercent(profile, cursor)
        val todayRest = profile.days[cursor]?.isRestDay == true
        if (todayPct < 75 && !todayRest) {
            cursor = shiftDateKey(cursor, -1)
        }
        repeat(365) {
            val log = profile.days[cursor]
            val pct = completionPercent(profile, cursor)
            if (pct >= 75 || log?.isRestDay == true) {
                streak++
                cursor = shiftDateKey(cursor, -1)
            } else {
                return streak
            }
        }
        return streak
    }

    fun totalXp(profile: UserProfile): Int {
        val tickedTasks = profile.days.values.sumOf { it.completedIds.size }
        val focusMinutes = profile.focusSessions.sumOf { it.minutes }
        return tickedTasks * 10 + focusMinutes * 2 + profile.gameBonusXp
    }

    fun levelInfo(xp: Int): Triple<Int, String, Float> {
        val level = (xp / 250) + 1
        val progress = (xp % 250) / 250f
        val title = when {
            level >= 15 -> "Grandmaster"
            level >= 10 -> "Study Legend"
            level >= 7 -> "Strategist"
            level >= 5 -> "Focused Scholar"
            level >= 3 -> "Consistent Learner"
            level >= 2 -> "Rising Seeker"
            else -> "Starter"
        }
        return Triple(level, title, progress)
    }

    fun evaluateUnlockedBadges(profile: UserProfile): Set<String> {
        val earned = profile.unlockedBadges.toMutableSet()
        val totalDone = profile.days.values.sumOf { it.completedIds.size }
        if (totalDone >= 1) earned.add("first_step")
        if (profile.days.keys.any { completionPercent(profile, it) >= 100 }) earned.add("perfect_day")
        val streak = calculateStreak(profile)
        if (streak >= 3) earned.add("streak_3")
        if (streak >= 7) earned.add("streak_7")
        if (profile.focusSessions.isNotEmpty()) earned.add("focus_starter")
        if (profile.focusSessions.sumOf { it.minutes } >= 120) earned.add("focus_120")
        if (profile.doubts.count { it.solved } >= 3) earned.add("doubt_crusher")
        if (totalXp(profile) >= 1000) earned.add("xp_1000")
        return earned
    }

    fun subjectHoursInDateRange(profile: UserProfile, dateKeys: List<String>): Map<String, Double> {
        val result = linkedMapOf<String, Double>()
        val keySet = dateKeys.toSet()
        for (dateKey in dateKeys) {
            val log = profile.days[dateKey] ?: continue
            profile.routine
                .filter { it.isStudyTask && it.id in log.completedIds }
                .forEach { task ->
                    val subj = task.subject.ifBlank { task.category }
                    result[subj] = (result[subj] ?: 0.0) + task.durationHours
                }
        }
        profile.focusSessions
            .filter { it.date in keySet }
            .forEach { session ->
                val subj = session.subject.ifBlank { "General" }
                result[subj] = (result[subj] ?: 0.0) + (session.minutes / 60.0)
            }
        return result.entries.sortedByDescending { it.value }.associate { it.key to it.value }
    }

    fun nextUpcomingExam(profile: UserProfile, fromDate: String = todayKey()): Pair<ExamItem, Int>? {
        return profile.exams
            .map { it to daysBetween(fromDate, it.date) }
            .filter { it.second >= 0 }
            .minByOrNull { it.second }
    }

    fun motivationalLine(pct: Int, isRestDay: Boolean, studyHrs: Double, goalHrs: Double): String {
        if (isRestDay) return "Rest day active — recharge fully while keeping your streak safe."
        return when {
            pct == 100 -> "100% complete! Outstanding discipline today."
            pct >= 75 -> "Streak secured for today! Finish strong on your remaining tasks."
            studyHrs >= goalHrs && goalHrs > 0 -> "Daily study goal reached! Keep checking off the routine."
            pct >= 40 -> "Solid momentum — stay locked in on your next block."
            pct > 0 -> "Great start! Small wins compound into unstoppable days."
            else -> "Fresh canvas today — start with your next scheduled block."
        }
    }

    fun localSmartInsights(profile: UserProfile, today: String = todayKey()): List<Pair<String, String>> {
        val insights = mutableListOf<Pair<String, String>>()
        val last7 = (0..6).map { shiftDateKey(today, -it) }
        val avgPct = last7.map { completionPercent(profile, it) }.average().roundToInt()
        val streak = calculateStreak(profile, today)
        val todayStudy = studyHoursForDay(profile, today)
        val remainingStudy = max(0.0, profile.dailyStudyGoalHours - todayStudy)

        insights.add(
            "7-Day Consistency" to "Averaging $avgPct% completion across the last 7 days with a $streak-day streak (75%+)."
        )
        if (remainingStudy > 0.05) {
            insights.add(
                "Today's Study Target" to String.format(
                    Locale.US,
                    "%.1f h logged of %.1f h goal — %.1f h left to hit gold.",
                    todayStudy, profile.dailyStudyGoalHours, remainingStudy
                )
            )
        } else {
            insights.add(
                "Goal Achieved" to String.format(
                    Locale.US,
                    "You hit %.1f h today (goal %.1f h). Great focus!",
                    todayStudy, profile.dailyStudyGoalHours
                )
            )
        }
        val subjHrs = subjectHoursInDateRange(profile, last7)
        if (subjHrs.isNotEmpty()) {
            val top = subjHrs.entries.first()
            insights.add(
                "Top Subject (7d)" to String.format(Locale.US, "%s leads at %.1f h.", top.key, top.value)
            )
        }
        val openDoubts = profile.doubts.count { !it.solved }
        if (openDoubts > 0) {
            insights.add("Open Doubts" to "$openDoubts open doubt${if (openDoubts == 1) "" else "s"} waiting in your notebook.")
        }
        nextUpcomingExam(profile, today)?.let { (exam, days) ->
            insights.add("Upcoming Exam" to "${exam.name} is in $days day${if (days == 1) "" else "s"}.")
        }
        return insights
    }
}
