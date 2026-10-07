package vn.ggpower.inverter.ui.devices

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test
import vn.ggpower.inverter.data.device.DeviceLoadException
import vn.ggpower.inverter.data.device.FakeDeviceRepository

class DevicesLoaderTest {

    private val repo = FakeDeviceRepository()

    @Test fun `tai xong thi co inverter kem dan pin`() = runTest {
        val l = DevicesLoader("p1", repo).also { it.load() }
        val units = (l.state.value as DevicesUiState.Content).units
        assertEquals(1, units.size)
        assertEquals("GGI5K-25031501", units[0].inverter.serial)
        assertEquals(76, units[0].battery?.socPercent)
    }

    @Test fun `loi mang, thu lai thi co noi dung`() = runTest {
        repo.failure = DeviceLoadException.Kind.NoConnection
        val l = DevicesLoader("p1", repo).also { it.load() }
        assertEquals(DevicesUiState.Error(noConnection = true), l.state.value)
        repo.failure = null
        l.load()
        assertEquals(DevicesUiState.Content(FakeDeviceRepository.SAMPLE), l.state.value)
    }
}
