package vn.ggpower.inverter.ui.warranty

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test
import vn.ggpower.inverter.data.warranty.FakeWarrantyRepository
import vn.ggpower.inverter.data.warranty.WarrantyLoadException
import java.time.Clock
import java.time.LocalDate
import java.time.ZoneId

class WarrantyLoaderTest {

    private val repo = FakeWarrantyRepository()
    private val zone = ZoneId.of("Asia/Ho_Chi_Minh")
    private val clock = Clock.fixed(LocalDate.of(2026, 10, 3).atTime(9, 0).atZone(zone).toInstant(), zone)

    @Test fun `tai xong co du lieu va ngay hom nay tu Clock`() = runTest {
        val l = WarrantyLoader("p1", repo, clock).also { it.load() }
        assertEquals(WarrantyUiState.Content(FakeWarrantyRepository.SAMPLE, LocalDate.of(2026, 10, 3)), l.state.value)
    }

    @Test fun `loi may chu, thu lai thi co noi dung`() = runTest {
        repo.failure = WarrantyLoadException.Kind.Server
        val l = WarrantyLoader("p1", repo, clock).also { it.load() }
        assertEquals(WarrantyUiState.Error(noConnection = false), l.state.value)
        repo.failure = null
        l.load()
        assertEquals(FakeWarrantyRepository.SAMPLE, (l.state.value as WarrantyUiState.Content).warranty)
    }
}
