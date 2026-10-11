package vn.ggpower.inverter.domain.battery

import vn.ggpower.inverter.domain.home.BatteryActivity
import vn.ggpower.inverter.domain.home.BatteryContent
import vn.ggpower.inverter.domain.home.FLOW_THRESHOLD_KW

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
    /** Giới hạn SOC sạc / xả theo cấu hình BMS (vạch nét đứt trên khối pin); null khi chưa có (backend chưa trả, contract/CHANGES.md). */
    val chargeLimitSocPercent: Int? = null,
    val dischargeLimitSocPercent: Int? = null,
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
 * "Dung lượng khả dụng" (gg-inverter-design BatteryEnergy.dc.html: 12,1 kWh = 76 % × 16 kWh): năng lượng đang có trong
 * pin theo SOC và dung lượng định mức; null khi thiếu một trong hai.
 */
val BatteryEnergy.availableKwh: Double?
    get() {
        val soc = socPercent ?: return null
        val cap = content?.capacityKwh ?: return null
        return cap * soc.coerceIn(0.0, 100.0) / 100
    }
