package com.example.potterywheeldetector

import android.content.Context

/** Assumptions for parts of a vessel the photo cannot show, used for new vessels. */
data class ClayDefaults(
    val wallMm: Double = 5.0,
    val baseMm: Double = 5.0,
    val allowancePercent: Double = 25.0
) {
    fun clamped() = ClayDefaults(
        wallMm.coerceIn(WALL_RANGE),
        baseMm.coerceIn(BASE_RANGE),
        allowancePercent.coerceIn(ALLOWANCE_RANGE)
    )

    companion object {
        val WALL_RANGE = 2.0..20.0
        val BASE_RANGE = 2.0..30.0
        val ALLOWANCE_RANGE = 0.0..100.0
    }
}

class ClayDefaultsStore(context: Context) {
    private val prefs = context.getSharedPreferences("clay_defaults", Context.MODE_PRIVATE)

    fun load(): ClayDefaults {
        val fallback = ClayDefaults()
        return ClayDefaults(
            prefs.getFloat(KEY_WALL, fallback.wallMm.toFloat()).toDouble(),
            prefs.getFloat(KEY_BASE, fallback.baseMm.toFloat()).toDouble(),
            prefs.getFloat(KEY_ALLOWANCE, fallback.allowancePercent.toFloat()).toDouble()
        ).clamped()
    }

    fun save(defaults: ClayDefaults) {
        val clean = defaults.clamped()
        prefs.edit()
            .putFloat(KEY_WALL, clean.wallMm.toFloat())
            .putFloat(KEY_BASE, clean.baseMm.toFloat())
            .putFloat(KEY_ALLOWANCE, clean.allowancePercent.toFloat())
            .apply()
    }

    private companion object {
        const val KEY_WALL = "wall_mm"
        const val KEY_BASE = "base_mm"
        const val KEY_ALLOWANCE = "allowance_percent"
    }
}
