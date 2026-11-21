package com.example.ui.screens

import android.media.AudioManager
import android.media.ToneGenerator
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.InterFontFamily
import com.example.ui.theme.SpaceGroteskFontFamily
import kotlin.math.*
import kotlin.random.Random

private enum class GamePhase {
    TITLE,
    PLAYING,
    PAUSED,
    GAME_OVER
}

private class SparkPickup(
    var angleRad: Float,
    var isShield: Boolean,
    var pulse: Float = 0f
)

private class HazardSweep(
    var centerAngleRad: Float,
    var arcWidthRad: Float,
    var timer: Float,
    val telegraphDuration: Float,
    val activeDuration: Float,
    var hitApplied: Boolean = false
)

private class BurstParticle(
    var x: Float = 0f,
    var y: Float = 0f,
    var vx: Float = 0f,
    var vy: Float = 0f,
    var life: Float = 0f,
    var maxLife: Float = 0.45f,
    var radius: Float = 5f,
    var color: Color = Color(0xFFDFBC7E)
)

/**
 * Full-viewport 60fps interactive 2D Study Break Arcade game: "Orbit Pulse".
 * Zero-lag single Canvas loop driven by withFrameNanos with clamped dt <= 0.05s,
 * pooled particles, hitstop, camera shake, procedural ToneGenerator audio,
 * and automatic high-score + Study XP reporting to Firestore.
 */
@Composable
fun OrbitPulseGameScreen(
    bestScore: Int,
    bestWave: Int,
    onFinishRun: (score: Int, wave: Int) -> Unit,
    onExit: () -> Unit
) {
    var phase by remember { mutableStateOf(GamePhase.TITLE) }
    var score by remember { mutableIntStateOf(0) }
    var wave by remember { mutableIntStateOf(1) }
    var combo by remember { mutableIntStateOf(1) }
    var hasShield by remember { mutableStateOf(false) }
    var isMuted by remember { mutableStateOf(false) }
    var runReported by remember { mutableStateOf(false) }

    // High-frequency mutable game world state (mutated inside frame loop, drawn directly in Canvas)
    var frameTick by remember { mutableLongStateOf(0L) }
    val playerAngle = remember { mutableFloatStateOf(-PI.toFloat() / 2f) }
    val playerDir = remember { mutableFloatStateOf(1f) }
    val hitFlash = remember { mutableFloatStateOf(0f) }
    val shakeAmp = remember { mutableFloatStateOf(0f) }
    val hitstopTimer = remember { mutableFloatStateOf(0f) }
    val reversePulse = remember { mutableFloatStateOf(0f) }
    val hazardCooldown = remember { mutableFloatStateOf(1.6f) }

    val trailAngles = remember { FloatArray(10) { -PI.toFloat() / 2f } }
    val sparkles = remember { mutableStateListOf<SparkPickup>() }
    val hazards = remember { mutableStateListOf<HazardSweep>() }
    val particles = remember { List(48) { BurstParticle() } }
    var particleCursor by remember { mutableIntStateOf(0) }

    val toneGen = remember {
        runCatching { ToneGenerator(AudioManager.STREAM_MUSIC, 65) }.getOrNull()
    }
    DisposableEffect(Unit) {
        onDispose { runCatching { toneGen?.release() } }
    }

    fun playTone(tone: Int, durationMs: Int) {
        if (!isMuted) {
            runCatching { toneGen?.startTone(tone, durationMs) }
        }
    }

    fun spawnBurst(angleRad: Float, color: Color, count: Int = 14) {
        for (i in 0 until count) {
            val p = particles[particleCursor]
            particleCursor = (particleCursor + 1) % particles.size
            val theta = Random.nextFloat() * (2f * PI.toFloat())
            val speed = 0.25f + Random.nextFloat() * 0.75f
            p.x = cos(angleRad)
            p.y = sin(angleRad)
            p.vx = cos(theta) * speed
            p.vy = sin(theta) * speed
            p.maxLife = 0.35f + Random.nextFloat() * 0.25f
            p.life = p.maxLife
            p.radius = 4f + Random.nextFloat() * 5f
            p.color = color
        }
    }

    fun spawnNextPickup() {
        val offset = (0.85f + Random.nextFloat() * 1.45f) * if (Random.nextBoolean()) 1f else -1f
        val nextAngle = normalizeAngle(playerAngle.floatValue + offset)
        val shieldChance = !hasShield && score >= 50 && Random.nextFloat() < 0.16f
        sparkles.clear()
        sparkles.add(SparkPickup(angleRad = nextAngle, isShield = shieldChance))
    }

    fun startNewRun() {
        score = 0
        wave = 1
        combo = 1
        hasShield = false
        runReported = false
        playerAngle.floatValue = -PI.toFloat() / 2f
        playerDir.floatValue = 1f
        for (i in trailAngles.indices) trailAngles[i] = playerAngle.floatValue
        hitFlash.floatValue = 0f
        shakeAmp.floatValue = 0f
        hitstopTimer.floatValue = 0f
        reversePulse.floatValue = 0f
        hazardCooldown.floatValue = 1.5f
        hazards.clear()
        particles.forEach { it.life = 0f }
        spawnNextPickup()
        phase = GamePhase.PLAYING
        playTone(ToneGenerator.TONE_PROP_ACK, 80)
    }

    fun reversePlayerDirection() {
        if (phase != GamePhase.PLAYING) return
        playerDir.floatValue = -playerDir.floatValue
        reversePulse.floatValue = 1f
        playTone(ToneGenerator.TONE_PROP_BEEP, 35)
    }

    BackHandler {
        when (phase) {
            GamePhase.PLAYING -> phase = GamePhase.PAUSED
            GamePhase.PAUSED -> phase = GamePhase.PLAYING
            else -> onExit()
        }
    }

    // 60fps Game Loop using withFrameNanos and clamped dt
    LaunchedEffect(phase) {
        if (phase != GamePhase.PLAYING) return@LaunchedEffect
        var lastNanos = 0L
        while (phase == GamePhase.PLAYING) {
            withFrameNanos { now ->
                if (lastNanos == 0L) {
                    lastNanos = now
                    return@withFrameNanos
                }
                val rawDt = (now - lastNanos) / 1_000_000_000f
                lastNanos = now
                val dt = rawDt.coerceIn(0.001f, 0.05f)

                // Decay juice timers
                hitFlash.floatValue = (hitFlash.floatValue - dt * 3.5f).coerceAtLeast(0f)
                shakeAmp.floatValue = (shakeAmp.floatValue - dt * 24f).coerceAtLeast(0f)
                reversePulse.floatValue = (reversePulse.floatValue - dt * 4.5f).coerceAtLeast(0f)

                // Update particles even during hitstop
                for (p in particles) {
                    if (p.life > 0f) {
                        p.x += p.vx * dt
                        p.y += p.vy * dt
                        p.life -= dt
                    }
                }

                if (hitstopTimer.floatValue > 0f) {
                    hitstopTimer.floatValue = (hitstopTimer.floatValue - dt).coerceAtLeast(0f)
                    frameTick++
                    return@withFrameNanos
                }

                // Move player orb smoothly around ring
                val angularSpeed = (2.15f + (wave - 1) * 0.22f).coerceAtMost(4.4f)
                playerAngle.floatValue = normalizeAngle(
                    playerAngle.floatValue + playerDir.floatValue * angularSpeed * dt
                )
                for (i in trailAngles.lastIndex downTo 1) {
                    trailAngles[i] = trailAngles[i - 1]
                }
                trailAngles[0] = playerAngle.floatValue

                // Check Spark / Shield pickup collision
                val pickup = sparkles.firstOrNull()
                if (pickup != null) {
                    pickup.pulse += dt * 4.2f
                    val dist = shortestAngleDiff(playerAngle.floatValue, pickup.angleRad)
                    if (abs(dist) < 0.24f) {
                        if (pickup.isShield) {
                            hasShield = true
                            score += 20
                            spawnBurst(pickup.angleRad, Color(0xFF45CE8D), 18)
                            playTone(ToneGenerator.TONE_PROP_PROMPT, 110)
                        } else {
                            score += 10 * combo
                            combo = (combo + 1).coerceAtMost(8)
                            spawnBurst(pickup.angleRad, Color(0xFFDFBC7E), 16)
                            playTone(ToneGenerator.TONE_PROP_BEEP2, 70)
                        }
                        wave = 1 + (score / 100)
                        spawnNextPickup()
                    }
                }

                // Spawn telegraphed distraction sweeps
                hazardCooldown.floatValue -= dt
                if (hazardCooldown.floatValue <= 0f) {
                    val telegraph = (1.18f - (wave - 1) * 0.06f).coerceAtLeast(0.62f)
                    val activeDur = 0.44f
                    val arcW = (0.76f + (wave - 1) * 0.05f).coerceAtMost(1.32f)
                    // Aim near player's forward path so reversing direction dodges cleanly
                    val targetAngle = normalizeAngle(
                        playerAngle.floatValue + playerDir.floatValue * (0.95f + Random.nextFloat() * 0.65f)
                    )
                    hazards.add(
                        HazardSweep(
                            centerAngleRad = targetAngle,
                            arcWidthRad = arcW,
                            timer = 0f,
                            telegraphDuration = telegraph,
                            activeDuration = activeDur
                        )
                    )
                    hazardCooldown.floatValue = (2.15f - (wave - 1) * 0.14f).coerceAtLeast(0.88f)
                }

                // Advance hazards & check collision during active strike phase
                val iter = hazards.iterator()
                while (iter.hasNext()) {
                    val h = iter.next()
                    h.timer += dt
                    val totalDur = h.telegraphDuration + h.activeDuration
                    if (h.timer >= h.telegraphDuration && h.timer < totalDur && !h.hitApplied) {
                        val diff = abs(shortestAngleDiff(playerAngle.floatValue, h.centerAngleRad))
                        if (diff <= h.arcWidthRad * 0.5f + 0.08f) {
                            h.hitApplied = true
                            hitFlash.floatValue = 1f
                            shakeAmp.floatValue = 14f
                            hitstopTimer.floatValue = 0.05f
                            if (hasShield) {
                                hasShield = false
                                combo = 1
                                spawnBurst(playerAngle.floatValue, Color(0xFF45CE8D), 22)
                                playTone(ToneGenerator.TONE_SUP_PIP, 130)
                            } else {
                                spawnBurst(playerAngle.floatValue, Color(0xFFFF5A5F), 28)
                                playTone(ToneGenerator.TONE_CDMA_ABBR_ALERT, 220)
                                phase = GamePhase.GAME_OVER
                                if (!runReported) {
                                    runReported = true
                                    onFinishRun(score, wave)
                                }
                            }
                        }
                    }
                    if (h.timer >= totalDur) {
                        iter.remove()
                    }
                }

                frameTick++
            }
        }
    }

    val bgBrush = remember {
        Brush.radialGradient(
            colors = listOf(Color(0xFF10284B), Color(0xFF071324), Color(0xFF030811))
        )
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(bgBrush)
            .pointerInput(phase) {
                detectTapGestures {
                    if (phase == GamePhase.PLAYING) {
                        reversePlayerDirection()
                    }
                }
            }
            .testTag("orbit_pulse_game_root")
    ) {
        // Full-viewport 60fps Arena Canvas
        Canvas(modifier = Modifier.fillMaxSize()) {
            // Read tick so Compose redraws every frame
            val tick = frameTick
            val shakeX = if (shakeAmp.floatValue > 0.1f) {
                (Random(tick).nextFloat() - 0.5f) * 2f * shakeAmp.floatValue
            } else 0f
            val shakeY = if (shakeAmp.floatValue > 0.1f) {
                (Random(tick + 7).nextFloat() - 0.5f) * 2f * shakeAmp.floatValue
            } else 0f

            val center = Offset(size.width / 2f + shakeX, size.height / 2f + shakeY)
            val orbitRadius = min(size.width, size.height) * 0.34f

            // Ambient center focus aura
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(Color(0x38C59B52), Color.Transparent),
                    center = center,
                    radius = orbitRadius * 1.45f
                ),
                radius = orbitRadius * 1.45f,
                center = center
            )

            // Outer decorative glass ring + Main orbital track
            drawCircle(
                color = Color(0x18FFFFFF),
                radius = orbitRadius * 1.18f,
                center = center,
                style = Stroke(width = 1.5.dp.toPx())
            )
            drawCircle(
                color = Color(0x45DFBC7E),
                radius = orbitRadius,
                center = center,
                style = Stroke(width = 5.dp.toPx())
            )

            // Reverse tap shockwave ring
            if (reversePulse.floatValue > 0.01f) {
                val p = reversePulse.floatValue
                drawCircle(
                    color = Color(0xFFDFBC7E).copy(alpha = p * 0.45f),
                    radius = orbitRadius * (1f + (1f - p) * 0.14f),
                    center = center,
                    style = Stroke(width = 3.dp.toPx())
                )
            }

            // Hazards (telegraph warning arc -> active crimson strike arc)
            val arcTopLeft = Offset(center.x - orbitRadius, center.y - orbitRadius)
            val arcSize = Size(orbitRadius * 2f, orbitRadius * 2f)
            hazards.forEach { h ->
                val startDeg = Math.toDegrees((h.centerAngleRad - h.arcWidthRad / 2f).toDouble()).toFloat()
                val sweepDeg = Math.toDegrees(h.arcWidthRad.toDouble()).toFloat()
                if (h.timer < h.telegraphDuration) {
                    val charge = (h.timer / h.telegraphDuration).coerceIn(0f, 1f)
                    drawArc(
                        color = Color(0xFFFF8C42).copy(alpha = 0.22f + charge * 0.48f),
                        startAngle = startDeg,
                        sweepAngle = sweepDeg,
                        useCenter = false,
                        topLeft = arcTopLeft,
                        size = arcSize,
                        style = Stroke(width = (8f + charge * 10f).dp.toPx(), cap = StrokeCap.Round)
                    )
                } else {
                    drawArc(
                        color = Color(0xFFFF4D5E),
                        startAngle = startDeg,
                        sweepAngle = sweepDeg,
                        useCenter = false,
                        topLeft = arcTopLeft,
                        size = arcSize,
                        style = Stroke(width = 20.dp.toPx(), cap = StrokeCap.Round)
                    )
                    drawArc(
                        color = Color(0xFFFFCDD2),
                        startAngle = startDeg,
                        sweepAngle = sweepDeg,
                        useCenter = false,
                        topLeft = arcTopLeft,
                        size = arcSize,
                        style = Stroke(width = 7.dp.toPx(), cap = StrokeCap.Round)
                    )
                }
            }

            // Spark / Shield pickups
            sparkles.forEach { sp ->
                val px = center.x + cos(sp.angleRad) * orbitRadius
                val py = center.y + sin(sp.angleRad) * orbitRadius
                val pulseScale = 1f + sin(sp.pulse) * 0.18f
                val mainColor = if (sp.isShield) Color(0xFF45CE8D) else Color(0xFFFFD166)
                drawCircle(
                    color = mainColor.copy(alpha = 0.32f),
                    radius = 22.dp.toPx() * pulseScale,
                    center = Offset(px, py)
                )
                drawCircle(
                    color = mainColor,
                    radius = 11.dp.toPx() * pulseScale,
                    center = Offset(px, py)
                )
                drawCircle(
                    color = Color.White,
                    radius = 4.5.dp.toPx(),
                    center = Offset(px, py)
                )
            }

            // Player motion trail
            for (i in trailAngles.lastIndex downTo 1) {
                val frac = 1f - (i.toFloat() / trailAngles.size)
                val tx = center.x + cos(trailAngles[i]) * orbitRadius
                val ty = center.y + sin(trailAngles[i]) * orbitRadius
                drawCircle(
                    color = Color(0xFF60A5FA).copy(alpha = frac * 0.35f),
                    radius = (11.dp.toPx()) * frac,
                    center = Offset(tx, ty)
                )
            }

            // Player Orb
            val playerX = center.x + cos(playerAngle.floatValue) * orbitRadius
            val playerY = center.y + sin(playerAngle.floatValue) * orbitRadius
            if (hasShield) {
                drawCircle(
                    color = Color(0xFF45CE8D).copy(alpha = 0.7f),
                    radius = 20.dp.toPx(),
                    center = Offset(playerX, playerY),
                    style = Stroke(width = 3.dp.toPx())
                )
            }
            drawCircle(
                color = Color(0xFF60A5FA).copy(alpha = 0.35f),
                radius = 22.dp.toPx(),
                center = Offset(playerX, playerY)
            )
            drawCircle(
                color = Color(0xFFF8FAFC),
                radius = 12.dp.toPx(),
                center = Offset(playerX, playerY)
            )

            // Particle bursts
            for (p in particles) {
                if (p.life > 0f) {
                    val alpha = (p.life / p.maxLife).coerceIn(0f, 1f)
                    val px = center.x + p.x * orbitRadius
                    val py = center.y + p.y * orbitRadius
                    drawCircle(
                        color = p.color.copy(alpha = alpha),
                        radius = p.radius.dp.toPx() * alpha,
                        center = Offset(px, py)
                    )
                }
            }

            // Center Score Readout Halo during play
            if (hitFlash.floatValue > 0.01f) {
                drawRect(color = Color(0xFFFF4D5E).copy(alpha = hitFlash.floatValue * 0.28f))
            }
        }

        // Top Minimal Glass HUD (only score/wave/mute/pause — zero chrome)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(99.dp))
                    .background(Color(0x550B1F3A))
                    .border(1.dp, Color(0x45FFFFFF), RoundedCornerShape(99.dp))
                    .padding(horizontal = 14.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Column {
                    Text(
                        text = "SCORE",
                        style = TextStyle(fontFamily = InterFontFamily, fontSize = 9.sp, letterSpacing = 1.sp, color = Color(0xFFA2B5CE))
                    )
                    Text(
                        text = "$score",
                        style = TextStyle(fontFamily = SpaceGroteskFontFamily, fontWeight = FontWeight.Bold, fontSize = 20.sp, color = Color(0xFFF4F1E8), fontFeatureSettings = "tnum")
                    )
                }
                Box(modifier = Modifier.width(1.dp).height(26.dp).background(Color(0x33FFFFFF)))
                Column {
                    Text(
                        text = "WAVE $wave",
                        style = TextStyle(fontFamily = InterFontFamily, fontSize = 9.sp, letterSpacing = 1.sp, color = Color(0xFFDFBC7E))
                    )
                    Text(
                        text = "${combo}x",
                        style = TextStyle(fontFamily = SpaceGroteskFontFamily, fontWeight = FontWeight.Bold, fontSize = 20.sp, color = Color(0xFFDFBC7E), fontFeatureSettings = "tnum")
                    )
                }
                if (hasShield) {
                    Icon(
                        imageVector = Icons.Rounded.Shield,
                        contentDescription = "Shield active",
                        tint = Color(0xFF45CE8D),
                        modifier = Modifier.size(20.dp)
                    )
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .clip(CircleShape)
                        .background(Color(0x550B1F3A))
                        .border(1.dp, Color(0x45FFFFFF), CircleShape)
                        .clickable { isMuted = !isMuted }
                        .testTag("game_mute_button"),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = if (isMuted) Icons.Rounded.VolumeOff else Icons.Rounded.VolumeUp,
                        contentDescription = if (isMuted) "Unmute sound" else "Mute sound",
                        tint = Color(0xFFF4F1E8),
                        modifier = Modifier.size(20.dp)
                    )
                }
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .clip(CircleShape)
                        .background(Color(0x550B1F3A))
                        .border(1.dp, Color(0x45FFFFFF), CircleShape)
                        .clickable {
                            when (phase) {
                                GamePhase.PLAYING -> phase = GamePhase.PAUSED
                                GamePhase.PAUSED -> phase = GamePhase.PLAYING
                                else -> onExit()
                            }
                        }
                        .testTag("game_pause_exit_button"),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = if (phase == GamePhase.PLAYING) Icons.Rounded.Pause else Icons.Rounded.Close,
                        contentDescription = if (phase == GamePhase.PLAYING) "Pause game" else "Exit game",
                        tint = Color(0xFFF4F1E8),
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }

        // Bottom subtle tap hint during play
        if (phase == GamePhase.PLAYING) {
            Text(
                text = "TAP ANYWHERE TO REVERSE ORBIT",
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .navigationBarsPadding()
                    .padding(bottom = 26.dp),
                style = TextStyle(
                    fontFamily = SpaceGroteskFontFamily,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 11.sp,
                    letterSpacing = 1.4.sp,
                    color = Color(0x88F4F1E8)
                )
            )
        }

        // Centered Modals for Title, Paused, Game Over
        if (phase != GamePhase.PLAYING) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color(0x88030812))
                    .padding(24.dp),
                contentAlignment = Alignment.Center
            ) {
                val modalShape = RoundedCornerShape(28.dp)
                Column(
                    modifier = Modifier
                        .widthIn(max = 380.dp)
                        .fillMaxWidth()
                        .clip(modalShape)
                        .background(Color(0xE00C203D))
                        .border(
                            1.2.dp,
                            Brush.linearGradient(listOf(Color(0x70FFFFFF), Color(0x60DFBC7E))),
                            modalShape
                        )
                        .padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    when (phase) {
                        GamePhase.TITLE -> {
                            Icon(
                                imageVector = Icons.Rounded.SportsEsports,
                                contentDescription = null,
                                tint = Color(0xFFDFBC7E),
                                modifier = Modifier.size(44.dp)
                            )
                            Spacer(Modifier.height(10.dp))
                            Text(
                                text = "ORBIT PULSE",
                                style = TextStyle(
                                    fontFamily = SpaceGroteskFontFamily,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 28.sp,
                                    letterSpacing = 1.5.sp,
                                    color = Color(0xFFF4F1E8)
                                )
                            )
                            Spacer(Modifier.height(4.dp))
                            Text(
                                text = "Study Break Reflex Arcade",
                                style = TextStyle(fontFamily = InterFontFamily, fontSize = 13.sp, color = Color(0xFFDFBC7E))
                            )
                            Spacer(Modifier.height(14.dp))
                            Text(
                                text = "Tap anywhere to flip orbit direction. Collect Gold Knowledge Sparks, grab Emerald Shields, and dodge Crimson Distraction sweeps.",
                                textAlign = TextAlign.Center,
                                style = TextStyle(fontFamily = InterFontFamily, fontSize = 13.sp, lineHeight = 19.sp, color = Color(0xFFB5C4DA))
                            )
                            Spacer(Modifier.height(14.dp))
                            Text(
                                text = "Best Score: $bestScore  •  Best Wave: $bestWave",
                                style = TextStyle(fontFamily = SpaceGroteskFontFamily, fontWeight = FontWeight.SemiBold, fontSize = 13.sp, color = Color(0xFF45CE8D))
                            )
                            Spacer(Modifier.height(20.dp))
                            Button(
                                onClick = { startNewRun() },
                                modifier = Modifier.fillMaxWidth().height(50.dp).testTag("game_play_button"),
                                shape = RoundedCornerShape(16.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFDFBC7E), contentColor = Color(0xFF071324))
                            ) {
                                Icon(Icons.Rounded.PlayArrow, contentDescription = null)
                                Spacer(Modifier.width(6.dp))
                                Text("PLAY NOW", fontFamily = SpaceGroteskFontFamily, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                            }
                            Spacer(Modifier.height(8.dp))
                            TextButton(onClick = onExit) {
                                Text("Back to Study Tracker", color = Color(0xFFA2B5CE))
                            }
                        }

                        GamePhase.PAUSED -> {
                            Text(
                                text = "PAUSED",
                                style = TextStyle(fontFamily = SpaceGroteskFontFamily, fontWeight = FontWeight.Bold, fontSize = 26.sp, color = Color(0xFFF4F1E8))
                            )
                            Spacer(Modifier.height(12.dp))
                            Text(
                                text = "Score: $score  •  Wave $wave",
                                style = TextStyle(fontFamily = InterFontFamily, fontSize = 14.sp, color = Color(0xFFDFBC7E))
                            )
                            Spacer(Modifier.height(20.dp))
                            Button(
                                onClick = { phase = GamePhase.PLAYING },
                                modifier = Modifier.fillMaxWidth().height(48.dp),
                                shape = RoundedCornerShape(14.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFDFBC7E), contentColor = Color(0xFF071324))
                            ) {
                                Text("RESUME", fontFamily = SpaceGroteskFontFamily, fontWeight = FontWeight.Bold)
                            }
                            Spacer(Modifier.height(8.dp))
                            OutlinedButton(
                                onClick = { startNewRun() },
                                modifier = Modifier.fillMaxWidth().height(46.dp),
                                shape = RoundedCornerShape(14.dp)
                            ) {
                                Text("RESTART", color = Color(0xFFF4F1E8), fontWeight = FontWeight.SemiBold)
                            }
                            Spacer(Modifier.height(6.dp))
                            TextButton(onClick = onExit) {
                                Text("Exit to Study Tracker", color = Color(0xFFA2B5CE))
                            }
                        }

                        GamePhase.GAME_OVER -> {
                            val earnedXp = (score / 10).coerceAtLeast(0)
                            val displayBest = max(bestScore, score)
                            Text(
                                text = "RUN COMPLETE",
                                style = TextStyle(fontFamily = SpaceGroteskFontFamily, fontWeight = FontWeight.Bold, fontSize = 26.sp, color = Color(0xFFF4F1E8))
                            )
                            Spacer(Modifier.height(14.dp))
                            Text(
                                text = "$score",
                                style = TextStyle(fontFamily = SpaceGroteskFontFamily, fontWeight = FontWeight.Bold, fontSize = 48.sp, color = Color(0xFFDFBC7E))
                            )
                            Text(
                                text = "Wave $wave  •  Best $displayBest",
                                style = TextStyle(fontFamily = InterFontFamily, fontSize = 13.sp, color = Color(0xFFA2B5CE))
                            )
                            if (earnedXp > 0) {
                                Spacer(Modifier.height(8.dp))
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(99.dp))
                                        .background(Color(0x3345CE8D))
                                        .padding(horizontal = 12.dp, vertical = 4.dp)
                                ) {
                                    Text(
                                        text = "+$earnedXp Study XP Earned",
                                        style = TextStyle(fontFamily = InterFontFamily, fontWeight = FontWeight.SemiBold, fontSize = 12.sp, color = Color(0xFF95F0BE))
                                    )
                                }
                            }
                            Spacer(Modifier.height(20.dp))
                            Button(
                                onClick = { startNewRun() },
                                modifier = Modifier.fillMaxWidth().height(50.dp).testTag("game_retry_button"),
                                shape = RoundedCornerShape(16.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFDFBC7E), contentColor = Color(0xFF071324))
                            ) {
                                Icon(Icons.Rounded.Replay, contentDescription = null)
                                Spacer(Modifier.width(6.dp))
                                Text("PLAY AGAIN", fontFamily = SpaceGroteskFontFamily, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                            }
                            Spacer(Modifier.height(8.dp))
                            TextButton(onClick = onExit) {
                                Text("Back to Study Tracker", color = Color(0xFFA2B5CE))
                            }
                        }

                        GamePhase.PLAYING -> Unit
                    }
                }
            }
        }
    }
}

private fun normalizeAngle(rad: Float): Float {
    val twoPi = (2.0 * PI).toFloat()
    var a = rad % twoPi
    if (a < 0f) a += twoPi
    return a
}

private fun shortestAngleDiff(a: Float, b: Float): Float {
    val twoPi = (2.0 * PI).toFloat()
    val pi = PI.toFloat()
    var d = (b - a) % twoPi
    if (d > pi) d -= twoPi
    if (d < -pi) d += twoPi
    return d
}
