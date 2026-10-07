package vn.ggpower.inverter.domain.tou

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import vn.ggpower.inverter.domain.config.OperatingMode
import java.time.DayOfWeek.FRIDAY
import java.time.DayOfWeek.MONDAY
import java.time.DayOfWeek.SATURDAY
import java.time.DayOfWeek.SUNDAY
import java.time.DayOfWeek.THURSDAY
import java.time.DayOfWeek.TUESDAY
import java.time.DayOfWeek.WEDNESDAY
import java.time.LocalDate

class TouScheduleTest {

    @Test fun `danh so khung theo loai`() {
        assertEquals(listOf(1, 2, 1, 2), WEEKDAYS.slots.indices.map { WEEKDAYS.numberOf(it) })
        assertEquals(3, WEEKDAYS.indexOf(SlotKind.Discharge, 2))
        assertEquals(-1, WEEKDAYS.indexOf(SlotKind.Charge, 3))
    }

    @Test fun `xoa khung thi khung sau don len va danh so lai`() {
        val after = WEEKDAYS.withSlotRemoved(WEEKDAYS.indexOf(SlotKind.Charge, 1))
        val charges = after.slotsOf(SlotKind.Charge)
        assertEquals(1, charges.size)
        assertEquals(t(13), charges[0].start)                 // Sạc 2 cũ thành Sạc 1
        assertEquals(1, after.numberOf(after.slots.indexOf(charges[0])))
        assertEquals(2, after.slotsOf(SlotKind.Discharge).size)
    }

    @Test fun `xoa het khung`() {
        var s = WEEKDAYS
        repeat(4) { s = s.withSlotRemoved(0) }
        assertTrue(s.summary().isEmpty)
    }

    @Test fun `thoi luong va qua dem`() {
        assertEquals(480, WEEKDAYS.slots[0].durationMinutes)   // 22:00–06:00
        assertTrue(WEEKDAYS.slots[0].overnight)
        assertFalse(WEEKDAYS.slots[1].overnight)
        assertEquals(150, charge(t(22, 30), t(1)).durationMinutes)
        assertTrue(charge(t(18), t(0)).overnight)              // kết thúc đúng 00:00 vẫn tính qua đêm như prototype
    }

    @Test fun `tom tat sap theo gio bat dau nhu box man 16`() {
        val s = WEEKDAYS.summary()
        assertEquals(2, s.chargeCount); assertEquals(2, s.dischargeCount)
        assertEquals(listOf(t(9), t(13), t(18), t(22)), s.periods.map { it.start })
        assertEquals(listOf(SlotKind.Discharge, SlotKind.Charge, SlotKind.Discharge, SlotKind.Charge), s.periods.map { it.kind })
        assertEquals(listOf(180, 180, 180, 480), s.periods.map { it.durationMinutes })
        assertEquals(listOf(false, false, false, true), s.periods.map { it.overnight })
    }

    @Test fun `chien luoc ap dung cho mot ngay`() {
        assertEquals("a", SAMPLE.strategyFor(LocalDate.of(2026, 9, 24))?.id)  // T5
        assertEquals("b", SAMPLE.strategyFor(LocalDate.of(2026, 9, 27))?.id)  // CN
        assertNull(listOf(WEEKDAYS).strategyFor(LocalDate.of(2026, 9, 26)))   // T7 chưa gán
    }

    @Test fun `chon thu dang thuoc chien luoc khac thi chuyen sang`() {
        val after = SAMPLE.toggleDay("a", SATURDAY)
        assertEquals(days(MONDAY, TUESDAY, WEDNESDAY, THURSDAY, FRIDAY, SATURDAY), after[0].weekdays)
        assertEquals(days(SUNDAY), after[1].weekdays)
    }

    @Test fun `bo chon thu thi thu do chua gan`() {
        val after = SAMPLE.toggleDay("a", MONDAY)
        assertFalse(MONDAY in after[0].weekdays)
        assertEquals(listOf(MONDAY), after.unassignedDays())
        assertEquals(days(SATURDAY, SUNDAY), after[1].weekdays)
    }

    @Test fun `moi thu thuoc toi da mot chien luoc sau nhieu lan chon`() {
        var s = SAMPLE + TouStrategy("c", "Mùa mưa", emptySet(), emptyList())
        s = s.toggleDay("c", SUNDAY).toggleDay("c", MONDAY).toggleDay("a", SUNDAY)
        java.time.DayOfWeek.entries.forEach { d -> assertTrue(s.count { d in it.weekdays } <= 1) }
        assertEquals("a", s.first { SUNDAY in it.weekdays }.id)
    }

    @Test fun `dong nhac thu cua chien luoc khac`() {
        val others = SAMPLE.daysOwnedByOthers("a")
        assertEquals(listOf(SATURDAY, SUNDAY), others.values.single())
        assertEquals("Cuối tuần", others.keys.single().name)
    }

    @Test fun `toi da 4 chien luoc`() {
        assertTrue(SAMPLE.canAddStrategy())
        val four = SAMPLE + TouStrategy("c", "C", emptySet(), emptyList()) + TouStrategy("d", "D", emptySet(), emptyList())
        assertFalse(four.canAddStrategy())
    }

    @Test fun `goi y gio mac dinh khi khong trung`() {
        val empty = TouStrategy("x", "", emptySet(), emptyList())
        assertEquals(defaultSlot(SlotKind.Charge), empty.suggestSlot(SlotKind.Charge))
        assertEquals(defaultSlot(SlotKind.Discharge), empty.suggestSlot(SlotKind.Discharge))
    }

    @Test fun `goi y khoang trong dau tien khi mac dinh bi trung`() {
        // Mặc định sạc 10:00–14:00 trùng Xả 1 09:00–12:00 → khoảng trống 4 giờ đầu tiên: sau Sạc 1 (06:00) bị Xả 1 chặn,
        // sau Xả 1 (12:00) bị Sạc 2 13:00 chặn, sau Sạc 2 (16:00) bị Xả 2 18:00 chặn, sau Xả 2 (21:00) bị Sạc 1 22:00 chặn
        // → không có khoảng 4 giờ; thử 1 giờ: 00:00 trùng Sạc 1, 06:00–07:00 trống.
        val s = WEEKDAYS.suggestSlot(SlotKind.Charge)
        assertEquals(t(6), s.start); assertEquals(t(7), s.end)
        assertEquals(80, s.stopSocPercent); assertEquals(3000, s.maxPowerW)
    }

    @Test fun `goi y khoang trong 4 gio`() {
        val busy = TouStrategy("x", "", emptySet(), listOf(discharge(t(9), t(12))))
        val s = busy.suggestSlot(SlotKind.Charge)
        assertEquals(t(0), s.start); assertEquals(t(4), s.end)
    }

    @Test fun `co bat tat lich theo che do 16 4a`() {
        assertEquals(ScheduleEnableFlags(true, true), scheduleEnableFlags(OperatingMode.Tou, WEEKDAYS))
        assertEquals(ScheduleEnableFlags(true, true), scheduleEnableFlags(OperatingMode.GgAi, WEEKDAYS))
        assertEquals(ScheduleEnableFlags(false, false), scheduleEnableFlags(OperatingMode.SelfConsumption, WEEKDAYS))
        assertEquals(ScheduleEnableFlags(false, false), scheduleEnableFlags(OperatingMode.Tou, null))
        val onlyCharge = WEEKDAYS.copy(slots = WEEKDAYS.slotsOf(SlotKind.Charge))
        assertEquals(ScheduleEnableFlags(true, false), scheduleEnableFlags(OperatingMode.Tou, onlyCharge))
    }
}
