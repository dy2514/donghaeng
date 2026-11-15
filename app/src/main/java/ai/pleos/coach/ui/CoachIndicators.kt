package ai.pleos.coach.ui

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.*
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.currentStateAsState
import java.util.Locale

/** Illustrations reflect observed input, never a recommendation to operate a control. */
@Composable
internal fun DrivingTelemetry(s: CoachUiState, modifier: Modifier = Modifier) {
    val c = LocalCoachColors.current
    val lifecycle by LocalLifecycleOwner.current.lifecycle.currentStateAsState()
    val animate = s.recording && !s.replayPaused && lifecycle.isAtLeast(Lifecycle.State.STARTED)
    Surface(color = c.surface, shape = RoundedCornerShape(8.dp), modifier = modifier) {
        Row(
            Modifier.fillMaxWidth().height(IntrinsicSize.Min).padding(24.dp),
            horizontalArrangement = Arrangement.spacedBy(24.dp)
        ) {
            Column(Modifier.weight(1.05f)) {
                Text("관찰 속도", color = c.secondary)
                Box(Modifier.fillMaxWidth().height(172.dp), contentAlignment = Alignment.CenterStart) {
                    Text(
                        s.speedKmh?.let { String.format(Locale.KOREA, "%.0f", it) } ?: "—",
                        fontSize = 96.sp, lineHeight = 106.sp, fontWeight = FontWeight.Light
                    )
                }
                Text("km/h", fontSize = 28.sp, color = c.secondary)
            }
            VerticalDivider(color = c.line, modifier = Modifier.fillMaxHeight())
            Column(Modifier.weight(1f).clearAndSetSemantics {
                contentDescription = "브레이크 페달"
                stateDescription = brakeLabel(s.brake)
            }) {
                Text("브레이크 페달", color = c.secondary)
                BrakePedalImage(
                    s.brake, animate,
                    Modifier.fillMaxWidth().height(172.dp)
                )
                Text(
                    brakeLabel(s.brake), fontSize = 28.sp, fontWeight = FontWeight.Medium,
                    color = if (s.brake == true) c.accent else c.text
                )
            }
            VerticalDivider(color = c.line, modifier = Modifier.fillMaxHeight())
            Column(Modifier.weight(1.1f).clearAndSetSemantics {
                contentDescription = "방향지시등"
                stateDescription = signalStateLabel(s.signal)
            }) {
                Text("방향지시등", color = c.secondary)
                SignalIndicatorImage(
                    s.signal, animate,
                    Modifier.fillMaxWidth().height(172.dp)
                )
                Text(
                    signalStateLabel(s.signal), fontSize = 28.sp, fontWeight = FontWeight.Medium,
                    color = if (s.signal == "LEFT" || s.signal == "RIGHT") signalColor(c) else c.text
                )
            }
        }
    }
}

internal fun brakeLabel(brake: Boolean?): String = when (brake) {
    true -> "눌림"
    false -> "해제"
    null -> "확인 불가"
}

internal fun signalStateLabel(signal: String?): String = when (signal) {
    "LEFT" -> "좌측 켜짐"
    "RIGHT" -> "우측 켜짐"
    "OFF" -> "꺼짐"
    else -> "확인 불가"
}

internal fun signalColor(c: CoachColors): Color =
    if (c == NightColors) Color(0xFF72E6AF) else Color(0xFF087A50)

@Composable
internal fun BrakePedalImage(brake: Boolean?, animate: Boolean, modifier: Modifier = Modifier) {
    val c = LocalCoachColors.current
    // Only a binary pressed/released value is available. Do not imply pedal pressure.
    val travel = animateFloatAsState(
        targetValue = if (brake == true) 1f else 0f,
        animationSpec = if (animate && brake != null) tween(220, easing = FastOutSlowInEasing) else snap(),
        label = "brake travel"
    )
    Canvas(modifier) {
        val edge = minOf(size.width, size.height)
        translate((size.width - edge) / 2f, (size.height - edge) / 2f) {
            drawBrakePedal(Size(edge, edge), if (brake == null) 0f else travel.value, brake != null, c)
        }
    }
}

@Composable
internal fun SignalIndicatorImage(signal: String?, animate: Boolean, modifier: Modifier = Modifier) {
    val c = LocalCoachColors.current
    val active = signal == "LEFT" || signal == "RIGHT"
    // The SDK supplies the selected direction, not lamp phase. This 1 Hz pulse is a UI cue.
    // Only an active, foreground, unpaused recording creates a repeating animation.
    val intensity: State<Float> = if (animate && active) {
        key(signal) {
            rememberInfiniteTransition(label = "signal pulse").animateFloat(
                initialValue = 1f, targetValue = 1f,
                animationSpec = infiniteRepeatable(
                    animation = keyframes {
                        durationMillis = 1000
                        1f at 0
                        1f at 420
                        .18f at 500
                        .18f at 920
                        1f at 1000
                    },
                    repeatMode = RepeatMode.Restart
                ),
                label = "signal brightness"
            )
        }
    } else rememberUpdatedState(1f)
    Canvas(modifier) {
        val arrowWidth = minOf(size.width * .42f, 100.dp.toPx())
        val arrowSize = Size(arrowWidth, arrowWidth)
        val gap = minOf(size.width * .12f, 28.dp.toPx())
        val start = (size.width - 2 * arrowWidth - gap) / 2f
        val top = (size.height - arrowWidth) / 2f
        val known = signal in listOf("LEFT", "RIGHT", "OFF")
        translate(start, top) {
            drawSignalArrow(arrowSize, true, signal == "LEFT", known, intensity.value, c)
        }
        translate(start + arrowWidth + gap, top) {
            drawSignalArrow(arrowSize, false, signal == "RIGHT", known, intensity.value, c)
        }
        if (!known) {
            drawLine(
                c.secondary, Offset(size.width / 2 - 6.dp.toPx(), size.height / 2),
                Offset(size.width / 2 + 6.dp.toPx(), size.height / 2), 2.dp.toPx()
            )
        }
    }
}

/** A broad, ribbed brake pedal with a fixed hinge and a short, damped travel. */
internal fun DrawScope.drawBrakePedal(
    bounds: Size, pressed: Float, known: Boolean, c: CoachColors
) {
    scale(bounds.width / 100f, bounds.height / 100f, pivot = Offset.Zero) {
        val p = pressed.coerceIn(0f, 1f)
        val travel = 8f * p
        val alpha = if (known) 1f else .38f
        val metal = c.secondary.copy(alpha = alpha)
        val face = lerp(c.background, c.selected, p)
        val edge = lerp(c.secondary, c.accent, p).copy(alpha = alpha)
        drawOval(c.text.copy(alpha = .045f), Offset(18f, 85f), Size(64f, 8f))
        drawLine(c.line, Offset(12f, 94f), Offset(88f, 94f), 1f)
        drawRoundRect(c.line.copy(alpha = alpha), Offset(40f, 6f), Size(20f, 14f), CornerRadius(4f))
        drawCircle(metal, 2f, Offset(50f, 13f))
        val arm = Path().apply {
            moveTo(50f, 18f)
            lineTo(50f, 26f + travel)
            lineTo(46f, 45f + travel)
        }
        drawPath(arm, metal, style = Stroke(8f, cap = StrokeCap.Round, join = StrokeJoin.Round))
        fun pedal(extra: Float) = Path().apply {
            moveTo(27f, 35f + travel + extra)
            lineTo(73f, 35f + travel + extra)
            quadraticBezierTo(77f, 35f + travel + extra, 78f, 40f + travel + extra)
            lineTo(84f, 73f + travel * .65f + extra)
            quadraticBezierTo(85f, 79f + travel * .65f + extra, 79f, 79f + travel * .65f + extra)
            lineTo(21f, 79f + travel * .65f + extra)
            quadraticBezierTo(15f, 79f + travel * .65f + extra, 16f, 73f + travel * .65f + extra)
            lineTo(22f, 40f + travel + extra)
            quadraticBezierTo(23f, 35f + travel + extra, 27f, 35f + travel + extra)
            close()
        }
        drawPath(pedal(4f - p * 2f), c.line.copy(alpha = alpha))
        drawPath(pedal(0f), Brush.verticalGradient(
            listOf(lerp(face, c.secondary, .08f), face), startY = 35f + travel, endY = 79f + travel
        ), alpha = alpha)
        drawPath(pedal(0f), edge, style = Stroke(1.8f))
        // Tread bars distinguish this from the narrow accelerator pedal.
        repeat(4) { i ->
            val y = 44f + i * 8f + travel * (1f - i * .10f)
            val inset = 29f - i * 1.2f
            drawLine(edge.copy(alpha = alpha * .8f), Offset(inset, y), Offset(100f - inset, y), 3f, StrokeCap.Round)
        }
        drawLine(edge.copy(alpha = alpha * .45f), Offset(31f, 38f + travel), Offset(69f, 38f + travel), 1f, StrokeCap.Round)
        if (known && p > 0f) {
            drawLine(c.accent.copy(alpha = p), Offset(34f, 94f), Offset(66f, 94f), 2.5f, StrokeCap.Round)
        }
    }
}

/** Dashboard direction indicators; separate from the bent route guidance arrows. */
internal fun DrawScope.drawSignalArrow(
    bounds: Size, left: Boolean, active: Boolean, known: Boolean,
    intensity: Float, c: CoachColors
) {
    scale(bounds.width / 100f, bounds.height / 100f, pivot = Offset.Zero) {
        scale(if (left) 1f else -1f, 1f, pivot = Offset(50f, 50f)) {
            val arrow = Path().apply {
                moveTo(8f, 50f)
                lineTo(40f, 20f)
                lineTo(40f, 37f)
                lineTo(89f, 37f)
                lineTo(89f, 63f)
                lineTo(40f, 63f)
                lineTo(40f, 80f)
                close()
            }
            val color = signalColor(c)
            if (active && known) {
                drawPath(arrow, color.copy(alpha = intensity.coerceIn(.18f, 1f)))
                // Persistent outline keeps the direction legible in the dim phase.
                drawPath(arrow, color, style = Stroke(2.2f, join = StrokeJoin.Round))
            } else {
                drawPath(arrow, c.line.copy(alpha = if (known) .22f else .08f))
                drawPath(arrow, c.secondary.copy(alpha = if (known) .7f else .32f),
                    style = Stroke(2.2f, join = StrokeJoin.Round))
            }
        }
    }
}

@Composable
internal fun LearningGoalImage(goal: LearningGoal, modifier: Modifier = Modifier) {
    val c = LocalCoachColors.current
    when (goal) {
        LearningGoal.ROUTE_PREVIEW -> TurnSymbol("RIGHT", c.accent, modifier)
        LearningGoal.SPEED_REVIEW -> BrakePedalImage(false, false, modifier)
        LearningGoal.SIGNAL_CHECK -> Canvas(modifier) {
            val edge = size.minDimension * .55f
            translate(0f, (size.height - edge) / 2f) {
                drawSignalArrow(Size(edge, edge), true, true, true, 1f, c)
            }
            translate(size.width - edge, (size.height - edge) / 2f) {
                drawSignalArrow(Size(edge, edge), false, true, true, 1f, c)
            }
        }
    }
}
