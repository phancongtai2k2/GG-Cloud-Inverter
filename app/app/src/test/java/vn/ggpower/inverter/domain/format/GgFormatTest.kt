package vn.ggpower.inverter.domain.format

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.DayOfWeek.FRIDAY
import java.time.DayOfWeek.MONDAY
import java.time.DayOfWeek.SATURDAY
import java.time.DayOfWeek.SUNDAY
import java.time.DayOfWeek.THURSDAY
import java.time.DayOfWeek.TUESDAY
import java.time.DayOfWeek.WEDNESDAY
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

class GgFormatTest {

    @Test fun `thap phan dau phay`() {
        assertEquals("4,2", GgFormat.number(4.2, 1))
        assertEquals("18,6", GgFormat.number(18.6, 1))
        assertEquals("0,0", GgFormat.number(0.0, 1))
        assertEquals("5,50", GgFormat.number(5.5, 2))
    }

    @Test fun `nghin dau cham`() {
        assertEquals("3.000", GgFormat.number(3000.0))
        assertEquals("1.273.480", GgFormat.number(1_273_480L))
        assertEquals("12.000", GgFormat.number(12_000L))
        assertEquals("999", GgFormat.number(999L))
        assertEquals("1.000", GgFormat.number(1000L))
    }

    @Test fun `lam tron nua len va khong co am 0`() {
        assertEquals("4,3", GgFormat.number(4.25, 1))
        assertEquals("0,0", GgFormat.number(-0.04, 1))
        assertEquals("-2,5", GgFormat.number(-2.5, 1))
        assertEquals("-1.200", GgFormat.number(-1200L))
    }

    @Test fun `so rut gon cho truc`() {
        assertEquals("2,5", GgFormat.trimmed(2.5))
        assertEquals("1,25", GgFormat.trimmed(1.25))
        assertEquals("0", GgFormat.trimmed(0.0))
        assertEquals("5", GgFormat.trimmed(5.0))
        assertEquals("25", GgFormat.trimmed(25.0))
        assertEquals("1.000", GgFormat.trimmed(1000.0))
        assertEquals("-2", GgFormat.trimmed(-2.0))
        assertEquals("1,8", GgFormat.trimmed(1.8))
    }

    @Test fun `tu doi don vi theo do lon, bang 4 8 cua dac ta`() {
        assertEquals("14,2 kWh", GgFormat.energy(14.2, "kWh", "MWh"))
        assertEquals("435 kWh", GgFormat.energy(435.0, "kWh", "MWh"))
        assertEquals("5,50 MWh", GgFormat.energy(5500.0, "kWh", "MWh"))
        assertEquals("10,4 kg", GgFormat.mass(10.4, "kg", "t"))
        assertEquals("274 kg", GgFormat.mass(274.0, "kg", "t"))
        assertEquals("3,24 t", GgFormat.mass(3240.0, "kg", "t"))
        assertEquals("15.043.600 đ", GgFormat.money(15_043_600L, "đ"))
    }

    @Test fun `kieu tieng anh nguoc dau`() {
        assertEquals("1,273.5", GgFormat.number(1273.5, 1, NumberStyle.EN))
    }

    @Test fun `khoang trang truoc don vi`() {
        assertEquals("4,2 kW", GgFormat.withUnit(4.2, 1, "kW"))
        assertEquals("76 %", GgFormat.withUnit(76L, "%"))
        assertEquals("3.000 W", GgFormat.withUnit(3000L, "W"))
        assertEquals("48.000 đ", GgFormat.money(48_000L, "đ"))
    }

    @Test fun `co dau cho cong suat pin`() {
        assertEquals("+1,1 kW", GgFormat.signedWithUnit(1.1, 1, "kW"))
        assertEquals("-0,8 kW", GgFormat.signedWithUnit(-0.8, 1, "kW"))
        assertEquals("0,0 kW", GgFormat.signedWithUnit(0.0, 1, "kW"))
        assertEquals("0,0 kW", GgFormat.signedWithUnit(0.04, 1, "kW"))
    }

    @Test fun `ngay va thoi diem`() {
        val t = LocalDateTime.of(2026, 9, 24, 7, 12)
        assertEquals("24/09/2026", GgFormat.date(LocalDate.of(2026, 9, 24)))
        assertEquals("24/09 07:12", GgFormat.dateTimeShort(t))
        assertEquals("07:12 · 24/09/2026", GgFormat.dateTimeFull(t))
        assertEquals("24/09/2026 07:12", GgFormat.dateTimeLog(t))
        assertEquals("22:00–06:00", GgFormat.timeRangeCompact(LocalTime.of(22, 0), LocalTime.of(6, 0)))
        assertEquals("09:05", GgFormat.time(LocalTime.of(9, 5)))
    }

    @Test fun `thoi luong`() {
        assertEquals("3 giờ", GgFormat.duration(180, "giờ", "phút"))
        assertEquals("8 giờ 30 phút", GgFormat.duration(510, "giờ", "phút"))
        assertEquals("45 phút", GgFormat.duration(45, "giờ", "phút"))
    }

    @Test fun `khung gio`() {
        assertEquals("22:00 – 06:00", GgFormat.timeRange(LocalTime.of(22, 0), LocalTime.of(6, 0)))
        assertEquals("00:00 – 23:59", GgFormat.timeRange(LocalTime.MIDNIGHT, LocalTime.of(23, 59)))
    }

    @Test fun `thu viet tat`() {
        assertEquals("T2", GgFormat.weekdayShort(MONDAY))
        assertEquals("T7", GgFormat.weekdayShort(SATURDAY))
        assertEquals("CN", GgFormat.weekdayShort(SUNDAY))
    }

    @Test fun `danh sach thu theo thu tu va hang ngay`() {
        assertEquals("T2, T3, T4, T5, T6", GgFormat.weekdays(setOf(FRIDAY, MONDAY, WEDNESDAY, TUESDAY, THURSDAY)))
        assertEquals("T7, CN", GgFormat.weekdays(setOf(SUNDAY, SATURDAY)))
        assertEquals("Hằng ngày", GgFormat.weekdays(DayOfWeek.entries.toSet()))
        assertEquals("", GgFormat.weekdays(emptySet()))
    }

    @Test fun `dong ho dem nguoc phut giay`() {
        assertEquals("01:00", GgFormat.countdown(60))
        assertEquals("00:45", GgFormat.countdown(45))
        assertEquals("00:00", GgFormat.countdown(-3))
    }
}
