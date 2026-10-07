package vn.ggpower.inverter.domain.telemetry

import vn.ggpower.inverter.domain.project.ProjectStatus
import java.time.Instant

/**
 * Số liệu thời gian thực của inverter (đặc tả §17.2). Mọi giá trị đã quy đổi sang đơn vị SI ở backend (§17.5);
 * null = chưa có dữ liệu hoặc ngoài dải hợp lệ → hiển thị "—", không hiển thị 0.
 * [machineState]: 0x210 MachineState đã được backend giải mã thành chuỗi tiếng Việt.
 * [activeErrors]: tên các mã lỗi đang bật trong 0x200 CurrErrReg (backend giải mã); rỗng = không có.
 */
data class InverterTelemetry(
    val name: String,
    val serial: String,
    val status: ProjectStatus,
    val updatedAt: Instant,
    val machineState: String?,
    val pv1VoltageV: Double?, val pv1CurrentA: Double?, val pv1PowerW: Double?,
    val pv2VoltageV: Double?, val pv2CurrentA: Double?, val pv2PowerW: Double?,
    val gridVoltageV: Double?, val gridCurrentA: Double?, val gridFrequencyHz: Double?,
    /** 0x23A, dương là phát lên lưới. */
    val gridPowerW: Double?,
    val outputVoltageV: Double?, val outputCurrentA: Double?,
    val upsLoadW: Double?, val loadApparentVa: Double?, val loadRatioPercent: Double?, val homeLoadW: Double?,
    val gridChargeCurrentA: Double?, val pvChargeCurrentA: Double?,
    val tempDcDcC: Double?, val tempDcAcC: Double?, val tempTransformerC: Double?, val tempAmbientC: Double?,
    val activeErrors: List<String>?,
) {
    /** Công suất PV tổng = 0x109 + 0x111; thiếu một chuỗi thì không tính (hiển thị "—"). */
    val pvTotalW: Double? get() = if (pv1PowerW != null && pv2PowerW != null) pv1PowerW + pv2PowerW else null
}

/** Độ cũ của số liệu cho dòng "cập nhật 3 giây trước" (đặc tả §17.1). */
sealed interface DataAge {
    data class Seconds(val value: Long) : DataAge
    data class Minutes(val value: Long) : DataAge
    /** Quá 1 giờ: hiện thời điểm. */
    data class At(val instant: Instant) : DataAge
}

fun dataAge(updatedAt: Instant, now: Instant): DataAge {
    val seconds = (now.epochSecond - updatedAt.epochSecond).coerceAtLeast(0)
    return when {
        seconds < 60 -> DataAge.Seconds(seconds)
        seconds < 3600 -> DataAge.Minutes(seconds / 60)
        else -> DataAge.At(updatedAt)
    }
}
