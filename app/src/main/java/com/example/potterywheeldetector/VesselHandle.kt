package com.example.potterywheeldetector

import kotlin.math.PI
import kotlin.math.hypot

enum class HandleSide { LEFT, RIGHT }

/** A point on the handle's midline in image pixels, with the thickness seen there. */
data class HandlePoint(val x: Float, val y: Float, val thickness: Float)

/**
 * A handle traced from the side-on silhouette. The path runs from the upper join
 * to the lower join along the middle of the handle. Only the thickness in the
 * photo plane is measured; the front-to-back width is hidden and comes from
 * [HandleOptions.widthMm].
 */
data class VesselHandle(
    val side: HandleSide,
    val path: List<HandlePoint>,
    val upperJoinY: Float,
    val lowerJoinY: Float,
    /** Furthest reach beyond the body edge, in pixels. */
    val projection: Float,
    /** True when background shows between handle and body, as with a pulled strap. */
    val hasEye: Boolean
) {
    val topY: Float get() = path.minOf { it.y - it.thickness / 2f }
    val bottomY: Float get() = path.maxOf { it.y + it.thickness / 2f }

    /** Midline length in pixels. */
    val length: Float get() = path.zipWithNext { a, b -> hypot(b.x - a.x, b.y - a.y) }.sum()

    /** Median seen thickness, robust to the thin tips at each join. */
    val typicalThickness: Float get() = path.map { it.thickness }.sorted()[path.size / 2]
}

enum class HandleSection(val label: String) {
    ROUND("round"), OVAL("oval"), STRAP("pulled strap");

    /**
     * Cross-section area for a seen thickness [thickness] and an assumed
     * front-to-back [width]. A round handle ignores the width: both are the same.
     * A pulled strap is close to a rectangle with softened edges.
     */
    fun area(thickness: Double, width: Double): Double = when (this) {
        ROUND -> PI / 4.0 * thickness * thickness
        OVAL -> PI / 4.0 * thickness * width
        STRAP -> 0.9 * thickness * width
    }

    /** Front-to-back width to model, in the same unit as [thickness]. */
    fun depth(thickness: Double, width: Double): Double =
        if (this == ROUND) thickness else width
}

data class HandleOptions(
    val enabled: Boolean = true,
    val section: HandleSection = HandleSection.OVAL,
    /** Assumed front-to-back width in mm; a photo taken side-on cannot show it. */
    val widthMm: Double = DEFAULT_WIDTH_MM
) {
    companion object {
        const val DEFAULT_WIDTH_MM = 20.0
        val WIDTH_RANGE = 4.0..60.0
    }
}
