package vn.ggpower.inverter.ui.home

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import vn.ggpower.inverter.data.home.DayCurves
import vn.ggpower.inverter.data.home.EnergyBucket
import vn.ggpower.inverter.data.home.FakeHomeRepository
import vn.ggpower.inverter.data.home.HomeOverview
import vn.ggpower.inverter.data.home.HomeRepository
import vn.ggpower.inverter.data.home.PeriodSummary
import vn.ggpower.inverter.data.home.YieldBucket
import vn.ggpower.inverter.domain.home.PeriodSelection
import vn.ggpower.inverter.domain.home.StatsPeriod
import java.time.Clock
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId

@OptIn(ExperimentalCoroutinesApi::class)
class StatsControllerTest {

    // Thời điểm của prototype: T5 24/09/2026 09:41.
    private val zone = ZoneId.of("Asia/Ho_Chi_Minh")
    private val clock = Clock.fixed(LocalDateTime.of(2026, 9, 24, 9, 41).atZone(zone).toInstant(), zone)

    private class Repo : HomeRepository {
        val curveDates = mutableListOf<LocalDate>()
        val yieldSelections = mutableListOf<PeriodSelection>()
        val energySelections = mutableListOf<PeriodSelection>()
        override fun overview(projectId: String): Flow<HomeOverview> = emptyFlow()
        override suspend fun dayCurves(projectId: String, date: LocalDate): DayCurves {
            curveDates += date; return FakeHomeRepository.SAMPLE_DAY
        }
        override suspend fun energyBuckets(projectId: String, selection: PeriodSelection): List<EnergyBucket> {
            energySelections += selection
            return List(selection.bucketCount) { EnergyBucket(1.0, 0.0, 0.5, 0.4, 1.0, 70) }
        }
        val summarySelections = mutableListOf<PeriodSelection>()
        override suspend fun periodSummary(projectId: String, selection: PeriodSelection): PeriodSummary {
            summarySelections += selection
            return PeriodSummary(1.0, 80, 1000, 1.0)
        }
        override suspend fun yieldBuckets(projectId: String, selection: PeriodSelection): List<YieldBucket> {
            yieldSelections += selection; return List(selection.bucketCount) { YieldBucket(1.0, 0.0, 0.0) }
        }
    }

    private fun TestScope.controller(repo: Repo) =
        StatsController("p1", repo, clock, this)

    @Test fun `mac dinh, hom nay, diem moi nhat, cot 08-10 gio`() = runTest(StandardTestDispatcher()) {
        val repo = Repo()
        val c = controller(repo)
        assertNull(c.state.value.curves)
        advanceUntilIdle()
        val s = c.state.value
        assertEquals(PeriodSelection(StatsPeriod.Day, LocalDate.of(2026, 9, 24)), s.selection)
        assertEquals(39, s.powerIndex)
        assertEquals(39, s.batteryIndex)
        assertEquals(4, s.yieldIndex)
        assertFalse(s.canGoNext)
    }

    @Test fun `doi sang thang, moi bieu do theo ngay trong thang, cot ngay 24`() = runTest(StandardTestDispatcher()) {
        val repo = Repo()
        val c = controller(repo)
        advanceUntilIdle()
        c.onPeriodChange(StatsPeriod.Month)
        advanceUntilIdle()
        val s = c.state.value
        assertEquals(1, repo.curveDates.size)                 // không tải lại đường 15 phút
        assertEquals(1, repo.energySelections.size)
        assertNull(s.curves)
        assertEquals(30, s.energyBuckets!!.size)
        assertEquals(30, s.yieldBuckets!!.size)
        assertEquals(Triple(23, 23, 23), Triple(s.powerIndex, s.batteryIndex, s.yieldIndex))
        assertFalse(s.isDay)
        assertEquals(listOf(StatsPeriod.Day, StatsPeriod.Month), repo.summarySelections.map { it.period })
        assertEquals(80, s.summary!!.selfUsePercent)
    }

    @Test fun `nam roi ve ngay, tai lai duong trong ngay`() = runTest(StandardTestDispatcher()) {
        val repo = Repo()
        val c = controller(repo)
        advanceUntilIdle()
        c.onPeriodChange(StatsPeriod.Year)
        advanceUntilIdle()
        assertEquals(8, c.state.value.powerIndex)
        c.onPeriodChange(StatsPeriod.Day)
        advanceUntilIdle()
        assertEquals(2, repo.curveDates.size)
        assertNull(c.state.value.energyBuckets)
        assertEquals(39, c.state.value.powerIndex)
    }

    @Test fun `lui mot ngay, tai lai ca hai, cot cuoi, tien lai duoc`() = runTest(StandardTestDispatcher()) {
        val repo = Repo()
        val c = controller(repo)
        advanceUntilIdle()
        c.onPrevious()
        advanceUntilIdle()
        assertEquals(listOf(LocalDate.of(2026, 9, 24), LocalDate.of(2026, 9, 23)), repo.curveDates)
        assertEquals(11, c.state.value.yieldIndex)
        assertTrue(c.state.value.canGoNext)
        c.onNext()
        advanceUntilIdle()
        assertEquals(LocalDate.of(2026, 9, 24), c.state.value.selection.anchor)
    }

    @Test fun `khong tien qua hom nay`() = runTest(StandardTestDispatcher()) {
        val repo = Repo()
        val c = controller(repo)
        advanceUntilIdle()
        c.onNext()
        advanceUntilIdle()
        assertEquals(1, repo.curveDates.size)
        assertEquals(1, repo.yieldSelections.size)
    }

    @Test fun `chon diem tren bieu do`() = runTest(StandardTestDispatcher()) {
        val c = controller(Repo())
        advanceUntilIdle()
        c.onSelectPower(10); c.onSelectBattery(5); c.onSelectYield(2)
        assertEquals(Triple(10, 5, 2), Triple(c.state.value.powerIndex, c.state.value.batteryIndex, c.state.value.yieldIndex))
    }
}
