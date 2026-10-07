package vn.ggpower.inverter.ui.support

import vn.ggpower.inverter.domain.support.TicketDetail
import vn.ggpower.inverter.domain.support.normalizedMessage

sealed interface DetailLoad {
    data object Loading : DetailLoad
    data class Error(val noConnection: Boolean) : DetailLoad
    data class Content(val detail: TicketDetail) : DetailLoad
}

/**
 * Màn 8 · Chi tiết yêu cầu. [message]: chữ trong ô "Nhập tin nhắn…" — gửi lỗi thì giữ nguyên, kèm [sendFailed].
 * [copied]: vừa sao chép mã — icon đổi ✓ và hiện thông báo nhỏ, tự hết sau [vn.ggpower.inverter.ui.service.COPIED_FEEDBACK_MS].
 */
data class TicketDetailUiState(
    val load: DetailLoad = DetailLoad.Loading,
    val message: String = "",
    val sending: Boolean = false,
    val sendFailed: SubmitFailure? = null,
    val copied: Boolean = false,
) {
    val detail: TicketDetail? get() = (load as? DetailLoad.Content)?.detail
    val canSend: Boolean get() = !sending && detail?.acceptsReplies == true && normalizedMessage(message) != null
}

/** Gom callback của màn để Screen / Preview gọn. */
data class TicketDetailActions(
    val onBack: () -> Unit = {},
    val onCopy: () -> Unit = {},
    val onMessage: (String) -> Unit = {},
    val onSend: () -> Unit = {},
    val onRetry: () -> Unit = {},
)
