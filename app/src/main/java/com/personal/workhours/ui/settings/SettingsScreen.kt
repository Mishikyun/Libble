package com.personal.workhours.ui.settings

import android.Manifest
import android.content.res.Configuration
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import com.personal.workhours.BuildConfig
import com.personal.workhours.alarm.PermissionHealthChecker
import com.personal.workhours.domain.model.ThemeMode
import com.personal.workhours.ui.components.InsetGroup
import com.personal.workhours.ui.components.InsetRowDivider
import com.personal.workhours.ui.components.InsetValueRow
import com.personal.workhours.ui.components.LibbleSwitch
import com.personal.workhours.ui.components.PillToggle
import com.personal.workhours.ui.components.SectionOverline
import com.personal.workhours.ui.theme.WorkHoursTheme
import com.personal.workhours.ui.theme.WorkHoursThemeExtras

@Composable
fun SettingsScreen(
    uiState: SettingsUiState,
    onSelectTheme: (ThemeMode) -> Unit,
    onToggleAutoDeduct: (Boolean) -> Unit,
    onTriggerTestAlarm: () -> Unit,
    onExportCsv: () -> Unit,
    onBackupJson: () -> Unit,
    onRestoreJson: () -> Unit,
    onReplayOnboarding: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val checker = remember { PermissionHealthChecker(context) }
    var health by remember { mutableStateOf(checker.check()) }
    var showTestScheduledDialog by remember { mutableStateOf(false) }

    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        health = checker.check()
    }

    val notifPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) {
        health = checker.check()
    }

    val extras = WorkHoursThemeExtras.colors

    Column(
        verticalArrangement = Arrangement.spacedBy(20.dp),
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 18.dp)
    ) {
        Text(
            text = "Settings",
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.onBackground
        )

        // Appearance
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            SectionOverline(title = "Appearance")
            InsetGroup {
                InsetValueRow(
                    label = "Theme",
                    trailing = {
                        PillToggle(
                            options = listOf("System", "Light", "Dark"),
                            selectedIndex = uiState.themeMode.ordinal,
                            onSelect = { idx -> onSelectTheme(ThemeMode.entries[idx]) }
                        )
                    }
                )
            }
        }

        // Alerts & Permissions
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            SectionOverline(title = "Alerts & permissions")
            InsetGroup {
                PermissionRow(
                    label = "Notifications",
                    subtitle = "Reminders and quick punch actions",
                    ok = health.notificationsGranted,
                    okColor = extras.clockedIn,
                    warnColor = extras.onBreak,
                    onFix = {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                            notifPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                        } else {
                            context.startActivity(checker.appNotificationSettingsIntent())
                        }
                    }
                )
                InsetRowDivider()
                PermissionRow(
                    label = "Exact Alarms",
                    subtitle = "Ring on time even in sleep mode",
                    ok = health.exactAlarmsGranted,
                    okColor = extras.clockedIn,
                    warnColor = extras.onBreak,
                    onFix = { context.startActivity(checker.exactAlarmSettingsIntent()) }
                )
                InsetRowDivider()
                PermissionRow(
                    label = "Lock Screen",
                    subtitle = "Full-screen alarm when locked",
                    ok = health.fullScreenIntentGranted,
                    okColor = extras.clockedIn,
                    warnColor = extras.onBreak,
                    onFix = { context.startActivity(checker.fullScreenIntentSettingsIntent()) }
                )
                InsetRowDivider()
                PermissionRow(
                    label = "Unrestricted battery",
                    subtitle = "Prevent system from delaying alarms",
                    ok = health.ignoringBatteryOptimizations,
                    okColor = extras.clockedIn,
                    warnColor = extras.onBreak,
                    onFix = { checker.openBatteryOptimizationSettings(context) }
                )
                InsetRowDivider()
                InsetValueRow(
                    label = "Test 5-second alarm",
                    subtitle = "Verify sound, vibration & modal",
                    onClick = {
                        onTriggerTestAlarm()
                        showTestScheduledDialog = true
                    },
                    trailing = {
                        Surface(
                            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.14f),
                            shape = RoundedCornerShape(50)
                        ) {
                            Text(
                                text = "Run",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.padding(horizontal = 14.dp, vertical = 5.dp)
                            )
                        }
                    }
                )
            }
        }

        // Preferences
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            SectionOverline(title = "Preferences")
            InsetGroup {
                InsetValueRow(
                    label = "Auto-deduct break",
                    subtitle = "Apply default break if none logged",
                    trailing = {
                        LibbleSwitch(
                            checked = uiState.autoDeductBreakEnabled,
                            onCheckedChange = onToggleAutoDeduct
                        )
                    }
                )
                InsetRowDivider()
                InsetValueRow(
                    label = "Replay setup intro",
                    onClick = onReplayOnboarding
                )
            }
        }

        // Data
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            SectionOverline(title = "Data")
            InsetGroup {
                InsetValueRow(label = "Export timesheets (CSV)", onClick = onExportCsv)
                InsetRowDivider()
                InsetValueRow(label = "Backup data (JSON)", onClick = onBackupJson)
                InsetRowDivider()
                InsetValueRow(label = "Restore from backup", onClick = onRestoreJson)
            }
        }

        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp)
        ) {
            Text(
                text = "Libble v${BuildConfig.VERSION_NAME}",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }

    if (showTestScheduledDialog) {
        AlertDialog(
            onDismissRequest = { showTestScheduledDialog = false },
            title = { Text("Test alarm armed") },
            text = { Text("Lock your screen or wait 5 seconds to verify your Libble alarm.") },
            confirmButton = {
                TextButton(onClick = { showTestScheduledDialog = false }) { Text("OK") }
            }
        )
    }
}

@Composable
private fun PermissionRow(
    label: String,
    ok: Boolean,
    okColor: androidx.compose.ui.graphics.Color,
    warnColor: androidx.compose.ui.graphics.Color,
    onFix: () -> Unit,
    subtitle: String? = null
) {
    InsetValueRow(
        label = label,
        subtitle = subtitle,
        onClick = if (ok) null else onFix,
        trailing = {
            if (ok) {
                Surface(
                    color = okColor.copy(alpha = 0.14f),
                    shape = RoundedCornerShape(50)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Check,
                            contentDescription = null,
                            tint = okColor,
                            modifier = Modifier.size(14.dp)
                        )
                        Text(
                            text = "Ready",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = okColor
                        )
                    }
                }
            } else {
                Surface(
                    color = warnColor.copy(alpha = 0.14f),
                    shape = RoundedCornerShape(50),
                    border = BorderStroke(1.dp, warnColor.copy(alpha = 0.35f))
                ) {
                    Text(
                        text = "Enable",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = warnColor,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 5.dp)
                    )
                }
            }
        }
    )
}

@Preview(uiMode = Configuration.UI_MODE_NIGHT_NO, showBackground = true)
@Preview(uiMode = Configuration.UI_MODE_NIGHT_YES, showBackground = true)
@Composable
private fun SettingsScreenPreview() {
    WorkHoursTheme(ThemeMode.SYSTEM) {
        Surface {
            SettingsScreen(
                uiState = SettingsUiState(ThemeMode.SYSTEM, true),
                onSelectTheme = {},
                onToggleAutoDeduct = {},
                onTriggerTestAlarm = {},
                onExportCsv = {},
                onBackupJson = {},
                onRestoreJson = {},
                onReplayOnboarding = {}
            )
        }
    }
}
