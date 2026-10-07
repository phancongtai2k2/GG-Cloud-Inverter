package vn.ggpower.inverter.domain.project

import vn.ggpower.inverter.domain.format.GgFormat

/** Cách tính giá mua điện (đặc tả §3.14 bước 3). */
enum class TariffKind { EvnTiered, Fixed }

/**
 * Một bậc giá EVN sinh hoạt: từ [fromKwh] tới [toKwh] (null: không giới hạn trên). [pricePerKwh] null khi chưa có số
 * (app hiện "[GIÁ]" như prototype, chờ Phòng EMS cung cấp — OPEN_QUESTIONS).
 */
data class EvnTier(val index: Int, val fromKwh: Int, val toKwh: Int?, val pricePerKwh: Long? = null)

/** Sáu bậc EVN sinh hoạt (AddProjectTariff.dc.html "6 bậc"). */
val EVN_RESIDENTIAL_TIERS = listOf(
    EvnTier(1, 0, 50), EvnTier(2, 51, 100), EvnTier(3, 101, 200),
    EvnTier(4, 201, 300), EvnTier(5, 301, 400), EvnTier(6, 401, null),
)

/** Dữ liệu nhập ở 14c. Ô tiền giữ chữ đã định dạng ("2.500"). */
data class TariffInput(
    val kind: TariffKind = TariffKind.EvnTiered,
    val fixedPrice: String = "",
    val sellPrice: String = "0",
)

/** Giá điện gửi backend khi tạo dự án. */
data class Tariff(val kind: TariffKind, val fixedPerKwh: Long?, val sellPerKwh: Long)

/** Lỗi ô "Giá cố định" (BO_SUNG §23). */
enum class TariffError { FixedEmpty, FixedZero }

data class TariffCheck(val fixedError: TariffError?, val tariff: Tariff?)

/** Số tiền nguyên từ chữ có dấu nghìn ("2.500" / "2,500" → 2500); null nếu trống. */
fun parseMoney(text: String): Long? = text.filter { it.isDigit() }.takeIf { it.isNotEmpty() }?.toLongOrNull()

/** Ô tiền khi gõ: chỉ giữ chữ số (tối đa 7), chèn dấu nghìn theo ngôn ngữ app. "2500" → "2.500" (tiếng Anh "2,500"). */
fun normalizeMoneyInput(text: String): String {
    val digits = text.filter { it.isDigit() }.trimStart('0').take(7)
    if (digits.isEmpty()) return if (text.any { it.isDigit() }) "0" else ""
    return GgFormat.number(digits.toLong())
}

/**
 * Kiểm tra 14c: giá cố định bắt buộc và lớn hơn 0 khi chọn "Giá cố định"; giá bán lên lưới để trống coi như 0
 * (đặc tả §3.14: mặc định 0).
 */
fun checkTariff(input: TariffInput): TariffCheck {
    val sell = parseMoney(input.sellPrice) ?: 0L
    if (input.kind == TariffKind.EvnTiered) return TariffCheck(null, Tariff(TariffKind.EvnTiered, null, sell))
    val fixed = parseMoney(input.fixedPrice)
    val error = when {
        fixed == null -> TariffError.FixedEmpty
        fixed == 0L -> TariffError.FixedZero
        else -> null
    }
    return TariffCheck(error, if (error == null) Tariff(TariffKind.Fixed, fixed, sell) else null)
}
