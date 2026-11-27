package com.personal.workhours.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.snapping.rememberSnapFlingBehavior
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.personal.workhours.ui.theme.LibbleMotion
import com.personal.workhours.ui.theme.tabular
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

@Composable
fun WorkTimePickerDialog(
    title: String,
    initialHour: Int,
    initialMinute: Int,
    onDismiss: () -> Unit,
    onConfirm: (hour: Int, minute: Int) -> Unit
) {
    val safeHour = initialHour.coerceIn(0, 23)
    var isPm by remember { mutableStateOf(safeHour >= 12) }
    var hour12 by remember {
        mutableIntStateOf(
            when (val h = safeHour % 12) {
                0 -> 12
                else -> h
            }
        )
    }
    var minute by remember { mutableIntStateOf(initialMinute.coerceIn(0, 59)) }

    val hourItems = remember { (1..12).map { it.toString() } }
    val minuteItems = remember { (0..59).map { it.toString().padStart(2, '0') } }
    val periodItems = remember { listOf("AM", "PM") }
    val formattedPreview = "$hour12:${minute.toString().padStart(2, '0')} ${if (isPm) "PM" else "AM"}"
    val surfaceColor = MaterialTheme.colorScheme.surface

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(24.dp),
            color = surfaceColor,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
            tonalElevation = 0.dp,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                verticalArrangement = Arrangement.spacedBy(20.dp),
                modifier = Modifier.padding(20.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = formattedPreview,
                        style = MaterialTheme.typography.titleMedium.tabular(),
                        color = MaterialTheme.colorScheme.primary
                    )
                }

                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(144.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                            .clip(RoundedCornerShape(14.dp))
                            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(14.dp))
                    )

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 8.dp)
                    ) {
                        DigitalWheelColumn(
                            items = hourItems,
                            selectedIndex = hour12 - 1,
                            onSelectedIndexChange = { hour12 = it + 1 },
                            looping = true,
                            modifier = Modifier.weight(1f)
                        )
                        Text(
                            text = ":",
                            style = MaterialTheme.typography.headlineMedium,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 2.dp)
                        )
                        DigitalWheelColumn(
                            items = minuteItems,
                            selectedIndex = minute,
                            onSelectedIndexChange = { minute = it },
                            looping = true,
                            modifier = Modifier.weight(1f)
                        )
                        Spacer(Modifier.width(8.dp))
                        DigitalWheelColumn(
                            items = periodItems,
                            selectedIndex = if (isPm) 1 else 0,
                            onSelectedIndexChange = { isPm = it == 1 },
                            looping = false,
                            accentSelected = true,
                            modifier = Modifier.weight(0.85f)
                        )
                    }

                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(28.dp)
                            .align(Alignment.TopCenter)
                            .background(Brush.verticalGradient(listOf(surfaceColor, Color.Transparent)))
                    )
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(28.dp)
                            .align(Alignment.BottomCenter)
                            .background(Brush.verticalGradient(listOf(Color.Transparent, surfaceColor)))
                    )
                }

                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    TextButton(onClick = onDismiss) {
                        Text("Cancel")
                    }
                    TactileButton(
                        onClick = {
                            val h24 = when {
                                !isPm && hour12 == 12 -> 0
                                isPm && hour12 == 12 -> 12
                                isPm -> hour12 + 12
                                else -> hour12
                            }
                            onConfirm(h24, minute)
                        },
                        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 10.dp)
                    ) {
                        Text("Set")
                    }
                }
            }
        }
    }
}

@Composable
private fun DigitalWheelColumn(
    items: List<String>,
    selectedIndex: Int,
    onSelectedIndexChange: (Int) -> Unit,
    modifier: Modifier = Modifier,
    looping: Boolean = true,
    accentSelected: Boolean = false
) {
    val itemHeight = 48.dp
    val itemHeightPx = with(LocalDensity.current) { itemHeight.toPx() }
    val totalCount = if (looping) items.size * 200 else items.size
    val startIndex = remember(items.size, looping) {
        val safeSelected = selectedIndex.coerceIn(0, items.lastIndex)
        if (looping) {
            val mid = totalCount / 2
            (mid - (mid % items.size)) + safeSelected
        } else {
            safeSelected
        }
    }

    val listState = rememberLazyListState(initialFirstVisibleItemIndex = startIndex)
    val flingBehavior = rememberSnapFlingBehavior(lazyListState = listState)
    val haptics = LocalHapticFeedback.current
    val scope = rememberCoroutineScope()

    val centeredRawIndex by remember {
        derivedStateOf {
            val firstIdx = listState.firstVisibleItemIndex
            val offset = listState.firstVisibleItemScrollOffset
            (if (offset > itemHeightPx / 2f) firstIdx + 1 else firstIdx).coerceIn(0, totalCount - 1)
        }
    }

    var initialized by remember { mutableStateOf(false) }
    LaunchedEffect(centeredRawIndex) {
        val mod = ((centeredRawIndex % items.size) + items.size) % items.size
        onSelectedIndexChange(mod)
        if (initialized) {
            haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
        } else {
            initialized = true
        }
    }

    LazyColumn(
        state = listState,
        flingBehavior = flingBehavior,
        contentPadding = PaddingValues(vertical = itemHeight),
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier.height(itemHeight * 3)
    ) {
        items(count = totalCount, key = { it }) { index ->
            val mod = index % items.size
            val isCentered = index == centeredRawIndex
            val scale by animateFloatAsState(
                targetValue = if (isCentered) 1f else if (accentSelected) 0.88f else 0.86f,
                animationSpec = tween(120, easing = LibbleMotion.EaseOutExpo),
                label = "wheelScale"
            )
            val alpha by animateFloatAsState(
                targetValue = if (isCentered) 1f else 0.35f,
                animationSpec = tween(120, easing = LibbleMotion.EaseOutExpo),
                label = "wheelAlpha"
            )

            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(itemHeight)
                    .clickable {
                        scope.launch { listState.animateScrollToItem(index) }
                    }
                    .graphicsLayer {
                        scaleX = scale
                        scaleY = scale
                        this.alpha = alpha
                    }
            ) {
                Text(
                    text = items[mod],
                    style = if (accentSelected) {
                        MaterialTheme.typography.titleLarge
                    } else {
                        MaterialTheme.typography.headlineMedium.tabular()
                    },
                    fontWeight = if (isCentered) FontWeight.SemiBold else FontWeight.Normal,
                    color = when {
                        isCentered && accentSelected -> MaterialTheme.colorScheme.primary
                        isCentered -> MaterialTheme.colorScheme.onSurface
                        else -> MaterialTheme.colorScheme.onSurfaceVariant
                    }
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WorkDatePickerDialog(
    initialDate: LocalDate,
    onDismiss: () -> Unit,
    onConfirm: (LocalDate) -> Unit
) {
    val initialUtcMillis = initialDate.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
    val state = rememberDatePickerState(initialSelectedDateMillis = initialUtcMillis)

    DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(
                onClick = {
                    val millis = state.selectedDateMillis ?: initialUtcMillis
                    val date = Instant.ofEpochMilli(millis).atZone(ZoneOffset.UTC).toLocalDate()
                    onConfirm(date)
                }
            ) {
                Text("OK")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    ) {
        DatePicker(state = state)
    }
}
