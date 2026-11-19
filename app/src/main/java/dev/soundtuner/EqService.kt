package dev.soundtuner

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.SharedPreferences
import android.content.pm.ServiceInfo
import android.graphics.drawable.Icon
import android.media.AudioDeviceCallback
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.media.audiofx.AudioEffect
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper

/**
 * Keeps the EQ alive in the background (Android removes audio effects when their app dies),
 * re-applies it whenever a setting changes, and switches profile when headphones connect.
 */
class EqService : Service() {
    companion object {
        const val ACTION_APPLY = "dev.soundtuner.APPLY"
        const val ACTION_ATTACH = "dev.soundtuner.ATTACH"
        const val ACTION_STOP = "dev.soundtuner.STOP"
        private const val CHANNEL_ID = "eq"
        private const val NOTIFICATION_ID = 1
    }

    private lateinit var settings: Settings
    private lateinit var audio: AudioManager
    private val engine = EqEngine()

    private val prefListener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
        if (key != "global_mode") refreshEffect()
    }

    /** Music apps announce their audio sessions with these standard broadcasts. */
    private val sessionReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            val session = intent.getIntExtra(AudioEffect.EXTRA_AUDIO_SESSION, AudioEffect.ERROR_BAD_VALUE)
            if (session <= 0) return
            when (intent.action) {
                AudioEffect.ACTION_OPEN_AUDIO_EFFECT_CONTROL_SESSION -> engine.attach(session)
                AudioEffect.ACTION_CLOSE_AUDIO_EFFECT_CONTROL_SESSION -> engine.detach(session)
            }
        }
    }

    private val deviceCallback = object : AudioDeviceCallback() {
        override fun onAudioDevicesAdded(addedDevices: Array<out AudioDeviceInfo>) = matchProfileToOutput()
        override fun onAudioDevicesRemoved(removedDevices: Array<out AudioDeviceInfo>) = matchProfileToOutput()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        settings = Settings(this)
        audio = getSystemService(AudioManager::class.java)
        createChannel()
        goForeground()

        settings.globalMode = engine.attach(0)

        val filter = IntentFilter().apply {
            addAction(AudioEffect.ACTION_OPEN_AUDIO_EFFECT_CONTROL_SESSION)
            addAction(AudioEffect.ACTION_CLOSE_AUDIO_EFFECT_CONTROL_SESSION)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(sessionReceiver, filter, Context.RECEIVER_EXPORTED)
        } else {
            registerReceiver(sessionReceiver, filter)
        }
        audio.registerAudioDeviceCallback(deviceCallback, Handler(Looper.getMainLooper()))
        settings.prefs.registerOnSharedPreferenceChangeListener(prefListener)

        matchProfileToOutput()
        refreshEffect()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        goForeground()
        when (intent?.action) {
            ACTION_STOP -> {
                settings.enabled = false
                stopSelf()
                return START_NOT_STICKY
            }
            ACTION_ATTACH -> {
                val session = intent.getIntExtra(AudioEffect.EXTRA_AUDIO_SESSION, AudioEffect.ERROR_BAD_VALUE)
                if (session > 0) engine.attach(session)
            }
        }
        if (!settings.enabled) {
            stopSelf()
            return START_NOT_STICKY
        }
        refreshEffect()
        return START_STICKY
    }

    override fun onDestroy() {
        settings.prefs.unregisterOnSharedPreferenceChangeListener(prefListener)
        audio.unregisterAudioDeviceCallback(deviceCallback)
        runCatching { unregisterReceiver(sessionReceiver) }
        engine.releaseAll()
        super.onDestroy()
    }

    private fun refreshEffect() {
        engine.update(settings.filters(), settings.enabled)
        if (settings.enabled) {
            getSystemService(NotificationManager::class.java).notify(NOTIFICATION_ID, buildNotification())
        }
    }

    /** EarPods plugged in -> EarPods profile. Galaxy Buds connected -> a Buds profile (keeps Hi-Fi/Pro choice). */
    private fun matchProfileToOutput() {
        if (!settings.autoSwitch) return
        val outputs = audio.getDevices(AudioManager.GET_DEVICES_OUTPUTS)
        fun AudioDeviceInfo.named(text: String) = productName?.toString()?.contains(text, ignoreCase = true) == true

        val earPods = outputs.any {
            (it.type == AudioDeviceInfo.TYPE_USB_HEADSET || it.type == AudioDeviceInfo.TYPE_USB_DEVICE) && it.named("EarPods")
        }
        val galaxyBuds = outputs.any {
            (it.type == AudioDeviceInfo.TYPE_BLUETOOTH_A2DP || it.type == AudioDeviceInfo.TYPE_BLE_HEADSET) && it.named("Buds")
        }
        val current = settings.profileId
        when {
            earPods && current != Profiles.EARPODS.id -> settings.profileId = Profiles.EARPODS.id
            !earPods && galaxyBuds && !Profiles.isBuds(current) -> settings.profileId = Profiles.BUDS_HIFI.id
        }
    }

    private fun createChannel() {
        val channel = NotificationChannel(CHANNEL_ID, "Equalizer", NotificationManager.IMPORTANCE_LOW).apply {
            description = "Shows while Sound Tuner is shaping your audio"
            setShowBadge(false)
        }
        getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    private fun goForeground() {
        val notification = buildNotification()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    private fun buildNotification(): Notification {
        val open = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE,
        )
        val stop = PendingIntent.getService(
            this, 1, Intent(this, EqService::class.java).setAction(ACTION_STOP), PendingIntent.FLAG_IMMUTABLE,
        )
        val turnOff = Notification.Action.Builder(
            Icon.createWithResource(this, R.drawable.ic_stat_eq), "Turn off", stop,
        ).build()
        return Notification.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_eq)
            .setContentTitle("Sound Tuner is on")
            .setContentText(Profiles.byId(settings.profileId).name)
            .setContentIntent(open)
            .setOngoing(true)
            .addAction(turnOff)
            .build()
    }
}
