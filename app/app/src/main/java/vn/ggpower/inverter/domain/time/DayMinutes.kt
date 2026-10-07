package vn.ggpower.inverter.domain.time

import java.time.LocalTime

/** Số phút trong một ngày; mốc 24:00 = [MINUTES_PER_DAY]. */
const val MINUTES_PER_DAY = 24 * 60

/** Phút tính từ 00:00: 06:30 → 390. */
fun LocalTime.minuteOfDay(): Int = hour * 60 + minute

/**
 * Một đoạn liên tục trong ngày, nửa mở `[start, end)` tính bằng phút, `0 ≤ start < end ≤ 1440`.
 */
data class DaySpan(val start: Int, val end: Int) {
    init {
        require(start in 0 until MINUTES_PER_DAY) { "start ngoài 0–1439: $start" }
        require(end in (start + 1)..MINUTES_PER_DAY) { "end phải trong (start, 1440]: $start–$end" }
    }

    val length: Int get() = end - start
}

/**
 * Khung giờ `start → end` vẽ trên thanh 24h. Khung qua nửa đêm (end < start, ví dụ 22:00 → 06:00)
 * tách thành hai đoạn `[22:00, 24:00)` và `[00:00, 06:00)`. start = end → không có đoạn nào
 * (khung không hợp lệ, bị chặn ở kiểm tra "Giờ kết thúc phải khác giờ bắt đầu").
 */
fun spansOf(start: LocalTime, end: LocalTime): List<DaySpan> {
    val s = start.minuteOfDay()
    val e = end.minuteOfDay()
    return when {
        s == e -> emptyList()
        s < e -> listOf(DaySpan(s, e))
        e == 0 -> listOf(DaySpan(s, MINUTES_PER_DAY))
        else -> listOf(DaySpan(s, MINUTES_PER_DAY), DaySpan(0, e))
    }
}
