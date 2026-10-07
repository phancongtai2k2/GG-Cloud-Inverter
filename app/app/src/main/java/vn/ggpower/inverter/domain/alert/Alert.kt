package vn.ggpower.inverter.domain.alert

import java.time.LocalDateTime

/** Mức độ cảnh báo (đặc tả §3.10): Lỗi đỏ, Cảnh báo vàng, Thông tin xanh dương. */
enum class AlertSeverity { Error, Warning, Info }

/**
 * Một cảnh báo của dự án (màn 10). [resolved]: "Đã xử lý" — người dùng đánh dấu hoặc backend tự chuyển khi sự cố tự hết.
 */
data class AlertSummary(
    val id: String,
    val severity: AlertSeverity,
    val title: String,
    val description: String,
    val startedAt: LocalDateTime,
    val resolved: Boolean,
)

/** Bộ lọc màn 10: Tất cả · Chưa xử lý · Đã xử lý. */
enum class AlertFilter { All, Open, Resolved }

fun List<AlertSummary>.filterBy(filter: AlertFilter): List<AlertSummary> = when (filter) {
    AlertFilter.All -> this
    AlertFilter.Open -> filter { !it.resolved }
    AlertFilter.Resolved -> filter { it.resolved }
}

/** Số cảnh báo của từng bộ lọc, cho nhãn "Tất cả (4)", "Chưa xử lý (2)"… */
fun List<AlertSummary>.counts(): Map<AlertFilter, Int> = AlertFilter.entries.associateWith { filterBy(it).size }

/** Số cảnh báo chưa xử lý — chấm đỏ trên chuông Màn hình chính (quyết 02/10/2026). */
fun List<AlertSummary>.unresolvedCount(): Int = count { !it.resolved }
