package vn.ggpower.inverter.domain.alert

import vn.ggpower.inverter.domain.settings.TemperatureUnit
import vn.ggpower.inverter.domain.telemetry.FieldUnit
import java.time.Duration
import java.time.LocalDateTime

/** Cách cảnh báo kết thúc (backend báo): tự phục hồi, hoặc firmware đã tự cập nhật. */
enum class AlertEndKind { AutoRecovered, AutoUpdated }

/**
 * Biểu đồ diễn biến 30 phút quanh thời điểm cảnh báo (Thay đổi 0.6 §6): [values] cách đều [stepSeconds] giây bắt đầu
 * từ [start]; [markIndex] là điểm của thời điểm cảnh báo (vạch đứt). [label]: tên đại lượng, ví dụ "Điện áp lưới";
 * [unit]: đơn vị của [values] — app ghép thành "Điện áp lưới (V)" (prototype tách `label` / `unit`); nhiệt độ là °C
 * và hiện theo cài đặt °C/°F (quyết 04/10/2026). [note]: một câu giải thích do backend soạn, hiện nguyên văn.
 */
data class AlertChart(
    val label: String,
    val unit: FieldUnit,
    val kind: ChartKind,
    val values: List<Double>,
    val start: LocalDateTime,
    val stepSeconds: Long,
    val markIndex: Int,
    val note: String,
) {
    enum class ChartKind { Grid, Pv, Battery }
    fun timeAt(index: Int): LocalDateTime = start.plusSeconds(index * stepSeconds)

    /** Giá trị theo đơn vị nhiệt độ đã chọn; biểu đồ không phải nhiệt độ hoặc chọn °C thì giữ nguyên. */
    fun inTemperatureUnit(temperatureUnit: TemperatureUnit): AlertChart =
        if (unit != FieldUnit.Celsius || temperatureUnit == TemperatureUnit.Celsius) this
        else copy(values = values.map(temperatureUnit::fromCelsius))
}

/**
 * Chi tiết một cảnh báo (màn 10a; Thay đổi 0.6 §6; đặc tả §3.23). [code]: mã thô (ví dụ "0x200 CurrErrReg · bit …"),
 * [codeName]: tên tiếng Việt do backend giải mã. [causes] / [steps]: do Phòng EMS biên soạn theo mã lỗi.
 */
data class AlertDetail(
    val id: String,
    val severity: AlertSeverity,
    val title: String,
    val description: String,
    val device: String,
    val projectName: String,
    val startedAt: LocalDateTime,
    val endedAt: LocalDateTime?,
    val endKind: AlertEndKind?,
    val resolved: Boolean,
    val code: String,
    val codeName: String,
    val chart: AlertChart?,
    val causes: List<String>,
    val steps: List<String>,
) {
    val ongoing: Boolean get() = endedAt == null
}

/**
 * Thời lượng (phút): tới lúc kết thúc, hoặc tới [now] nếu đang diễn ra. Cảnh báo Thông tin không có thời lượng
 * (prototype ghi "—").
 */
fun AlertDetail.durationMinutes(now: LocalDateTime): Long? {
    if (severity == AlertSeverity.Info) return null
    val end = endedAt ?: now
    return Duration.between(startedAt, end).toMinutes().coerceAtLeast(0)
}
