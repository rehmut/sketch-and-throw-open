package io.github.rehmut.sketchandthrow

/**
 * The profile as the pot is, not as the photo foreshortens it. Where the rim or
 * foot shows as an ellipse or a lean, those rows are widened to the measured
 * diameter, centred on the turning axis. The 3D model, the clay estimate and the
 * technical drawing all use this one outline, so they agree with each other and
 * with the rim and foot readings.
 *
 * Rows are only ever widened, so a real flare keeps its shape, and a bevel,
 * which is real geometry, is left alone.
 */
object ProfileEnds {
    fun level(profile: VesselProfile): VesselProfile {
        val rows = profile.rows
        if (rows.size < 3) return profile
        val rim = EndDiameter.rimReading(rows)
        val foot = EndDiameter.footReading(rows)
        if (!rim.perspective && !foot.perspective) return profile
        val axis = profile.axisX
        val footStart = rows.lastIndex - foot.rowsIn
        val levelled = rows.mapIndexed { index, row ->
            when {
                rim.perspective && index < rim.rowsIn -> widen(row, axis, rim.width)
                foot.perspective && index > footStart -> widen(row, axis, foot.width)
                else -> row
            }
        }
        return profile.copy(rows = levelled)
    }

    private fun widen(row: ProfileRow, axis: Float, width: Float) =
        row.copy(left = minOf(row.left, axis - width / 2f), right = maxOf(row.right, axis + width / 2f))
}
