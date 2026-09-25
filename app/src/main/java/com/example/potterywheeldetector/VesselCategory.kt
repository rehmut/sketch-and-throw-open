package com.example.potterywheeldetector

import java.util.Locale

/** What kind of pot a vessel is, used for its illustration and for filtering the library. */
enum class VesselCategory(val label: String) {
    BOWL("bowl"), MUG("mug"), CUP("cup"), VASE("vase"), JAR("jar"), PLATE("plate");

    /** "mug 03" for the third mug, so designs in one category stay apart. */
    fun defaultName(existingInCategory: Int) =
        String.format(Locale.ROOT, "%s %02d", label, existingInCategory + 1)

    companion object {
        /** Height : width below which a form reads as a plate. */
        private const val PLATE_RATIO = 0.25f
        /** Height : width below which an open form reads as a bowl. */
        private const val BOWL_RATIO = 0.75f
        /** Height : width at or above which a handled form is a mug rather than a cup. */
        private const val MUG_RATIO = 0.9f
        /** Height : width above which a closed form is a vase rather than a jar. */
        private const val VASE_RATIO = 1.3f
        /** Mouth narrower than this fraction of the belly makes a closed form. */
        private const val CLOSED_MOUTH = 0.75f
        /**
         * Depth, as a height fraction, at which the mouth is measured. Below the
         * rim itself, which a photo shows as a narrowing ellipse rather than an edge.
         */
        private const val MOUTH_DEPTH = 0.08f

        fun fromName(name: String?): VesselCategory? = entries.firstOrNull { it.name == name }

        /**
         * A first guess from proportions alone; the user can change it when saving.
         * Pass [withHandle] false when the handle is switched off for this vessel.
         */
        fun suggest(profile: VesselProfile, withHandle: Boolean = true): VesselCategory {
            val ratio = profile.height / profile.width.coerceAtLeast(1f)
            if (withHandle && profile.handle != null) return if (ratio >= MUG_RATIO) MUG else CUP
            if (ratio < PLATE_RATIO) return PLATE
            val rows = profile.rows
            val mouthRow = (rows.lastIndex * MOUTH_DEPTH).toInt().coerceIn(0, rows.lastIndex)
            val mouth = rows[mouthRow].let { it.right - it.left }
            val belly = rows.maxOf { it.right - it.left }
            val closed = mouth < belly * CLOSED_MOUTH
            return when {
                !closed && ratio < BOWL_RATIO -> BOWL
                ratio > VASE_RATIO -> VASE
                closed -> JAR
                else -> BOWL
            }
        }
    }
}
