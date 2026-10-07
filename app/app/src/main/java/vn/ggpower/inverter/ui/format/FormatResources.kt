package vn.ggpower.inverter.ui.format

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import vn.ggpower.inverter.R
import vn.ggpower.inverter.domain.format.GgFormat
import vn.ggpower.inverter.domain.format.WeekdayLabels
import vn.ggpower.inverter.domain.settings.TemperatureUnit
import vn.ggpower.inverter.domain.telemetry.FieldUnit
import java.time.DayOfWeek

/** Nhãn thứ cho [vn.ggpower.inverter.domain.format.GgFormat.weekdays], lấy từ `strings.xml` theo ngôn ngữ đang chọn. */
@Composable
fun weekdayLabels(): WeekdayLabels = WeekdayLabels(
    short = mapOf(
        DayOfWeek.MONDAY to stringResource(R.string.weekday_mon),
        DayOfWeek.TUESDAY to stringResource(R.string.weekday_tue),
        DayOfWeek.WEDNESDAY to stringResource(R.string.weekday_wed),
        DayOfWeek.THURSDAY to stringResource(R.string.weekday_thu),
        DayOfWeek.FRIDAY to stringResource(R.string.weekday_fri),
        DayOfWeek.SATURDAY to stringResource(R.string.weekday_sat),
        DayOfWeek.SUNDAY to stringResource(R.string.weekday_sun),
    ),
    everyDay = stringResource(R.string.weekday_every_day),
)

/** "°C" / "°F" theo cài đặt ở màn 3. */
@Composable
fun temperatureUnitLabel(unit: TemperatureUnit): String = stringResource(
    when (unit) {
        TemperatureUnit.Celsius -> R.string.unit_celsius
        TemperatureUnit.Fahrenheit -> R.string.unit_fahrenheit
    },
)

/** Nhãn đơn vị của một đại lượng; nhiệt độ theo cài đặt °C/°F (giá trị phải đã đổi sang [temperatureUnit]). */
@Composable
fun fieldUnitLabel(unit: FieldUnit, temperatureUnit: TemperatureUnit): String = when (unit) {
    FieldUnit.Watt -> stringResource(R.string.unit_watt)
    FieldUnit.Celsius -> temperatureUnitLabel(temperatureUnit)
    FieldUnit.Volt -> stringResource(R.string.unit_volt)
    FieldUnit.Hertz -> stringResource(R.string.unit_hertz)
    FieldUnit.Percent -> stringResource(R.string.unit_percent)
    FieldUnit.Ampere -> stringResource(R.string.unit_ampere)
    FieldUnit.MilliVolt -> stringResource(R.string.unit_millivolt)
}

/** Nhiệt độ đo (°C từ thiết bị) hiện theo cài đặt: [GgFormat.temperature]. */
@Composable
fun temperatureText(celsius: Double, unit: TemperatureUnit, decimals: Int = 0): String =
    GgFormat.temperature(celsius, unit, decimals, temperatureUnitLabel(unit))
