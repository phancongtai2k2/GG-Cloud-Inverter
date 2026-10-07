package vn.ggpower.inverter.domain.alert

import org.junit.Assert.assertEquals
import org.junit.Test
import vn.ggpower.inverter.data.alert.FakeAlertRepository

/** Bộ lọc và đếm cảnh báo màn 10; số chưa xử lý cho chuông Màn hình chính (quyết 02/10/2026). */
class AlertTest {
    private val sample = FakeAlertRepository.SAMPLE

    @Test fun `loc theo trang thai xu ly`() {
        assertEquals(4, sample.filterBy(AlertFilter.All).size)
        assertEquals(listOf("grid", "temp"), sample.filterBy(AlertFilter.Open).map { it.id })
        assertEquals(listOf("fw", "pv"), sample.filterBy(AlertFilter.Resolved).map { it.id })
    }

    @Test fun `dem cho nhan chip va chuong`() {
        assertEquals(mapOf(AlertFilter.All to 4, AlertFilter.Open to 2, AlertFilter.Resolved to 2), sample.counts())
        assertEquals(2, sample.unresolvedCount())
        assertEquals(0, sample.map { it.copy(resolved = true) }.unresolvedCount())
    }
}
