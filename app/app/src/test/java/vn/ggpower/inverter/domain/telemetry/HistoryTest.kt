package vn.ggpower.inverter.domain.telemetry

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import vn.ggpower.inverter.domain.settings.TemperatureUnit
import java.time.LocalDateTime

/** Chọn trường, trục và khoảng của biểu đồ lịch sử (đặc tả §17.4; BO_SUNG_v3.md §12). */
class HistoryTest {
    private val pv = HistoryField("pv", FieldUnit.Watt)
    private val load = HistoryField("load", FieldUnit.Watt)
    private val temp = HistoryField("temp", FieldUnit.Celsius)
    private val volt = HistoryField("volt", FieldUnit.Volt)
    private val freq = HistoryField("freq", FieldUnit.Hertz)

    @Test fun `toi da 3 truong va 2 don vi`() {
        assertTrue(canSelect(listOf(pv, temp), load))
        assertFalse(canSelect(listOf(pv, temp), volt))
        assertFalse(canSelect(listOf(pv, load, temp), volt))
        assertTrue(canSelect(listOf(pv), volt))
        assertEquals(listOf(pv, temp, load), toggleField(listOf(pv, temp), load))
        assertEquals(listOf(pv, temp), toggleField(listOf(pv, temp), freq))
    }

    @Test fun `bo chon, khong bo duoc truong cuoi`() {
        assertEquals(listOf(temp), toggleField(listOf(pv, temp), pv))
        assertEquals(listOf(temp), toggleField(listOf(temp), temp))
    }

    @Test fun `truc trai la don vi chon dau, truc phai la don vi thu hai`() {
        assertEquals(FieldUnit.Watt to FieldUnit.Celsius, axisUnits(listOf(pv, load, temp)))
        assertEquals(FieldUnit.Celsius to FieldUnit.Watt, axisUnits(listOf(temp, pv)))
        assertEquals(FieldUnit.Watt to null, axisUnits(listOf(pv, load)))
    }

    @Test fun `mien truc dep 3 khoang`() {
        assertEquals(0.0..6000.0, niceRange(listOf(0.0, 4200.0, 3100.0)))
        assertEquals(25.0..40.0, niceRange(listOf(29.8, 33.0, 38.5)))
        assertEquals(-50.0..25.0, niceRange(listOf(-30.4, 21.5)))
        assertEquals(0.0..1.0, niceRange(listOf(null, null)))
        val flat = niceRange(listOf(50.0, 50.0))
        assertTrue(50.0 in flat && flat.start < 50 && flat.endInclusive > 50)
    }

    @Test fun `buoc dep`() {
        assertEquals(1.0, niceStep(0.8), 1e-9)
        assertEquals(2.5, niceStep(2.2), 1e-9)
        assertEquals(500.0, niceStep(420.0), 1e-9)
        assertEquals(10.0, niceStep(6.0), 1e-9)
    }

    @Test fun `moc bat dau va so diem`() {
        val now = LocalDateTime.of(2026, 9, 24, 9, 41, 30)
        assertEquals(LocalDateTime.of(2026, 9, 24, 8, 41), rangeStart(HistoryRange.Hour, now))
        assertEquals(LocalDateTime.of(2026, 9, 24, 0, 0), rangeStart(HistoryRange.Day, now))
        assertEquals(LocalDateTime.of(2026, 9, 18, 0, 0), rangeStart(HistoryRange.Week, now))
        assertEquals(61, HistorySeries(HistoryRange.Hour, now, emptyMap()).size)
        assertEquals(97, HistorySeries(HistoryRange.Day, now, emptyMap()).size)
        assertEquals(169, HistorySeries(HistoryRange.Week, now, emptyMap()).size)
    }

    @Test fun `diem cuoi co du lieu`() {
        val s = HistorySeries(HistoryRange.Day, LocalDateTime.of(2026, 9, 24, 0, 0), mapOf("pv" to List(97) { if (it <= 38) 1.0 else null }))
        assertEquals(38, s.lastIndex())
        assertEquals(LocalDateTime.of(2026, 9, 24, 9, 30), s.timeAt(38))
    }

    @Test fun `doi truong nhiet do sang do F, truong khac giu nguyen`() {
        val s = HistorySeries(
            HistoryRange.Hour, LocalDateTime.of(2026, 9, 24, 8, 41),
            mapOf("pv" to listOf(100.0, null), "temp" to listOf(45.0, null, -20.0)),
        )
        assertSame(s, s.inTemperatureUnit(listOf(pv, temp), TemperatureUnit.Celsius))
        val f = s.inTemperatureUnit(listOf(pv, temp), TemperatureUnit.Fahrenheit)
        assertEquals(listOf(100.0, null), f.values["pv"])
        assertEquals(listOf(113.0, null, -4.0), f.values["temp"])
        assertEquals(s.range to s.start, f.range to f.start)
    }
}
