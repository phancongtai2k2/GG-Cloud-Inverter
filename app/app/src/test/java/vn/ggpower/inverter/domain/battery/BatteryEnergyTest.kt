package vn.ggpower.inverter.domain.battery

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import vn.ggpower.inverter.data.battery.FakeBatteryEnergyRepository
import vn.ggpower.inverter.domain.home.BatteryActivity

class BatteryEnergyTest {

    @Test fun `dung luong kha dung theo SOC va dung luong dinh muc`() {
        val e = FakeBatteryEnergyRepository.SAMPLE.copy(socPercent = 75.625)
        assertEquals(12.1, e.availableKwh!!, 1e-9)
        assertEquals(16.0, e.copy(socPercent = 120.0).availableKwh!!, 1e-9)
        assertNull(e.copy(socPercent = null).availableKwh)
        assertNull(e.copy(content = null).availableKwh)
    }

    @Test fun `trang thai sac xa theo nguong 0,05 kW`() {
        val e = FakeBatteryEnergyRepository.SAMPLE
        assertEquals(BatteryActivity.Charging, e.activity)
        assertEquals(BatteryActivity.Discharging, e.copy(powerKw = -0.8).activity)
        assertEquals(BatteryActivity.Idle, e.copy(powerKw = 0.04).activity)
        assertEquals(BatteryActivity.Idle, e.copy(powerKw = null).activity)
    }
}
