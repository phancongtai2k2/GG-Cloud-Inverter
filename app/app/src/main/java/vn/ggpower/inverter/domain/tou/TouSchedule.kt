package vn.ggpower.inverter.domain.tou

import vn.ggpower.inverter.domain.config.OperatingMode
import vn.ggpower.inverter.domain.time.MINUTES_PER_DAY
import vn.ggpower.inverter.domain.time.minuteOfDay
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime

// ---------- Khung trong một chiến lược ----------

/** Thêm khung vào cuối danh sách (đánh số tiếp theo trong loại của nó). Không kiểm tra — gọi sau [validateSlot]. */
fun TouStrategy.withSlotAdded(slot: TouSlot): TouStrategy = copy(slots = slots + slot)

fun TouStrategy.withSlotReplaced(index: Int, slot: TouSlot): TouStrategy =
    copy(slots = slots.mapIndexed { i, s -> if (i == index) slot else s })

/**
 * Xoá khung: khung biến mất, các khung cùng loại phía sau dồn lên và được đánh số lại (Thay đổi 0.6 §4) —
 * số thứ tự luôn tính theo vị trí nên chỉ cần bỏ phần tử.
 */
fun TouStrategy.withSlotRemoved(index: Int): TouStrategy = copy(slots = slots.filterIndexed { i, _ -> i != index })

/** Giờ mặc định của Thay đổi 0.6 §5: sạc 10:00–14:00, SOC 80 %, 3.000 W; xả 06:00–08:00, SOC 40 %, 4.000 W. */
fun defaultSlot(kind: SlotKind): TouSlot = when (kind) {
    SlotKind.Charge -> TouSlot(kind, LocalTime.of(10, 0), LocalTime.of(14, 0), 80, 3000)
    SlotKind.Discharge -> TouSlot(kind, LocalTime.of(6, 0), LocalTime.of(8, 0), 40, 4000)
}

/**
 * Gợi ý khung mới (ADR-002 mục 6): mặc định §5 nếu không trùng khung nào; nếu trùng, lấy khoảng trống đầu tiên
 * đủ dài tính từ 00:00 (ứng viên: 00:00 rồi giờ kết thúc của từng khung, theo thứ tự) với thời lượng mặc định
 * (sạc 4 giờ, xả 2 giờ); không có thì thử 1 giờ; vẫn không có thì trả mặc định để kiểm tra trùng báo lỗi.
 */
fun TouStrategy.suggestSlot(kind: SlotKind): TouSlot {
    val def = defaultSlot(kind)
    if (slots.none { it.overlaps(def) }) return def
    val candidates = (listOf(0) + slots.map { it.end.minuteOfDay() }.sorted()).distinct()
    for (length in listOf(def.durationMinutes, 60)) {
        for (startMin in candidates) {
            val endMin = (startMin + length) % MINUTES_PER_DAY
            val trial = def.copy(start = minuteToTime(startMin), end = minuteToTime(endMin))
            if (slots.none { it.overlaps(trial) }) return trial
        }
    }
    return def
}

private fun minuteToTime(m: Int) = LocalTime.of(m / 60, m % 60)

// ---------- Tập chiến lược ----------

/** Chiến lược áp dụng cho [date] (mỗi thứ thuộc tối đa một chiến lược); null = chạy như Tự tiêu thụ tối đa. */
fun List<TouStrategy>.strategyFor(date: LocalDate): TouStrategy? = firstOrNull { date.dayOfWeek in it.weekdays }

fun List<TouStrategy>.canAddStrategy(): Boolean = size < MAX_STRATEGIES

/** Các thứ chưa thuộc chiến lược nào, theo thứ tự T2 → CN. */
fun List<TouStrategy>.unassignedDays(): List<DayOfWeek> = DayOfWeek.entries.filter { d -> none { d in it.weekdays } }

/**
 * Bật / tắt [day] cho chiến lược [strategyId] (Thay đổi 0.6 §4): đang chọn → bỏ; chưa chọn → chọn, và nếu thứ đó
 * đang thuộc chiến lược khác thì chuyển sang (bỏ khỏi chiến lược kia).
 */
fun List<TouStrategy>.toggleDay(strategyId: String, day: DayOfWeek): List<TouStrategy> {
    val target = firstOrNull { it.id == strategyId } ?: return this
    val selecting = day !in target.weekdays
    return map { s ->
        when {
            s.id == strategyId -> s.copy(weekdays = if (selecting) s.weekdays + day else s.weekdays - day)
            selecting && day in s.weekdays -> s.copy(weekdays = s.weekdays - day)
            else -> s
        }
    }
}

/** Các thứ chưa chọn trong [strategyId] nhưng đang thuộc chiến lược khác (cho dòng nhắc dưới chip ngày). */
fun List<TouStrategy>.daysOwnedByOthers(strategyId: String): Map<TouStrategy, List<DayOfWeek>> =
    filter { it.id != strategyId }
        .associateWith { other -> DayOfWeek.entries.filter { it in other.weekdays } }
        .filterValues { it.isNotEmpty() }

// ---------- Tóm tắt lịch (box màn 16, thẻ 16a) ----------

/** Một giai đoạn trong tóm tắt: loại, giờ, thời lượng, có qua đêm không. */
data class SchedulePeriod(val kind: SlotKind, val start: LocalTime, val end: LocalTime, val durationMinutes: Int, val overnight: Boolean)

/** "N giai đoạn: a lần sạc, b lần xả" + từng giai đoạn sắp theo giờ bắt đầu (Thay đổi 0.6 §2–3). */
data class ScheduleSummary(val periods: List<SchedulePeriod>, val chargeCount: Int, val dischargeCount: Int) {
    val isEmpty: Boolean get() = periods.isEmpty()
}

fun TouStrategy.summary(): ScheduleSummary {
    val periods = slots
        .map { SchedulePeriod(it.kind, it.start, it.end, it.durationMinutes, it.overnight) }
        .sortedBy { it.start }
    return ScheduleSummary(
        periods,
        chargeCount = slots.count { it.kind == SlotKind.Charge },
        dischargeCount = slots.count { it.kind == SlotKind.Discharge },
    )
}

// ---------- Bật / tắt lịch theo chế độ (đặc tả §16.4a) ----------

/** Giá trị E02C OnTimeChargeEn / E033 OnTimeDischgEn (true = 1). */
data class ScheduleEnableFlags(val charge: Boolean, val discharge: Boolean)

/**
 * §16.4a: chỉ khi chế độ là TOU (hoặc GG AI — cũng ghi qua nhóm thanh ghi TOU) mới bật lịch; E02C = 1 khi chiến lược áp
 * dụng hôm nay có ít nhất một khung sạc, E033 = 1 khi có ít nhất một khung xả. Ngày không thuộc chiến lược nào
 * hoặc chế độ khác → cả hai 0 (ADR-001 sửa đổi mục 4). Backend ghi; app dùng để hiển thị và kiểm tra.
 */
fun scheduleEnableFlags(mode: OperatingMode, today: TouStrategy?): ScheduleEnableFlags {
    if ((mode != OperatingMode.Tou && mode != OperatingMode.GgAi) || today == null) return ScheduleEnableFlags(false, false)
    return ScheduleEnableFlags(
        charge = today.slots.any { it.kind == SlotKind.Charge },
        discharge = today.slots.any { it.kind == SlotKind.Discharge },
    )
}

// ---------- Tên chiến lược (ADR-002 mục 5) ----------

const val MAX_STRATEGY_NAME_LENGTH = 40

enum class StrategyNameError { Empty, TooLong }

/** Tên 1–40 ký tự sau khi bỏ khoảng trắng hai đầu; null = hợp lệ. */
fun validateStrategyName(text: String): StrategyNameError? {
    val name = text.trim()
    return when {
        name.isEmpty() -> StrategyNameError.Empty
        name.length > MAX_STRATEGY_NAME_LENGTH -> StrategyNameError.TooLong
        else -> null
    }
}

/** Chữ cái của chiến lược theo vị trí trong danh sách: A, B, C, D (TouStrategy.dc.html). */
fun strategyLetter(index: Int): Char = 'A' + index

/** Chiến lược trống thêm vào cuối bản nháp (ADR-002 mục 9): chưa có ngày, chưa có khung. */
fun List<TouStrategy>.withNewStrategy(id: String, name: String): List<TouStrategy> =
    this + TouStrategy(id = id, name = name, weekdays = emptySet(), slots = emptyList())
