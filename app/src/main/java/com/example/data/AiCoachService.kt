package com.example.data

import android.util.Log
import com.google.firebase.Firebase
import com.google.firebase.ai.ai
import com.google.firebase.ai.type.Schema
import com.google.firebase.ai.type.content
import com.google.firebase.ai.type.generationConfig
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.withTimeout
import org.json.JSONObject
import java.util.Locale

private const val TAG = "AiCoachService"
private const val DEFAULT_MODEL = "gemini-3.8-flash"

class AiCoachService {

    private val streamModel by lazy {
        Firebase.ai.generativeModel(modelName = DEFAULT_MODEL)
    }

    private val quickAddSchema = Schema.obj(
        properties = mapOf(
            "title" to Schema.string(description = "Concise task title"),
            "start" to Schema.string(description = "24-hour start time in HH:mm format, e.g. 19:00"),
            "end" to Schema.string(description = "24-hour end time in HH:mm format, e.g. 20:00"),
            "category" to Schema.enumeration(
                values = ALL_CATEGORIES,
                description = "Best matching routine category"
            ),
            "subject" to Schema.string(description = "Subject name if Study/Revision/Practice/Classes, else empty string")
        )
    )

    private val quickAddModel by lazy {
        Firebase.ai.generativeModel(
            modelName = DEFAULT_MODEL,
            generationConfig = generationConfig {
                responseMimeType = "application/json"
                responseSchema = quickAddSchema
            }
        )
    }

    private val buildRoutineSchema = Schema.obj(
        properties = mapOf(
            "summary" to Schema.string(description = "Brief 1-2 sentence explanation of the routine"),
            "tasks" to Schema.array(
                description = "Ordered daily routine tasks covering the day from wake-up to sleep",
                items = Schema.obj(
                    properties = mapOf(
                        "start" to Schema.string(description = "24-hour start time HH:mm"),
                        "end" to Schema.string(description = "24-hour end time HH:mm"),
                        "title" to Schema.string(description = "Clear task title"),
                        "category" to Schema.enumeration(values = ALL_CATEGORIES),
                        "subject" to Schema.string(description = "Subject name for study tasks, empty otherwise")
                    )
                )
            )
        )
    )

    private val buildRoutineModel by lazy {
        Firebase.ai.generativeModel(
            modelName = DEFAULT_MODEL,
            generationConfig = generationConfig {
                responseMimeType = "application/json"
                responseSchema = buildRoutineSchema
            }
        )
    }

    // 1. Multi-Turn Gemini Chatbot with System Instruction & Mode Selection
    fun streamChatReply(
        profile: UserProfile,
        history: List<ChatMessageItem>,
        userMessage: String,
        mode: AiChatMode
    ): Flow<String> = flow {
        val today = todayKey()
        val streak = Analytics.calculateStreak(profile, today)
        val todayStudy = Analytics.studyHoursForDay(profile, today)
        val examsText = profile.exams.joinToString(", ") {
            "${it.name} (${daysBetween(today, it.date)}d left)"
        }.ifBlank { "None added" }

        val depthHint = when (mode) {
            AiChatMode.DEEP -> "Think deeply and explain concepts step-by-step with formulas, examples, and clear logical breakdowns."
            AiChatMode.FAST -> "Keep answers ultra-crisp, punchy, and under 5 short bullet points."
            AiChatMode.BALANCED -> "Give balanced, encouraging, practical study advice with crisp formatting."
        }

        val sysPrompt = """
            You are an expert personal Study Mentor & Routine Coach inside "Daily Routine & Study Tracker" (by Manav Pandya).
            Always reply in ${profile.aiLanguage}.
            $depthHint
            Student context:
            - Name: ${profile.name}
            - Daily study goal: ${profile.dailyStudyGoalHours} h (Logged today: ${String.format(Locale.US, "%.1f", todayStudy)} h)
            - Current 75%+ streak: $streak days
            - Subjects: ${profile.subjects.joinToString(", ")}
            - Upcoming exams: $examsText
            - Open doubts: ${profile.doubts.count { !it.solved }}
        """.trimIndent()

        val chatModel = Firebase.ai.generativeModel(
            modelName = mode.modelName,
            systemInstruction = content { text(sysPrompt) }
        )

        val recentHistory = history.takeLast(14).map { msg ->
            content(role = if (msg.role == "model") "model" else "user") {
                text(msg.text)
            }
        }

        val chat = chatModel.startChat(history = recentHistory)
        withTimeout(60_000L) {
            chat.sendMessageStream(userMessage).collect { chunk ->
                val txt = chunk.text.orEmpty()
                if (txt.isNotEmpty()) emit(txt)
            }
        }
    }

    // 2. Audio Transcription (Multimodal audio bytes -> Gemini transcript)
    suspend fun transcribeAudio(audioBytes: ByteArray, language: String): String {
        val prompt = """
            Transcribe the spoken audio accurately and cleanly.
            Preferred language context: $language (preserve Hindi/Hinglish/English words naturally as spoken).
            Output ONLY the plain transcript text with proper punctuation, no timestamps or extra preamble.
        """.trimIndent()

        val response = withTimeout(60_000L) {
            streamModel.generateContent(
                content {
                    inlineData(audioBytes, "audio/mp4")
                    text(prompt)
                }
            )
        }
        return response.text?.trim().orEmpty().ifBlank {
            throw IllegalStateException("Could not detect clear speech in the recording. Try speaking closer to the mic.")
        }
    }

    // 3. Real-Time Voice Conversation Turn (Audio input -> Natural spoken reply)
    fun streamVoiceConversationTurn(
        profile: UserProfile,
        recentTurns: List<Pair<String, String>>, // role to text
        audioBytes: ByteArray
    ): Flow<String> = flow {
        val historyContext = if (recentTurns.isEmpty()) "" else {
            "Recent conversation:\n" + recentTurns.takeLast(6).joinToString("\n") { (r, t) ->
                "${if (r == "user") "Student" else "Coach"}: $t"
            }
        }
        val sysPrompt = """
            You are a warm, real-time Voice Study Coach having a live spoken conversation with ${profile.name}.
            Reply in natural conversational ${profile.aiLanguage} meant to be read aloud via text-to-speech.
            Keep your spoken reply crisp (2 to 4 short sentences, NO markdown bullets, NO asterisks, NO emojis).
            Student subjects: ${profile.subjects.joinToString(", ")}. Daily study goal: ${profile.dailyStudyGoalHours}h.
        """.trimIndent()

        val voiceModel = Firebase.ai.generativeModel(
            modelName = DEFAULT_MODEL,
            systemInstruction = content { text(sysPrompt) }
        )

        withTimeout(60_000L) {
            voiceModel.generateContentStream(
                content {
                    if (historyContext.isNotBlank()) text(historyContext)
                    inlineData(audioBytes, "audio/mp4")
                    text("Listen to what the student just said in the audio and respond conversationally.")
                }
            ).collect { chunk ->
                val txt = chunk.text.orEmpty()
                if (txt.isNotEmpty()) emit(txt)
            }
        }
    }

    // 4. Weekly Coach
    fun streamWeeklyCoach(profile: UserProfile, language: String): Flow<String> = flow {
        val today = todayKey()
        val last7 = (0..6).map { shiftDateKey(today, -it) }.reversed()
        val dayLines = last7.joinToString("\n") { d ->
            val pct = Analytics.completionPercent(profile, d)
            val hrs = Analytics.studyHoursForDay(profile, d)
            val rest = if (profile.days[d]?.isRestDay == true) " (Rest day)" else ""
            "- $d: $pct% routine done, ${String.format(Locale.US, "%.1f", hrs)}h study$rest"
        }
        val subjLines = Analytics.subjectHoursInDateRange(profile, last7)
            .entries.joinToString(", ") { "${it.key}: ${String.format(Locale.US, "%.1f", it.value)}h" }
            .ifBlank { "No subject study logged yet" }
        val examLines = profile.exams.joinToString(", ") {
            "${it.name} in ${daysBetween(today, it.date)} days"
        }.ifBlank { "None" }
        val openDoubts = profile.doubts.count { !it.solved }
        val streak = Analytics.calculateStreak(profile, today)

        val prompt = """
            You are an encouraging, sharp personal study coach inside "Daily Routine & Study Tracker".
            Respond strictly in $language. Keep the response concise, practical, structured with short bullet points.

            Student: ${profile.name}
            Daily study goal: ${profile.dailyStudyGoalHours} hours
            Current 75%+ streak: $streak days
            Last 7 days:
            $dayLines
            Subject hours (7d): $subjLines
            Upcoming exams: $examLines
            Open doubts: $openDoubts

            Provide:
            1. What went well this week
            2. One bottleneck to fix right now
            3. 3 concrete actions for tomorrow
        """.trimIndent()

        withTimeout(60_000L) {
            streamModel.generateContentStream(prompt).collect { chunk ->
                val txt = chunk.text.orEmpty()
                if (txt.isNotEmpty()) emit(txt)
            }
        }
    }

    // 5. Exam Planner
    fun streamExamPlan(
        profile: UserProfile,
        examName: String,
        daysLeft: Int,
        weakAreas: String,
        language: String
    ): Flow<String> = flow {
        val prompt = """
            You are a strategic exam preparation coach.
            Respond strictly in $language.
            Create a focused study & revision roadmap for:
            - Student: ${profile.name}
            - Exam: $examName
            - Days remaining: $daysLeft days
            - Daily study target: ${profile.dailyStudyGoalHours} hours
            - Subjects: ${profile.subjects.joinToString(", ")}
            - Weak areas: ${weakAreas.ifBlank { "Balanced full syllabus revision" }}

            Break into clear phases with daily hour splits and mock-test checkpoints.
        """.trimIndent()

        withTimeout(60_000L) {
            streamModel.generateContentStream(prompt).collect { chunk ->
                val txt = chunk.text.orEmpty()
                if (txt.isNotEmpty()) emit(txt)
            }
        }
    }

    // 6. Doubt Solver
    fun streamDoubtAnswer(subject: String, question: String, language: String): Flow<String> = flow {
        val prompt = """
            You are a patient, crystal-clear tutor solving a student's doubt.
            Respond strictly in $language.
            Subject: $subject
            Question: $question

            Explain step-by-step:
            1. Core concept simply
            2. Step-by-step solution
            3. Common mistake to avoid + quick memory trick
        """.trimIndent()

        withTimeout(60_000L) {
            streamModel.generateContentStream(prompt).collect { chunk ->
                val txt = chunk.text.orEmpty()
                if (txt.isNotEmpty()) emit(txt)
            }
        }
    }

    // 7. Quick Add (Structured JSON)
    suspend fun parseQuickAddTask(input: String, availableSubjects: List<String>): RoutineTask {
        val prompt = """
            Parse this natural-language routine task request into structured fields:
            "$input"

            Available subjects: ${availableSubjects.joinToString(", ")}
            Available categories: ${ALL_CATEGORIES.joinToString(", ")}
            Convert 12-hour times like "7 to 8 pm" into 24-hour "19:00" and "20:00".
            If no time is given, pick "18:00" to "19:00".
        """.trimIndent()

        val response = withTimeout(45_000L) {
            quickAddModel.generateContent(prompt)
        }
        val raw = response.text ?: throw IllegalStateException("Empty AI response for Quick Add.")
        val json = JSONObject(raw)
        val cat = json.optString("category", "Study").takeIf { it in ALL_CATEGORIES } ?: "Study"
        return RoutineTask(
            start = normalizeHHmm(json.optString("start", "19:00")),
            end = normalizeHHmm(json.optString("end", "20:00")),
            title = json.optString("title", input.trim()).ifBlank { input.trim() },
            category = cat,
            subject = if (cat in STUDY_CATEGORIES) json.optString("subject", "") else ""
        )
    }

    // 8. Build My Routine (Structured JSON)
    suspend fun generateRoutinePlan(
        profile: UserProfile,
        userGoals: String,
        language: String
    ): Pair<String, List<RoutineTask>> {
        val prompt = """
            Build a realistic full-day routine (12 to 18 blocks from wake-up to sleep) for a student.
            Write summary in $language; keep task titles crisp.
            Student: ${profile.name}
            Daily study target: ${profile.dailyStudyGoalHours} hours
            Subjects: ${profile.subjects.joinToString(", ")}
            Goals & constraints: $userGoals
            Categories allowed: ${ALL_CATEGORIES.joinToString(", ")}
        """.trimIndent()

        val response = withTimeout(60_000L) {
            buildRoutineModel.generateContent(prompt)
        }
        val raw = response.text ?: throw IllegalStateException("Empty AI response for routine builder.")
        val json = JSONObject(raw)
        val summary = json.optString("summary", "Custom routine generated for your goals.")
        val arr = json.getJSONArray("tasks")
        val tasks = mutableListOf<RoutineTask>()
        for (i in 0 until arr.length()) {
            val item = arr.getJSONObject(i)
            val cat = item.optString("category", "Study").takeIf { it in ALL_CATEGORIES } ?: "Study"
            tasks.add(
                RoutineTask(
                    start = normalizeHHmm(item.optString("start", "06:00")),
                    end = normalizeHHmm(item.optString("end", "07:00")),
                    title = item.optString("title", "Study block"),
                    category = cat,
                    subject = if (cat in STUDY_CATEGORIES) item.optString("subject", "") else ""
                )
            )
        }
        if (tasks.isEmpty()) throw IllegalStateException("AI generated an empty routine.")
        return summary to tasks.sortedBy { it.startMinutes }
    }

    private fun normalizeHHmm(raw: String): String {
        val parts = raw.trim().split(":")
        val h = parts.getOrNull(0)?.filter { it.isDigit() }?.toIntOrNull()?.coerceIn(0, 23) ?: 7
        val m = parts.getOrNull(1)?.filter { it.isDigit() }?.toIntOrNull()?.coerceIn(0, 59) ?: 0
        return String.format(Locale.US, "%02d:%02d", h, m)
    }

    companion object {
        fun friendlyError(e: Exception): String {
            Log.e(TAG, "AI request failed", e)
            val raw = e.localizedMessage?.takeIf { it.isNotBlank() }
            return when {
                e is TimeoutCancellationException ->
                    "AI timed out after 60 seconds. Check your internet and try again."
                e is CancellationException ->
                    "Stopped."
                raw != null && raw.contains("Default FirebaseApp is not initialized", ignoreCase = true) ->
                    "Firebase is not configured in this APK build (app/google-services.json was missing when built). Add the GOOGLE_SERVICES_JSON GitHub secret and rebuild the APK."
                raw != null ->
                    raw
                else ->
                    "Gemini couldn't complete this request right now. Please try again."
            }
        }
    }
}
