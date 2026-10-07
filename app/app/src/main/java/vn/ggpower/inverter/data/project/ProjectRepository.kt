package vn.ggpower.inverter.data.project

import vn.ggpower.inverter.domain.project.ProjectSummary

/** Lỗi khi tải danh sách dự án. Chưa có hợp đồng API nên chỉ phân loại. */
class ProjectLoadException(val kind: Kind) : Exception() {
    enum class Kind { NoConnection, Server }
}

interface ProjectRepository {
    /** Danh sách dự án của tài khoản đang đăng nhập. Ném [ProjectLoadException] khi lỗi. */
    suspend fun getProjects(): List<ProjectSummary>
}
