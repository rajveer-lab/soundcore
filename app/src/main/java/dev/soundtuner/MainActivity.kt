package dev.soundtuner

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.media.audiofx.AudioEffect
import android.os.Build
import android.os.Bundle
import android.text.SpannableString
import android.text.Spanned
import android.text.style.ForegroundColorSpan
import android.text.style.RelativeSizeSpan
import android.view.View
import android.widget.Button
import android.widget.RadioButton
import android.widget.RadioGroup
import android.widget.SeekBar
import android.widget.Switch
import android.widget.TextView
import java.util.Locale

class MainActivity : Activity() {
    private lateinit var settings: Settings
    private lateinit var power: Switch
    private lateinit var status: TextView
    private lateinit var curve: CurveView
    private lateinit var profileGroup: RadioGroup
    private lateinit var autoSwitch: Switch
    private val profileButtonIds = mutableMapOf<String, Int>()
    private val sliders = mutableListOf<Slider>()

    /** True while the UI is being filled from settings, so listeners don't write back. */
    private var refreshing = false

    private val prefListener = SharedPreferences.OnSharedPreferenceChangeListener { _, _ ->
        runOnUiThread { refresh() }
    }

    private inner class Slider(
        val bar: SeekBar, val label: TextView, val title: String,
        val min: Float, val max: Float,
        val read: () -> Float, val write: (Float) -> Unit,
    ) {
        init {
            bar.max = ((max - min) * 2).toInt() // 0.5 dB steps
            bar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(seekBar: SeekBar, progress: Int, fromUser: Boolean) {
                    val value = min + progress / 2f
                    showLabel(value)
                    if (!refreshing && fromUser) write(value)
                }
                override fun onStartTrackingTouch(seekBar: SeekBar) {}
                override fun onStopTrackingTouch(seekBar: SeekBar) {}
            })
        }

        fun load() {
            val value = read()
            bar.progress = ((value - min) * 2).toInt()
            showLabel(value)
        }

        private fun showLabel(value: Float) {
            label.text = String.format(Locale.US, "%s   %+.1f dB", title, value)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        settings = Settings(this)

        power = findViewById(R.id.power)
        status = findViewById(R.id.status)
        curve = findViewById(R.id.curve)
        profileGroup = findViewById(R.id.profiles)
        autoSwitch = findViewById(R.id.autoSwitch)

        buildProfileList()

        sliders += Slider(findViewById(R.id.bass), findViewById(R.id.bassLabel), "Bass",
            -6f, 9f, { settings.bass }, { settings.bass = it })
        sliders += Slider(findViewById(R.id.clarity), findViewById(R.id.clarityLabel), "Clarity",
            -4f, 4f, { settings.clarity }, { settings.clarity = it })
        sliders += Slider(findViewById(R.id.treble), findViewById(R.id.trebleLabel), "Treble",
            -6f, 6f, { settings.treble }, { settings.treble = it })

        power.setOnCheckedChangeListener { _, on -> if (!refreshing) setEqEnabled(on) }
        autoSwitch.setOnCheckedChangeListener { _, on -> if (!refreshing) settings.autoSwitch = on }
        findViewById<Button>(R.id.reset).setOnClickListener {
            settings.bass = 0f; settings.clarity = 0f; settings.treble = 0f
        }

        refresh()
        handleEqualizerIntent(intent)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleEqualizerIntent(intent)
    }

    override fun onResume() {
        super.onResume()
        settings.prefs.registerOnSharedPreferenceChangeListener(prefListener)
        // If the EQ should be on but its service was stopped (restart, update, killed), bring it back
        if (settings.enabled) {
            runCatching {
                startForegroundService(Intent(this, EqService::class.java).setAction(EqService.ACTION_APPLY))
            }
        }
        refresh()
    }

    override fun onPause() {
        settings.prefs.unregisterOnSharedPreferenceChangeListener(prefListener)
        super.onPause()
    }

    /** Opened from a music app's "Equalizer" button: hook straight into that app's audio. */
    private fun handleEqualizerIntent(intent: Intent?) {
        if (intent?.action != AudioEffect.ACTION_DISPLAY_AUDIO_EFFECT_CONTROL_PANEL) return
        val session = intent.getIntExtra(AudioEffect.EXTRA_AUDIO_SESSION, AudioEffect.ERROR_BAD_VALUE)
        if (session > 0 && settings.enabled) {
            startForegroundService(
                Intent(this, EqService::class.java)
                    .setAction(EqService.ACTION_ATTACH)
                    .putExtra(AudioEffect.EXTRA_AUDIO_SESSION, session)
            )
        }
    }

    private fun setEqEnabled(on: Boolean) {
        settings.enabled = on
        val service = Intent(this, EqService::class.java)
        if (on) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
            ) {
                requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 1)
            }
            startForegroundService(service.setAction(EqService.ACTION_APPLY))
        } else {
            stopService(service)
        }
    }

    private fun buildProfileList() {
        val muted = getColor(R.color.muted)
        for (profile in Profiles.ALL) {
            val text = SpannableString("${profile.name}\n${profile.description}").apply {
                val start = profile.name.length + 1
                setSpan(RelativeSizeSpan(0.82f), start, length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                setSpan(ForegroundColorSpan(muted), start, length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            }
            val button = RadioButton(this).apply {
                id = View.generateViewId()
                this.text = text
                setTextColor(getColor(R.color.text))
                textSize = 16f
                buttonTintList = getColorStateList(R.color.accent)
                val pad = (10 * resources.displayMetrics.density).toInt()
                setPadding(pad, pad, pad, pad)
            }
            profileButtonIds[profile.id] = button.id
            profileGroup.addView(button)
        }
        profileGroup.setOnCheckedChangeListener { _, checkedId ->
            if (refreshing) return@setOnCheckedChangeListener
            profileButtonIds.entries.firstOrNull { it.value == checkedId }?.let { settings.profileId = it.key }
        }
    }

    private fun refresh() {
        refreshing = true
        power.isChecked = settings.enabled
        profileButtonIds[settings.profileId]?.let { profileGroup.check(it) }
        sliders.forEach { it.load() }
        autoSwitch.isChecked = settings.autoSwitch
        curve.setFilters(settings.filters())
        val profileName = Profiles.byId(settings.profileId).name
        status.text = when {
            !settings.enabled -> "Off · tap the switch to turn on"
            settings.globalMode -> "On · all apps · $profileName"
            else -> "On · music apps that support equalizers · $profileName"
        }
        refreshing = false
    }
}
