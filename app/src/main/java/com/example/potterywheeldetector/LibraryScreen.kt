package com.example.potterywheeldetector

import android.app.Activity
import android.app.AlertDialog
import android.graphics.Typeface
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.HorizontalScrollView
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView

/** Builds the vessel library and the home screen's recent row from [VesselStore]. */
class LibraryScreen(
    private val activity: Activity,
    private val store: VesselStore,
    private val content: LinearLayout,
    private val recentRow: LinearLayout,
    private val recentEmpty: View,
    private val onOpen: (SavedVessel) -> Unit,
    private val onDeleted: (id: String) -> Unit,
    private val onStatus: (String) -> Unit
) {
    private val density = activity.resources.displayMetrics.density
    private val Int.dp: Int get() = (this * density).toInt()
    /** Category shown on its own, or null for every category. */
    private var filterCategory: VesselCategory? = null

    fun render() {
        content.removeAllViews()
        val used = store.recent(Int.MAX_VALUE).map { it.category }.toSet()
        if (filterCategory !in used) filterCategory = null
        if (used.size > 1) content.addView(categoryFilter(VesselCategory.entries.filter { it in used }))
        var shown = 0
        store.folders().forEach { folder ->
            val saved = store.vessels(folder.id)
                .filter { filterCategory == null || it.category == filterCategory }
            if (saved.isEmpty() && filterCategory != null) return@forEach
            if (shown++ > 0) content.addView(rule())
            content.addView(folderHeading(folder))
            if (saved.isEmpty()) content.addView(label("no vessels yet").apply {
                setPadding(0, 2.dp, 0, 10.dp)
            })
            saved.forEach { content.addView(vesselRow(it)) }
        }
    }

    fun renderRecent() {
        recentRow.removeAllViews()
        val recent = store.recent(3)
        recentEmpty.visibility = if (recent.isEmpty()) View.VISIBLE else View.GONE
        recentRow.visibility = if (recent.isEmpty()) View.GONE else View.VISIBLE
        recent.forEachIndexed { index, entry ->
            val key = LinearLayout(activity).apply {
                orientation = LinearLayout.VERTICAL
                background = activity.getDrawable(R.drawable.bg_clickable_row)
                setPadding(10.dp, 10.dp, 10.dp, 10.dp)
                isClickable = true
                isFocusable = true
                contentDescription = "Open ${entry.name}"
                setOnClickListener { onOpen(entry) }
                addView(thumbnail(entry), LinearLayout.LayoutParams(40.dp, 40.dp))
                addView(TextView(activity).apply {
                    text = entry.name
                    textSize = 12f
                    setTextColor(activity.getColor(R.color.graphite))
                    maxLines = 2
                    setPadding(0, 6.dp, 0, 0)
                })
            }
            recentRow.addView(key, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f)
                .apply { if (index > 0) leftMargin = 8.dp })
        }
        // Keep three equal slots so one or two vessels do not stretch across the row.
        repeat(3 - recent.size) {
            if (recent.isNotEmpty()) recentRow.addView(View(activity),
                LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f)
                    .apply { leftMargin = 8.dp })
        }
    }

    fun createFolderDialog() {
        val input = EditText(activity).apply { hint = "folder name, e.g. bowls" }
        AlertDialog.Builder(activity).setTitle("New folder").setView(padded(input))
            .setNegativeButton("Cancel", null)
            .setPositiveButton("Create") { _, _ ->
                try {
                    store.createFolder(input.text.toString())
                    render()
                    onStatus("Folder created.")
                } catch (error: IllegalArgumentException) {
                    onStatus("Choose a folder name that is not empty and not already used.")
                }
            }.show()
    }

    private fun categoryFilter(categories: List<VesselCategory>): View {
        val rocker = LinearLayout(activity).apply {
            orientation = LinearLayout.HORIZONTAL
            background = activity.getDrawable(R.drawable.bg_rocker)
            setPadding(3.dp, 3.dp, 3.dp, 3.dp)
        }
        (listOf<VesselCategory?>(null) + categories).forEachIndexed { index, category ->
            rocker.addView(Button(activity, null, 0, R.style.RockerItem).apply {
                text = category?.label ?: "all"
                isSelected = category == filterCategory
                setPadding(16.dp, 0, 16.dp, 0)
                setOnClickListener {
                    filterCategory = category
                    render()
                }
            }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, 44.dp)
                .apply { if (index > 0) leftMargin = 3.dp })
        }
        return HorizontalScrollView(activity).apply {
            isHorizontalScrollBarEnabled = false
            addView(rocker)
            setPadding(0, 12.dp, 0, 4.dp)
        }
    }

    private fun folderHeading(folder: VesselFolder) = LinearLayout(activity).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        setPadding(0, 10.dp, 0, 2.dp)
        addView(TextView(activity).apply {
            text = folder.name.lowercase()
            textSize = 16f
            typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
            setTextColor(activity.getColor(R.color.graphite))
        })
        addView(label(String.format("%02d", store.vessels(folder.id).size)).apply {
            typeface = Typeface.MONOSPACE
            setPadding(8.dp, 0, 0, 0)
        }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        addView(ImageButton(activity, null, 0, R.style.IconButton).apply {
            setImageResource(R.drawable.ic_more)
            contentDescription = "Folder options for ${folder.name}"
            setOnClickListener { editFolderDialog(folder) }
        }, LinearLayout.LayoutParams(48.dp, 48.dp))
    }

    private fun vesselRow(entry: SavedVessel): View {
        val measured = entry.heightCm?.let {
            VesselMeasurementCalculator.calculate(entry.profile, it, entry.wallMm,
                entry.allowancePercent, entry.baseMm, entry.handleOptions)
        }
        val dimensions = measured?.let {
            String.format("Ø %.1f · h %.1f cm", it.widestDiameterCm, it.heightCm)
        } ?: "height not set"
        return LinearLayout(activity).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            background = activity.getDrawable(R.drawable.bg_clickable_row)
            minimumHeight = 64.dp
            setPadding(8.dp, 8.dp, 0, 10.dp)
            isClickable = true
            isFocusable = true
            setOnClickListener { onOpen(entry) }
            addView(thumbnail(entry), LinearLayout.LayoutParams(48.dp, 48.dp))
            addView(LinearLayout(activity).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(12.dp, 0, 8.dp, 0)
                addView(TextView(activity).apply {
                    text = entry.name
                    textSize = 14f
                    typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
                    setTextColor(activity.getColor(R.color.graphite))
                })
                addView(label("${entry.category.label} · $dimensions").apply { typeface = Typeface.MONOSPACE })
            }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
            addView(TextView(activity).apply {
                text = measured?.let { VesselReadout.grams(it.estimatedClayGrams) } ?: "—"
                typeface = Typeface.MONOSPACE
                textSize = 13f
                setTextColor(activity.getColor(R.color.graphite))
            })
            addView(ImageButton(activity, null, 0, R.style.IconButton).apply {
                setImageResource(R.drawable.ic_more)
                contentDescription = "Options for ${entry.name}"
                setOnClickListener { confirmDelete(entry) }
            }, LinearLayout.LayoutParams(48.dp, 48.dp))
        }.also { row ->
            row.layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT).apply { topMargin = 8.dp }
        }
    }

    private fun confirmDelete(entry: SavedVessel) {
        AlertDialog.Builder(activity).setTitle(entry.name)
            .setItems(arrayOf("Open", "Delete")) { _, choice ->
                if (choice == 0) onOpen(entry) else AlertDialog.Builder(activity)
                    .setTitle("Delete ${entry.name}?")
                    .setMessage("This removes the saved photo and vessel profile from the app.")
                    .setNegativeButton("Cancel", null)
                    .setPositiveButton("Delete") { _, _ ->
                        store.delete(entry.id)
                        onDeleted(entry.id)
                        render()
                        renderRecent()
                    }.show()
            }.show()
    }

    private fun editFolderDialog(folder: VesselFolder) {
        AlertDialog.Builder(activity).setTitle(folder.name)
            .setItems(arrayOf("Rename folder", "Delete empty folder")) { _, choice ->
                if (choice == 0) renameFolderDialog(folder) else deleteFolder(folder)
            }.show()
    }

    private fun renameFolderDialog(folder: VesselFolder) {
        val input = EditText(activity).apply { setText(folder.name); selectAll() }
        AlertDialog.Builder(activity).setTitle("Rename folder").setView(padded(input))
            .setNegativeButton("Cancel", null)
            .setPositiveButton("Rename") { _, _ ->
                try {
                    store.renameFolder(folder.id, input.text.toString())
                    render()
                } catch (error: IllegalArgumentException) {
                    onStatus("Choose a name that is not empty and not already used.")
                }
            }.show()
    }

    private fun deleteFolder(folder: VesselFolder) {
        if (store.vessels(folder.id).isNotEmpty()) {
            onStatus("Move or delete this folder's vessels before deleting it.")
            return
        }
        AlertDialog.Builder(activity).setTitle("Delete ${folder.name}?")
            .setNegativeButton("Cancel", null)
            .setPositiveButton("Delete") { _, _ ->
                try {
                    store.deleteFolder(folder.id)
                    render()
                } catch (error: IllegalArgumentException) {
                    onStatus("Keep at least one folder in the library.")
                }
            }.show()
    }

    /** The category's line illustration; the photo stays stored for retracing. */
    private fun thumbnail(entry: SavedVessel) = ImageView(activity).apply {
        setImageResource(entry.category.illustration)
        scaleType = ImageView.ScaleType.FIT_CENTER
        background = activity.getDrawable(R.drawable.bg_thumb)
        setPadding(4.dp, 4.dp, 4.dp, 4.dp)
        importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
    }

    private fun label(text: String) = TextView(activity).apply {
        this.text = text
        textSize = 12f
        letterSpacing = 0.05f
        setTextColor(activity.getColor(R.color.label))
    }

    private fun rule() = View(activity).apply {
        setBackgroundColor(activity.getColor(R.color.rule))
        layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 1.dp)
            .apply { topMargin = 14.dp }
    }

    private fun padded(view: View) = LinearLayout(activity).apply {
        setPadding(20.dp, 8.dp, 20.dp, 0)
        addView(view, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT))
    }
}
