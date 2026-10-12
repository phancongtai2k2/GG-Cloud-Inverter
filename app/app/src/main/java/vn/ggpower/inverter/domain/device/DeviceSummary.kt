package vn.ggpower.inverter.domain.device

import vn.ggpower.inverter.domain.project.ProjectStatus

/**
 * Thẻ Inverter ở màn 5 (đặc tả §3.5). Chỉ số null khi chưa có dữ liệu → hiển thị "—" (§17.5).
 */
data class InverterSummary(
    val name: String,
    val serial: String,
    val status: ProjectStatus,
    val powerKw: Double?,
    val temperatureC: Double?,
    val firmware: String?,
)

/** Pin đang sạc / xả / đứng yên. */
enum class BatteryState { Charging, Discharging, Idle }

/**
 * Thẻ Pin S16 Pre (đặc tả §3.5): số liệu gộp của cả dàn pin sau một inverter (quyết 02/10/2026).
 * [hoursRemaining]: giờ tới đầy khi sạc, giờ còn lại khi xả — backend tính; null khi đứng yên / chưa có.
 * [powerKw] dương là sạc.
 */
data class BatterySummary(
    val name: String,
    val state: BatteryState,
    val hoursRemaining: Double?,
    val socPercent: Int?,
    val powerKw: Double?,
    val voltageV: Double?,
    val sohPercent: Int?,
)

/** Thiết bị của một dự án: mỗi inverter kèm dàn pin (nếu có) và datalogger của nó. */
data class DeviceUnit(val inverter: InverterSummary, val battery: BatterySummary?, val datalogger: DataloggerSummary? = null)

/** Mức sóng Wi-Fi hiển thị ở thẻ Datalogger (gg-inverter-design Device.dc.html: "Mạnh"). */
enum class SignalLevel {
    Strong, Medium, Weak;

    companion object {
        /** Từ RSSI (dBm): ≥ −60 mạnh, ≥ −70 trung bình, còn lại yếu (ngưỡng Wi-Fi thông dụng). */
        fun fromRssi(rssi: Int?): SignalLevel? = when {
            rssi == null -> null
            rssi >= -60 -> Strong
            rssi >= -70 -> Medium
            else -> Weak
        }
    }
}

enum class LinkType { WiFi, Ethernet, Cellular }

/**
 * Thẻ Datalogger Wi-Fi ở màn 5 (thiết kế mới). Giá trị null hiện "—": backend chưa trả serial datalogger, tín hiệu và
 * kiểu kết nối (contract/CHANGES.md, mục "Đang chờ"); [online] null khi chưa biết.
 */
data class DataloggerSummary(
    val serial: String?,
    val online: Boolean?,
    val signal: SignalLevel?,
    val link: LinkType?,
    val firmware: String?,
)
