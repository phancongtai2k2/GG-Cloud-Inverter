package vn.ggpower.inverter.ui.support

import vn.ggpower.inverter.domain.project.ProjectSummary
import vn.ggpower.inverter.domain.support.TicketDraft
import vn.ggpower.inverter.domain.support.TicketFieldError
import vn.ggpower.inverter.domain.support.TicketSummary
import java.time.LocalDateTime

/** Hai thẻ của màn 8 (đặc tả §3.8). */
enum class SupportTab { New, Mine }

/** Tải dữ liệu cho form: danh sách dự án (và cảnh báo khi mở từ 10a). */
sealed interface FormLoad {
    data object Loading : FormLoad
    data class Error(val noConnection: Boolean) : FormLoad
    data object Ready : FormLoad
}

/** Dải đỏ "Tạo từ cảnh báo" (Thay đổi 0.6 §6). */
data class AlertBanner(val title: String, val startedAt: LocalDateTime)

sealed interface TicketsState {
    data object Loading : TicketsState
    data class Error(val noConnection: Boolean) : TicketsState
    data class Content(val tickets: List<TicketSummary>) : TicketsState
}

/**
 * Màn 8 · Yêu cầu hỗ trợ. Lỗi kiểm tra chỉ hiện sau lần bấm "Gửi yêu cầu" đầu tiên, rồi cập nhật theo nội dung gõ.
 * [sent]: phiếu vừa gửi thành công — UI mở màn 24 rồi báo đã xử lý.
 */
data class SupportUiState(
    val tab: SupportTab = SupportTab.New,
    val load: FormLoad = FormLoad.Loading,
    val alert: AlertBanner? = null,
    val draft: TicketDraft = TicketDraft(),
    val projects: List<ProjectSummary> = emptyList(),
    val projectMenuOpen: Boolean = false,
    val showErrors: Boolean = false,
    val submitting: Boolean = false,
    /** Gửi lỗi: giữ nguyên nội dung, nút đổi thành "Gửi lại" (đặc tả §3.24). null = không lỗi. */
    val submitFailed: SubmitFailure? = null,
    val tickets: TicketsState = TicketsState.Loading,
    val sent: TicketSummary? = null,
) {
    val hasProjects: Boolean get() = projects.isNotEmpty()
    val showsProject: Boolean get() = draft.showsProject(hasProjects)
    val selectedProject: ProjectSummary? get() = projects.firstOrNull { it.id == draft.projectId }
    val errors: Set<TicketFieldError> get() = if (showErrors) draft.validate(hasProjects) else emptySet()
}

data class SubmitFailure(val noConnection: Boolean)
