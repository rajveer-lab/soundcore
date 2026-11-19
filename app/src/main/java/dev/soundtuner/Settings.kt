package dev.soundtuner

import android.content.Context
import android.content.SharedPreferences

/** Everything the user picks, saved on the phone. The service watches this and re-applies on change. */
class Settings(context: Context) {
    val prefs: SharedPreferences =
        context.applicationContext.getSharedPreferences("soundtuner", Context.MODE_PRIVATE)

    var enabled: Boolean
        get() = prefs.getBoolean("enabled", false)
        set(v) = prefs.edit().putBoolean("enabled", v).apply()

    var profileId: String
        get() = prefs.getString("profile", Profiles.EARPODS.id) ?: Profiles.EARPODS.id
        set(v) = prefs.edit().putString("profile", v).apply()

    var bass: Float
        get() = prefs.getFloat("bass", 0f)
        set(v) = prefs.edit().putFloat("bass", v).apply()

    var clarity: Float
        get() = prefs.getFloat("clarity", 0f)
        set(v) = prefs.edit().putFloat("clarity", v).apply()

    var treble: Float
        get() = prefs.getFloat("treble", 0f)
        set(v) = prefs.edit().putFloat("treble", v).apply()

    var autoSwitch: Boolean
        get() = prefs.getBoolean("auto_switch", true)
        set(v) = prefs.edit().putBoolean("auto_switch", v).apply()

    /** Written by the service: true when the EQ is hooked into the phone's whole audio output. */
    var globalMode: Boolean
        get() = prefs.getBoolean("global_mode", false)
        set(v) = prefs.edit().putBoolean("global_mode", v).apply()

    /** The selected profile plus the Bass / Clarity / Treble sliders. */
    fun filters(): List<Filter> = Profiles.byId(profileId).filters + listOfNotNull(
        bass.takeIf { it != 0f }?.let { Filter(FilterType.LOW_SHELF, 105.0, it.toDouble(), 0.70) },
        clarity.takeIf { it != 0f }?.let { Filter(FilterType.PEAK, 3000.0, it.toDouble(), 1.0) },
        treble.takeIf { it != 0f }?.let { Filter(FilterType.HIGH_SHELF, 8000.0, it.toDouble(), 0.70) },
    )
}
