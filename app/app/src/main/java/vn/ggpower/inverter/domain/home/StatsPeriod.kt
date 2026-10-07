package vn.ggpower.inverter.domain.home

import java.time.LocalDate
import java.time.LocalDateTime
import java.time.YearMonth
import kotlin.math.floor
import kotlin.math.log10
import kotlin.math.pow

/** Bộ chọn Ngày / Tháng / Năm (đặc tả §3.4 mục 4.4). */
enum class StatsPeriod { Day, Month, Year }

/**
 * Kỳ đang xem: [period] và một ngày bất kỳ trong kỳ ([anchor]). Điều hướng ‹ › lùi / tiến một kỳ;
 * không tiến quá kỳ chứa hôm nay.
 */
data class PeriodSelection(val period: StatsPeriod, val anchor: LocalDate) {

    fun previous(): PeriodSelection = copy(anchor = shift(-1))

    fun next(today: LocalDate): PeriodSelection = if (canGoNext(today)) copy(anchor = shift(1)) else this

    fun canGoNext(today: LocalDate): Boolean = when (period) {
        StatsPeriod.Day -> anchor < today
        StatsPeriod.Month -> YearMonth.from(anchor) < YearMonth.from(today)
        StatsPeriod.Year -> anchor.year < today.year
    }

    fun withPeriod(p: StatsPeriod): PeriodSelection = copy(period = p)

    private fun shift(step: Long): LocalDate = when (period) {
        StatsPeriod.Day -> anchor.plusDays(step)
        StatsPeriod.Month -> anchor.plusMonths(step)
        StatsPeriod.Year -> anchor.plusYears(step)
    }

    /** Số cột của biểu đồ Sản lượng PV: ngày 12 cột 2 giờ, tháng mỗi ngày một cột, năm 12 tháng. */
    val bucketCount: Int
        get() = when (period) {
            StatsPeriod.Day -> 12
            StatsPeriod.Month -> YearMonth.from(anchor).lengthOfMonth()
            StatsPeriod.Year -> 12
        }

    /** Số giờ của cột [index], để tính hệ số hiệu quả = sản lượng / thời gian (đặc tả 4.7). */
    fun bucketHours(index: Int): Double = when (period) {
        StatsPeriod.Day -> 2.0
        StatsPeriod.Month -> 24.0
        StatsPeriod.Year -> YearMonth.of(anchor.year, index + 1).lengthOfMonth() * 24.0
    }

    /** Khoảng thời gian của cột [index]: [start, end). */
    fun bucketRange(index: Int): Pair<LocalDateTime, LocalDateTime> = when (period) {
        StatsPeriod.Day -> anchor.atTime(index * 2, 0).let { it to it.plusHours(2) }
        StatsPeriod.Month -> anchor.withDayOfMonth(index + 1).atStartOfDay().let { it to it.plusDays(1) }
        StatsPeriod.Year -> LocalDate.of(anchor.year, index + 1, 1).atStartOfDay().let { it to it.plusMonths(1) }
    }

    /** Cột chọn sẵn: cột chứa [now] nếu kỳ đang xem chứa [now], không thì cột cuối. */
    fun currentBucket(now: LocalDateTime): Int {
        val inPeriod = when (period) {
            StatsPeriod.Day -> anchor == now.toLocalDate()
            StatsPeriod.Month -> YearMonth.from(anchor) == YearMonth.from(now)
            StatsPeriod.Year -> anchor.year == now.year
        }
        if (!inPeriod) return bucketCount - 1
        return when (period) {
            StatsPeriod.Day -> now.hour / 2
            StatsPeriod.Month -> now.dayOfMonth - 1
            StatsPeriod.Year -> now.monthValue - 1
        }
    }

    /**
     * Nhãn trục x của biểu đồ cột (prototype): ngày "0h", "", "4h"…; tháng số ngày 1, 6, 11… (cách 5);
     * năm "T1", "", "T3"… Chuỗi rỗng = không ghi nhãn. Hậu tố "h" và tiền tố "T" do [hourSuffix], [monthPrefix].
     */
    fun bucketLabels(hourSuffix: String, monthPrefix: String): List<String> = (0 until bucketCount).map { i ->
        when (period) {
            StatsPeriod.Day -> if (i % 2 == 0) "${i * 2}$hourSuffix" else ""
            StatsPeriod.Month -> if (i % 5 == 0) "${i + 1}" else ""
            StatsPeriod.Year -> if (i % 2 == 0) "$monthPrefix${i + 1}" else ""
        }
    }
}

/** Trục giá trị: miền vẽ [min, max] và các mốc. */
data class AxisRange(val min: Double, val max: Double, val ticks: List<Double>)

/**
 * Trục công suất của biểu đồ Công suất (prototype: dữ liệu −1,6…4,2 kW → mốc −2, 0, 2, 4, miền −2…5):
 * bước = làm tròn đẹp của (max − min) / 3; mốc dưới = bội của bước ≤ min (không trên 0); mốc trên = bội ≤ max
 * (không dưới 0); đỉnh miền = mốc trên + nửa bước, thêm một bước nếu dữ liệu vượt.
 */
fun powerAxis(minValue: Double, maxValue: Double): AxisRange {
    val lo = minOf(minValue, 0.0)
    val hi = maxOf(maxValue, 0.0)
    val step = niceCeil(((hi - lo) / 3).coerceAtLeast(0.5))
    val bottom = floor(lo / step) * step
    var topTick = floor(hi / step) * step
    if (hi > topTick + step / 2) topTick += step
    val ticks = generateSequence(bottom) { it + step }.takeWhile { it <= topTick + 1e-9 }.toList()
    return AxisRange(bottom, topTick + step / 2, ticks)
}

/** Trục cột sản lượng / hệ số hiệu quả: đỉnh = làm tròn đẹp của max × 1,05; mốc 0, giữa, đỉnh. */
fun barAxis(maxValue: Double): AxisRange {
    val top = niceCeil(maxValue * 1.05)
    return AxisRange(0.0, top, listOf(0.0, top / 2, top))
}

/**
 * Trục đối xứng quanh 0 cho cột sạc (+) / xả (−) của biểu đồ Pin ở Tháng / Năm: đỉnh = làm tròn đẹp của
 * |giá trị| lớn nhất × 1,05; mốc −đỉnh, −nửa, 0, nửa, đỉnh.
 */
fun symmetricAxis(maxAbs: Double): AxisRange {
    val top = niceCeil(maxAbs * 1.05)
    return AxisRange(-top, top, listOf(-top, -top / 2, 0.0, top / 2, top))
}

/** Phút trong ngày → vị trí trên trục 0…1440. Dùng cho mốc giờ 0h, 6h, 12h, 18h, 24h. */
val DayHourTicks: List<Int> = listOf(0, 6, 12, 18, 24)

/**
 * Làm tròn lên giá trị "đẹp" cho đỉnh trục: bội 1 · 2 · 2,5 · 4 · 5 · 10 của luỹ thừa 10
 * (hàm `nice` của prototype Main.dc.html). 4,41 → 5; 2,2 → 2,5; 600 → 1000.
 */
fun niceCeil(raw: Double): Double {
    if (raw <= 0.0) return 1.0
    val mag = 10.0.pow(floor(log10(raw)))
    for (m in doubleArrayOf(1.0, 2.0, 2.5, 4.0, 5.0, 10.0)) {
        if (m * mag >= raw - 1e-12) return m * mag
    }
    return 10 * mag
}
