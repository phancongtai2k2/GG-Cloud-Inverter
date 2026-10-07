package vn.ggpower.inverter.domain.config

/**
 * Chế độ vận hành (Thay đổi 0.6 §1, đặc tả §16.2). Không còn "Dự phòng mất điện".
 * [Unknown]: tổ hợp thanh ghi đọc được không khớp bảng §16.2 (hợp đồng API `OperatingMode.unknown`).
 */
enum class OperatingMode { GgAi, SelfConsumption, Tou, MaxGridExport, BatteryPriority, Unknown }
