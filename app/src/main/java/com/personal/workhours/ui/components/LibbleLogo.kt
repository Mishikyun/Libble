package com.personal.workhours.ui.components

import android.content.res.Configuration
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.personal.workhours.domain.model.ThemeMode
import com.personal.workhours.ui.theme.LibbleMotion
import com.personal.workhours.ui.theme.WorkHoursTheme
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun LibbleLogo(
    size: Dp,
    animated: Boolean = false,
    modifier: Modifier = Modifier
) {
    val minuteSweep = remember(animated) { Animatable(if (animated) -42f else 0f) }
    val hourSweep = remember(animated) { Animatable(if (animated) -55f else 0f) }

    LaunchedEffect(animated) {
        if (animated) {
            delay(360L)
            launch {
                minuteSweep.animateTo(
                    targetValue = 0f,
                    animationSpec = tween(durationMillis = 520, easing = LibbleMotion.EaseOutExpo)
                )
            }
            launch {
                hourSweep.animateTo(
                    targetValue = 0f,
                    animationSpec = tween(durationMillis = 620, easing = LibbleMotion.EaseOutExpo)
                )
            }
        }
    }

    Canvas(modifier = modifier.size(size)) {
        val w = this.size.width
        val h = this.size.height
        val corner = w * 0.28f

        // Obsidian-teal squircle container
        drawRoundRect(
            color = Color(0xFF071A1A),
            topLeft = Offset.Zero,
            size = Size(w, h),
            cornerRadius = CornerRadius(corner, corner)
        )

        // Subtle inner dial ring
        val dialRadius = w * 0.34f
        val center = Offset(w / 2f, h / 2f)
        drawCircle(
            color = Color(0xFF1E5F5A),
            radius = dialRadius,
            center = center,
            style = Stroke(width = w * 0.032f)
        )

        // Pivot shifted slightly down-left so the 12:15 hands center optically as an 'L'
        val pivot = Offset(w * 0.43f, h * 0.56f)
        val strokeWidth = w * 0.088f

        // Vertical hand (12 o'clock stem of 'L')
        rotate(degrees = minuteSweep.value, pivot = pivot) {
            drawLine(
                color = Color.White,
                start = pivot,
                end = Offset(pivot.x, w * 0.29f),
                strokeWidth = strokeWidth,
                cap = StrokeCap.Round
            )
        }

        // Horizontal hand (3 o'clock base of 'L')
        rotate(degrees = hourSweep.value, pivot = pivot) {
            drawLine(
                color = Color.White,
                start = pivot,
                end = Offset(w * 0.70f, pivot.y),
                strokeWidth = strokeWidth,
                cap = StrokeCap.Round
            )
        }

        // Mint-teal precision pivot dot
        drawCircle(
            color = Color(0xFF2DD4BF),
            radius = w * 0.044f,
            center = pivot
        )
    }
}

@Preview(uiMode = Configuration.UI_MODE_NIGHT_NO, showBackground = true)
@Preview(uiMode = Configuration.UI_MODE_NIGHT_YES, showBackground = true)
@Composable
private fun LibbleLogoPreview() {
    WorkHoursTheme(ThemeMode.SYSTEM) {
        Surface {
            LibbleLogo(size = 84.dp, modifier = Modifier.padding(16.dp))
        }
    }
}
