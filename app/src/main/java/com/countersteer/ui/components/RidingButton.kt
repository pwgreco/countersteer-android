package com.countersteer.ui.components

import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FloatingActionButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import com.countersteer.ui.theme.LocalRidingContext
import com.countersteer.ui.theme.CountersteerDesign

/**
 * Glove-friendly button sized for motorcycle use.
 * Minimum 80dp height in planning context, 96dp in riding context.
 */
@Composable
fun RidingButton(
    text: String,
    icon: ImageVector,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    containerColor: Color = MaterialTheme.colorScheme.primary,
    contentColor: Color = MaterialTheme.colorScheme.onPrimary,
    iconContentDescription: String? = null,
) {
    val isRiding = LocalRidingContext.current
    val height = if (isRiding) CountersteerDesign.TouchTarget.riding else CountersteerDesign.TouchTarget.standard

    ExtendedFloatingActionButton(
        onClick = onClick,
        modifier = modifier.height(height),
        containerColor = containerColor,
        contentColor = contentColor,
        elevation = FloatingActionButtonDefaults.elevation(
            defaultElevation = CountersteerDesign.Elevation.button,
        ),
        shape = CountersteerDesign.CornerRadius.large,
        icon = {
            Icon(
                imageVector = icon,
                contentDescription = iconContentDescription,
                modifier = Modifier.size(CountersteerDesign.IconSize.lg),
            )
        },
        text = {
            Text(
                text = text,
                style = MaterialTheme.typography.titleMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        },
    )
}
