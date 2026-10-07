package vn.ggpower.inverter.domain.warranty

import org.junit.Assert.assertEquals
import org.junit.Test
import vn.ggpower.inverter.data.warranty.FakeWarrantyRepository
import java.time.LocalDate

class WarrantyTest {

    private val expires = LocalDate.of(2026, 11, 9)
    private val meter = DeviceWarranty("Đồng hồ đo thông minh", "GGM-24110902", LocalDate.of(2024, 11, 9), expires)

    // ---------- Tình trạng ----------

    @Test fun `con 91 ngay la con bao hanh`() =
        assertEquals(WarrantyStatus.Active, meter.status(expires.minusDays(91)))

    @Test fun `dung mep 90 ngay la sap het han`() =
        assertEquals(WarrantyStatus.ExpiringSoon, meter.status(expires.minusDays(WARRANTY_SOON_DAYS)))

    @Test fun `dung ngay het han van la sap het han`() =
        assertEquals(WarrantyStatus.ExpiringSoon, meter.status(expires))

    @Test fun `qua ngay het han mot ngay la het bao hanh`() =
        assertEquals(WarrantyStatus.Expired, meter.status(expires.plusDays(1)))

    @Test fun `du lieu mau o ngay dung anh`() {
        val today = FakeWarrantyRepository.DESIGN_TODAY
        assertEquals(
            listOf(WarrantyStatus.Active, WarrantyStatus.Active, WarrantyStatus.ExpiringSoon),
            FakeWarrantyRepository.SAMPLE.devices.map { it.status(today) },
        )
    }

    // ---------- Thanh đã dùng ----------

    @Test fun `truoc ngay kich hoat thanh bang 0`() =
        assertEquals(0f, meter.usedFraction(meter.activatedOn.minusDays(10)), 0f)

    @Test fun `sau han thanh kep o 100 phan tram`() {
        assertEquals(1f, meter.usedFraction(expires.plusDays(400)), 0f)
        assertEquals(100, meter.usedPercent(expires.plusDays(400)))
    }

    @Test fun `ngay kich hoat bang 0 va ngay het han bang 100`() {
        assertEquals(0, meter.usedPercent(meter.activatedOn))
        assertEquals(100, meter.usedPercent(expires))
    }

    @Test fun `giua ky la 50 phan tram`() {
        val d = DeviceWarranty("x", "s", LocalDate.of(2026, 1, 1), LocalDate.of(2026, 1, 11))
        assertEquals(50, d.usedPercent(LocalDate.of(2026, 1, 6)))
    }

    @Test fun `han trung ngay kich hoat coi nhu da dung het`() {
        val d = DeviceWarranty("x", "s", LocalDate.of(2026, 1, 1), LocalDate.of(2026, 1, 1))
        assertEquals(1f, d.usedFraction(LocalDate.of(2025, 12, 1)), 0f)
    }

    @Test fun `phan tram du lieu mau o ngay dung anh`() {
        val today = FakeWarrantyRepository.DESIGN_TODAY
        assertEquals(listOf(31, 15, 94), FakeWarrantyRepository.SAMPLE.devices.map { it.usedPercent(today) })
    }

    // ---------- Thời gian còn lại ----------

    @Test fun `chu con lai du lieu mau khop HTML`() {
        val today = FakeWarrantyRepository.DESIGN_TODAY
        assertEquals(
            listOf(
                WarrantyRemaining.YearsMonths(3, 6),
                WarrantyRemaining.YearsMonths(8, 6),
                WarrantyRemaining.Days(46),
            ),
            FakeWarrantyRepository.SAMPLE.devices.map { it.remaining(today) },
        )
    }

    @Test fun `con 90 ngay ghi so ngay, 91 ngay ghi thang`() {
        val d = DeviceWarranty("x", "s", LocalDate.of(2025, 1, 1), LocalDate.of(2026, 4, 2))
        assertEquals(WarrantyRemaining.Days(90), d.remaining(LocalDate.of(2026, 1, 2)))
        assertEquals(WarrantyRemaining.YearsMonths(0, 3), d.remaining(LocalDate.of(2026, 1, 1)))
    }

    @Test fun `du 14 ngay lam tron xuong, 15 ngay lam tron len`() {
        val d = DeviceWarranty("x", "s", LocalDate.of(2025, 1, 1), LocalDate.of(2030, 6, 15))
        assertEquals(WarrantyRemaining.YearsMonths(2, 5), d.remaining(LocalDate.of(2028, 1, 1)))  // 2 năm 5 tháng 14 ngày
        assertEquals(WarrantyRemaining.YearsMonths(2, 6), d.remaining(LocalDate.of(2027, 12, 31))) // 2 năm 5 tháng 15 ngày
    }

    @Test fun `11 thang du 20 ngay lam tron thanh nam chan`() {
        val d = DeviceWarranty("x", "s", LocalDate.of(2025, 1, 1), LocalDate.of(2030, 1, 21))
        assertEquals(WarrantyRemaining.YearsMonths(3, 0), d.remaining(LocalDate.of(2027, 2, 1))) // 2 năm 11 tháng 20 ngày
    }

    @Test fun `ngay het han va sau han`() {
        assertEquals(WarrantyRemaining.Days(1), meter.remaining(expires.minusDays(1)))
        assertEquals(WarrantyRemaining.EndsToday, meter.remaining(expires))
        assertEquals(WarrantyRemaining.Expired, meter.remaining(expires.plusDays(1)))
    }

    // ---------- Gói ----------

    @Test fun `thoi han goi`() {
        assertEquals(WarrantyTerm(2, 0), meter.term)
        assertEquals(WarrantyTerm(1, 6), DeviceWarranty("x", "s", LocalDate.of(2025, 1, 1), LocalDate.of(2026, 7, 1)).term)
        assertEquals(listOf(5, 10, 2), FakeWarrantyRepository.SAMPLE.devices.map { it.term.years })
    }
}
