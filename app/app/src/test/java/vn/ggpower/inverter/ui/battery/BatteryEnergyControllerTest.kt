package vn.ggpower.inverter.ui.battery

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import vn.ggpower.inverter.data.battery.FakeBatteryEnergyRepository

@OptIn(ExperimentalCoroutinesApi::class)
class BatteryEnergyControllerTest {
    private val repo = FakeBatteryEnergyRepository()

    @Test fun `tai du lieu`() = runTest {
        val c = BatteryEnergyController("p1", repo, backgroundScope)
        backgroundScope.launch { c.state.collect {} }
        runCurrent()
        assertEquals(BatteryEnergyUiState.Loading, c.state.value)
        advanceTimeBy(1_000); runCurrent()
        assertEquals(BatteryEnergyUiState.Content(FakeBatteryEnergyRepository.SAMPLE), c.state.value)
    }

    @Test fun `loi mang, thu lai`() = runTest {
        repo.failure = true
        val c = BatteryEnergyController("p1", repo, backgroundScope)
        backgroundScope.launch { c.state.collect {} }
        advanceTimeBy(1_000); runCurrent()
        assertEquals(BatteryEnergyUiState.Error(noConnection = true), c.state.value)
        repo.failure = null
        c.retry()
        runCurrent()
        assertEquals(BatteryEnergyUiState.Loading, c.state.value)
        advanceTimeBy(1_000); runCurrent()
        assertEquals(BatteryEnergyUiState.Content(FakeBatteryEnergyRepository.SAMPLE), c.state.value)
    }

    @Test fun `vach nhan theo prototype`() {
        val (pv, grid) = labelLines(10.2 / 16, 1.9 / 16)
        assertEquals(182f, pv!!, 0.01f)
        assertEquals(268f, grid!!, 0.01f)
    }

    @Test fun `hai lop mong thi nhan PV day len, lop rong khong co nhan`() {
        val (pv, grid) = labelLines(0.6 / 16, 2.9 / 16)
        assertEquals(52f, grid!! - pv!!, 0.01f)
        val (pvOnly, none) = labelLines(0.98, 0.0)
        assertNull(none)
        assertEquals((300f - 240f * 0.98f + 300f) / 2 - 13f, pvOnly!!, 0.01f)
    }
}
