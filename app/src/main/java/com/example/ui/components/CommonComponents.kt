package com.example.ui.components

import android.os.Build
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Dashboard
import androidx.compose.material.icons.outlined.Timer
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Dashboard
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.SystemUpdate
import androidx.compose.material.icons.rounded.Timer
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.BlurredEdgeTreatment
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathMeasure
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.AppTab
import com.example.ui.theme.InterFontFamily
import com.example.ui.theme.LocalDrstColors
import com.example.ui.theme.SpaceGroteskFontFamily
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

/**
 * Helper applying hardware-accelerated RenderEffect blur on Android 12+ (API 31+)
 * while remaining a zero-crash no-op on API 24-30.
 */
fun Modifier.backdropBlurIfSupported(radius: Dp): Modifier =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && radius > 0.dp) {
        this.blur(radius = radius, edgeTreatment = BlurredEdgeTreatment.Unbounded)
    } else {
        this
    }

/**
 * 120Hz-optimized Prism iOS 26 Liquid Glass ambient backdrop.
 * Uses cached GPU radial gradient shaders (`drawWithCache`) with zero per-frame invalidation
 * and zero redundant full-screen RenderEffect passes so scrolling stays locked at 90/120fps.
 */
@Composable
fun LiquidGlassBackdrop(
    modifier: Modifier = Modifier,
    translucentOverlay: Boolean = false,
    content: @Composable BoxScope.() -> Unit
) {
    val c = LocalDrstColors.current

    Box(modifier = modifier.fillMaxSize()) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .drawWithCache {
                    val baseAlpha = if (translucentOverlay) {
                        if (c.isDark) 0.78f else 0.84f
                    } else 1f

                    val bgBrush = Brush.verticalGradient(
                        colors = listOf(
                            c.bgGradTop.copy(alpha = baseAlpha),
                            c.bgGradMid.copy(alpha = baseAlpha),
                            c.bgGradBottom.copy(alpha = baseAlpha)
                        )
                    )
                    val w = size.width
                    val h = size.height

                    val c1 = Offset(w * 0.14f, h * 0.12f)
                    val orb1 = Brush.radialGradient(
                        colors = listOf(c.orbPrimary, Color.Transparent),
                        center = c1,
                        radius = w * 0.86f
                    )
                    val c2 = Offset(w * 0.88f, h * 0.28f)
                    val orb2 = Brush.radialGradient(
                        colors = listOf(c.orbAccent, Color.Transparent),
                        center = c2,
                        radius = w * 0.82f
                    )
                    val c3 = Offset(w * 0.16f, h * 0.66f)
                    val orb3 = Brush.radialGradient(
                        colors = listOf(c.orbGold, Color.Transparent),
                        center = c3,
                        radius = w * 0.78f
                    )
                    val c4 = Offset(w * 0.82f, h * 0.88f)
                    val orb4 = Brush.radialGradient(
                        colors = listOf(c.ok.copy(alpha = if (c.isDark) 0.26f else 0.22f), Color.Transparent),
                        center = c4,
                        radius = w * 0.70f
                    )

                    onDrawBehind {
                        drawRect(brush = bgBrush)
                        drawCircle(brush = orb1, radius = w * 0.86f, center = c1)
                        drawCircle(brush = orb2, radius = w * 0.82f, center = c2)
                        drawCircle(brush = orb3, radius = w * 0.78f, center = c3)
                        drawCircle(brush = orb4, radius = w * 0.70f, center = c4)
                    }
                }
        )

        content()
    }
}

@Composable
fun BrandLogoMark(
    modifier: Modifier = Modifier,
    size: Dp = 38.dp,
    arcProgress: Float = 1f,
    checkProgress: Float = 1f,
    arcColor: Color = LocalDrstColors.current.prismCyan,
    checkColor: Color = LocalDrstColors.current.ink
) {
    val c = LocalDrstColors.current
    Canvas(modifier = modifier.size(size)) {
        val strokeW = this.size.minDimension * 0.12f
        val pad = strokeW * 0.8f
        val arcSize = Size(this.size.width - pad * 2, this.size.height - pad * 2)

        drawArc(
            color = arcColor.copy(alpha = 0.22f),
            startAngle = -90f,
            sweepAngle = 360f,
            useCenter = false,
            topLeft = Offset(pad, pad),
            size = arcSize,
            style = Stroke(width = strokeW, cap = StrokeCap.Round)
        )

        drawArc(
            brush = Brush.sweepGradient(
                listOf(arcColor, c.prismViolet, c.gold, arcColor)
            ),
            startAngle = -90f,
            sweepAngle = 320f * arcProgress.coerceIn(0f, 1f),
            useCenter = false,
            topLeft = Offset(pad, pad),
            size = arcSize,
            style = Stroke(width = strokeW, cap = StrokeCap.Round)
        )

        if (checkProgress > 0f) {
            val fullPath = Path().apply {
                moveTo(this@Canvas.size.width * 0.30f, this@Canvas.size.height * 0.52f)
                lineTo(this@Canvas.size.width * 0.45f, this@Canvas.size.height * 0.67f)
                lineTo(this@Canvas.size.width * 0.72f, this@Canvas.size.height * 0.36f)
            }
            val measure = PathMeasure()
            measure.setPath(fullPath, false)
            val partial = Path()
            measure.getSegment(0f, measure.length * checkProgress.coerceIn(0f, 1f), partial, true)
            drawPath(
                path = partial,
                color = checkColor,
                style = Stroke(width = strokeW, cap = StrokeCap.Round, join = StrokeJoin.Round)
            )
        }
    }
}

/**
 * Prism iOS 26 Liquid Glass Card:
 * - Minimal ultra-clear frosted glass surface with super-rounded 26dp corners
 * - Soft diagonal top-left crystal light reflection sheen
 * - Razor-crisp top specular reflection arc + prismatic directional glass border
 * Zero elevation shadows on scrolling items keeps LazyColumn locked at 60fps.
 */
@Composable
fun DrstCard(
    modifier: Modifier = Modifier,
    title: String? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    val c = LocalDrstColors.current
    val shape = RoundedCornerShape(26.dp)
    val rimBrush = remember(c.glassBorderTop, c.glassBorderBottom, c.prismViolet, c.isDark) {
        Brush.linearGradient(
            colors = listOf(
                c.glassBorderTop,
                if (c.isDark) c.prismCyan.copy(alpha = 0.24f) else Color(0xB3FFFFFF),
                c.glassBorderBottom
            )
        )
    }
    val glassFillBrush = remember(c.glassFill, c.glassHighlight, c.isDark) {
        Brush.verticalGradient(
            colors = listOf(
                c.glassHighlight,
                c.glassFill,
                if (c.isDark) Color(0x14FFFFFF) else c.glassFill.copy(alpha = 0.90f)
            )
        )
    }
    val topSpecularColor = if (c.isDark) Color(0x59FFFFFF) else Color(0xE6FFFFFF)

    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(shape)
            .background(if (c.isDark) Color(0x5A0B1228) else Color(0x88FFFFFF))
            .background(glassFillBrush)
            .drawWithCache {
                val inset = 1.dp.toPx()
                val sheenCenter = Offset(size.width * 0.14f, 0f)
                val sheenRadius = (size.width * 0.60f).coerceAtLeast(1f)
                val sheenBrush = Brush.radialGradient(
                    colors = listOf(
                        if (c.isDark) c.prismCyan.copy(alpha = 0.12f) else Color(0x66FFFFFF),
                        Color.Transparent
                    ),
                    center = sheenCenter,
                    radius = sheenRadius
                )
                val specularH = 28.dp.toPx().coerceAtMost(size.height)
                val specularBrush = Brush.verticalGradient(
                    colors = listOf(topSpecularColor, Color.Transparent),
                    startY = 0f,
                    endY = specularH
                )
                val corner = CornerRadius(25.dp.toPx(), 25.dp.toPx())
                val stroke = Stroke(width = 1.15.dp.toPx())
                val specSize = Size((size.width - inset * 2f).coerceAtLeast(0f), specularH)

                onDrawWithContent {
                    drawCircle(brush = sheenBrush, radius = sheenRadius, center = sheenCenter)
                    drawContent()
                    drawRoundRect(
                        brush = specularBrush,
                        topLeft = Offset(inset, inset),
                        size = specSize,
                        cornerRadius = corner,
                        style = stroke
                    )
                }
            }
            .border(width = 1.15.dp, brush = rimBrush, shape = shape)
    ) {
        Column(modifier = Modifier.padding(18.dp)) {
            if (title != null) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(7.dp),
                    modifier = Modifier.padding(bottom = 12.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(6.dp)
                            .clip(CircleShape)
                            .background(
                                Brush.linearGradient(listOf(c.prismCyan, c.prismViolet))
                            )
                    )
                    Text(
                        text = title.uppercase(),
                        style = TextStyle(
                            fontFamily = InterFontFamily,
                            fontWeight = FontWeight.Bold,
                            fontSize = 11.sp,
                            letterSpacing = 1.1.sp,
                            color = c.muted
                        )
                    )
                }
            }
            content()
        }
    }
}

/**
 * Luminous Prism Gradient Completion Ring with glowing progress arc.
 */
@Composable
fun CompletionRing(
    percent: Int,
    modifier: Modifier = Modifier,
    size: Dp = 128.dp,
    strokeWidth: Dp = 11.dp,
    label: String = "DONE",
    ringColor: Color = LocalDrstColors.current.ok
) {
    val c = LocalDrstColors.current
    val animatedSweep by animateFloatAsState(
        targetValue = (percent.coerceIn(0, 100) / 100f) * 360f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioNoBouncy,
            stiffness = Spring.StiffnessLow
        ),
        label = "ring_sweep"
    )
    Box(
        modifier = modifier.size(size),
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val sw = strokeWidth.toPx()
            val inset = sw / 2f + 2.dp.toPx()
            val arcSize = Size(this.size.width - inset * 2f, this.size.height - inset * 2f)

            // Frosted glass track ring
            drawArc(
                color = c.track,
                startAngle = -90f,
                sweepAngle = 360f,
                useCenter = false,
                topLeft = Offset(inset, inset),
                size = arcSize,
                style = Stroke(width = sw, cap = StrokeCap.Round)
            )

            // Soft luminous halo behind progress arc
            if (animatedSweep > 1f) {
                drawArc(
                    color = ringColor.copy(alpha = 0.24f),
                    startAngle = -90f,
                    sweepAngle = animatedSweep,
                    useCenter = false,
                    topLeft = Offset(inset, inset),
                    size = arcSize,
                    style = Stroke(width = sw + 5.dp.toPx(), cap = StrokeCap.Round)
                )
            }

            // Crisp prism gradient progress arc
            drawArc(
                brush = Brush.sweepGradient(
                    colors = listOf(c.prismCyan, ringColor, c.gold, c.prismCyan)
                ),
                startAngle = -90f,
                sweepAngle = animatedSweep,
                useCenter = false,
                topLeft = Offset(inset, inset),
                size = arcSize,
                style = Stroke(width = sw, cap = StrokeCap.Round)
            )
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = "$percent%",
                style = TextStyle(
                    fontFamily = SpaceGroteskFontFamily,
                    fontWeight = FontWeight.Bold,
                    fontSize = if (size >= 160.dp) 38.sp else 27.sp,
                    color = c.ink,
                    fontFeatureSettings = "tnum"
                )
            )
            Text(
                text = label,
                style = TextStyle(
                    fontFamily = InterFontFamily,
                    fontWeight = FontWeight.Bold,
                    fontSize = 9.sp,
                    letterSpacing = 1.sp,
                    color = c.muted
                )
            )
        }
    }
}

/**
 * Playful, bouncy squircle Liquid Glass checkbox with juicy spring pop on check.
 */
@Composable
fun AnimatedTaskCheckbox(
    checked: Boolean,
    borderColor: Color,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier
) {
    val c = LocalDrstColors.current
    val popScale = remember { Animatable(1f) }
    val checkDraw by animateFloatAsState(
        targetValue = if (checked) 1f else 0f,
        animationSpec = tween(durationMillis = 220, easing = FastOutSlowInEasing),
        label = "check_draw"
    )
    val fillColor by animateColorAsState(
        targetValue = if (checked) c.ok else if (c.isDark) Color(0x1CFFFFFF) else Color(0xCCFFFFFF),
        animationSpec = tween(220),
        label = "cb_fill"
    )
    val ringColor by animateColorAsState(
        targetValue = if (checked) c.ok else borderColor.copy(alpha = 0.72f),
        animationSpec = tween(220),
        label = "cb_ring"
    )

    LaunchedEffect(checked) {
        if (checked) {
            popScale.animateTo(1.24f, animationSpec = tween(95))
            popScale.animateTo(1f, animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy))
        }
    }

    Box(
        modifier = modifier
            .size(48.dp)
            .semantics { contentDescription = if (checked) "Mark task pending" else "Mark task done" }
            .clickable(role = Role.Checkbox, onClick = onToggle),
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .size(32.dp)
                .scale(popScale.value)
                .clip(RoundedCornerShape(11.dp))
                .background(fillColor)
                .border(
                    width = if (checked) 1.5.dp else 1.8.dp,
                    color = ringColor,
                    shape = RoundedCornerShape(11.dp)
                ),
            contentAlignment = Alignment.Center
        ) {
            if (checkDraw > 0.01f) {
                Canvas(modifier = Modifier.size(18.dp)) {
                    val full = Path().apply {
                        moveTo(size.width * 0.18f, size.height * 0.52f)
                        lineTo(size.width * 0.42f, size.height * 0.76f)
                        lineTo(size.width * 0.84f, size.height * 0.26f)
                    }
                    val pm = PathMeasure()
                    pm.setPath(full, false)
                    val seg = Path()
                    pm.getSegment(0f, pm.length * checkDraw, seg, true)
                    drawPath(
                        path = seg,
                        color = if (c.isDark) Color(0xFF041C14) else Color.White,
                        style = Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round)
                    )
                }
            }
        }
    }
}

/**
 * Minimal, Luminous iOS 26 Floating Liquid Glass Capsule Dock (strictly 5 navigation tabs).
 */
@Composable
fun FloatingTabBar(
    selectedTab: AppTab,
    onSelectTab: (AppTab) -> Unit,
    modifier: Modifier = Modifier
) {
    val c = LocalDrstColors.current
    val pillShape = RoundedCornerShape(34.dp)
    val dockFill = remember(c.isDark) {
        if (c.isDark) {
            Brush.verticalGradient(
                colors = listOf(Color(0xCC1B2246), Color(0xE80A0F24))
            )
        } else {
            Brush.verticalGradient(
                colors = listOf(Color(0xF2FFFFFF), Color(0xDDEEF4FF))
            )
        }
    }
    val rimBrush = remember(c.isDark, c.prismCyan, c.prismViolet) {
        if (c.isDark) {
            Brush.linearGradient(
                colors = listOf(Color(0x9EFFFFFF), c.prismCyan.copy(alpha = 0.50f), c.prismViolet.copy(alpha = 0.45f))
            )
        } else {
            Brush.linearGradient(
                colors = listOf(Color(0xFFFFFFFF), c.prismCyan.copy(alpha = 0.35f), Color(0xB3FFFFFF))
            )
        }
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(horizontal = 16.dp, vertical = 10.dp),
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .widthIn(max = 500.dp)
                .fillMaxWidth()
                .clip(pillShape)
                .background(dockFill)
                .border(width = 1.25.dp, brush = rimBrush, shape = pillShape)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 6.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                AppTab.entries.forEach { tab ->
                    val active = tab == selectedTab
                    val iconScale by animateFloatAsState(
                        targetValue = if (active) 1.16f else 1f,
                        animationSpec = spring(
                            dampingRatio = Spring.DampingRatioMediumBouncy,
                            stiffness = Spring.StiffnessMedium
                        ),
                        label = "tab_scale_${tab.name}"
                    )
                    val iconLift by animateFloatAsState(
                        targetValue = if (active) -2.5f else 0f,
                        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy),
                        label = "tab_lift_${tab.name}"
                    )

                    val iconVec = when (tab) {
                        AppTab.CHECKLIST -> if (active) Icons.Rounded.CheckCircle else Icons.Outlined.CheckCircle
                        AppTab.FOCUS -> if (active) Icons.Rounded.Timer else Icons.Outlined.Timer
                        AppTab.DASHBOARD -> if (active) Icons.Rounded.Dashboard else Icons.Outlined.Dashboard
                        AppTab.AI_COACH -> if (active) Icons.Rounded.AutoAwesome else Icons.Outlined.AutoAwesome
                    }

                    // Playful prism droplet behind the active tab
                    val activeModifier = if (active) {
                        val dropletBrush = if (c.isDark) {
                            Brush.linearGradient(
                                listOf(c.prismCyan.copy(alpha = 0.30f), c.prismViolet.copy(alpha = 0.28f))
                            )
                        } else {
                            Brush.linearGradient(
                                listOf(c.prismCyan.copy(alpha = 0.18f), c.prismViolet.copy(alpha = 0.16f))
                            )
                        }
                        val dropletRim = if (c.isDark) {
                            Brush.verticalGradient(listOf(Color(0xA6FFFFFF), c.prismCyan.copy(alpha = 0.65f)))
                        } else {
                            Brush.verticalGradient(listOf(Color(0xFFFFFFFF), c.prismCyan.copy(alpha = 0.55f)))
                        }
                        Modifier
                            .background(dropletBrush)
                            .border(1.1.dp, dropletRim, RoundedCornerShape(26.dp))
                    } else {
                        Modifier
                    }

                    val activeIconTint = if (c.isDark) c.prismCyan else c.band
                    val inactiveIconTint = if (c.isDark) c.muted else c.muted
                    val activeLabelColor = if (c.isDark) c.ink else c.band
                    val inactiveLabelColor = c.muted

                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .heightIn(min = 50.dp)
                            .clip(RoundedCornerShape(26.dp))
                            .then(activeModifier)
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null
                            ) { onSelectTab(tab) }
                            .padding(vertical = 6.dp, horizontal = 2.dp)
                            .testTag("tab_${tab.name.lowercase()}"),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            imageVector = iconVec,
                            contentDescription = tab.label,
                            tint = if (active) activeIconTint else inactiveIconTint,
                            modifier = Modifier
                                .size(22.dp)
                                .graphicsLayer {
                                    scaleX = iconScale
                                    scaleY = iconScale
                                    translationY = iconLift
                                }
                        )
                        Spacer(Modifier.height(2.dp))
                        Text(
                            text = tab.label,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            style = TextStyle(
                                fontFamily = InterFontFamily,
                                fontWeight = if (active) FontWeight.Bold else FontWeight.Medium,
                                fontSize = 10.sp,
                                color = if (active) activeLabelColor else inactiveLabelColor
                            )
                        )
                    }
                }
            }
        }
    }
}

private data class ConfettiParticle(
    val xFrac: Float,
    val vx: Float,
    val vy: Float,
    val sizeDp: Float,
    val color: Color,
    val angle: Float,
    val spin: Float
)

@Composable
fun ConfettiOverlay(
    burstId: Int,
    modifier: Modifier = Modifier
) {
    val progress = remember { Animatable(1f) }
    val particles = remember(burstId) {
        if (burstId == 0) emptyList()
        else {
            val palette = listOf(
                Color(0xFF38BDF8),
                Color(0xFFA78BFA),
                Color(0xFF2EE6A6),
                Color(0xFFFFB84D),
                Color(0xFFFF7A90),
                Color(0xFFF8FAFF)
            )
            val rnd = Random(burstId * 9973)
            List(56) {
                val theta = rnd.nextFloat() * Math.PI.toFloat() * 2f
                val speed = 0.25f + rnd.nextFloat() * 0.65f
                ConfettiParticle(
                    xFrac = 0.2f + rnd.nextFloat() * 0.6f,
                    vx = cos(theta) * speed,
                    vy = -0.35f - rnd.nextFloat() * 0.55f,
                    sizeDp = 5f + rnd.nextFloat() * 5f,
                    color = palette[it % palette.size],
                    angle = rnd.nextFloat() * 360f,
                    spin = (rnd.nextFloat() - 0.5f) * 520f
                )
            }
        }
    }

    LaunchedEffect(burstId) {
        if (burstId > 0) {
            progress.snapTo(0f)
            progress.animateTo(1f, animationSpec = tween(durationMillis = 1400, easing = FastOutSlowInEasing))
        }
    }

    if (burstId > 0 && progress.value < 1f) {
        val t = progress.value
        val alpha = (1f - t).coerceIn(0f, 1f)
        Canvas(modifier = modifier.fillMaxSize()) {
            val w = size.width
            val h = size.height
            particles.forEach { p ->
                val x = w * p.xFrac + p.vx * w * 0.45f * t
                val y = h * 0.28f + p.vy * h * 0.45f * t + 0.55f * h * t * t
                val r = p.sizeDp.dp.toPx()
                drawCircle(
                    color = p.color.copy(alpha = alpha),
                    radius = r * 0.55f,
                    center = Offset(x, y)
                )
            }
        }
    }
}

@Composable
fun ToastBanner(
    message: String?,
    modifier: Modifier = Modifier
) {
    val c = LocalDrstColors.current
    AnimatedVisibility(
        visible = message != null,
        enter = fadeIn(tween(180)) + slideInVertically { -it / 2 },
        exit = fadeOut(tween(180)) + slideOutVertically { -it / 2 },
        modifier = modifier
    ) {
        Surface(
            shape = RoundedCornerShape(99.dp),
            color = if (c.isDark) Color(0xEB101733) else Color(0xF2151E40),
            border = BorderStroke(
                1.1.dp,
                Brush.linearGradient(listOf(Color(0xB3FFFFFF), c.prismCyan.copy(alpha = 0.85f)))
            ),
            shadowElevation = 10.dp
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .clip(CircleShape)
                        .background(Brush.linearGradient(listOf(c.prismCyan, c.ok)))
                )
                Text(
                    text = message.orEmpty(),
                    style = TextStyle(
                        fontFamily = InterFontFamily,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 13.sp,
                        color = Color(0xFFF8FAFF)
                    )
                )
            }
        }
    }
}

/**
 * Sleek in-app Liquid Glass banner that appears automatically whenever a new GitHub Release is detected.
 */
@Composable
fun UpdateAvailableBanner(
    versionName: String,
    buildCode: Int,
    sizeLabel: String,
    onDownloadClick: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    val c = LocalDrstColors.current
    val shape = RoundedCornerShape(22.dp)
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp)
            .clip(shape)
            .background(
                Brush.horizontalGradient(
                    if (c.isDark) listOf(Color(0xE0112244), Color(0xE01A1A48))
                    else listOf(Color(0xF0EAF6FF), Color(0xF0F2ECFF))
                )
            )
            .border(
                1.2.dp,
                Brush.linearGradient(listOf(c.prismCyan, c.prismViolet, c.ok)),
                shape
            )
            .padding(horizontal = 12.dp, vertical = 10.dp)
            .testTag("in_app_update_banner"),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Box(
            modifier = Modifier
                .size(36.dp)
                .clip(CircleShape)
                .background(c.prismCyan.copy(alpha = 0.22f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Rounded.SystemUpdate,
                contentDescription = "New update available",
                tint = if (c.isDark) c.prismCyan else c.band,
                modifier = Modifier.size(20.dp)
            )
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = "New Update: $versionName",
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = TextStyle(
                    fontFamily = SpaceGroteskFontFamily,
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp,
                    color = c.ink
                )
            )
            val meta = if (sizeLabel.isNotBlank()) "Build #$buildCode • $sizeLabel • Keeps data" else "Build #$buildCode • Keeps your data"
            Text(
                text = meta,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = TextStyle(
                    fontFamily = InterFontFamily,
                    fontSize = 11.sp,
                    color = c.muted
                )
            )
        }
        Row(
            modifier = Modifier
                .clip(RoundedCornerShape(99.dp))
                .background(Brush.horizontalGradient(listOf(c.prismCyan, c.ok)))
                .clickable(onClick = onDownloadClick)
                .padding(horizontal = 12.dp, vertical = 8.dp)
                .testTag("banner_download_update_button"),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Icon(
                imageVector = Icons.Rounded.Download,
                contentDescription = null,
                tint = Color(0xFF061322),
                modifier = Modifier.size(15.dp)
            )
            Text(
                text = "Update",
                style = TextStyle(
                    fontFamily = SpaceGroteskFontFamily,
                    fontWeight = FontWeight.Bold,
                    fontSize = 12.sp,
                    color = Color(0xFF061322)
                )
            )
        }
        IconButton(
            onClick = onDismiss,
            modifier = Modifier.size(30.dp).testTag("banner_dismiss_update_button")
        ) {
            Icon(
                imageVector = Icons.Rounded.Close,
                contentDescription = "Dismiss update banner",
                tint = c.muted,
                modifier = Modifier.size(16.dp)
            )
        }
    }
}
