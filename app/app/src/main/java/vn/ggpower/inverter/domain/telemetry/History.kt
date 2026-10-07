package vn.ggpower.inverter.domain.telemetry

import vn.ggpower.inverter.domain.settings.TemperatureUnit
import java.time.LocalDateTime
import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.log10
import kotlin.math.pow

/**
 * Khoảng của biểu đồ lịch sử (đặc tả §17.4; BO_SUNG_v3.md §12): 1 giờ điểm 1 phút, 24 giờ điểm 15 phút (theo ngày,
 * 00–24), 7 ngày điểm 1 giờ.
 */
enum class HistoryRange(val stepMinutes: Int, val spanMinutes: Int) {
    Hour(1, 60),
    Day(15, 24 * 60),
    Week(60, 7 * 24 * 60),
}

enum class FieldUnit { Watt, Celsius, Volt, Hertz, Percent, Ampere, MilliVolt }

/** Một trường vẽ được. [id] ổn định để lưu lựa chọn; tên hiển thị và màu do tầng UI quyết. */
data class HistoryField(val id: String, val unit: FieldUnit)

const val MAX_HISTORY_FIELDS = 3
const val MAX_HISTORY_UNITS = 2

/** Chọn thêm [field] được không: chưa đủ 3 trường và không tạo đơn vị thứ ba (BO_SUNG_v3.md §12). */
fun canSelect(selected: List<HistoryField>, field: HistoryField): Boolean {
    if (field in selected) return true
    if (selected.size >= MAX_HISTORY_FIELDS) return false
    val units = selected.map { it.unit }.toSet()
    return field.unit in units || units.size < MAX_HISTORY_UNITS
}

/** Bật / tắt [field]; không bỏ được trường cuối cùng, không thêm được khi [canSelect] = false. */
fun toggleField(selected: List<HistoryField>, field: HistoryField): List<HistoryField> = when {
    field in selected -> if (selected.size > 1) selected - field else selected
    canSelect(selected, field) -> selected + field
    else -> selected
}

/** Đơn vị trục trái = đơn vị của trường chọn đầu tiên; trục phải = đơn vị thứ hai (null nếu chỉ một đơn vị). */
fun axisUnits(selected: List<HistoryField>): Pair<FieldUnit?, FieldUnit?> {
    val units = selected.map { it.unit }.distinct()
    return units.getOrNull(0) to units.getOrNull(1)
}

/**
 * Miền trục "đẹp" chia 3 khoảng bằng nhau (4 vạch lưới như prototype) bao trọn [values]. Bước là 1, 2, 2,5 hoặc 5 × 10ⁿ.
 * Giá trị đều không âm thì miền bắt đầu từ 0 nếu 0 gần hơn một bước (công suất, SOC…). Không có giá trị → 0…1.
 */
fun niceRange(values: List<Double?>): ClosedFloatingPointRange<Double> {
    val present = values.filterNotNull()
    if (present.isEmpty()) return 0.0..1.0
    var lo = present.min()
    val hi = present.max()
    if (hi == lo) return if (lo == 0.0) 0.0..1.0 else niceRange(listOf(lo - abs(lo) * 0.1, hi + abs(hi) * 0.1))
    if (lo >= 0 && lo <= (hi - lo) / 3) lo = 0.0
    var step = niceStep((hi - lo) / 3)
    while (true) {
        val start = floor(lo / step) * step
        if (start + 3 * step >= hi - 1e-9) return start..(start + 3 * step)
        step = niceStep(step * 1.01)
    }
}

/** Số đẹp nhỏ nhất ≥ [raw] trong dãy 1, 2, 2,5, 5 × 10ⁿ. */
fun niceStep(raw: Double): Double {
    val exp = floor(log10(raw))
    val base = 10.0.pow(exp)
    val f = raw / base
    val nice = listOf(1.0, 2.0, 2.5, 5.0, 10.0).first { it >= f - 1e-9 }
    return nice * base
}

/**
 * Dữ liệu một khoảng: điểm thứ i ở [start] + i × bước; null = không có dữ liệu (hoặc chưa tới thời điểm đó).
 * [values] theo [HistoryField.id].
 */
data class HistorySeries(
    val range: HistoryRange,
    val start: LocalDateTime,
    val values: Map<String, List<Double?>>,
) {
    val size: Int get() = range.spanMinutes / range.stepMinutes + 1
    fun timeAt(index: Int): LocalDateTime = start.plusMinutes(index.toLong() * range.stepMinutes)

    /**
     * Đổi giá trị các trường nhiệt độ (°C trong [fields]) sang [unit] để vẽ trục và chú giải theo cài đặt màn 3;
     * trường khác giữ nguyên. °C thì trả lại chính nó.
     */
    fun inTemperatureUnit(fields: List<HistoryField>, unit: TemperatureUnit): HistorySeries {
        if (unit == TemperatureUnit.Celsius) return this
        val celsius = fields.filter { it.unit == FieldUnit.Celsius }.map { it.id }.toSet()
        return copy(values = values.mapValues { (id, v) -> if (id in celsius) v.map { it?.let(unit::fromCelsius) } else v })
    }

    /** Điểm cuối cùng có dữ liệu ở bất kỳ trường nào (chọn sẵn như prototype). */
    fun lastIndex(): Int = (size - 1 downTo 0).firstOrNull { i -> values.values.any { it.getOrNull(i) != null } } ?: 0
}

/** Mốc bắt đầu của khoảng tính tới [now]: 1 giờ trước (theo phút); đầu ngày; đầu ngày của 6 ngày trước. */
fun rangeStart(range: HistoryRange, now: LocalDateTime): LocalDateTime = when (range) {
    HistoryRange.Hour -> now.withSecond(0).withNano(0).minusMinutes(60)
    HistoryRange.Day -> now.toLocalDate().atStartOfDay()
    HistoryRange.Week -> now.toLocalDate().minusDays(6).atStartOfDay()
}

/** Thiết bị của màn 17. */
enum class DeviceKind { Inverter, Battery }

/** Các trường vẽ được của từng thiết bị (DataInverter / DataBattery.dc.html), theo thứ tự chip. */
object HistoryFields {
    val PvPower = HistoryField("pv_power", FieldUnit.Watt)
    val LoadPower = HistoryField("load_power", FieldUnit.Watt)
    val TempDcAc = HistoryField("temp_dcac", FieldUnit.Celsius)
    val GridVoltage = HistoryField("grid_voltage", FieldUnit.Volt)
    val GridFrequency = HistoryField("grid_frequency", FieldUnit.Hertz)

    val Soc = HistoryField("soc", FieldUnit.Percent)
    val PackCurrent = HistoryField("pack_current", FieldUnit.Ampere)
    val BatteryTemp = HistoryField("battery_temp", FieldUnit.Celsius)
    val CellDelta = HistoryField("cell_delta", FieldUnit.MilliVolt)

    fun of(device: DeviceKind): List<HistoryField> = when (device) {
        DeviceKind.Inverter -> listOf(PvPower, LoadPower, TempDcAc, GridVoltage, GridFrequency)
        DeviceKind.Battery -> listOf(Soc, PackCurrent, BatteryTemp, CellDelta)
    }

    /** Chọn sẵn như ảnh: inverter 3 trường đầu, pin 2 trường đầu. */
    fun defaults(device: DeviceKind): List<HistoryField> = of(device).take(if (device == DeviceKind.Inverter) 3 else 2)
}
