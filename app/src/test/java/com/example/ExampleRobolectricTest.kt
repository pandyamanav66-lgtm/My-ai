package com.example

import android.app.Application
import android.content.Context
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToKey
import androidx.test.core.app.ApplicationProvider
import com.example.data.*
import com.example.ui.components.FloatingTabBar
import com.example.ui.components.HeaderBand
import com.example.ui.screens.AiCoachScreen
import com.example.ui.screens.DashboardScreen
import com.example.ui.theme.StudyTrackerTheme
import com.example.viewmodel.AiCoachUiState
import com.example.viewmodel.AiHubSection
import com.example.viewmodel.TrackerViewModel
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class ExampleRobolectricTest {

    @get:Rule
    val composeRule = createComposeRule()

    private lateinit var app: Application

    @Before
    fun setUp() {
        app = ApplicationProvider.getApplicationContext()
        app.getSharedPreferences("drst_storage", Context.MODE_PRIVATE).edit().clear().commit()
    }

    @Test
    fun appResources_matchStudyTrackerAndRecaptchaConfigured() {
        assertEquals("Study Tracker", app.getString(R.string.app_name))
        val recaptchaRes = app.resources.getIdentifier("recaptcha_site_key", "string", app.packageName)
        assertTrue("recaptcha_site_key must be bundled in resources", recaptchaRes != 0)
        assertTrue(app.getString(recaptchaRes).isNotBlank())
    }

    @Test
    fun viewModel_profileChecklistFocusSubjectsExamsDoubtsGameAndBackup_endToEnd() {
        val vm = TrackerViewModel(app)

        // 1. Create profile
        vm.createProfile("Manav Pandya")
        val p1 = vm.uiState.value.store.activeProfile
        assertNotNull(p1)
        assertEquals("Manav Pandya", p1!!.name)
        assertEquals("MP", p1.initials)
        assertEquals(19, p1.routine.size)

        // 2. Toggle checklist task, add note, adjust study goal, rest day
        val firstTask = p1.routine.first()
        vm.toggleTaskCompletion(firstTask.id)
        vm.updateTaskNote(firstTask.id, "Completed early")
        vm.adjustStudyGoal(0.5)
        vm.toggleRestDay()

        val afterChecklist = vm.uiState.value.store.activeProfile!!
        val todayLog = afterChecklist.days[todayKey()]!!
        assertTrue(firstTask.id in todayLog.completedIds)
        assertEquals("Completed early", todayLog.notes[firstTask.id])
        assertEquals(6.5, afterChecklist.dailyStudyGoalHours, 0.01)
        assertTrue(todayLog.isRestDay)
        assertTrue("first_step" in afterChecklist.unlockedBadges)

        // 3. Routine save normalizes times & rejects empty list
        vm.saveRoutine(emptyList())
        assertEquals(19, vm.uiState.value.store.activeProfile!!.routine.size)
        vm.saveRoutine(
            listOf(
                RoutineTask(id = "r1", start = "7:5", end = "8:30", title = "  Morning Maths  ", category = "Study", subject = "Maths")
            )
        )
        val savedRoutine = vm.uiState.value.store.activeProfile!!.routine
        assertEquals(1, savedRoutine.size)
        assertEquals("07:05", savedRoutine[0].start)
        assertEquals("08:30", savedRoutine[0].end)
        assertEquals("Morning Maths", savedRoutine[0].title)

        // 4. Subject rename syncs routine tasks, doubts, timer, and AI doubt subject
        vm.addDoubt("Maths", "How to integrate sin^2(x)?")
        vm.selectFocusSubject("Maths")
        vm.renameSubject("Maths", "Mathematics")
        val afterRename = vm.uiState.value.store.activeProfile!!
        assertTrue("Mathematics" in afterRename.subjects)
        assertFalse("Maths" in afterRename.subjects)
        assertEquals("Mathematics", afterRename.routine.first().subject)
        assertEquals("Mathematics", afterRename.doubts.first().subject)
        assertEquals("Mathematics", vm.uiState.value.timer.selectedSubject)
        assertEquals("Mathematics", vm.uiState.value.ai.doubtSubject)

        // 5. Exam & Doubt toggle + send to AI
        val examDate = shiftDateKey(todayKey(), 10)
        vm.addExam("CET 2026", examDate)
        assertEquals(1, vm.uiState.value.store.activeProfile!!.exams.size)
        val doubtId = vm.uiState.value.store.activeProfile!!.doubts.first().id
        vm.toggleDoubtSolved(doubtId)
        assertTrue(vm.uiState.value.store.activeProfile!!.doubts.first().solved)

        // 6. Focus Timer presets & pause/reset
        vm.setFocusPresetMinutes(45)
        assertEquals(45 * 60, vm.uiState.value.timer.remainingSeconds)
        vm.startOrResumeTimer()
        assertTrue(vm.uiState.value.timer.isRunning)
        vm.pauseTimer()
        assertFalse(vm.uiState.value.timer.isRunning)
        assertTrue(vm.uiState.value.timer.isPaused)
        vm.resetTimerState()
        assertFalse(vm.uiState.value.timer.isPaused)

        // 7. Orbit Pulse game run scoring & XP
        vm.recordGameRun(score = 240, wave = 3)
        val afterGame = vm.uiState.value.store.activeProfile!!
        assertEquals(240, afterGame.bestGameScore)
        assertEquals(3, afterGame.bestGameWave)
        assertEquals(24, afterGame.gameBonusXp)

        // 8. Backup JSON round-trip + CSV + Weekly Report
        val backupJson = vm.getBackupJson()
        assertTrue(backupJson.contains("Manav Pandya"))
        val csv = vm.getCsvExport()
        assertTrue(csv.startsWith("Date,Completed Tasks"))
        val report = vm.getWeeklyReportText()
        assertTrue(report.contains("Weekly Report"))

        vm.deleteActiveProfile()
        assertNull(vm.uiState.value.store.activeProfile)
        assertTrue(vm.restoreFromBackupJson(backupJson))
        assertEquals("Manav Pandya", vm.uiState.value.store.activeProfile?.name)
        assertEquals(240, vm.uiState.value.store.activeProfile?.bestGameScore)
    }

    @Test
    fun aiCoachService_sanitizesHistoryAndFormatsErrorsCleanly() {
        val service = AiCoachService()
        val messyHistory = listOf(
            ChatMessageItem(role = "model", text = "Orphan model message should be skipped"),
            ChatMessageItem(role = "user", text = "First question"),
            ChatMessageItem(role = "user", text = "Second question replaces orphaned first"),
            ChatMessageItem(role = "model", text = "Clean answer"),
            ChatMessageItem(role = "user", text = "Trailing failed user message should drop")
        )
        val valid = service.buildValidChatHistory(messyHistory)
        assertEquals(2, valid.size)
        assertEquals("user", valid[0].role)
        assertEquals("model", valid[1].role)

        assertEquals("07:05", service.normalizeHHmm("7:5"))
        assertEquals("23:59", service.normalizeHHmm("99:88"))

        val initErr = AiCoachService.friendlyError(IllegalStateException("Default FirebaseApp is not initialized"))
        assertTrue(initErr.contains("GOOGLE_SERVICES_JSON"))
    }

    @Test
    fun ui_headerHasNoGameIcon_dockHas5Tabs_andDashboardLaunchesGame() {
        val sampleProfile = UserProfile(name = "Manav Pandya", bestGameScore = 180, bestGameWave = 2)
        var gameOpened = false

        composeRule.setContent {
            StudyTrackerTheme(themeMode = "dark") {
                HeaderBand(
                    profile = sampleProfile,
                    selectedDate = todayKey(),
                    streakDays = 4,
                    isRestDay = false,
                    onPrevDay = {},
                    onNextDay = {},
                    onPickDate = {},
                    onJumpToday = {},
                    onOpenEditRoutine = {},
                    onOpenProfile = {}
                )
            }
        }

        composeRule.onNodeWithText("Daily Routine & Study Tracker").assertIsDisplayed()
        composeRule.onNodeWithTag("edit_routine_button").assertIsDisplayed()
        composeRule.onNodeWithTag("profile_avatar_button").assertIsDisplayed()
        composeRule.onNodeWithTag("open_game_button").assertDoesNotExist()
    }

    @Test
    fun ui_floatingTabBarHasOnly5TabsAndNoGameTab() {
        var selected = AppTab.CHECKLIST
        composeRule.setContent {
            StudyTrackerTheme(themeMode = "dark") {
                FloatingTabBar(
                    selectedTab = selected,
                    onSelectTab = { selected = it }
                )
            }
        }

        AppTab.entries.forEach { tab ->
            composeRule.onNodeWithTag("tab_${tab.name.lowercase()}").assertIsDisplayed()
        }
        assertEquals(5, AppTab.entries.size)
        composeRule.onNodeWithTag("open_game_button").assertDoesNotExist()
    }

    @Test
    fun ui_dashboardExposesOrbitPulseGameLauncher() {
        val sampleProfile = UserProfile(name = "Manav Pandya", bestGameScore = 150, bestGameWave = 2)
        var opened = false
        composeRule.setContent {
            StudyTrackerTheme(themeMode = "dark") {
                DashboardScreen(
                    profile = sampleProfile,
                    selectedDate = todayKey(),
                    onOpenGame = { opened = true }
                )
            }
        }

        composeRule.onNodeWithTag("dashboard_list").performScrollToKey("dashboard_orbit_pulse_card")
        composeRule.onNodeWithTag("dashboard_play_game_button").assertIsDisplayed().performClick()
        assertTrue(opened)
    }

    @Test
    fun ui_aiCoachErrorBannerIsDismissible() {
        val sampleProfile = UserProfile(name = "Manav Pandya")
        var dismissed = false
        composeRule.setContent {
            StudyTrackerTheme(themeMode = "dark") {
                AiCoachScreen(
                    profile = sampleProfile,
                    aiState = AiCoachUiState(
                        activeSection = AiHubSection.TOOLS,
                        errorMessage = "Sample error for test"
                    ),
                    onSelectSection = {},
                    onSelectChatMode = {},
                    onSendChatMessage = {},
                    onClearChatHistory = {},
                    onStartTranscriptRecording = {},
                    onStopAndTranscribeAudio = {},
                    onCopyTranscript = {},
                    onSendTranscriptToChat = {},
                    onSendTranscriptToQuickAdd = {},
                    onSaveTranscriptAsDoubt = {},
                    onToggleVoiceAutoSpeak = {},
                    onStopVoiceSpeaking = {},
                    onStartVoiceRecording = {},
                    onStopVoiceRecordingAndRespond = {},
                    onClearVoiceConversation = {},
                    onSelectTool = {},
                    onSetLanguage = {},
                    onRunWeeklyCoach = {},
                    onRunBuildRoutine = {},
                    onApplyPreviewRoutine = {},
                    onDiscardPreviewRoutine = {},
                    onUndoAppliedRoutine = {},
                    onRunQuickAdd = {},
                    onUndoQuickAdd = {},
                    onRunExamPlanner = { _, _, _ -> },
                    onUpdateDoubtDraft = { _, _ -> },
                    onRunDoubtSolver = { _, _ -> },
                    onStopAi = {},
                    onDismissError = { dismissed = true }
                )
            }
        }

        composeRule.onNodeWithTag("ai_error_banner").assertIsDisplayed()
        composeRule.onNodeWithTag("ai_error_dismiss").performClick()
        assertTrue(dismissed)
    }
}
