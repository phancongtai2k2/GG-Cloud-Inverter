package vn.ggpower.chart

import org.junit.Assert.assertEquals
import org.junit.Test

class ZoomTicksTest {
    private fun labels(t0: Double, t1: Double) = zoomTicks(t0, t1, DayMinuteSteps) { hourMinuteLabel(it) }.map { it.label }

    @Test
    fun fullDayUsesSixHourSteps() {
        assertEquals(listOf("0h", "6h", "12h", "18h", "24h"), labels(0.0, 1440.0))
    }

    @Test
    fun zoomedWindowPicksSmallerStep() {
        // Cửa sổ 6 giờ (×4) → bước 2 giờ; 3 giờ (×8) → bước 1 giờ.
        assertEquals(listOf("8h", "10h", "12h", "14h"), labels(480.0, 840.0))
        assertEquals(listOf("9h", "10h", "11h", "12h"), labels(540.0, 720.0))
    }

    @Test
    fun halfHourLabelsArePadded() {
        assertEquals(listOf("09:30", "10h", "10:30", "11h"), labels(560.0, 680.0))
    }

    @Test
    fun emptyForInvalidRange() {
        assertEquals(emptyList<AxisTick>(), zoomTicks(5.0, 5.0, DayMinuteSteps) { "" })
    }
}
