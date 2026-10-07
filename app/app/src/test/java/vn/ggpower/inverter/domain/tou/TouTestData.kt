package vn.ggpower.inverter.domain.tou

import java.time.DayOfWeek
import java.time.DayOfWeek.FRIDAY
import java.time.DayOfWeek.MONDAY
import java.time.DayOfWeek.SATURDAY
import java.time.DayOfWeek.SUNDAY
import java.time.DayOfWeek.THURSDAY
import java.time.DayOfWeek.TUESDAY
import java.time.DayOfWeek.WEDNESDAY
import java.time.LocalTime

internal fun t(h: Int, m: Int = 0): LocalTime = LocalTime.of(h, m)
internal fun charge(s: LocalTime, e: LocalTime, soc: Int = 90, w: Int = 3000) = TouSlot(SlotKind.Charge, s, e, soc, w)
internal fun discharge(s: LocalTime, e: LocalTime, soc: Int = 20, w: Int = 4000) = TouSlot(SlotKind.Discharge, s, e, soc, w)

/** Dữ liệu mẫu Thay đổi 0.6 §3 (hôm nay T5 24/09/2026). */
internal val WEEKDAYS = TouStrategy(
    id = "a", name = "Ngày thường",
    weekdays = setOf(MONDAY, TUESDAY, WEDNESDAY, THURSDAY, FRIDAY),
    slots = listOf(
        charge(t(22), t(6), 90, 3000),
        charge(t(13), t(16), 100, 3000),
        discharge(t(9), t(12), 50, 4000),
        discharge(t(18), t(21), 20, 5000),
    ),
)

internal val WEEKEND = TouStrategy(
    id = "b", name = "Cuối tuần",
    weekdays = setOf(SATURDAY, SUNDAY),
    slots = listOf(
        charge(t(0), t(6), 90, 3000),
        charge(t(14), t(16), 100, 3000),
        discharge(t(10), t(12), 60, 4000),
        discharge(t(18), t(23), 20, 5000),
    ),
)

internal val SAMPLE: List<TouStrategy> = listOf(WEEKDAYS, WEEKEND)

internal fun days(vararg d: DayOfWeek) = d.toSet()
