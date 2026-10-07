package vn.ggpower.inverter.ui.tou

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import vn.ggpower.inverter.data.config.ConfigLoadException
import vn.ggpower.inverter.data.config.FakeConfigRepository
import vn.ggpower.inverter.domain.config.Role
import vn.ggpower.inverter.domain.config.WriteState
import vn.ggpower.inverter.domain.tou.TouStrategy

/** Màn 16a: danh sách chiến lược đã lưu, nút "Thêm chiến lược" (Thay đổi 0.6 §3; ADR-001; ADR-002 mục 9). */
class TouScheduleLoaderTest {

    private val repo = FakeConfigRepository()
    private fun loader(readOnly: Boolean = false) = TouScheduleLoader("p1", repo, readOnly)
    private val TouScheduleLoader.content get() = state.value as TouScheduleUiState.Content

    @Test fun `danh sach mau, hom nay thuoc Ngay thuong, them duoc`() = runTest {
        val l = loader().also { it.load() }
        assertEquals(listOf("Ngày thường", "Cuối tuần"), l.content.strategies.map { it.name })
        assertEquals(FakeConfigRepository.WEEKDAYS.id, l.content.todayStrategyId)
        assertTrue(l.content.canAdd)
        assertTrue(l.content.addEnabled)
    }

    @Test fun `du 4 chien luoc thi an nut them`() = runTest {
        val extra = (3..4).map { TouStrategy("s$it", "Chiến lược $it", emptySet(), emptyList()) }
        repo.config = repo.config.copy(strategies = repo.config.strategies + extra)
        val l = loader().also { it.load() }
        assertFalse(l.content.canAdd)
    }

    @Test fun `khong co quyen ghi thi khoa nut them`() = runTest {
        assertFalse(loader(readOnly = true).also { it.load() }.content.addEnabled)
        repo.config = repo.config.copy(writeAccess = FakeConfigRepository.accessFor(Role.Owner, WriteState.GatewayOffline))
        assertFalse(loader().also { it.load() }.content.addEnabled)
    }

    @Test fun `doc lai thay danh sach vua luu, loi thi giu danh sach cu`() = runTest {
        val l = loader().also { it.load() }
        repo.config = repo.config.copy(strategies = listOf(FakeConfigRepository.WEEKDAYS))
        l.refresh()
        assertEquals(1, l.content.strategies.size)
        repo.failure = ConfigLoadException.Kind.Server
        l.refresh()
        assertEquals(1, l.content.strategies.size)
    }

    @Test fun `loi tai`() = runTest {
        repo.failure = ConfigLoadException.Kind.NoConnection
        val l = loader().also { it.load() }
        assertEquals(TouScheduleUiState.Error(noConnection = true), l.state.value)
        l.refresh()
        assertEquals(TouScheduleUiState.Error(noConnection = true), l.state.value)
    }
}
