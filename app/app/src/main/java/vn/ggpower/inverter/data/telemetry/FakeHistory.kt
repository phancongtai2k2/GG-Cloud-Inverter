package vn.ggpower.inverter.data.telemetry

import vn.ggpower.inverter.domain.telemetry.HistoryField
import vn.ggpower.inverter.domain.telemetry.HistoryFields
import vn.ggpower.inverter.domain.telemetry.HistoryRange
import vn.ggpower.inverter.domain.telemetry.HistorySeries
import vn.ggpower.inverter.domain.telemetry.rangeStart
import java.time.LocalDateTime
import kotlin.math.PI
import kotlin.math.sin

/**
 * Lịch sử mẫu cho màn 17. Đường trong ngày lấy theo các điểm SVG của prototype (x 0…318 = 00:00…24:00, y theo khung
 * 150 px) rồi quy đổi đơn vị; 1 giờ / 7 ngày dùng lại đường trong ngày theo giờ trong ngày, mỗi ngày nhân hệ số nhẹ.
 * Điểm sau [now] là null (chưa tới).
 */
object FakeHistory {

    private val PV = listOf(0.0 to 145.0, 40.0 to 145.0, 60.0 to 120.0, 80.0 to 70.0, 100.0 to 40.0, 120.0 to 28.0, 140.0 to 32.0, 160.0 to 58.0, 180.0 to 100.0, 200.0 to 138.0, 220.0 to 145.0, 318.0 to 145.0)
    private val LOAD = listOf(0.0 to 122.0, 40.0 to 128.0, 60.0 to 118.0, 80.0 to 112.0, 100.0 to 106.0, 120.0 to 100.0, 140.0 to 104.0, 160.0 to 110.0, 180.0 to 96.0, 200.0 to 80.0, 220.0 to 70.0, 260.0 to 90.0, 318.0 to 120.0)
    private val TEMP = listOf(0.0 to 110.0, 60.0 to 108.0, 100.0 to 96.0, 140.0 to 84.0, 180.0 to 88.0, 220.0 to 100.0, 260.0 to 108.0, 318.0 to 112.0)
    private val SOC = listOf(0.0 to 60.0, 40.0 to 62.0, 60.0 to 66.0, 80.0 to 70.0, 100.0 to 62.0, 120.0 to 50.0, 140.0 to 40.0, 160.0 to 32.0, 180.0 to 30.0, 200.0 to 36.0, 220.0 to 48.0, 260.0 to 64.0, 318.0 to 78.0)
    private val CURRENT = listOf(0.0 to 112.0, 40.0 to 112.0, 60.0 to 110.0, 80.0 to 98.0, 100.0 to 80.0, 120.0 to 62.0, 140.0 to 60.0, 160.0 to 70.0, 180.0 to 90.0, 200.0 to 118.0, 220.0 to 130.0, 260.0 to 128.0, 318.0 to 118.0)

    private fun y(points: List<Pair<Double, Double>>, minuteOfDay: Double): Double {
        val x = minuteOfDay / 1440.0 * 318.0
        for (j in 0 until points.lastIndex) {
            val (x0, y0) = points[j]
            val (x1, y1) = points[j + 1]
            if (x in x0..x1) return y0 + (x - x0) / ((x1 - x0).takeIf { it > 0 } ?: 1.0) * (y1 - y0)
        }
        return points.last().second
    }

    /** Giá trị của [field] tại phút [minuteOfDay] của một ngày có hệ số [k] (0,85…1,15). */
    private fun value(field: HistoryField, minuteOfDay: Double, k: Double): Double = when (field) {
        HistoryFields.PvPower -> maxOf(0.0, (145 - y(PV, minuteOfDay)) / 135 * 5000) * k
        HistoryFields.LoadPower -> maxOf(0.0, (145 - y(LOAD, minuteOfDay)) / 135 * 5000) * (2 - k)
        HistoryFields.TempDcAc -> 20 + (145 - y(TEMP, minuteOfDay)) / 135 * 40
        HistoryFields.GridVoltage -> 229.5 + 2.5 * sin(minuteOfDay / 1440 * 2 * PI)
        HistoryFields.GridFrequency -> 50.0 + 0.03 * sin(minuteOfDay / 90)
        HistoryFields.Soc -> ((145 - y(SOC, minuteOfDay)) / 135 * 100).coerceIn(0.0, 100.0)
        HistoryFields.PackCurrent -> (77.5 - y(CURRENT, minuteOfDay)) / 135 * 100
        HistoryFields.BatteryTemp -> 27 + (145 - y(SOC, minuteOfDay)) / 135 * 6
        HistoryFields.CellDelta -> 10 + 4 * sin(minuteOfDay / 1440 * 2 * PI)
        else -> 0.0
    }

    fun series(range: HistoryRange, fields: List<HistoryField>, now: LocalDateTime): HistorySeries {
        val start = rangeStart(range, now)
        val empty = HistorySeries(range, start, emptyMap())
        val values = fields.associate { f ->
            f.id to List(empty.size) { i ->
                val t = empty.timeAt(i)
                if (t.isAfter(now)) null
                else value(f, t.hour * 60.0 + t.minute, 1 + 0.15 * sin(t.toLocalDate().toEpochDay().toDouble()))
            }
        }
        return HistorySeries(range, start, values)
    }
}
