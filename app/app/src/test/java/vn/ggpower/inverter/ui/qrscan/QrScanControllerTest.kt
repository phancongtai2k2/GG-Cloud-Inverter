package vn.ggpower.inverter.ui.qrscan

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import vn.ggpower.inverter.data.project.FakeAddProjectRepository
import vn.ggpower.inverter.domain.project.DeviceQr
import vn.ggpower.inverter.domain.project.qrChecksum

@OptIn(ExperimentalCoroutinesApi::class)
class QrScanControllerTest {
    private val repo = FakeAddProjectRepository()
    private val sample = "GGP:HYB5K:SN2609A00137:19"
    private fun qrFor(serial: String) = "GGP:HYB5K:$serial".let { "$it:${qrChecksum(it)}" }

    @Test fun `ma sai dinh dang thi hien nhan 2 giay roi quet tiep`() = runTest {
        val c = QrScanController(repo, backgroundScope)
        c.onCodeDetected("https://example.com")
        assertTrue(c.state.value.invalid)
        assertNull(c.state.value.sheet)
        advanceTimeBy(1_999); runCurrent()
        assertTrue(c.state.value.invalid)
        advanceTimeBy(2); runCurrent()
        assertFalse(c.state.value.invalid)
    }

    @Test fun `cung mot ma sai con trong khung khong gia han nhan`() = runTest {
        val c = QrScanController(repo, backgroundScope)
        c.onCodeDetected("ABC")
        advanceTimeBy(1_500); runCurrent()
        c.onCodeDetected("ABC")
        advanceTimeBy(600); runCurrent()
        assertFalse(c.state.value.invalid)
    }

    @Test fun `ma dung thi rung, kiem tra thiet bi va dung nhan ma moi`() = runTest {
        val c = QrScanController(repo, backgroundScope)
        var buzz = 0
        backgroundScope.launch { c.scanned.collect { buzz++ } }
        c.onCodeDetected(sample)
        runCurrent()
        assertEquals(1, buzz)
        assertEquals(ScanSheet.Checking(DeviceQr("HYB5K", "SN2609A00137")), c.state.value.sheet)
        c.onCodeDetected("ABC")
        assertFalse(c.state.value.invalid)
        advanceTimeBy(1_000); runCurrent()
        assertEquals(ScanSheet.Found(DeviceQr("HYB5K", "SN2609A00137"), FakeAddProjectRepository.SAMPLE_DEVICE), c.state.value.sheet)
        c.onRescan()
        assertNull(c.state.value.sheet)
    }

    @Test fun `thiet bi thuoc tai khoan khac hoac khong tim thay`() = runTest {
        val c = QrScanController(repo, backgroundScope)
        c.onCodeDetected(qrFor(FakeAddProjectRepository.OWNED_SERIAL))
        advanceTimeBy(1_000); runCurrent()
        assertEquals(ScanProblem.OwnedByOther, (c.state.value.sheet as ScanSheet.Problem).problem)
        c.onRescan()
        c.onCodeDetected(qrFor("SN0000000000"))
        advanceTimeBy(1_000); runCurrent()
        assertEquals(ScanProblem.NotFound, (c.state.value.sheet as ScanSheet.Problem).problem)
    }

    @Test fun `bat tat den pin`() = runTest {
        val c = QrScanController(repo, backgroundScope)
        c.onToggleTorch()
        assertTrue(c.state.value.torchOn)
        c.onToggleTorch()
        assertFalse(c.state.value.torchOn)
    }
}
