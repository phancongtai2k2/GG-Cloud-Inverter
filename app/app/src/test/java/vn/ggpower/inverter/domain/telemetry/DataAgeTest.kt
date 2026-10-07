package vn.ggpower.inverter.domain.telemetry

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import vn.ggpower.inverter.data.telemetry.FakeTelemetryRepository
import java.time.Instant

class DataAgeTest {
    private val t0 = Instant.parse("2026-09-24T02:41:00Z")

    @Test fun `duoi 1 phut tinh giay, duoi 1 gio tinh phut, lau hon thi ghi thoi diem`() {
        assertEquals(DataAge.Seconds(3), dataAge(t0, t0.plusSeconds(3)))
        assertEquals(DataAge.Seconds(59), dataAge(t0, t0.plusSeconds(59)))
        assertEquals(DataAge.Minutes(1), dataAge(t0, t0.plusSeconds(60)))
        assertEquals(DataAge.Minutes(59), dataAge(t0, t0.plusSeconds(3599)))
        assertEquals(DataAge.At(t0), dataAge(t0, t0.plusSeconds(3600)))
    }

    @Test fun `dong ho lech tuong lai thi la 0 giay`() {
        assertEquals(DataAge.Seconds(0), dataAge(t0, t0.minusSeconds(5)))
    }

    @Test fun `cong suat PV tong thieu mot chuoi thi khong tinh`() {
        val t = FakeTelemetryRepository.INVERTER
        assertEquals(4200.0, t.pvTotalW!!, 1e-9)
        assertNull(t.copy(pv2PowerW = null).pvTotalW)
    }
}
