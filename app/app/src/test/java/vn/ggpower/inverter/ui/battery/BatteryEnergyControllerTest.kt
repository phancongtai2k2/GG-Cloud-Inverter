package vn.ggpower.inverter.ui.battery

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
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
}
