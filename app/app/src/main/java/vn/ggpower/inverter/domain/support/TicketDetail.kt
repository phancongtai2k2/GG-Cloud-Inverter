package vn.ggpower.inverter.domain.support

import java.time.LocalDateTime

/** Người viết một lượt trao đổi (TicketDetail.dc.html): "Hỗ trợ GG Power" bên trái, "Bạn" bên phải. */
enum class MessageAuthor { Support, Me }

data class TicketMessage(val id: String, val author: MessageAuthor, val text: String, val sentAt: LocalDateTime)

/**
 * Trang chi tiết phiếu (đặc tả §3.8: nội dung đã gửi, tệp đính kèm và các lượt trao đổi). Chưa có hợp đồng API
 * (docs/OPEN_QUESTIONS.md) nên đây chỉ là mô hình phía app. [photoCount]: số ảnh / video người dùng đính kèm.
 */
data class TicketDetail(
    val summary: TicketSummary,
    val description: String,
    val photoCount: Int,
    val messages: List<TicketMessage>,
) {
    /** Loại Kỹ thuật được tự đính kèm thông tin hệ thống (đặc tả §3.8 "Cần bổ sung"). */
    val systemInfoAttached: Boolean get() = summary.type == TicketType.Technical

    /** Phiếu Đã đóng không nhận tin nhắn mới — thay ô nhập bằng ghi chú (chốt 03/10/2026). Đã xử lý vẫn nhắn được. */
    val acceptsReplies: Boolean get() = summary.status != TicketStatus.Closed

    val attachmentLine: AttachmentLine get() = attachmentLine(photoCount, systemInfoAttached)

    /** Thêm tin backend vừa xác nhận; bỏ qua nếu đã có (backend có thể đã đẩy về trước). */
    fun withMessage(message: TicketMessage): TicketDetail =
        if (messages.any { it.id == message.id }) this else copy(messages = messages + message)
}

/** Dòng 📎 dưới nội dung: "2 ảnh đính kèm · thông tin hệ thống tự đính kèm" và các biến thể khi thiếu một vế. */
sealed interface AttachmentLine {
    data class PhotosAndSystem(val count: Int) : AttachmentLine
    data class PhotosOnly(val count: Int) : AttachmentLine
    data object SystemOnly : AttachmentLine
    data object None : AttachmentLine
}

fun attachmentLine(photoCount: Int, systemInfo: Boolean): AttachmentLine = when {
    photoCount > 0 && systemInfo -> AttachmentLine.PhotosAndSystem(photoCount)
    photoCount > 0 -> AttachmentLine.PhotosOnly(photoCount)
    systemInfo -> AttachmentLine.SystemOnly
    else -> AttachmentLine.None
}

/** Tin sắp gửi: bỏ khoảng trắng thừa ở hai đầu; null khi trống (nút Gửi bị khoá). */
fun normalizedMessage(text: String): String? = text.trim().ifEmpty { null }

/**
 * Giờ của một lượt trao đổi: prototype chỉ ghi "HH:mm" (cùng ngày gửi phiếu). Tin ở ngày khác ngày gửi phiếu thêm
 * ngày "dd/MM HH:mm" để khỏi nhầm (chốt tạm 03/10/2026, docs/OPEN_QUESTIONS.md).
 */
fun messageShowsDate(sentAt: LocalDateTime, ticketCreatedAt: LocalDateTime): Boolean =
    sentAt.toLocalDate() != ticketCreatedAt.toLocalDate()
