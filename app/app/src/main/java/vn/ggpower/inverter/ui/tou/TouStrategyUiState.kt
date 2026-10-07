package vn.ggpower.inverter.ui.tou

import vn.ggpower.inverter.domain.config.OperatingMode
import vn.ggpower.inverter.domain.config.WriteState
import vn.ggpower.inverter.ui.config.ConfigNotice
import vn.ggpower.inverter.domain.tou.SlotError
import vn.ggpower.inverter.domain.tou.SlotInput
import vn.ggpower.inverter.domain.tou.StrategyNameError
import vn.ggpower.inverter.domain.tou.TouChange
import vn.ggpower.inverter.domain.tou.TouStrategy
import vn.ggpower.inverter.domain.tou.canSave
import vn.ggpower.inverter.domain.tou.daysOwnedByOthers
import vn.ggpower.inverter.domain.tou.diffSchedules
import java.time.DayOfWeek

sealed interface TouStrategyUiState {
    data object Loading : TouStrategyUiState
    data class Error(val noConnection: Boolean) : TouStrategyUiState

    /**
     * Màn 16b trên bản nháp (ADR-002 mục 2): [saved] là danh sách chiến lược đã lưu, [draft] là bản đang sửa (mọi thao
     * tác chỉ đổi bản nháp). [editable]: có quyền ghi lịch TOU (ADR-001); không có thì chỉ xem.
     */
    data class Content(
        /** `version` của cấu hình đã đọc, gửi lại ở `based_on_version`. */
        val version: String,
        val saved: List<TouStrategy>,
        val draft: List<TouStrategy>,
        val strategyId: String,
        val editable: Boolean,
        val mode: OperatingMode,
        val rename: RenameState? = null,
        val showDiscard: Boolean = false,
        val slotSheet: SlotSheetState? = null,
        val confirm: TouConfirm? = null,
        val writing: Boolean = false,
        val notice: ConfigNotice? = null,
        val showUnknownDialog: Boolean = false,
        /** Backend vừa từ chối vì khoá ghi (BO_SUNG_v3.md §3): hiện dải và chuyển sang chỉ xem. */
        val banner: WriteState? = null,
        /** Đã xoá chiến lược thành công: màn tự quay về 16a. */
        val finished: Boolean = false,
    ) : TouStrategyUiState {
        val index: Int get() = draft.indexOfFirst { it.id == strategyId }
        val strategy: TouStrategy get() = draft[index]
        val changes: List<TouChange> get() = diffSchedules(saved, draft)
        val hasChanges: Boolean get() = changes.isNotEmpty()
        val missingDays: Boolean get() = strategy.weekdays.isEmpty()
        /** Sửa được bản nháp lúc này: có quyền ghi và không có lệnh đang chạy. */
        val interactive: Boolean get() = editable && !writing
        val saveEnabled: Boolean get() = editable && !writing && canSave(saved, draft)
        /** Chiến lược khác và các thứ của nó, cho dòng nhắc dưới chip ngày (Thay đổi 0.6 §4). */
        val daysOfOthers: Map<TouStrategy, List<DayOfWeek>> get() = draft.daysOwnedByOthers(strategyId)
        /** "Xoá chiến lược này" chỉ hiện khi còn chiến lược khác (TouStrategy.dc.html `canDelete`). */
        val canDelete: Boolean get() = editable && !writing && draft.size > 1
    }
}

/**
 * Popup thêm / sửa khung (Thay đổi 0.6 §5). [editingIndex]: chỉ số trong `slots` của khung đang sửa, null = thêm mới.
 * [error]: lỗi đầu tiên theo thứ tự kiểm tra; có lỗi thì khoá nút Lưu.
 */
data class SlotSheetState(val editingIndex: Int?, val input: SlotInput, val error: SlotError?)

enum class SlotField { Start, End, Soc, Power }

/**
 * Hộp xác nhận lưu lịch (ADR-002 mục 2, 8): [draft] là danh sách sẽ gửi trong lệnh `tou_schedule`; [deleting]: lệnh
 * xoá chiến lược đang xem (thành công thì về 16a); [stale]: vừa đọc lại vì `CONFIG_STALE`.
 */
data class TouConfirm(val draft: List<TouStrategy>, val deleting: Boolean = false, val stale: Boolean = false)

/** Bottom sheet "Đổi tên chiến lược" (BO_SUNG_v3.md §4). */
data class RenameState(val text: String, val error: StrategyNameError?)
