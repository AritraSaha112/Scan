package io.github.folioscan.data

import android.content.Context
import io.github.folioscan.core.Quality

class Prefs(context: Context) {
    private val sp = context.getSharedPreferences("settings", Context.MODE_PRIVATE)

    var autoOcr: Boolean
        get() = sp.getBoolean("autoOcr", true)
        set(v) = sp.edit().putBoolean("autoOcr", v).apply()

    /** false = Latin script model, true = Devanagari model (also reads English). */
    var devanagari: Boolean
        get() = sp.getBoolean("devanagari", false)
        set(v) = sp.edit().putBoolean("devanagari", v).apply()

    var quality: Quality
        get() = runCatching { Quality.valueOf(sp.getString("quality", Quality.MEDIUM.name)!!) }
            .getOrDefault(Quality.MEDIUM)
        set(v) = sp.edit().putString("quality", v.name).apply()
}
