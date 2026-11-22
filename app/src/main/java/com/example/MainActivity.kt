package com.example

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.graphics.Color as AndroidColor
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.data.*
import com.example.ui.components.ConfettiOverlay
import com.example.ui.components.FloatingTabBar
import com.example.ui.components.HeaderBand
import com.example.ui.components.LiquidGlassBackdrop
import com.example.ui.components.ToastBanner
import com.example.ui.components.backdropBlurIfSupported
import com.example.ui.screens.*
import com.example.ui.sheets.EditRoutineSheet
import com.example.ui.sheets.ProfileSheet
import com.example.ui.theme.StudyTrackerTheme
import com.example.viewmodel.TrackerUiState
import com.example.viewmodel.TrackerViewModel

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // STEP 1: Configure Firebase App Check with launch intent BEFORE UI or ViewModels (for Firebase AI Logic)
        configureAppCheck(this, intent)

        // STEP 2: Enable edge-to-edge full bleed
        enableEdgeToEdge()

        setContent {
            val vm: TrackerViewModel = viewModel()
            val state by vm.uiState.collectAsStateWithLifecycle()
            val sysDark = isSystemInDarkTheme()
            val effectiveDark = when (state.store.theme) {
                "dark" -> true
                "light" -> false
                else -> sysDark
            }

            SideEffect {
                enableEdgeToEdge(
                    statusBarStyle = if (effectiveDark) {
                        SystemBarStyle.dark(AndroidColor.TRANSPARENT)
                    } else {
                        SystemBarStyle.light(AndroidColor.TRANSPARENT, AndroidColor.TRANSPARENT)
                    },
                    navigationBarStyle = SystemBarStyle.auto(AndroidColor.TRANSPARENT, AndroidColor.TRANSPARENT)
                )
            }

            StudyTrackerTheme(themeMode = state.store.theme) {
                StudyTrackerRoot(vm = vm, state = state)
            }
        }
    }
}

@Composable
private fun StudyTrackerRoot(
    vm: TrackerViewModel,
    state: TrackerUiState
) {
    val context = LocalContext.current

    // SAF zero-permission file export & import launchers
    val saveBackupLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json")
    ) { uri ->
        if (uri != null) {
            runCatching {
                context.contentResolver.openOutputStream(uri)?.use { out ->
                    out.write(vm.getBackupJson().toByteArray(Charsets.UTF_8))
                }
            }.onSuccess {
                vm.showToast("Backup file saved")
            }.onFailure {
                vm.showToast("Could not save backup file")
            }
        }
    }

    val restoreBackupLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            runCatching {
                context.contentResolver.openInputStream(uri)?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }
            }.onSuccess { text ->
                if (!text.isNullOrBlank()) vm.restoreFromBackupJson(text)
            }.onFailure {
                vm.showToast("Could not read backup file")
            }
        }
    }

    val exportCsvLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("text/csv")
    ) { uri ->
        if (uri != null) {
            runCatching {
                context.contentResolver.openOutputStream(uri)?.use { out ->
                    out.write(vm.getCsvExport().toByteArray(Charsets.UTF_8))
                }
            }.onSuccess {
                vm.showToast("CSV exported")
            }.onFailure {
                vm.showToast("Could not export CSV")
            }
        }
    }

    val saveWeeklyReportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("text/plain")
    ) { uri ->
        if (uri != null) {
            runCatching {
                context.contentResolver.openOutputStream(uri)?.use { out ->
                    out.write(vm.getWeeklyReportText().toByteArray(Charsets.UTF_8))
                }
            }.onSuccess {
                vm.showToast("Weekly report saved")
            }.onFailure {
                vm.showToast("Could not save report")
            }
        }
    }

    fun copyToClipboard(label: String, text: String, toast: String) {
        val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
        cm?.setPrimaryClip(ClipData.newPlainText(label, text))
        vm.showToast(toast)
    }

    fun shareOnWhatsApp(text: String) {
        val waIntent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, text)
            setPackage("com.whatsapp")
        }
        val fallback = Intent.createChooser(
            Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_TEXT, text)
            },
            "Share Weekly Report"
        )
        runCatching { context.startActivity(waIntent) }
            .onFailure { context.startActivity(fallback) }
    }

    fun openExternalUrl(url: String) {
        val viewIntent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
        runCatching { context.startActivity(viewIntent) }
            .onFailure { vm.showToast("Could not open browser for update link") }
    }

    val activeProfile = state.store.activeProfile

    Box(modifier = Modifier.fillMaxSize()) {
        when {
            state.showSplash -> {
                SplashScreen(onFinished = { vm.dismissSplash() })
            }

            state.showNewProfilePrompt -> {
                NamePromptScreen(
                    isCreatingAdditional = state.store.profiles.isNotEmpty(),
                    onCreateProfile = { vm.createProfile(it) },
                    onCancel = if (state.store.profiles.isNotEmpty()) {
                        { vm.setNewProfilePromptVisible(false) }
                    } else null
                )
            }

            activeProfile == null && state.store.profiles.isEmpty() -> {
                NamePromptScreen(
                    isCreatingAdditional = false,
                    onCreateProfile = { vm.createProfile(it) }
                )
            }

            activeProfile == null -> {
                ProfilePickerScreen(
                    profiles = state.store.profiles,
                    onSelectProfile = { vm.selectProfile(it) },
                    onNewProfile = { vm.setNewProfilePromptVisible(true) }
                )
            }

            else -> {
                BackHandler(
                    enabled = state.selectedTab != AppTab.CHECKLIST &&
                        !state.showEditRoutineSheet &&
                        !state.showProfileSheet &&
                        !state.showGameOverlay
                ) {
                    vm.selectTab(AppTab.CHECKLIST)
                }

                val streak = remember(activeProfile) { Analytics.calculateStreak(activeProfile, todayKey()) }
                val isRestDay = activeProfile.days[state.selectedDate]?.isRestDay == true
                val anyModalOpen = state.showEditRoutineSheet || state.showProfileSheet || state.showGameOverlay
                val modalBackdropBlur by animateDpAsState(
                    targetValue = if (anyModalOpen) 18.dp else 0.dp,
                    animationSpec = tween(durationMillis = 220),
                    label = "modal_backdrop_blur"
                )

                LiquidGlassBackdrop {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .backdropBlurIfSupported(modalBackdropBlur)
                    ) {
                        HeaderBand(
                            profile = activeProfile,
                            selectedDate = state.selectedDate,
                            streakDays = streak,
                            isRestDay = isRestDay,
                            onPrevDay = { vm.shiftSelectedDate(-1) },
                            onNextDay = { vm.shiftSelectedDate(1) },
                            onPickDate = { vm.selectDate(it) },
                            onJumpToday = { vm.jumpToToday() },
                            onOpenEditRoutine = { vm.setEditRoutineSheetVisible(true) },
                            onOpenProfile = { vm.setProfileSheetVisible(true) }
                        )

                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxWidth(),
                            contentAlignment = Alignment.TopCenter
                        ) {
                            Box(
                                modifier = Modifier
                                    .widthIn(max = 960.dp)
                                    .fillMaxSize()
                            ) {
                                Crossfade(
                                    targetState = state.selectedTab,
                                    animationSpec = tween(durationMillis = 190),
                                    label = "tab_crossfade"
                                ) { tab ->
                                    when (tab) {
                                    AppTab.CHECKLIST -> ChecklistScreen(
                                        profile = activeProfile,
                                        selectedDate = state.selectedDate,
                                        filter = state.checklistFilter,
                                        onSelectFilter = vm::setChecklistFilter,
                                        onToggleTask = vm::toggleTaskCompletion,
                                        onUpdateNote = vm::updateTaskNote,
                                        onAdjustStudyGoal = vm::adjustStudyGoal,
                                        onToggleRestDay = vm::toggleRestDay,
                                        onResetDay = vm::resetCurrentDay
                                    )

                                    AppTab.DASHBOARD -> DashboardScreen(
                                        profile = activeProfile,
                                        selectedDate = state.selectedDate,
                                        onOpenGame = { vm.setGameOverlayVisible(true) }
                                    )

                                    AppTab.WEEKLY -> WeeklyScreen(
                                        profile = activeProfile,
                                        weekStartMonday = state.weeklyStartMonday,
                                        onPrevWeek = { vm.shiftWeek(-1) },
                                        onNextWeek = { vm.shiftWeek(1) },
                                        onJumpCurrentWeek = vm::jumpToCurrentWeek,
                                        onOpenDay = vm::openDayOnChecklist,
                                        onCopyReport = {
                                            copyToClipboard("Weekly Report", vm.getWeeklyReportText(), "Weekly report copied")
                                        },
                                        onShareWhatsApp = { shareOnWhatsApp(vm.getWeeklyReportText()) },
                                        onSaveReportFile = {
                                            saveWeeklyReportLauncher.launch("weekly-report-${state.weeklyStartMonday}.txt")
                                        }
                                    )

                                    AppTab.FOCUS -> FocusScreen(
                                        profile = activeProfile,
                                        timer = state.timer,
                                        doubtFilter = state.doubtFilter,
                                        onSelectSubject = vm::selectFocusSubject,
                                        onAddSubject = vm::addSubject,
                                        onRenameSubject = vm::renameSubject,
                                        onDeleteSubject = vm::deleteSubject,
                                        onSetPresetMinutes = vm::setFocusPresetMinutes,
                                        onStartOrResume = vm::startOrResumeTimer,
                                        onPause = vm::pauseTimer,
                                        onSaveAndStop = vm::saveAndStopTimer,
                                        onResetTimer = vm::resetTimerState,
                                        onAddExam = vm::addExam,
                                        onDeleteExam = vm::deleteExam,
                                        onSetDoubtFilter = vm::setDoubtFilter,
                                        onAddDoubt = vm::addDoubt,
                                        onToggleDoubtSolved = vm::toggleDoubtSolved,
                                        onDeleteDoubt = vm::deleteDoubt,
                                        onAskAiDoubt = vm::sendDoubtToAiCoach,
                                        onOpenGame = { vm.setGameOverlayVisible(true) }
                                    )

                                    AppTab.AI_COACH -> AiCoachScreen(
                                        profile = activeProfile,
                                        aiState = state.ai,
                                        onSelectSection = vm::selectAiHubSection,
                                        onSelectChatMode = vm::selectChatMode,
                                        onSendChatMessage = vm::sendChatMessage,
                                        onClearChatHistory = vm::clearChatHistory,
                                        onStartTranscriptRecording = vm::startTranscriptRecording,
                                        onStopAndTranscribeAudio = vm::stopAndTranscribeAudio,
                                        onCopyTranscript = { txt ->
                                            copyToClipboard("Transcript", txt, "Transcript copied")
                                        },
                                        onSendTranscriptToChat = vm::sendTranscriptToChat,
                                        onSendTranscriptToQuickAdd = vm::sendTranscriptToQuickAdd,
                                        onSaveTranscriptAsDoubt = { txt ->
                                            val subj = activeProfile.subjects.firstOrNull() ?: "General"
                                            vm.addDoubt(subj, txt)
                                            vm.showToast("Saved transcript to Doubt Notebook")
                                        },
                                        onToggleVoiceAutoSpeak = vm::toggleVoiceAutoSpeak,
                                        onStopVoiceSpeaking = vm::stopVoiceSpeaking,
                                        onStartVoiceRecording = vm::startVoiceConversationRecording,
                                        onStopVoiceRecordingAndRespond = vm::stopVoiceRecordingAndRespond,
                                        onClearVoiceConversation = vm::clearVoiceConversation,
                                        onSelectTool = vm::selectAiTool,
                                        onSetLanguage = vm::setAiLanguage,
                                        onRunWeeklyCoach = vm::runWeeklyCoach,
                                        onRunBuildRoutine = vm::runBuildMyRoutine,
                                        onApplyPreviewRoutine = vm::applyPreviewRoutine,
                                        onDiscardPreviewRoutine = vm::discardPreviewRoutine,
                                        onUndoAppliedRoutine = vm::undoAppliedRoutine,
                                        onRunQuickAdd = vm::runQuickAdd,
                                        onUndoQuickAdd = vm::undoQuickAdd,
                                        onRunExamPlanner = vm::runExamPlanner,
                                        onUpdateDoubtDraft = vm::updateDoubtDraft,
                                        onRunDoubtSolver = vm::runDoubtSolver,
                                        onStopAi = vm::stopAiGeneration,
                                        onDismissError = vm::clearAiError,
                                            onCopyOutput = { txt ->
                                                copyToClipboard("AI Response", txt, "AI response copied")
                                            }
                                        )
                                    }
                                }
                            }

                            // Floating Liquid Glass Bottom Tab Pill
                            FloatingTabBar(
                                selectedTab = state.selectedTab,
                                onSelectTab = vm::selectTab,
                                modifier = Modifier.align(Alignment.BottomCenter)
                            )
                        }
                    }

                    // Edit Routine full-screen modal sheet
                    if (state.showEditRoutineSheet) {
                        EditRoutineSheet(
                            profile = activeProfile,
                            onSaveRoutine = vm::saveRoutine,
                            onRestoreDefaults = vm::restoreDefault19Routine,
                            onDismiss = { vm.setEditRoutineSheetVisible(false) }
                        )
                    }

                    // Profile & Backup full-screen modal sheet
                    if (state.showProfileSheet) {
                        ProfileSheet(
                            profile = activeProfile,
                            themeMode = state.store.theme,
                            updateState = state.updateState,
                            onCheckForUpdates = vm::checkForAppUpdate,
                            onOpenUpdateUrl = ::openExternalUrl,
                            onSaveDetails = vm::updateProfileDetails,
                            onSetThemeMode = vm::setThemeMode,
                            onSaveBackupFile = { saveBackupLauncher.launch("drst-backup-${todayKey()}.json") },
                            onCopyBackupJson = {
                                copyToClipboard("DRST Backup", vm.getBackupJson(), "Backup JSON copied")
                            },
                            onRestoreBackupFile = { restoreBackupLauncher.launch(arrayOf("application/json", "text/plain", "*/*")) },
                            onRestoreBackupText = { vm.restoreFromBackupJson(it) },
                            onExportCsv = { exportCsvLauncher.launch("study-tracker-${todayKey()}.csv") },
                            onLogout = vm::logoutProfile,
                            onDeleteProfile = vm::deleteActiveProfile,
                            onDismiss = { vm.setProfileSheetVisible(false) }
                        )
                    }

                    // Full-viewport Mind Lab • Study Focus Trainer Overlay
                    if (state.showGameOverlay) {
                        MindFocusGameScreen(
                            bestScore = activeProfile.bestGameScore,
                            bestLevel = activeProfile.bestGameWave,
                            onFinishRun = vm::recordGameRun,
                            onExit = { vm.setGameOverlayVisible(false) }
                        )
                    }
                }
            }
        }

        // Confetti Celebration Overlay
        ConfettiOverlay(burstId = state.confettiBurstId)

        // Floating Toast Banner
        ToastBanner(
            message = state.toastMessage,
            modifier = Modifier
                .align(Alignment.TopCenter)
                .statusBarsPadding()
                .padding(top = 12.dp, start = 16.dp, end = 16.dp)
        )
    }
}
