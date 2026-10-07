package vn.ggpower.inverter.domain.home

import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * Số liệu thời gian thực của sơ đồ dòng năng lượng (đặc tả §3.4 mục 4.2), đơn vị kW, đã quy đổi ở backend.
 * Quy ước dấu: [gridKw] dương = mua từ lưới, âm = phát lên lưới; [batteryKw] dương = sạc, âm = xả.
 */
data class EnergySnapshot(
    val pvKw: Double,
    val gridKw: Double,
    val batteryKw: Double,
    val loadKw: Double,
    val batterySocPercent: Int,
)

/** Chiều tia sáng trên một đường nối, so với GG SmartBox ở giữa. */
enum class FlowDirection { None, ToHub, FromHub }

data class FlowEdges(
    val pv: FlowDirection,
    val grid: FlowDirection,
    val battery: FlowDirection,
    val load: FlowDirection,
)

enum class BatteryActivity { Charging, Discharging, Idle }

/** Dưới ngưỡng này coi như không có dòng điện: không vẽ tia sáng (đặc tả 4.2) và pin "không sạc/xả". */
const val FLOW_THRESHOLD_KW = 0.05

/**
 * Chiều dòng điện trên 4 đường nối: PV → SmartBox; lưới ⇄ SmartBox (mua / phát); SmartBox ⇄ pin (sạc / xả);
 * SmartBox → tải. Đường có |công suất| < [FLOW_THRESHOLD_KW] không có tia sáng.
 */
fun EnergySnapshot.edges(): FlowEdges {
    fun signed(kw: Double, positive: FlowDirection, negative: FlowDirection) = when {
        kw >= FLOW_THRESHOLD_KW -> positive
        kw <= -FLOW_THRESHOLD_KW -> negative
        else -> FlowDirection.None
    }
    return FlowEdges(
        pv = signed(pvKw, FlowDirection.ToHub, FlowDirection.None),
        grid = signed(gridKw, FlowDirection.ToHub, FlowDirection.FromHub),
        battery = signed(batteryKw, FlowDirection.FromHub, FlowDirection.ToHub),
        load = signed(loadKw, FlowDirection.FromHub, FlowDirection.None),
    )
}

val EnergySnapshot.batteryActivity: BatteryActivity
    get() = when {
        batteryKw >= FLOW_THRESHOLD_KW -> BatteryActivity.Charging
        batteryKw <= -FLOW_THRESHOLD_KW -> BatteryActivity.Discharging
        else -> BatteryActivity.Idle
    }

/**
 * Năng lượng đang có trong pin theo nguồn (box Pin S16 Pre 4.3, màn 11). Tỷ lệ tính trên dung lượng định mức;
 * phương pháp tách theo nguồn do cloud/EMS tính (đặc tả §3.11, còn chờ chốt).
 */
data class BatteryContent(
    val capacityKwh: Double,
    val fromPvKwh: Double,
    val fromGridKwh: Double,
) {
    init { require(capacityKwh > 0) { "Dung lượng pin phải > 0" } }

    /** Phần thanh tô cam (Từ PV), 0–1. */
    val pvFraction: Double get() = (fromPvKwh / capacityKwh).coerceIn(0.0, 1.0)

    /** Phần thanh tô xanh dương (Từ lưới), 0–1; tổng với [pvFraction] không vượt 1. */
    val gridFraction: Double get() = (fromGridKwh / capacityKwh).coerceIn(0.0, 1.0 - pvFraction)
}

enum class Trend { Higher, Lower, Same }

/** "Hôm nay sản lượng cao hơn 18 % so với hôm qua" (4.1). [percent] là trị tuyệt đối, làm tròn. */
data class YieldComparison(val trend: Trend, val percent: Int)

/** null khi hôm qua không có sản lượng (không so được). Chênh dưới 0,5 % coi là bằng. */
fun compareYield(todayKwh: Double, yesterdayKwh: Double): YieldComparison? {
    if (yesterdayKwh <= 0.0) return null
    val change = (todayKwh - yesterdayKwh) / yesterdayKwh * 100
    val percent = abs(change).roundToInt()
    val trend = when {
        percent == 0 -> Trend.Same
        change > 0 -> Trend.Higher
        else -> Trend.Lower
    }
    return YieldComparison(trend, percent)
}
