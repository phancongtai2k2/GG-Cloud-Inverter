package vn.ggpower.inverter.domain.tou

import vn.ggpower.inverter.domain.time.MINUTES_PER_DAY
import vn.ggpower.inverter.domain.time.minuteOfDay
import vn.ggpower.inverter.domain.time.spansOf
import java.time.DayOfWeek
import java.time.LocalTime

/** Tối đa 3 khung mỗi loại (ADR-002 mục 1; phần cứng SRNE E026–E032). */
const val MAX_SLOTS_PER_TYPE = 3

/** Tối đa 4 chiến lược (Thay đổi 0.6 §3). */
const val MAX_STRATEGIES = 4

enum class SlotKind { Charge, Discharge }

/**
 * Một khung sạc hoặc xả. Khung có thể qua nửa đêm (end < start). Khung sạc luôn lấy điện từ lưới
 * (Thay đổi 0.6 §5, MAU_THUAN M11) nên không có trường nguồn sạc.
 */
data class TouSlot(
    val kind: SlotKind,
    val start: LocalTime,
    val end: LocalTime,
    val stopSocPercent: Int,
    val maxPowerW: Int,
) {
    /** Thời lượng tính bằng phút, khung qua nửa đêm tính vòng qua 24:00. */
    val durationMinutes: Int
        get() = (end.minuteOfDay() - start.minuteOfDay() + MINUTES_PER_DAY) % MINUTES_PER_DAY

    /** "qua đêm" như prototype: giờ kết thúc nhỏ hơn giờ bắt đầu (kể cả kết thúc đúng 00:00). */
    val overnight: Boolean get() = end < start

    /** Hai khung chồng lấn nếu có đoạn chung; chạm mép (05:00 và 05:00) không tính (Thay đổi 0.6 §5). */
    fun overlaps(other: TouSlot): Boolean {
        val mine = spansOf(start, end)
        val theirs = spansOf(other.start, other.end)
        return mine.any { a -> theirs.any { b -> a.start < b.end && b.start < a.end } }
    }
}

/**
 * Chiến lược = một bộ khung sạc / xả + tập thứ áp dụng (Thay đổi 0.6 §3). [slots] giữ thứ tự thêm vào;
 * khung được đánh số liên tục theo loại: Sạc 1, Sạc 2…, Xả 1, Xả 2… (Thay đổi 0.6 §4).
 */
data class TouStrategy(
    val id: String,
    val name: String,
    val weekdays: Set<DayOfWeek>,
    val slots: List<TouSlot>,
) {
    fun slotsOf(kind: SlotKind): List<TouSlot> = slots.filter { it.kind == kind }

    /** Số thứ tự hiển thị (1-based) của khung [index] trong [slots] theo loại của nó. */
    fun numberOf(index: Int): Int {
        val kind = slots[index].kind
        return slots.take(index + 1).count { it.kind == kind }
    }

    /** Chỉ số trong [slots] của khung "[kind] [number]" (1-based); -1 nếu không có. */
    fun indexOf(kind: SlotKind, number: Int): Int {
        var n = 0
        slots.forEachIndexed { i, s -> if (s.kind == kind && ++n == number) return i }
        return -1
    }
}
