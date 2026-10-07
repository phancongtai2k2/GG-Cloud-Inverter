package vn.ggpower.inverter.domain.tou

import org.junit.Assert.assertEquals
import org.junit.Test

/** Điền sẵn và nhập liệu popup khung (Thay đổi 0.6 §5; ADR-002 mục 1, 6). */
class SlotSheetTest {

    @Test fun `dien san tu khung, cong suat co dau cham nghin`() {
        assertEquals(SlotInput(SlotKind.Charge, "22:00", "06:00", "90", "3.000"), charge(t(22), t(6), 90, 3000).toInput())
        assertEquals("12.000", discharge(t(1), t(2), 20, 12000).toInput().maxPower)
    }

    @Test fun `o gio tu chen dau hai cham`() {
        assertEquals("22:00", normalizeTimeInput("2200"))
        assertEquals("01:3", normalizeTimeInput("013"))
        assertEquals("01", normalizeTimeInput("01"))
        assertEquals("22:00", normalizeTimeInput("22:00"))
        assertEquals("1:30", normalizeTimeInput("1:30"))
        assertEquals("22:00", normalizeTimeInput("22:001"))
        assertEquals("12:30", normalizeTimeInput("12a:3b0"))
    }

    @Test fun `loai mac dinh khi them`() {
        assertEquals(SlotKind.Charge, WEEKDAYS.defaultAddKind())
        val fullCharge = WEEKDAYS.copy(slots = WEEKDAYS.slots + charge(t(16), t(17)))
        assertEquals(SlotKind.Discharge, fullCharge.defaultAddKind())
        val full = fullCharge.copy(slots = fullCharge.slots + discharge(t(21), t(22)))
        assertEquals(SlotKind.Charge, full.defaultAddKind())
        // Đủ 3 + 3: popup vẫn mở với khung sạc và báo lỗi 1.
        val v = validateSlot(full.suggestSlot(SlotKind.Charge).toInput(), full, null)
        assertEquals(SlotValidation.Invalid(SlotError.LimitReached(SlotKind.Charge)), v)
    }

    @Test fun `o cong suat tu chen dau cham nghin khi go`() {
        assertEquals(listOf("3", "35", "350", "3.500"), listOf("3", "35", "350", "3500").map { normalizePowerInput(it) })
        assertEquals("12.000", normalizePowerInput("12000"))
        assertEquals("350", normalizePowerInput("3.50")) // xoá lùi từ 3.500
        assertEquals("0", normalizePowerInput("000"))
        assertEquals("", normalizePowerInput(""))
        assertEquals("123.456", normalizePowerInput("1234567"))
        // 13.000 vẫn gõ được để kiểm tra 5 báo lỗi.
        val v = validateSlot(SlotInput(SlotKind.Charge, "01:00", "02:00", "90", normalizePowerInput("13000")), WEEKDAYS.copy(slots = emptyList()), null)
        assertEquals(SlotValidation.Invalid(SlotError.PowerRange), v)
    }
}
