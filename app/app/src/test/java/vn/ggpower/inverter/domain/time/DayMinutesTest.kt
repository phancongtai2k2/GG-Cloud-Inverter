package vn.ggpower.inverter.domain.time

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalTime

class DayMinutesTest {

    private fun t(h: Int, m: Int = 0) = LocalTime.of(h, m)

    @Test fun `phut trong ngay`() {
        assertEquals(0, t(0).minuteOfDay())
        assertEquals(390, t(6, 30).minuteOfDay())
        assertEquals(1439, t(23, 59).minuteOfDay())
    }

    @Test fun `khung trong ngay la mot doan`() {
        assertEquals(listOf(DaySpan(540, 720)), spansOf(t(9), t(12)))
    }

    @Test fun `khung qua nua dem tach hai doan`() {
        assertEquals(listOf(DaySpan(1320, 1440), DaySpan(0, 360)), spansOf(t(22), t(6)))
    }

    @Test fun `khung ket thuc dung 00 00 chi mot doan toi 24 00`() {
        assertEquals(listOf(DaySpan(1080, 1440)), spansOf(t(18), t(0)))
    }

    @Test fun `khung bat dau 00 00`() {
        assertEquals(listOf(DaySpan(0, 360)), spansOf(t(0), t(6)))
    }

    @Test fun `bat dau bang ket thuc khong co doan`() {
        assertTrue(spansOf(t(5), t(5)).isEmpty())
    }

    @Test fun `do dai doan`() {
        assertEquals(480, spansOf(t(22), t(6)).sumOf { it.length })
    }
}
