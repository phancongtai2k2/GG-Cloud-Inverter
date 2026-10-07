package vn.ggpower.inverter.domain.battery

import vn.ggpower.inverter.domain.home.BatteryActivity
import vn.ggpower.inverter.domain.home.BatteryContent
import vn.ggpower.inverter.domain.home.FLOW_THRESHOLD_KW
import java.math.BigDecimal
import java.math.RoundingMode

/**
 * Dữ liệu màn 11 · Nguồn năng lượng pin (đặc tả §3.11). [content] do cloud/EMS tính (phương pháp tách theo nguồn
 * còn chờ chốt); null khi chưa có. Các thông số khác lấy từ telemetry pin; null hiện "—".
 */
data class BatteryEnergy(
    val name: String,
    val socPercent: Double?,
    /** Công suất pin, dương = sạc. */
    val powerKw: Double?,
    val content: BatteryContent?,
    val voltageV: Double?,
    /** Dòng pin, dương = sạc. */
    val currentA: Double?,
    val temperatureC: Double?,
    val sohPercent: Double?,
    val cycles: Int?,
    val cellDeltaMv: Double?,
)

/** Cùng ngưỡng với Màn hình chính: |công suất| < 0,05 kW là không sạc/xả. */
val BatteryEnergy.activity: BatteryActivity
    get() {
        val kw = powerKw ?: return BatteryActivity.Idle
        return when {
            kw >= FLOW_THRESHOLD_KW -> BatteryActivity.Charging
            kw <= -FLOW_THRESHOLD_KW -> BatteryActivity.Discharging
            else -> BatteryActivity.Idle
        }
    }

/**
 * Bảng "Từ PV · Từ lưới điện · Còn trống" và hai lớp của khối pin. Phần trăm tính trên dung lượng định mức, làm tròn
 * 1 chữ số; "Còn trống" là phần còn lại để ba dòng cộng đúng 100 % (prototype: 63,8 + 11,9 + 24,3).
 * Dung lượng khả dụng = Từ PV + Từ lưới (12,1 kWh trên ảnh).
 */
data class EnergyShares(
    val pvKwh: Double,
    val gridKwh: Double,
    val emptyKwh: Double,
    val pvPercent: Double,
    val gridPercent: Double,
    val emptyPercent: Double,
) {
    val availableKwh: Double get() = pvKwh + gridKwh
}

fun BatteryContent.shares(): EnergyShares {
    val cap = BigDecimal.valueOf(capacityKwh)
    val pv = BigDecimal.valueOf(fromPvKwh.coerceIn(0.0, capacityKwh))
    val grid = BigDecimal.valueOf(fromGridKwh.coerceIn(0.0, capacityKwh)).min(cap - pv)
    fun pct(v: BigDecimal) = v.multiply(BigDecimal(100)).divide(cap, 1, RoundingMode.HALF_UP)
    val pvPct = pct(pv)
    val gridPct = pct(grid)
    return EnergyShares(
        pvKwh = pv.toDouble(),
        gridKwh = grid.toDouble(),
        emptyKwh = (cap - pv - grid).toDouble(),
        pvPercent = pvPct.toDouble(),
        gridPercent = gridPct.toDouble(),
        emptyPercent = (BigDecimal(100) - pvPct - gridPct).max(BigDecimal.ZERO).toDouble(),
    )
}
