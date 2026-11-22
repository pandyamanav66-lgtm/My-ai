package com.example.ui.screens

import android.media.AudioManager
import android.media.ToneGenerator
import androidx.activity.compose.BackHandler
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.components.LiquidGlassBackdrop
import com.example.ui.components.backdropBlurIfSupported
import com.example.ui.theme.InterFontFamily
import com.example.ui.theme.LocalDrstColors
import com.example.ui.theme.SpaceGroteskFontFamily
import kotlinx.coroutines.delay
import kotlin.math.max
import kotlin.random.Random

private enum class MindGameMode(
    val title: String,
    val badge: String,
    val studyBenefit: String,
    val instruction: String,
    val icon: ImageVector,
    val tag: String
) {
    MEMORY(
        title = "Memory Matrix",
        badge = "RECALL",
        studyBenefit = "Boosts formula & diagram retention",
        instruction = "Memorize the glowing tiles, then tap them back from memory.",
        icon = Icons.Rounded.GridView,
        tag = "mind_game_mode_memory"
    ),
    STROOP(
        title = "Stroop Focus",
        badge = "ATTENTION",
        studyBenefit = "Blocks distractions & sharpens focus",
        instruction = "Ignore the word — tap the INK COLOR as fast as you can!",
        icon = Icons.Rounded.CenterFocusStrong,
        tag = "mind_game_mode_stroop"
    ),
    MATH(
        title = "Speed Math",
        badge = "WARM-UP",
        studyBenefit = "Activates calculation speed before study",
        instruction = "Solve rapid mental math problems to build combo streaks.",
        icon = Icons.Rounded.Calculate,
        tag = "mind_game_mode_math"
    )
}

private enum class MindGamePhase {
    MENU, PLAYING, PAUSED, GAME_OVER
}

private enum class MemoryStep {
    SHOWING, INPUT, FEEDBACK
}

private data class StroopColorOption(
    val name: String,
    val color: Color
)

private data class StroopPrompt(
    val word: String,
    val inkOption: StroopColorOption,
    val choices: List<StroopColorOption>
)

private data class MathPrompt(
    val expression: String,
    val correctAnswer: Int,
    val choices: List<Int>
)

private val STROOP_PALETTE = listOf(
    StroopColorOption("CYAN", Color(0xFF38BDF8)),
    StroopColorOption("VIOLET", Color(0xFFA78BFA)),
    StroopColorOption("CORAL", Color(0xFFFF6B8A)),
    StroopColorOption("MINT", Color(0xFF2EE6A6)),
    StroopColorOption("GOLD", Color(0xFFFFC145))
)

private fun generateStroopPrompt(rnd: Random): StroopPrompt {
    val ink = STROOP_PALETTE[rnd.nextInt(STROOP_PALETTE.size)]
    // 85% incongruent word vs ink for genuine Stroop selective-attention training
    val wordOpt = if (rnd.nextFloat() < 0.85f) {
        STROOP_PALETTE.filter { it.name != ink.name }.random(rnd)
    } else {
        ink
    }
    val distractors = STROOP_PALETTE.filter { it.name != ink.name }.shuffled(rnd).take(3)
    val choices = (distractors + ink).shuffled(rnd)
    return StroopPrompt(word = wordOpt.name, inkOption = ink, choices = choices)
}

private fun generateMathPrompt(level: Int, rnd: Random): MathPrompt {
    val (expr, ans) = when {
        level <= 2 -> {
            val a = rnd.nextInt(8, 36 + level * 8)
            val b = rnd.nextInt(6, 28 + level * 6)
            if (rnd.nextBoolean()) {
                "$a + $b" to (a + b)
            } else {
                val big = max(a, b) + 4
                val small = minOf(a, b)
                "$big − $small" to (big - small)
            }
        }
        level <= 5 -> {
            when (rnd.nextInt(3)) {
                0 -> {
                    val a = rnd.nextInt(4, 15)
                    val b = rnd.nextInt(3, 12)
                    "$a × $b" to (a * b)
                }
                1 -> {
                    val divisor = rnd.nextInt(3, 13)
                    val quotient = rnd.nextInt(4, 15)
                    "${divisor * quotient} ÷ $divisor" to quotient
                }
                else -> {
                    val a = rnd.nextInt(25, 79)
                    val b = rnd.nextInt(14, 49)
                    if (rnd.nextBoolean()) "$a + $b" to (a + b) else "$a − $b" to (a - b)
                }
            }
        }
        else -> {
            when (rnd.nextInt(3)) {
                0 -> {
                    val n = rnd.nextInt(6, 17)
                    "$n²" to (n * n)
                }
                1 -> {
                    val a = rnd.nextInt(6, 15)
                    val b = rnd.nextInt(4, 11)
                    val c = rnd.nextInt(3, 19)
                    "$a × $b + $c" to (a * b + c)
                }
                else -> {
                    val divisor = rnd.nextInt(4, 15)
                    val quotient = rnd.nextInt(6, 19)
                    "${divisor * quotient} ÷ $divisor" to quotient
                }
            }
        }
    }

    val options = linkedSetOf(ans)
    val offsets = listOf(-10, -4, -3, -2, -1, 1, 2, 3, 4, 5, 10, -5, 6, -6)
    var guard = 0
    while (options.size < 4 && guard < 40) {
        guard++
        val delta = offsets[rnd.nextInt(offsets.size)]
        val candidate = ans + delta
        if (candidate >= 0 && candidate != ans) {
            options.add(candidate)
        }
    }
    while (options.size < 4) {
        options.add(ans + options.size + 1)
    }
    return MathPrompt(
        expression = expr,
        correctAnswer = ans,
        choices = options.toList().shuffled(rnd)
    )
}

/**
 * Mind Lab • Study Focus Trainer
 * 3-mode Liquid Glass cognitive trainer (Memory Matrix, Stroop Focus, Speed Math)
 * built to warm up working memory, selective attention, and calculation speed before or between study blocks.
 */
@Composable
fun MindFocusGameScreen(
    bestScore: Int,
    bestLevel: Int,
    onFinishRun: (score: Int, level: Int) -> Unit,
    onExit: () -> Unit
) {
    val c = LocalDrstColors.current
    val haptic = LocalHapticFeedback.current
    val rnd = remember { Random(System.currentTimeMillis()) }

    var selectedMode by remember { mutableStateOf(MindGameMode.MEMORY) }
    var phase by remember { mutableStateOf(MindGamePhase.MENU) }
    var muted by remember { mutableStateOf(false) }

    var score by remember { mutableIntStateOf(0) }
    var level by remember { mutableIntStateOf(1) }
    var hearts by remember { mutableIntStateOf(3) }
    var combo by remember { mutableIntStateOf(1) }
    var maxCombo by remember { mutableIntStateOf(1) }
    var totalAttempts by remember { mutableIntStateOf(0) }
    var correctAttempts by remember { mutableIntStateOf(0) }
    var hitsThisLevel by remember { mutableIntStateOf(0) }
    var feedbackBanner by remember { mutableStateOf<String?>(null) }
    var feedbackPositive by remember { mutableStateOf(true) }
    var runReported by remember { mutableStateOf(false) }
    var roundToken by remember { mutableIntStateOf(0) }

    // Memory Matrix state
    var gridSize by remember { mutableIntStateOf(3) }
    var targetTiles by remember { mutableStateOf<Set<Int>>(emptySet()) }
    var tappedCorrect by remember { mutableStateOf<Set<Int>>(emptySet()) }
    var tappedWrong by remember { mutableStateOf<Int?>(null) }
    var memoryStep by remember { mutableStateOf(MemoryStep.SHOWING) }

    // Stroop & Math state + countdown progress (1f -> 0f)
    var stroopPrompt by remember { mutableStateOf(generateStroopPrompt(rnd)) }
    var mathPrompt by remember { mutableStateOf(generateMathPrompt(1, rnd)) }
    var timerProgress by remember { mutableFloatStateOf(1f) }
    var inputLocked by remember { mutableStateOf(false) }

    val toneGen = remember {
        runCatching { ToneGenerator(AudioManager.STREAM_MUSIC, 42) }.getOrNull()
    }
    DisposableEffect(Unit) {
        onDispose { runCatching { toneGen?.release() } }
    }

    fun playTone(tone: Int, durationMs: Int = 70) {
        if (!muted) {
            runCatching { toneGen?.startTone(tone, durationMs) }
        }
    }

    fun finishRunOnce() {
        if (!runReported && score > 0) {
            runReported = true
            onFinishRun(score, level)
        }
    }

    fun exitAndPreserveScore() {
        finishRunOnce()
        onExit()
    }

    fun prepareMemoryRound(forLevel: Int) {
        val g = when {
            forLevel >= 8 -> 5
            forLevel >= 4 -> 4
            else -> 3
        }
        val tileCount = when (g) {
            3 -> (2 + forLevel).coerceIn(3, 5)
            4 -> (4 + (forLevel - 3)).coerceIn(5, 8)
            else -> (7 + (forLevel - 7)).coerceIn(7, 11)
        }
        gridSize = g
        targetTiles = (0 until (g * g)).shuffled(rnd).take(tileCount).toSet()
        tappedCorrect = emptySet()
        tappedWrong = null
        memoryStep = MemoryStep.SHOWING
        timerProgress = 1f
        inputLocked = false
        roundToken++
    }

    fun prepareTimedRound(mode: MindGameMode, forLevel: Int) {
        if (mode == MindGameMode.STROOP) {
            stroopPrompt = generateStroopPrompt(rnd)
        } else {
            mathPrompt = generateMathPrompt(forLevel, rnd)
        }
        timerProgress = 1f
        inputLocked = false
        roundToken++
    }

    fun startSession(mode: MindGameMode) {
        selectedMode = mode
        score = 0
        level = 1
        hearts = 3
        combo = 1
        maxCombo = 1
        totalAttempts = 0
        correctAttempts = 0
        hitsThisLevel = 0
        feedbackBanner = null
        feedbackPositive = true
        runReported = false
        phase = MindGamePhase.PLAYING
        when (mode) {
            MindGameMode.MEMORY -> prepareMemoryRound(1)
            MindGameMode.STROOP, MindGameMode.MATH -> prepareTimedRound(mode, 1)
        }
        playTone(ToneGenerator.TONE_PROP_BEEP, 70)
    }

    fun handleMistake(reason: String) {
        totalAttempts++
        combo = 1
        hearts = (hearts - 1).coerceAtLeast(0)
        feedbackPositive = false
        feedbackBanner = reason
        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
        playTone(ToneGenerator.TONE_PROP_NACK, 120)
        if (hearts <= 0) {
            phase = MindGamePhase.GAME_OVER
            finishRunOnce()
        }
    }

    fun handleCorrect(basePoints: Int, banner: String? = null) {
        totalAttempts++
        correctAttempts++
        val gained = basePoints * combo
        score += gained
        combo = (combo + 1).coerceAtMost(8)
        maxCombo = max(maxCombo, combo)
        hitsThisLevel++
        feedbackPositive = true
        feedbackBanner = banner ?: "+$gained"
        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
        playTone(ToneGenerator.TONE_PROP_ACK, 65)
    }

    // Memory Matrix SHOWING -> INPUT transition loop
    LaunchedEffect(phase, selectedMode, memoryStep, roundToken) {
        if (phase != MindGamePhase.PLAYING || selectedMode != MindGameMode.MEMORY) return@LaunchedEffect
        if (memoryStep == MemoryStep.SHOWING) {
            val showMs = (1750L - (level - 1) * 75L).coerceAtLeast(1050L)
            val steps = 30
            val slice = showMs / steps
            for (i in steps downTo 0) {
                if (phase != MindGamePhase.PLAYING) return@LaunchedEffect
                timerProgress = i.toFloat() / steps.toFloat()
                delay(slice)
            }
            if (phase == MindGamePhase.PLAYING && memoryStep == MemoryStep.SHOWING) {
                memoryStep = MemoryStep.INPUT
                timerProgress = 1f
            }
        } else if (memoryStep == MemoryStep.FEEDBACK) {
            delay(820L)
            if (phase == MindGamePhase.PLAYING && hearts > 0) {
                feedbackBanner = null
                prepareMemoryRound(level)
            }
        }
    }

    // Timed countdown loop for Stroop Focus & Speed Math
    LaunchedEffect(phase, selectedMode, roundToken) {
        if (phase != MindGamePhase.PLAYING || selectedMode == MindGameMode.MEMORY) return@LaunchedEffect
        val totalMs = if (selectedMode == MindGameMode.STROOP) {
            (4600L - (level - 1) * 300L).coerceAtLeast(2100L)
        } else {
            (7000L - (level - 1) * 420L).coerceAtLeast(3400L)
        }
        val tickMs = 40L
        var elapsed = ((1f - timerProgress) * totalMs).toLong().coerceAtLeast(0L)
        while (phase == MindGamePhase.PLAYING && elapsed < totalMs && !inputLocked) {
            delay(tickMs)
            elapsed += tickMs
            timerProgress = (1f - elapsed.toFloat() / totalMs.toFloat()).coerceIn(0f, 1f)
        }
        if (phase == MindGamePhase.PLAYING && !inputLocked && elapsed >= totalMs) {
            inputLocked = true
            handleMistake("Time's up!")
            if (hearts > 0 && phase == MindGamePhase.PLAYING) {
                delay(520L)
                feedbackBanner = null
                prepareTimedRound(selectedMode, level)
            }
        }
    }

    // Brief feedback reset for Stroop / Math after answer tap
    var pendingNextTimedRound by remember { mutableIntStateOf(0) }
    LaunchedEffect(pendingNextTimedRound) {
        if (pendingNextTimedRound == 0) return@LaunchedEffect
        delay(380L)
        if (phase == MindGamePhase.PLAYING && hearts > 0) {
            feedbackBanner = null
            prepareTimedRound(selectedMode, level)
        }
    }

    BackHandler {
        when (phase) {
            MindGamePhase.PLAYING -> phase = MindGamePhase.PAUSED
            MindGamePhase.PAUSED -> phase = MindGamePhase.PLAYING
            MindGamePhase.MENU, MindGamePhase.GAME_OVER -> exitAndPreserveScore()
        }
    }

    val overlayBlur by animateDpAsState(
        targetValue = if (phase == MindGamePhase.PAUSED || phase == MindGamePhase.GAME_OVER) 14.dp else 0.dp,
        animationSpec = tween(220),
        label = "mind_game_overlay_blur"
    )

    LiquidGlassBackdrop(
        translucentOverlay = true,
        modifier = Modifier.testTag("mind_game_root")
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            // Main interactive stage (blurs smoothly behind Pause / Game Over glass modals)
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .backdropBlurIfSupported(overlayBlur)
                    .statusBarsPadding()
                    .navigationBarsPadding()
                    .padding(horizontal = 16.dp, vertical = 10.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Top Floating Glass HUD
                Row(
                    modifier = Modifier
                        .widthIn(max = 600.dp)
                        .fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    GlassCircleButton(
                        icon = Icons.AutoMirrored.Rounded.ArrowBack,
                        contentDescription = "Exit Mind Lab",
                        onClick = { exitAndPreserveScore() },
                        modifier = Modifier.testTag("mind_game_close_button")
                    )

                    if (phase == MindGamePhase.PLAYING || phase == MindGamePhase.PAUSED) {
                        Row(
                            modifier = Modifier
                                .clip(RoundedCornerShape(99.dp))
                                .background(if (c.isDark) Color(0x88101836) else Color(0xCCFFFFFF))
                                .border(
                                    1.1.dp,
                                    Brush.linearGradient(listOf(c.glassBorderTop, c.prismCyan.copy(alpha = 0.55f))),
                                    RoundedCornerShape(99.dp)
                                )
                                .padding(horizontal = 14.dp, vertical = 7.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(
                                    text = "SCORE",
                                    style = TextStyle(fontFamily = InterFontFamily, fontSize = 9.sp, fontWeight = FontWeight.Bold, color = c.muted, letterSpacing = 0.8.sp)
                                )
                                Text(
                                    text = "$score",
                                    style = TextStyle(fontFamily = SpaceGroteskFontFamily, fontSize = 16.sp, fontWeight = FontWeight.Bold, color = c.ink, fontFeatureSettings = "tnum"),
                                    modifier = Modifier.testTag("mind_game_score")
                                )
                            }
                            Box(modifier = Modifier.width(1.dp).height(22.dp).background(c.line))
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(
                                    text = "LVL $level",
                                    style = TextStyle(fontFamily = SpaceGroteskFontFamily, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = c.prismCyan)
                                )
                                Text(
                                    text = "x$combo",
                                    style = TextStyle(fontFamily = SpaceGroteskFontFamily, fontSize = 11.sp, fontWeight = FontWeight.Bold, color = c.gold)
                                )
                            }
                            Box(modifier = Modifier.width(1.dp).height(22.dp).background(c.line))
                            Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                                repeat(3) { idx ->
                                    Icon(
                                        imageVector = Icons.Rounded.Favorite,
                                        contentDescription = null,
                                        tint = if (idx < hearts) c.prismCoral else c.track,
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            }
                        }
                    } else {
                        Row(
                            modifier = Modifier
                                .clip(RoundedCornerShape(99.dp))
                                .background(if (c.isDark) Color(0x88101836) else Color(0xCCFFFFFF))
                                .border(1.dp, c.glassBorderTop, RoundedCornerShape(99.dp))
                                .padding(horizontal = 14.dp, vertical = 7.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Icon(Icons.Rounded.Psychology, contentDescription = null, tint = c.prismCyan, modifier = Modifier.size(17.dp))
                            Text(
                                text = "Mind Lab",
                                style = TextStyle(fontFamily = SpaceGroteskFontFamily, fontWeight = FontWeight.Bold, fontSize = 14.sp, color = c.ink)
                            )
                        }
                    }

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        GlassCircleButton(
                            icon = if (muted) Icons.Rounded.VolumeOff else Icons.Rounded.VolumeUp,
                            contentDescription = if (muted) "Unmute audio" else "Mute audio",
                            onClick = { muted = !muted }
                        )
                        if (phase == MindGamePhase.PLAYING || phase == MindGamePhase.PAUSED) {
                            GlassCircleButton(
                                icon = if (phase == MindGamePhase.PAUSED) Icons.Rounded.PlayArrow else Icons.Rounded.Pause,
                                contentDescription = "Pause game",
                                onClick = {
                                    phase = if (phase == MindGamePhase.PAUSED) MindGamePhase.PLAYING else MindGamePhase.PAUSED
                                },
                                modifier = Modifier.testTag("mind_game_pause_button")
                            )
                        }
                    }
                }

                Spacer(Modifier.height(14.dp))

                if (phase == MindGamePhase.MENU) {
                    MindGameMenuContent(
                        selectedMode = selectedMode,
                        bestScore = max(bestScore, score),
                        bestLevel = max(bestLevel, 1),
                        onSelectMode = { selectedMode = it },
                        onStart = { startSession(selectedMode) }
                    )
                } else {
                    // Active Training Board
                    Column(
                        modifier = Modifier
                            .widthIn(max = 560.dp)
                            .fillMaxWidth()
                            .weight(1f),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        // Mode status + animated Focus Timer Bar
                        GlassPanel {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Icon(selectedMode.icon, contentDescription = null, tint = c.prismCyan, modifier = Modifier.size(18.dp))
                                    Text(
                                        text = selectedMode.title,
                                        style = TextStyle(fontFamily = SpaceGroteskFontFamily, fontWeight = FontWeight.Bold, fontSize = 15.sp, color = c.ink)
                                    )
                                }
                                Text(
                                    text = feedbackBanner ?: when (selectedMode) {
                                        MindGameMode.MEMORY -> when (memoryStep) {
                                            MemoryStep.SHOWING -> "MEMORIZE PATTERN"
                                            MemoryStep.INPUT -> "TAP ${targetTiles.size - tappedCorrect.size} LEFT"
                                            MemoryStep.FEEDBACK -> "NEXT PATTERN..."
                                        }
                                        MindGameMode.STROOP -> "TAP INK COLOR"
                                        MindGameMode.MATH -> "SOLVE QUICKLY"
                                    },
                                    style = TextStyle(
                                        fontFamily = SpaceGroteskFontFamily,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 12.sp,
                                        letterSpacing = 0.6.sp,
                                        color = when {
                                            feedbackBanner != null && feedbackPositive -> c.ok
                                            feedbackBanner != null && !feedbackPositive -> c.prismCoral
                                            else -> c.prismCyan
                                        }
                                    )
                                )
                            }

                            Spacer(Modifier.height(10.dp))

                            val barWidth by animateFloatAsState(
                                targetValue = timerProgress.coerceIn(0f, 1f),
                                animationSpec = tween(70),
                                label = "mind_timer_bar"
                            )
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(8.dp)
                                    .clip(RoundedCornerShape(99.dp))
                                    .background(c.track)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxHeight()
                                        .fillMaxWidth(barWidth)
                                        .clip(RoundedCornerShape(99.dp))
                                        .background(
                                            Brush.horizontalGradient(
                                                if (timerProgress > 0.35f) listOf(c.prismCyan, c.prismViolet, c.ok)
                                                else listOf(c.prismCoral, c.gold)
                                            )
                                        )
                                )
                            }
                        }

                        // Game-specific interactive board
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f),
                            contentAlignment = Alignment.Center
                        ) {
                            when (selectedMode) {
                                MindGameMode.MEMORY -> MemoryMatrixBoard(
                                    gridSize = gridSize,
                                    step = memoryStep,
                                    targetTiles = targetTiles,
                                    tappedCorrect = tappedCorrect,
                                    tappedWrong = tappedWrong,
                                    onTapTile = { index ->
                                        if (phase != MindGamePhase.PLAYING || memoryStep != MemoryStep.INPUT) return@MemoryMatrixBoard
                                        if (index in tappedCorrect) return@MemoryMatrixBoard

                                        if (index in targetTiles) {
                                            val updated = tappedCorrect + index
                                            tappedCorrect = updated
                                            handleCorrect(25, "+${25 * combo}")
                                            if (updated.size == targetTiles.size) {
                                                score += 40
                                                level++
                                                feedbackPositive = true
                                                feedbackBanner = "PERFECT RECALL! LVL $level"
                                                memoryStep = MemoryStep.FEEDBACK
                                            }
                                        } else {
                                            tappedWrong = index
                                            handleMistake("Missed tile!")
                                            if (hearts > 0) {
                                                memoryStep = MemoryStep.FEEDBACK
                                            }
                                        }
                                    }
                                )

                                MindGameMode.STROOP -> StroopFocusBoard(
                                    prompt = stroopPrompt,
                                    enabled = phase == MindGamePhase.PLAYING && !inputLocked,
                                    onPick = { picked ->
                                        if (inputLocked || phase != MindGamePhase.PLAYING) return@StroopFocusBoard
                                        inputLocked = true
                                        if (picked.name == stroopPrompt.inkOption.name) {
                                            handleCorrect(30)
                                            if (hitsThisLevel >= 5) {
                                                hitsThisLevel = 0
                                                level++
                                                feedbackBanner = "LEVEL $level UNLOCKED!"
                                            }
                                        } else {
                                            handleMistake("Ink was ${stroopPrompt.inkOption.name}")
                                        }
                                        if (hearts > 0) pendingNextTimedRound++
                                    }
                                )

                                MindGameMode.MATH -> SpeedMathBoard(
                                    prompt = mathPrompt,
                                    enabled = phase == MindGamePhase.PLAYING && !inputLocked,
                                    onPick = { picked ->
                                        if (inputLocked || phase != MindGamePhase.PLAYING) return@SpeedMathBoard
                                        inputLocked = true
                                        if (picked == mathPrompt.correctAnswer) {
                                            handleCorrect(30)
                                            if (hitsThisLevel >= 5) {
                                                hitsThisLevel = 0
                                                level++
                                                feedbackBanner = "LEVEL $level UNLOCKED!"
                                            }
                                        } else {
                                            handleMistake("Answer was ${mathPrompt.correctAnswer}")
                                        }
                                        if (hearts > 0) pendingNextTimedRound++
                                    }
                                )
                            }
                        }

                        // Subtle Study Tip Footer
                        Text(
                            text = selectedMode.studyBenefit,
                            style = TextStyle(fontFamily = InterFontFamily, fontSize = 12.sp, color = c.muted),
                            textAlign = TextAlign.Center,
                            modifier = Modifier.padding(bottom = 4.dp)
                        )
                    }
                }
            }

            // Frosted Glass Pause Modal
            if (phase == MindGamePhase.PAUSED) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color(0x66060A18))
                        .padding(24.dp),
                    contentAlignment = Alignment.Center
                ) {
                    GlassPanel(modifier = Modifier.widthIn(max = 380.dp)) {
                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Icon(Icons.Rounded.PauseCircle, contentDescription = null, tint = c.prismCyan, modifier = Modifier.size(44.dp))
                            Text(
                                text = "Focus Paused",
                                style = TextStyle(fontFamily = SpaceGroteskFontFamily, fontWeight = FontWeight.Bold, fontSize = 22.sp, color = c.ink)
                            )
                            Text(
                                text = "Score $score • Level $level • +${score / 10} Study XP ready",
                                style = TextStyle(fontFamily = InterFontFamily, fontSize = 13.sp, color = c.muted),
                                textAlign = TextAlign.Center
                            )
                            PrismPrimaryButton(
                                text = "Resume Training",
                                icon = Icons.Rounded.PlayArrow,
                                onClick = { phase = MindGamePhase.PLAYING }
                            )
                            GlassSecondaryButton(
                                text = "Switch Mind Mode",
                                onClick = {
                                    finishRunOnce()
                                    phase = MindGamePhase.MENU
                                }
                            )
                            GlassSecondaryButton(
                                text = "Save XP & Exit",
                                onClick = { exitAndPreserveScore() }
                            )
                        }
                    }
                }
            }

            // Frosted Glass Game Over Modal
            if (phase == MindGamePhase.GAME_OVER) {
                val accuracy = if (totalAttempts > 0) (correctAttempts * 100) / totalAttempts else 0
                val earnedXp = (score / 10).coerceAtLeast(0)
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color(0x66060A18))
                        .padding(24.dp),
                    contentAlignment = Alignment.Center
                ) {
                    GlassPanel(modifier = Modifier.widthIn(max = 400.dp)) {
                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(56.dp)
                                    .clip(CircleShape)
                                    .background(Brush.linearGradient(listOf(c.prismCyan.copy(alpha = 0.25f), c.prismViolet.copy(alpha = 0.30f))))
                                    .border(1.2.dp, c.prismCyan.copy(alpha = 0.7f), CircleShape),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(Icons.Rounded.EmojiEvents, contentDescription = null, tint = c.gold, modifier = Modifier.size(30.dp))
                            }
                            Text(
                                text = "Brain Warm-Up Done!",
                                style = TextStyle(fontFamily = SpaceGroteskFontFamily, fontWeight = FontWeight.Bold, fontSize = 22.sp, color = c.ink)
                            )
                            Text(
                                text = if (score > bestScore && score > 0) "New Personal Best! +$earnedXp Study XP"
                                else "Earned +$earnedXp Study XP for your profile",
                                style = TextStyle(fontFamily = InterFontFamily, fontSize = 13.sp, color = c.ok),
                                textAlign = TextAlign.Center
                            )

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                SummaryMiniStat("SCORE", "$score", c.prismCyan, Modifier.weight(1f))
                                SummaryMiniStat("LEVEL", "$level", c.prismViolet, Modifier.weight(1f))
                                SummaryMiniStat("ACCURACY", "$accuracy%", c.gold, Modifier.weight(1f))
                            }

                            Text(
                                text = "Max Combo x$maxCombo • All-Time Best ${max(bestScore, score)}",
                                style = TextStyle(fontFamily = InterFontFamily, fontSize = 12.sp, color = c.muted)
                            )

                            PrismPrimaryButton(
                                text = "Train Again",
                                icon = Icons.Rounded.Refresh,
                                onClick = { startSession(selectedMode) }
                            )
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                GlassSecondaryButton(
                                    text = "Modes",
                                    onClick = { phase = MindGamePhase.MENU },
                                    modifier = Modifier.weight(1f)
                                )
                                GlassSecondaryButton(
                                    text = "Back to Study",
                                    onClick = { exitAndPreserveScore() },
                                    modifier = Modifier.weight(1f)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun MindGameMenuContent(
    selectedMode: MindGameMode,
    bestScore: Int,
    bestLevel: Int,
    onSelectMode: (MindGameMode) -> Unit,
    onStart: () -> Unit
) {
    val c = LocalDrstColors.current
    Column(
        modifier = Modifier
            .widthIn(max = 560.dp)
            .fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(10.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Hero Header Card
            GlassPanel {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(54.dp)
                            .clip(RoundedCornerShape(18.dp))
                            .background(Brush.linearGradient(listOf(c.prismCyan.copy(alpha = 0.25f), c.prismViolet.copy(alpha = 0.30f))))
                            .border(1.2.dp, Color(0x88FFFFFF), RoundedCornerShape(18.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Rounded.Psychology, contentDescription = null, tint = c.prismCyan, modifier = Modifier.size(30.dp))
                    }
                    Text(
                        text = "Study Focus Trainer",
                        style = TextStyle(fontFamily = SpaceGroteskFontFamily, fontWeight = FontWeight.Bold, fontSize = 22.sp, color = c.ink)
                    )
                    Text(
                        text = "2-minute brain warm-ups that sharpen memory, focus & calculation speed.",
                        style = TextStyle(fontFamily = InterFontFamily, fontSize = 12.sp, color = c.muted),
                        textAlign = TextAlign.Center
                    )
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        StatBadgePill("Best $bestScore", c.gold)
                        StatBadgePill("Top Lvl $bestLevel", c.prismCyan)
                        StatBadgePill("Earns XP", c.ok)
                    }
                }
            }

            // 3 Mode Selector Glass Cards
            MindGameMode.entries.forEach { mode ->
                val selected = mode == selectedMode
                val shape = RoundedCornerShape(22.dp)
                val borderBrush = if (selected) {
                    Brush.linearGradient(listOf(c.prismCyan, c.prismViolet, c.gold))
                } else {
                    Brush.linearGradient(listOf(c.glassBorderTop, c.glassBorderBottom))
                }
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(shape)
                        .background(
                            if (selected) {
                                if (c.isDark) Color(0x8816234B) else Color(0xE6FFFFFF)
                            } else {
                                if (c.isDark) Color(0x520B1228) else Color(0xA8FFFFFF)
                            }
                        )
                        .border(if (selected) 1.6.dp else 1.dp, borderBrush, shape)
                        .clickable { onSelectMode(mode) }
                        .padding(13.dp)
                        .testTag(mode.tag),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(44.dp)
                            .clip(RoundedCornerShape(14.dp))
                            .background(
                                if (selected) Brush.linearGradient(listOf(c.prismCyan, c.prismViolet))
                                else Brush.linearGradient(listOf(c.prismCyan.copy(alpha = 0.16f), c.prismViolet.copy(alpha = 0.16f)))
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = mode.icon,
                            contentDescription = mode.title,
                            tint = if (selected) Color(0xFF061322) else c.prismCyan,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                    Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Text(
                                text = mode.title,
                                style = TextStyle(fontFamily = SpaceGroteskFontFamily, fontWeight = FontWeight.Bold, fontSize = 15.sp, color = c.ink)
                            )
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(99.dp))
                                    .background(c.prismCyan.copy(alpha = 0.18f))
                                    .padding(horizontal = 7.dp, vertical = 2.dp)
                            ) {
                                Text(
                                    text = mode.badge,
                                    style = TextStyle(fontFamily = InterFontFamily, fontWeight = FontWeight.Bold, fontSize = 9.sp, letterSpacing = 0.7.sp, color = if (c.isDark) c.prismCyan else c.band)
                                )
                            }
                        }
                        Text(
                            text = mode.studyBenefit,
                            style = TextStyle(fontFamily = InterFontFamily, fontWeight = FontWeight.Medium, fontSize = 12.sp, color = c.muted)
                        )
                    }
                    Icon(
                        imageVector = if (selected) Icons.Rounded.RadioButtonChecked else Icons.Rounded.RadioButtonUnchecked,
                        contentDescription = null,
                        tint = if (selected) c.prismCyan else c.muted,
                        modifier = Modifier.size(22.dp)
                    )
                }
            }
        }

        // Pinned How-to hint + Start Button (always visible without scrolling)
        Text(
            text = selectedMode.instruction,
            style = TextStyle(fontFamily = InterFontFamily, fontSize = 12.sp, color = c.muted),
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = 8.dp)
        )

        PrismPrimaryButton(
            text = "Start ${selectedMode.title}",
            icon = Icons.Rounded.PlayArrow,
            onClick = onStart,
            modifier = Modifier.testTag("mind_game_start_button")
        )
    }
}

@Composable
private fun MemoryMatrixBoard(
    gridSize: Int,
    step: MemoryStep,
    targetTiles: Set<Int>,
    tappedCorrect: Set<Int>,
    tappedWrong: Int?,
    onTapTile: (Int) -> Unit
) {
    val c = LocalDrstColors.current
    GlassPanel(modifier = Modifier.widthIn(max = 360.dp)) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(1f),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            for (row in 0 until gridSize) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    for (col in 0 until gridSize) {
                        val idx = row * gridSize + col
                        val isTarget = idx in targetTiles
                        val isFound = idx in tappedCorrect
                        val isWrong = idx == tappedWrong
                        val lit = when (step) {
                            MemoryStep.SHOWING -> isTarget
                            MemoryStep.INPUT -> isFound
                            MemoryStep.FEEDBACK -> isTarget || isFound
                        }

                        val tileScale by animateFloatAsState(
                            targetValue = if (lit || isWrong) 1.03f else 0.98f,
                            animationSpec = spring(dampingRatio = 0.55f),
                            label = "tile_scale_$idx"
                        )
                        val fill by animateColorAsState(
                            targetValue = when {
                                isWrong -> c.prismCoral.copy(alpha = 0.78f)
                                isFound -> c.ok.copy(alpha = 0.80f)
                                lit -> c.prismCyan.copy(alpha = 0.78f)
                                else -> if (c.isDark) Color(0x38FFFFFF) else Color(0xB8FFFFFF)
                            },
                            animationSpec = tween(180),
                            label = "tile_fill_$idx"
                        )
                        val rim = when {
                            isWrong -> c.prismCoral
                            isFound -> c.ok
                            lit -> Color(0xE6FFFFFF)
                            else -> c.glassBorderTop
                        }

                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight()
                                .scale(tileScale)
                                .clip(RoundedCornerShape(18.dp))
                                .background(fill)
                                .border(1.4.dp, rim, RoundedCornerShape(18.dp))
                                .clickable(enabled = step == MemoryStep.INPUT) { onTapTile(idx) }
                                .testTag("memory_tile_$idx"),
                            contentAlignment = Alignment.Center
                        ) {
                            if (isFound) {
                                Icon(Icons.Rounded.Check, contentDescription = null, tint = Color(0xFF061322), modifier = Modifier.size(22.dp))
                            } else if (isWrong) {
                                Icon(Icons.Rounded.Close, contentDescription = null, tint = Color.White, modifier = Modifier.size(22.dp))
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun StroopFocusBoard(
    prompt: StroopPrompt,
    enabled: Boolean,
    onPick: (StroopColorOption) -> Unit
) {
    val c = LocalDrstColors.current
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        GlassPanel {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = "WHICH INK COLOR IS THIS?",
                    style = TextStyle(fontFamily = InterFontFamily, fontWeight = FontWeight.Bold, fontSize = 11.sp, letterSpacing = 1.1.sp, color = c.muted)
                )
                Text(
                    text = prompt.word,
                    style = TextStyle(
                        fontFamily = SpaceGroteskFontFamily,
                        fontWeight = FontWeight.Bold,
                        fontSize = 48.sp,
                        letterSpacing = 2.sp,
                        color = prompt.inkOption.color
                    ),
                    modifier = Modifier.testTag("stroop_word")
                )
            }
        }

        // 2x2 Liquid Glass color choice buttons
        val rows = prompt.choices.chunked(2)
        Column(verticalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
            rows.forEach { pair ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    pair.forEach { opt ->
                        val shape = RoundedCornerShape(20.dp)
                        Row(
                            modifier = Modifier
                                .weight(1f)
                                .heightIn(min = 64.dp)
                                .clip(shape)
                                .background(if (c.isDark) Color(0x70101836) else Color(0xCCFFFFFF))
                                .border(1.3.dp, opt.color.copy(alpha = 0.72f), shape)
                                .clickable(enabled = enabled) { onPick(opt) }
                                .padding(horizontal = 14.dp, vertical = 12.dp)
                                .testTag("stroop_choice_${opt.name.lowercase()}"),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(14.dp)
                                    .clip(CircleShape)
                                    .background(opt.color)
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(
                                text = opt.name,
                                style = TextStyle(
                                    fontFamily = SpaceGroteskFontFamily,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 16.sp,
                                    color = c.ink
                                )
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SpeedMathBoard(
    prompt: MathPrompt,
    enabled: Boolean,
    onPick: (Int) -> Unit
) {
    val c = LocalDrstColors.current
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        GlassPanel {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = "MENTAL SPRINT",
                    style = TextStyle(fontFamily = InterFontFamily, fontWeight = FontWeight.Bold, fontSize = 11.sp, letterSpacing = 1.1.sp, color = c.muted)
                )
                Text(
                    text = "${prompt.expression} = ?",
                    style = TextStyle(
                        fontFamily = SpaceGroteskFontFamily,
                        fontWeight = FontWeight.Bold,
                        fontSize = 40.sp,
                        color = c.ink,
                        fontFeatureSettings = "tnum"
                    ),
                    modifier = Modifier.testTag("math_expression")
                )
            }
        }

        val rows = prompt.choices.chunked(2)
        Column(verticalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
            rows.forEachIndexed { rIdx, pair ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    pair.forEachIndexed { cIdx, value ->
                        val shape = RoundedCornerShape(20.dp)
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .heightIn(min = 68.dp)
                                .clip(shape)
                                .background(if (c.isDark) Color(0x70101836) else Color(0xCCFFFFFF))
                                .border(
                                    1.3.dp,
                                    Brush.linearGradient(listOf(c.glassBorderTop, c.prismCyan.copy(alpha = 0.55f))),
                                    shape
                                )
                                .clickable(enabled = enabled) { onPick(value) }
                                .testTag("math_choice_${rIdx * 2 + cIdx}"),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "$value",
                                style = TextStyle(
                                    fontFamily = SpaceGroteskFontFamily,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 24.sp,
                                    color = c.ink,
                                    fontFeatureSettings = "tnum"
                                )
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun GlassPanel(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit
) {
    val c = LocalDrstColors.current
    val shape = RoundedCornerShape(26.dp)
    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(shape)
            .background(if (c.isDark) Color(0x780C1430) else Color(0xC8FFFFFF))
            .background(Brush.verticalGradient(listOf(c.glassHighlight, c.glassFill)))
            .border(
                1.2.dp,
                Brush.linearGradient(listOf(c.glassBorderTop, c.prismCyan.copy(alpha = 0.35f), c.glassBorderBottom)),
                shape
            )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(18.dp),
            content = content
        )
    }
}

@Composable
private fun GlassCircleButton(
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val c = LocalDrstColors.current
    Box(
        modifier = modifier
            .size(44.dp)
            .clip(CircleShape)
            .background(if (c.isDark) Color(0x80101836) else Color(0xCCFFFFFF))
            .border(1.1.dp, c.glassBorderTop, CircleShape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Icon(icon, contentDescription = contentDescription, tint = c.ink, modifier = Modifier.size(20.dp))
    }
}

@Composable
private fun PrismPrimaryButton(
    text: String,
    icon: ImageVector,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val c = LocalDrstColors.current
    Row(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 52.dp)
            .clip(RoundedCornerShape(18.dp))
            .background(Brush.horizontalGradient(listOf(c.prismCyan, c.ok)))
            .clickable(onClick = onClick)
            .padding(horizontal = 18.dp, vertical = 14.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, contentDescription = null, tint = Color(0xFF061322), modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(8.dp))
        Text(
            text = text,
            style = TextStyle(fontFamily = SpaceGroteskFontFamily, fontWeight = FontWeight.Bold, fontSize = 15.sp, color = Color(0xFF061322))
        )
    }
}

@Composable
private fun GlassSecondaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val c = LocalDrstColors.current
    Box(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(if (c.isDark) Color(0x45FFFFFF) else Color(0xA6FFFFFF))
            .border(1.dp, c.glassBorderTop, RoundedCornerShape(16.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            style = TextStyle(fontFamily = SpaceGroteskFontFamily, fontWeight = FontWeight.Bold, fontSize = 14.sp, color = c.ink)
        )
    }
}

@Composable
private fun StatBadgePill(text: String, accent: Color) {
    val c = LocalDrstColors.current
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(99.dp))
            .background(accent.copy(alpha = 0.16f))
            .border(1.dp, accent.copy(alpha = 0.45f), RoundedCornerShape(99.dp))
            .padding(horizontal = 10.dp, vertical = 4.dp)
    ) {
        Text(
            text = text,
            style = TextStyle(fontFamily = SpaceGroteskFontFamily, fontWeight = FontWeight.Bold, fontSize = 11.sp, color = if (c.isDark) accent else c.ink)
        )
    }
}

@Composable
private fun SummaryMiniStat(label: String, value: String, accent: Color, modifier: Modifier = Modifier) {
    val c = LocalDrstColors.current
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .background(if (c.isDark) Color(0x38FFFFFF) else Color(0x99FFFFFF))
            .border(1.dp, accent.copy(alpha = 0.45f), RoundedCornerShape(16.dp))
            .padding(vertical = 10.dp, horizontal = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = value,
            style = TextStyle(fontFamily = SpaceGroteskFontFamily, fontWeight = FontWeight.Bold, fontSize = 20.sp, color = c.ink, fontFeatureSettings = "tnum")
        )
        Text(
            text = label,
            style = TextStyle(fontFamily = InterFontFamily, fontWeight = FontWeight.Bold, fontSize = 9.sp, letterSpacing = 0.8.sp, color = c.muted)
        )
    }
}
