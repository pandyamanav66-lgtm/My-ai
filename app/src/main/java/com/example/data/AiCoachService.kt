package com.example.data

import android.graphics.Bitmap
import android.util.Base64
import android.util.Log
import com.example.BuildConfig
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import okhttp3.Call
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import java.util.Locale
import java.util.concurrent.TimeUnit
import kotlin.math.max
import kotlin.math.roundToInt

private const val TAG = "AiCoachService"
private const val BASE_URL = "https://api.openai.com/v1"
private const val DEFAULT_MODEL = "gpt-4o-mini"
private const val DEEP_MODEL = "gpt-4o"
private const val WHISPER_MODEL = "whisper-1"
private const val PLACEHOLDER_KEY = "OPENAI_API_KEY_DEFAULT_VALUE"
private const val AI_TIMEOUT_MS = 60_000L
private const val MAX_VISION_DIMENSION_PX = 1280

private val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()
private val AUDIO_MEDIA_TYPE = "audio/mp4".toMediaType()

class MissingOpenAiApiKeyException : IllegalStateException(
    "OpenAI API key is not configured. Add OPENAI_API_KEY in the AI Studio Secrets panel (or GitHub Actions Repository Secrets for APK builds) and rebuild."
)

class OpenAiApiException(
    val statusCode: Int,
    val apiMessage: String
) : IOException("OpenAI API error ($statusCode): $apiMessage")

internal sealed interface SseDeltaResult {
    data class Chunk(val text: String) : SseDeltaResult
    data object Done : SseDeltaResult
    data object Ignore : SseDeltaResult
}

class AiCoachService {

    private val httpClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .retryOnConnectionFailure(true)
            .build()
    }

    internal fun requireApiKey(rawKey: String = BuildConfig.OPENAI_API_KEY): String {
        val key = rawKey.trim()
        if (key.isEmpty() || key.equals(PLACEHOLDER_KEY, ignoreCase = true)) {
            throw MissingOpenAiApiKeyException()
        }
        return key
    }

    internal fun buildValidChatHistory(history: List<ChatMessageItem>): List<Pair<String, String>> {
        val valid = mutableListOf<Pair<String, String>>()
        var expectedRole = "user"
        for (msg in history.takeLast(16)) {
            val role = if (msg.role == "model" || msg.role == "assistant") "assistant" else "user"
            val body = msg.text.trim()
            if (body.isEmpty()) continue
            if (role == expectedRole) {
                valid.add(role to body)
                expectedRole = if (expectedRole == "user") "assistant" else "user"
            } else if (role == "user" && valid.isNotEmpty() && expectedRole == "assistant") {
                // Replace orphaned user message from an earlier failed send
                valid[valid.lastIndex] = "user" to body
            }
        }
        if (valid.isNotEmpty() && expectedRole == "assistant") {
            valid.removeAt(valid.lastIndex)
        }
        return valid.takeLast(12)
    }

    internal fun parseSseDelta(line: String): SseDeltaResult {
        val trimmed = line.trim()
        if (!trimmed.startsWith("data:")) return SseDeltaResult.Ignore
        val payload = trimmed.removePrefix("data:").trim()
        if (payload.isEmpty()) return SseDeltaResult.Ignore
        if (payload == "[DONE]") return SseDeltaResult.Done

        return try {
            val obj = JSONObject(payload)
            val choices = obj.optJSONArray("choices") ?: return SseDeltaResult.Ignore
            if (choices.length() == 0) return SseDeltaResult.Ignore
            val delta = choices.getJSONObject(0).optJSONObject("delta") ?: return SseDeltaResult.Ignore
            val content = delta.optString("content", "")
            if (content.isNotEmpty()) SseDeltaResult.Chunk(content) else SseDeltaResult.Ignore
        } catch (e: Exception) {
            Log.w(TAG, "Skipped unparseable SSE chunk: ${e.localizedMessage}")
            SseDeltaResult.Ignore
        }
    }

    private fun parseOpenAiErrorMessage(statusCode: Int, rawBody: String): String {
        val parsed = try {
            val err = JSONObject(rawBody).optJSONObject("error")
            err?.optString("message", "")?.takeIf { it.isNotBlank() }
        } catch (_: Exception) {
            null
        }
        return parsed ?: rawBody.trim().take(240).ifBlank { "HTTP $statusCode" }
    }

    private fun encodeBitmapToDataUrl(bitmap: Bitmap): String {
        val maxSide = max(bitmap.width, bitmap.height)
        val scaled = if (maxSide > MAX_VISION_DIMENSION_PX && maxSide > 0) {
            val ratio = MAX_VISION_DIMENSION_PX.toFloat() / maxSide.toFloat()
            val targetW = (bitmap.width * ratio).roundToInt().coerceAtLeast(1)
            val targetH = (bitmap.height * ratio).roundToInt().coerceAtLeast(1)
            Bitmap.createScaledBitmap(bitmap, targetW, targetH, true)
        } else {
            bitmap
        }

        val out = ByteArrayOutputStream()
        try {
            scaled.compress(Bitmap.CompressFormat.JPEG, 82, out)
            val b64 = Base64.encodeToString(out.toByteArray(), Base64.NO_WRAP)
            return "data:image/jpeg;base64,$b64"
        } finally {
            if (scaled !== bitmap && !scaled.isRecycled) {
                scaled.recycle()
            }
        }
    }

    private fun buildTextMessages(
        systemPrompt: String?,
        history: List<Pair<String, String>> = emptyList(),
        userMessage: String
    ): JSONArray {
        val arr = JSONArray()
        if (!systemPrompt.isNullOrBlank()) {
            arr.put(
                JSONObject()
                    .put("role", "system")
                    .put("content", systemPrompt)
            )
        }
        history.forEach { (role, text) ->
            arr.put(
                JSONObject()
                    .put("role", role)
                    .put("content", text)
            )
        }
        arr.put(
            JSONObject()
                .put("role", "user")
                .put("content", userMessage)
        )
        return arr
    }

    private suspend fun executeStreamingRequest(
        apiKey: String,
        model: String,
        messages: JSONArray,
        maxTokens: Int?,
        onCallCreated: (Call) -> Unit,
        onChunk: suspend (String) -> Unit
    ): Boolean {
        val bodyJson = JSONObject()
            .put("model", model)
            .put("messages", messages)
            .put("stream", true)
            .put("temperature", 0.6)
        if (maxTokens != null) {
            bodyJson.put("max_tokens", maxTokens)
        }

        val request = Request.Builder()
            .url("$BASE_URL/chat/completions")
            .header("Authorization", "Bearer $apiKey")
            .header("Content-Type", "application/json")
            .header("Accept", "text/event-stream")
            .post(bodyJson.toString().toRequestBody(JSON_MEDIA_TYPE))
            .build()

        val call = httpClient.newCall(request)
        onCallCreated(call)

        var emittedAny = false
        call.execute().use { response ->
            if (!response.isSuccessful) {
                val errBody = response.body?.string().orEmpty()
                throw OpenAiApiException(
                    statusCode = response.code,
                    apiMessage = parseOpenAiErrorMessage(response.code, errBody)
                )
            }
            val source = response.body?.source()
                ?: throw IOException("Empty streaming body from OpenAI.")

            while (!source.exhausted()) {
                val line = source.readUtf8Line() ?: break
                when (val delta = parseSseDelta(line)) {
                    is SseDeltaResult.Chunk -> {
                        emittedAny = true
                        onChunk(delta.text)
                    }
                    SseDeltaResult.Done -> break
                    SseDeltaResult.Ignore -> Unit
                }
            }
        }
        return emittedAny
    }

    /**
     * Streams live token deltas over Server-Sent Events (SSE) from OpenAI Chat Completions.
     * If a heavier model (e.g. gpt-4o) fails transiently before emitting any tokens, automatically
     * falls back to ultra-fast gpt-4o-mini so the student still gets a crisp answer.
     */
    private fun streamChatCompletions(
        model: String,
        messages: JSONArray,
        maxTokens: Int? = null
    ): Flow<String> = channelFlow {
        val apiKey = requireApiKey()
        var activeCall: Call? = null

        val job = launch(Dispatchers.IO) {
            withTimeout(AI_TIMEOUT_MS) {
                var emittedAny = false
                try {
                    emittedAny = executeStreamingRequest(
                        apiKey = apiKey,
                        model = model,
                        messages = messages,
                        maxTokens = maxTokens,
                        onCallCreated = { activeCall = it },
                        onChunk = { chunk -> send(chunk) }
                    )
                    if (!emittedAny) {
                        throw IllegalStateException("ChatGPT returned an empty response. Please try again.")
                    }
                } catch (ce: CancellationException) {
                    activeCall?.cancel()
                    throw ce
                } catch (primaryErr: Exception) {
                    val canFallback = !emittedAny &&
                        model != DEFAULT_MODEL &&
                        primaryErr !is MissingOpenAiApiKeyException &&
                        (primaryErr !is OpenAiApiException || primaryErr.statusCode != 401)
                    if (canFallback) {
                        Log.w(TAG, "Model $model failed (${primaryErr.localizedMessage}); falling back to $DEFAULT_MODEL.")
                        val fallbackEmitted = executeStreamingRequest(
                            apiKey = apiKey,
                            model = DEFAULT_MODEL,
                            messages = messages,
                            maxTokens = maxTokens,
                            onCallCreated = { activeCall = it },
                            onChunk = { chunk -> send(chunk) }
                        )
                        if (!fallbackEmitted) {
                            throw primaryErr
                        }
                    } else {
                        throw primaryErr
                    }
                }
            }
        }

        job.invokeOnCompletion { err ->
            if (err != null) {
                activeCall?.cancel()
                close(err)
            } else {
                close()
            }
        }

        awaitClose {
            activeCall?.cancel()
            job.cancel()
        }
    }.flowOn(Dispatchers.IO)

    private suspend fun unaryChatCompletion(
        model: String = DEFAULT_MODEL,
        messages: JSONArray,
        maxTokens: Int? = null,
        jsonMode: Boolean = false
    ): String = withContext(Dispatchers.IO) {
        val apiKey = requireApiKey()
        withTimeout(AI_TIMEOUT_MS) {
            val bodyJson = JSONObject()
                .put("model", model)
                .put("messages", messages)
                .put("temperature", if (jsonMode) 0.2 else 0.6)
            if (maxTokens != null) {
                bodyJson.put("max_tokens", maxTokens)
            }
            if (jsonMode) {
                bodyJson.put("response_format", JSONObject().put("type", "json_object"))
            }

            val request = Request.Builder()
                .url("$BASE_URL/chat/completions")
                .header("Authorization", "Bearer $apiKey")
                .header("Content-Type", "application/json")
                .post(bodyJson.toString().toRequestBody(JSON_MEDIA_TYPE))
                .build()

            httpClient.newCall(request).execute().use { response ->
                val rawBody = response.body?.string().orEmpty()
                if (!response.isSuccessful) {
                    throw OpenAiApiException(
                        statusCode = response.code,
                        apiMessage = parseOpenAiErrorMessage(response.code, rawBody)
                    )
                }
                val json = JSONObject(rawBody)
                val choices = json.optJSONArray("choices")
                val content = choices?.optJSONObject(0)
                    ?.optJSONObject("message")
                    ?.optString("content", "")
                    ?.trim()
                    .orEmpty()
                if (content.isEmpty()) {
                    throw IllegalStateException("ChatGPT returned an empty response. Please try again.")
                }
                content
            }
        }
    }

    // 1. Multi-Turn ChatGPT Study Mentor with System Instruction & Mode Selection
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

        val sysPrompt = """
            You are an expert personal Study Mentor & Routine Coach inside "Study Tracker".
            Always reply in ${profile.aiLanguage}.
            $depthHint
            Student context:
            - Name: ${profile.name}
            - Target exam: ${profile.customDetails.ifBlank { "General studies" }}
            - Daily study goal: ${profile.dailyStudyGoalHours} h (Logged today: ${String.format(Locale.US, "%.1f", todayStudy)} h)
            - Current 75%+ streak: $streak days
            - Subjects: ${profile.subjects.joinToString(", ")}
            - Upcoming exams: $examsText
        """.trimIndent()

        val recentHistory = buildValidChatHistory(history)
        val messages = buildTextMessages(
            systemPrompt = sysPrompt,
            history = recentHistory,
            userMessage = userMessage
        )
        val targetModel = when (mode) {
            AiChatMode.DEEP -> DEEP_MODEL
            AiChatMode.FAST, AiChatMode.BALANCED -> DEFAULT_MODEL
        }
        val tokenLimit = when (mode) {
            AiChatMode.FAST -> 550
            AiChatMode.BALANCED -> 1100
            AiChatMode.DEEP -> 1800
        }

        return streamChatCompletions(
            model = targetModel,
            messages = messages,
            maxTokens = tokenLimit
        )
    }

    // 1b. Multimodal Photo Doubt Solver (Image Bitmap + Prompt -> Step-by-step ChatGPT Vision solution)
    fun streamPhotoDoubtReply(
        profile: UserProfile,
        bitmap: Bitmap,
        userPrompt: String
    ): Flow<String> {
        val cleanPrompt = userPrompt.trim().ifBlank {
            "Read the question or notes in this image carefully and solve or explain it step-by-step."
        }
        val sysPrompt = """
            You are an expert personal Study Tutor inside "Study Tracker".
            Always reply in ${profile.aiLanguage}.
            When a student uploads a photo of a question, equation, diagram, or textbook page:
            1. Clearly state what the question/topic is.
            2. Solve or explain it cleanly step-by-step with formulas and crisp formatting.
            3. Highlight one common mistake to avoid or a quick exam tip.
            Student target exam: ${profile.customDetails.ifBlank { "General studies" }}.
        """.trimIndent()

        val dataUrl = encodeBitmapToDataUrl(bitmap)
        val userContentParts = JSONArray()
            .put(JSONObject().put("type", "text").put("text", cleanPrompt))
            .put(
                JSONObject()
                    .put("type", "image_url")
                    .put(
                        "image_url",
                        JSONObject()
                            .put("url", dataUrl)
                            .put("detail", "auto")
                    )
            )

        val messages = JSONArray()
            .put(JSONObject().put("role", "system").put("content", sysPrompt))
            .put(JSONObject().put("role", "user").put("content", userContentParts))

        return streamChatCompletions(
            model = DEFAULT_MODEL,
            messages = messages,
            maxTokens = 1400
        )
    }

    // 2. Audio Transcription (OpenAI Whisper API: audio bytes -> clean transcript)
    suspend fun transcribeAudio(audioBytes: ByteArray, language: String): String = withContext(Dispatchers.IO) {
        val apiKey = requireApiKey()
        withTimeout(AI_TIMEOUT_MS) {
            val multipart = MultipartBody.Builder()
                .setType(MultipartBody.FORM)
                .addFormDataPart("model", WHISPER_MODEL)
                .addFormDataPart("response_format", "json")
                .addFormDataPart(
                    "prompt",
                    "Study notes and spoken questions in $language (Hindi, Hinglish, or English)."
                )
                .addFormDataPart(
                    "file",
                    "voice_note.m4a",
                    audioBytes.toRequestBody(AUDIO_MEDIA_TYPE)
                )
                .build()

            val request = Request.Builder()
                .url("$BASE_URL/audio/transcriptions")
                .header("Authorization", "Bearer $apiKey")
                .post(multipart)
                .build()

            httpClient.newCall(request).execute().use { response ->
                val rawBody = response.body?.string().orEmpty()
                if (!response.isSuccessful) {
                    throw OpenAiApiException(
                        statusCode = response.code,
                        apiMessage = parseOpenAiErrorMessage(response.code, rawBody)
                    )
                }
                val transcript = JSONObject(rawBody).optString("text", "").trim()
                if (transcript.isBlank()) {
                    throw IllegalStateException("Could not detect clear speech in the recording. Try speaking closer to the mic.")
                }
                transcript
            }
        }
    }

    // 3. Real-Time Voice Conversation Turn (Whisper transcription -> Fast ChatGPT spoken reply)
    fun streamVoiceConversationTurn(
        profile: UserProfile,
        recentTurns: List<Pair<String, String>>,
        audioBytes: ByteArray
    ): Flow<String> = channelFlow {
        val spokenText = transcribeAudio(audioBytes, profile.aiLanguage)

        val sysPrompt = """
            You are a warm, real-time Voice Study Coach having a live spoken conversation with ${profile.name}.
            Reply in natural conversational ${profile.aiLanguage} meant to be read aloud via text-to-speech.
            Keep your spoken reply crisp (2 to 4 short sentences, NO markdown bullets, NO asterisks, NO emojis).
            Student subjects: ${profile.subjects.joinToString(", ")}. Daily study goal: ${profile.dailyStudyGoalHours}h.
        """.trimIndent()

        val normalizedTurns = recentTurns.takeLast(6).map { (r, t) ->
            (if (r == "user") "user" else "assistant") to t
        }
        val messages = buildTextMessages(
            systemPrompt = sysPrompt,
            history = normalizedTurns,
            userMessage = spokenText
        )

        streamChatCompletions(
            model = DEFAULT_MODEL,
            messages = messages,
            maxTokens = 350
        ).collect { chunk ->
            send(chunk)
        }
    }.flowOn(Dispatchers.IO)

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

        return streamChatCompletions(
            model = DEFAULT_MODEL,
            messages = buildTextMessages(systemPrompt = null, userMessage = prompt),
            maxTokens = 900
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

        return streamChatCompletions(
            model = DEFAULT_MODEL,
            messages = buildTextMessages(systemPrompt = null, userMessage = prompt),
            maxTokens = 1200
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

        return streamChatCompletions(
            model = DEFAULT_MODEL,
            messages = buildTextMessages(systemPrompt = null, userMessage = prompt),
            maxTokens = 1100
        )
    }

    // 7. Quick Add (Structured JSON)
    suspend fun parseQuickAddTask(input: String, availableSubjects: List<String>): RoutineTask {
        val sysPrompt = """
            Return ONLY valid JSON with exact keys:
            {"title": string, "start": "HH:mm", "end": "HH:mm", "category": one of [${ALL_CATEGORIES.joinToString(", ")}], "subject": string}
        """.trimIndent()
        val prompt = """
            Parse this natural-language routine task request into structured JSON:
            "$input"

            Available subjects: ${availableSubjects.joinToString(", ")}
            Available categories: ${ALL_CATEGORIES.joinToString(", ")}
            Convert 12-hour times like "7 to 8 pm" into 24-hour "19:00" and "20:00".
            If no time is given, pick "18:00" to "19:00".
            Leave "subject" empty if category is not Study/Revision/Practice/Classes.
        """.trimIndent()

        val raw = unaryChatCompletion(
            model = DEFAULT_MODEL,
            messages = buildTextMessages(systemPrompt = sysPrompt, userMessage = prompt),
            maxTokens = 300,
            jsonMode = true
        )
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
        val sysPrompt = """
            Return ONLY valid JSON matching this schema:
            {
              "summary": "Brief 1-2 sentence explanation",
              "tasks": [
                {"start": "HH:mm", "end": "HH:mm", "title": "Task title", "category": "Study", "subject": "Subject or empty string"}
              ]
            }
            Allowed categories: ${ALL_CATEGORIES.joinToString(", ")}.
        """.trimIndent()

        val prompt = """
            Build a realistic full-day routine (12 to 16 ordered blocks from wake-up to sleep) for a student.
            Write summary in $language; keep task titles crisp.
            Student: ${profile.name}
            Daily study target: ${profile.dailyStudyGoalHours} hours
            Subjects: ${profile.subjects.joinToString(", ")}
            Goals & constraints: $userGoals
        """.trimIndent()

        val raw = unaryChatCompletion(
            model = DEFAULT_MODEL,
            messages = buildTextMessages(systemPrompt = sysPrompt, userMessage = prompt),
            maxTokens = 1800,
            jsonMode = true
        )
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
                e is MissingOpenAiApiKeyException ->
                    e.message ?: "OpenAI API key is missing. Set OPENAI_API_KEY in AI Studio Secrets or GitHub Actions Secrets."
                e is OpenAiApiException && e.statusCode == 401 ->
                    "Invalid OpenAI API key (401). Check your OPENAI_API_KEY secret and rebuild the app."
                e is OpenAiApiException && e.statusCode == 429 ->
                    "OpenAI rate limit or billing quota reached (429). Check your OpenAI API usage/credits or try again shortly."
                e is OpenAiApiException && e.statusCode in 500..599 ->
                    "OpenAI servers are temporarily busy (${e.statusCode}). Please try again in a few seconds."
                e is OpenAiApiException ->
                    "ChatGPT error (${e.statusCode}): ${e.apiMessage}"
                e is TimeoutCancellationException || e is SocketTimeoutException ->
                    "ChatGPT timed out. Check your internet connection and tap Send again."
                e is UnknownHostException ->
                    "No internet connection. Please connect to Wi-Fi or mobile data and try again."
                e is CancellationException ->
                    "Stopped."
                raw != null ->
                    raw
                else ->
                    "ChatGPT couldn't complete this request right now (${e.javaClass.simpleName}). Please try again."
            }
        }
    }
}
