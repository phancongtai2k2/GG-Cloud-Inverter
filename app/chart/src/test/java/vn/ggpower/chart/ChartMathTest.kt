package vn.ggpower.chart

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ChartMathTest {

    @Test fun `thang tuyen tinh dao chieu cho truc y`() {
        // Công suất: -2…5 kW → y 150…10 (như prototype: 0 kW ở y = 110)
        val s = LinearScale(-2.0, 5.0, 150f, 10f)
        assertEquals(110f, s.map(0.0), 1e-4f)
        assertEquals(30f, s.map(4.0), 1e-4f)
        assertEquals(4.0, s.invert(30f), 1e-6)
    }

    @Test fun `diem gan nhat`() {
        val xs = listOf(0.0, 15.0, 30.0, 45.0, 581.0)
        assertEquals(0, nearestIndex(xs, -10.0))
        assertEquals(1, nearestIndex(xs, 20.0))
        assertEquals(1, nearestIndex(xs, 22.5))       // cách đều → lấy điểm trước
        assertEquals(3, nearestIndex(xs, 300.0))
        assertEquals(4, nearestIndex(xs, 400.0))
        assertEquals(4, nearestIndex(xs, 2000.0))
        assertEquals(-1, nearestIndex(emptyList(), 1.0))
    }

    @Test fun `catmull rom di qua cac diem va bi kep`() {
        val pts = listOf(0f to 150f, 10f to 0f, 20f to 150f)
        val c = catmullRom(pts, clampMin = 0f, clampMax = 150f)
        assertEquals(2, c.size)
        assertEquals(0f, c[0].x0); assertEquals(10f, c[0].x1); assertEquals(0f, c[0].y1)
        assertEquals(20f, c[1].x1)
        c.forEach { assertTrue(it.c1y in 0f..150f && it.c2y in 0f..150f) }
        assertTrue(catmullRom(listOf(1f to 1f), 0f, 1f).isEmpty())
    }
}
