package vn.ggpower.inverter.ui.data

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import vn.ggpower.chart.AxisSide
import vn.ggpower.chart.AxisTick
import vn.ggpower.chart.CartesianChart
import vn.ggpower.chart.CartesianChartModel
import vn.ggpower.chart.ChartGeometry
import vn.ggpower.chart.LineSeries
import vn.ggpower.chart.ValueAxis
import vn.ggpower.inverter.R
import vn.ggpower.inverter.domain.format.GgFormat
import vn.ggpower.inverter.domain.settings.TemperatureUnit
import vn.ggpower.inverter.domain.telemetry.FieldUnit
import vn.ggpower.inverter.domain.telemetry.HistoryField
import vn.ggpower.inverter.domain.telemetry.HistoryRange
import vn.ggpower.inverter.domain.telemetry.HistorySeries
import vn.ggpower.inverter.domain.telemetry.axisUnits
import vn.ggpower.inverter.domain.telemetry.canSelect
import vn.ggpower.inverter.domain.telemetry.niceRange
import vn.ggpower.inverter.ui.components.ButtonSize
import vn.ggpower.inverter.ui.components.GgCard
import vn.ggpower.inverter.ui.components.SecondaryButton
import vn.ggpower.inverter.ui.components.SegmentedControl
import vn.ggpower.inverter.ui.components.SegmentedSize
import vn.ggpower.inverter.ui.components.ggChartStyle
import vn.ggpower.inverter.ui.format.fieldUnitLabel
import vn.ggpower.inverter.ui.format.weekdayLabels
import vn.ggpower.inverter.ui.theme.GgRadius
import vn.ggpower.inverter.ui.theme.GgSpacing
import vn.ggpower.inverter.ui.theme.GgTheme

/** Cách hiển thị một trường: chữ trên chip, chữ ở chú giải (pin: "Dòng pack" / "Dòng pack (BMS)") và màu đường. */
data class FieldStyle(val chip: String, val legend: String, val color: Color)

class HistoryActions(
    val onRangeChange: (HistoryRange) -> Unit = {},
    val onToggleField: (HistoryField) -> Unit = {},
    val onSelectIndex: (Int) -> Unit = {},
    val onRetry: () -> Unit = {},
)

/**
 * Thẻ "Biểu đồ lịch sử" (DataInverter / DataBattery.dc.html; đặc tả §17.4; BO_SUNG_v3.md §12): tiêu đề + "tối đa 3
 * trường"; thời điểm đang chọn; bộ chọn 1 giờ · 24 giờ · 7 ngày; chip trường; đơn vị hai trục; biểu đồ có thanh kéo
 * (trục phải vẽ nét đứt); chú giải kèm giá trị tại điểm đang chọn. Đệm 14 16, cách 10.
 */
@Composable
fun HistoryCard(
    state: HistoryUiState,
    fields: List<HistoryField>,
    styleOf: (HistoryField) -> FieldStyle,
    actions: HistoryActions,
    temperatureUnit: TemperatureUnit = TemperatureUnit.Celsius,
) {
    val colors = GgTheme.colors
    val series = remember(state.series, fields, temperatureUnit) { state.series?.inTemperatureUnit(fields, temperatureUnit) }
    GgCard(contentPadding = PaddingValues(horizontal = 16.dp, vertical = 14.dp), verticalSpacing = 10.dp) {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    stringResource(R.string.history_title),
                    style = GgTheme.typography.body.copy(fontSize = 15.sp, lineHeight = 22.sp, fontWeight = FontWeight.SemiBold),
                    color = colors.ink,
                    modifier = Modifier.weight(1f).semantics { heading() },
                )
                Text(stringResource(R.string.history_max_fields), style = GgTheme.typography.caption.copy(fontWeight = FontWeight.Normal), color = colors.inkMuted)
            }
            Text(
                series?.let { stamp(it, state.index) } ?: " ",
                style = GgTheme.typography.caption.copy(fontWeight = FontWeight.Normal, fontFeatureSettings = "tnum"),
                color = colors.inkMuted,
            )
        }
        SegmentedControl(
            options = listOf(
                stringResource(R.string.history_range_hour),
                stringResource(R.string.history_range_day),
                stringResource(R.string.history_range_week),
            ),
            selectedIndex = state.range.ordinal,
            onSelect = { actions.onRangeChange(HistoryRange.entries[it]) },
            size = SegmentedSize.Regular,
            groupLabel = stringResource(R.string.history_range_group),
        )
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            fields.forEach { f ->
                FieldChip(styleOf(f).chip, selected = f in state.selected, enabled = canSelect(state.selected, f)) { actions.onToggleField(f) }
            }
        }
        when {
            series != null -> Chart(state, series, styleOf, actions.onSelectIndex, temperatureUnit)
            state.failed -> Column(
                Modifier.fillMaxWidth().height(170.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(GgSpacing.space3, Alignment.CenterVertically),
            ) {
                Text(stringResource(R.string.error_server), style = GgTheme.typography.bodySm, color = colors.inkMuted)
                SecondaryButton(stringResource(R.string.action_retry), onClick = actions.onRetry, size = ButtonSize.Medium)
            }
            else -> Box(Modifier.fillMaxWidth().height(170.dp).clip(RoundedCornerShape(GgRadius.radiusMd)).background(colors.surfaceSunken))
        }
        if (series != null) Legend(state, series, styleOf, temperatureUnit)
    }
}

/**
 * Chip trường (prototype): cao 32, bo tròn, đệm ngang 12, chữ 12/600. Đang chọn: nền `ink`, chữ trắng, dấu ✓ 14 dp;
 * không chọn: nền `surfaceRaised`, viền `lineStrong`. Không chọn thêm được: mờ 50 % (BO_SUNG_v3.md §12).
 */
@Composable
private fun FieldChip(text: String, selected: Boolean, enabled: Boolean, onClick: () -> Unit) {
    val colors = GgTheme.colors
    val shape = CircleShape
    // Chip cao 32 dp như thiết kế; Compose tự mở rộng vùng chạm của phần tử nhỏ tới 48 dp.
    Box(
        Modifier
            .alpha(if (enabled) 1f else 0.5f)
            .clip(shape)
            .toggleable(value = selected, enabled = enabled, role = Role.Checkbox) { onClick() },
        contentAlignment = Alignment.Center,
    ) {
        Row(
            Modifier
                .height(32.dp)
                .background(if (selected) colors.ink else colors.surfaceRaised)
                .then(if (selected) Modifier else Modifier.border(1.dp, colors.lineStrong, shape))
                .padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            if (selected) Icon(painterResource(R.drawable.ic_check), contentDescription = null, tint = colors.surfaceRaised, modifier = Modifier.size(14.dp))
            Text(text, style = GgTheme.typography.label.copy(fontSize = 12.sp), color = if (selected) colors.surfaceRaised else colors.ink)
        }
    }
}

@Composable
private fun Chart(
    state: HistoryUiState,
    series: HistorySeries,
    styleOf: (HistoryField) -> FieldStyle,
    onSelect: (Int) -> Unit,
    temperatureUnit: TemperatureUnit,
) {
    val colors = GgTheme.colors
    val (leftUnit, rightUnit) = axisUnits(state.selected)
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(Modifier.fillMaxWidth()) {
            val unitStyle = GgTheme.typography.caption.copy(fontSize = 11.sp, lineHeight = 14.sp, fontWeight = FontWeight.Normal)
            Text(leftUnit?.let { fieldUnitLabel(it, temperatureUnit) } ?: "", style = unitStyle, color = colors.inkMuted, modifier = Modifier.weight(1f))
            Text(rightUnit?.let { fieldUnitLabel(it, temperatureUnit) } ?: "", style = unitStyle, color = colors.inkMuted)
        }
        fun axis(unit: FieldUnit?): ValueAxis? {
            unit ?: return null
            val values = state.selected.filter { it.unit == unit }.flatMap { series.values[it.id].orEmpty() }
            val r = niceRange(values)
            val step = (r.endInclusive - r.start) / 3
            return ValueAxis(r.start, r.endInclusive, List(4) { AxisTick(r.start + it * step, "") })
        }
        val left = axis(leftUnit) ?: ValueAxis(0.0, 1.0, emptyList())
        val right = axis(rightUnit)
        val step = series.range.stepMinutes.toDouble()
        val xs = List(series.size) { it * step }
        val model = CartesianChartModel(
            xs = xs,
            xMin = 0.0,
            xMax = series.range.spanMinutes.toDouble(),
            xTicks = xTicks(series),
            left = left,
            right = right,
            series = state.selected.map { f ->
                val onRight = rightUnit != null && f.unit == rightUnit
                LineSeries(
                    values = series.values[f.id].orEmpty(),
                    color = styleOf(f).color,
                    axis = if (onRight) AxisSide.Right else AxisSide.Left,
                    dash = if (onRight) listOf(4.dp, 4.dp) else null,
                )
            },
        )
        CartesianChart(
            model = model,
            selectedIndex = state.index,
            onSelect = onSelect,
            style = ggChartStyle(),
            geometry = ChartGeometry.Compact,
            contentDescription = stringResource(R.string.history_chart_cd),
            selectionDescription = stamp(series, state.index),
        )
    }
}

@Composable
private fun Legend(state: HistoryUiState, series: HistorySeries, styleOf: (HistoryField) -> FieldStyle, temperatureUnit: TemperatureUnit) {
    val colors = GgTheme.colors
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        state.selected.forEach { f ->
            val style = styleOf(f)
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Box(Modifier.width(12.dp).height(3.dp).clip(CircleShape).background(style.color))
                Text(style.legend, style = GgTheme.typography.caption.copy(fontWeight = FontWeight.Normal), color = colors.ink, modifier = Modifier.weight(1f))
                Text(
                    formatValue(f.unit, series.values[f.id]?.getOrNull(state.index), temperatureUnit),
                    style = GgTheme.typography.caption.copy(fontWeight = FontWeight.SemiBold, fontFeatureSettings = "tnum"),
                    color = colors.ink,
                )
            }
        }
    }
}

/** Nhãn trục x: 1 giờ HH:mm mỗi 15 phút; 24 giờ 00 · 06 · 12 · 18 · 24; 7 ngày thứ của từng ngày. */
@Composable
private fun xTicks(series: HistorySeries): List<AxisTick> = when (series.range) {
    HistoryRange.Hour -> (0..60 step 15).map { AxisTick(it.toDouble(), GgFormat.time(series.start.plusMinutes(it.toLong()).toLocalTime())) }
    HistoryRange.Day -> listOf(0, 6, 12, 18, 24).map { AxisTick(it * 60.0, it.toString().padStart(2, '0')) }
    HistoryRange.Week -> {
        val labels = weekdayLabels()
        (0 until 7).map { d -> AxisTick(d * 1440.0 + 720.0, labels.short.getValue(series.start.plusDays(d.toLong()).dayOfWeek)) }
    }
}

/** Thời điểm điểm đang chọn `dd/MM HH:mm` (7 ngày điểm theo giờ nên luôn là `HH:00`). */
private fun stamp(series: HistorySeries, index: Int): String = GgFormat.dateTimeShort(series.timeAt(index))

/** Giá trị ở chú giải: W 0 số lẻ; nhiệt độ, V 1; Hz 2; % và mV 0; A có dấu, 1 số lẻ. null → "—". */
@Composable
private fun formatValue(unit: FieldUnit, value: Double?, temperatureUnit: TemperatureUnit): String {
    value ?: return stringResource(R.string.value_missing)
    val u = fieldUnitLabel(unit, temperatureUnit)
    return when (unit) {
        FieldUnit.Watt, FieldUnit.Percent, FieldUnit.MilliVolt -> GgFormat.withUnit(value, 0, u)
        FieldUnit.Celsius, FieldUnit.Volt -> GgFormat.withUnit(value, 1, u)
        FieldUnit.Hertz -> GgFormat.withUnit(value, 2, u)
        FieldUnit.Ampere -> GgFormat.signedWithUnit(value, 1, u)
    }
}
