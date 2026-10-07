package vn.ggpower.inverter.domain.project

import vn.ggpower.inverter.domain.format.GgFormat
import vn.ggpower.inverter.domain.format.NumberStyle
import java.time.LocalDate

/** Toạ độ công trình (nút định vị ở 14b; đặc tả §3.14: tra cứu thời tiết và bức xạ). */
data class Coordinates(val latitude: Double, val longitude: Double)

/** Múi giờ mặc định (đặc tả §3.14 bước 2). */
const val DEFAULT_TIME_ZONE = "Asia/Ho_Chi_Minh"

/** Danh sách chọn múi giờ ở 14b (BO_SUNG §22): mặc định Việt Nam, kèm các múi giờ lân cận. */
val PROJECT_TIME_ZONES = listOf(
    DEFAULT_TIME_ZONE, "Asia/Bangkok", "Asia/Phnom_Penh", "Asia/Vientiane", "Asia/Jakarta", "Asia/Singapore", "Asia/Kuala_Lumpur",
)

/** Tên dự án tối đa (BO_SUNG §22). */
const val PROJECT_NAME_MAX_LENGTH = 40

/** Dữ liệu nhập ở 14b. [pvKwp] giữ nguyên chữ người dùng gõ ("6,5"). */
data class ProjectInfoInput(
    val name: String,
    val siteType: SiteType = SiteType.Home,
    val address: String = "",
    val coordinates: Coordinates? = null,
    val timeZone: String = DEFAULT_TIME_ZONE,
    val installedOn: LocalDate,
    val pvKwp: String = "",
    val photoUri: String? = null,
)

/** Lỗi dưới ô của 14b (BO_SUNG §22). */
enum class ProjectInfoError { NameEmpty, NameTooLong, AddressEmpty, KwpEmpty, KwpInvalid, DateInFuture }

data class ProjectInfoCheck(
    val nameError: ProjectInfoError? = null,
    val addressError: ProjectInfoError? = null,
    val dateError: ProjectInfoError? = null,
    val kwpError: ProjectInfoError? = null,
    val kwp: Double? = null,
) {
    val valid: Boolean get() = nameError == null && addressError == null && dateError == null && kwpError == null
}

/**
 * Số thập phân của ô kWp: dấu phẩy hoặc chấm đều là dấu thập phân (ô không có dấu nghìn); null nếu không phải số.
 * "6,5" / "6.5" → 6.5.
 */
fun parseDecimal(text: String): Double? {
    val t = text.trim().replace(',', '.')
    if (t.isEmpty() || t.count { it == '.' } > 1 || !t.all { it.isDigit() || it == '.' } || t == ".") return null
    return t.toDoubleOrNull()
}

/**
 * Ô kWp khi gõ: chỉ giữ chữ số và một dấu thập phân của [style] (gõ phẩy hay chấm đều đổi sang dấu đó), tối đa 5 chữ
 * số phần nguyên và 2 chữ số lẻ. Tiếng Việt: "6.5" → "6,5"; "12a,345" → "12,34". Tiếng Anh: "6,5" → "6.5".
 */
fun normalizeKwpInput(text: String, style: NumberStyle = GgFormat.defaultStyle): String {
    val sb = StringBuilder()
    var comma = false
    var intDigits = 0
    var fracDigits = 0
    for (ch in text) {
        when {
            ch.isDigit() && !comma && intDigits < 5 -> { sb.append(ch); intDigits++ }
            ch.isDigit() && comma && fracDigits < 2 -> { sb.append(ch); fracDigits++ }
            (ch == ',' || ch == '.') && !comma && intDigits > 0 -> { sb.append(style.decimalSeparator); comma = true }
        }
    }
    return sb.toString()
}

/**
 * Kiểm tra 14b (đặc tả §3.14 bước 2): tên không trống, tối đa [PROJECT_NAME_MAX_LENGTH] ký tự; địa chỉ không trống;
 * ngày lắp đặt không sau [today]; công suất PV là số lớn hơn 0.
 */
fun checkProjectInfo(input: ProjectInfoInput, today: LocalDate): ProjectInfoCheck {
    val kwp = parseDecimal(input.pvKwp)
    return ProjectInfoCheck(
        nameError = when {
            input.name.isBlank() -> ProjectInfoError.NameEmpty
            input.name.trim().length > PROJECT_NAME_MAX_LENGTH -> ProjectInfoError.NameTooLong
            else -> null
        },
        addressError = if (input.address.isBlank()) ProjectInfoError.AddressEmpty else null,
        dateError = if (input.installedOn.isAfter(today)) ProjectInfoError.DateInFuture else null,
        kwpError = when {
            input.pvKwp.isBlank() -> ProjectInfoError.KwpEmpty
            kwp == null || kwp <= 0.0 -> ProjectInfoError.KwpInvalid
            else -> null
        },
        kwp = kwp?.takeIf { it > 0.0 },
    )
}
