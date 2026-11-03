package com.countersteer.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.semantics.LiveRegionMode
import com.countersteer.ui.theme.CountersteerDesign
import com.countersteer.ui.theme.SafetyAmber
import com.countersteer.ui.theme.SafetyGreen
import com.countersteer.ui.theme.SafetyRed

enum class SpeedZone { SAFE, NEAR, OVER }

fun speedZone(currentSpeedKmh: Int, limitKmh: Int?): SpeedZone {
    if (limitKmh == null) return SpeedZone.SAFE
    val diff = currentSpeedKmh - limitKmh
    return when {
        diff > 5 -> SpeedZone.OVER
        diff >= 0 -> SpeedZone.NEAR
        else -> SpeedZone.SAFE
    }
}

fun speedColor(zone: SpeedZone): Color = when (zone) {
    SpeedZone.OVER -> SafetyRed
    SpeedZone.NEAR -> SafetyAmber
    SpeedZone.SAFE -> SafetyGreen
}

@Composable
fun SpeedDisplay(
    currentSpeedKmh: Int,
    speedLimitKmh: Int?,
    modifier: Modifier = Modifier,
) {
    val zone by remember(currentSpeedKmh, speedLimitKmh) {
        derivedStateOf { speedZone(currentSpeedKmh, speedLimitKmh) }
    }
    val color by animateColorAsState(
        targetValue = speedColor(zone),
        label = "speedColor",
    )
    val limitScale by animateFloatAsState(
        targetValue = if (zone == SpeedZone.OVER) 1.1f else 1.0f,
        animationSpec = tween(300),
        label = "limitScale",
    )
    val stateDesc = when (zone) {
        SpeedZone.SAFE -> "Within speed limit"
        SpeedZone.NEAR -> "Near speed limit"
        SpeedZone.OVER -> "Over speed limit"
    }

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier.semantics(mergeDescendants = true) {
            contentDescription = "$currentSpeedKmh kilometers per hour"
            stateDescription = stateDesc
            liveRegion = LiveRegionMode.Polite
        },
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            val glowColor = color
            Text(
                text = "$currentSpeedKmh",
                color = color,
                style = MaterialTheme.typography.displaySmall,
                modifier = if (zone == SpeedZone.OVER) {
                    Modifier.drawBehind {
                        drawCircle(
                            color = glowColor.copy(alpha = 0.15f),
                            radius = size.maxDimension * 0.6f,
                        )
                    }
                } else {
                    Modifier
                },
            )
            Text(
                text = "km/h",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.labelLarge,
            )
        }

        if (speedLimitKmh != null) {
            Spacer(modifier = Modifier.width(CountersteerDesign.Spacing.md))
            SpeedLimitSign(
                speedLimitKmh = speedLimitKmh,
                borderColor = color,
                modifier = Modifier.scale(limitScale),
            )
        }
    }
}

@Composable
private fun SpeedLimitSign(
    speedLimitKmh: Int,
    borderColor: Color,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .size(width = 56.dp, height = 68.dp)
            .background(Color.White, RoundedCornerShape(6.dp))
            .border(3.dp, borderColor, RoundedCornerShape(6.dp))
            .padding(4.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = "MAX",
            color = Color.Black,
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.align(Alignment.TopCenter).padding(top = 2.dp),
        )
        Text(
            text = "$speedLimitKmh",
            color = Color.Black,
            fontSize = 24.sp,
            fontWeight = FontWeight.Black,
            modifier = Modifier.align(Alignment.Center).padding(top = 6.dp),
        )
        Text(
            text = "km/h",
            color = Color.Black,
            fontSize = 8.sp,
            modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 1.dp),
        )
    }
}
