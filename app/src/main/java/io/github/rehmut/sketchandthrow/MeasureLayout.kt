package io.github.rehmut.sketchandthrow

import android.transition.AutoTransition
import android.transition.TransitionManager
import android.view.View
import android.view.ViewGroup
import android.view.animation.DecelerateInterpolator
import android.widget.LinearLayout

/**
 * The top of the measure step. Before a height is known the drawing sits small
 * beside the height box; once it is known the drawing grows to the full width
 * and the box slides underneath it.
 */
internal class MeasureLayout(
    private val scene: ViewGroup,
    private val row: LinearLayout,
    private val drawing: ProfileView,
    private val heightBlock: View
) {
    private val density = drawing.resources.displayMetrics.density
    private val Int.dp: Int get() = (this * density).toInt()

    var expanded = false
        private set

    fun setExpanded(expand: Boolean, animate: Boolean = true) {
        if (expand == expanded) return
        expanded = expand
        if (animate) {
            TransitionManager.beginDelayedTransition(scene, AutoTransition().apply {
                duration = TRANSITION_MS
                interpolator = DecelerateInterpolator()
            })
        }
        if (expand) {
            row.orientation = LinearLayout.VERTICAL
            drawing.layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, EXPANDED_HEIGHT_DP.dp)
            heightBlock.layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
                .apply { topMargin = 12.dp }
        } else {
            row.orientation = LinearLayout.HORIZONTAL
            drawing.layoutParams = LinearLayout.LayoutParams(COMPACT_WIDTH_DP.dp, COMPACT_HEIGHT_DP.dp)
            heightBlock.layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
                .apply { leftMargin = 12.dp }
        }
        drawing.showValues = expand
    }

    private companion object {
        const val TRANSITION_MS = 420L
        const val COMPACT_WIDTH_DP = 150
        const val COMPACT_HEIGHT_DP = 190
        const val EXPANDED_HEIGHT_DP = 380
    }
}
