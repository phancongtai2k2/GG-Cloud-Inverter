package vn.ggpower.inverter.domain.format

import vn.ggpower.inverter.domain.settings.AppLanguage
import vn.ggpower.inverter.domain.settings.TemperatureUnit
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

/**
 * Dấu thập phân và dấu nhóm nghìn. Tiếng Việt: `4,2` · `3.000`; tiếng Anh ngược lại (đặc tả mục 5).
 */
data class NumberStyle(val decimalSeparator: Char, val groupSeparator: Char) {
    companion object {
        val VI = NumberStyle(decimalSeparator = ',', groupSeparator = '.')
        val EN = NumberStyle(decimalSeparator = '.', groupSeparator = ',')

        fun of(language: AppLanguage): NumberStyle = when (language) {
            AppLanguage.Vietnamese -> VI
            AppLanguage.English -> EN
        }
    }
}

/**
 * Nhãn thứ viết tắt và chữ "Hằng ngày". Lấy từ `strings.xml` ở tầng UI; [VI] dùng cho test và preview.
 */
data class WeekdayLabels(
    val short: Map<DayOfWeek, String>,
    val everyDay: String,
    val separator: String = ", ",
) {
    companion object {
        val VI = WeekdayLabels(
            short = mapOf(
                DayOfWeek.MONDAY to "T2",
                DayOfWeek.TUESDAY to "T3",
                DayOfWeek.WEDNESDAY to "T4",
                DayOfWeek.THURSDAY to "T5",
                DayOfWeek.FRIDAY to "T6",
                DayOfWeek.SATURDAY to "T7",
                DayOfWeek.SUNDAY to "CN",
            ),
            everyDay = "Hằng ngày",
        )
    }
}

/** Định dạng số và thời gian dùng chung (AGENTS.md §5, đặc tả mục 5). */
object GgFormat {

    /**
     * Kiểu số theo ngôn ngữ app đang dùng, là mặc định của mọi hàm định dạng số ở đây và của các ô nhập số. `GgPowerTheme`
     * đặt lại mỗi khi dựng giao diện (Activity được dựng lại khi đổi ngôn ngữ); test đổi thì phải trả về [NumberStyle.VI].
     */
    @Volatile
    var defaultStyle: NumberStyle = NumberStyle.VI

    /** `4.2, 1` → `4,2`; `3000.0, 0` → `3.000`. Làm tròn nửa lên; không bao giờ trả `-0`. */
    fun number(value: Double, decimals: Int = 0, style: NumberStyle = defaultStyle): String {
        require(decimals >= 0) { "decimals phải ≥ 0" }
        val rounded = BigDecimal.valueOf(value).setScale(decimals, RoundingMode.HALF_UP)
        val negative = rounded.signum() < 0
        val plain = rounded.abs().toPlainString()
        val intPart = plain.substringBefore('.')
        val fracPart = if (decimals > 0) plain.substringAfter('.') else ""
        val grouped = groupThousands(intPart, style.groupSeparator)
        val body = if (decimals > 0) grouped + style.decimalSeparator + fracPart else grouped
        return if (negative) "-$body" else body
    }

    /**
     * Số rút gọn cho nhãn trục và chú giải (prototype `fmt`): ≥ 10 làm tròn số nguyên; nhỏ hơn giữ tối đa
     * [maxDecimals] chữ số thập phân, bỏ số 0 thừa. 2.5 → "2,5"; 1.25 → "1,25"; 1000 → "1.000"; 0 → "0".
     */
    fun trimmed(value: Double, maxDecimals: Int = 2, style: NumberStyle = defaultStyle): String {
        if (kotlin.math.abs(value) >= 10) return number(value, 0, style)
        val full = number(value, maxDecimals, style)
        if (style.decimalSeparator !in full) return full
        return full.trimEnd('0').trimEnd(style.decimalSeparator)
    }

    fun number(value: Long, style: NumberStyle = defaultStyle): String {
        val grouped = groupThousands(kotlin.math.abs(value).toString(), style.groupSeparator)
        return if (value < 0) "-$grouped" else grouped
    }

    /** Có khoảng trắng trước đơn vị: `4,2 kW`, `76 %`, `42 °C`. */
    fun withUnit(value: Double, decimals: Int, unit: String, style: NumberStyle = defaultStyle): String =
        "${number(value, decimals, style)} $unit"

    fun withUnit(value: Long, unit: String, style: NumberStyle = defaultStyle): String =
        "${number(value, style)} $unit"

    /**
     * Nhiệt độ đo (luôn là °C từ thiết bị) theo đơn vị đã chọn ở màn 3: `32,5 °C` / `90,5 °F`. Cùng số chữ số thập
     * phân cho cả hai đơn vị (quyết 04/10/2026). [unitLabel]: "°C" / "°F" lấy từ `strings.xml`.
     */
    fun temperature(
        celsius: Double,
        unit: TemperatureUnit,
        decimals: Int,
        unitLabel: String,
        style: NumberStyle = defaultStyle,
    ): String = withUnit(unit.fromCelsius(celsius), decimals, unitLabel, style)

    /** Có dấu: `+1,1 kW` (dương = sạc / mua), `-0,8 kW`; giá trị làm tròn bằng 0 không có dấu. */
    fun signedWithUnit(value: Double, decimals: Int, unit: String, style: NumberStyle = defaultStyle): String {
        val text = number(value, decimals, style)
        val isZero = BigDecimal.valueOf(value).setScale(decimals, RoundingMode.HALF_UP).signum() == 0
        val signed = if (!isZero && value > 0) "+$text" else text
        return "$signed $unit"
    }

    /**
     * Năng lượng tự đổi đơn vị (đặc tả mục 5): dưới 100 kWh 1 chữ số thập phân ("14,2 kWh"), 100–999 làm tròn
     * ("435 kWh"), từ 1.000 kWh đổi sang MWh 2 chữ số ("5,50 MWh").
     */
    fun energy(kwh: Double, kwhUnit: String, mwhUnit: String, style: NumberStyle = defaultStyle): String = when {
        kotlin.math.abs(kwh) >= 1000 -> withUnit(kwh / 1000, 2, mwhUnit, style)
        kotlin.math.abs(kwh) >= 100 -> withUnit(kwh, 0, kwhUnit, style)
        else -> withUnit(kwh, 1, kwhUnit, style)
    }

    /** Khối lượng CO₂ tự đổi đơn vị: "10,4 kg", "274 kg", từ 1.000 kg đổi sang tấn "3,24 t". */
    fun mass(kg: Double, kgUnit: String, tonUnit: String, style: NumberStyle = defaultStyle): String = when {
        kotlin.math.abs(kg) >= 1000 -> withUnit(kg / 1000, 2, tonUnit, style)
        kotlin.math.abs(kg) >= 100 -> withUnit(kg, 0, kgUnit, style)
        else -> withUnit(kg, 1, kgUnit, style)
    }

    /** `48000, "đ"` → `48.000 đ`. */
    fun money(amount: Long, currency: String, style: NumberStyle = defaultStyle): String =
        withUnit(amount, currency, style)

    /** `24/09/2026`. */
    fun date(date: LocalDate): String = "${two(date.dayOfMonth)}/${two(date.monthValue)}/${date.year}"

    /** `09:41`. */
    fun time(time: LocalTime): String = "${two(time.hour)}:${two(time.minute)}"

    /** Đồng hồ đếm ngược phút:giây ("Gửi lại mã sau 00:45"); số âm coi như 0. */
    fun countdown(seconds: Int): String {
        val s = seconds.coerceAtLeast(0)
        return "${two(s / 60)}:${two(s % 60)}"
    }

    /** Thời lượng "3 giờ", "8 giờ 30 phút", "45 phút" (tóm tắt lịch TOU, prototype `dur`). */
    fun duration(minutes: Int, hourUnit: String, minuteUnit: String): String {
        val h = minutes / 60
        val m = minutes % 60
        return when {
            h == 0 -> "$m $minuteUnit"
            m == 0 -> "$h $hourUnit"
            else -> "$h $hourUnit $m $minuteUnit"
        }
    }

    /** Thời điểm ngắn `dd/MM HH:mm`: `24/09 09:41`. */
    fun dateTimeShort(dateTime: LocalDateTime): String =
        "${two(dateTime.dayOfMonth)}/${two(dateTime.monthValue)} ${time(dateTime.toLocalTime())}"

    /** Thời điểm đầy đủ `HH:mm · dd/MM/yyyy`: `07:12 · 24/09/2026`. */
    fun dateTimeFull(dateTime: LocalDateTime): String =
        "${time(dateTime.toLocalTime())} · ${date(dateTime.toLocalDate())}"

    /** Thời điểm có năm `dd/MM/yyyy HH:mm` (Lịch sử thay đổi màn 16): `18/09/2026 21:04`. */
    fun dateTimeLog(dateTime: LocalDateTime): String =
        "${date(dateTime.toLocalDate())} ${time(dateTime.toLocalTime())}"

    /** Khung giờ `HH:mm – HH:mm` (gạch ngang dài có khoảng trắng hai bên): `22:00 – 06:00`. */
    fun timeRange(start: LocalTime, end: LocalTime): String = "${time(start)} – ${time(end)}"

    /** Khung giờ gọn không khoảng trắng (dòng tóm tắt thẻ 16a): `09:00–12:00`. */
    fun timeRangeCompact(start: LocalTime, end: LocalTime): String = "${time(start)}–${time(end)}"

    /** Thứ viết tắt: `T2`…`T7`, `CN`. */
    fun weekdayShort(day: DayOfWeek, labels: WeekdayLabels = WeekdayLabels.VI): String =
        labels.short.getValue(day)

    /**
     * Các thứ áp dụng, theo thứ tự T2 → CN: `T2, T3, T5`; đủ 7 ngày → `Hằng ngày`; rỗng → chuỗi rỗng
     * (màn hình tự hiện câu "chưa chọn ngày nào").
     */
    fun weekdays(days: Set<DayOfWeek>, labels: WeekdayLabels = WeekdayLabels.VI): String = when (days.size) {
        7 -> labels.everyDay
        else -> DayOfWeek.entries.filter { it in days }.joinToString(labels.separator) { labels.short.getValue(it) }
    }

    private fun two(n: Int) = n.toString().padStart(2, '0')

    private fun groupThousands(digits: String, separator: Char): String {
        val sb = StringBuilder()
        digits.forEachIndexed { i, c ->
            if (i > 0 && (digits.length - i) % 3 == 0) sb.append(separator)
            sb.append(c)
        }
        return sb.toString()
    }
}
