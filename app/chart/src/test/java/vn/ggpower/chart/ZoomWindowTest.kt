package vn.ggpower.chart

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ZoomWindowTest {

    @Test
    fun pinchKeepsPointUnderFingers() {
        val w = ZoomWindow().transformed(factor = 2f, focus = 0.5f, pan = 0f, maxZoom = 4f)
        assertEquals(2f, w.zoom, 1e-6f)
        assertEquals(0.25f, w.start, 1e-6f)
        assertEquals(0.75f, w.end, 1e-6f)
        // The middle of the axis is still under the fingers.
        assertEquals(0.5f, w.global(0.5f), 1e-6f)
    }

    @Test
    fun zoomIsClampedToRange() {
        assertEquals(4f, ZoomWindow().transformed(10f, 0.5f, 0f, maxZoom = 4f).zoom, 1e-6f)
        assertEquals(1f, ZoomWindow(2f, 0.3f).transformed(0.1f, 0.5f, 0f, maxZoom = 4f).zoom, 1e-6f)
        assertEquals(0f, ZoomWindow(2f, 0.3f).transformed(0.1f, 0.5f, 0f, maxZoom = 4f).start, 1e-6f)
    }

    @Test
    fun windowStaysInsideAxis() {
        val right = ZoomWindow(2f, 0.5f).transformed(1f, 0.5f, pan = 1f, maxZoom = 4f)
        assertEquals(0.5f, right.start, 1e-6f)
        val left = ZoomWindow(2f, 0f).transformed(1f, 0.5f, pan = -1f, maxZoom = 4f)
        assertEquals(0f, left.start, 1e-6f)
    }

    @Test
    fun panMovesWindow() {
        val w = ZoomWindow(4f, 0.25f).transformed(1f, 0.5f, pan = 0.5f, maxZoom = 4f)
        assertEquals(0.25f + 0.5f / 4f, w.start, 1e-6f)
    }

    @Test
    fun localAndContains() {
        val w = ZoomWindow(2f, 0.25f)
        assertEquals(0.5f, w.local(0.5f), 1e-6f)
        assertTrue(w.contains(0.75f))
        assertFalse(w.contains(0.8f))
        assertFalse(ZoomWindow().zoomed)
        assertTrue(w.zoomed)
    }

    @Test
    fun windowLengthLabel() {
        assertEquals("30", windowLength(30.0, 1f))
        assertEquals("15", windowLength(30.0, 2f))
        assertEquals("7,5", windowLength(30.0, 4f))
        assertEquals("12", windowLength(30.0, 2.49f))
    }
}
