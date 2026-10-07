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

/** Thiết bị của một dự án: mỗi inverter kèm dàn pin của nó (nếu có). */
data class DeviceUnit(val inverter: InverterSummary, val battery: BatterySummary?)
