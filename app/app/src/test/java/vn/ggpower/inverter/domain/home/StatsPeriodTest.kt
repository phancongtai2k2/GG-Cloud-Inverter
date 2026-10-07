package vn.ggpower.inverter.domain.home

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime

class StatsPeriodTest {

    private val today = LocalDate.of(2026, 9, 24)
    private val now = LocalDateTime.of(2026, 9, 24, 9, 41)

    @Test fun `lui va tien ky, khong tien qua hom nay`() {
        val day = PeriodSelection(StatsPeriod.Day, today)
        assertEquals(LocalDate.of(2026, 9, 23), day.previous().anchor)
        assertFalse(day.canGoNext(today))
        assertEquals(day, day.next(today))
        assertEquals(today, day.previous().next(today).anchor)

        val month = PeriodSelection(StatsPeriod.Month, today)
        assertEquals(LocalDate.of(2026, 8, 24), month.previous().anchor)
        assertFalse(month.canGoNext(today))
        assertTrue(month.previous().canGoNext(today))

        val year = PeriodSelection(StatsPeriod.Year, today)
        assertEquals(2025, year.previous().anchor.year)
        assertFalse(year.canGoNext(today))
    }

    @Test fun `so cot va gio moi cot`() {
        assertEquals(12, PeriodSelection(StatsPeriod.Day, today).bucketCount)
        assertEquals(30, PeriodSelection(StatsPeriod.Month, today).bucketCount)          // tháng 9
        assertEquals(31, PeriodSelection(StatsPeriod.Month, LocalDate.of(2026, 10, 2)).bucketCount)
        assertEquals(29, PeriodSelection(StatsPeriod.Month, LocalDate.of(2028, 2, 10)).bucketCount)
        assertEquals(2.0, PeriodSelection(StatsPeriod.Day, today).bucketHours(4), 0.0)
        assertEquals(720.0, PeriodSelection(StatsPeriod.Year, today).bucketHours(8), 0.0) // tháng 9 có 30 ngày
    }

    @Test fun `cot chon san nhu prototype`() {
        assertEquals(4, PeriodSelection(StatsPeriod.Day, today).currentBucket(now))      // 08:00–10:00
        assertEquals(23, PeriodSelection(StatsPeriod.Month, today).currentBucket(now))   // ngày 24
        assertEquals(8, PeriodSelection(StatsPeriod.Year, today).currentBucket(now))     // tháng 9
        assertEquals(11, PeriodSelection(StatsPeriod.Day, today.minusDays(1)).currentBucket(now))
    }

    @Test fun `khoang thoi gian cua cot`() {
        val (s, e) = PeriodSelection(StatsPeriod.Day, today).bucketRange(4)
        assertEquals(LocalDateTime.of(2026, 9, 24, 8, 0), s)
        assertEquals(LocalDateTime.of(2026, 9, 24, 10, 0), e)
    }

    @Test fun `nhan truc x`() {
        assertEquals(
            listOf("0h", "", "4h", "", "8h", "", "12h", "", "16h", "", "20h", ""),
            PeriodSelection(StatsPeriod.Day, today).bucketLabels("h", "T"),
        )
        val m = PeriodSelection(StatsPeriod.Month, today).bucketLabels("h", "T")
        assertEquals(listOf("1", "6", "11", "16", "21", "26"), m.filter { it.isNotEmpty() })
        assertEquals(
            listOf("T1", "", "T3", "", "T5", "", "T7", "", "T9", "", "T11", ""),
            PeriodSelection(StatsPeriod.Year, today).bucketLabels("h", "T"),
        )
    }

    @Test fun `truc cong suat nhu prototype`() {
        val a = powerAxis(-1.6, 4.2)
        assertEquals(listOf(-2.0, 0.0, 2.0, 4.0), a.ticks)
        assertEquals(-2.0, a.min, 1e-9)
        assertEquals(5.0, a.max, 1e-9)
    }

    @Test fun `truc cong suat chi duong`() {
        val a = powerAxis(0.0, 8.7)
        assertEquals(0.0, a.min, 1e-9)
        assertTrue(a.max >= 8.7)
        assertEquals(0.0, a.ticks.first(), 1e-9)
    }

    @Test fun `nice ceil nhu prototype`() {
        assertEquals(5.0, niceCeil(4.2 * 1.05), 1e-9)
        assertEquals(2.5, niceCeil(2.1 * 1.05), 1e-9)
        assertEquals(25.0, niceCeil(23.2 * 1.05), 1e-9)
        assertEquals(1000.0, niceCeil(571 * 1.05), 1e-9)
        assertEquals(4.0, niceCeil(3.9), 1e-9)
        assertEquals(1.0, niceCeil(0.0), 1e-9)
    }

    @Test fun `truc doi xung sac xa`() {
        assertEquals(AxisRange(-5.0, 5.0, listOf(-5.0, -2.5, 0.0, 2.5, 5.0)), symmetricAxis(4.4))
        assertEquals(-1.0, symmetricAxis(0.0).min, 1e-9)
    }

    @Test fun `truc cot`() {
        assertEquals(AxisRange(0.0, 5.0, listOf(0.0, 2.5, 5.0)), barAxis(4.2))
        assertEquals(AxisRange(0.0, 2.5, listOf(0.0, 1.25, 2.5)), barAxis(2.1))
    }
}
