package com.example

import android.Manifest
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color as AndroidColor
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.data.*
import com.example.ui.components.ConfettiOverlay
import com.example.ui.components.FloatingTabBar
import com.example.ui.components.HeaderBand
import com.example.ui.components.LiquidGlassBackdrop
import com.example.ui.components.ToastBanner
import com.example.ui.components.UpdateAvailableBanner
import com.example.ui.components.backdropBlurIfSupported
import com.example.ui.screens.*
import com.example.ui.sheets.EditRoutineSheet
import com.example.ui.sheets.ProfileSheet
import com.example.ui.theme.StudyTrackerTheme
import com.example.viewmodel.TrackerUiState
import com.example.viewmodel.TrackerViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : ComponentActivity() {
    private val openUpdateTrigger = mutableIntStateOf(0)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // STEP 1: Configure Firebase App Check with launch intent BEFORE UI or ViewModels (for Firebase AI Logic)
        configureAppCheck(this, intent)

        // STEP 2: Unlock native high refresh rate (90Hz / 120Hz) on OEM Android displays
        enableHighRefreshRate()

        // STEP 3: Ensure update notification channel exists & schedule background periodic checks
        UpdateNotificationHelper.ensureChannel(this)
        UpdateCheckScheduler.schedulePeriodicCheck(this)
        if (intent?.getBooleanExtra(UpdateNotificationHelper.EXTRA_OPEN_UPDATE, false) == true) {
            openUpdateTrigger.intValue++
        }

        // STEP 4: Enable edge-to-edge full bleed
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

            val triggerCount by openUpdateTrigger
            LaunchedEffect(triggerCount) {
                if (triggerCount > 0) {
                    vm.setProfileSheetVisible(true)
                    vm.checkForAppUpdate(silent = false)
                }
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

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        if (intent.getBooleanExtra(UpdateNotificationHelper.EXTRA_OPEN_UPDATE, false)) {
            openUpdateTrigger.intValue++
        }
    }

    @Suppress("DEPRECATION")
    private fun enableHighRefreshRate() {
        runCatching {
            val activeDisplay = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                display
            } else {
                windowManager.defaultDisplay
            } ?: return
            val currentMode = activeDisplay.mode
            val bestMode = activeDisplay.supportedModes
                .filter {
                    it.physicalWidth == currentMode.physicalWidth &&
                        it.physicalHeight == currentMode.physicalHeight
                }
                .maxByOrNull { it.refreshRate }
            if (bestMode != null && bestMode.refreshRate > currentMode.refreshRate) {
                val lp = window.attributes
                lp.preferredDisplayModeId = bestMode.modeId
                window.attributes = lp
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
    val scope = rememberCoroutineScope()

    // Zero-permission Android Photo Picker for AI Coach Photo Doubt Solver
    val pickDoubtPhotoLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) {
            scope.launch(Dispatchers.IO) {
                val bmp = decodeScaledBitmap(context, uri, maxDim = 1280)
                withContext(Dispatchers.Main) {
                    if (bmp != null) {
                        vm.setAttachedPhoto(bmp)
                    } else {
                        vm.showToast("Could not read selected photo")
                    }
                }
            }
        }
    }

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
                    onCreateProfile = { name, preset -> vm.createProfile(name, preset) },
                    onCancel = if (state.store.profiles.isNotEmpty()) {
                        { vm.setNewProfilePromptVisible(false) }
                    } else null
                )
            }

            activeProfile == null && state.store.profiles.isEmpty() -> {
                NamePromptScreen(
                    isCreatingAdditional = false,
                    onCreateProfile = { name, preset -> vm.createProfile(name, preset) }
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
                    targetValue = if (anyModalOpen) 14.dp else 0.dp,
                    animationSpec = tween(durationMillis = 140),
                    label = "modal_backdrop_blur"
                )

                // Request Android 13+ notification permission once cleanly so new-update alerts can notify the user
                val notifPermissionLauncher = rememberLauncherForActivityResult(
                    ActivityResultContracts.RequestPermission()
                ) { granted ->
                    if (granted) {
                        vm.onNotificationPermissionGranted()
                    }
                }

                // Automatically re-check GitHub Releases whenever user returns to Study Tracker
                val lifecycleOwner = LocalLifecycleOwner.current
                DisposableEffect(lifecycleOwner) {
                    val observer = LifecycleEventObserver { _, event ->
                        if (event == Lifecycle.Event.ON_RESUME) {
                            vm.checkForAppUpdateOnResume()
                        }
                    }
                    lifecycleOwner.lifecycle.addObserver(observer)
                    onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
                }

                LaunchedEffect(activeProfile.id) {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        val granted = ContextCompat.checkSelfPermission(
                            context,
                            Manifest.permission.POST_NOTIFICATIONS
                        ) == PackageManager.PERMISSION_GRANTED
                        if (!granted) {
                            notifPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                        }
                    }
                }

                fun requestOrOpenNotificationSettings() {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                        ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
                    ) {
                        notifPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                    } else {
                        val settingsIntent = Intent().apply {
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                                action = Settings.ACTION_APP_NOTIFICATION_SETTINGS
                                putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
                            } else {
                                action = Settings.ACTION_APPLICATION_DETAILS_SETTINGS
                                data = Uri.fromParts("package", context.packageName, null)
                            }
                        }
                        runCatching { context.startActivity(settingsIntent) }
                    }
                }

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

                        val availableUpdate = state.updateState as? UpdateCheckState.UpdateAvailable
                        AnimatedVisibility(
                            visible = availableUpdate != null && !state.updateBannerDismissed,
                            enter = fadeIn(tween(180)) + expandVertically(),
                            exit = fadeOut(tween(150)) + shrinkVertically()
                        ) {
                            if (availableUpdate != null) {
                                UpdateAvailableBanner(
                                    versionName = availableUpdate.latestVersionName,
                                    buildCode = availableUpdate.latestBuildCode,
                                    sizeLabel = availableUpdate.apkSizeLabel,
                                    onDownloadClick = { openExternalUrl(availableUpdate.apkDownloadUrl) },
                                    onDismiss = vm::dismissUpdateBanner
                                )
                            }
                        }

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
                                            onResetDay = vm::resetCurrentDay,
                                            onFocusTask = vm::startFocusForTask
                                        )

                                        AppTab.FOCUS -> FocusScreen(
                                            profile = activeProfile,
                                            timer = state.timer,
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
                                            onOpenGame = { vm.setGameOverlayVisible(true) },
                                            onSelectSoundMode = vm::setFocusSoundMode
                                        )

                                        AppTab.DASHBOARD -> DashboardScreen(
                                            profile = activeProfile,
                                            selectedDate = state.selectedDate,
                                            onShareWeeklyReport = { shareOnWhatsApp(vm.getWeeklyReportText()) }
                                        )

                                        AppTab.AI_COACH -> AiCoachScreen(
                                            profile = activeProfile,
                                            aiState = state.ai,
                                            onSendChatMessage = vm::sendChatMessage,
                                            onClearChatHistory = vm::clearChatHistory,
                                            onSetLanguage = vm::setAiLanguage,
                                            onStopAi = vm::stopAiGeneration,
                                            onDismissError = vm::clearAiError,
                                            onCopyOutput = { txt ->
                                                copyToClipboard("AI Response", txt, "AI response copied")
                                            },
                                            onPickPhoto = {
                                                pickDoubtPhotoLauncher.launch(
                                                    PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                                )
                                            },
                                            onClearPhoto = { vm.setAttachedPhoto(null) }
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

                    // Profile & Settings full-screen modal sheet
                    if (state.showProfileSheet) {
                        ProfileSheet(
                            profile = activeProfile,
                            themeMode = state.store.theme,
                            updateState = state.updateState,
                            hasSavedGitHubToken = state.hasSavedGitHubToken,
                            savedRepoSlug = state.savedRepoSlug,
                            notificationsEnabled = UpdateNotificationHelper.areNotificationsAllowed(context),
                            onCheckForUpdates = { vm.checkForAppUpdate(silent = false) },
                            onOpenUpdateUrl = ::openExternalUrl,
                            onSaveGitHubToken = vm::saveGitHubUpdateToken,
                            onSaveRepoSlug = vm::saveGitHubRepoSlug,
                            onEnableNotifications = ::requestOrOpenNotificationSettings,
                            onSaveDetails = vm::updateProfileDetails,
                            onSetThemeMode = vm::setThemeMode,
                            onSaveBackupFile = { saveBackupLauncher.launch("drst-backup-${todayKey()}.json") },
                            onRestoreBackupFile = { restoreBackupLauncher.launch(arrayOf("application/json", "text/plain", "*/*")) },
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

private fun decodeScaledBitmap(context: Context, uri: Uri, maxDim: Int): Bitmap? {
    return runCatching {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        context.contentResolver.openInputStream(uri)?.use { stream ->
            BitmapFactory.decodeStream(stream, null, bounds)
        }
        val origW = bounds.outWidth
        val origH = bounds.outHeight
        if (origW <= 0 || origH <= 0) return@runCatching null

        var sampleSize = 1
        while ((origW / sampleSize) > maxDim * 2 || (origH / sampleSize) > maxDim * 2) {
            sampleSize *= 2
        }

        val decodeOpts = BitmapFactory.Options().apply { inSampleSize = sampleSize }
        val decoded = context.contentResolver.openInputStream(uri)?.use { stream ->
            BitmapFactory.decodeStream(stream, null, decodeOpts)
        } ?: return@runCatching null

        val maxSide = maxOf(decoded.width, decoded.height)
        if (maxSide <= maxDim) {
            decoded
        } else {
            val scale = maxDim.toFloat() / maxSide.toFloat()
            val targetW = (decoded.width * scale).toInt().coerceAtLeast(1)
            val targetH = (decoded.height * scale).toInt().coerceAtLeast(1)
            Bitmap.createScaledBitmap(decoded, targetW, targetH, true).also {
                if (it !== decoded) decoded.recycle()
            }
        }
    }.getOrNull()
}
