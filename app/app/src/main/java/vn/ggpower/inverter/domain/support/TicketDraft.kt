package vn.ggpower.inverter.domain.support

import vn.ggpower.inverter.domain.alert.AlertDetail
import vn.ggpower.inverter.domain.format.GgFormat

/** Số tệp đính kèm tối đa — tạm đặt 03/10/2026, chờ chốt giới hạn (docs/OPEN_QUESTIONS.md). */
const val MAX_TICKET_ATTACHMENTS = 5

/** Lỗi kiểm tra form (đặc tả §3.8 "Cần bổ sung": tiêu đề, mô tả; dự án khi chọn Kỹ thuật). */
enum class TicketFieldError { ProjectMissing, TitleEmpty, DescriptionEmpty }

/** Bản nháp của thẻ "Gửi yêu cầu mới". */
data class TicketDraft(
    val type: TicketType = TicketType.Technical,
    val projectId: String? = null,
    val title: String = "",
    val description: String = "",
    val attachments: List<TicketAttachment> = emptyList(),
) {
    /**
     * Ô dự án chỉ hiện khi chọn Kỹ thuật và tài khoản có ít nhất một dự án. Tài khoản chưa có dự án (mở từ 14a / 18 —
     * BO_SUNG §21) thì ẩn ô và cho gửi không gắn dự án (chốt 03/10/2026).
     */
    fun showsProject(hasProjects: Boolean): Boolean = type == TicketType.Technical && hasProjects

    fun validate(hasProjects: Boolean): Set<TicketFieldError> = buildSet {
        if (showsProject(hasProjects) && projectId == null) add(TicketFieldError.ProjectMissing)
        if (title.isBlank()) add(TicketFieldError.TitleEmpty)
        if (description.isBlank()) add(TicketFieldError.DescriptionEmpty)
    }

    /** Thêm tệp mới chọn: bỏ tệp trùng, không vượt [MAX_TICKET_ATTACHMENTS]. */
    fun withAttachments(picked: List<TicketAttachment>): TicketDraft {
        val known = attachments.map { it.uri }.toMutableSet()
        val fresh = picked.filter { known.add(it.uri) }
        return copy(attachments = (attachments + fresh).take(MAX_TICKET_ATTACHMENTS))
    }

    fun withoutAttachment(uri: String): TicketDraft = copy(attachments = attachments.filterNot { it.uri == uri })

    val attachmentSlotsLeft: Int get() = (MAX_TICKET_ATTACHMENTS - attachments.size).coerceAtLeast(0)

    /**
     * Nội dung gửi đi: bỏ khoảng trắng thừa; loại Tài khoản / Khác không gắn dự án (ô dự án bị ẩn nên giá trị cũ
     * không được gửi kèm).
     */
    fun toNewTicket(hasProjects: Boolean, projectName: (String) -> String?, alertId: String?): NewTicket {
        val pid = projectId.takeIf { showsProject(hasProjects) }
        return NewTicket(
            type = type,
            projectId = pid,
            projectName = pid?.let(projectName),
            title = title.trim(),
            description = description.trim(),
            attachments = attachments,
            alertId = alertId.takeIf { type == TicketType.Technical },
        )
    }
}

/** Tiêu đề điền sẵn khi mở từ 10a (Thay đổi 0.6 §6): "‹tiêu đề cảnh báo› – ‹tên dự án›". */
fun alertTicketTitle(alert: AlertDetail): String = "${alert.title} – ${alert.projectName}"

/**
 * Mô tả điền sẵn (Support.dc.html): "Cảnh báo Lỗi lúc 07:12 · 24/09/2026, vẫn đang diễn ra. Mã lỗi: 0x200 …".
 * Cảnh báo đã kết thúc dùng [endedTemplate] (chưa có trong thiết kế, chốt 03/10/2026). Mẫu lấy từ strings.xml:
 * [ongoingTemplate] nhận (mức độ, thời điểm, mã lỗi); [endedTemplate] nhận (mức độ, thời điểm, lúc kết thúc, mã lỗi).
 */
fun alertTicketDescription(
    alert: AlertDetail,
    severityLabel: String,
    ongoingTemplate: String,
    endedTemplate: String,
): String {
    val started = GgFormat.dateTimeFull(alert.startedAt)
    val ended = alert.endedAt
    return if (ended == null) ongoingTemplate.format(severityLabel, started, alert.code)
    else endedTemplate.format(severityLabel, started, GgFormat.dateTimeFull(ended), alert.code)
}
