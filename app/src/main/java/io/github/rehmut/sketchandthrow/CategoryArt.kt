package io.github.rehmut.sketchandthrow

import androidx.annotation.DrawableRes

/** The line illustration drawn for each category. */
@get:DrawableRes
val VesselCategory.illustration: Int
    get() = when (this) {
        VesselCategory.BOWL -> R.drawable.ill_bowl
        VesselCategory.MUG -> R.drawable.ill_mug
        VesselCategory.CUP -> R.drawable.ill_cup
        VesselCategory.VASE -> R.drawable.ill_vase
        VesselCategory.JAR -> R.drawable.ill_jar
        VesselCategory.PLATE -> R.drawable.ill_plate
    }
