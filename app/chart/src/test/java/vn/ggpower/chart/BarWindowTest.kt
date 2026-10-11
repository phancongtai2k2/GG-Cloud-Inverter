package vn.ggpower.chart

import org.junit.Assert.assertEquals
import org.junit.Test

class BarWindowTest {
    @Test
    fun notZoomedShowsAll() {
        assertEquals(0 to 12, barWindow(12, null))
        assertEquals(0 to 12, barWindow(12, ZoomWindow()))
    }

    @Test
    fun zoomedShowsRoundedSlice() {
        assertEquals(6 to 6, barWindow(12, ZoomWindow(zoom = 2f, start = 0.5f)))
        assertEquals(9 to 3, barWindow(12, ZoomWindow(zoom = 4f, start = 0.75f)))
        // 30 ngày phóng 8 → 4 cột.
        assertEquals(26 to 4, barWindow(30, ZoomWindow(zoom = 8f, start = 0.875f)))
    }

    @Test
    fun countNeverPassesEnd() {
        assertEquals(11 to 1, barWindow(12, ZoomWindow(zoom = 3f, start = 0.95f)))
    }
}
