package vn.ggpower.inverter.ui.config

import vn.ggpower.inverter.domain.config.ConfigControls
import vn.ggpower.inverter.domain.config.ConfigRequest
import vn.ggpower.inverter.domain.config.ProjectConfig
import vn.ggpower.inverter.domain.config.WriteGroup

sealed interface ConfigUiState {
    data object Loading : ConfigUiState
    data class Error(val noConnection: Boolean) : ConfigUiState

    /**
     * [backupSocDraft]: giá trị thanh SOC dự phòng đang hiển thị (đang kéo hoặc chờ xác nhận); bằng giá trị đã lưu khi
     * không thao tác. [writing]: nhóm đang có lệnh ghi chạy — mọi control ghi bị khoá (BO_SUNG_v3.md §2).
     */
    data class Content(
        val config: ProjectConfig,
        val controls: ConfigControls,
        val backupSocDraft: Int = config.backupSocPercent,
        val confirm: ConfirmState? = null,
        val writing: WriteGroup? = null,
        val notice: ConfigNotice? = null,
        val showUnknownDialog: Boolean = false,
    ) : ConfigUiState
}

/** Hộp xác nhận "cũ → mới" đang mở. [stale]: vừa đọc lại vì `CONFIG_STALE` (thêm dòng cảnh báo trên đầu). */
data class ConfirmState(val request: ConfigRequest, val stale: Boolean = false)

/** Snackbar một lần (BO_SUNG_v3.md §2–3). [id] tăng dần để hiện lại cùng loại. */
data class ConfigNotice(val id: Long, val kind: NoticeKind, val retryAfterSeconds: Int? = null)

enum class NoticeKind {
    AppliedWithoutDischarge, Applied, SavedNotWritten, Reverted, Failed, DeviceRejected,
    NotPermitted, CommandInProgress, RateLimited, InvalidData, NoInternet, Generic,
}
