package com.example.potterywheeldetector

import android.view.View
import android.widget.Switch
import android.widget.TextView

/**
 * Clay defaults: wall, base and trimming allowance. Sliders always set the
 * defaults for new vessels; with "apply to open vessel" on they also change the
 * vessel being worked on, through [onApplyToVessel].
 */
internal class SettingsScreen(
    root: View,
    private val store: ClayDefaultsStore,
    private val onApplyToVessel: (ClayDefaults) -> Unit
) {
    private val wall: FaderControl = root.findViewById(R.id.settingsWall)
    private val base: FaderControl = root.findViewById(R.id.settingsBase)
    private val trim: FaderControl = root.findViewById(R.id.settingsTrim)
    private val applyToOpen: Switch = root.findViewById(R.id.applyToOpenSwitch)
    private val applyHint: TextView = root.findViewById(R.id.applyToOpenHint)
    /** Set while values are shown programmatically, so listeners do not echo them back. */
    private var syncing = false
    private var vesselOpen = false

    init {
        wall.formatter = { "${VesselReadout.millimetres(it)} mm" }
        base.formatter = { "${VesselReadout.millimetres(it)} mm" }
        listOf(wall, base, trim).forEach { fader ->
            fader.onValueChange = { _, fromUser -> if (fromUser && !syncing) changed() }
        }
        applyToOpen.setOnCheckedChangeListener { _, checked -> if (checked && !syncing) changed() }
        root.findViewById<View>(R.id.resetDefaultsButton).setOnClickListener {
            show(ClayDefaults())
            changed()
        }
    }

    /**
     * Prepares the screen. Opened from a vessel, it shows that vessel's values
     * with "apply to open vessel" on; otherwise the saved defaults.
     */
    fun open(vesselValues: ClayDefaults?, fromVessel: Boolean) {
        vesselOpen = vesselValues != null
        syncing = true
        applyToOpen.isEnabled = vesselOpen
        applyToOpen.isChecked = fromVessel && vesselOpen
        syncing = false
        show(if (fromVessel && vesselValues != null) vesselValues else store.load())
        applyHint.text = if (vesselOpen) "otherwise only new vessels change" else "no vessel open"
    }

    private fun show(values: ClayDefaults) {
        syncing = true
        wall.value = values.wallMm
        base.value = values.baseMm
        trim.value = values.allowancePercent
        syncing = false
    }

    private fun changed() {
        val values = ClayDefaults(wall.value, base.value, trim.value).clamped()
        store.save(values)
        if (applyToOpen.isChecked && vesselOpen) onApplyToVessel(values)
    }
}
