package com.example.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
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
import androidx.compose.material.icons.rounded.Dashboard
import androidx.compose.material.icons.rounded.Timer
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.scale
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
import kotlin.math.cos
import kotlin.random.Random

/**
 * High-performance static ambient gradient + luminous soft glow backdrop
 * that gives translucent Liquid Glass surfaces depth at 60fps with zero GPU blur lag.
 */
@Composable
fun LiquidGlassBackdrop(
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit
) {
    val c = LocalDrstColors.current
    Box(
        modifier = modifier
            .fillMaxSize()
            .drawBehind {
                drawRect(
                    brush = Brush.verticalGradient(
                        colors = listOf(c.bgGradTop, c.bgGradMid, c.bgGradBottom)
                    )
                )
                val w = size.width
                val h = size.height
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(c.orbPrimary, Color.Transparent),
                        center = Offset(w * 0.14f, h * 0.12f),
                        radius = w * 0.68f
                    ),
                    radius = w * 0.68f,
                    center = Offset(w * 0.14f, h * 0.12f)
                )
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(c.orbGold, Color.Transparent),
                        center = Offset(w * 0.88f, h * 0.34f),
                        radius = w * 0.64f
                    ),
                    radius = w * 0.64f,
                    center = Offset(w * 0.88f, h * 0.34f)
                )
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(c.orbAccent, Color.Transparent),
                        center = Offset(w * 0.25f, h * 0.82f),
                        radius = w * 0.62f
                    ),
                    radius = w * 0.62f,
                    center = Offset(w * 0.25f, h * 0.82f)
                )
            }
    ) {
        content()
    }
}

@Composable
fun BrandLogoMark(
    modifier: Modifier = Modifier,
    size: Dp = 38.dp,
    arcProgress: Float = 1f,
    checkProgress: Float = 1f,
    arcColor: Color = LocalDrstColors.current.gold,
    checkColor: Color = LocalDrstColors.current.onBand
) {
    Canvas(modifier = modifier.size(size)) {
        val strokeW = this.size.minDimension * 0.11f
        val pad = strokeW * 0.8f
        val arcSize = Size(this.size.width - pad * 2, this.size.height - pad * 2)

        drawArc(
            color = arcColor.copy(alpha = 0.24f),
            startAngle = -90f,
            sweepAngle = 360f,
            useCenter = false,
            topLeft = Offset(pad, pad),
            size = arcSize,
            style = Stroke(width = strokeW, cap = StrokeCap.Round)
        )

        drawArc(
            color = arcColor,
            startAngle = -90f,
            sweepAngle = 315f * arcProgress.coerceIn(0f, 1f),
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
 * Liquid Glass Card: frosted translucent fill + specular diagonal glass rim + subtle top sheen.
 * Zero elevation shadows on scrolling items keeps LazyColumn locked at 60fps.
 */
@Composable
fun DrstCard(
    modifier: Modifier = Modifier,
    title: String? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    val c = LocalDrstColors.current
    val shape = RoundedCornerShape(22.dp)
    val rimBrush = remember(c.glassBorderTop, c.glassBorderBottom) {
        Brush.linearGradient(
            colors = listOf(c.glassBorderTop, c.glassBorderBottom)
        )
    }
    val sheenBrush = remember(c.glassHighlight) {
        Brush.verticalGradient(
            colors = listOf(c.glassHighlight, Color.Transparent)
        )
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(shape)
            .background(c.glassFill)
            .background(sheenBrush)
            .border(width = 1.dp, brush = rimBrush, shape = shape)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            if (title != null) {
                Text(
                    text = title.uppercase(),
                    style = TextStyle(
                        fontFamily = InterFontFamily,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 11.sp,
                        letterSpacing = 1.sp,
                        color = c.muted
                    ),
                    modifier = Modifier.padding(bottom = 12.dp)
                )
            }
            content()
        }
    }
}

@Composable
fun CompletionRing(
    percent: Int,
    modifier: Modifier = Modifier,
    size: Dp = 132.dp,
    strokeWidth: Dp = 11.dp,
    label: String = "DONE",
    ringColor: Color = LocalDrstColors.current.ok
) {
    val c = LocalDrstColors.current
    val animatedSweep by animateFloatAsState(
        targetValue = (percent.coerceIn(0, 100) / 100f) * 360f,
        animationSpec = tween(durationMillis = 700, easing = FastOutSlowInEasing),
        label = "ring_sweep"
    )
    Box(
        modifier = modifier.size(size),
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val sw = strokeWidth.toPx()
            val inset = sw / 2f
            val arcSize = Size(this.size.width - sw, this.size.height - sw)
            drawArc(
                color = c.track,
                startAngle = -90f,
                sweepAngle = 360f,
                useCenter = false,
                topLeft = Offset(inset, inset),
                size = arcSize,
                style = Stroke(width = sw, cap = StrokeCap.Round)
            )
            drawArc(
                color = ringColor,
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
                    fontSize = if (size >= 160.dp) 38.sp else 28.sp,
                    color = c.ink,
                    fontFeatureSettings = "tnum"
                )
            )
            Text(
                text = label,
                style = TextStyle(
                    fontFamily = InterFontFamily,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 10.sp,
                    letterSpacing = 0.9.sp,
                    color = c.muted
                )
            )
        }
    }
}

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

    LaunchedEffect(checked) {
        if (checked) {
            popScale.animateTo(1.18f, animationSpec = tween(100))
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
                .size(34.dp)
                .scale(popScale.value)
                .clip(RoundedCornerShape(11.dp))
                .background(if (checked) c.ok else c.glassFillStrong)
                .border(
                    width = 2.dp,
                    color = if (checked) c.ok else borderColor,
                    shape = RoundedCornerShape(11.dp)
                ),
            contentAlignment = Alignment.Center
        ) {
            if (checkDraw > 0.01f) {
                Canvas(modifier = Modifier.size(20.dp)) {
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
                        color = Color.White,
                        style = Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round)
                    )
                }
            }
        }
    }
}

@Composable
fun FloatingTabBar(
    selectedTab: AppTab,
    onSelectTab: (AppTab) -> Unit,
    modifier: Modifier = Modifier
) {
    val c = LocalDrstColors.current
    val pillShape = RoundedCornerShape(30.dp)
    val pillBg = if (c.isDark) Color(0xE00A1B33) else Color(0xE80B1F3A)
    val rimBrush = remember(c.gold) {
        Brush.linearGradient(
            colors = listOf(Color(0x75FFFFFF), c.gold.copy(alpha = 0.55f), Color(0x38FFFFFF))
        )
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(horizontal = 14.dp, vertical = 10.dp),
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .widthIn(max = 520.dp)
                .fillMaxWidth()
                .clip(pillShape)
                .background(pillBg)
                .border(width = 1.1.dp, brush = rimBrush, shape = pillShape)
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
                        targetValue = if (active) 1.14f else 1f,
                        animationSpec = spring(
                            dampingRatio = Spring.DampingRatioMediumBouncy,
                            stiffness = Spring.StiffnessMedium
                        ),
                        label = "tab_scale_${tab.name}"
                    )
                    val iconLift by animateFloatAsState(
                        targetValue = if (active) -2f else 0f,
                        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy),
                        label = "tab_lift_${tab.name}"
                    )

                    val iconVec = when (tab) {
                        AppTab.CHECKLIST -> if (active) Icons.Rounded.CheckCircle else Icons.Outlined.CheckCircle
                        AppTab.DASHBOARD -> if (active) Icons.Rounded.Dashboard else Icons.Outlined.Dashboard
                        AppTab.WEEKLY -> if (active) Icons.Rounded.CalendarMonth else Icons.Outlined.CalendarMonth
                        AppTab.FOCUS -> if (active) Icons.Rounded.Timer else Icons.Outlined.Timer
                        AppTab.AI_COACH -> if (active) Icons.Rounded.AutoAwesome else Icons.Outlined.AutoAwesome
                    }

                    val activeModifier = if (active) {
                        Modifier
                            .background(
                                Brush.verticalGradient(
                                    listOf(c.gold.copy(alpha = 0.28f), c.gold.copy(alpha = 0.12f))
                                )
                            )
                            .border(
                                1.dp,
                                c.gold.copy(alpha = 0.55f),
                                RoundedCornerShape(23.dp)
                            )
                    } else {
                        Modifier
                    }

                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .heightIn(min = 50.dp)
                            .clip(RoundedCornerShape(23.dp))
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
                            tint = if (active) c.gold else c.onBandMuted,
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
                                fontWeight = if (active) FontWeight.SemiBold else FontWeight.Medium,
                                fontSize = 10.sp,
                                color = if (active) c.onBand else c.onBandMuted
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
                Color(0xFFC59B52),
                Color(0xFFDFBC7E),
                Color(0xFF2FA36B),
                Color(0xFFE0A800),
                Color(0xFF3B82F6),
                Color(0xFFF4EDE0)
            )
            val rnd = Random(burstId * 9973)
            List(52) {
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
            color = Color(0xEB0A1B33),
            border = BorderStroke(1.dp, c.gold.copy(alpha = 0.8f)),
            shadowElevation = 6.dp
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
                        .background(c.gold)
                )
                Text(
                    text = message.orEmpty(),
                    style = TextStyle(
                        fontFamily = InterFontFamily,
                        fontWeight = FontWeight.Medium,
                        fontSize = 13.sp,
                        color = c.onBand
                    )
                )
            }
        }
    }
}
