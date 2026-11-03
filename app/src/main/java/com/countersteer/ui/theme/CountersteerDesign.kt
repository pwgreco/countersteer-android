package com.countersteer.ui.theme

import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Whether the UI is in active riding/navigation mode.
 * Components query this to adjust touch targets, animation speed, and information density.
 */
val LocalRidingContext = compositionLocalOf { false }

object CountersteerDesign {

    object Spacing {
        val xxs: Dp = 4.dp
        val xs: Dp = 8.dp
        val sm: Dp = 12.dp
        val md: Dp = 16.dp
        val lg: Dp = 24.dp
        val xl: Dp = 32.dp
        val xxl: Dp = 48.dp

        /** Minimum gap between interactive elements (automotive guideline + vibration buffer). */
        val targetGap: Dp = 28.dp
    }

    object TouchTarget {
        /** Standard minimum for all interactive elements. */
        val standard: Dp = 80.dp
        /** Critical actions during riding (end nav, save/discard). */
        val riding: Dp = 96.dp
        /** Top bar icon buttons. */
        val topBarIcon: Dp = 56.dp
        /** Search bars and input fields. */
        val input: Dp = 56.dp
    }

    object CornerRadius {
        val small = RoundedCornerShape(8.dp)
        val medium = RoundedCornerShape(12.dp)
        val large = RoundedCornerShape(16.dp)
        val full = CircleShape
    }

    object IconSize {
        val sm: Dp = 20.dp
        val md: Dp = 24.dp
        val lg: Dp = 28.dp
        val xl: Dp = 32.dp
        val xxl: Dp = 64.dp
    }

    object Animation {
        /** Duration for riding context — near-instant, never blocks a glance. */
        const val RIDING_DURATION_MS = 150

        /** Duration for planning context — smooth Material 3 feel. */
        const val PLANNING_DURATION_MS = 300

        fun <T> ridingSpec(): AnimationSpec<T> = tween(RIDING_DURATION_MS)

        fun <T> planningSpec(): AnimationSpec<T> = spring(stiffness = Spring.StiffnessMediumLow)
    }

    object Elevation {
        val button: Dp = 6.dp
        val card: Dp = 2.dp
        val overlay: Dp = 8.dp
    }
}
