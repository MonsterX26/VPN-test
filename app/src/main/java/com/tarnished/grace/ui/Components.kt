package com.tarnished.grace.ui

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tarnished.grace.data.NodeView

@Composable
fun OrnateDivider(modifier: Modifier = Modifier) {
    Canvas(modifier.fillMaxWidth().height(14.dp)) {
        val y = size.height / 2
        val cx = size.width / 2
        val gap = 14.dp.toPx()
        drawLine(
            brush = Brush.horizontalGradient(listOf(Color.Transparent, Grace.Gold), startX = 0f, endX = cx - gap),
            start = Offset(0f, y),
            end = Offset(cx - gap, y),
            strokeWidth = 1.dp.toPx()
        )
        drawLine(
            brush = Brush.horizontalGradient(listOf(Grace.Gold, Color.Transparent), startX = cx + gap, endX = size.width),
            start = Offset(cx + gap, y),
            end = Offset(size.width, y),
            strokeWidth = 1.dp.toPx()
        )
        val r = 6.dp.toPx()
        val diamond = Path().apply {
            moveTo(cx, y - r)
            lineTo(cx + r, y)
            lineTo(cx, y + r)
            lineTo(cx - r, y)
            close()
        }
        drawPath(diamond, Grace.Gold)
        val inner = Path().apply {
            moveTo(cx, y - r * 0.5f)
            lineTo(cx + r * 0.5f, y)
            lineTo(cx, y + r * 0.5f)
            lineTo(cx - r * 0.5f, y)
            close()
        }
        drawPath(inner, Grace.Ink)
    }
}

@Composable
fun GraceOrb(on: Boolean, busy: Boolean, diameter: Dp, onClick: () -> Unit) {
    val transition = rememberInfiniteTransition(label = "pulse")
    val pulse by transition.animateFloat(
        initialValue = 0.55f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(1800, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "pulseValue"
    )
    val glow = if (on) pulse else if (busy) 0.7f else 0.22f
    val tint = if (on) Grace.GoldBright else if (busy) Grace.Gold else Grace.GoldDim
    Box(
        modifier = Modifier
            .size(diameter)
            .clip(CircleShape)
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Canvas(Modifier.fillMaxSize()) {
            val c = center
            val r = size.minDimension / 2
            drawCircle(
                brush = Brush.radialGradient(listOf(Grace.Gold.copy(alpha = 0.5f * glow), Color.Transparent), center = c, radius = r),
                radius = r
            )
            drawRect(
                brush = Brush.verticalGradient(
                    listOf(Color.Transparent, Grace.GoldBright.copy(alpha = 0.55f * glow)),
                    startY = c.y - r * 0.98f,
                    endY = c.y
                ),
                topLeft = Offset(c.x - 1.5.dp.toPx(), c.y - r * 0.98f),
                size = Size(3.dp.toPx(), r * 0.98f)
            )
            drawCircle(color = Grace.Gold.copy(alpha = 0.55f + 0.4f * glow), radius = r * 0.62f, style = Stroke(width = 3.dp.toPx()))
            drawCircle(color = Grace.GoldDim, radius = r * 0.53f, style = Stroke(width = 1.dp.toPx()))
            drawCircle(
                brush = Brush.radialGradient(
                    listOf(Grace.GoldBright.copy(alpha = 0.15f + 0.6f * glow), Grace.Gold.copy(alpha = 0.2f * glow), Color.Transparent),
                    center = c,
                    radius = r * 0.5f
                ),
                radius = r * 0.5f
            )
            val ir = r * 0.17f
            drawArc(
                color = tint,
                startAngle = -60f,
                sweepAngle = 300f,
                useCenter = false,
                topLeft = Offset(c.x - ir, c.y - ir),
                size = Size(ir * 2, ir * 2),
                style = Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round)
            )
            drawLine(
                color = tint,
                start = Offset(c.x, c.y - ir * 1.35f),
                end = Offset(c.x, c.y - ir * 0.2f),
                strokeWidth = 3.dp.toPx(),
                cap = StrokeCap.Round
            )
        }
    }
}

@Composable
fun NodeCard(node: NodeView) {
    val shape = RoundedCornerShape(4.dp)
    val edge = if (node.active) Grace.Gold else Grace.GoldDim.copy(alpha = 0.45f)
    val latencyColor = when {
        !node.alive -> Grace.Blood
        node.latency == null -> Grace.Ash
        node.latency <= 300 -> Grace.GoldBright
        node.latency <= 700 -> Grace.Gold
        else -> Grace.Ember
    }
    val latencyText = when {
        !node.alive -> "ÉTEINT"
        node.latency == null -> "—"
        else -> "${node.latency} ms"
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(if (node.active) Grace.PanelHigh else Grace.Panel)
            .border(1.dp, edge, shape)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Canvas(Modifier.size(12.dp)) {
            val h = size.width / 2
            val p = Path().apply {
                moveTo(h, 0f)
                lineTo(size.width, h)
                lineTo(h, size.height)
                lineTo(0f, h)
                close()
            }
            val fill = when {
                node.active -> Grace.GoldBright
                node.alive -> Grace.GoldDim
                else -> Grace.Blood
            }
            drawPath(p, fill)
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                text = node.name,
                style = MaterialTheme.typography.bodyLarge,
                color = Grace.Bone,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (node.active) {
                Text(text = "GUIDE ACTUEL", style = MaterialTheme.typography.labelSmall, color = Grace.Gold)
            }
        }
        Spacer(Modifier.width(10.dp))
        Text(text = latencyText, style = MaterialTheme.typography.labelLarge, color = latencyColor)
    }
}

@Composable
fun LostBanner() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                Brush.verticalGradient(listOf(Color.Transparent, Color(0xDD000000), Color(0xDD000000), Color.Transparent))
            )
            .padding(vertical = 22.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = "CONNEXION PERDUE",
            color = Grace.Blood,
            fontFamily = GraceSerif,
            fontSize = 26.sp,
            letterSpacing = 7.sp,
            textAlign = TextAlign.Center
        )
    }
}

@Composable
fun GoldButton(text: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val shape = RoundedCornerShape(3.dp)
    Box(
        modifier = modifier
            .clip(shape)
            .background(Brush.verticalGradient(listOf(Grace.PanelHigh, Grace.Panel)))
            .border(1.dp, Grace.Gold, shape)
            .clickable(onClick = onClick)
            .padding(vertical = 14.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(text = text, style = MaterialTheme.typography.labelLarge, color = Grace.GoldBright)
    }
}

@Composable
fun SectionTitle(left: String, right: String) {
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(text = left, style = MaterialTheme.typography.titleMedium, color = Grace.Gold)
            Text(text = right, style = MaterialTheme.typography.labelSmall, color = Grace.Ash)
        }
        OrnateDivider()
    }
}
