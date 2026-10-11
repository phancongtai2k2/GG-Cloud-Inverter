package vn.ggpower.inverter.ui.data

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import vn.ggpower.inverter.data.telemetry.FakeTelemetryRepository
import vn.ggpower.inverter.data.telemetry.TelemetryLoadException
import vn.ggpower.inverter.data.telemetry.TelemetryRepository
import vn.ggpower.inverter.domain.telemetry.BatteryTelemetry
import vn.ggpower.inverter.domain.telemetry.InverterTelemetry
import vn.ggpower.inverter.domain.telemetry.DeviceKind
import vn.ggpower.inverter.domain.telemetry.HistoryField
import vn.ggpower.inverter.domain.telemetry.HistoryRange
import vn.ggpower.inverter.domain.telemetry.HistorySeries
import java.time.Clock
import java.time.LocalDateTime
import java.time.Instant
import java.time.ZoneOffset

@OptIn(ExperimentalCoroutinesApi::class)
/** Số liệu thời gian thực dùng chung màn 17 (LiveController), thử với luồng inverter. */
class LiveControllerTest {

    private val clock = Clock.fixed(Instant.parse("2026-09-24T02:41:03Z"), ZoneOffset.UTC)

    private class Repo : TelemetryRepository {
        var calls = 0
        var fail: TelemetryLoadException.Kind? = null
        override fun inverter(projectId: String): Flow<InverterTelemetry> = flow {
            calls++
            fail?.let { throw TelemetryLoadException(it) }
            emit(FakeTelemetryRepository.INVERTER)
        }
        override fun battery(projectId: String): Flow<BatteryTelemetry> = flow { emit(FakeTelemetryRepository.BATTERY) }
        override suspend fun history(projectId: String, device: DeviceKind, range: HistoryRange, fields: List<HistoryField>, now: LocalDateTime): HistorySeries =
            HistorySeries(range, now, emptyMap())
    }

    @Test fun `co so lieu kem dong ho`() = runTest {
        val c = LiveController({ Repo().inverter("p1") }, clock, backgroundScope)
        backgroundScope.launch { c.state.collect {} }
        runCurrent()
        val s = c.state.value as LiveUiState.Content
        assertEquals(clock.instant(), s.now)
    }

    @Test fun `loi khi chua co so lieu thi bao loi, lam moi thi mo lai luong`() = runTest {
        val repo = Repo().apply { fail = TelemetryLoadException.Kind.NoConnection }
        val c = LiveController({ repo.inverter("p1") }, clock, backgroundScope)
        backgroundScope.launch { c.state.collect {} }
        runCurrent()
        assertEquals(LiveUiState.Error(noConnection = true), c.state.value)
        repo.fail = null
        c.onRefresh()
        runCurrent()
        assertTrue(c.state.value is LiveUiState.Content)
        assertEquals(2, repo.calls)
    }

    @Test fun `loi khi da co so lieu thi giu so lieu cu`() = runTest {
        val repo = Repo()
        val c = LiveController({ repo.inverter("p1") }, clock, backgroundScope)
        backgroundScope.launch { c.state.collect {} }
        runCurrent()
        repo.fail = TelemetryLoadException.Kind.Server
        c.onRefresh()
        advanceTimeBy(1_100)
        assertTrue(c.state.value is LiveUiState.Content)
    }
}
