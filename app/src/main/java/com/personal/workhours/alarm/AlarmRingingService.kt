package com.personal.workhours.alarm

import android.app.KeyguardManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.media.RingtoneManager
import android.net.Uri
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import androidx.core.app.ServiceCompat
import com.personal.workhours.domain.model.EscalationMode
import com.personal.workhours.domain.model.ReminderEventType
import com.personal.workhours.domain.model.VibrationPatternType
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class AlarmRingingService : Service() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var autoStopJob: Job? = null
    private var mediaPlayer: MediaPlayer? = null
    private var vibrationController: VibrationController? = null
    private var wakeLock: PowerManager.WakeLock? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == AlarmContracts.ACTION_STOP_SERVICE) {
            stopAlarmAndSelf()
            return START_NOT_STICKY
        }

        val eventType = runCatching {
            ReminderEventType.valueOf(intent?.getStringExtra(AlarmContracts.EXTRA_EVENT_TYPE) ?: "")
        }.getOrDefault(ReminderEventType.TIME_IN)
        val workDate = intent?.getStringExtra(AlarmContracts.EXTRA_WORK_DATE) ?: ""
        val targetEpochMs = intent?.getLongExtra(AlarmContracts.EXTRA_TARGET_EPOCH_MS, System.currentTimeMillis())
            ?: System.currentTimeMillis()
        val isTest = intent?.getBooleanExtra(AlarmContracts.EXTRA_IS_TEST, false) ?: false

        val mode = runCatching {
            EscalationMode.valueOf(intent?.getStringExtra(EXTRA_ESCALATION_MODE) ?: "")
        }.getOrDefault(EscalationMode.FULL_ALARM)
        val vibPattern = runCatching {
            VibrationPatternType.valueOf(intent?.getStringExtra(EXTRA_VIBRATION_PATTERN) ?: "")
        }.getOrDefault(VibrationPatternType.PULSE)
        val soundUriString = intent?.getStringExtra(EXTRA_SOUND_URI)

        val km = getSystemService(Context.KEYGUARD_SERVICE) as KeyguardManager
        val pm = getSystemService(Context.POWER_SERVICE) as PowerManager
        val isScreenUnlocked = pm.isInteractive && !km.isKeyguardLocked
        val openModalDirectly = isTest && mode == EscalationMode.FULL_ALARM && isScreenUnlocked

        val notifManager = AlarmNotificationManager(this)
        val notification = notifManager.buildStage2ForegroundNotification(
            eventType = eventType,
            workDate = workDate,
            targetEpochMs = targetEpochMs,
            fullScreen = (mode == EscalationMode.FULL_ALARM),
            isTest = isTest,
            suppressHeadsUp = openModalDirectly
        )

        val fgsType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK
        } else 0
        ServiceCompat.startForeground(this, AlarmContracts.NOTIF_ID_ALARM_SERVICE, notification, fgsType)

        if (openModalDirectly) {
            runCatching {
                startActivity(notifManager.buildAlarmActivityIntent(eventType, workDate, targetEpochMs, isTest))
            }
        }

        acquireBoundedWakeLock()
        startVibration(vibPattern)
        if (mode == EscalationMode.SOUND_AND_VIBRATE || mode == EscalationMode.FULL_ALARM) {
            startAlarmAudio(soundUriString)
        }

        autoStopJob?.cancel()
        autoStopJob = scope.launch {
            delay(AUTO_STOP_TIMEOUT_MS)
            notifManager.showMissedAlarmNotification(eventType)
            stopAlarmAndSelf()
        }

        return START_NOT_STICKY
    }

    private fun acquireBoundedWakeLock() {
        val pm = getSystemService(Context.POWER_SERVICE) as PowerManager
        if (wakeLock?.isHeld != true) {
            wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "WorkHours:AlarmRingingLock").apply {
                acquire(AUTO_STOP_TIMEOUT_MS)
            }
        }
    }

    private fun startVibration(pattern: VibrationPatternType) {
        if (vibrationController == null) {
            vibrationController = VibrationController(this)
        }
        vibrationController?.startLooping(pattern)
    }

    private fun startAlarmAudio(soundUriString: String?) {
        stopAudio()
        val uri = soundUriString
            ?.let { runCatching { Uri.parse(it) }.getOrNull() }
            ?.takeIf { it.scheme == "content" || it.scheme == "android.resource" }
            ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
            ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
            ?: return

        runCatching {
            mediaPlayer = MediaPlayer().apply {
                setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_ALARM)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build()
                )
                setDataSource(this@AlarmRingingService, uri)
                isLooping = true
                prepare()
                start()
            }
        }
    }

    private fun stopAudio() {
        runCatching {
            mediaPlayer?.let {
                if (it.isPlaying) it.stop()
                it.release()
            }
        }
        mediaPlayer = null
    }

    private fun stopAlarmAndSelf() {
        autoStopJob?.cancel()
        stopAudio()
        vibrationController?.stop()
        runCatching {
            if (wakeLock?.isHeld == true) wakeLock?.release()
        }
        wakeLock = null
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    override fun onDestroy() {
        stopAudio()
        vibrationController?.stop()
        runCatching {
            if (wakeLock?.isHeld == true) wakeLock?.release()
        }
        sendBroadcast(Intent(AlarmContracts.ACTION_CLOSE_ALARM_UI).setPackage(packageName))
        scope.cancel()
        super.onDestroy()
    }

    companion object {
        private const val AUTO_STOP_TIMEOUT_MS = 5 * 60_000L
        const val EXTRA_ESCALATION_MODE = "extra_escalation_mode"
        const val EXTRA_VIBRATION_PATTERN = "extra_vibration_pattern"
        const val EXTRA_SOUND_URI = "extra_sound_uri"

        fun stop(context: Context) {
            context.stopService(Intent(context, AlarmRingingService::class.java))
        }
    }
}
