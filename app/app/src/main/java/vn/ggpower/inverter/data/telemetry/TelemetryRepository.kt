package vn.ggpower.inverter.data.telemetry

import kotlinx.coroutines.flow.Flow
import vn.ggpower.inverter.domain.telemetry.BatteryTelemetry
import vn.ggpower.inverter.domain.telemetry.DeviceKind
import vn.ggpower.inverter.domain.telemetry.HistoryField
import vn.ggpower.inverter.domain.telemetry.HistoryRange
import vn.ggpower.inverter.domain.telemetry.HistorySeries
import vn.ggpower.inverter.domain.telemetry.InverterTelemetry
import java.time.LocalDateTime

/** Lỗi khi tải số liệu. Chưa có hợp đồng API nên chỉ phân loại. */
class TelemetryLoadException(val kind: Kind) : Exception() {
    enum class Kind { NoConnection, Server }
}

/**
 * Số liệu thời gian thực của thiết bị (đặc tả §17). Backend đẩy mỗi 5 s qua WebSocket (§17.4); chỉ nhận khi màn đang
 * hiển thị (§17.5). Chưa có hợp đồng API (OPEN_QUESTIONS 02/10/2026).
 */
interface TelemetryRepository {
    /** Luồng số liệu inverter của dự án; phát giá trị mới mỗi chu kỳ, ném [TelemetryLoadException] khi lỗi. */
    fun inverter(projectId: String): Flow<InverterTelemetry>

    /** Luồng số liệu dàn pin của dự án (gộp, đọc qua inverter — quyết 02/10/2026). */
    fun battery(projectId: String): Flow<BatteryTelemetry>

    /**
     * Lịch sử của [fields] trong [range] tính tới [now] (§17.4: lưu 1 phút một điểm, 7 ngày đọc từ continuous aggregate).
     * Ném [TelemetryLoadException] khi lỗi.
     */
    suspend fun history(projectId: String, device: DeviceKind, range: HistoryRange, fields: List<HistoryField>, now: LocalDateTime): HistorySeries
}
