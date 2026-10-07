package vn.ggpower.inverter.domain.battery

import org.junit.Assert.assertEquals
import org.junit.Test
import vn.ggpower.inverter.data.battery.FakeBatteryEnergyRepository
import vn.ggpower.inverter.domain.home.BatteryActivity
import vn.ggpower.inverter.domain.home.BatteryContent

class BatteryEnergyTest {

    @Test fun `ty le theo prototype, ba dong cong du 100 phan tram`() {
        val s = BatteryContent(16.0, 10.2, 1.9).shares()
        assertEquals(10.2, s.pvKwh, 1e-9)
        assertEquals(1.9, s.gridKwh, 1e-9)
        assertEquals(3.9, s.emptyKwh, 1e-9)
        assertEquals(12.1, s.availableKwh, 1e-9)
        assertEquals(63.8, s.pvPercent, 1e-9)
        assertEquals(11.9, s.gridPercent, 1e-9)
        assertEquals(24.3, s.emptyPercent, 1e-9)
    }

    @Test fun `vuot dung luong thi chan, khong am`() {
        val s = BatteryContent(16.0, 12.0, 6.0).shares()
        assertEquals(4.0, s.gridKwh, 1e-9)
        assertEquals(0.0, s.emptyKwh, 1e-9)
        assertEquals(0.0, s.emptyPercent, 1e-9)
        val neg = BatteryContent(16.0, -1.0, 2.0).shares()
        assertEquals(0.0, neg.pvKwh, 1e-9)
    }

    @Test fun `trang thai sac xa theo nguong 0,05 kW`() {
        val e = FakeBatteryEnergyRepository.SAMPLE
        assertEquals(BatteryActivity.Charging, e.activity)
        assertEquals(BatteryActivity.Discharging, e.copy(powerKw = -0.8).activity)
        assertEquals(BatteryActivity.Idle, e.copy(powerKw = 0.04).activity)
        assertEquals(BatteryActivity.Idle, e.copy(powerKw = null).activity)
    }
}
