package vn.ggpower.inverter.domain.telemetry

import vn.ggpower.inverter.domain.project.ProjectStatus
import java.time.Instant
import kotlin.math.abs

/** Cờ BMS: 32 bit cảnh báo (0x118–0x119) và 32 bit bảo vệ (0x11A–0x11B). */
enum class BmsFlagKind { Alarm, Protect }

/**
 * Một cờ BMS. [name]: tên tiếng Việt do backend giải mã; null khi chưa có bảng ánh xạ bit của SRNE — app hiện nhãn thô
 * "Cờ cảnh báo bit n" (đặc tả §17.3 nhóm D; BO_SUNG_v3.md §13).
 */
data class BmsFlag(val kind: BmsFlagKind, val bit: Int, val name: String?, val active: Boolean)

/**
 * Số liệu thời gian thực của dàn pin S16 Pre, đọc qua inverter (đặc tả §17.3). Đơn vị SI, null = chưa có dữ liệu.
 * [packCurrentA] có dấu, dương là sạc.
 */
data class BatteryTelemetry(
    val name: String,
    val serial: String,
    val status: ProjectStatus,
    val updatedAt: Instant,
    // Nhóm A — Tổng quan
    val socPercent: Double?, val sohPercent: Double?, val cycles: Int?,
    val packVoltageInverterV: Double?, val packVoltageBmsV: Double?, val packCurrentA: Double?,
    val tempBmsC: Double?, val tempInverterC: Double?,
    // Nhóm B — Giới hạn
    val chargeLimitVoltageV: Double?, val chargeLimitCurrentA: Double?, val dischargeLimitCurrentA: Double?,
    /** E025 BMSChgLCMode đọc lại: 0 / 1 / 2. */
    val chargeLimitMode: Int?,
    // Nhóm C — Cell
    val cellMaxMv: Double?, val cellMaxIndex: Int?, val cellMinMv: Double?, val cellMinIndex: Int?,
    val cellTempMaxC: Double?, val cellTempMinC: Double?,
    // Nhóm D — Bảo vệ
    val packsOnline: Int?, val packsChargeBlocked: Int?, val packsDischargeBlocked: Int?, val packsOffline: Int?,
    /** 0x131 BmsSysFaultTbl đã giải mã; rỗng = không có. */
    val systemFaults: List<String>?,
    val flags: List<BmsFlag>?,
    // Nhóm E — Phiên bản
    val manufacturer: String?, val softwareVersion: String?, val hardwareVersion: String?, val protocolVersion: String?,
) {
    /** Lệch giữa điện áp inverter đo và BMS báo (V, không dấu). */
    val voltageDeviationV: Double? get() =
        if (packVoltageInverterV != null && packVoltageBmsV != null) abs(packVoltageInverterV - packVoltageBmsV) else null

    /** Lệch quá 1 V là dấu hiệu tiếp xúc kém hoặc sai cấu hình — làm nổi bằng chip vàng (§17.3 nhóm A). */
    val voltageDeviationHigh: Boolean get() = (voltageDeviationV ?: 0.0) > MAX_VOLTAGE_DEVIATION_V

    /** Công suất cho phép = dòng giới hạn × điện áp BMS báo (§17.3 nhóm B). */
    val allowedChargeW: Double? get() = product(chargeLimitCurrentA, packVoltageBmsV)
    val allowedDischargeW: Double? get() = product(dischargeLimitCurrentA, packVoltageBmsV)

    /** Chênh áp cell = cao nhất − thấp nhất (mV). */
    val cellDeltaMv: Double? get() = if (cellMaxMv != null && cellMinMv != null) cellMaxMv - cellMinMv else null

    private fun product(a: Double?, b: Double?) = if (a != null && b != null) a * b else null

    companion object {
        const val MAX_VOLTAGE_DEVIATION_V = 1.0
    }
}

/** Dòng pack so với giới hạn của BMS (đồng hồ hai cung, §17.3 nhóm B). */
enum class CurrentLimitState { Charging, Discharging, Idle, LimitedCharge, LimitedDischarge }

/**
 * "Chạm giới hạn" khi |dòng| ≥ 98 % giới hạn của chiều đó (sai số đo); dưới 0,5 A coi như đứng yên.
 * Thiếu giới hạn thì chỉ phân biệt sạc / xả.
 */
fun currentLimitState(currentA: Double, chargeLimitA: Double?, dischargeLimitA: Double?): CurrentLimitState = when {
    abs(currentA) < IDLE_CURRENT_A -> CurrentLimitState.Idle
    currentA > 0 -> if (chargeLimitA != null && currentA >= chargeLimitA * LIMIT_RATIO) CurrentLimitState.LimitedCharge else CurrentLimitState.Charging
    else -> if (dischargeLimitA != null && -currentA >= dischargeLimitA * LIMIT_RATIO) CurrentLimitState.LimitedDischarge else CurrentLimitState.Discharging
}

private const val IDLE_CURRENT_A = 0.5
private const val LIMIT_RATIO = 0.98
