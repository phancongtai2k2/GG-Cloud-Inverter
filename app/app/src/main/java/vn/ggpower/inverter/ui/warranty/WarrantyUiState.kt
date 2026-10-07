package vn.ggpower.inverter.ui.warranty

import vn.ggpower.inverter.domain.warranty.ProjectWarranty
import java.time.LocalDate

/**
 * Màn 9 · Bảo hành. Tải / lỗi như màn 5 (chốt 03/10/2026). [today]: ngày tính tình trạng, thanh và "Còn …",
 * lấy từ Clock lúc tải xong.
 */
sealed interface WarrantyUiState {
    data object Loading : WarrantyUiState
    data class Error(val noConnection: Boolean) : WarrantyUiState
    data class Content(val warranty: ProjectWarranty, val today: LocalDate) : WarrantyUiState
}
