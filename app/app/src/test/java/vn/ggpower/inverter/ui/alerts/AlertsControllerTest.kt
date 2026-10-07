package vn.ggpower.inverter.ui.alerts

import kotlinx.coroutines.launch
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import vn.ggpower.inverter.data.alert.AlertLoadException
import vn.ggpower.inverter.data.alert.FakeAlertRepository
import vn.ggpower.inverter.domain.alert.AlertFilter

@OptIn(ExperimentalCoroutinesApi::class)
class AlertsControllerTest {
    private val repo = FakeAlertRepository()

    @Test fun `tai, doi bo loc, cap nhat khi danh dau da xu ly`() = runTest {
        val c = AlertsController("p1", repo, backgroundScope)
        backgroundScope.launch { c.state.collect {} }
        advanceTimeBy(1_000); runCurrent()
        assertEquals(AlertsUiState.Content(FakeAlertRepository.SAMPLE, AlertFilter.All), c.state.value)
        c.onFilterChange(AlertFilter.Open)
        advanceTimeBy(1_000); runCurrent()
        assertEquals(AlertFilter.Open, (c.state.value as AlertsUiState.Content).filter)
        repo.setResolved("grid", true)
        advanceTimeBy(1_000); runCurrent()
        assertTrue((c.state.value as AlertsUiState.Content).alerts.first { it.id == "grid" }.resolved)
    }

    @Test fun `loi mang, thu lai`() = runTest {
        repo.failure = AlertLoadException.Kind.NoConnection
        val c = AlertsController("p1", repo, backgroundScope)
        backgroundScope.launch { c.state.collect {} }
        advanceTimeBy(1_000); runCurrent()
        assertEquals(AlertsUiState.Error(noConnection = true), c.state.value)
        repo.failure = null
        c.retry()
        advanceTimeBy(1_000); runCurrent()
        assertTrue(c.state.value is AlertsUiState.Content)
    }
}
