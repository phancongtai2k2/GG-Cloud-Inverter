package vn.ggpower.inverter.data.home

import kotlinx.coroutines.flow.Flow
import vn.ggpower.inverter.domain.config.OperatingMode
import vn.ggpower.inverter.domain.home.BatteryContent
import vn.ggpower.inverter.domain.home.EnergySnapshot
import vn.ggpower.inverter.domain.home.PeriodSelection
import java.time.LocalDate
import vn.ggpower.inverter.domain.project.ProjectStatus

/** Phần trên của Màn hình chính (đặc tả §3.4 mục 4.1–4.3). Chưa có hợp đồng API. */
data class HomeOverview(
    val projectName: String,
    val status: ProjectStatus,
    val unresolvedAlerts: Int,
    val yieldTodayKwh: Double,
    val yieldYesterdayKwh: Double,
    val energy: EnergySnapshot,
    val mode: OperatingMode,
    val backupSocPercent: Int,
    val battery: BatteryContent,
    /** CO₂ giảm phát thải hôm nay (chip trên sơ đồ ngôi nhà); null khi backend chưa trả (`/overview` chưa có trường). */
    val co2TodayKg: Double? = null,
    /**
     * kWh đã sạc vào pin hôm nay theo nguồn (thẻ Pin ở màn chính, quyết định 11/10/2026 — khác [battery] là năng lượng
     * đang có trong pin); null khi backend chưa trả (`rollup_15m.pv_charge_kwh`, `contract/CHANGES.md` mục Đang chờ).
     */
    val chargedToday: ChargedToday? = null,
)

/** Năng lượng sạc vào pin trong ngày: từ PV và từ lưới (kWh). */
data class ChargedToday(val fromPvKwh: Double, val fromGridKwh: Double)

/** Một điểm của biểu đồ Công suất (4.5): phút trong ngày và 4 chuỗi kW (pin dương = sạc, lưới dương = mua). */
data class PowerSample(val minute: Int, val pvKw: Double, val gridKw: Double, val batteryKw: Double, val loadKw: Double)

/** Một điểm của biểu đồ Pin S16 Pre (4.6): dòng BMS (A, dương = sạc) và SOC (%). */
data class BatterySample(val minute: Int, val currentA: Double, val socPercent: Int)

/** Đường 24 giờ của một ngày, tới thời điểm hiện tại nếu là hôm nay. */
data class DayCurves(val power: List<PowerSample>, val battery: List<BatterySample>)

/** Một cột Sản lượng PV (4.7): PV tự tiêu thụ, PV sạc pin, PV lên lưới (kWh). */
data class YieldBucket(val selfKwh: Double, val chargeKwh: Double, val exportKwh: Double) {
    val totalKwh: Double get() = selfKwh + chargeKwh + exportKwh
}

/**
 * Năng lượng của một ngày (chế độ Tháng) hoặc một tháng (chế độ Năm), kWh — biểu đồ Năng lượng và Pin khi xem
 * theo Tháng / Năm (quyết định 02/10/2026). [gridNetKwh]: mua (+) / bán (−) ròng.
 */
data class EnergyBucket(
    val pvKwh: Double,
    val gridNetKwh: Double,
    val batteryChargeKwh: Double,
    val batteryDischargeKwh: Double,
    val loadKwh: Double,
    val averageSocPercent: Int,
) {
    /** Sạc (+) / xả (−) ròng. */
    val batteryNetKwh: Double get() = batteryChargeKwh - batteryDischargeKwh
}

/** Chỉ số tổng hợp theo kỳ (đặc tả 4.8): tiêu thụ, tỷ lệ tự dùng, tiền điện tiết kiệm (đ), giảm phát thải CO₂. */
data class PeriodSummary(
    val consumptionKwh: Double,
    val selfUsePercent: Int,
    val savingVnd: Long,
    val co2Kg: Double,
)

interface HomeRepository {
    /** Phát lại mỗi khi có số liệu mới (thời gian thực). */
    fun overview(projectId: String): Flow<HomeOverview>

    suspend fun dayCurves(projectId: String, date: LocalDate): DayCurves

    /** Chế độ Tháng / Năm: đúng [PeriodSelection.bucketCount] phần tử. */
    suspend fun energyBuckets(projectId: String, selection: PeriodSelection): List<EnergyBucket>

    suspend fun periodSummary(projectId: String, selection: PeriodSelection): PeriodSummary

    /** Đúng [PeriodSelection.bucketCount] cột. */
    suspend fun yieldBuckets(projectId: String, selection: PeriodSelection): List<YieldBucket>
}
