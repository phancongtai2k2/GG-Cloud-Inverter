package vn.ggpower.inverter.ui.service

import vn.ggpower.inverter.domain.support.SupportContact

/** Dòng liên hệ có nút sao chép trong box "Hỗ trợ GG Power". */
enum class ContactField { Hotline, Email }

/**
 * Màn 6 · Dịch vụ. Liên hệ là cấu hình tĩnh nên luôn có (không có trạng thái tải / lỗi).
 * [copied]: dòng vừa sao chép — icon đổi thành ✓ nền xanh và hiện thông báo nhỏ, tự hết sau [COPIED_FEEDBACK_MS].
 */
data class ServiceUiState(
    val contact: SupportContact,
    val copied: ContactField? = null,
)

/** Thời gian giữ dấu ✓ và thông báo "Đã sao chép…" (chốt 03/10/2026; prototype giữ mãi). */
const val COPIED_FEEDBACK_MS = 2_000L
