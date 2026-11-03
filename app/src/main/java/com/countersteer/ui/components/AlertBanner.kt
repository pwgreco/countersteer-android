package com.countersteer.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.countersteer.alerts.AlertInfo
import com.countersteer.data.entity.MarkerType
import com.countersteer.ui.theme.CountersteerDesign
import com.countersteer.ui.theme.SafetyAmber
import com.countersteer.ui.theme.SafetyGreen
import com.countersteer.ui.theme.SafetyRed

@Composable
fun AlertBanner(
    alertInfo: AlertInfo?,
    modifier: Modifier = Modifier,
    onDismiss: () -> Unit = {},
) {
    AnimatedVisibility(
        visible = alertInfo != null,
        enter = slideInVertically(initialOffsetY = { -it }) + fadeIn(),
        exit = fadeOut(),
        modifier = modifier,
    ) {
        alertInfo?.let { alert ->
            val borderColor = alertBorderColor(alert.type)
            val icon = alertIcon(alert.type)

            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = CountersteerDesign.Spacing.md, vertical = CountersteerDesign.Spacing.xs)
                    .defaultMinSize(minHeight = CountersteerDesign.TouchTarget.standard)
                    .clickable(onClickLabel = "Dismiss alert") { onDismiss() }
                    .semantics {
                        stateDescription = "Alert: ${alert.displayName}"
                        liveRegion = LiveRegionMode.Assertive
                        customActions = listOf(
                            CustomAccessibilityAction("Dismiss alert") { onDismiss(); true },
                        )
                    },
                shape = CountersteerDesign.CornerRadius.medium,
                border = BorderStroke(2.dp, borderColor),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.92f),
                ),
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(CountersteerDesign.Spacing.md),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = alert.displayName,
                        tint = borderColor,
                        modifier = Modifier.size(36.dp),
                    )

                    Spacer(modifier = Modifier.width(CountersteerDesign.Spacing.sm))

                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = alert.displayName,
                            color = borderColor,
                            style = MaterialTheme.typography.titleMedium,
                        )
                        Text(
                            text = formatAlertDistance(alert.distanceMeters),
                            color = MaterialTheme.colorScheme.onSurface,
                            style = MaterialTheme.typography.bodyMedium,
                        )
                        if (!alert.note.isNullOrBlank()) {
                            Text(
                                text = alert.note,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                style = MaterialTheme.typography.labelLarge,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                }
            }
        }
    }
}

private fun alertBorderColor(type: MarkerType) = when (type) {
    MarkerType.SPEED_TRAP -> SafetyRed
    MarkerType.HAZARD -> SafetyAmber
    MarkerType.PHOTO_SPOT -> SafetyGreen
    MarkerType.CUSTOM -> SafetyGreen
    MarkerType.SPEED_CAMERA -> SafetyRed
    MarkerType.RED_LIGHT_CAMERA -> SafetyRed
}

private fun alertIcon(type: MarkerType): ImageVector = when (type) {
    MarkerType.SPEED_TRAP -> Icons.Default.CameraAlt
    MarkerType.HAZARD -> Icons.Default.Warning
    MarkerType.PHOTO_SPOT -> Icons.Default.PhotoCamera
    MarkerType.CUSTOM -> Icons.Default.LocationOn
    MarkerType.SPEED_CAMERA -> Icons.Default.CameraAlt
    MarkerType.RED_LIGHT_CAMERA -> Icons.Default.Warning
}

private fun formatAlertDistance(meters: Int): String = when {
    meters < 100 -> "Ahead"
    meters < 1000 -> "$meters m ahead"
    else -> "%.1f km ahead".format(meters / 1000.0)
}
