package vn.ggpower.inverter.domain.warranty

import java.time.LocalDate
import java.time.Period
import java.time.temporal.ChronoUnit

/**
 * Còn từ bấy nhiêu ngày trở xuống thì chip "Sắp hết hạn" (đặc tả §3.9 bảng tình trạng). Đề xuất 90 ngày,
 * **chưa chốt** (đặc tả mục 7 điểm 4) — đổi ở đây khi GG Power xác nhận.
 */
const val WARRANTY_SOON_DAYS = 90L

/** Tình trạng bảo hành (đặc tả §3.9): Còn bảo hành (xanh) · Sắp hết hạn (vàng) · Hết bảo hành (đỏ). */
enum class WarrantyStatus { Active, ExpiringSoon, Expired }

/** Thời hạn gói, ví dụ "Bảo hành 5 năm". */
data class WarrantyTerm(val years: Int, val months: Int)

/** Chữ "thời gian còn lại" dưới thanh (Warranty.dc.html: "Còn 3 năm 6 tháng", "Còn 46 ngày"). */
sealed interface WarrantyRemaining {
    /** Còn trên [WARRANTY_SOON_DAYS] ngày: năm + tháng, tháng làm tròn gần nhất (dư ≥ 15 ngày → thêm 1 tháng; chốt 03/10/2026). */
    data class YearsMonths(val years: Int, val months: Int) : WarrantyRemaining
    /** Còn 1…[WARRANTY_SOON_DAYS] ngày. */
    data class Days(val days: Long) : WarrantyRemaining
    /** Đúng ngày hết hạn: bảo hành còn hiệu lực đến hết ngày → "Hết hạn hôm nay", chip Sắp hết hạn (chốt 03/10/2026). */
    data object EndsToday : WarrantyRemaining
    /** Đã qua ngày hết hạn → "Đã hết hạn". */
    data object Expired : WarrantyRemaining
}

/** Bảo hành một thiết bị của dự án (màn 9). Mốc tính là ngày kích hoạt; hạn là ngày cuối còn bảo hành. */
data class DeviceWarranty(
    val name: String,
    val serial: String,
    val activatedOn: LocalDate,
    val expiresOn: LocalDate,
) {
    fun daysLeft(today: LocalDate): Long = ChronoUnit.DAYS.between(today, expiresOn)

    fun status(today: LocalDate): WarrantyStatus {
        val left = daysLeft(today)
        return when {
            left < 0 -> WarrantyStatus.Expired
            left <= WARRANTY_SOON_DAYS -> WarrantyStatus.ExpiringSoon
            else -> WarrantyStatus.Active
        }
    }

    /** Tỉ lệ thời gian bảo hành đã dùng, kẹp trong 0…1 (trước ngày kích hoạt = 0, sau hạn = 1). */
    fun usedFraction(today: LocalDate): Float {
        val total = ChronoUnit.DAYS.between(activatedOn, expiresOn)
        if (total <= 0) return 1f
        val used = ChronoUnit.DAYS.between(activatedOn, today)
        return (used.toFloat() / total).coerceIn(0f, 1f)
    }

    /** Phần trăm đã dùng (làm tròn) cho mô tả trợ năng "Thời gian bảo hành đã dùng 30 %". */
    fun usedPercent(today: LocalDate): Int = Math.round(usedFraction(today) * 100)

    val term: WarrantyTerm
        get() = Period.between(activatedOn, expiresOn).let { WarrantyTerm(it.years, it.months) }

    fun remaining(today: LocalDate): WarrantyRemaining {
        val left = daysLeft(today)
        return when {
            left < 0 -> WarrantyRemaining.Expired
            left == 0L -> WarrantyRemaining.EndsToday
            left <= WARRANTY_SOON_DAYS -> WarrantyRemaining.Days(left)
            else -> {
                val p = Period.between(today, expiresOn)
                var years = p.years
                var months = p.months + if (p.days >= ROUND_UP_DAYS) 1 else 0
                if (months == 12) { years += 1; months = 0 }
                WarrantyRemaining.YearsMonths(years, months)
            }
        }
    }

    private companion object {
        const val ROUND_UP_DAYS = 15
    }
}

/** Loại việc trong "Lịch sử bảo hành": sửa chữa theo phiếu, hoặc cập nhật firmware. */
sealed interface WarrantyWork {
    /** "Pin S16 Pre · phiếu #BH-2606-0012". */
    data class Repair(val ticketCode: String) : WarrantyWork
    /** "Inverter hybrid 5 kW · v1.3.1 → v1.3.2". */
    data class Firmware(val fromVersion: String, val toVersion: String) : WarrantyWork
}

/** Hình thức thực hiện: "kỹ thuật viên tại nhà" / "từ xa". */
enum class WarrantyChannel { OnSite, Remote }

/** Trạng thái lượt bảo hành. Prototype chỉ có "Hoàn tất"; các trạng thái khác chờ hợp đồng API (OPEN_QUESTIONS 03/10/2026). */
enum class WarrantyEntryStatus { Completed }

/** Một dòng "Lịch sử bảo hành" (Warranty.dc.html). */
data class WarrantyHistoryEntry(
    val title: String,
    val device: String,
    val work: WarrantyWork,
    val date: LocalDate,
    val channel: WarrantyChannel,
    val status: WarrantyEntryStatus,
)

/** Dữ liệu màn 9 của một dự án. */
data class ProjectWarranty(
    val projectName: String,
    val devices: List<DeviceWarranty>,
    val history: List<WarrantyHistoryEntry>,
)
