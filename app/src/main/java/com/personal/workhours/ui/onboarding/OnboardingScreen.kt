package com.personal.workhours.ui.onboarding

import android.Manifest
import android.content.res.Configuration
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.rounded.Alarm
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.NightsStay
import androidx.compose.material.icons.rounded.NotificationsNone
import androidx.compose.material.icons.rounded.PhonelinkLock
import androidx.compose.material.icons.rounded.WbSunny
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import com.personal.workhours.alarm.PermissionHealthChecker
import com.personal.workhours.alarm.PermissionHealthStatus
import com.personal.workhours.domain.WorkDurationCalculator
import com.personal.workhours.domain.model.ThemeMode
import com.personal.workhours.ui.components.InsetGroup
import com.personal.workhours.ui.components.InsetRowDivider
import com.personal.workhours.ui.components.LibbleLogo
import com.personal.workhours.ui.components.PillToggle
import com.personal.workhours.ui.components.SectionOverline
import com.personal.workhours.ui.components.TactileButton
import com.personal.workhours.ui.components.TactileOutlinedButton
import com.personal.workhours.ui.components.WorkTimePickerDialog
import com.personal.workhours.ui.theme.LibbleMotion
import com.personal.workhours.ui.theme.WorkHoursTheme
import com.personal.workhours.ui.theme.WorkHoursThemeExtras
import com.personal.workhours.ui.theme.tabular
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.time.DayOfWeek
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale

private const val STEP_COUNT = 5

@Composable
fun OnboardingScreen(
    onCompleteOnboarding: (
        workDaysOfWeek: Set<Int>,
        startMinuteOfDay: Int,
        endMinuteOfDay: Int,
        defaultBreakMinutes: Int
    ) -> Unit,
    onTriggerTestAlarm: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val checker = remember { PermissionHealthChecker(context) }
    var health by remember { mutableStateOf(checker.check()) }

    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        health = checker.check()
    }

    val notifPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) {
        health = checker.check()
    }

    var currentStep by remember { mutableIntStateOf(0) }
    var selectedDays by remember { mutableStateOf(setOf(1, 2, 3, 4, 5)) }
    var startMinutes by remember { mutableIntStateOf(8 * 60) }
    var endMinutes by remember { mutableIntStateOf(17 * 60) }
    var breakMinutes by remember { mutableIntStateOf(60) }
    var pickingTarget by remember { mutableStateOf<String?>(null) }

    Surface(
        color = MaterialTheme.colorScheme.background,
        modifier = modifier.fillMaxSize()
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .padding(horizontal = 24.dp, vertical = 20.dp)
        ) {
            if (currentStep > 0) {
                SegmentedProgressBar(
                    currentStep = currentStep,
                    totalSteps = STEP_COUNT - 1,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 4.dp, bottom = 24.dp)
                )
            }

            Box(modifier = Modifier.weight(1f)) {
                AnimatedContent(
                    targetState = currentStep,
                    transitionSpec = {
                        val forward = targetState > initialState
                        val enterOffset: (Int) -> Int = { full -> if (forward) full / 3 else -full / 3 }
                        val exitOffset: (Int) -> Int = { full -> if (forward) -full / 3 else full / 3 }
                        (slideInHorizontally(
                            animationSpec = tween(280, easing = FastOutSlowInEasing),
                            initialOffsetX = enterOffset
                        ) + fadeIn(tween(260))) togetherWith (slideOutHorizontally(
                            animationSpec = tween(240, easing = FastOutSlowInEasing),
                            targetOffsetX = exitOffset
                        ) + fadeOut(tween(200)))
                    },
                    label = "onboardingStepContent"
                ) { step ->
                    when (step) {
                        0 -> WelcomeStep(onNext = { currentStep = 1 })

                        1 -> WorkdaysStep(
                            selectedDays = selectedDays,
                            onUpdateDays = { selectedDays = it },
                            onBack = { currentStep = 0 },
                            onNext = { currentStep = 2 }
                        )

                        2 -> PreferredHoursStep(
                            startMinutes = startMinutes,
                            endMinutes = endMinutes,
                            breakMinutes = breakMinutes,
                            onPickStart = { pickingTarget = "START" },
                            onPickEnd = { pickingTarget = "END" },
                            onSelectBreak = { breakMinutes = it },
                            onBack = { currentStep = 1 },
                            onNext = { currentStep = 3 }
                        )

                        3 -> PermissionsStep(
                            health = health,
                            onRequestNotifications = {
                                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                    notifPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                                } else {
                                    context.startActivity(checker.appNotificationSettingsIntent())
                                }
                            },
                            onOpenExactAlarm = { context.startActivity(checker.exactAlarmSettingsIntent()) },
                            onOpenFullScreen = { context.startActivity(checker.fullScreenIntentSettingsIntent()) },
                            onOpenBattery = { checker.openBatteryOptimizationSettings(context) },
                            onBack = { currentStep = 2 },
                            onNext = { currentStep = 4 }
                        )

                        4 -> ReadyStep(
                            selectedDays = selectedDays,
                            startMinutes = startMinutes,
                            endMinutes = endMinutes,
                            breakMinutes = breakMinutes,
                            onTestAlarm = onTriggerTestAlarm,
                            onBack = { currentStep = 3 },
                            onFinish = {
                                onCompleteOnboarding(selectedDays, startMinutes, endMinutes, breakMinutes)
                            }
                        )
                    }
                }
            }
        }
    }

    val target = pickingTarget
    if (target != null) {
        val mins = if (target == "START") startMinutes else endMinutes
        WorkTimePickerDialog(
            title = if (target == "START") "Time In" else "Time Out",
            initialHour = mins / 60,
            initialMinute = mins % 60,
            onDismiss = { pickingTarget = null },
            onConfirm = { h, m ->
                if (target == "START") startMinutes = h * 60 + m else endMinutes = h * 60 + m
                pickingTarget = null
            }
        )
    }
}

@Composable
private fun SegmentedProgressBar(
    currentStep: Int,
    totalSteps: Int,
    modifier: Modifier = Modifier
) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = modifier
    ) {
        for (idx in 1..totalSteps) {
            val active = idx <= currentStep
            val color by animateColorAsState(
                targetValue = if (active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
                animationSpec = tween(240, easing = FastOutSlowInEasing),
                label = "seg_$idx"
            )
            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(4.dp)
                    .clip(RoundedCornerShape(50))
                    .background(color)
            )
        }
    }
}

@Composable
private fun WelcomeStep(onNext: () -> Unit) {
    val density = LocalDensity.current
    val slideOffsetPx = with(density) { 24.dp.toPx() }

    val emblemAlpha = remember { Animatable(0f) }
    val emblemScale = remember { Animatable(0.95f) }
    val titleAlpha = remember { Animatable(0f) }
    val titleOffset = remember { Animatable(slideOffsetPx) }
    val tagAlpha = remember { Animatable(0f) }
    val tagOffset = remember { Animatable(slideOffsetPx) }
    val ctaAlpha = remember { Animatable(0f) }
    val ctaOffset = remember { Animatable(slideOffsetPx) }

    val infiniteTransition = rememberInfiniteTransition(label = "welcomeHalo")
    val haloScale by infiniteTransition.animateFloat(
        initialValue = 0.96f,
        targetValue = 1.08f,
        animationSpec = infiniteRepeatable(
            animation = tween(2200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "haloScale"
    )
    val haloAlpha by infiniteTransition.animateFloat(
        initialValue = 0.12f,
        targetValue = 0.26f,
        animationSpec = infiniteRepeatable(
            animation = tween(2200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "haloAlpha"
    )

    LaunchedEffect(Unit) {
        delay(320L)
        launch { emblemAlpha.animateTo(1f, tween(LibbleMotion.DurationReveal, easing = LibbleMotion.EaseOutExpo)) }
        launch { emblemScale.animateTo(1f, tween(LibbleMotion.DurationReveal, easing = LibbleMotion.EaseOutExpo)) }
        delay(220L)
        launch { titleAlpha.animateTo(1f, tween(280, easing = LibbleMotion.EaseOutExpo)) }
        launch { titleOffset.animateTo(0f, tween(280, easing = LibbleMotion.EaseOutExpo)) }
        delay(240L)
        launch { tagAlpha.animateTo(1f, tween(280, easing = LibbleMotion.EaseOutExpo)) }
        launch { tagOffset.animateTo(0f, tween(280, easing = LibbleMotion.EaseOutExpo)) }
        delay(280L)
        launch { ctaAlpha.animateTo(1f, tween(280, easing = LibbleMotion.EaseOutExpo)) }
        launch { ctaOffset.animateTo(0f, tween(280, easing = LibbleMotion.EaseOutExpo)) }
    }

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.SpaceBetween,
        modifier = Modifier.fillMaxSize()
    ) {
        Spacer(Modifier.height(24.dp))

        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.fillMaxWidth()
        ) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(120.dp)
                    .graphicsLayer {
                        alpha = emblemAlpha.value
                        scaleX = emblemScale.value
                        scaleY = emblemScale.value
                    }
            ) {
                Box(
                    modifier = Modifier
                        .size(116.dp)
                        .graphicsLayer {
                            scaleX = haloScale
                            scaleY = haloScale
                            alpha = haloAlpha
                        }
                        .clip(RoundedCornerShape(32.dp))
                        .background(MaterialTheme.colorScheme.primary)
                )
                LibbleLogo(size = 84.dp, animated = true)
            }

            Spacer(Modifier.height(32.dp))

            Text(
                text = "Libble",
                style = MaterialTheme.typography.displaySmall,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onBackground,
                modifier = Modifier.graphicsLayer {
                    alpha = titleAlpha.value
                    translationY = titleOffset.value
                }
            )

            Spacer(Modifier.height(10.dp))

            Text(
                text = "Clock in on time, every day.",
                style = MaterialTheme.typography.bodyLarge,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.graphicsLayer {
                    alpha = tagAlpha.value
                    translationY = tagOffset.value
                }
            )
        }

        TactileButton(
            onClick = onNext,
            contentPadding = PaddingValues(vertical = 15.dp),
            modifier = Modifier
                .fillMaxWidth()
                .graphicsLayer {
                    alpha = ctaAlpha.value
                    translationY = ctaOffset.value
                }
        ) {
            Text("Get Started", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.width(8.dp))
            Icon(Icons.AutoMirrored.Rounded.ArrowForward, contentDescription = null)
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun WorkdaysStep(
    selectedDays: Set<Int>,
    onUpdateDays: (Set<Int>) -> Unit,
    onBack: () -> Unit,
    onNext: () -> Unit
) {
    Column(
        verticalArrangement = Arrangement.SpaceBetween,
        modifier = Modifier.fillMaxSize()
    ) {
        Column(
            verticalArrangement = Arrangement.spacedBy(20.dp),
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    text = "Workdays",
                    style = MaterialTheme.typography.headlineMedium,
                    color = MaterialTheme.colorScheme.onBackground
                )
                Text(
                    text = "Select your active days.",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(
                    selected = selectedDays == setOf(1, 2, 3, 4, 5),
                    onClick = { onUpdateDays(setOf(1, 2, 3, 4, 5)) },
                    label = { Text("Mon – Fri") }
                )
                FilterChip(
                    selected = selectedDays == setOf(1, 2, 3, 4, 5, 6),
                    onClick = { onUpdateDays(setOf(1, 2, 3, 4, 5, 6)) },
                    label = { Text("Mon – Sat") }
                )
                FilterChip(
                    selected = selectedDays == (1..7).toSet(),
                    onClick = { onUpdateDays((1..7).toSet()) },
                    label = { Text("Daily") }
                )
            }

            Row(
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                (1..7).forEach { dow ->
                    val active = dow in selectedDays
                    val label = DayOfWeek.of(dow).getDisplayName(TextStyle.NARROW, Locale.US)
                    val fullLabel = DayOfWeek.of(dow).getDisplayName(TextStyle.FULL, Locale.US)
                    val bg by animateColorAsState(
                        targetValue = if (active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surface,
                        animationSpec = tween(200, easing = FastOutSlowInEasing),
                        label = "dayBg_$dow"
                    )
                    val fg by animateColorAsState(
                        targetValue = if (active) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                        animationSpec = tween(200, easing = FastOutSlowInEasing),
                        label = "dayFg_$dow"
                    )
                    val scale by animateFloatAsState(
                        targetValue = if (active) 1f else 0.94f,
                        animationSpec = spring(
                            dampingRatio = Spring.DampingRatioNoBouncy,
                            stiffness = Spring.StiffnessMedium
                        ),
                        label = "dayScale_$dow"
                    )

                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .size(44.dp)
                            .graphicsLayer {
                                scaleX = scale
                                scaleY = scale
                            }
                            .clip(CircleShape)
                            .background(bg)
                            .border(
                                width = 1.dp,
                                color = if (active) bg else MaterialTheme.colorScheme.outline.copy(alpha = 0.5f),
                                shape = CircleShape
                            )
                            .clickable(onClickLabel = fullLabel) {
                                val next = if (active && selectedDays.size > 1) {
                                    selectedDays - dow
                                } else {
                                    selectedDays + dow
                                }
                                onUpdateDays(next)
                            }
                    ) {
                        Text(
                            text = label,
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.SemiBold,
                            color = fg
                        )
                    }
                }
            }
        }

        StepperNavRow(onBack = onBack, nextLabel = "Continue", onNext = onNext)
    }
}

@Composable
private fun PreferredHoursStep(
    startMinutes: Int,
    endMinutes: Int,
    breakMinutes: Int,
    onPickStart: () -> Unit,
    onPickEnd: () -> Unit,
    onSelectBreak: (Int) -> Unit,
    onBack: () -> Unit,
    onNext: () -> Unit
) {
    val timeFormatter = remember { DateTimeFormatter.ofPattern("h:mm a", Locale.US) }
    val startText = LocalTime.of(startMinutes / 60, startMinutes % 60).format(timeFormatter)
    val endText = LocalTime.of(endMinutes / 60, endMinutes % 60).format(timeFormatter)
    val grossMins = ((endMinutes - startMinutes) + 1440) % 1440
    val netTargetMins = (grossMins - breakMinutes).coerceAtLeast(60)
    val targetLabel = WorkDurationCalculator.formatDurationHm(netTargetMins * 60_000L)

    Column(
        verticalArrangement = Arrangement.SpaceBetween,
        modifier = Modifier.fillMaxSize()
    ) {
        Column(
            verticalArrangement = Arrangement.spacedBy(20.dp),
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    text = "Work hours",
                    style = MaterialTheme.typography.headlineMedium,
                    color = MaterialTheme.colorScheme.onBackground
                )
                Text(
                    text = "Tap any time to adjust.",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            InsetGroup {
                TimePickerRow(
                    icon = Icons.Rounded.WbSunny,
                    label = "Time In",
                    timeText = startText,
                    onClick = onPickStart
                )
                InsetRowDivider()
                TimePickerRow(
                    icon = Icons.Rounded.NightsStay,
                    label = "Time Out",
                    timeText = endText,
                    onClick = onPickEnd
                )
            }

            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                SectionOverline(title = "Break")
                InsetGroup {
                    val breakOptions = listOf(0, 30, 45, 60)
                    PillToggle(
                        options = listOf("None", "30m", "45m", "1h"),
                        selectedIndex = breakOptions.indexOf(breakMinutes).coerceAtLeast(0),
                        onSelect = { idx -> onSelectBreak(breakOptions[idx]) },
                        fillWidth = true,
                        modifier = Modifier.padding(14.dp)
                    )
                }
            }

            Text(
                text = "$targetLabel daily target",
                style = MaterialTheme.typography.bodyMedium.tabular(),
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(horizontal = 4.dp)
            )
        }

        StepperNavRow(onBack = onBack, nextLabel = "Continue", onNext = onNext)
    }
}

@Composable
private fun TimePickerRow(
    icon: ImageVector,
    label: String,
    timeText: String,
    onClick: () -> Unit
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp)
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(36.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f))
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(19.dp)
            )
        }

        Text(
            text = label,
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f)
        )

        Surface(
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            shape = RoundedCornerShape(10.dp),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp)
            ) {
                Text(
                    text = timeText,
                    style = MaterialTheme.typography.titleMedium.tabular(),
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.primary
                )
                Icon(
                    imageVector = Icons.Rounded.ChevronRight,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.8f),
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    }
}

@Composable
private fun PermissionsStep(
    health: PermissionHealthStatus,
    onRequestNotifications: () -> Unit,
    onOpenExactAlarm: () -> Unit,
    onOpenFullScreen: () -> Unit,
    onOpenBattery: () -> Unit,
    onBack: () -> Unit,
    onNext: () -> Unit
) {
    val extras = WorkHoursThemeExtras.colors
    val grantedCount = listOf(
        health.notificationsGranted,
        health.exactAlarmsGranted,
        health.fullScreenIntentGranted,
        health.ignoringBatteryOptimizations
    ).count { it }

    Column(
        verticalArrangement = Arrangement.SpaceBetween,
        modifier = Modifier.fillMaxSize()
    ) {
        Column(
            verticalArrangement = Arrangement.spacedBy(18.dp),
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    text = "Reminders",
                    style = MaterialTheme.typography.headlineMedium,
                    color = MaterialTheme.colorScheme.onBackground
                )
                Text(
                    text = "Allow alerts so alarms work reliably.",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            InsetGroup {
                PermissionActionRow(
                    icon = Icons.Rounded.NotificationsNone,
                    title = "Notifications",
                    subtitle = "Show reminders and quick actions",
                    granted = health.notificationsGranted,
                    onGrant = onRequestNotifications
                )
                InsetRowDivider()
                PermissionActionRow(
                    icon = Icons.Rounded.Alarm,
                    title = "Exact Alarms",
                    subtitle = "Ring on time even in sleep mode",
                    granted = health.exactAlarmsGranted,
                    onGrant = onOpenExactAlarm
                )
                InsetRowDivider()
                PermissionActionRow(
                    icon = Icons.Rounded.PhonelinkLock,
                    title = "Lock Screen",
                    subtitle = "Show full-screen alarm when locked",
                    granted = health.fullScreenIntentGranted,
                    onGrant = onOpenFullScreen
                )
                InsetRowDivider()
                PermissionActionRow(
                    icon = Icons.Rounded.Bolt,
                    title = "Unrestricted Battery",
                    subtitle = "Prevent system from delaying alarms",
                    granted = health.ignoringBatteryOptimizations,
                    onGrant = onOpenBattery
                )
            }

            Text(
                text = if (grantedCount == 4) {
                    "All reminder permissions are ready."
                } else {
                    "$grantedCount of 4 enabled · You can update these anytime in Settings."
                },
                style = MaterialTheme.typography.bodyMedium,
                color = if (grantedCount == 4) extras.clockedIn else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 4.dp)
            )
        }

        StepperNavRow(onBack = onBack, nextLabel = "Continue", onNext = onNext)
    }
}

@Composable
private fun ReadyStep(
    selectedDays: Set<Int>,
    startMinutes: Int,
    endMinutes: Int,
    breakMinutes: Int,
    onTestAlarm: () -> Unit,
    onBack: () -> Unit,
    onFinish: () -> Unit
) {
    val timeFormatter = remember { DateTimeFormatter.ofPattern("h:mm a", Locale.US) }
    val startText = LocalTime.of(startMinutes / 60, startMinutes % 60).format(timeFormatter)
    val endText = LocalTime.of(endMinutes / 60, endMinutes % 60).format(timeFormatter)
    val daysSummary = if (selectedDays == setOf(1, 2, 3, 4, 5)) {
        "Mon – Fri"
    } else if (selectedDays == (1..7).toSet()) {
        "Daily"
    } else {
        selectedDays.sorted().joinToString(", ") {
            DayOfWeek.of(it).getDisplayName(TextStyle.SHORT, Locale.US)
        }
    }
    var testTriggered by remember { mutableStateOf(false) }

    val badgeAlpha = remember { Animatable(0f) }
    val badgeScale = remember { Animatable(0.95f) }
    LaunchedEffect(Unit) {
        launch { badgeAlpha.animateTo(1f, tween(260, easing = FastOutSlowInEasing)) }
        launch { badgeScale.animateTo(1f, spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessMediumLow)) }
    }

    Column(
        verticalArrangement = Arrangement.SpaceBetween,
        modifier = Modifier.fillMaxSize()
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
        ) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(72.dp)
                    .graphicsLayer {
                        alpha = badgeAlpha.value
                        scaleX = badgeScale.value
                        scaleY = badgeScale.value
                    }
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primaryContainer)
            ) {
                Icon(
                    imageVector = Icons.Rounded.Check,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.size(36.dp)
                )
            }

            Spacer(Modifier.height(20.dp))

            Text(
                text = "All set",
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.onBackground
            )

            Spacer(Modifier.height(10.dp))

            Surface(
                color = MaterialTheme.colorScheme.surface,
                shape = RoundedCornerShape(50),
                modifier = Modifier.padding(horizontal = 8.dp)
            ) {
                val breakLabel = if (breakMinutes == 0) "No break" else "${breakMinutes}m break"
                Text(
                    text = "$daysSummary · $startText – $endText · $breakLabel",
                    style = MaterialTheme.typography.bodyMedium.tabular(),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp)
                )
            }

            Spacer(Modifier.height(24.dp))

            TactileOutlinedButton(
                onClick = {
                    testTriggered = true
                    onTestAlarm()
                }
            ) {
                Icon(Icons.Rounded.Alarm, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text(if (testTriggered) "Alarm in 5s" else "Test 5s Alarm")
            }
        }

        StepperNavRow(onBack = onBack, nextLabel = "Start", onNext = onFinish)
    }
}

@Composable
private fun StepperNavRow(
    onBack: () -> Unit,
    nextLabel: String,
    onNext: () -> Unit
) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 12.dp)
    ) {
        TactileOutlinedButton(
            onClick = onBack,
            modifier = Modifier.weight(1f)
        ) {
            Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(6.dp))
            Text("Back")
        }
        TactileButton(
            onClick = onNext,
            modifier = Modifier.weight(1.4f)
        ) {
            Text(nextLabel)
            Spacer(Modifier.width(6.dp))
            Icon(Icons.AutoMirrored.Rounded.ArrowForward, contentDescription = null, modifier = Modifier.size(18.dp))
        }
    }
}

@Composable
private fun PermissionActionRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    granted: Boolean,
    onGrant: () -> Unit
) {
    val extras = WorkHoursThemeExtras.colors
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier
            .fillMaxWidth()
            .then(if (!granted) Modifier.clickable(onClick = onGrant) else Modifier)
            .padding(horizontal = 16.dp, vertical = 14.dp)
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(36.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(
                    if (granted) {
                        extras.clockedIn.copy(alpha = 0.14f)
                    } else {
                        MaterialTheme.colorScheme.surfaceContainerHigh
                    }
                )
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = if (granted) extras.clockedIn else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(19.dp)
            )
        }

        Column(
            verticalArrangement = Arrangement.spacedBy(2.dp),
            modifier = Modifier.weight(1f)
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        if (granted) {
            Surface(
                color = extras.clockedIn.copy(alpha = 0.14f),
                shape = RoundedCornerShape(50)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Check,
                        contentDescription = null,
                        tint = extras.clockedIn,
                        modifier = Modifier.size(14.dp)
                    )
                    Text(
                        text = "Ready",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = extras.clockedIn
                    )
                }
            }
        } else {
            Surface(
                onClick = onGrant,
                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.14f),
                shape = RoundedCornerShape(50),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.35f))
            ) {
                Text(
                    text = "Allow",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp)
                )
            }
        }
    }
}

@Preview(uiMode = Configuration.UI_MODE_NIGHT_NO, showBackground = true)
@Preview(uiMode = Configuration.UI_MODE_NIGHT_YES, showBackground = true)
@Composable
private fun OnboardingScreenPreview() {
    WorkHoursTheme(ThemeMode.SYSTEM) {
        OnboardingScreen(
            onCompleteOnboarding = { _, _, _, _ -> },
            onTriggerTestAlarm = {}
        )
    }
}
