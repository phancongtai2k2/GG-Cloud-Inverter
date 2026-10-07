package vn.ggpower.inverter.domain.home

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import vn.ggpower.inverter.domain.home.FlowDirection.FromHub
import vn.ggpower.inverter.domain.home.FlowDirection.None
import vn.ggpower.inverter.domain.home.FlowDirection.ToHub

class EnergyFlowTest {

    private fun snap(pv: Double = 0.0, grid: Double = 0.0, bat: Double = 0.0, load: Double = 0.0) =
        EnergySnapshot(pv, grid, bat, load, 50)

    @Test fun `du lieu mau man hinh chinh`() {
        // PV 4,2 kW · lưới 0,0 · pin +1,1 (sạc) · tải 3,1 (Main.png)
        val s = snap(pv = 4.2, grid = 0.0, bat = 1.1, load = 3.1)
        assertEquals(FlowEdges(pv = ToHub, grid = None, battery = FromHub, load = FromHub), s.edges())
        assertEquals(BatteryActivity.Charging, s.batteryActivity)
    }

    @Test fun `ban dem pin xa, mua luoi`() {
        val s = snap(pv = 0.0, grid = 0.2, bat = -0.6, load = 0.8)
        assertEquals(FlowEdges(pv = None, grid = ToHub, battery = ToHub, load = FromHub), s.edges())
        assertEquals(BatteryActivity.Discharging, s.batteryActivity)
    }

    @Test fun `phat len luoi`() = assertEquals(FromHub, snap(grid = -1.5).edges().grid)

    @Test fun `duoi nguong khong co tia sang`() {
        val s = snap(pv = 0.04, grid = -0.04, bat = 0.049, load = 0.0)
        assertEquals(FlowEdges(None, None, None, None), s.edges())
        assertEquals(BatteryActivity.Idle, s.batteryActivity)
    }

    @Test fun `dung nguong thi tinh la co dong`() = assertEquals(ToHub, snap(pv = 0.05).edges().pv)

    @Test fun `ty le pin theo nguon mau 16 kWh`() {
        val b = BatteryContent(capacityKwh = 16.0, fromPvKwh = 10.2, fromGridKwh = 1.9)
        assertEquals(0.6375, b.pvFraction, 1e-9)
        assertEquals(0.11875, b.gridFraction, 1e-9)
    }

    @Test fun `ty le khong vuot 1`() {
        val b = BatteryContent(capacityKwh = 10.0, fromPvKwh = 9.0, fromGridKwh = 4.0)
        assertEquals(0.9, b.pvFraction, 1e-9)
        assertEquals(0.1, b.gridFraction, 1e-9)
    }

    @Test fun `so sanh san luong`() {
        assertEquals(YieldComparison(Trend.Higher, 18), compareYield(18.6, 15.76))
        assertEquals(YieldComparison(Trend.Lower, 25), compareYield(15.0, 20.0))
        assertEquals(YieldComparison(Trend.Same, 0), compareYield(10.02, 10.0))
        assertNull(compareYield(5.0, 0.0))
    }
}
