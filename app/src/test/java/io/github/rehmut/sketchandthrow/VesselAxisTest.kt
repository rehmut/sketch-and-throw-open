package io.github.rehmut.sketchandthrow

import org.junit.Assert.assertEquals
import org.junit.Test

class VesselAxisTest {
    @Test fun axisFollowsTheBodyNotAnOffCentreRimTip() {
        // Body centred on x = 100; the top five rows are a tilted rim whose highest
        // point sits well to the right, as in a slightly tilted photo.
        val rows = (0..200).map { y ->
            if (y < 5) ProfileRow(y.toFloat(), 130f, 170f) else ProfileRow(y.toFloat(), 50f, 150f)
        }
        val profile = VesselProfile(300, 300, rows)
        assertEquals(100f, profile.axisX, 0.01f)
        // The bounding box would put it at 110.
        assertEquals(110f, (profile.left + profile.right) / 2f, 0.01f)
    }
}
