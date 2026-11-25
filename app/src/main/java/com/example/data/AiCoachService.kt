package com.example.data

import android.graphics.Bitmap
import android.util.Log
import com.google.firebase.Firebase
import com.google.firebase.ai.ai
import com.google.firebase.ai.type.Content
import com.google.firebase.ai.type.GenerateContentResponse
import com.google.firebase.ai.type.Schema
import com.google.firebase.ai.type.content
import com.google.firebase.ai.type.generationConfig
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.json.JSONObject
import java.util.Locale

private const val TAG = "AiCoachService"
private const val DEEP_MODEL = "gemini-3.8-flash"
private const val FAST_LITE_MODEL = "gemini-3.5-flash-lite"
private const val FIRST_CHUNK_WATCHDOG_MS = 18_000L
private const val UNARY_PRIMARY_TIMEOUT_MS = 26_000L
private const val AI_TIMEOUT_MS = 60_000L

class AiCoachService {

    private val deepModel by lazy {
        Firebase.ai.generativeModel(modelName = DEEP_MODEL)
    }

    private val fastLiteModel by lazy {
        Firebase.ai.generativeModel(
            modelName = FAST_LITE_MODEL,
            generationConfig = generationConfig {
                temperature = 0.45f
                maxOutputTokens = 700
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

    private val quickAddFastModel by lazy {
        Firebase.ai.generativeModel(
            modelName = FAST_LITE_MODEL,
            generationConfig = generationConfig {
                responseMimeType = "application/json"
                responseSchema = quickAddSchema
                temperature = 0.2f
                maxOutputTokens = 350
            }
        )
    }

    private val quickAddDeepModel by lazy {
        Firebase.ai.generativeModel(
            modelName = DEEP_MODEL,
            generationConfig = generationConfig {
                responseMimeType = "application/json"
                responseSchema = quickAddSchema
                temperature = 0.2f
                maxOutputTokens = 350
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
            modelName = DEEP_MODEL,
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
     * Resilient low-latency streaming pipeline:
     * - Starts streaming immediately without pre-warm stalls (Firebase.ai attaches App Check natively).
     * - If no first token arrives within 18 seconds (or the primary stream fails before emitting text),
     *   switches automatically to a fast unary fallback on gemini-3.5-flash-lite inside the 60s budget.
     */
    private fun streamWithFastFallback(
        primaryStream: () -> Flow<GenerateContentResponse>,
        fallbackUnary: suspend () -> GenerateContentResponse
    ): Flow<String> = channelFlow {
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

    private suspend fun unaryWithFastFallback(
        primaryCall: suspend () -> GenerateContentResponse,
        fallbackCall: suspend () -> GenerateContentResponse
    ): GenerateContentResponse = withContext(Dispatchers.IO) {
        withTimeout(AI_TIMEOUT_MS) {
            try {
                withTimeout(UNARY_PRIMARY_TIMEOUT_MS) {
                    primaryCall()
                }
            } catch (t: TimeoutCancellationException) {
                Log.w(TAG, "Primary unary call timed out; retrying fallback model.")
                fallbackCall()
            } catch (ce: CancellationException) {
                throw ce
            } catch (e: Exception) {
                Log.w(TAG, "Primary unary call failed (${e.localizedMessage}); retrying fallback model.")
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

    internal fun buildValidChatHistory(history: List<ChatMessageItem>, maxTurns: Int = 12): List<Content> {
        val valid = mutableListOf<Content>()
        var expectedRole = "user"
        for (msg in history.takeLast(maxTurns + 6)) {
            val role = if (msg.role == "model" || msg.role == "assistant") "model" else "user"
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
        return valid.takeLast(maxTurns)
    }

    // 1. Multi-Turn Gemini Chatbot with Flash (Ultra-Fast) vs Deep (Detailed) Mode
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

        val isFast = mode == AiChatMode.FAST
        val depthHint = if (isFast) {
            "Be ultra-fast, direct, and concise: give the exact answer first in 3 to 6 crisp bullet points (no long filler)."
        } else {
            "Explain deeply and thoroughly step-by-step with formulas, worked examples, key intuition, and common exam traps to avoid."
        }

        val sysPrompt = """
            You are an expert personal Study Mentor inside "Study Tracker".
            Always reply in ${profile.aiLanguage}.
            $depthHint
            Student: ${profile.name} | Target: ${profile.customDetails.ifBlank { "General studies" }}
            Goal: ${profile.dailyStudyGoalHours}h/day (Today: ${String.format(Locale.US, "%.1f", todayStudy)}h) | Streak: ${streak}d
            Subjects: ${profile.subjects.joinToString(", ")} | Exams: $examsText
        """.trimIndent()

        // Flash trims history tighter (6 turns) for minimum token latency; Deep preserves 14 turns.
        val recentHistory = buildValidChatHistory(history, maxTurns = if (isFast) 6 else 14)
        val chatGenConfig = generationConfig {
            temperature = if (isFast) 0.45f else 0.65f
            maxOutputTokens = if (isFast) 520 else 2048
        }

        return streamWithFastFallback(
            primaryStream = {
                val chatModel = Firebase.ai.generativeModel(
                    modelName = mode.modelName,
                    generationConfig = chatGenConfig,
                    systemInstruction = content { text(sysPrompt) }
                )
                chatModel.startChat(history = recentHistory).sendMessageStream(userMessage)
            },
            fallbackUnary = {
                val liteModel = Firebase.ai.generativeModel(
                    modelName = FAST_LITE_MODEL,
                    generationConfig = chatGenConfig,
                    systemInstruction = content { text(sysPrompt) }
                )
                try {
                    liteModel.startChat(history = recentHistory).sendMessage(userMessage)
                } catch (ce: CancellationException) {
                    throw ce
                } catch (chatErr: Exception) {
                    Log.w(TAG, "Lite chat with history failed (${chatErr.localizedMessage}); retrying single-turn.", chatErr)
                    liteModel.generateContent(userMessage)
                }
            }
        )
    }

    // 1b. Multimodal Photo Doubt Solver (respects Gemini Flash vs Gemini Deep mode)
    fun streamPhotoDoubtReply(
        profile: UserProfile,
        bitmap: Bitmap,
        userPrompt: String,
        mode: AiChatMode = AiChatMode.FAST
    ): Flow<String> {
        val isFast = mode == AiChatMode.FAST
        val cleanPrompt = userPrompt.trim().ifBlank {
            if (isFast) "Read this question/notes and give a fast, clear step-by-step solution."
            else "Read the question or notes in this image carefully and explain every step in full detail with formulas and exam tips."
        }
        val sysPrompt = """
            You are an expert Study Tutor inside "Study Tracker".
            Always reply in ${profile.aiLanguage}.
            ${if (isFast) "Keep the solution crisp, accurate, and fast to scan." else "Provide a thorough, detailed breakdown with formulas, reasoning, and one common exam mistake to avoid."}
            Student target exam: ${profile.customDetails.ifBlank { "General studies" }}.
        """.trimIndent()

        val multimodalContent = content {
            image(bitmap)
            text(cleanPrompt)
        }
        val visionConfig = generationConfig {
            temperature = if (isFast) 0.4f else 0.55f
            maxOutputTokens = if (isFast) 700 else 2048
        }

        return streamWithFastFallback(
            primaryStream = {
                val visionModel = Firebase.ai.generativeModel(
                    modelName = mode.modelName,
                    generationConfig = visionConfig,
                    systemInstruction = content { text(sysPrompt) }
                )
                visionModel.generateContentStream(multimodalContent)
            },
            fallbackUnary = {
                val liteVisionModel = Firebase.ai.generativeModel(
                    modelName = FAST_LITE_MODEL,
                    generationConfig = visionConfig,
                    systemInstruction = content { text(sysPrompt) }
                )
                liteVisionModel.generateContent(multimodalContent)
            }
        )
    }

    // 2. Audio Transcription (Multimodal audio bytes -> Gemini Flash-Lite fast transcript)
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
            primaryCall = { fastLiteModel.generateContent(reqContent) },
            fallbackCall = { deepModel.generateContent(reqContent) }
        )

        return response.text?.trim().orEmpty().ifBlank {
            throw IllegalStateException("Could not detect clear speech in the recording. Try speaking closer to the mic.")
        }
    }

    // 3. Real-Time Voice Conversation Turn (Audio input -> Fast spoken reply)
    fun streamVoiceConversationTurn(
        profile: UserProfile,
        recentTurns: List<Pair<String, String>>,
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
                    modelName = FAST_LITE_MODEL,
                    generationConfig = generationConfig { maxOutputTokens = 380 },
                    systemInstruction = content { text(sysPrompt) }
                )
                voiceModel.generateContentStream(reqContent)
            },
            fallbackUnary = {
                val fallbackVoiceModel = Firebase.ai.generativeModel(
                    modelName = DEEP_MODEL,
                    generationConfig = generationConfig { maxOutputTokens = 380 },
                    systemInstruction = content { text(sysPrompt) }
                )
                fallbackVoiceModel.generateContent(reqContent)
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
            primaryStream = { fastLiteModel.generateContentStream(prompt) },
            fallbackUnary = { deepModel.generateContent(prompt) }
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
            primaryStream = { deepModel.generateContentStream(prompt) },
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
            primaryStream = { deepModel.generateContentStream(prompt) },
            fallbackUnary = { fastLiteModel.generateContent(prompt) }
        )
    }

    // 7. Quick Add (Structured JSON — ultra-fast Flash-Lite primary)
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
            primaryCall = { quickAddFastModel.generateContent(prompt) },
            fallbackCall = { quickAddDeepModel.generateContent(prompt) }
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
                    "Gemini timed out after 60 seconds. Switch to Flash mode or check your internet connection and tap Send again."
                e is CancellationException ->
                    "Stopped."
                raw != null && raw.contains("Default FirebaseApp is not initialized", ignoreCase = true) ->
                    "Firebase is not configured in this APK build (app/google-services.json was missing when built). Add GOOGLE_SERVICES_JSON or commit app/google-services.json and rebuild."
                raw != null && raw.contains("App Check", ignoreCase = true) ->
                    "Firebase App Check verification failed ($raw). Ensure Play Services & internet are active and retry."
                raw != null ->
                    raw
                else ->
                    "Gemini couldn't complete this request right now (${e.javaClass.simpleName}). Please try again."
            }
        }
    }
}
