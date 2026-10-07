package vn.ggpower.inverter.ui.home

import vn.ggpower.inverter.data.home.DayCurves
import vn.ggpower.inverter.data.home.EnergyBucket
import vn.ggpower.inverter.data.home.PeriodSummary
import vn.ggpower.inverter.data.home.YieldBucket
import vn.ggpower.inverter.domain.home.PeriodSelection
import java.time.LocalDate

/**
 * Phần thống kê của Màn hình chính (4.4–4.7). Mọi biểu đồ đổi theo kỳ (quyết định 02/10/2026):
 * - Ngày: Công suất và Pin là đường 15 phút của ngày [PeriodSelection.anchor] ([curves]);
 * - Tháng / Năm: Năng lượng và Pin theo từng ngày / tháng ([energyBuckets], kWh);
 * - Sản lượng PV luôn theo cột của kỳ ([yieldBuckets]).
 * [powerIndex], [batteryIndex]: chỉ số điểm (Ngày) hoặc chỉ số cột (Tháng / Năm). null = đang tải.
 */
data class StatsUiState(
    val selection: PeriodSelection,
    val today: LocalDate,
    val curves: DayCurves? = null,
    val energyBuckets: List<EnergyBucket>? = null,
    val powerIndex: Int = 0,
    val batteryIndex: Int = 0,
    val yieldBuckets: List<YieldBucket>? = null,
    val yieldIndex: Int = 0,
    /** Chỉ số tổng hợp 4.8 của kỳ; null khi đang tải. */
    val summary: PeriodSummary? = null,
) {
    val canGoNext: Boolean get() = selection.canGoNext(today)
    val isDay: Boolean get() = selection.period == vn.ggpower.inverter.domain.home.StatsPeriod.Day
}
