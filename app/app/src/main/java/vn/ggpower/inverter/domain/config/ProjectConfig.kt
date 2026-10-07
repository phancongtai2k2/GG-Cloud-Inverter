package vn.ggpower.inverter.domain.config

import vn.ggpower.inverter.domain.tou.TouStrategy
import java.time.LocalDateTime

/**
 * Cấu hình dự án cho màn 16. Phần đầu theo hợp đồng API nháp `ConfigSnapshot` (`docs/api/config-write.openapi.yaml`);
 * [settings] và [history] chưa có hợp đồng (OPEN_QUESTIONS 02/10/2026), hiện chỉ lấy từ dữ liệu mẫu.
 */
data class ProjectConfig(
    val version: String,
    val mode: OperatingMode,
    val backupSocPercent: Int,
    val strategies: List<TouStrategy>,
    val todayStrategyId: String?,
    val writeAccess: WriteAccess,
    val settings: DeviceSettings,
    val history: List<ConfigChange>,
) {
    val todayStrategy: TouStrategy? get() = strategies.firstOrNull { it.id == todayStrategyId }
}

/** Các tham số chỉ xem ở bản demo (đặc tả §16.3, §16.5–16.7). Mã giá trị giữ nguyên như thanh ghi. */
data class DeviceSettings(
    /** Dung lượng pin và tải trung bình 7 ngày, để ước tính giờ dự phòng (§16.3). */
    val batteryCapacityKwh: Double,
    val averageLoadKw: Double,
    val stopChargeSocPercent: Int,       // E01D
    val lowSocAlarmPercent: Int,         // E01E
    val switchToGridSocPercent: Int,     // E01F
    val maxChargeCurrentA: Int,          // E20A
    /** Giới hạn dòng của BMS khi có truyền thông BMS; null nếu không giới hạn. */
    val bmsChargeLimitA: Int?,
    val gridChargeCurrentA: Int,         // E205
    val chargeLimitMode: Int,            // E025: 0 / 1 / 2
    val gridWorkMode: Int,               // E037: 0–4
    val zeroExportW: Int,                // E42C
    val maxLinePowerW: Int,              // E21D
    val outputVoltageV: Int,             // E208
    val outputFrequencyHz: Int,          // E209
    val protection: ProtectionSettings,
    val bmsComm: Int,                    // E215: 0 không, 1 RS485, 2 CAN
    /** E21B: mã giao thức theo tài liệu SRNE, hiển thị nguyên văn. */
    val bmsProtocol: String,
    val modbusAddress: Int,              // E200
    val parallelMode: Int,               // E201: 0 độc lập, 1 một pha, 5–7 ba pha
    /**
     * false khi backend chưa đọc được các tham số chỉ xem ở trên (datalogger chưa gửi các thanh ghi đó): màn 16 ẩn các
     * dòng / mục tương ứng thay vì hiện số 0 giả. Dung lượng pin và tải trung bình vẫn dùng được.
     */
    val available: Boolean = true,
)

/** Bảo vệ và khởi động lại (§16.6). */
data class ProtectionSettings(
    val autoRestartOverload: Boolean,    // E20D
    val autoRestartOverTemp: Boolean,    // E20E
    val stopOnBmsLoss: Boolean,          // E214
    val alarmEnabled: Boolean,           // E210
    val powerSaving: Boolean,            // E20C
)

/** Một dòng Lịch sử thay đổi · 90 ngày (§16.8): người thực hiện, thời điểm, giá trị cũ → mới. */
data class ConfigChange(val value: ChangedValue, val actor: String, val at: LocalDateTime)

sealed interface ChangedValue {
    data class Mode(val old: OperatingMode, val new: OperatingMode) : ChangedValue
    data class BackupSoc(val old: Int, val new: Int) : ChangedValue
}

/**
 * Số giờ pin giữ được ở mức SOC dự phòng với tải trung bình (Config.dc.html: dung lượng × SOC / tải).
 * Null khi chưa có tải trung bình.
 */
fun backupHours(capacityKwh: Double, socPercent: Int, averageLoadKw: Double): Double? =
    if (averageLoadKw <= 0.0) null else capacityKwh * socPercent / 100.0 / averageLoadKw
