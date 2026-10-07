package vn.ggpower.inverter.data.alert

import kotlinx.coroutines.flow.Flow
import vn.ggpower.inverter.domain.alert.AlertDetail
import vn.ggpower.inverter.domain.alert.AlertSummary

/** Lỗi khi tải cảnh báo. Chưa có hợp đồng API nên chỉ phân loại. */
class AlertLoadException(val kind: Kind) : Exception() {
    enum class Kind { NoConnection, Server }
}

/** Cảnh báo của dự án (màn 10, 10a). Chưa có hợp đồng API (OPEN_QUESTIONS 02/10/2026). */
interface AlertRepository {
    /** Danh sách mới nhất trước; phát lại khi có thay đổi (ví dụ đánh dấu đã xử lý). Ném [AlertLoadException] khi lỗi. */
    fun alerts(projectId: String): Flow<List<AlertSummary>>

    /** Chi tiết một cảnh báo (màn 10a); phát lại khi trạng thái xử lý đổi. Ném [AlertLoadException] khi lỗi. */
    fun detail(projectId: String, alertId: String): Flow<AlertDetail>

    /** Đánh dấu / bỏ đánh dấu đã xử lý (mọi vai trò, mọi lúc — quyết 02/10/2026). Ném [AlertLoadException] khi lỗi. */
    suspend fun setResolved(projectId: String, alertId: String, resolved: Boolean)
}
