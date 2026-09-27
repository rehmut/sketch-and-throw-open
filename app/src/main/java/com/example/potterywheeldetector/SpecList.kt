package com.example.potterywheeldetector

import android.graphics.Typeface
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView

/** Fills [list] with lettered dimension rows, a hairline above each. */
internal fun showSpecRows(list: LinearLayout, rows: List<VesselReadout.SpecRow>) {
    val context = list.context
    val density = context.resources.displayMetrics.density
    list.removeAllViews()
    rows.forEach { row ->
        list.addView(View(context).apply { setBackgroundColor(context.getColor(R.color.rule)) },
            LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 1))
        list.addView(LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(0, (9 * density).toInt(), 0, (9 * density).toInt())
            contentDescription = "${row.letter}, ${row.label}: ${row.value}"
            addView(TextView(context).apply {
                text = row.letter
                typeface = Typeface.MONOSPACE
                textSize = 11f
                setTextColor(context.getColor(R.color.label))
            }, LinearLayout.LayoutParams((24 * density).toInt(), LinearLayout.LayoutParams.WRAP_CONTENT))
            addView(TextView(context).apply {
                text = row.label
                textSize = 13f
                setTextColor(context.getColor(R.color.graphite))
            }, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
            addView(TextView(context).apply {
                text = row.value
                typeface = Typeface.MONOSPACE
                textSize = 13f
                setTextColor(context.getColor(R.color.graphite))
            })
        })
    }
}
