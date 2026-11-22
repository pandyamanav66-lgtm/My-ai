package com.example.viewmodel

import android.app.Application
import android.content.Context
import android.media.AudioManager
import android.media.ToneGenerator
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.Locale
import kotlin.math.max
import kotlin.math.roundToInt

private const val TAG = "TrackerViewModel"

enum class AiHubSection(val label: String) {
    CHAT("Chat"),
    VOICE("Voice Live"),
    TRANSCRIBE("Transcribe"),
    TOOLS("Coach Tools")
}

enum class AiTool {
    WEEKLY_COACH,
    BUILD_ROUTINE,
    QUICK_ADD,
    EXAM_PLANNER,
    DOUBT_SOLVER
}

data class FocusTimerState(
    val selectedSubject: String = "Maths",
    val durationMinutes: Int = 25,
    val remainingSeconds: Int = 25 * 60,
    val isRunning: Boolean = false,
    val isPaused: Boolean = false,
    val startedAtEpochMs: Long? = null,
    val endTimeEpochMs: Long? = null,
    val accumulatedElapsedSec: Int = 0
)

data class VoiceTurn(
    val id: String,
    val role: String, // "user" | "model"
    val text: String
)

data class AiCoachUiState(
    val activeSection: AiHubSection = AiHubSection.CHAT,
    val selectedTool: AiTool = AiTool.WEEKLY_COACH,
    val chatMode: AiChatMode = AiChatMode.BALANCED,
    val chatMessages: List<ChatMessageItem> = emptyList(),
    val streamingChatReply: String = "",
    val isChatStreaming: Boolean = false,
    // Audio Transcription
    val isRecordingTranscript: Boolean = false,
    val recordingSeconds: Int = 0,
    val micAmplitude: Float = 0f,
    val isTranscribing: Boolean = false,
    val transcriptText: String = "",
    // Live Voice Conversation
    val isVoiceRecording: Boolean = false,
    val isVoiceThinking: Boolean = false,
    val isVoiceSpeaking: Boolean = false,
    val voiceAutoSpeak: Boolean = true,
    val voiceStreamingText: String = "",
    val voiceTurns: List<VoiceTurn> = emptyList(),
    // Tools state
    val isStreaming: Boolean = false,
    val streamOutput: String = "",
    val errorMessage: String? = null,
    val previewSummary: String? = null,
    val previewRoutine: List<RoutineTask>? = null,
    val previousRoutineForUndo: List<RoutineTask>? = null,
    val lastQuickAddedTaskId: String? = null,
    val lastQuickAddedSummary: String? = null,
    val doubtSubject: String = "Maths",
    val doubtQuestion: String = ""
)

data class TrackerUiState(
    val store: DrstStore = DrstStore(),
    val showSplash: Boolean = true,
    val showNewProfilePrompt: Boolean = false,
    val selectedTab: AppTab = AppTab.CHECKLIST,
    val selectedDate: String = todayKey(),
    val checklistFilter: ChecklistFilter = ChecklistFilter.ALL,
    val weeklyStartMonday: String = startOfWeekMonday(todayKey()),
    val doubtFilter: DoubtFilter = DoubtFilter.OPEN,
    val showEditRoutineSheet: Boolean = false,
    val showProfileSheet: Boolean = false,
    val showGameOverlay: Boolean = false,
    val timer: FocusTimerState = FocusTimerState(),
    val ai: AiCoachUiState = AiCoachUiState(),
    val updateState: UpdateCheckState = UpdateCheckState.Idle,
    val updateBannerDismissed: Boolean = false,
    val hasSavedGitHubToken: Boolean = false,
    val confettiBurstId: Int = 0,
    val toastMessage: String? = null
)

class TrackerViewModel(application: Application) : AndroidViewModel(application) {
    private val repo = DrstRepository(application)
    private val aiService = AiCoachService()
    private val appUpdateService = AppUpdateService()
    private val audioRecorder = AudioRecorderHelper(application)

    private var updateCheckJob: Job? = null
    private var tts: TextToSpeech? = null
    private var ttsReady = false

    private val _uiState = MutableStateFlow(TrackerUiState())
    val uiState: StateFlow<TrackerUiState> = _uiState.asStateFlow()

    private var timerJob: Job? = null
    private var aiJob: Job? = null
    private var micPollJob: Job? = null
    private var toastJob: Job? = null

    init {
        initTextToSpeech(application)
        val loaded = repo.loadStore()
        val active = loaded.activeProfile
        val defaultSubject = active?.subjects?.firstOrNull() ?: "Maths"
        val savedToken = UpdateNotificationHelper.getSavedGitHubToken(application)
        _uiState.update {
            it.copy(
                store = loaded,
                selectedTab = AppTab.CHECKLIST,
                selectedDate = todayKey(),
                weeklyStartMonday = startOfWeekMonday(todayKey()),
                timer = it.timer.copy(selectedSubject = defaultSubject),
                ai = it.ai.copy(
                    chatMessages = active?.chatHistory.orEmpty(),
                    doubtSubject = defaultSubject
                ),
                hasSavedGitHubToken = savedToken.isNotBlank()
            )
        }
        // Automatically check GitHub Releases in the background on startup (non-blocking)
        viewModelScope.launch {
            delay(1800L)
            checkForAppUpdate(silent = true)
        }
    }

    private fun initTextToSpeech(context: Context) {
        try {
            tts = TextToSpeech(context.applicationContext) { status ->
                ttsReady = status == TextToSpeech.SUCCESS
                if (ttsReady) {
                    tts?.language = Locale.US
                    tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                        override fun onStart(utteranceId: String?) {
                            _uiState.update { it.copy(ai = it.ai.copy(isVoiceSpeaking = true)) }
                        }

                        override fun onDone(utteranceId: String?) {
                            _uiState.update { it.copy(ai = it.ai.copy(isVoiceSpeaking = false)) }
                        }

                        @Deprecated("Deprecated in Java")
                        override fun onError(utteranceId: String?) {
                            _uiState.update { it.copy(ai = it.ai.copy(isVoiceSpeaking = false)) }
                        }

                        override fun onError(utteranceId: String?, errorCode: Int) {
                            _uiState.update { it.copy(ai = it.ai.copy(isVoiceSpeaking = false)) }
                        }
                    })
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "TTS init skipped", e)
        }
    }

    fun dismissSplash() {
        _uiState.update { it.copy(showSplash = false) }
    }

    // ---- Profile lifecycle (100% local, zero sign-in) ----

    fun createProfile(name: String) {
        val clean = name.trim()
        if (clean.isEmpty()) return
        val newProfile = UserProfile(name = clean)
        updateStore { s ->
            s.copy(
                activeProfileId = newProfile.id,
                profiles = s.profiles + newProfile
            )
        }
        _uiState.update {
            it.copy(
                showNewProfilePrompt = false,
                selectedTab = AppTab.CHECKLIST,
                selectedDate = todayKey(),
                timer = it.timer.copy(selectedSubject = newProfile.subjects.firstOrNull() ?: "Maths"),
                ai = AiCoachUiState(
                    chatMessages = emptyList(),
                    doubtSubject = newProfile.subjects.firstOrNull() ?: "Maths"
                )
            )
        }
        showToast("Welcome, $clean!")
    }

    fun selectProfile(profileId: String) {
        val target = _uiState.value.store.profiles.firstOrNull { it.id == profileId } ?: return
        stopTimerInternal()
        cancelAiJob()
        stopTts()
        stopAudioRecordingClean()
        updateStore { it.copy(activeProfileId = profileId) }
        _uiState.update {
            it.copy(
                selectedTab = AppTab.CHECKLIST,
                selectedDate = todayKey(),
                weeklyStartMonday = startOfWeekMonday(todayKey()),
                showProfileSheet = false,
                timer = FocusTimerState(selectedSubject = target.subjects.firstOrNull() ?: "Maths"),
                ai = AiCoachUiState(
                    chatMessages = target.chatHistory,
                    doubtSubject = target.subjects.firstOrNull() ?: "Maths"
                )
            )
        }
    }

    fun setNewProfilePromptVisible(visible: Boolean) {
        _uiState.update { it.copy(showNewProfilePrompt = visible) }
    }

    fun logoutProfile() {
        stopTimerInternal()
        cancelAiJob()
        stopTts()
        stopAudioRecordingClean()
        updateStore { it.copy(activeProfileId = null) }
        _uiState.update { it.copy(showProfileSheet = false) }
    }

    fun deleteActiveProfile() {
        val activeId = _uiState.value.store.activeProfileId ?: return
        stopTimerInternal()
        cancelAiJob()
        stopTts()
        stopAudioRecordingClean()
        updateStore { s ->
            val remaining = s.profiles.filterNot { it.id == activeId }
            s.copy(
                profiles = remaining,
                activeProfileId = null
            )
        }
        _uiState.update { it.copy(showProfileSheet = false) }
        showToast("Profile deleted")
    }

    fun updateProfileDetails(name: String, email: String, phone: String, customDetails: String) {
        val cleanName = name.trim()
        if (cleanName.isEmpty()) return
        updateActiveProfile { p ->
            p.copy(
                name = cleanName,
                email = email.trim(),
                phone = phone.trim(),
                customDetails = customDetails.trim()
            )
        }
        showToast("Profile details saved")
    }

    fun setThemeMode(mode: String) {
        if (mode !in listOf("auto", "light", "dark")) return
        updateStore { it.copy(theme = mode) }
    }

    // ---- Navigation & UI state ----

    fun selectTab(tab: AppTab) {
        if (tab != AppTab.AI_COACH && audioRecorder.isRecording) {
            stopAudioRecordingClean()
            _uiState.update {
                it.copy(
                    selectedTab = tab,
                    ai = it.ai.copy(
                        isRecordingTranscript = false,
                        isVoiceRecording = false,
                        micAmplitude = 0f
                    )
                )
            }
        } else {
            _uiState.update { it.copy(selectedTab = tab) }
        }
    }
    fun selectDate(dateKey: String) = _uiState.update {
        it.copy(selectedDate = dateKey, weeklyStartMonday = startOfWeekMonday(dateKey))
    }
    fun shiftSelectedDate(deltaDays: Int) = selectDate(shiftDateKey(_uiState.value.selectedDate, deltaDays))
    fun jumpToToday() = selectDate(todayKey())
    fun openDayOnChecklist(dateKey: String) = _uiState.update {
        it.copy(selectedDate = dateKey, selectedTab = AppTab.CHECKLIST)
    }
    fun setChecklistFilter(filter: ChecklistFilter) = _uiState.update { it.copy(checklistFilter = filter) }
    fun shiftWeek(deltaWeeks: Int) = _uiState.update {
        it.copy(weeklyStartMonday = shiftDateKey(it.weeklyStartMonday, deltaWeeks * 7))
    }
    fun jumpToCurrentWeek() = _uiState.update { it.copy(weeklyStartMonday = startOfWeekMonday(todayKey())) }
    fun setDoubtFilter(filter: DoubtFilter) = _uiState.update { it.copy(doubtFilter = filter) }
    fun setEditRoutineSheetVisible(visible: Boolean) = _uiState.update { it.copy(showEditRoutineSheet = visible) }
    fun setProfileSheetVisible(visible: Boolean) = _uiState.update { it.copy(showProfileSheet = visible) }
    fun setGameOverlayVisible(visible: Boolean) = _uiState.update { it.copy(showGameOverlay = visible) }

    // ---- Checklist actions ----

    fun toggleTaskCompletion(taskId: String) {
        val date = _uiState.value.selectedDate
        val beforeProfile = _uiState.value.store.activeProfile ?: return
        val wasAllDone = Analytics.completionPercent(beforeProfile, date) == 100

        updateActiveProfile { p ->
            val currentLog = p.days[date] ?: DayLog()
            val updatedIds = if (taskId in currentLog.completedIds) {
                currentLog.completedIds - taskId
            } else {
                currentLog.completedIds + taskId
            }
            val newDays = p.days.toMutableMap()
            newDays[date] = currentLog.copy(completedIds = updatedIds)
            p.copy(days = newDays)
        }

        val afterProfile = _uiState.value.store.activeProfile ?: return
        val isAllDoneNow = Analytics.completionPercent(afterProfile, date) == 100 && afterProfile.routine.isNotEmpty()
        if (!wasAllDone && isAllDoneNow) {
            triggerConfetti()
            showToast("All tasks completed! Amazing work!")
        }
        checkAndUnlockBadges()
    }

    fun updateTaskNote(taskId: String, note: String) {
        val date = _uiState.value.selectedDate
        updateActiveProfile { p ->
            val currentLog = p.days[date] ?: DayLog()
            val newNotes = currentLog.notes.toMutableMap()
            if (note.isBlank()) newNotes.remove(taskId) else newNotes[taskId] = note
            val newDays = p.days.toMutableMap()
            newDays[date] = currentLog.copy(notes = newNotes)
            p.copy(days = newDays)
        }
    }

    fun adjustStudyGoal(deltaHours: Double) {
        updateActiveProfile { p ->
            val next = ((p.dailyStudyGoalHours + deltaHours) * 2.0).roundToInt() / 2.0
            p.copy(dailyStudyGoalHours = next.coerceIn(0.5, 16.0))
        }
    }

    fun toggleRestDay() {
        val date = _uiState.value.selectedDate
        var nowRest = false
        updateActiveProfile { p ->
            val currentLog = p.days[date] ?: DayLog()
            nowRest = !currentLog.isRestDay
            val newDays = p.days.toMutableMap()
            newDays[date] = currentLog.copy(isRestDay = nowRest)
            p.copy(days = newDays)
        }
        showToast(if (nowRest) "Marked as rest day (streak protected)" else "Rest day removed")
    }

    fun resetCurrentDay() {
        val date = _uiState.value.selectedDate
        updateActiveProfile { p ->
            val newDays = p.days.toMutableMap()
            newDays.remove(date)
            p.copy(days = newDays)
        }
        showToast("Day reset")
    }

    // ---- Routine Editor ----

    fun saveRoutine(tasks: List<RoutineTask>) {
        val sorted = tasks
            .filter { it.title.isNotBlank() }
            .map { task ->
                val startMin = parseTimeToMinutes(task.start)
                val endMin = parseTimeToMinutes(task.end)
                task.copy(
                    title = task.title.trim(),
                    start = String.format(Locale.US, "%02d:%02d", startMin / 60, startMin % 60),
                    end = String.format(Locale.US, "%02d:%02d", endMin / 60, endMin % 60)
                )
            }
            .sortedBy { it.startMinutes }
        if (sorted.isEmpty()) {
            showToast("Add at least one named task before saving")
            return
        }
        updateActiveProfile { it.copy(routine = sorted) }
        _uiState.update { it.copy(showEditRoutineSheet = false) }
        showToast("Routine updated (${sorted.size} tasks)")
    }

    fun restoreDefault19Routine() {
        updateActiveProfile { it.copy(routine = defaultRoutine19Tasks()) }
        showToast("Default 19-task routine restored")
    }

    // ---- Focus Timer (timestamp-based so accurate in background) ----

    fun selectFocusSubject(subject: String) {
        _uiState.update { it.copy(timer = it.timer.copy(selectedSubject = subject)) }
    }

    fun addSubject(name: String) {
        val clean = name.trim()
        if (clean.isEmpty()) return
        updateActiveProfile { p ->
            if (p.subjects.any { it.equals(clean, ignoreCase = true) }) p
            else p.copy(subjects = p.subjects + clean)
        }
        _uiState.update { it.copy(timer = it.timer.copy(selectedSubject = clean)) }
        showToast("Added subject: $clean")
    }

    fun renameSubject(oldName: String, newName: String) {
        val clean = newName.trim()
        if (clean.isEmpty() || oldName == clean) return
        updateActiveProfile { p ->
            val updatedSubjects = p.subjects.map { if (it == oldName) clean else it }.distinct()
            val updatedRoutine = p.routine.map {
                if (it.subject == oldName) it.copy(subject = clean) else it
            }
            val updatedSessions = p.focusSessions.map {
                if (it.subject == oldName) it.copy(subject = clean) else it
            }
            val updatedDoubts = p.doubts.map {
                if (it.subject == oldName) it.copy(subject = clean) else it
            }
            p.copy(
                subjects = updatedSubjects,
                routine = updatedRoutine,
                focusSessions = updatedSessions,
                doubts = updatedDoubts
            )
        }
        _uiState.update { state ->
            state.copy(
                timer = if (state.timer.selectedSubject == oldName) {
                    state.timer.copy(selectedSubject = clean)
                } else state.timer,
                ai = if (state.ai.doubtSubject == oldName) {
                    state.ai.copy(doubtSubject = clean)
                } else state.ai
            )
        }
        showToast("Renamed to $clean")
    }

    fun deleteSubject(subject: String) {
        updateActiveProfile { p ->
            if (p.subjects.size <= 1) p
            else p.copy(subjects = p.subjects.filterNot { it == subject })
        }
        val first = _uiState.value.store.activeProfile?.subjects?.firstOrNull() ?: "Maths"
        _uiState.update { state ->
            state.copy(
                timer = if (state.timer.selectedSubject == subject) {
                    state.timer.copy(selectedSubject = first)
                } else state.timer,
                ai = if (state.ai.doubtSubject == subject) {
                    state.ai.copy(doubtSubject = first)
                } else state.ai
            )
        }
    }

    fun setFocusPresetMinutes(minutes: Int) {
        val clamped = minutes.coerceIn(1, 180)
        stopTimerInternal()
        _uiState.update {
            it.copy(
                timer = it.timer.copy(
                    durationMinutes = clamped,
                    remainingSeconds = clamped * 60,
                    isRunning = false,
                    isPaused = false,
                    startedAtEpochMs = null,
                    endTimeEpochMs = null,
                    accumulatedElapsedSec = 0
                )
            )
        }
    }

    fun startOrResumeTimer() {
        val cur = _uiState.value.timer
        if (cur.isRunning) return
        val remSec = if (cur.remainingSeconds <= 0) cur.durationMinutes * 60 else cur.remainingSeconds
        val now = System.currentTimeMillis()
        val endMs = now + remSec * 1000L
        _uiState.update {
            it.copy(
                timer = cur.copy(
                    isRunning = true,
                    isPaused = false,
                    startedAtEpochMs = cur.startedAtEpochMs ?: now,
                    endTimeEpochMs = endMs,
                    remainingSeconds = remSec
                )
            )
        }
        startTickerLoop()
    }

    fun pauseTimer() {
        val cur = _uiState.value.timer
        if (!cur.isRunning) return
        timerJob?.cancel()
        val now = System.currentTimeMillis()
        val end = cur.endTimeEpochMs ?: now
        val rem = max(0, ((end - now) / 1000L).toInt())
        val totalSec = cur.durationMinutes * 60
        val elapsed = max(cur.accumulatedElapsedSec, totalSec - rem)
        _uiState.update {
            it.copy(
                timer = cur.copy(
                    isRunning = false,
                    isPaused = true,
                    remainingSeconds = rem,
                    endTimeEpochMs = null,
                    accumulatedElapsedSec = elapsed
                )
            )
        }
    }

    fun saveAndStopTimer() {
        val cur = _uiState.value.timer
        val now = System.currentTimeMillis()
        val totalSec = cur.durationMinutes * 60
        val rem = if (cur.isRunning && cur.endTimeEpochMs != null) {
            max(0, ((cur.endTimeEpochMs - now) / 1000L).toInt())
        } else {
            cur.remainingSeconds
        }
        val elapsedSec = max(cur.accumulatedElapsedSec, totalSec - rem)
        val loggedMinutes = max(1, (elapsedSec / 60.0).roundToInt())
        resetTimerState()
        if (elapsedSec >= 10) {
            recordFocusSession(cur.selectedSubject, loggedMinutes)
        } else {
            showToast("Session too short to save")
        }
    }

    fun resetTimerState() {
        stopTimerInternal()
        _uiState.update {
            it.copy(
                timer = it.timer.copy(
                    isRunning = false,
                    isPaused = false,
                    remainingSeconds = it.timer.durationMinutes * 60,
                    startedAtEpochMs = null,
                    endTimeEpochMs = null,
                    accumulatedElapsedSec = 0
                )
            )
        }
    }

    private fun stopTimerInternal() {
        timerJob?.cancel()
        timerJob = null
    }

    private fun startTickerLoop() {
        timerJob?.cancel()
        timerJob = viewModelScope.launch {
            while (true) {
                val cur = _uiState.value.timer
                if (!cur.isRunning || cur.endTimeEpochMs == null) break
                val now = System.currentTimeMillis()
                val remMs = cur.endTimeEpochMs - now
                val remSec = max(0, ((remMs + 999L) / 1000L).toInt())
                if (remMs <= 0L) {
                    val subject = cur.selectedSubject
                    val mins = cur.durationMinutes
                    resetTimerState()
                    recordFocusSession(subject, mins)
                    playCompletionBeepAndVibrate()
                    triggerConfetti()
                    break
                } else {
                    _uiState.update { it.copy(timer = it.timer.copy(remainingSeconds = remSec)) }
                }
                delay(250L)
            }
        }
    }

    private fun recordFocusSession(subject: String, minutes: Int) {
        val session = FocusSession(
            date = todayKey(),
            subject = subject,
            minutes = minutes
        )
        updateActiveProfile { p ->
            p.copy(focusSessions = listOf(session) + p.focusSessions)
        }
        showToast("Saved ${minutes}m focus on $subject (+${minutes * 2} XP)")
        checkAndUnlockBadges()
    }

    private fun playCompletionBeepAndVibrate() {
        try {
            val tone = ToneGenerator(AudioManager.STREAM_NOTIFICATION, 85)
            tone.startTone(ToneGenerator.TONE_PROP_BEEP2, 400)
            viewModelScope.launch {
                delay(500L)
                tone.release()
            }
        } catch (e: Exception) {
            Log.w(TAG, "Beep failed", e)
        }
        try {
            val app = getApplication<Application>()
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val vm = app.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
                vm?.defaultVibrator?.vibrate(VibrationEffect.createOneShot(320L, VibrationEffect.DEFAULT_AMPLITUDE))
            } else {
                @Suppress("DEPRECATION")
                val v = app.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    v?.vibrate(VibrationEffect.createOneShot(320L, VibrationEffect.DEFAULT_AMPLITUDE))
                } else {
                    @Suppress("DEPRECATION")
                    v?.vibrate(320L)
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Vibrate failed", e)
        }
    }

    // ---- Exams & Doubt Notebook ----

    fun addExam(name: String, dateKey: String) {
        val clean = name.trim()
        if (clean.isEmpty()) return
        val item = ExamItem(name = clean, date = dateKey)
        updateActiveProfile { p ->
            p.copy(exams = (p.exams + item).sortedBy { it.date })
        }
        showToast("Exam added: $clean")
    }

    fun deleteExam(examId: String) {
        updateActiveProfile { p -> p.copy(exams = p.exams.filterNot { it.id == examId }) }
    }

    fun addDoubt(subject: String, text: String) {
        val clean = text.trim()
        if (clean.isEmpty()) return
        val item = DoubtItem(subject = subject, text = clean)
        updateActiveProfile { p -> p.copy(doubts = listOf(item) + p.doubts) }
        showToast("Saved to Doubt Notebook")
    }

    fun toggleDoubtSolved(doubtId: String) {
        updateActiveProfile { p ->
            p.copy(
                doubts = p.doubts.map {
                    if (it.id == doubtId) it.copy(solved = !it.solved) else it
                }
            )
        }
        checkAndUnlockBadges()
    }

    fun deleteDoubt(doubtId: String) {
        updateActiveProfile { p -> p.copy(doubts = p.doubts.filterNot { it.id == doubtId }) }
    }

    fun sendDoubtToAiCoach(doubt: DoubtItem) {
        _uiState.update {
            it.copy(
                selectedTab = AppTab.AI_COACH,
                ai = it.ai.copy(
                    activeSection = AiHubSection.TOOLS,
                    selectedTool = AiTool.DOUBT_SOLVER,
                    doubtSubject = doubt.subject,
                    doubtQuestion = doubt.text
                )
            )
        }
        runDoubtSolver(doubt.subject, doubt.text)
    }

    // ---- Mind Lab Study Focus Trainer ----

    fun recordGameRun(score: Int, wave: Int) {
        val earnedXp = (score / 10).coerceAtLeast(0)
        var isNewBest = false
        updateActiveProfile { p ->
            isNewBest = score > p.bestGameScore
            p.copy(
                bestGameScore = max(p.bestGameScore, score),
                bestGameWave = max(p.bestGameWave, wave),
                gameBonusXp = p.gameBonusXp + earnedXp
            )
        }
        if (isNewBest && score > 0) {
            triggerConfetti()
            showToast("New Mind Lab Best: $score! (+$earnedXp Study XP)")
        } else if (earnedXp > 0) {
            showToast("Mind Lab finished! +$earnedXp Study XP")
        }
        checkAndUnlockBadges()
    }

    // ---- Gemini AI Hub: Multi-turn Chat, Audio Transcribe, Real-Time Voice Live, Tools ----

    fun selectAiHubSection(section: AiHubSection) {
        if (audioRecorder.isRecording) {
            stopAudioRecordingClean()
        }
        _uiState.update {
            it.copy(
                ai = it.ai.copy(
                    activeSection = section,
                    isRecordingTranscript = false,
                    isVoiceRecording = false,
                    micAmplitude = 0f,
                    errorMessage = null
                )
            )
        }
    }

    fun clearAiError() {
        _uiState.update { it.copy(ai = it.ai.copy(errorMessage = null)) }
    }

    fun selectChatMode(mode: AiChatMode) {
        _uiState.update { it.copy(ai = it.ai.copy(chatMode = mode, errorMessage = null)) }
    }

    fun sendChatMessage(text: String) {
        val clean = text.trim()
        val profile = _uiState.value.store.activeProfile ?: return
        if (clean.isEmpty()) return

        val mode = _uiState.value.ai.chatMode
        val priorHistory = _uiState.value.ai.chatMessages
        val userMsg = ChatMessageItem(role = "user", text = clean, modelUsed = mode.modelName)
        val updatedWithUser = (priorHistory + userMsg).takeLast(60)

        updateActiveProfile { it.copy(chatHistory = updatedWithUser) }

        cancelAiJob()
        _uiState.update {
            it.copy(
                ai = it.ai.copy(
                    chatMessages = updatedWithUser,
                    isChatStreaming = true,
                    streamingChatReply = "",
                    errorMessage = null
                )
            )
        }

        aiJob = viewModelScope.launch {
            val sb = StringBuilder()
            try {
                aiService.streamChatReply(
                    profile = profile,
                    history = priorHistory,
                    userMessage = clean,
                    mode = mode
                ).collect { chunk ->
                    sb.append(chunk)
                    _uiState.update {
                        it.copy(ai = it.ai.copy(streamingChatReply = sb.toString()))
                    }
                }
                val finalReply = sb.toString().trim()
                if (finalReply.isNotEmpty()) {
                    val modelMsg = ChatMessageItem(role = "model", text = finalReply, modelUsed = mode.modelName)
                    val finalList = (updatedWithUser + modelMsg).takeLast(60)
                    updateActiveProfile { it.copy(chatHistory = finalList) }
                    _uiState.update {
                        it.copy(
                            ai = it.ai.copy(
                                chatMessages = finalList,
                                isChatStreaming = false,
                                streamingChatReply = ""
                            )
                        )
                    }
                } else {
                    _uiState.update {
                        it.copy(ai = it.ai.copy(isChatStreaming = false, streamingChatReply = ""))
                    }
                }
            } catch (t: TimeoutCancellationException) {
                Log.e(TAG, "Chat stream timed out", t)
                _uiState.update {
                    it.copy(
                        ai = it.ai.copy(
                            isChatStreaming = false,
                            streamingChatReply = "",
                            errorMessage = AiCoachService.friendlyError(t)
                        )
                    )
                }
            } catch (ce: CancellationException) {
                throw ce
            } catch (e: Exception) {
                Log.e(TAG, "Chat stream failed", e)
                _uiState.update {
                    it.copy(
                        ai = it.ai.copy(
                            isChatStreaming = false,
                            streamingChatReply = "",
                            errorMessage = AiCoachService.friendlyError(e)
                        )
                    )
                }
            }
        }
    }

    fun clearChatHistory() {
        cancelAiJob()
        updateActiveProfile { it.copy(chatHistory = emptyList()) }
        _uiState.update {
            it.copy(ai = it.ai.copy(chatMessages = emptyList(), streamingChatReply = "", isChatStreaming = false))
        }
        showToast("Chat history cleared")
    }

    // ---- Audio Transcription ----

    fun startTranscriptRecording() {
        stopTts()
        val res = audioRecorder.startRecording()
        if (res.isFailure) {
            _uiState.update {
                it.copy(ai = it.ai.copy(errorMessage = "Could not start microphone: ${res.exceptionOrNull()?.localizedMessage}"))
            }
            return
        }
        _uiState.update {
            it.copy(
                ai = it.ai.copy(
                    isRecordingTranscript = true,
                    recordingSeconds = 0,
                    micAmplitude = 0f,
                    errorMessage = null
                )
            )
        }
        startMicPollLoop()
    }

    fun stopAndTranscribeAudio() {
        val profile = _uiState.value.store.activeProfile ?: return
        micPollJob?.cancel()
        val audioResult = audioRecorder.stopAndReadBytes()
        _uiState.update {
            it.copy(ai = it.ai.copy(isRecordingTranscript = false, micAmplitude = 0f))
        }
        val bytes = audioResult.getOrElse { err ->
            _uiState.update {
                it.copy(ai = it.ai.copy(errorMessage = err.localizedMessage ?: "Recording was too short. Speak for at least 1 second."))
            }
            return
        }
        cancelAiJob()
        _uiState.update { it.copy(ai = it.ai.copy(isTranscribing = true, errorMessage = null)) }
        aiJob = viewModelScope.launch {
            try {
                val transcript = aiService.transcribeAudio(bytes, profile.aiLanguage)
                _uiState.update {
                    it.copy(ai = it.ai.copy(isTranscribing = false, transcriptText = transcript))
                }
                showToast("Audio transcribed with Gemini!")
            } catch (t: TimeoutCancellationException) {
                _uiState.update {
                    it.copy(
                        ai = it.ai.copy(
                            isTranscribing = false,
                            errorMessage = AiCoachService.friendlyError(t)
                        )
                    )
                }
            } catch (ce: CancellationException) {
                throw ce
            } catch (err: Exception) {
                _uiState.update {
                    it.copy(
                        ai = it.ai.copy(
                            isTranscribing = false,
                            errorMessage = AiCoachService.friendlyError(err)
                        )
                    )
                }
            }
        }
    }

    fun sendTranscriptToChat() {
        val txt = _uiState.value.ai.transcriptText.trim()
        if (txt.isEmpty()) return
        _uiState.update { it.copy(ai = it.ai.copy(activeSection = AiHubSection.CHAT)) }
        sendChatMessage(txt)
    }

    fun sendTranscriptToQuickAdd() {
        val txt = _uiState.value.ai.transcriptText.trim()
        if (txt.isEmpty()) return
        _uiState.update {
            it.copy(ai = it.ai.copy(activeSection = AiHubSection.TOOLS, selectedTool = AiTool.QUICK_ADD))
        }
        runQuickAdd(txt)
    }

    // ---- Real-Time Voice Conversation ----

    fun toggleVoiceAutoSpeak() {
        val next = !_uiState.value.ai.voiceAutoSpeak
        if (!next) stopTts()
        _uiState.update { it.copy(ai = it.ai.copy(voiceAutoSpeak = next)) }
    }

    fun stopVoiceSpeaking() {
        stopTts()
        _uiState.update { it.copy(ai = it.ai.copy(isVoiceSpeaking = false)) }
    }

    fun startVoiceConversationRecording() {
        stopTts()
        val res = audioRecorder.startRecording()
        if (res.isFailure) {
            _uiState.update {
                it.copy(ai = it.ai.copy(errorMessage = "Could not start voice mic: ${res.exceptionOrNull()?.localizedMessage}"))
            }
            return
        }
        _uiState.update {
            it.copy(
                ai = it.ai.copy(
                    isVoiceRecording = true,
                    isVoiceSpeaking = false,
                    recordingSeconds = 0,
                    micAmplitude = 0f,
                    errorMessage = null
                )
            )
        }
        startMicPollLoop()
    }

    fun stopVoiceRecordingAndRespond() {
        val profile = _uiState.value.store.activeProfile ?: return
        micPollJob?.cancel()
        val audioResult = audioRecorder.stopAndReadBytes()
        _uiState.update {
            it.copy(ai = it.ai.copy(isVoiceRecording = false, micAmplitude = 0f))
        }
        val bytes = audioResult.getOrElse { err ->
            _uiState.update {
                it.copy(ai = it.ai.copy(errorMessage = err.localizedMessage ?: "No speech captured. Tap the orb, speak, and tap again to send."))
            }
            return
        }

        val priorContext = _uiState.value.ai.voiceTurns.takeLast(6).map { it.role to it.text }
        val userTurn = VoiceTurn(
            id = "u_${System.currentTimeMillis()}",
            role = "user",
            text = "Spoken voice message (${_uiState.value.ai.recordingSeconds.coerceAtLeast(1)}s)"
        )

        cancelAiJob()
        _uiState.update {
            it.copy(
                ai = it.ai.copy(
                    isVoiceThinking = true,
                    voiceStreamingText = "",
                    voiceTurns = it.ai.voiceTurns + userTurn,
                    errorMessage = null
                )
            )
        }

        aiJob = viewModelScope.launch {
            val sb = StringBuilder()
            try {
                aiService.streamVoiceConversationTurn(
                    profile = profile,
                    recentTurns = priorContext,
                    audioBytes = bytes
                ).collect { chunk ->
                    sb.append(chunk)
                    _uiState.update {
                        it.copy(ai = it.ai.copy(voiceStreamingText = sb.toString()))
                    }
                }
                val finalReply = sb.toString().trim()
                val modelTurn = VoiceTurn(
                    id = "m_${System.currentTimeMillis()}",
                    role = "model",
                    text = finalReply.ifEmpty { "I heard you! Let's keep pushing on your study goals." }
                )
                _uiState.update {
                    it.copy(
                        ai = it.ai.copy(
                            isVoiceThinking = false,
                            voiceStreamingText = "",
                            voiceTurns = it.ai.voiceTurns + modelTurn
                        )
                    )
                }
                if (_uiState.value.ai.voiceAutoSpeak && finalReply.isNotEmpty()) {
                    speakAloud(finalReply, profile.aiLanguage)
                }
            } catch (t: TimeoutCancellationException) {
                Log.e(TAG, "Voice conversation timed out", t)
                _uiState.update {
                    it.copy(
                        ai = it.ai.copy(
                            isVoiceThinking = false,
                            voiceStreamingText = "",
                            errorMessage = AiCoachService.friendlyError(t)
                        )
                    )
                }
            } catch (ce: CancellationException) {
                throw ce
            } catch (e: Exception) {
                Log.e(TAG, "Voice conversation failed", e)
                _uiState.update {
                    it.copy(
                        ai = it.ai.copy(
                            isVoiceThinking = false,
                            voiceStreamingText = "",
                            errorMessage = AiCoachService.friendlyError(e)
                        )
                    )
                }
            }
        }
    }

    fun clearVoiceConversation() {
        stopTts()
        _uiState.update {
            it.copy(ai = it.ai.copy(voiceTurns = emptyList(), voiceStreamingText = "", isVoiceSpeaking = false))
        }
    }

    private fun speakAloud(text: String, language: String) {
        val engine = tts ?: return
        if (!ttsReady) return
        try {
            val locale = if (language.equals("Hindi", ignoreCase = true)) Locale("hi", "IN") else Locale("en", "IN")
            engine.language = locale
            _uiState.update { it.copy(ai = it.ai.copy(isVoiceSpeaking = true)) }
            engine.speak(text, TextToSpeech.QUEUE_FLUSH, null, "voice_reply_${System.currentTimeMillis()}")
            val estMs = (text.length * 62L).coerceIn(2200L, 18_000L)
            viewModelScope.launch {
                delay(estMs)
                if (!engine.isSpeaking) {
                    _uiState.update { it.copy(ai = it.ai.copy(isVoiceSpeaking = false)) }
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "TTS speak failed", e)
            _uiState.update { it.copy(ai = it.ai.copy(isVoiceSpeaking = false)) }
        }
    }

    private fun stopTts() {
        try {
            tts?.stop()
        } catch (_: Exception) {
        }
    }

    private fun startMicPollLoop() {
        micPollJob?.cancel()
        micPollJob = viewModelScope.launch {
            val startedMs = System.currentTimeMillis()
            while (audioRecorder.isRecording) {
                val amp = audioRecorder.getNormalizedAmplitude()
                val sec = ((System.currentTimeMillis() - startedMs) / 1000L).toInt()
                _uiState.update {
                    it.copy(ai = it.ai.copy(micAmplitude = amp, recordingSeconds = sec))
                }
                delay(90L)
            }
        }
    }

    private fun stopAudioRecordingClean() {
        micPollJob?.cancel()
        audioRecorder.stopAndDiscard()
    }

    // ---- Classic AI Coach Tools ----

    fun selectAiTool(tool: AiTool) = _uiState.update { it.copy(ai = it.ai.copy(selectedTool = tool, errorMessage = null)) }

    fun setAiLanguage(lang: String) {
        if (lang !in AI_LANGUAGES) return
        updateActiveProfile { it.copy(aiLanguage = lang) }
    }

    fun updateDoubtDraft(subject: String, question: String) = _uiState.update {
        it.copy(ai = it.ai.copy(doubtSubject = subject, doubtQuestion = question))
    }

    fun stopAiGeneration() {
        cancelAiJob()
        stopTts()
        _uiState.update {
            it.copy(
                ai = it.ai.copy(
                    isStreaming = false,
                    isChatStreaming = false,
                    isTranscribing = false,
                    isVoiceThinking = false,
                    isVoiceSpeaking = false
                )
            )
        }
        showToast("Stopped AI response")
    }

    fun runWeeklyCoach() {
        val profile = _uiState.value.store.activeProfile ?: return
        cancelAiJob()
        _uiState.update {
            it.copy(ai = it.ai.copy(isStreaming = true, streamOutput = "", errorMessage = null))
        }
        aiJob = viewModelScope.launch {
            val sb = StringBuilder()
            try {
                aiService.streamWeeklyCoach(profile, profile.aiLanguage).collect { chunk ->
                    sb.append(chunk)
                    _uiState.update { it.copy(ai = it.ai.copy(streamOutput = sb.toString())) }
                }
                _uiState.update { it.copy(ai = it.ai.copy(isStreaming = false)) }
            } catch (t: TimeoutCancellationException) {
                _uiState.update {
                    it.copy(ai = it.ai.copy(isStreaming = false, errorMessage = AiCoachService.friendlyError(t)))
                }
            } catch (ce: CancellationException) {
                throw ce
            } catch (e: Exception) {
                Log.e(TAG, "Weekly coach failed", e)
                _uiState.update {
                    it.copy(ai = it.ai.copy(isStreaming = false, errorMessage = AiCoachService.friendlyError(e)))
                }
            }
        }
    }

    fun runExamPlanner(examName: String, daysLeft: Int, weakAreas: String) {
        val profile = _uiState.value.store.activeProfile ?: return
        cancelAiJob()
        _uiState.update {
            it.copy(ai = it.ai.copy(isStreaming = true, streamOutput = "", errorMessage = null))
        }
        aiJob = viewModelScope.launch {
            val sb = StringBuilder()
            try {
                aiService.streamExamPlan(profile, examName, daysLeft, weakAreas, profile.aiLanguage).collect { chunk ->
                    sb.append(chunk)
                    _uiState.update { it.copy(ai = it.ai.copy(streamOutput = sb.toString())) }
                }
                _uiState.update { it.copy(ai = it.ai.copy(isStreaming = false)) }
            } catch (t: TimeoutCancellationException) {
                _uiState.update {
                    it.copy(ai = it.ai.copy(isStreaming = false, errorMessage = AiCoachService.friendlyError(t)))
                }
            } catch (ce: CancellationException) {
                throw ce
            } catch (e: Exception) {
                Log.e(TAG, "Exam planner failed", e)
                _uiState.update {
                    it.copy(ai = it.ai.copy(isStreaming = false, errorMessage = AiCoachService.friendlyError(e)))
                }
            }
        }
    }

    fun runDoubtSolver(subject: String, question: String) {
        val profile = _uiState.value.store.activeProfile ?: return
        val q = question.trim()
        if (q.isEmpty()) return
        cancelAiJob()
        _uiState.update {
            it.copy(
                ai = it.ai.copy(
                    doubtSubject = subject,
                    doubtQuestion = q,
                    isStreaming = true,
                    streamOutput = "",
                    errorMessage = null
                )
            )
        }
        aiJob = viewModelScope.launch {
            val sb = StringBuilder()
            try {
                aiService.streamDoubtAnswer(subject, q, profile.aiLanguage).collect { chunk ->
                    sb.append(chunk)
                    _uiState.update { it.copy(ai = it.ai.copy(streamOutput = sb.toString())) }
                }
                _uiState.update { it.copy(ai = it.ai.copy(isStreaming = false)) }
            } catch (t: TimeoutCancellationException) {
                _uiState.update {
                    it.copy(ai = it.ai.copy(isStreaming = false, errorMessage = AiCoachService.friendlyError(t)))
                }
            } catch (ce: CancellationException) {
                throw ce
            } catch (e: Exception) {
                Log.e(TAG, "Doubt solver failed", e)
                _uiState.update {
                    it.copy(ai = it.ai.copy(isStreaming = false, errorMessage = AiCoachService.friendlyError(e)))
                }
            }
        }
    }

    fun runQuickAdd(input: String) {
        val profile = _uiState.value.store.activeProfile ?: return
        val clean = input.trim()
        if (clean.isEmpty()) return
        cancelAiJob()
        _uiState.update { it.copy(ai = it.ai.copy(isStreaming = true, errorMessage = null)) }
        aiJob = viewModelScope.launch {
            try {
                val task = aiService.parseQuickAddTask(clean, profile.subjects)
                updateActiveProfile { p ->
                    p.copy(routine = (p.routine + task).sortedBy { it.startMinutes })
                }
                _uiState.update {
                    it.copy(
                        ai = it.ai.copy(
                            isStreaming = false,
                            lastQuickAddedTaskId = task.id,
                            lastQuickAddedSummary = "${formatTime12h(task.start)} – ${formatTime12h(task.end)} • ${task.title} (${task.category})"
                        )
                    )
                }
                showToast("Added: ${task.title}")
            } catch (t: TimeoutCancellationException) {
                _uiState.update {
                    it.copy(ai = it.ai.copy(isStreaming = false, errorMessage = AiCoachService.friendlyError(t)))
                }
            } catch (ce: CancellationException) {
                throw ce
            } catch (err: Exception) {
                _uiState.update {
                    it.copy(ai = it.ai.copy(isStreaming = false, errorMessage = AiCoachService.friendlyError(err)))
                }
            }
        }
    }

    fun undoQuickAdd() {
        val id = _uiState.value.ai.lastQuickAddedTaskId ?: return
        updateActiveProfile { p -> p.copy(routine = p.routine.filterNot { it.id == id }) }
        _uiState.update {
            it.copy(ai = it.ai.copy(lastQuickAddedTaskId = null, lastQuickAddedSummary = null))
        }
        showToast("Quick-added task removed")
    }

    fun runBuildMyRoutine(goalsPrompt: String) {
        val profile = _uiState.value.store.activeProfile ?: return
        val clean = goalsPrompt.trim().ifEmpty { "Balanced 6-hour study routine with breaks, exercise, and sleep" }
        cancelAiJob()
        _uiState.update {
            it.copy(
                ai = it.ai.copy(
                    isStreaming = true,
                    errorMessage = null,
                    previewRoutine = null,
                    previewSummary = null
                )
            )
        }
        aiJob = viewModelScope.launch {
            try {
                val (summary, tasks) = aiService.generateRoutinePlan(profile, clean, profile.aiLanguage)
                _uiState.update {
                    it.copy(
                        ai = it.ai.copy(
                            isStreaming = false,
                            previewSummary = summary,
                            previewRoutine = tasks
                        )
                    )
                }
            } catch (t: TimeoutCancellationException) {
                _uiState.update {
                    it.copy(ai = it.ai.copy(isStreaming = false, errorMessage = AiCoachService.friendlyError(t)))
                }
            } catch (ce: CancellationException) {
                throw ce
            } catch (err: Exception) {
                _uiState.update {
                    it.copy(ai = it.ai.copy(isStreaming = false, errorMessage = AiCoachService.friendlyError(err)))
                }
            }
        }
    }

    fun applyPreviewRoutine() {
        val preview = _uiState.value.ai.previewRoutine ?: return
        val old = _uiState.value.store.activeProfile?.routine ?: return
        updateActiveProfile { it.copy(routine = preview.sortedBy { t -> t.startMinutes }) }
        _uiState.update {
            it.copy(
                ai = it.ai.copy(
                    previewRoutine = null,
                    previewSummary = null,
                    previousRoutineForUndo = old
                )
            )
        }
        showToast("Applied new routine (${preview.size} tasks)")
    }

    fun discardPreviewRoutine() = _uiState.update {
        it.copy(ai = it.ai.copy(previewRoutine = null, previewSummary = null))
    }

    fun undoAppliedRoutine() {
        val prev = _uiState.value.ai.previousRoutineForUndo ?: return
        updateActiveProfile { it.copy(routine = prev) }
        _uiState.update { it.copy(ai = it.ai.copy(previousRoutineForUndo = null)) }
        showToast("Previous routine restored")
    }

    // ---- Backup / Restore / Export ----

    fun getBackupJson(): String = repo.exportBackupJson(_uiState.value.store)

    fun restoreFromBackupJson(raw: String): Boolean {
        val res = repo.importBackupJson(raw)
        return res.fold(
            onSuccess = { restored ->
                val active = restored.activeProfile
                _uiState.update {
                    it.copy(
                        store = restored,
                        selectedTab = AppTab.CHECKLIST,
                        selectedDate = todayKey(),
                        showProfileSheet = false,
                        ai = it.ai.copy(chatMessages = active?.chatHistory.orEmpty())
                    )
                }
                showToast("Backup restored (${restored.profiles.size} profile${if (restored.profiles.size == 1) "" else "s"})")
                true
            },
            onFailure = { err ->
                showToast(err.localizedMessage ?: "Invalid backup JSON")
                false
            }
        )
    }

    fun getCsvExport(): String {
        val p = _uiState.value.store.activeProfile ?: return ""
        return repo.exportCsv(p)
    }

    fun getWeeklyReportText(): String {
        val p = _uiState.value.store.activeProfile ?: return ""
        return repo.buildWeeklyReportText(p, _uiState.value.weeklyStartMonday)
    }

    // ---- In-App GitHub Update Checker & Notification ----

    fun checkForAppUpdate(silent: Boolean = false) {
        updateCheckJob?.cancel()
        if (!silent) {
            _uiState.update { it.copy(updateState = UpdateCheckState.Checking) }
        }
        updateCheckJob = viewModelScope.launch {
            val token = UpdateNotificationHelper.getSavedGitHubToken(getApplication())
            val result = appUpdateService.checkLatestRelease(authToken = token)
            if (silent && result !is UpdateCheckState.UpdateAvailable) {
                // Keep UI quiet on silent startup checks when offline or already up to date
                return@launch
            }
            _uiState.update {
                it.copy(
                    updateState = result,
                    updateBannerDismissed = if (result is UpdateCheckState.UpdateAvailable) false else it.updateBannerDismissed
                )
            }
            when (result) {
                is UpdateCheckState.UpdateAvailable -> {
                    UpdateNotificationHelper.notifyUpdateAvailable(getApplication(), result)
                    if (!silent) {
                        showToast("Update available: ${result.latestVersionName}")
                    }
                }
                is UpdateCheckState.UpToDate -> {
                    if (!silent) showToast("You're on the latest version (${result.currentVersionName})")
                }
                is UpdateCheckState.Error -> {
                    if (!silent) showToast("Update check failed — check Profile > App Updates")
                }
                else -> Unit
            }
        }
    }

    fun dismissUpdateBanner() {
        _uiState.update { it.copy(updateBannerDismissed = true) }
    }

    fun saveGitHubUpdateToken(token: String) {
        val clean = token.trim()
        UpdateNotificationHelper.saveGitHubToken(getApplication(), clean)
        _uiState.update { it.copy(hasSavedGitHubToken = clean.isNotBlank()) }
        showToast(if (clean.isNotBlank()) "GitHub token saved — checking updates..." else "GitHub token cleared")
        if (clean.isNotBlank()) {
            checkForAppUpdate(silent = false)
        }
    }

    // ---- Helpers ----

    private fun checkAndUnlockBadges() {
        val p = _uiState.value.store.activeProfile ?: return
        val allUnlocked = Analytics.evaluateUnlockedBadges(p)
        val newlyUnlocked = allUnlocked - p.unlockedBadges
        if (newlyUnlocked.isNotEmpty()) {
            updateActiveProfile { cur -> cur.copy(unlockedBadges = allUnlocked) }
            val title = ALL_BADGES.firstOrNull { it.id == newlyUnlocked.first() }?.title ?: "New Badge"
            triggerConfetti()
            showToast("Badge unlocked: $title!")
        }
    }

    fun triggerConfetti() = _uiState.update { it.copy(confettiBurstId = it.confettiBurstId + 1) }

    fun showToast(message: String) {
        toastJob?.cancel()
        _uiState.update { it.copy(toastMessage = message) }
        toastJob = viewModelScope.launch {
            delay(2600L)
            _uiState.update { it.copy(toastMessage = null) }
        }
    }

    private fun cancelAiJob() {
        aiJob?.cancel()
        aiJob = null
    }

    private fun updateStore(transform: (DrstStore) -> DrstStore) {
        val next = transform(_uiState.value.store)
        repo.saveStore(next)
        _uiState.update { it.copy(store = next) }
    }

    private fun updateActiveProfile(transform: (UserProfile) -> UserProfile) {
        val activeId = _uiState.value.store.activeProfileId ?: return
        updateStore { store ->
            store.copy(
                profiles = store.profiles.map { p ->
                    if (p.id == activeId) transform(p) else p
                }
            )
        }
    }

    override fun onCleared() {
        super.onCleared()
        timerJob?.cancel()
        aiJob?.cancel()
        micPollJob?.cancel()
        audioRecorder.stopAndDiscard()
        try {
            tts?.stop()
            tts?.shutdown()
        } catch (_: Exception) {
        }
    }
}
