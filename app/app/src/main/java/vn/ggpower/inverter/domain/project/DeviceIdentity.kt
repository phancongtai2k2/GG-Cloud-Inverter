package vn.ggpower.inverter.domain.project

import java.time.LocalDate

/**
 * Số serial nhập tay ở 14a: bỏ khoảng trắng (kể cả ở giữa, khi chép từ tem) và viết hoa, vì tem in chữ hoa
 * (đặc tả §3.18 "toàn bộ viết hoa, không dấu cách").
 */
fun normalizeSerial(raw: String): String = raw.filterNot { it.isWhitespace() }.uppercase()

/** Thông tin thiết bị backend trả về khi serial hợp lệ (đặc tả §3.14 bước 1: model, công suất định mức, ngày xuất xưởng). */
data class DeviceIdentity(
    val serial: String,
    val name: String,
    val model: String,
    val ratedPowerKw: Double,
    val manufacturedOn: LocalDate,
)
