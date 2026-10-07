package vn.ggpower.inverter.domain.tou

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SlotValidationTest {

    private fun input(kind: SlotKind = SlotKind.Charge, start: String = "10:00", end: String = "11:00", soc: String = "80", power: String = "3.000") =
        SlotInput(kind, start, end, soc, power)

    private val empty = TouStrategy("x", "Trống", emptySet(), emptyList())

    private fun err(v: SlotValidation) = (v as SlotValidation.Invalid).error

    @Test fun `khung hop le, cong suat co dau cham nghin`() {
        val v = validateSlot(input(power = "3.500"), empty, null) as SlotValidation.Valid
        assertEquals(3500, v.slot.maxPowerW)
        assertEquals(80, v.slot.stopSocPercent)
        // Tiếng Anh: dấu phẩy ngăn nghìn.
        assertEquals(3500, (validateSlot(input(power = "3,500"), empty, null) as SlotValidation.Valid).slot.maxPowerW)
        assertEquals(SlotError.PowerRange, err(validateSlot(input(power = "12,001"), empty, null)))
    }

    @Test fun `1 du so khung cung loai, toi da 3`() {
        val three = empty.copy(slots = listOf(charge(t(0), t(1)), charge(t(2), t(3)), charge(t(4), t(5))))
        assertEquals(SlotError.LimitReached(SlotKind.Charge), err(validateSlot(input(start = "20:00", end = "21:00"), three, null)))
        // Khung xả vẫn thêm được; sửa một khung sạc có sẵn cũng được.
        assertTrue(validateSlot(input(SlotKind.Discharge, "20:00", "21:00"), three, null) is SlotValidation.Valid)
        assertTrue(validateSlot(input(start = "00:00", end = "01:30"), three, editingIndex = 0) is SlotValidation.Valid)
    }

    @Test fun `2 sai dinh dang gio`() {
        listOf("1:30", "24:00", "12:60", "12h30", "", "12:3").forEach {
            assertEquals(it, SlotError.TimeFormat, err(validateSlot(input(start = it), empty, null)))
        }
        assertEquals(SlotError.TimeFormat, err(validateSlot(input(end = "abc"), empty, null)))
        assertTrue(validateSlot(input(start = "01:30", end = "23:59"), empty, null) is SlotValidation.Valid)
    }

    @Test fun `3 bat dau bang ket thuc`() =
        assertEquals(SlotError.SameStartEnd, err(validateSlot(input(start = "05:00", end = "05:00"), empty, null)))

    @Test fun `4 soc ngoai 0-100`() {
        listOf("101", "-1", "abc", "", "1000", "50.5").forEach {
            assertEquals(it, SlotError.SocRange, err(validateSlot(input(soc = it), empty, null)))
        }
        assertTrue(validateSlot(input(soc = "0"), empty, null) is SlotValidation.Valid)
        assertTrue(validateSlot(input(soc = "100"), empty, null) is SlotValidation.Valid)
    }

    @Test fun `5 cong suat ngoai 0-12000`() {
        listOf("12.001", "13000", "abc", "", "-5").forEach {
            assertEquals(it, SlotError.PowerRange, err(validateSlot(input(power = it), empty, null)))
        }
        assertTrue(validateSlot(input(power = "12.000"), empty, null) is SlotValidation.Valid)
        assertTrue(validateSlot(input(power = "0"), empty, null) is SlotValidation.Valid)
    }

    @Test fun `6 trung khung khac, bao dung ten va gio`() {
        val e = err(validateSlot(input(start = "10:00", end = "11:30"), WEEKDAYS, null))
        assertEquals(SlotError.Overlap(SlotKind.Discharge, 1, t(9), t(12)), e)
    }

    @Test fun `trung nhieu khung thi bao khung sac truoc nhu prototype`() {
        // 11:00–13:30 trùng cả Xả 1 (09–12) và Sạc 2 (13–16) → báo Sạc 2.
        assertEquals(SlotError.Overlap(SlotKind.Charge, 2, t(13), t(16)), err(validateSlot(input(start = "11:00", end = "13:30"), WEEKDAYS, null)))
        // Thứ tự thêm khung không ảnh hưởng.
        val reordered = WEEKDAYS.copy(slots = WEEKDAYS.slots.reversed())
        assertEquals(SlotKind.Charge, (err(validateSlot(input(start = "11:00", end = "13:30"), reordered, null)) as SlotError.Overlap).kind)
    }

    @Test fun `trung voi khung qua nua dem`() {
        // Sạc 1 22:00–06:00 phủ 05:00–07:00 và 23:00–23:30.
        assertEquals(SlotError.Overlap(SlotKind.Charge, 1, t(22), t(6)), err(validateSlot(input(SlotKind.Discharge, "05:00", "07:00"), WEEKDAYS, null)))
        assertEquals(SlotError.Overlap(SlotKind.Charge, 1, t(22), t(6)), err(validateSlot(input(SlotKind.Discharge, "23:00", "23:30"), WEEKDAYS, null)))
    }

    @Test fun `cham mep khong tinh la trung`() {
        // 06:00–09:00 chạm Sạc 1 (kết thúc 06:00) và Xả 1 (bắt đầu 09:00).
        assertTrue(validateSlot(input(SlotKind.Discharge, "06:00", "09:00"), WEEKDAYS.copy(slots = WEEKDAYS.slots.take(3)), null) is SlotValidation.Valid)
        val a = charge(t(5), t(7)); val b = charge(t(7), t(9))
        assertFalse(a.overlaps(b))
    }

    @Test fun `thu tu kiem tra, loi dau tien`() {
        // Sai giờ và sai SOC cùng lúc → báo giờ (bước 2) trước.
        assertEquals(SlotError.TimeFormat, err(validateSlot(input(start = "x", soc = "999"), empty, null)))
    }

    @Test fun `sua khung khong tu trung voi chinh no`() {
        val idx = WEEKDAYS.indexOf(SlotKind.Charge, 2) // 13:00–16:00
        assertTrue(validateSlot(input(start = "13:30", end = "16:00"), WEEKDAYS, idx) is SlotValidation.Valid)
    }

    @Test fun `parse gio`() {
        assertEquals(t(1, 30), parseTime("01:30"))
        assertNull(parseTime("1:30"))
    }
}
