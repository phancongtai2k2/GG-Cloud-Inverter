package vn.ggpower.inverter.domain.tou

import java.time.LocalTime

/** Dữ liệu người dùng gõ trong popup thêm / sửa khung (chuỗi thô, công suất có thể có dấu chấm nghìn "3.000"). */
data class SlotInput(
    val kind: SlotKind,
    val start: String,
    val end: String,
    val stopSoc: String,
    val maxPower: String,
)

/** Lỗi của popup, theo đúng thứ tự kiểm tra của Thay đổi 0.6 §5 (thông báo dựng ở tầng UI). */
sealed interface SlotError {
    /** 1 · "Đã đủ 3 khung sạc / xả. Hãy sửa hoặc xoá một khung có sẵn." */
    data class LimitReached(val kind: SlotKind) : SlotError
    /** 2 · "Giờ phải có dạng hh:mm, ví dụ 01:30." */
    data object TimeFormat : SlotError
    /** 3 · "Giờ kết thúc phải khác giờ bắt đầu." */
    data object SameStartEnd : SlotError
    /** 4 · "SOC dừng phải trong khoảng 0–100 %." */
    data object SocRange : SlotError
    /** 5 · "Công suất tối đa phải trong khoảng 0–12.000 W." */
    data object PowerRange : SlotError
    /** 6 · "Trùng với Xả 1 (09:00 – 12:00). Các khung không được chồng lấn." */
    data class Overlap(val kind: SlotKind, val number: Int, val start: LocalTime, val end: LocalTime) : SlotError
}

sealed interface SlotValidation {
    data class Valid(val slot: TouSlot) : SlotValidation
    data class Invalid(val error: SlotError) : SlotValidation
}

const val MAX_POWER_W = 12_000

private val TIME = Regex("^([01]\\d|2[0-3]):([0-5]\\d)$")
private val SOC = Regex("^\\d{1,3}$")
/** Chữ số và dấu nghìn ("3.000" tiếng Việt, "3,000" tiếng Anh). */
private val POWER = Regex("^[\\d.,]+$")

/**
 * Kiểm tra khung [input] trong chiến lược [strategy]. [editingIndex]: chỉ số trong [TouStrategy.slots] của khung đang
 * sửa (null = thêm mới). Kiểm tra lần lượt, gặp lỗi đầu tiên thì dừng (popup chỉ hiện một hộp lỗi).
 */
fun validateSlot(input: SlotInput, strategy: TouStrategy, editingIndex: Int?): SlotValidation {
    val sameKind = strategy.slots.withIndex().count { (i, s) -> s.kind == input.kind && i != editingIndex }
    if (sameKind >= MAX_SLOTS_PER_TYPE) return SlotValidation.Invalid(SlotError.LimitReached(input.kind))

    val start = parseTime(input.start)
    val end = parseTime(input.end)
    if (start == null || end == null) return SlotValidation.Invalid(SlotError.TimeFormat)
    if (start == end) return SlotValidation.Invalid(SlotError.SameStartEnd)

    val soc = input.stopSoc.trim()
    if (!SOC.matches(soc) || soc.toInt() > 100) return SlotValidation.Invalid(SlotError.SocRange)

    val powerText = input.maxPower.trim()
    val power = if (POWER.matches(powerText)) powerText.filter { it.isDigit() }.toIntOrNull() else null
    if (power == null || power > MAX_POWER_W) return SlotValidation.Invalid(SlotError.PowerRange)

    val slot = TouSlot(input.kind, start, end, soc.toInt(), power)
    // Duyệt như prototype: Sạc 1, Sạc 2…, rồi Xả 1, Xả 2…; báo khung trùng đầu tiên.
    val order = strategy.slots.indices.sortedWith(compareBy({ strategy.slots[it].kind.ordinal }, { strategy.numberOf(it) }))
    order.forEach { i ->
        val other = strategy.slots[i]
        if (i != editingIndex && slot.overlaps(other)) {
            return SlotValidation.Invalid(SlotError.Overlap(other.kind, strategy.numberOf(i), other.start, other.end))
        }
    }
    return SlotValidation.Valid(slot)
}

/** "hh:mm" 24 giờ, đủ hai chữ số: "01:30" hợp lệ, "1:30" / "24:00" / "12:60" không. */
fun parseTime(text: String): LocalTime? {
    val m = TIME.matchEntire(text.trim()) ?: return null
    return LocalTime.of(m.groupValues[1].toInt(), m.groupValues[2].toInt())
}

// ---------- Popup thêm / sửa khung (Thay đổi 0.6 §5) ----------

/** Điền sẵn popup từ khung có sẵn hoặc khung gợi ý; công suất có dấu nghìn theo ngôn ngữ app ("3.000" / "3,000"). */
fun TouSlot.toInput(): SlotInput = SlotInput(
    kind = kind,
    start = vn.ggpower.inverter.domain.format.GgFormat.time(start),
    end = vn.ggpower.inverter.domain.format.GgFormat.time(end),
    stopSoc = stopSocPercent.toString(),
    maxPower = vn.ggpower.inverter.domain.format.GgFormat.number(maxPowerW.toLong()),
)

/**
 * Chuẩn hoá chữ gõ vào ô giờ (bàn phím số không có dấu ":"): chỉ giữ chữ số và ":", tối đa 5 ký tự; gõ liền 3–4 chữ
 * số thì tự chèn ":" sau 2 chữ số đầu ("2200" → "22:00"). Không tự sửa giá trị sai — kiểm tra 2 báo lỗi.
 */
fun normalizeTimeInput(text: String): String {
    val kept = text.filter { it.isDigit() || it == ':' }
    val withColon = if (':' !in kept && kept.length > 2) kept.take(2) + ":" + kept.drop(2) else kept
    return withColon.take(5)
}

/** Loại khung mặc định khi bấm "Thêm khung sạc hoặc xả": sạc, trừ khi sạc đã đủ mà xả còn chỗ (TouStrategy.dc.html). */
fun TouStrategy.defaultAddKind(): SlotKind {
    val chargeFull = slotsOf(SlotKind.Charge).size >= MAX_SLOTS_PER_TYPE
    val dischargeFull = slotsOf(SlotKind.Discharge).size >= MAX_SLOTS_PER_TYPE
    return if (chargeFull && !dischargeFull) SlotKind.Discharge else SlotKind.Charge
}

/**
 * Chuẩn hoá ô công suất ngay khi gõ (quyết 02/10/2026, thay cho "định dạng lại khi lưu" của Thay đổi 0.6 §5): chỉ giữ
 * chữ số, tối đa 6 chữ số (đủ để gõ quá 12.000 và thấy lỗi 5), bỏ số 0 thừa ở đầu, chèn dấu nghìn theo ngôn ngữ app ("3500" → "3.500", tiếng Anh "3,500").
 */
fun normalizePowerInput(text: String): String {
    val digits = text.filter { it.isDigit() }.take(6)
    if (digits.isEmpty()) return ""
    return vn.ggpower.inverter.domain.format.GgFormat.number(digits.toLong())
}
