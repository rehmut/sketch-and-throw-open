package io.github.rehmut.sketchandthrow

import android.view.View
import android.widget.Button
import android.widget.Switch
import android.widget.TextView

/**
 * The controls of step 02 and the "find a handle" switch of step 01: detection
 * on or off, cross-section and assumed width. Changes go out through
 * [onDetection] and [onOptions]; [render] shows the current state.
 */
internal class HandleStepPanel(
    root: View,
    private val onDetection: (Boolean) -> Unit,
    private val onOptions: (HandleOptions) -> Unit
) {
    private val context = root.context
    private val switches: List<Switch> = listOf(root.findViewById(R.id.handleSwitch),
        root.findViewById(R.id.detectHandleSwitch))
    private val noHandleCard: View = root.findViewById(R.id.noHandleCard)
    private val noHandleTitle: TextView = root.findViewById(R.id.noHandleTitle)
    private val noHandleText: TextView = root.findViewById(R.id.noHandleText)
    private val controls: View = root.findViewById(R.id.handleControls)
    private val sideText: TextView = root.findViewById(R.id.handleSideText)
    private val widthFader: FaderControl = root.findViewById(R.id.handleWidthFader)
    private val sectionButtons: Map<HandleSection, Button> = mapOf(
        HandleSection.ROUND to root.findViewById(R.id.sectionRound),
        HandleSection.OVAL to root.findViewById(R.id.sectionOval),
        HandleSection.STRAP to root.findViewById(R.id.sectionStrap)
    )
    private var options = HandleOptions()
    /** Set while [render] moves controls, so their listeners do not echo. */
    private var syncing = false

    init {
        switches.forEach { toggle ->
            toggle.setOnCheckedChangeListener { _, checked -> if (!syncing) onDetection(checked) }
        }
        sectionButtons.forEach { (section, button) ->
            button.setOnClickListener { onOptions(options.copy(section = section)) }
        }
        widthFader.onValueChange = { value, fromUser ->
            if (fromUser && !syncing) onOptions(options.copy(widthMm = value))
        }
    }

    fun render(handle: VesselHandle?, current: HandleOptions) {
        options = current
        syncing = true
        switches.forEach { it.isChecked = current.enabled }
        widthFader.value = current.widthMm
        syncing = false
        noHandleCard.visibility = if (handle == null) View.VISIBLE else View.GONE
        controls.visibility = if (handle == null) View.GONE else View.VISIBLE
        if (handle == null) {
            sideText.text = if (current.enabled) "on · none found yet" else "off"
            noHandleTitle.text = if (current.enabled) "no handle found" else "handle detection is off"
            noHandleText.text = if (current.enabled)
                "photograph the vessel side-on so the handle sticks out to the left or right. " +
                    "if it is missed, tap the handle in the photo in 01 outline."
            else "switch it on for mugs and cups. bowls, vases and jars are modelled from the body alone."
            return
        }
        sideText.text = "traced on the ${handle.side.name.lowercase()} · " +
            if (handle.hasEye) "open handle" else "solid lug"
        sectionButtons.forEach { (section, button) ->
            button.isSelected = section == current.section
            button.setTextColor(context.getColor(if (button.isSelected) R.color.key else R.color.graphite_soft))
            button.compoundDrawableTintList = button.textColors
        }
        widthFader.isEnabled = current.section != HandleSection.ROUND
        widthFader.setLabel(if (current.section == HandleSection.ROUND)
            "width · same as thickness for round" else "width front to back · assumed")
    }
}
