package vn.ggpower.inverter.domain.project

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class ProjectInfoTest {
    private val today = LocalDate.of(2026, 10, 3)
    private val sample = ProjectInfoInput(
        name = "Nhà của bạn", address = "Xuân Quan, Văn Giang, Hưng Yên", installedOn = LocalDate.of(2026, 9, 26), pvKwp = "6,5",
    )

    @Test fun `du lieu mau cua prototype hop le`() {
        val c = checkProjectInfo(sample, today)
        assertTrue(c.valid)
        assertEquals(6.5, c.kwp!!, 1e-9)
    }

    @Test fun `o bat buoc trong`() {
        val c = checkProjectInfo(sample.copy(name = " ", address = "", pvKwp = ""), today)
        assertEquals(ProjectInfoError.NameEmpty, c.nameError)
        assertEquals(ProjectInfoError.AddressEmpty, c.addressError)
        assertEquals(ProjectInfoError.KwpEmpty, c.kwpError)
        assertFalse(c.valid)
    }

    @Test fun `ten qua dai, cong suat bang 0, ngay lap dat trong tuong lai`() {
        val c = checkProjectInfo(sample.copy(name = "x".repeat(41), pvKwp = "0", installedOn = today.plusDays(1)), today)
        assertEquals(ProjectInfoError.NameTooLong, c.nameError)
        assertEquals(ProjectInfoError.KwpInvalid, c.kwpError)
        assertEquals(ProjectInfoError.DateInFuture, c.dateError)
        assertNull(checkProjectInfo(sample.copy(installedOn = today), today).dateError)
    }

    @Test fun `so thap phan dau phay hoac cham`() {
        assertEquals(6.5, parseDecimal("6,5")!!, 1e-9)
        assertEquals(6.5, parseDecimal("6.5")!!, 1e-9)
        assertEquals(12.0, parseDecimal("12")!!, 1e-9)
        assertNull(parseDecimal("6,5,1"))
        assertNull(parseDecimal("abc"))
        assertNull(parseDecimal(","))
    }

    @Test fun `o kWp khi go`() {
        assertEquals("6,5", normalizeKwpInput("6.5"))
        assertEquals("12,34", normalizeKwpInput("12a,345"))
        assertEquals("5", normalizeKwpInput(",5"))
        assertEquals("12345", normalizeKwpInput("1234567"))
    }
}
