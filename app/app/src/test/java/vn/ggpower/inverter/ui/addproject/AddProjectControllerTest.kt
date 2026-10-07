package vn.ggpower.inverter.ui.addproject

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
import vn.ggpower.inverter.data.location.LocationProvider
import vn.ggpower.inverter.data.location.LocationResult
import vn.ggpower.inverter.data.project.FakeAddProjectRepository
import vn.ggpower.inverter.data.project.CreateProjectResult
import vn.ggpower.inverter.data.project.SerialCheckResult
import vn.ggpower.inverter.domain.project.TariffError
import vn.ggpower.inverter.domain.project.TariffKind
import vn.ggpower.inverter.domain.project.Coordinates
import vn.ggpower.inverter.domain.project.ProjectInfoError
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

@OptIn(ExperimentalCoroutinesApi::class)
class AddProjectControllerTest {
    private val repo = FakeAddProjectRepository()
    private val clock = Clock.fixed(Instant.parse("2026-10-03T03:00:00Z"), ZoneOffset.UTC)

    private class StubLocation(var result: LocationResult) : LocationProvider {
        override suspend fun currentLocation() = result
    }
    private val location = StubLocation(LocationResult.Found(Coordinates(20.9512, 105.9283), "Xuân Quan, Văn Giang, Hưng Yên"))

    @Test fun `serial trong thi bao loi, khong goi backend`() = runTest {
        val c = AddProjectController(repo, location, clock, backgroundScope)
        c.onCheck()
        assertEquals(SerialError.Empty, c.device.value.error)
        assertFalse(c.device.value.checking)
    }

    @Test fun `serial hop le thi hien thiet bi va bat Tiep tuc, sua serial thi bo ket qua`() = runTest {
        val c = AddProjectController(repo, location, clock, backgroundScope)
        c.onSerialChange("sn2609a00137")
        assertEquals("SN2609A00137", c.device.value.serial)
        c.onCheck()
        runCurrent()
        assertTrue(c.device.value.checking)
        assertFalse(c.device.value.canContinue)
        advanceTimeBy(1_000); runCurrent()
        assertEquals(FakeAddProjectRepository.SAMPLE_DEVICE, c.device.value.device)
        assertTrue(c.device.value.canContinue)
        c.onSerialChange("SN2609A0013")
        assertNull(c.device.value.device)
        assertFalse(c.device.value.canContinue)
    }

    @Test fun `thuoc tai khoan khac, khong tim thay, loi mang`() = runTest {
        val c = AddProjectController(repo, location, clock, backgroundScope)
        c.onSerialChange(FakeAddProjectRepository.OWNED_SERIAL)
        c.onCheck()
        advanceTimeBy(1_000); runCurrent()
        assertEquals(SerialError.OwnedByOther, c.device.value.error)
        c.onSerialChange("SN0000000000")
        assertNull(c.device.value.error)
        c.onCheck()
        advanceTimeBy(1_000); runCurrent()
        assertEquals(SerialError.NotFound, c.device.value.error)
        repo.failure = SerialCheckResult.NoConnection
        c.onCheck()
        advanceTimeBy(1_000); runCurrent()
        assertEquals(SerialError.NoConnection, c.device.value.error)
    }

    @Test fun `sua serial trong luc dang kiem tra thi bo ket qua cu`() = runTest {
        val c = AddProjectController(repo, location, clock, backgroundScope)
        c.onSerialChange(FakeAddProjectRepository.SAMPLE_SERIAL)
        c.onCheck()
        runCurrent()
        c.onSerialChange("SN1")
        advanceTimeBy(1_000); runCurrent()
        assertNull(c.device.value.device)
        assertFalse(c.device.value.checking)
    }

    @Test fun `serial tu man quet QR duoc dien va kiem tra ngay`() = runTest {
        val c = AddProjectController(repo, location, clock, backgroundScope)
        c.onScanned(FakeAddProjectRepository.SAMPLE_SERIAL)
        advanceTimeBy(1_000); runCurrent()
        assertEquals(FakeAddProjectRepository.SAMPLE_DEVICE, c.device.value.device)
    }

    @Test fun `14b mac dinh ten Nha cua ban va ngay hom nay`() = runTest {
        val c = AddProjectController(repo, location, clock, backgroundScope)
        assertEquals("Nhà của bạn", c.info.value.input.name)
        assertEquals(LocalDate.of(2026, 10, 3), c.info.value.input.installedOn)
        assertEquals("Asia/Ho_Chi_Minh", c.info.value.input.timeZone)
    }

    @Test fun `14b bam Tiep tuc thi kiem tra, hop le moi sang 14c`() = runTest {
        val c = AddProjectController(repo, location, clock, backgroundScope)
        var done = 0
        backgroundScope.launch { c.infoDone.collect { done++ } }
        c.onInfoContinue()
        runCurrent()
        assertEquals(ProjectInfoError.AddressEmpty, c.info.value.addressError)
        assertEquals(ProjectInfoError.KwpEmpty, c.info.value.kwpError)
        assertEquals(0, done)
        c.onInfoChange { it.copy(address = "Xuân Quan", pvKwp = "6.5") }
        assertNull(c.info.value.addressError)
        assertEquals("6,5", c.info.value.input.pvKwp)
        c.onInfoContinue()
        runCurrent()
        assertEquals(1, done)
    }

    @Test fun `14b dinh vi dien dia chi khi o trong, giu dia chi da nhap`() = runTest {
        val c = AddProjectController(repo, location, clock, backgroundScope)
        c.onLocate()
        runCurrent()
        assertEquals("Xuân Quan, Văn Giang, Hưng Yên", c.info.value.input.address)
        assertEquals(Coordinates(20.9512, 105.9283), c.info.value.input.coordinates)
        assertEquals(LocateNote.Found(Coordinates(20.9512, 105.9283)), c.info.value.locateNote)
        c.onInfoChange { it.copy(address = "Số 1 Đường A") }
        c.onLocate()
        runCurrent()
        assertEquals("Số 1 Đường A", c.info.value.input.address)
    }

    @Test fun `14b vi tri tat, bi tu choi quyen`() = runTest {
        location.result = LocationResult.Disabled
        val c = AddProjectController(repo, location, clock, backgroundScope)
        c.onLocate()
        runCurrent()
        assertEquals(LocateNote.Disabled, c.info.value.locateNote)
        assertFalse(c.info.value.locating)
        c.onLocationDenied()
        assertEquals(LocateNote.Denied, c.info.value.locateNote)
    }

    /** Đã qua 14a (serial mẫu hợp lệ) và 14b (thông tin hợp lệ). */
    private fun kotlinx.coroutines.test.TestScope.atTariff(): AddProjectController {
        val c = AddProjectController(repo, location, clock, backgroundScope)
        c.onScanned(FakeAddProjectRepository.SAMPLE_SERIAL)
        advanceTimeBy(1_000); runCurrent()
        c.onInfoChange { it.copy(address = "Xuân Quan", pvKwp = "6,5") }
        return c
    }

    @Test fun `14c gia co dinh trong thi bao loi, khong tao du an`() = runTest {
        val c = atTariff()
        c.onTariffChange { it.copy(kind = TariffKind.Fixed) }
        c.onCreate(skipTariff = false)
        assertEquals(TariffError.FixedEmpty, c.tariff.value.fixedError)
        assertFalse(c.tariff.value.busy)
        c.onTariffChange { it.copy(fixedPrice = "2500") }
        assertEquals("2.500", c.tariff.value.input.fixedPrice)
        assertNull(c.tariff.value.fixedError)
    }

    @Test fun `14c tao du an hoac bo qua deu tao, phat id du an moi`() = runTest {
        val c = atTariff()
        val ids = mutableListOf<String>()
        backgroundScope.launch { c.created.collect { ids += it } }
        c.onCreate(skipTariff = true)
        runCurrent()
        assertTrue(c.tariff.value.skipping)
        advanceTimeBy(1_000); runCurrent()
        assertEquals(listOf("new-1"), ids)
        assertFalse(c.tariff.value.busy)
    }

    @Test fun `14c du 10 du an thi bao loi`() = runTest {
        val c = atTariff()
        repo.createFailure = CreateProjectResult.LimitReached
        c.onCreate(skipTariff = false)
        advanceTimeBy(1_000); runCurrent()
        assertEquals(CreateError.LimitReached, c.tariff.value.formError)
    }
}
