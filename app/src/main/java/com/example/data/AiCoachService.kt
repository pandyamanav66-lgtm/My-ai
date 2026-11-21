package com.example.data

import android.util.Log
import com.google.firebase.Firebase
import com.google.firebase.ai.ai
import com.google.firebase.ai.type.Content
import com.google.firebase.ai.type.GenerateContentResponse
import com.google.firebase.ai.type.Schema
import com.google.firebase.ai.type.content
import com.google.firebase.ai.type.generationConfig
import com.google.firebase.appcheck.FirebaseAppCheck
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.json.JSONObject
import java.util.Locale
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

private const val TAG = "AiCoachService"
private const val DEFAULT_MODEL = "gemini-3.8-flash"
private const val FAST_LITE_MODEL = "gemini-3.5-flash-lite"
private const val APP_CHECK_TIMEOUT_MS = 20_000L
private const val FIRST_CHUNK_WATCHDOG_MS = 16_000L
private const val UNARY_PRIMARY_TIMEOUT_MS = 26_000L
private const val AI_TIMEOUT_MS = 60_000L

class AiCoachService {

    private val streamModel by lazy {
        Firebase.ai.generativeModel(
            modelName = DEFAULT_MODEL,
            generationConfig = generationConfig {
                maxOutputTokens = 1400
            }
        )
    }

    private val fastLiteModel by lazy {
        Firebase.ai.generativeModel(
            modelName = FAST_LITE_MODEL,
            generationConfig = generationConfig {
                maxOutputTokens = 1200
            }
        )
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
                maxOutputTokens = 400
            }
        )
    }

    private val quickAddLiteModel by lazy {
        Firebase.ai.generativeModel(
            modelName = FAST_LITE_MODEL,
            generationConfig = generationConfig {
                responseMimeType = "application/json"
                responseSchema = quickAddSchema
                maxOutputTokens = 400
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
                maxOutputTokens = 2048
            }
        )
    }

    private val buildRoutineLiteModel by lazy {
        Firebase.ai.generativeModel(
            modelName = FAST_LITE_MODEL,
            generationConfig = generationConfig {
                responseMimeType = "application/json"
                responseSchema = buildRoutineSchema
                maxOutputTokens = 2048
            }
        )
    }

    /**
     * Preflight App Check attestation with a bounded timeout so hangs or attestation failures
     * surface immediately instead of leaving the UI spinning indefinitely.
     */
    private suspend fun ensureAppCheckReady() {
        try {
            withTimeout(APP_CHECK_TIMEOUT_MS) {
                suspendCancellableCoroutine<Unit> { cont ->
                    FirebaseAppCheck.getInstance()
                        .getAppCheckToken(false)
                        .addOnSuccessListener { token ->
                            if (cont.isActive) {
                                if (token.token.isNullOrBlank()) {
                                    cont.resumeWithException(
                                        IllegalStateException("App Check returned an empty attestation token.")
                                    )
                                } else {
                                    cont.resume(Unit)
                                }
                            }
                        }
                        .addOnFailureListener { exc ->
                            if (cont.isActive) cont.resumeWithException(exc)
                        }
                }
            }
        } catch (t: TimeoutCancellationException) {
            throw IllegalStateException(
                "App Check verification timed out (20s). Check internet/Google Play Services and retry.",
                t
            )
        } catch (ce: CancellationException) {
            throw ce
        } catch (e: Exception) {
            throw IllegalStateException(
                "App Check verification failed: ${e.localizedMessage ?: e.javaClass.simpleName}",
                e
            )
        }
    }

    /**
     * Resilient streaming pipeline with a 16-second first-chunk watchdog:
     * - Streams smoothly from the primary model when chunks flow normally.
     * - If SSE streaming stalls on mobile carriers or the primary model takes >16s before
     *   emitting its first token (or throws a transient error before outputting text),
     *   cancels the stalled stream and immediately executes a fast unary fallback call on
     *   gemini-3.5-flash-lite within the 60-second total budget.
     */
    private fun streamWithFastFallback(
        primaryStream: () -> Flow<GenerateContentResponse>,
        fallbackUnary: suspend () -> GenerateContentResponse
    ): Flow<String> = channelFlow {
        ensureAppCheckReady()

        var receivedAnyChunk = false
        var primaryFailed: Throwable? = null

        withTimeout(AI_TIMEOUT_MS) {
            val streamJob = launch {
                try {
                    primaryStream().collect { chunk ->
                        val txt = chunk.text.orEmpty()
                        if (txt.isNotEmpty()) {
                            receivedAnyChunk = true
                            send(txt)
                        }
                    }
                } catch (ce: CancellationException) {
                    throw ce
                } catch (e: Throwable) {
                    primaryFailed = e
                    Log.w(TAG, "Primary stream interrupted (chunks=$receivedAnyChunk): ${e.localizedMessage}", e)
                }
            }

            val watchdogJob = launch {
                delay(FIRST_CHUNK_WATCHDOG_MS)
                if (!receivedAnyChunk && streamJob.isActive) {
                    Log.w(TAG, "No first chunk in ${FIRST_CHUNK_WATCHDOG_MS}ms; switching to fast unary fallback.")
                    streamJob.cancel()
                }
            }

            streamJob.join()
            watchdogJob.cancel()

            if (!receivedAnyChunk) {
                try {
                    val fallbackResp = fallbackUnary()
                    val fallbackText = fallbackResp.text?.trim().orEmpty()
                    if (fallbackText.isNotEmpty()) {
                        send(fallbackText)
                    } else if (primaryFailed != null) {
                        throw primaryFailed!!
                    } else {
                        throw IllegalStateException("Gemini returned an empty response. Please try again.")
                    }
                } catch (ce: CancellationException) {
                    throw ce
                } catch (fallbackErr: Exception) {
                    Log.e(TAG, "Fallback unary call failed", fallbackErr)
                    throw (primaryFailed as? Exception) ?: fallbackErr
                }
            }
        }
    }.flowOn(Dispatchers.IO)

    /**
     * Resilient unary generation with primary 26s timeout and automatic fallback to
     * gemini-3.5-flash-lite inside a 60s total budget.
     */
    private suspend fun unaryWithFastFallback(
        primaryCall: suspend () -> GenerateContentResponse,
        fallbackCall: suspend () -> GenerateContentResponse
    ): GenerateContentResponse = withContext(Dispatchers.IO) {
        ensureAppCheckReady()
        withTimeout(AI_TIMEOUT_MS) {
            try {
                withTimeout(UNARY_PRIMARY_TIMEOUT_MS) {
                    primaryCall()
                }
            } catch (t: TimeoutCancellationException) {
                Log.w(TAG, "Primary unary call timed out after ${UNARY_PRIMARY_TIMEOUT_MS}ms; retrying on $FAST_LITE_MODEL.")
                fallbackCall()
            } catch (ce: CancellationException) {
                throw ce
            } catch (e: Exception) {
                Log.w(TAG, "Primary unary call failed (${e.localizedMessage}); retrying on $FAST_LITE_MODEL.")
                try {
                    fallbackCall()
                } catch (ce: CancellationException) {
                    throw ce
                } catch (_: Exception) {
                    throw e
                }
            }
        }
    }

    internal fun buildValidChatHistory(history: List<ChatMessageItem>): List<Content> {
        val valid = mutableListOf<Content>()
        var expectedRole = "user"
        for (msg in history.takeLast(20)) {
            val role = if (msg.role == "model") "model" else "user"
            val body = msg.text.trim()
            if (body.isEmpty()) continue
            if (role == expectedRole) {
                valid.add(content(role = role) { text(body) })
                expectedRole = if (expectedRole == "user") "model" else "user"
            } else if (role == "user" && valid.isNotEmpty() && expectedRole == "model") {
                // Replace orphaned user message from an earlier failed send
                valid[valid.lastIndex] = content(role = "user") { text(body) }
            }
        }
        if (valid.isNotEmpty() && expectedRole == "model") {
            valid.removeAt(valid.lastIndex)
        }
        return valid
    }

    // 1. Multi-Turn Gemini Chatbot with System Instruction & Mode Selection
    fun streamChatReply(
        profile: UserProfile,
        history: List<ChatMessageItem>,
        userMessage: String,
        mode: AiChatMode
    ): Flow<String> {
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
        val tokenCap = when (mode) {
            AiChatMode.FAST -> 600
            AiChatMode.BALANCED -> 1100
            AiChatMode.DEEP -> 1800
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

        val recentHistory = buildValidChatHistory(history)

        return streamWithFastFallback(
            primaryStream = {
                val chatModel = Firebase.ai.generativeModel(
                    modelName = mode.modelName,
                    generationConfig = generationConfig { maxOutputTokens = tokenCap },
                    systemInstruction = content { text(sysPrompt) }
                )
                chatModel.startChat(history = recentHistory).sendMessageStream(userMessage)
            },
            fallbackUnary = {
                val liteModel = Firebase.ai.generativeModel(
                    modelName = FAST_LITE_MODEL,
                    generationConfig = generationConfig { maxOutputTokens = tokenCap },
                    systemInstruction = content { text(sysPrompt) }
                )
                liteModel.startChat(history = recentHistory).sendMessage(userMessage)
            }
        )
    }

    // 2. Audio Transcription (Multimodal audio bytes -> Gemini transcript)
    suspend fun transcribeAudio(audioBytes: ByteArray, language: String): String {
        val prompt = """
            Transcribe the spoken audio accurately and cleanly.
            Preferred language context: $language (preserve Hindi/Hinglish/English words naturally as spoken).
            Output ONLY the plain transcript text with proper punctuation, no timestamps or extra preamble.
        """.trimIndent()

        val reqContent = content {
            inlineData(audioBytes, "audio/mp4")
            text(prompt)
        }

        val response = unaryWithFastFallback(
            primaryCall = { streamModel.generateContent(reqContent) },
            fallbackCall = { fastLiteModel.generateContent(reqContent) }
        )

        return response.text?.trim().orEmpty().ifBlank {
            throw IllegalStateException("Could not detect clear speech in the recording. Try speaking closer to the mic.")
        }
    }

    // 3. Real-Time Voice Conversation Turn (Audio input -> Natural spoken reply)
    fun streamVoiceConversationTurn(
        profile: UserProfile,
        recentTurns: List<Pair<String, String>>, // role to text
        audioBytes: ByteArray
    ): Flow<String> {
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

        val reqContent = content {
            if (historyContext.isNotBlank()) text(historyContext)
            inlineData(audioBytes, "audio/mp4")
            text("Listen to what the student just said in the audio and respond conversationally.")
        }

        return streamWithFastFallback(
            primaryStream = {
                val voiceModel = Firebase.ai.generativeModel(
                    modelName = DEFAULT_MODEL,
                    generationConfig = generationConfig { maxOutputTokens = 450 },
                    systemInstruction = content { text(sysPrompt) }
                )
                voiceModel.generateContentStream(reqContent)
            },
            fallbackUnary = {
                val liteVoiceModel = Firebase.ai.generativeModel(
                    modelName = FAST_LITE_MODEL,
                    generationConfig = generationConfig { maxOutputTokens = 450 },
                    systemInstruction = content { text(sysPrompt) }
                )
                liteVoiceModel.generateContent(reqContent)
            }
        )
    }

    // 4. Weekly Coach
    fun streamWeeklyCoach(profile: UserProfile, language: String): Flow<String> {
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

        return streamWithFastFallback(
            primaryStream = { streamModel.generateContentStream(prompt) },
            fallbackUnary = { fastLiteModel.generateContent(prompt) }
        )
    }

    // 5. Exam Planner
    fun streamExamPlan(
        profile: UserProfile,
        examName: String,
        daysLeft: Int,
        weakAreas: String,
        language: String
    ): Flow<String> {
        val prompt = """
            You are a strategic exam preparation coach.
            Respond strictly in $language.
            Create a crisp, practical study & revision roadmap for:
            - Student: ${profile.name}
            - Exam: $examName
            - Days remaining: $daysLeft days
            - Daily study target: ${profile.dailyStudyGoalHours} hours
            - Subjects: ${profile.subjects.joinToString(", ")}
            - Weak areas: ${weakAreas.ifBlank { "Balanced full syllabus revision" }}

            Break into clear phases with daily hour splits and mock-test checkpoints. Keep formatting scannable.
        """.trimIndent()

        return streamWithFastFallback(
            primaryStream = { streamModel.generateContentStream(prompt) },
            fallbackUnary = { fastLiteModel.generateContent(prompt) }
        )
    }

    // 6. Doubt Solver
    fun streamDoubtAnswer(subject: String, question: String, language: String): Flow<String> {
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

        return streamWithFastFallback(
            primaryStream = { streamModel.generateContentStream(prompt) },
            fallbackUnary = { fastLiteModel.generateContent(prompt) }
        )
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

        val response = unaryWithFastFallback(
            primaryCall = { quickAddModel.generateContent(prompt) },
            fallbackCall = { quickAddLiteModel.generateContent(prompt) }
        )
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
            Build a realistic full-day routine (12 to 16 blocks from wake-up to sleep) for a student.
            Write summary in $language; keep task titles crisp.
            Student: ${profile.name}
            Daily study target: ${profile.dailyStudyGoalHours} hours
            Subjects: ${profile.subjects.joinToString(", ")}
            Goals & constraints: $userGoals
            Categories allowed: ${ALL_CATEGORIES.joinToString(", ")}
        """.trimIndent()

        val response = unaryWithFastFallback(
            primaryCall = { buildRoutineModel.generateContent(prompt) },
            fallbackCall = { buildRoutineLiteModel.generateContent(prompt) }
        )
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

    internal fun normalizeHHmm(raw: String): String {
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
                    "Gemini timed out after 60 seconds. Check your internet connection or try Fast Lite mode and tap again."
                e is CancellationException ->
                    "Stopped."
                raw != null && raw.contains("Default FirebaseApp is not initialized", ignoreCase = true) ->
                    "Firebase is not configured in this APK build (app/google-services.json was missing when built). Add the GOOGLE_SERVICES_JSON GitHub secret and rebuild the APK."
                raw != null ->
                    raw
                else ->
                    "Gemini couldn't complete this request right now (${e.javaClass.simpleName}). Please try again."
            }
        }
    }
}
