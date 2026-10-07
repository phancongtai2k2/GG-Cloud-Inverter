package vn.ggpower.inverter.domain.tou

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.DayOfWeek.SATURDAY
import java.time.DayOfWeek.SUNDAY

class TouDraftTest {

    @Test fun `khong doi gi thi khong co thay doi, khong luu duoc`() {
        assertTrue(diffSchedules(SAMPLE, SAMPLE).isEmpty())
        assertFalse(canSave(SAMPLE, SAMPLE))
    }

    @Test fun `sua, them, xoa khung`() {
        val edited = WEEKDAYS
            .withSlotReplaced(WEEKDAYS.indexOf(SlotKind.Charge, 2), charge(t(13), t(15), 100, 3000))
            .withSlotRemoved(WEEKDAYS.indexOf(SlotKind.Discharge, 1))
            .withSlotAdded(charge(t(6), t(7), 80, 3000))
        val changes = diffSchedules(SAMPLE, listOf(edited, WEEKEND))
        assertEquals(
            listOf(
                TouChange.SlotChanged("Ngày thường", SlotKind.Charge, 2, charge(t(13), t(16), 100, 3000), charge(t(13), t(15), 100, 3000)),
                TouChange.SlotAdded("Ngày thường", SlotKind.Charge, 3, charge(t(6), t(7), 80, 3000)),
                // Xả 1 bị xoá → Xả 2 cũ thành Xả 1: so theo số thứ tự nên Xả 1 "đổi", Xả 2 "bị xoá".
                TouChange.SlotChanged("Ngày thường", SlotKind.Discharge, 1, discharge(t(9), t(12), 50, 4000), discharge(t(18), t(21), 20, 5000)),
                TouChange.SlotRemoved("Ngày thường", SlotKind.Discharge, 2, discharge(t(18), t(21), 20, 5000)),
            ),
            changes,
        )
        assertTrue(canSave(SAMPLE, listOf(edited, WEEKEND)))
    }

    @Test fun `chuyen thu tu chien luoc khac hien thay doi cua ca hai`() {
        val draft = SAMPLE.toggleDay("a", SATURDAY)
        val changes = diffSchedules(SAMPLE, draft)
        assertEquals(2, changes.size)
        assertEquals(TouChange.DaysChanged("Ngày thường", WEEKDAYS.weekdays, WEEKDAYS.weekdays + SATURDAY), changes[0])
        assertEquals(TouChange.DaysChanged("Cuối tuần", days(SATURDAY, SUNDAY), days(SUNDAY)), changes[1])
    }

    @Test fun `doi ten`() {
        val draft = listOf(WEEKDAYS.copy(name = "Mùa mưa"), WEEKEND)
        assertEquals(listOf(TouChange.Renamed("Mùa mưa", "Ngày thường")), diffSchedules(SAMPLE, draft))
    }

    @Test fun `them va xoa chien luoc`() {
        val added = TouStrategy("c", "Chiến lược C", days(SUNDAY), listOf(charge(t(1), t(2))))
        val draft = listOf(WEEKDAYS, WEEKEND.copy(weekdays = days(SATURDAY)), added)
        val changes = diffSchedules(SAMPLE, draft)
        assertTrue(TouChange.StrategyAdded("Chiến lược C") in changes)
        assertTrue(TouChange.SlotAdded("Chiến lược C", SlotKind.Charge, 1, charge(t(1), t(2))) in changes)

        val removed = diffSchedules(SAMPLE, listOf(WEEKDAYS))
        assertEquals(listOf(TouChange.StrategyRemoved("Cuối tuần", days(SATURDAY, SUNDAY))), removed)
    }

    @Test fun `chan luu khi chien luoc khong co ngay`() {
        val noDays = SAMPLE.toggleDay("b", SATURDAY).toggleDay("b", SUNDAY)
        assertEquals(listOf("b"), saveBlockers(noDays).map { it.id })
        assertFalse(canSave(SAMPLE, noDays))
        // Chuyển hết ngày của "Cuối tuần" sang "Ngày thường" cũng làm "Cuối tuần" trống → chặn.
        val moved = SAMPLE.toggleDay("a", SATURDAY).toggleDay("a", SUNDAY)
        assertFalse(canSave(SAMPLE, moved))
    }
}
