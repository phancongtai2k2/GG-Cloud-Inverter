package vn.ggpower.inverter.ui.support

/** Màn 24. [technical]: chỉ loại Kỹ thuật có thời gian phản hồi dự kiến trong đặc tả (§3.24). */
data class SupportSentUiState(val code: String, val technical: Boolean, val copied: Boolean = false)
