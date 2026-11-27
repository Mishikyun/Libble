package com.personal.workhours.ui.dashboard

import android.content.res.Configuration
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.personal.workhours.domain.model.DailyChartPoint
import com.personal.workhours.domain.model.ThemeMode
import com.personal.workhours.domain.model.WorkDayStatus
import com.personal.workhours.ui.theme.LibbleMotion
import com.personal.workhours.ui.theme.WorkHoursTheme
import com.personal.workhours.ui.theme.WorkHoursThemeExtras
import com.personal.workhours.ui.theme.tabular
import java.util.Locale
import kotlin.math.max

@Composable
fun HoursBarChart(
    points: List<DailyChartPoint>,
    isMonthView: Boolean,
    onEditDay: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = MaterialTheme.colorScheme
    val extras = WorkHoursThemeExtras.colors
    val textMeasurer = rememberTextMeasurer()
    var selectedIndex by remember(points) { mutableIntStateOf(-1) }

    val maxHours = remember(points) {
        val peak = points.maxOfOrNull { max(it.netHours, it.targetHours) } ?: 8f
        max(10f, (peak + 2f).toInt().toFloat())
    }
    val targetLineHours = remember(points) {
        points.firstOrNull { it.targetHours > 0f }?.targetHours ?: 8f
    }

    val animatedFractions = points.mapIndexed { idx, pt ->
        val targetFrac = (pt.netHours / maxHours).coerceIn(0f, 1f)
        val anim by animateFloatAsState(
            targetValue = targetFrac,
            animationSpec = tween(
                durationMillis = LibbleMotion.DurationReveal,
                easing = LibbleMotion.EaseOutExpo
            ),
            label = "bar_$idx"
        )
        anim
    }

    val summaryTalkBack = remember(points) {
        val total = points.sumOf { it.netHours.toDouble() }
        String.format(Locale.US, "Hours chart showing %d days totaling %.1f hours.", points.size, total)
    }

    val labelStyle = TextStyle(
        color = colors.onSurfaceVariant,
        fontSize = 11.sp,
        fontFeatureSettings = "tnum"
    )

    Column(modifier = modifier.fillMaxWidth()) {
        val chartHeight = 164.dp
        val scrollState = rememberScrollState()

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .then(if (isMonthView) Modifier.horizontalScroll(scrollState) else Modifier)
        ) {
            val canvasModifier = if (isMonthView) {
                val minWidthDp = (points.size * 26).dp.coerceAtLeast(320.dp)
                Modifier
                    .width(minWidthDp)
                    .height(chartHeight)
            } else {
                Modifier
                    .fillMaxWidth()
                    .height(chartHeight)
            }

            Canvas(
                modifier = canvasModifier
                    .semantics { contentDescription = summaryTalkBack }
                    .pointerInput(points) {
                        detectTapGestures { offset ->
                            if (points.isEmpty()) return@detectTapGestures
                            val leftMargin = 28.dp.toPx()
                            val usableWidth = (size.width - leftMargin).coerceAtLeast(1f)
                            val slotWidth = usableWidth / points.size
                            val idx = ((offset.x - leftMargin) / slotWidth).toInt()
                            if (idx in points.indices) {
                                selectedIndex = if (selectedIndex == idx) -1 else idx
                            }
                        }
                    }
            ) {
                val leftMargin = 28.dp.toPx()
                val topMargin = 12.dp.toPx()
                val bottomMargin = 24.dp.toPx()
                val plotHeight = (size.height - topMargin - bottomMargin).coerceAtLeast(1f)
                val plotWidth = (size.width - leftMargin).coerceAtLeast(1f)

                // Layer 1: Baseline + dashed target reference line
                val baseY = topMargin + plotHeight
                drawLine(
                    color = colors.outlineVariant,
                    start = Offset(leftMargin, baseY),
                    end = Offset(size.width, baseY),
                    strokeWidth = 1.dp.toPx()
                )

                val targetY = topMargin + plotHeight * (1f - (targetLineHours / maxHours).coerceIn(0f, 1f))
                drawLine(
                    color = extras.chartTargetLine,
                    start = Offset(leftMargin, targetY),
                    end = Offset(size.width, targetY),
                    strokeWidth = 1.2.dp.toPx(),
                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(8f, 8f))
                )
                drawText(
                    textMeasurer = textMeasurer,
                    text = String.format(Locale.US, "%.0fh", targetLineHours),
                    topLeft = Offset(0f, (targetY - 7.dp.toPx()).coerceAtLeast(0f)),
                    style = labelStyle
                )

                // Layer 2: Clean data bars (no grey background tracks) + axis labels
                if (points.isNotEmpty()) {
                    val slotWidth = plotWidth / points.size
                    val barWidth = (slotWidth * 0.46f).coerceIn(10.dp.toPx(), 24.dp.toPx())
                    val corner = CornerRadius(6.dp.toPx(), 6.dp.toPx())

                    points.forEachIndexed { idx, pt ->
                        val centerX = leftMargin + slotWidth * idx + slotWidth / 2f
                        val barLeft = centerX - barWidth / 2f
                        val frac = animatedFractions[idx]
                        val barHeight = (plotHeight * frac).coerceAtLeast(if (pt.netHours > 0f) 4.dp.toPx() else 2.dp.toPx())
                        val barTop = baseY - barHeight

                        val barColor = when {
                            pt.netHours <= 0f -> colors.outlineVariant
                            pt.status == WorkDayStatus.DAY_OFF -> extras.chartBarDayOff
                            pt.netHours >= pt.targetHours && pt.targetHours > 0f -> extras.chartBarMet
                            else -> extras.chartBarUnder
                        }

                        if (idx == selectedIndex) {
                            drawRoundRect(
                                color = colors.primary.copy(alpha = 0.09f),
                                topLeft = Offset(centerX - slotWidth * 0.42f, topMargin),
                                size = Size(slotWidth * 0.84f, plotHeight),
                                cornerRadius = corner
                            )
                        }

                        drawRoundRect(
                            color = barColor,
                            topLeft = Offset(barLeft, barTop),
                            size = Size(barWidth, barHeight),
                            cornerRadius = corner
                        )

                        val measured = textMeasurer.measure(pt.shortLabel, style = labelStyle)
                        drawText(
                            textLayoutResult = measured,
                            topLeft = Offset(
                                x = centerX - measured.size.width / 2f,
                                y = baseY + 6.dp.toPx()
                            )
                        )
                    }
                }
            }
        }

        if (selectedIndex in points.indices) {
            val selected = points[selectedIndex]
            Spacer(modifier = Modifier.height(10.dp))
            HorizontalDivider(color = colors.outlineVariant)
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onEditDay(selected.workDate) }
                    .padding(top = 10.dp)
            ) {
                Text(
                    text = String.format(
                        Locale.US,
                        "%s · %.1fh worked · %dm break",
                        selected.workDate,
                        selected.netHours,
                        selected.breakMinutes
                    ),
                    style = MaterialTheme.typography.bodyMedium.tabular(),
                    color = colors.onSurfaceVariant
                )
                Text(
                    text = "Edit",
                    style = MaterialTheme.typography.labelLarge,
                    color = colors.primary
                )
            }
        }
    }
}

private val previewPoints = listOf(
    DailyChartPoint("2026-11-23", "M", 8.2f, 8f, 60, WorkDayStatus.COMPLETED),
    DailyChartPoint("2026-11-24", "T", 8.5f, 8f, 55, WorkDayStatus.COMPLETED),
    DailyChartPoint("2026-11-25", "W", 6.1f, 8f, 45, WorkDayStatus.CLOCKED_IN),
    DailyChartPoint("2026-11-26", "T", 0f, 8f, 0, WorkDayStatus.NOT_STARTED),
    DailyChartPoint("2026-11-27", "F", 0f, 8f, 0, WorkDayStatus.NOT_STARTED),
    DailyChartPoint("2026-11-28", "S", 0f, 0f, 0, WorkDayStatus.DAY_OFF),
    DailyChartPoint("2026-11-29", "S", 0f, 0f, 0, WorkDayStatus.DAY_OFF)
)

@Preview(uiMode = Configuration.UI_MODE_NIGHT_NO, showBackground = true)
@Preview(uiMode = Configuration.UI_MODE_NIGHT_YES, showBackground = true)
@Composable
private fun HoursBarChartPreview() {
    WorkHoursTheme(ThemeMode.SYSTEM) {
        Surface {
            HoursBarChart(
                points = previewPoints,
                isMonthView = false,
                onEditDay = {},
                modifier = Modifier.padding(16.dp)
            )
        }
    }
}
