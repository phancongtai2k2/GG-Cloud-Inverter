package vn.ggpower.inverter.ui.data

import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import vn.ggpower.inverter.data.telemetry.FakeTelemetryRepository
import vn.ggpower.inverter.data.telemetry.TelemetryLoadException
import vn.ggpower.inverter.domain.telemetry.DeviceKind
import vn.ggpower.inverter.domain.telemetry.HistoryFields
import vn.ggpower.inverter.domain.telemetry.HistoryRange
import java.time.Clock
import java.time.LocalDateTime
import java.time.ZoneOffset

/** Thẻ Biểu đồ lịch sử (BO_SUNG_v3.md §12). */
class HistoryControllerTest {
    // 24/09/2026 09:41 giờ địa phương giả định UTC.
    private val clock = Clock.fixed(LocalDateTime.of(2026, 9, 24, 9, 41).toInstant(ZoneOffset.UTC), ZoneOffset.UTC)
    private val repo = FakeTelemetryRepository(clock)

    @Test fun `mac dinh 24 gio, chon san 3 truong va diem moi nhat`() = runTest {
        val c = HistoryController("p1", DeviceKind.Inverter, repo, clock, this)
        advanceUntilIdle()
        val s = c.state.value
        assertEquals(HistoryRange.Day, s.range)
        assertEquals(HistoryFields.defaults(DeviceKind.Inverter), s.selected)
        assertFalse(s.loading)
        // 09:30 là điểm 15 phút cuối cùng đã có (09:45 chưa tới).
        assertEquals(38, s.index)
        assertEquals(LocalDateTime.of(2026, 9, 24, 9, 30), s.series!!.timeAt(s.index))
    }

    @Test fun `doi khoang thi tai lai, doi truong thi khong`() = runTest {
        val c = HistoryController("p1", DeviceKind.Inverter, repo, clock, this)
        advanceUntilIdle()
        c.onRangeChange(HistoryRange.Week)
        assertTrue(c.state.value.loading)
        advanceUntilIdle()
        assertEquals(HistoryRange.Week, c.state.value.series!!.range)
        c.onToggleField(HistoryFields.LoadPower)
        assertFalse(c.state.value.loading)
        assertEquals(listOf(HistoryFields.PvPower, HistoryFields.TempDcAc), c.state.value.selected)
        c.onToggleField(HistoryFields.GridVoltage)
        assertEquals(listOf(HistoryFields.PvPower, HistoryFields.TempDcAc), c.state.value.selected)
    }

    @Test fun `keo chon diem bi kep trong mien`() = runTest {
        val c = HistoryController("p1", DeviceKind.Battery, repo, clock, this)
        advanceUntilIdle()
        assertEquals(HistoryFields.defaults(DeviceKind.Battery), c.state.value.selected)
        c.onSelectIndex(500)
        assertEquals(96, c.state.value.index)
        c.onSelectIndex(-3)
        assertEquals(0, c.state.value.index)
    }

    @Test fun `loi tai, thu lai`() = runTest {
        repo.failure = TelemetryLoadException.Kind.Server
        val c = HistoryController("p1", DeviceKind.Inverter, repo, clock, this)
        advanceUntilIdle()
        assertTrue(c.state.value.failed)
        repo.failure = null
        c.retry()
        advanceUntilIdle()
        assertFalse(c.state.value.failed)
        assertTrue(c.state.value.series != null)
    }
}
