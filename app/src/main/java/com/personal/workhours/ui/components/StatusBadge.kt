package com.personal.workhours.ui.components

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import com.personal.workhours.domain.model.WorkDayStatus
import com.personal.workhours.ui.theme.LibbleMotion
import com.personal.workhours.ui.theme.WorkHoursThemeExtras

@Composable
fun StatusBadge(
    status: WorkDayStatus,
    modifier: Modifier = Modifier
) {
    val extras = WorkHoursThemeExtras.colors
    val (color, label) = when (status) {
        WorkDayStatus.NOT_STARTED -> extras.clockedOut to "Ready"
        WorkDayStatus.CLOCKED_IN -> extras.clockedIn to "Clocked in"
        WorkDayStatus.ON_BREAK -> extras.onBreak to "On break"
        WorkDayStatus.COMPLETED -> MaterialTheme.colorScheme.primary to "Completed"
        WorkDayStatus.DAY_OFF -> extras.clockedOut to "Day off"
        WorkDayStatus.MISSED -> extras.alarm to "Missed"
    }

    val isLive = status == WorkDayStatus.CLOCKED_IN || status == WorkDayStatus.ON_BREAK
    val pulseAlpha = if (isLive) {
        val transition = rememberInfiniteTransition(label = "statusPulse")
        val alpha by transition.animateFloat(
            initialValue = 0.45f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(
                animation = tween(1100, easing = LibbleMotion.EaseOutExpo),
                repeatMode = RepeatMode.Reverse
            ),
            label = "dotAlpha"
        )
        alpha
    } else 1f

    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(7.dp),
        modifier = modifier
    ) {
        Box(
            modifier = Modifier
                .size(7.dp)
                .graphicsLayer { alpha = pulseAlpha }
                .clip(CircleShape)
                .background(color)
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge,
            color = if (isLive || status == WorkDayStatus.MISSED) color else MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
