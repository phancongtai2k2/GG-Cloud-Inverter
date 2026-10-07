package vn.ggpower.inverter.ui.alerts

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import vn.ggpower.inverter.data.alert.AlertLoadException
import vn.ggpower.inverter.data.alert.FakeAlertRepository
import vn.ggpower.inverter.domain.alert.durationMinutes
import vn.ggpower.inverter.domain.format.GgFormat
import java.time.Clock
import java.time.LocalDateTime
import java.time.ZoneOffset

@OptIn(ExperimentalCoroutinesApi::class)
class AlertDetailControllerTest {
    private val repo = FakeAlertRepository()
    private val now = LocalDateTime.of(2026, 9, 24, 9, 41)
    private val clock = Clock.fixed(now.toInstant(ZoneOffset.UTC), ZoneOffset.UTC)

    private fun content(c: AlertDetailController) = c.state.value as AlertDetailUiState.Content

    @Test fun `thoi luong theo prototype`() {
        fun dur(id: String) = FakeAlertRepository.detailSample(id).durationMinutes(now)?.let { GgFormat.duration(it.toInt(), "giờ", "phút") }
        assertEquals("2 giờ 29 phút", dur("grid"))
        assertEquals("19 giờ 11 phút", dur("temp"))
        assertEquals("25 phút", dur("pv"))
        assertNull(dur("fw"))
    }

    @Test fun `danh dau va bo danh dau da xu ly, danh sach 10 cap nhat theo`() = runTest {
        val c = AlertDetailController("p1", "grid", repo, clock, backgroundScope)
        backgroundScope.launch { c.state.collect {} }
        advanceTimeBy(1_000); runCurrent()
        assertFalse(content(c).detail.resolved)
        assertEquals(now, content(c).now)

        c.onToggleResolved()
        runCurrent()
        assertTrue(content(c).saving)
        advanceTimeBy(1_000); runCurrent()
        assertTrue(content(c).detail.resolved)
        assertFalse(content(c).saving)
        assertTrue(repo.current.first().first { it.id == "grid" }.resolved)

        c.onToggleResolved()
        advanceTimeBy(1_000); runCurrent()
        assertFalse(content(c).detail.resolved)
    }

    @Test fun `luu loi thi giu trang thai va bao loi`() = runTest {
        val c = AlertDetailController("p1", "grid", repo, clock, backgroundScope)
        backgroundScope.launch { c.state.collect {} }
        advanceTimeBy(1_000); runCurrent()
        repo.failure = AlertLoadException.Kind.NoConnection
        c.onToggleResolved()
        advanceTimeBy(1_000); runCurrent()
        val s = content(c)
        assertFalse(s.detail.resolved)
        assertFalse(s.saving)
        assertEquals(true, s.saveFailed?.noConnection)
        c.onSaveFailureShown(s.saveFailed!!.id)
        runCurrent()
        assertNull(content(c).saveFailed)
    }

    @Test fun `tai loi, thu lai`() = runTest {
        repo.failure = AlertLoadException.Kind.Server
        val c = AlertDetailController("p1", "temp", repo, clock, backgroundScope)
        backgroundScope.launch { c.state.collect {} }
        advanceTimeBy(1_000); runCurrent()
        assertEquals(AlertDetailUiState.Error(noConnection = false), c.state.value)
        repo.failure = null
        c.retry()
        advanceTimeBy(1_000); runCurrent()
        assertEquals("temp", content(c).detail.id)
    }
}
