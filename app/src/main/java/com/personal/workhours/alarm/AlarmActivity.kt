package com.personal.workhours.alarm

import android.app.KeyguardManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.res.Configuration
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AlarmOn
import androidx.compose.material.icons.rounded.Snooze
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import com.personal.workhours.MainActivity
import com.personal.workhours.WorkHoursApplication
import com.personal.workhours.data.local.toDomain
import com.personal.workhours.domain.model.ReminderEventType
import com.personal.workhours.domain.model.ThemeMode
import com.personal.workhours.ui.components.LibbleLogo
import com.personal.workhours.ui.components.TactileButton
import com.personal.workhours.ui.theme.LibbleMotion
import com.personal.workhours.ui.theme.WorkHoursTheme
import com.personal.workhours.ui.theme.tabular
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

class AlarmActivity : ComponentActivity() {

    private var eventTypeState by mutableStateOf(ReminderEventType.TIME_IN)
    private var workDateState by mutableStateOf(LocalDate.now().toString())
    private var targetEpochState by mutableLongStateOf(System.currentTimeMillis())
    private var isTestState by mutableStateOf(false)
    private var actionInFlight = false

    private val closeUiReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (!actionInFlight && !isFinishing) {
                finish()
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        configureLockScreenBehavior()
        applyIntentExtras(intent)
        ContextCompat.registerReceiver(
            this,
            closeUiReceiver,
            IntentFilter(AlarmContracts.ACTION_CLOSE_ALARM_UI),
            ContextCompat.RECEIVER_NOT_EXPORTED
        )

        val container = (application as WorkHoursApplication).container

        setContent {
            val themeMode by container.preferencesRepository.themeMode.collectAsStateWithLifecycle(ThemeMode.SYSTEM)
            WorkHoursTheme(themeMode = themeMode) {
                AlarmLockScreenUi(
                    eventType = eventTypeState,
                    targetEpochMs = targetEpochState,
                    isTest = isTestState,
                    onPrimaryPunch = {
                        if (actionInFlight) return@AlarmLockScreenUi
                        actionInFlight = true
                        val currentEvent = eventTypeState
                        val currentWorkDate = workDateState
                        lifecycleScope.launch {
                            AlarmRingingService.stop(this@AlarmActivity)
                            container.notificationManager.cancelNotificationForEvent(currentEvent)
                            container.alarmScheduler.cancelEvent(currentEvent)
                            val date = runCatching { LocalDate.parse(currentWorkDate) }.getOrDefault(LocalDate.now())
                            val changed = withContext(Dispatchers.IO) {
                                when (currentEvent) {
                                    ReminderEventType.TIME_IN -> container.workLogRepository.clockIn(date)
                                    ReminderEventType.BREAK_START -> container.workLogRepository.startBreak(date)
                                    ReminderEventType.BREAK_END -> container.workLogRepository.resumeWork(date)
                                    ReminderEventType.TIME_OUT -> container.workLogRepository.clockOut(date)
                                }
                            }
                            val message = when (currentEvent) {
                                ReminderEventType.TIME_IN -> if (changed) "Clocked in" else "Already clocked in or shift already logged today"
                                ReminderEventType.BREAK_START -> if (changed) "Break started" else "Clock in first or break already active"
                                ReminderEventType.BREAK_END -> if (changed) "Work resumed" else "No active break to resume"
                                ReminderEventType.TIME_OUT -> if (changed) "Clocked out" else "No active shift to clock out"
                            }
                            Toast.makeText(applicationContext, message, Toast.LENGTH_SHORT).show()
                            val dashboardIntent = Intent(this@AlarmActivity, MainActivity::class.java).apply {
                                flags = Intent.FLAG_ACTIVITY_NEW_TASK or
                                    Intent.FLAG_ACTIVITY_CLEAR_TOP or
                                    Intent.FLAG_ACTIVITY_SINGLE_TOP
                                putExtra(MainActivity.EXTRA_OPEN_DASHBOARD, true)
                            }
                            startActivity(dashboardIntent)
                            finish()
                        }
                    },
                    onSnooze = {
                        if (actionInFlight) return@AlarmLockScreenUi
                        actionInFlight = true
                        val currentEvent = eventTypeState
                        val currentWorkDate = workDateState
                        val currentIsTest = isTestState
                        lifecycleScope.launch {
                            AlarmRingingService.stop(this@AlarmActivity)
                            container.notificationManager.cancelNotificationForEvent(currentEvent)
                            val cfg = container.database.scheduleDao().getReminderConfig(currentEvent.name)?.toDomain()
                            container.alarmScheduler.scheduleSnooze(
                                eventType = currentEvent,
                                workDate = currentWorkDate,
                                snoozeMinutes = cfg?.snoozeMinutes ?: 5,
                                isTest = currentIsTest
                            )
                            finish()
                        }
                    },
                    onDismiss = {
                        if (actionInFlight) return@AlarmLockScreenUi
                        actionInFlight = true
                        val currentEvent = eventTypeState
                        AlarmRingingService.stop(this@AlarmActivity)
                        container.notificationManager.cancelNotificationForEvent(currentEvent)
                        container.alarmScheduler.cancelEvent(currentEvent)
                        finish()
                    }
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        applyIntentExtras(intent)
    }

    override fun onDestroy() {
        runCatching { unregisterReceiver(closeUiReceiver) }
        super.onDestroy()
    }

    private fun applyIntentExtras(sourceIntent: Intent?) {
        eventTypeState = runCatching {
            ReminderEventType.valueOf(sourceIntent?.getStringExtra(AlarmContracts.EXTRA_EVENT_TYPE) ?: "")
        }.getOrDefault(ReminderEventType.TIME_IN)
        workDateState = sourceIntent?.getStringExtra(AlarmContracts.EXTRA_WORK_DATE) ?: LocalDate.now().toString()
        targetEpochState = sourceIntent?.getLongExtra(AlarmContracts.EXTRA_TARGET_EPOCH_MS, System.currentTimeMillis())
            ?: System.currentTimeMillis()
        isTestState = sourceIntent?.getBooleanExtra(AlarmContracts.EXTRA_IS_TEST, false) ?: false
        actionInFlight = false
    }

    private fun configureLockScreenBehavior() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
            val km = getSystemService(Context.KEYGUARD_SERVICE) as KeyguardManager
            km.requestDismissKeyguard(this, null)
        } else {
            @Suppress("DEPRECATION")
            window.addFlags(
                WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                    WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON or
                    WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON
            )
        }
    }
}

@Composable
private fun AlarmLockScreenUi(
    eventType: ReminderEventType,
    targetEpochMs: Long,
    isTest: Boolean,
    onPrimaryPunch: () -> Unit,
    onSnooze: () -> Unit,
    onDismiss: () -> Unit
) {
    val nowMs = remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(1_000L)
            nowMs.longValue = System.currentTimeMillis()
        }
    }

    val zoneId = remember { ZoneId.systemDefault() }
    val clockDigitsFmt = remember { DateTimeFormatter.ofPattern("h:mm", Locale.US) }
    val amPmFmt = remember { DateTimeFormatter.ofPattern("a", Locale.US) }
    val fullTimeFmt = remember { DateTimeFormatter.ofPattern("h:mm a", Locale.US) }

    val nowLocalTime = Instant.ofEpochMilli(nowMs.longValue).atZone(zoneId).toLocalTime()
    val currentDigits = nowLocalTime.format(clockDigitsFmt)
    val currentAmPm = nowLocalTime.format(amPmFmt)
    val scheduledTimeText = Instant.ofEpochMilli(targetEpochMs).atZone(zoneId).toLocalTime().format(fullTimeFmt)
    val overdueMinutes = ((nowMs.longValue - targetEpochMs) / 60_000L).coerceAtLeast(0L)

    val (headline, ctaLabel) = when (eventType) {
        ReminderEventType.TIME_IN -> "Time to Clock In" to "Clock In Now"
        ReminderEventType.BREAK_START -> "Time for Break" to "Start Break"
        ReminderEventType.BREAK_END -> "Break is over" to "Resume Work"
        ReminderEventType.TIME_OUT -> "Time to Clock Out" to "Clock Out Now"
    }

    val infiniteTransition = rememberInfiniteTransition(label = "alarmHalo")
    val ringScale by infiniteTransition.animateFloat(
        initialValue = 0.94f,
        targetValue = 1.16f,
        animationSpec = infiniteRepeatable(
            animation = tween(1600, easing = LibbleMotion.EaseOutExpo),
            repeatMode = RepeatMode.Reverse
        ),
        label = "ringScale"
    )
    val ringAlpha by infiniteTransition.animateFloat(
        initialValue = 0.24f,
        targetValue = 0.06f,
        animationSpec = infiniteRepeatable(
            animation = tween(1600, easing = LibbleMotion.EaseOutExpo),
            repeatMode = RepeatMode.Reverse
        ),
        label = "ringAlpha"
    )

    Surface(
        color = MaterialTheme.colorScheme.background,
        modifier = Modifier.fillMaxSize()
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween,
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .padding(horizontal = 24.dp, vertical = 32.dp)
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier.size(104.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(100.dp)
                            .graphicsLayer {
                                scaleX = ringScale
                                scaleY = ringScale
                                alpha = ringAlpha
                            }
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primary)
                    )
                    LibbleLogo(size = 64.dp, animated = true)
                }
                Text(
                    text = if (isTest) "LIBBLE · TEST" else "LIBBLE",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Row(verticalAlignment = Alignment.Bottom) {
                    Text(
                        text = currentDigits,
                        style = MaterialTheme.typography.displaySmall.copy(
                            fontSize = 64.sp,
                            lineHeight = 68.sp,
                            letterSpacing = (-1.6).sp,
                            fontWeight = FontWeight.SemiBold
                        ).tabular(),
                        color = MaterialTheme.colorScheme.onBackground
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        text = currentAmPm,
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(bottom = 12.dp)
                    )
                }

                Text(
                    text = headline,
                    style = MaterialTheme.typography.headlineMedium,
                    textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.onBackground
                )

                Surface(
                    color = MaterialTheme.colorScheme.surface,
                    shape = RoundedCornerShape(50),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                ) {
                    val infoText = if (overdueMinutes > 0 && !isTest) {
                        "Scheduled $scheduledTimeText · ${overdueMinutes}m overdue"
                    } else {
                        "Scheduled $scheduledTimeText"
                    }
                    Text(
                        text = infoText,
                        style = MaterialTheme.typography.bodyMedium.tabular(),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 7.dp)
                    )
                }
            }

            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                TactileButton(
                    onClick = onPrimaryPunch,
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                    contentPadding = PaddingValues(vertical = 17.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Rounded.AlarmOn, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text(ctaLabel, style = MaterialTheme.typography.titleMedium)
                }

                TactileButton(
                    onClick = onSnooze,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant,
                        contentColor = MaterialTheme.colorScheme.onSurface
                    ),
                    contentPadding = PaddingValues(vertical = 14.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Rounded.Snooze, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Snooze", style = MaterialTheme.typography.labelLarge)
                }

                TextButton(onClick = onDismiss) {
                    Text(
                        text = "Dismiss",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Preview(uiMode = Configuration.UI_MODE_NIGHT_NO, showBackground = true)
@Preview(uiMode = Configuration.UI_MODE_NIGHT_YES, showBackground = true)
@Composable
private fun AlarmLockScreenPreview() {
    WorkHoursTheme(ThemeMode.SYSTEM) {
        AlarmLockScreenUi(
            eventType = ReminderEventType.TIME_IN,
            targetEpochMs = System.currentTimeMillis() - 10 * 60_000L,
            isTest = false,
            onPrimaryPunch = {},
            onSnooze = {},
            onDismiss = {}
        )
    }
}
