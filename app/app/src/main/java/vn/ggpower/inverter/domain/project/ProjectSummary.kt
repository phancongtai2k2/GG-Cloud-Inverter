package vn.ggpower.inverter.domain.project

import java.text.Normalizer

/** Loại công trình, quyết định icon trên thẻ dự án (đặc tả §3.14 bước 2). */
enum class SiteType { Home, Factory }

/**
 * Trạng thái dự án (đặc tả §3.2): Online (xanh), Cảnh báo (vàng); bổ sung khi phát triển: Offline, Lỗi (đỏ);
 * Đang chờ kết nối (§3.14: thiết bị hợp lệ nhưng chưa gửi gói dữ liệu đầu tiên).
 */
enum class ProjectStatus { Online, Warning, Offline, Error, WaitingConnection }

/**
 * Một thẻ ở màn 2 · Dự án. Chỉ số nhanh có thể null khi chưa có dữ liệu → hiển thị "—", không hiển thị 0
 * (đặc tả §17.5).
 */
data class ProjectSummary(
    val id: String,
    val name: String,
    val address: String,
    /** Ví dụ "Inverter hybrid 5 kW · S16 Pre". */
    val configuration: String,
    val siteType: SiteType,
    val status: ProjectStatus,
    val pvPowerKw: Double?,
    val todayEnergyKwh: Double?,
    val batterySocPercent: Int?,
)

/**
 * Lọc theo ô "Tìm theo tên hoặc địa chỉ": không phân biệt hoa thường và dấu tiếng Việt ("an thi" khớp "Nhà Ân Thi"),
 * bỏ khoảng trắng thừa; mọi từ trong câu tìm phải xuất hiện trong tên hoặc địa chỉ. Câu tìm rỗng → giữ nguyên.
 */
fun List<ProjectSummary>.search(query: String): List<ProjectSummary> {
    val words = foldVietnamese(query).split(' ').filter { it.isNotEmpty() }
    if (words.isEmpty()) return this
    return filter { p ->
        val haystack = foldVietnamese(p.name + " " + p.address)
        words.all { it in haystack }
    }
}

private val COMBINING_MARKS = Regex("\\p{Mn}+")

/** "Nhà Ân Thi, Đường" → "nha an thi, duong". */
internal fun foldVietnamese(text: String): String =
    Normalizer.normalize(text.trim().lowercase(), Normalizer.Form.NFD)
        .replace(COMBINING_MARKS, "")
        .replace('đ', 'd')
        .replace(Regex("\\s+"), " ")
