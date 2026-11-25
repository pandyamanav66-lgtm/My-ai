package com.example.data

import android.content.Context
import android.util.Log
import org.json.JSONArray
import org.json.JSONObject
import java.util.Locale

private const val TAG = "DrstRepository"
private const val PREFS_NAME = "drst_storage"
private const val KEY_V2 = "drst.v2"
private const val KEY_V1 = "drst.v1"

class DrstRepository(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun loadStore(): DrstStore {
        val rawV2 = prefs.getString(KEY_V2, null)
        if (!rawV2.isNullOrBlank()) {
            return parseStoreJson(rawV2) ?: DrstStore()
        }
        // Migrate from drst.v1 if present
        val rawV1 = prefs.getString(KEY_V1, null)
        if (!rawV1.isNullOrBlank()) {
            val migrated = parseStoreJson(rawV1)
            if (migrated != null) {
                saveStore(migrated)
                return migrated
            }
        }
        return DrstStore()
    }

    fun saveStore(store: DrstStore) {
        val json = serializeStoreJson(store)
        prefs.edit().putString(KEY_V2, json).apply()
    }

    fun exportBackupJson(store: DrstStore): String = serializeStoreJson(store)

    fun importBackupJson(rawJson: String): Result<DrstStore> {
        return try {
            val parsed = parseStoreJson(rawJson.trim())
                ?: return Result.failure(IllegalArgumentException("Invalid drst.v2 backup JSON format."))
            if (parsed.profiles.isEmpty()) {
                return Result.failure(IllegalArgumentException("Backup contains no profiles."))
            }
            val activeId = parsed.activeProfileId
                ?.takeIf { id -> parsed.profiles.any { it.id == id } }
                ?: parsed.profiles.first().id
            val normalized = parsed.copy(version = 2, activeProfileId = activeId)
            saveStore(normalized)
            Result.success(normalized)
        } catch (e: Exception) {
            Log.e(TAG, "Backup import failed", e)
            Result.failure(e)
        }
    }

    fun exportCsv(profile: UserProfile): String {
        val sb = StringBuilder()
        sb.appendLine("Date,Completed Tasks,Total Tasks,Completion %,Study Hours,Focus Minutes,Rest Day")
        val allDates = (profile.days.keys + profile.focusSessions.map { it.date }).toSortedSet()
        for (date in allDates) {
            val done = Analytics.completedCount(profile, date)
            val total = profile.routine.size
            val pct = Analytics.completionPercent(profile, date)
            val studyHrs = Analytics.studyHoursForDay(profile, date)
            val focusMin = profile.focusSessions.filter { it.date == date }.sumOf { it.minutes }
            val rest = if (profile.days[date]?.isRestDay == true) "Yes" else "No"
            sb.appendLine(
                String.format(
                    Locale.US,
                    "%s,%d,%d,%d%%,%.2f,%d,%s",
                    date, done, total, pct, studyHrs, focusMin, rest
                )
            )
        }
        return sb.toString()
    }

    fun buildWeeklyReportText(profile: UserProfile, weekStartMonday: String): String {
        val days = (0..6).map { shiftDateKey(weekStartMonday, it) }
        val endDay = days.last()
        val avgPct = days.map { Analytics.completionPercent(profile, it) }.average().toInt()
        val totalStudy = days.sumOf { Analytics.studyHoursForDay(profile, it) }
        val totalDone = days.sumOf { Analytics.completedCount(profile, it) }
        val streakDays = days.count {
            Analytics.completionPercent(profile, it) >= 75 || profile.days[it]?.isRestDay == true
        }
        val sb = StringBuilder()
        sb.appendLine("Daily Routine & Study Tracker — Weekly Report")
        sb.appendLine("Student: ${profile.name}")
        sb.appendLine("Week: ${formatCompactDate(weekStartMonday)} – ${formatCompactDate(endDay)}")
        sb.appendLine("Avg Completion: $avgPct% | Study Hours: ${String.format(Locale.US, "%.1f", totalStudy)} h")
        sb.appendLine("Tasks Done: $totalDone | Strong Days (75%+): $streakDays/7")
        sb.appendLine("---")
        for (d in days) {
            val pct = Analytics.completionPercent(profile, d)
            val hrs = Analytics.studyHoursForDay(profile, d)
            val restTag = if (profile.days[d]?.isRestDay == true) " [Rest]" else ""
            sb.appendLine("${formatFriendlyDate(d)}: $pct% (${String.format(Locale.US, "%.1f", hrs)}h)$restTag")
        }
        val subjMap = Analytics.subjectHoursInDateRange(profile, days)
        if (subjMap.isNotEmpty()) {
            sb.appendLine("---")
            sb.appendLine("Hours by Subject:")
            subjMap.forEach { (s, h) ->
                sb.appendLine("• $s: ${String.format(Locale.US, "%.1f", h)} h")
            }
        }
        sb.appendLine("\nTracked with Daily Routine & Study Tracker by Manav Pandya")
        return sb.toString()
    }

    private fun serializeStoreJson(store: DrstStore): String {
        val root = JSONObject()
        root.put("version", 2)
        root.put("activeProfileId", store.activeProfileId ?: JSONObject.NULL)
        root.put("theme", store.theme)

        val profilesArr = JSONArray()
        for (p in store.profiles) {
            val po = JSONObject()
            po.put("id", p.id)
            po.put("name", p.name)
            po.put("email", p.email)
            po.put("phone", p.phone)
            po.put("customDetails", p.customDetails)
            po.put("dailyStudyGoalHours", p.dailyStudyGoalHours)
            po.put("aiLanguage", p.aiLanguage)
            po.put("createdAt", p.createdAt)

            val routineArr = JSONArray()
            for (t in p.routine) {
                routineArr.put(JSONObject().apply {
                    put("id", t.id)
                    put("start", t.start)
                    put("end", t.end)
                    put("title", t.title)
                    put("category", t.category)
                    put("subject", t.subject)
                })
            }
            po.put("routine", routineArr)

            po.put("subjects", JSONArray(p.subjects))

            val daysObj = JSONObject()
            for ((dateKey, log) in p.days) {
                val doObj = JSONObject()
                doObj.put("completedIds", JSONArray(log.completedIds.toList()))
                val notesObj = JSONObject()
                log.notes.forEach { (k, v) -> notesObj.put(k, v) }
                doObj.put("notes", notesObj)
                doObj.put("isRestDay", log.isRestDay)
                daysObj.put(dateKey, doObj)
            }
            po.put("days", daysObj)

            val focusArr = JSONArray()
            for (f in p.focusSessions) {
                focusArr.put(JSONObject().apply {
                    put("id", f.id)
                    put("date", f.date)
                    put("subject", f.subject)
                    put("minutes", f.minutes)
                    put("timestamp", f.timestamp)
                })
            }
            po.put("focusSessions", focusArr)

            val examsArr = JSONArray()
            for (e in p.exams) {
                examsArr.put(JSONObject().apply {
                    put("id", e.id)
                    put("name", e.name)
                    put("date", e.date)
                })
            }
            po.put("exams", examsArr)

            val doubtsArr = JSONArray()
            for (d in p.doubts) {
                doubtsArr.put(JSONObject().apply {
                    put("id", d.id)
                    put("subject", d.subject)
                    put("text", d.text)
                    put("solved", d.solved)
                    put("createdAt", d.createdAt)
                })
            }
            po.put("doubts", doubtsArr)
            po.put("unlockedBadges", JSONArray(p.unlockedBadges.toList()))
            po.put("bestGameScore", p.bestGameScore)
            po.put("bestGameWave", p.bestGameWave)
            po.put("gameBonusXp", p.gameBonusXp)

            val chatArr = JSONArray()
            for (cm in p.chatHistory.takeLast(60)) {
                chatArr.put(JSONObject().apply {
                    put("id", cm.id)
                    put("role", cm.role)
                    put("text", cm.text)
                    put("modelUsed", cm.modelUsed)
                    put("timestamp", cm.timestamp)
                })
            }
            po.put("chatHistory", chatArr)

            profilesArr.put(po)
        }
        root.put("profiles", profilesArr)
        return root.toString(2)
    }

    private fun parseStoreJson(raw: String): DrstStore? {
        return try {
            val root = JSONObject(raw)
            // Support v2 multi-profile schema OR legacy single-profile v1 schema
            if (root.has("profiles")) {
                val theme = root.optString("theme", "auto")
                val activeId = if (root.isNull("activeProfileId")) null else root.optString("activeProfileId", "").ifBlank { null }
                val arr = root.getJSONArray("profiles")
                val profiles = (0 until arr.length()).mapNotNull { i ->
                    parseProfileJson(arr.getJSONObject(i))
                }
                DrstStore(version = 2, activeProfileId = activeId, theme = theme, profiles = profiles)
            } else if (root.has("name") || root.has("routine") || root.has("days")) {
                val p = parseProfileJson(root) ?: return null
                DrstStore(version = 2, activeProfileId = p.id, theme = root.optString("theme", "auto"), profiles = listOf(p))
            } else {
                null
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to parse store JSON", e)
            null
        }
    }

    private fun parseProfileJson(po: JSONObject): UserProfile? {
        val name = po.optString("name", "").trim()
        if (name.isEmpty()) return null
        val id = po.optString("id", "").ifBlank { java.util.UUID.randomUUID().toString().take(8) }
        val routine = mutableListOf<RoutineTask>()
        val rArr = po.optJSONArray("routine")
        if (rArr != null && rArr.length() > 0) {
            for (i in 0 until rArr.length()) {
                val ro = rArr.getJSONObject(i)
                routine.add(
                    RoutineTask(
                        id = ro.optString("id", "t$i"),
                        start = ro.optString("start", "06:00"),
                        end = ro.optString("end", "07:00"),
                        title = ro.optString("title", ro.optString("name", "Task")),
                        category = ro.optString("category", "Study"),
                        subject = ro.optString("subject", "")
                    )
                )
            }
        } else {
            routine.addAll(defaultRoutine19Tasks())
        }

        val subjects = mutableListOf<String>()
        val sArr = po.optJSONArray("subjects")
        if (sArr != null && sArr.length() > 0) {
            for (i in 0 until sArr.length()) {
                val s = sArr.optString(i).trim()
                if (s.isNotEmpty()) subjects.add(s)
            }
        } else {
            subjects.addAll(DEFAULT_SUBJECTS)
        }

        val days = mutableMapOf<String, DayLog>()
        val daysObj = po.optJSONObject("days")
        if (daysObj != null) {
            val keys = daysObj.keys()
            while (keys.hasNext()) {
                val k = keys.next()
                val doObj = daysObj.optJSONObject(k) ?: continue
                val comp = mutableSetOf<String>()
                val cArr = doObj.optJSONArray("completedIds") ?: doObj.optJSONArray("done")
                if (cArr != null) {
                    for (i in 0 until cArr.length()) {
                        comp.add(cArr.optString(i))
                    }
                }
                val notes = mutableMapOf<String, String>()
                val nObj = doObj.optJSONObject("notes")
                if (nObj != null) {
                    val nk = nObj.keys()
                    while (nk.hasNext()) {
                        val tid = nk.next()
                        notes[tid] = nObj.optString(tid, "")
                    }
                }
                val rest = doObj.optBoolean("isRestDay", doObj.optBoolean("rest", false))
                days[k] = DayLog(completedIds = comp, notes = notes, isRestDay = rest)
            }
        }

        val focus = mutableListOf<FocusSession>()
        val fArr = po.optJSONArray("focusSessions")
        if (fArr != null) {
            for (i in 0 until fArr.length()) {
                val fo = fArr.getJSONObject(i)
                focus.add(
                    FocusSession(
                        id = fo.optString("id", "f$i"),
                        date = fo.optString("date", todayKey()),
                        subject = fo.optString("subject", "Maths"),
                        minutes = fo.optInt("minutes", 0),
                        timestamp = fo.optLong("timestamp", System.currentTimeMillis())
                    )
                )
            }
        }

        val exams = mutableListOf<ExamItem>()
        val eArr = po.optJSONArray("exams")
        if (eArr != null) {
            for (i in 0 until eArr.length()) {
                val eo = eArr.getJSONObject(i)
                exams.add(
                    ExamItem(
                        id = eo.optString("id", "e$i"),
                        name = eo.optString("name", "Exam"),
                        date = eo.optString("date", todayKey())
                    )
                )
            }
        }

        val doubts = mutableListOf<DoubtItem>()
        val dArr = po.optJSONArray("doubts")
        if (dArr != null) {
            for (i in 0 until dArr.length()) {
                val dob = dArr.getJSONObject(i)
                doubts.add(
                    DoubtItem(
                        id = dob.optString("id", "d$i"),
                        subject = dob.optString("subject", "General"),
                        text = dob.optString("text", ""),
                        solved = dob.optBoolean("solved", false),
                        createdAt = dob.optLong("createdAt", System.currentTimeMillis())
                    )
                )
            }
        }

        val badges = mutableSetOf<String>()
        val bArr = po.optJSONArray("unlockedBadges")
        if (bArr != null) {
            for (i in 0 until bArr.length()) {
                badges.add(bArr.optString(i))
            }
        }

        val chatHistory = mutableListOf<ChatMessageItem>()
        val chArr = po.optJSONArray("chatHistory")
        if (chArr != null) {
            for (i in 0 until chArr.length()) {
                val co = chArr.optJSONObject(i) ?: continue
                chatHistory.add(
                    ChatMessageItem(
                        id = co.optString("id", "c$i"),
                        role = co.optString("role", "user"),
                        text = co.optString("text", ""),
                        modelUsed = co.optString("modelUsed", "gpt-4o-mini"),
                        timestamp = co.optLong("timestamp", System.currentTimeMillis())
                    )
                )
            }
        }

        return UserProfile(
            id = id,
            name = name,
            email = po.optString("email", ""),
            phone = po.optString("phone", ""),
            customDetails = po.optString("customDetails", ""),
            dailyStudyGoalHours = po.optDouble("dailyStudyGoalHours", 6.0).coerceIn(0.5, 16.0),
            routine = routine,
            subjects = subjects,
            days = days,
            focusSessions = focus,
            exams = exams,
            doubts = doubts,
            unlockedBadges = badges,
            aiLanguage = po.optString("aiLanguage", "Hinglish"),
            bestGameScore = po.optInt("bestGameScore", 0),
            bestGameWave = po.optInt("bestGameWave", 0),
            gameBonusXp = po.optInt("gameBonusXp", 0),
            chatHistory = chatHistory,
            createdAt = po.optLong("createdAt", System.currentTimeMillis())
        )
    }
}
