package vn.ggpower.inverter.domain.support

import java.time.LocalDateTime

/** Loại yêu cầu (đặc tả §3.8): bộ chọn Kỹ thuật / Tài khoản / Khác, mặc định Kỹ thuật. */
enum class TicketType { Technical, Account, Other }

/** Trạng thái phiếu (đặc tả §3.8): Đã tiếp nhận · Đang xử lý · Đã xử lý · Đã đóng. */
enum class TicketStatus { Received, InProgress, Resolved, Closed }

/**
 * Một phiếu ở thẻ "Yêu cầu của tôi" (phiếu của mọi dự án thuộc tài khoản). [code] do backend cấp, ví dụ "#HT-2609-0142".
 * [projectName] null khi phiếu không gắn dự án (loại Tài khoản / Khác) — thẻ hiện tên loại thay cho dự án.
 */
data class TicketSummary(
    val id: String,
    val code: String,
    val title: String,
    val type: TicketType,
    val projectName: String?,
    val createdAt: LocalDateTime,
    val status: TicketStatus,
)

/** Phiếu mới nhất lên đầu (đặc tả §3.24: phiếu vừa tạo nằm trên cùng). */
fun List<TicketSummary>.newestFirst(): List<TicketSummary> = sortedByDescending { it.createdAt }

/** Tệp đính kèm đã chọn: [uri] của bộ chọn ảnh hệ thống, [name] để hiển thị. */
data class TicketAttachment(val uri: String, val name: String, val isVideo: Boolean)

/**
 * Nội dung gửi đi. Chưa có hợp đồng API (docs/OPEN_QUESTIONS.md) nên đây chỉ là mô hình phía app, không phải JSON.
 * [alertId]: phiếu tạo từ cảnh báo 10a — backend tự đính kèm serial, firmware, mã lỗi (Thay đổi 0.6 §6).
 */
data class NewTicket(
    val type: TicketType,
    val projectId: String?,
    val projectName: String?,
    val title: String,
    val description: String,
    val attachments: List<TicketAttachment>,
    val alertId: String?,
)
