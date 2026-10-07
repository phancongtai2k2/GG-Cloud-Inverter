package vn.ggpower.inverter.domain.telemetry

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import vn.ggpower.inverter.data.telemetry.FakeTelemetryRepository

/** Số liệu tính thêm của màn 17 pin (đặc tả §17.3; BO_SUNG_v3.md §14). */
class BatteryTelemetryTest {
    private val t = FakeTelemetryRepository.BATTERY

    @Test fun `lech dien ap, qua 1 V thi lam noi`() {
        assertEquals(0.2, t.voltageDeviationV!!, 1e-9)
        assertFalse(t.voltageDeviationHigh)
        assertFalse(t.copy(packVoltageInverterV = 52.0).voltageDeviationHigh)
        assertTrue(t.copy(packVoltageInverterV = 52.1).voltageDeviationHigh)
        assertNull(t.copy(packVoltageBmsV = null).voltageDeviationV)
    }

    @Test fun `cong suat cho phep va chenh ap cell`() {
        assertEquals(5100.0, t.allowedChargeW!!, 1e-9)
        assertEquals(5100.0, t.allowedDischargeW!!, 1e-9)
        assertEquals(12.0, t.cellDeltaMv!!, 1e-9)
        assertNull(t.copy(chargeLimitCurrentA = null).allowedChargeW)
    }

    @Test fun `trang thai so voi gioi han dong`() {
        assertEquals(CurrentLimitState.Charging, currentLimitState(21.5, 100.0, 100.0))
        assertEquals(CurrentLimitState.LimitedCharge, currentLimitState(98.0, 100.0, 100.0))
        assertEquals(CurrentLimitState.Discharging, currentLimitState(-30.4, 100.0, 100.0))
        assertEquals(CurrentLimitState.LimitedDischarge, currentLimitState(-80.0, 100.0, 80.0))
        assertEquals(CurrentLimitState.Idle, currentLimitState(0.3, 100.0, 100.0))
        assertEquals(CurrentLimitState.Charging, currentLimitState(150.0, null, null))
    }
}
