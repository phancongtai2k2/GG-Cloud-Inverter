package vn.ggpower.inverter.domain.alert

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Test
import vn.ggpower.inverter.data.alert.FakeAlertRepository
import vn.ggpower.inverter.domain.settings.TemperatureUnit
import java.time.LocalDateTime

class AlertDetailTest {
    private val now = LocalDateTime.of(2026, 9, 24, 9, 41)

    @Test fun `thoi luong tinh toi bay gio khi dang dien ra, toi luc ket thuc khi da ket thuc`() {
        assertEquals(149L, FakeAlertRepository.detailSample("grid").durationMinutes(now))
        assertEquals(25L, FakeAlertRepository.detailSample("pv").durationMinutes(now))
    }

    @Test fun `canh bao thong tin khong co thoi luong`() {
        assertNull(FakeAlertRepository.detailSample("fw").durationMinutes(now))
    }

    @Test fun `bat dau sau bay gio thi thoi luong bang 0`() {
        val d = FakeAlertRepository.detailSample("grid")
        assertEquals(0L, d.durationMinutes(d.startedAt.minusMinutes(5)))
    }

    @Test fun `moc gio cua bieu do`() {
        val c = FakeAlertRepository.detailSample("grid").chart!!
        assertEquals(LocalDateTime.of(2026, 9, 24, 6, 57), c.timeAt(0))
        assertEquals(LocalDateTime.of(2026, 9, 24, 7, 12), c.timeAt(c.markIndex))
        assertEquals(LocalDateTime.of(2026, 9, 24, 7, 27), c.timeAt(c.values.lastIndex))
    }

    @Test fun `bieu do nhiet do doi theo do F, bieu do khac giu nguyen`() {
        val temp = FakeAlertRepository.detailSample("temp").chart!!
        assertSame(temp, temp.inTemperatureUnit(TemperatureUnit.Celsius))
        val f = temp.inTemperatureUnit(TemperatureUnit.Fahrenheit)
        assertEquals(100.4, f.values.first(), 1e-9)   // 38 °C
        assertEquals(113.0, f.values[f.markIndex], 1e-9) // 45 °C
        assertEquals(temp.label to temp.unit, f.label to f.unit)
        val grid = FakeAlertRepository.detailSample("grid").chart!!
        assertSame(grid, grid.inTemperatureUnit(TemperatureUnit.Fahrenheit))
    }
}
