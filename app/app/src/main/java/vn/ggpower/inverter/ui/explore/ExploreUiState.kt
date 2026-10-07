package vn.ggpower.inverter.ui.explore

import vn.ggpower.inverter.domain.explore.ExploreContent
import vn.ggpower.inverter.domain.explore.ExploreTab

/**
 * Màn 7 · Khám phá. Nội dung là dữ liệu tĩnh nên luôn có (không có trạng thái tải / lỗi — chốt 03/10/2026).
 * [content] đã lọc theo [query]; thẻ đang mở không còn mục nào thì hiện "Không tìm thấy nội dung phù hợp.".
 */
data class ExploreUiState(
    val tab: ExploreTab = ExploreTab.Products,
    val query: String = "",
    val content: ExploreContent,
) {
    val noResult: Boolean get() = content.isEmpty(tab)
}
