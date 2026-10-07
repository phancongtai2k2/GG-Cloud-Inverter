package vn.ggpower.inverter.ui.tou

import vn.ggpower.inverter.domain.tou.TouStrategy

sealed interface TouScheduleUiState {
    data object Loading : TouScheduleUiState
    data class Error(val noConnection: Boolean) : TouScheduleUiState

    /**
     * [canAdd]: còn dưới 4 chiến lược thì hiện nút "Thêm chiến lược" (Thay đổi 0.6 §3);
     * [addEnabled]: có quyền ghi lịch TOU (ADR-001) — không có thì nút bị khoá, vẫn xem được chiến lược.
     */
    data class Content(
        val strategies: List<TouStrategy>,
        val todayStrategyId: String?,
        val canAdd: Boolean,
        val addEnabled: Boolean,
    ) : TouScheduleUiState
}
