package vn.ggpower.inverter.ui.projects

import vn.ggpower.inverter.domain.project.ProjectSummary
import vn.ggpower.inverter.domain.project.search

sealed interface ProjectsUiState {
    /** Đang tải lần đầu: khung xương đúng hình thẻ (đặc tả §3.34). */
    data object Loading : ProjectsUiState

    /** Lỗi tải: thông báo ngắn + nút Thử lại (§3.34). */
    data class Error(val noConnection: Boolean) : ProjectsUiState

    data class Content(val projects: List<ProjectSummary>, val query: String = "") : ProjectsUiState {
        /** Tài khoản chưa có dự án nào (ProjectsEmpty.png) — khác với "tìm không thấy". */
        val isEmpty: Boolean get() = projects.isEmpty()
        val visible: List<ProjectSummary> get() = projects.search(query)
    }
}
