package com.example.potterywheeldetector

import android.app.Activity
import android.app.AlertDialog
import android.view.Gravity
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.Spinner
import android.widget.TextView

/**
 * Name, category and folder for a vessel. The category starts at [suggested]
 * (or the saved one) and is picked from illustrated keys. A new vessel gets a
 * numbered name for its category ("mug 03") that follows the picked category
 * until the user types their own. A saved vessel can also be deleted here.
 */
internal class SaveVesselDialog(
    private val activity: Activity,
    private val folders: List<VesselFolder>,
    private val previous: SavedVessel?,
    /** A name already given this session, kept instead of a numbered one. */
    private val givenName: String?,
    private val suggested: VesselCategory,
    private val numberedName: (VesselCategory) -> String,
    private val onDelete: (() -> Unit)?,
    private val onSave: (name: String, category: VesselCategory, folder: VesselFolder) -> Unit
) {
    private val density = activity.resources.displayMetrics.density
    private val Int.dp: Int get() = (this * density).toInt()
    private var category = previous?.category ?: suggested
    private val categoryKeys = mutableMapOf<VesselCategory, Button>()
    private var nameInput: EditText? = null
    /** The numbered name last filled in; replaced on a category change unless edited. */
    private var autoName: String? = null

    fun show() {
        val form = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(20.dp, 8.dp, 20.dp, 0)
        }
        val fixedName = previous?.name ?: givenName
        autoName = if (fixedName == null) numberedName(category) else null
        val nameInput = EditText(activity).apply {
            hint = "vessel name"
            setText(fixedName ?: autoName)
            selectAll()
        }
        this.nameInput = nameInput
        val folderSpinner = Spinner(activity).apply {
            adapter = ArrayAdapter(activity, android.R.layout.simple_spinner_dropdown_item,
                folders.map { it.name })
            val index = folders.indexOfFirst { it.id == previous?.folderId }
            if (index >= 0) setSelection(index)
        }
        form.addView(nameInput)
        form.addView(label("category"))
        form.addView(categoryPicker())
        form.addView(label("folder"))
        form.addView(folderSpinner)
        val builder = AlertDialog.Builder(activity)
            .setTitle(if (previous == null) "Save vessel" else "Update vessel")
            .setView(form).setNegativeButton("Cancel", null)
            .setPositiveButton("Save") { _, _ ->
                onSave(nameInput.text.toString(), category, folders[folderSpinner.selectedItemPosition])
            }
        onDelete?.let { delete -> builder.setNeutralButton("Delete") { _, _ -> delete() } }
        builder.show()
    }

    /** All six categories at once, three to a row, so none hides off screen. */
    private fun categoryPicker(): LinearLayout {
        val grid = LinearLayout(activity).apply { orientation = LinearLayout.VERTICAL }
        VesselCategory.entries.chunked(CATEGORIES_PER_ROW).forEachIndexed { rowIndex, options ->
            val row = LinearLayout(activity).apply { orientation = LinearLayout.HORIZONTAL }
            options.forEachIndexed { index, option ->
                val key = Button(activity, null, 0, R.style.Key).apply {
                    background = activity.getDrawable(R.drawable.bg_key_choice)
                    text = option.label
                    textSize = 12f
                    minHeight = 0
                    minimumHeight = 0
                    setCompoundDrawablesWithIntrinsicBounds(0, option.illustration, 0, 0)
                    // Illustrations carry their own colours; the Key style would tint them grey.
                    compoundDrawableTintList = null
                    compoundDrawablePadding = 2.dp
                    setPadding(4.dp, 8.dp, 4.dp, 10.dp)
                    setOnClickListener { select(option) }
                }
                categoryKeys[option] = key
                row.addView(key, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
                    .apply { if (index > 0) leftMargin = 6.dp })
            }
            grid.addView(row, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT).apply { if (rowIndex > 0) topMargin = 6.dp })
        }
        select(category)
        return grid
    }

    private fun select(option: VesselCategory) {
        category = option
        nameInput?.let { input ->
            if (autoName != null && input.text.toString() == autoName) {
                autoName = numberedName(option)
                input.setText(autoName)
                input.selectAll()
            }
        }
        categoryKeys.forEach { (key, button) ->
            button.isSelected = key == option
            button.contentDescription = if (key == option) "${key.label}, selected" else key.label
        }
    }

    private companion object {
        const val CATEGORIES_PER_ROW = 3
    }

    private fun label(text: String) = TextView(activity).apply {
        this.text = text
        textSize = 12f
        letterSpacing = 0.05f
        gravity = Gravity.START
        setTextColor(activity.getColor(R.color.label))
        setPadding(4.dp, 14.dp, 0, 6.dp)
    }
}
