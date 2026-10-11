package vn.ggpower.inverter.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import vn.ggpower.chart.AxisSide
import vn.ggpower.chart.AxisTick
import vn.ggpower.chart.BarSeries
import vn.ggpower.chart.CartesianChart
import vn.ggpower.chart.CartesianChartModel
import vn.ggpower.chart.LineSeries
import vn.ggpower.chart.StackedBarChart
import vn.ggpower.chart.StackedBarModel
import vn.ggpower.chart.ValueAxis
import vn.ggpower.inverter.R
import vn.ggpower.inverter.domain.format.GgFormat
import vn.ggpower.inverter.domain.home.DayHourTicks
import vn.ggpower.inverter.domain.home.PeriodSelection
import vn.ggpower.inverter.domain.home.StatsPeriod
import vn.ggpower.inverter.domain.home.barAxis
import vn.ggpower.inverter.domain.home.powerAxis
import vn.ggpower.inverter.domain.home.symmetricAxis
import vn.ggpower.inverter.ui.components.ChartLegend
import vn.ggpower.inverter.ui.components.GgCard
import vn.ggpower.inverter.ui.components.LegendEntry
import vn.ggpower.inverter.ui.components.LegendSwatch
import vn.ggpower.inverter.ui.components.SegmentedControl
import vn.ggpower.inverter.ui.components.SegmentedSize
import vn.ggpower.inverter.ui.components.ggChartStyle
import vn.ggpower.inverter.ui.theme.GgSpacing
import vn.ggpower.inverter.ui.theme.GgTheme
import java.time.LocalTime

/** Callback của phần thống kê. */
data class StatsActions(
    val onPeriodChange: (StatsPeriod) -> Unit = {},
    val onPrevious: () -> Unit = {},
    val onNext: () -> Unit = {},
    val onSelectPower: (Int) -> Unit = {},
    val onSelectBattery: (Int) -> Unit = {},
    val onSelectYield: (Int) -> Unit = {},
)

/** Mục 4.4–4.8: bộ chọn kỳ (cách nhau 4 dp, mũi tên cách biểu đồ 8 dp), 3 thẻ biểu đồ và thẻ chỉ số tổng hợp. */
@Composable
fun HomeStats(state: StatsUiState, actions: StatsActions) {
    Column(verticalArrangement = Arrangement.spacedBy(GgSpacing.space1)) {
        PeriodSelector(state, actions)
        Column(Modifier.padding(top = GgSpacing.space2), verticalArrangement = Arrangement.spacedBy(GgSpacing.space3)) {
            PowerChartCard(state, actions.onSelectPower)
            BatteryChartCard(state, actions.onSelectBattery)
            YieldChartCard(state, actions.onSelectYield)
            SummaryCard(state)
        }
    }
}

/** Segmented Ngày / Tháng / Năm (cỡ nhỏ) và hàng ‹ kỳ › (nút 44 × 40, nhãn 13/20 600). */
@Composable
private fun PeriodSelector(state: StatsUiState, actions: StatsActions) {
    val periods = StatsPeriod.entries
    SegmentedControl(
        options = listOf(stringResource(R.string.period_day), stringResource(R.string.period_month), stringResource(R.string.period_year)),
        selectedIndex = periods.indexOf(state.selection.period),
        onSelect = { actions.onPeriodChange(periods[it]) },
        size = SegmentedSize.Compact,
        groupLabel = stringResource(R.string.period_group),
    )
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = actions.onPrevious, modifier = Modifier.size(48.dp, 48.dp)) {
            Icon(painterResource(R.drawable.ic_chevron_left), stringResource(R.string.cd_period_previous), tint = GgTheme.colors.ink)
        }
        Text(
            periodLabel(state.selection),
            style = GgTheme.typography.bodySm.copy(fontWeight = FontWeight.SemiBold, fontFeatureSettings = "tnum"),
            color = GgTheme.colors.ink,
            modifier = Modifier.weight(1f),
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
        )
        IconButton(onClick = actions.onNext, enabled = state.canGoNext, modifier = Modifier.size(48.dp, 48.dp)) {
            Icon(
                painterResource(R.drawable.ic_chevron_right),
                stringResource(R.string.cd_period_next),
                tint = GgTheme.colors.ink,
                modifier = Modifier.size(24.dp).alpha(if (state.canGoNext) 1f else 0.3f),
            )
        }
    }
}

@Composable
private fun periodLabel(selection: PeriodSelection): String = when (selection.period) {
    StatsPeriod.Day -> GgFormat.date(selection.anchor)
    StatsPeriod.Month -> stringResource(
        R.string.period_month_label,
        "%02d/%d".format(selection.anchor.monthValue, selection.anchor.year),
    )
    StatsPeriod.Year -> stringResource(R.string.period_year_label, selection.anchor.year)
}

/** Thẻ biểu đồ: tiêu đề 18/26 600, dòng thời điểm 12/16 `inkMuted` (sát tiêu đề), chú giải, biểu đồ. */
@Composable
private fun ChartCard(title: String, stamp: String?, content: @Composable () -> Unit) {
    GgCard(verticalSpacing = GgSpacing.space3) {
        Column {
            Text(
                title,
                style = GgTheme.typography.body.copy(fontSize = 18.sp, lineHeight = 26.sp, fontWeight = FontWeight.SemiBold),
                color = GgTheme.colors.ink,
                modifier = Modifier.semantics { heading() },
            )
            if (stamp != null) {
                Text(stamp, style = GgTheme.typography.caption.copy(fontWeight = FontWeight.Normal, fontFeatureSettings = "tnum"), color = GgTheme.colors.inkMuted, modifier = Modifier.padding(top = 8.dp))
            }
        }
        content()
    }
}

@Composable
private fun PowerChartCard(state: StatsUiState, onSelect: (Int) -> Unit) =
    if (state.isDay) PowerDayCard(state, onSelect) else EnergyPeriodCard(state, onSelect)

@Composable
private fun BatteryChartCard(state: StatsUiState, onSelect: (Int) -> Unit) =
    if (state.isDay) BatteryDayCard(state, onSelect) else BatteryPeriodCard(state, onSelect)

/** Trục x theo cột của kỳ (Tháng / Năm): mỗi ngày / tháng một vị trí, nhãn như biểu đồ Sản lượng PV. */
@Composable
private fun bucketXAxis(selection: PeriodSelection): Triple<List<Double>, Double, List<AxisTick>> {
    val labels = selection.bucketLabels(stringResource(R.string.chart_hour_suffix), stringResource(R.string.chart_month_prefix))
    val xs = labels.indices.map { it.toDouble() }
    val ticks = labels.mapIndexedNotNull { i, l -> if (l.isEmpty()) null else AxisTick(i.toDouble(), l) }
    return Triple(xs, labels.size.toDouble(), ticks)
}

/** kWh: từ 100 trở lên làm tròn số nguyên, nhỏ hơn 1 chữ số thập phân. */
@Composable
private fun kwhText(v: Double, signed: Boolean = false): String {
    val unit = stringResource(R.string.unit_kwh)
    val decimals = if (kotlin.math.abs(v) >= 100) 0 else 1
    return if (signed) GgFormat.signedWithUnit(v, decimals, unit) else GgFormat.withUnit(v, decimals, unit)
}

/**
 * 4.5 · Năng lượng (Tháng / Năm): 4 đường kWh mỗi ngày / tháng — PV, lưới mua (+) / bán (−) ròng,
 * pin sạc (+) / xả (−) ròng, tải. Cùng màu, trục và thanh kéo như biểu đồ Công suất.
 */
@Composable
private fun EnergyPeriodCard(state: StatsUiState, onSelect: (Int) -> Unit) {
    val colors = GgTheme.colors
    val buckets = state.energyBuckets
    val i = state.powerIndex
    val b = buckets?.getOrNull(i)
    val missing = stringResource(R.string.value_missing)
    val pv = b?.let { kwhText(it.pvKwh) } ?: missing
    val grid = b?.let { kwhText(it.gridNetKwh) } ?: missing
    val bat = b?.let { kwhText(it.batteryNetKwh, signed = true) } ?: missing
    val load = b?.let { kwhText(it.loadKwh) } ?: missing
    val stamp = if (b != null) yieldStamp(state.selection, i) else null
    ChartCard(stringResource(R.string.chart_energy_title), stamp) {
        ChartLegend(
            listOf(
                LegendEntry(LegendSwatch.Line(colors.energyPv), stringResource(R.string.legend_pv), pv),
                LegendEntry(LegendSwatch.Line(colors.energyGrid), stringResource(R.string.legend_grid), grid),
                LegendEntry(LegendSwatch.Line(colors.energyBattery), stringResource(R.string.legend_battery), bat),
                LegendEntry(LegendSwatch.Line(colors.energyLoad), stringResource(R.string.legend_load), load),
            ),
        )
        if (buckets.isNullOrEmpty()) {
            ChartPlaceholder(174)
        } else {
            val (xs, xMax, ticks) = bucketXAxis(state.selection)
            val all = buckets.flatMap { listOf(it.pvKwh, it.gridNetKwh, it.batteryNetKwh, it.loadKwh) }
            val axis = powerAxis(all.min(), all.max())
            val model = CartesianChartModel(
                xs = xs, xMin = -0.5, xMax = xMax - 0.5, xTicks = ticks,
                left = ValueAxis(axis.min, axis.max, axis.ticks.map { AxisTick(it, GgFormat.trimmed(it)) }, baseline = 0.0),
                series = listOf(
                    LineSeries(buckets.map { it.pvKwh }, colors.energyPv),
                    LineSeries(buckets.map { it.gridNetKwh }, colors.energyGrid),
                    LineSeries(buckets.map { it.batteryNetKwh }, colors.energyBattery),
                    LineSeries(buckets.map { it.loadKwh }, colors.energyLoad),
                ),
            )
            CartesianChart(
                model, i, onSelect, ggChartStyle(),
                contentDescription = stringResource(R.string.cd_chart_energy),
                selectionDescription = stringResource(R.string.cd_power_selection, stamp.orEmpty(), pv, grid, bat, load),
            )
        }
    }
}

/**
 * 4.6 · Pin S16 Pre (Tháng / Năm): cột sạc (lên, màu energyBattery) và xả (xuống, cùng màu nhạt) mỗi ngày / tháng,
 * trục kWh đối xứng; SOC trung bình là vùng tô + đường đứt theo trục phải 0…100 % như biểu đồ ngày.
 */
@Composable
private fun BatteryPeriodCard(state: StatsUiState, onSelect: (Int) -> Unit) {
    val colors = GgTheme.colors
    val buckets = state.energyBuckets
    val i = state.batteryIndex
    val b = buckets?.getOrNull(i)
    val missing = stringResource(R.string.value_missing)
    val pct = stringResource(R.string.unit_percent)
    val charge = b?.let { kwhText(it.batteryChargeKwh) } ?: missing
    val discharge = b?.let { kwhText(it.batteryDischargeKwh) } ?: missing
    val soc = b?.let { GgFormat.withUnit(it.averageSocPercent.toLong(), pct) } ?: missing
    val stamp = if (b != null) yieldStamp(state.selection, i) else null
    val dischargeColor = colors.energyBattery.copy(alpha = 0.45f)
    ChartCard(stringResource(R.string.chart_battery_title), stamp) {
        ChartLegend(
            listOf(
                LegendEntry(LegendSwatch.Square(colors.energyBattery), stringResource(R.string.legend_battery_charge), charge),
                LegendEntry(LegendSwatch.Square(dischargeColor), stringResource(R.string.legend_battery_discharge), discharge),
                LegendEntry(LegendSwatch.Area(colors.energyBattery.copy(alpha = 0.18f), colors.primary), stringResource(R.string.legend_soc_average), soc),
            ),
        )
        if (buckets.isNullOrEmpty()) {
            ChartPlaceholder(174)
        } else {
            val (xs, xMax, ticks) = bucketXAxis(state.selection)
            val axis = symmetricAxis(buckets.maxOf { maxOf(it.batteryChargeKwh, it.batteryDischargeKwh) })
            val barWidth = if (buckets.size > 20) 4.dp else 8.dp
            val model = CartesianChartModel(
                xs = xs, xMin = -0.5, xMax = xMax - 0.5, xTicks = ticks,
                left = ValueAxis(axis.min, axis.max, axis.ticks.map { AxisTick(it, GgFormat.trimmed(it)) }, baseline = 0.0),
                right = ValueAxis(0.0, 100.0, listOf(0.0, 50.0, 100.0).map { AxisTick(it, GgFormat.withUnit(it.toLong(), pct)) }),
                series = listOf(
                    LineSeries(
                        buckets.map { it.averageSocPercent.toDouble() }, colors.primary, width = 1.5.dp, dash = listOf(4.dp, 3.dp),
                        axis = AxisSide.Right, areaColor = colors.energyBattery.copy(alpha = 0.12f),
                    ),
                ),
                bars = listOf(
                    BarSeries(buckets.map { it.batteryChargeKwh }, colors.energyBattery, width = barWidth),
                    BarSeries(buckets.map { -it.batteryDischargeKwh }, dischargeColor, width = barWidth),
                ),
            )
            CartesianChart(
                model, i, onSelect, ggChartStyle(),
                contentDescription = stringResource(R.string.cd_chart_battery_energy),
                selectionDescription = stringResource(R.string.cd_battery_energy_selection, stamp.orEmpty(), charge, discharge, soc),
            )
        }
    }
}

/** Khung chờ dữ liệu cao bằng biểu đồ. */
@Composable
private fun ChartPlaceholder(height: Int) {
    Box(Modifier.fillMaxWidth().height(height.dp).clip(RoundedCornerShape(10.dp)).background(GgTheme.colors.surfaceSunken))
}

private fun hourTicks(suffix: String) = DayHourTicks.map { AxisTick(it * 60.0, "$it$suffix") }

@Composable
private fun stampOf(state: StatsUiState, minute: Int): String =
    GgFormat.dateTimeShort(state.selection.anchor.atTime(LocalTime.of(minute / 60 % 24, minute % 60)))

/** 4.5 · Công suất (Ngày): 4 đường PV / Lưới / S16 Pre / Tải, trục kW co theo dữ liệu, đường 0 đậm. */
@Composable
private fun PowerDayCard(state: StatsUiState, onSelect: (Int) -> Unit) {
    val colors = GgTheme.colors
    val samples = state.curves?.power
    val i = state.powerIndex
    val s = samples?.getOrNull(i)
    val kw = stringResource(R.string.unit_kw)
    val missing = stringResource(R.string.value_missing)
    val pv = s?.let { GgFormat.withUnit(it.pvKw, 1, kw) } ?: missing
    val grid = s?.let { GgFormat.withUnit(it.gridKw, 1, kw) } ?: missing
    val bat = s?.let { GgFormat.signedWithUnit(it.batteryKw, 1, kw) } ?: missing
    val load = s?.let { GgFormat.withUnit(it.loadKw, 1, kw) } ?: missing
    val stamp = s?.let { stampOf(state, it.minute) }
    ChartCard(stringResource(R.string.chart_power_title), stamp) {
        ChartLegend(
            listOf(
                LegendEntry(LegendSwatch.Line(colors.energyPv), stringResource(R.string.legend_pv), pv),
                LegendEntry(LegendSwatch.Line(colors.energyGrid), stringResource(R.string.legend_grid), grid),
                LegendEntry(LegendSwatch.Line(colors.energyBattery), stringResource(R.string.legend_battery), bat),
                LegendEntry(LegendSwatch.Line(colors.energyLoad), stringResource(R.string.legend_load), load),
            ),
        )
        if (samples.isNullOrEmpty()) {
            ChartPlaceholder(174)
        } else {
            val all = samples.flatMap { listOf(it.pvKw, it.gridKw, it.batteryKw, it.loadKw) }
            val axis = powerAxis(all.min(), all.max())
            val model = CartesianChartModel(
                xs = samples.map { it.minute.toDouble() },
                xMin = 0.0, xMax = 1440.0,
                xTicks = hourTicks(stringResource(R.string.chart_hour_suffix)),
                left = ValueAxis(axis.min, axis.max, axis.ticks.map { AxisTick(it, GgFormat.trimmed(it)) }, baseline = 0.0),
                series = listOf(
                    LineSeries(samples.map { it.pvKw }, colors.energyPv),
                    LineSeries(samples.map { it.gridKw }, colors.energyGrid),
                    LineSeries(samples.map { it.batteryKw }, colors.energyBattery),
                    LineSeries(samples.map { it.loadKw }, colors.energyLoad),
                ),
            )
            CartesianChart(
                model, i, onSelect, ggChartStyle(),
                contentDescription = stringResource(R.string.cd_chart_power),
                selectionDescription = stringResource(R.string.cd_power_selection, stamp.orEmpty(), pv, grid, bat, load),
            )
        }
    }
}

/** 4.6 · Pin S16 Pre (Ngày): dòng BMS (A, trục trái −40…40) và SOC (%, trục phải 0…100, vùng tô + đường đứt). */
@Composable
private fun BatteryDayCard(state: StatsUiState, onSelect: (Int) -> Unit) {
    val colors = GgTheme.colors
    val samples = state.curves?.battery
    val i = state.batteryIndex
    val s = samples?.getOrNull(i)
    val missing = stringResource(R.string.value_missing)
    val pct = stringResource(R.string.unit_percent)
    val amp = s?.let { GgFormat.signedWithUnit(it.currentA, 1, stringResource(R.string.unit_ampere)) } ?: missing
    val soc = s?.let { GgFormat.withUnit(it.socPercent.toLong(), pct) } ?: missing
    val stamp = s?.let { stampOf(state, it.minute) }
    val areaFill = colors.energyBattery.copy(alpha = 0.12f)
    ChartCard(stringResource(R.string.chart_battery_title), stamp) {
        ChartLegend(
            listOf(
                LegendEntry(LegendSwatch.Line(colors.energyBattery), stringResource(R.string.legend_bms_current), amp),
                LegendEntry(LegendSwatch.Area(colors.energyBattery.copy(alpha = 0.18f), colors.primary), stringResource(R.string.legend_soc), soc),
            ),
        )
        if (samples.isNullOrEmpty()) {
            ChartPlaceholder(174)
        } else {
            val model = CartesianChartModel(
                xs = samples.map { it.minute.toDouble() },
                xMin = 0.0, xMax = 1440.0,
                xTicks = hourTicks(stringResource(R.string.chart_hour_suffix)),
                left = ValueAxis(-40.0, 40.0, listOf(-40.0, -20.0, 0.0, 20.0, 40.0).map { AxisTick(it, GgFormat.trimmed(it)) }, baseline = 0.0),
                right = ValueAxis(0.0, 100.0, listOf(0.0, 50.0, 100.0).map { AxisTick(it, GgFormat.withUnit(it.toLong(), pct)) }),
                series = listOf(
                    LineSeries(
                        samples.map { it.socPercent.toDouble() }, colors.primary, width = 1.5.dp, dash = listOf(4.dp, 3.dp),
                        axis = AxisSide.Right, areaColor = areaFill,
                    ),
                    LineSeries(samples.map { it.currentA }, colors.energyBattery),
                ),
            )
            CartesianChart(
                model, i, onSelect, ggChartStyle(),
                contentDescription = stringResource(R.string.cd_chart_battery),
                selectionDescription = stringResource(R.string.cd_battery_selection, stamp.orEmpty(), amp, soc),
            )
        }
    }
}

/** 4.7 · Sản lượng PV: cột chồng tiêu thụ / sạc pin / lên lưới, đường đứt hệ số hiệu quả (trục phải). */
@Composable
private fun YieldChartCard(state: StatsUiState, onSelect: (Int) -> Unit) {
    val colors = GgTheme.colors
    val buckets = state.yieldBuckets
    val i = state.yieldIndex
    val b = buckets?.getOrNull(i)
    val kwh = stringResource(R.string.unit_kwh)
    val missing = stringResource(R.string.value_missing)
    fun energy(v: Double) = if (v >= 100) GgFormat.withUnit(v, 0, kwh) else GgFormat.withUnit(v, 1, kwh)
    val efficiency = buckets?.mapIndexed { k, it -> it.totalKwh / state.selection.bucketHours(k) }
    val self = b?.let { energy(it.selfKwh) } ?: missing
    val charge = b?.let { energy(it.chargeKwh) } ?: missing
    val export = b?.let { energy(it.exportKwh) } ?: missing
    val eff = efficiency?.getOrNull(i)?.let { GgFormat.withUnit(it, 2, stringResource(R.string.unit_kw)) } ?: missing
    val stamp = if (b != null) yieldStamp(state.selection, i) else null
    ChartCard(stringResource(R.string.chart_yield_title), stamp) {
        ChartLegend(
            listOf(
                LegendEntry(LegendSwatch.Square(colors.energyPv), stringResource(R.string.legend_pv_self), self),
                LegendEntry(LegendSwatch.Square(colors.energyBattery), stringResource(R.string.legend_pv_charge), charge),
                LegendEntry(LegendSwatch.Square(colors.energyGrid), stringResource(R.string.legend_pv_export), export),
                LegendEntry(LegendSwatch.Dashed(colors.lineStrong.copy(alpha = 0.35f)), stringResource(R.string.legend_efficiency), eff),
            ),
            valueSize = 11,
        )
        if (buckets.isNullOrEmpty() || efficiency == null) {
            ChartPlaceholder(172)
        } else {
            val left = barAxis(buckets.maxOf { it.totalKwh })
            val right = barAxis(efficiency.max())
            val model = StackedBarModel(
                segments = buckets.map { listOf(it.selfKwh, it.chargeKwh, it.exportKwh) },
                segmentColors = listOf(colors.energyPv, colors.energyBattery, colors.energyGrid),
                labels = state.selection.bucketLabels(stringResource(R.string.chart_hour_suffix), stringResource(R.string.chart_month_prefix)),
                leftMax = left.max,
                leftTicks = left.ticks.map { AxisTick(it, GgFormat.trimmed(it)) },
                barWidth = if (buckets.size > 20) 5.dp else 12.dp,
                overlay = efficiency,
                overlayColor = colors.lineStrong.copy(alpha = 0.35f),
                rightMax = right.max,
                rightTicks = right.ticks.map { AxisTick(it, GgFormat.trimmed(it)) },
            )
            StackedBarChart(
                model, i, onSelect, ggChartStyle(barChart = true),
                contentDescription = stringResource(R.string.cd_chart_yield),
                selectionDescription = stringResource(R.string.cd_yield_selection, stamp.orEmpty(), self, charge, export),
            )
        }
    }
}

/** "24/09 08:00 – 10:00" (ngày) · "24/09" (tháng) · "Tháng 09" (năm). */
@Composable
private fun yieldStamp(selection: PeriodSelection, index: Int): String {
    val (start, end) = selection.bucketRange(index)
    return when (selection.period) {
        StatsPeriod.Day -> "%02d/%02d ".format(start.dayOfMonth, start.monthValue) + GgFormat.timeRange(start.toLocalTime(), end.toLocalTime())
        StatsPeriod.Month -> "%02d/%02d".format(start.dayOfMonth, start.monthValue)
        StatsPeriod.Year -> stringResource(R.string.period_month_label, "%02d".format(start.monthValue))
    }
}

/**
 * 4.8 · Chỉ số tổng hợp theo kỳ: thẻ đệm 4 / 16 dp, mỗi dòng đệm dọc 12 dp, icon 20 dp cách nhãn 12 dp, nhãn 15/24,
 * giá trị 15/24 600 số đều; các dòng ngăn bằng vạch 1 dp màu `line`. Đơn vị tự đổi theo độ lớn (kWh → MWh, kg → t).
 */
@Composable
private fun SummaryCard(state: StatsUiState) {
    val colors = GgTheme.colors
    val s = state.summary
    val missing = stringResource(R.string.value_missing)
    val rows = listOf(
        Triple(R.drawable.ic_energy_load to colors.energyLoad, R.string.summary_consumption,
            s?.let { GgFormat.energy(it.consumptionKwh, stringResource(R.string.unit_kwh), stringResource(R.string.unit_mwh)) } ?: missing),
        Triple(R.drawable.ic_shield_check to colors.primary, R.string.summary_self_use,
            s?.let { GgFormat.withUnit(it.selfUsePercent.toLong(), stringResource(R.string.unit_percent)) } ?: missing),
        Triple(R.drawable.ic_money to colors.primary, R.string.summary_saving,
            s?.let { GgFormat.money(it.savingVnd, stringResource(R.string.unit_vnd)) } ?: missing),
        Triple(R.drawable.ic_leaf to colors.energyBattery, R.string.summary_co2,
            s?.let { GgFormat.mass(it.co2Kg, stringResource(R.string.unit_kg), stringResource(R.string.unit_ton)) } ?: missing),
    )
    GgCard(contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = GgSpacing.space4, vertical = GgSpacing.space1)) {
        rows.forEach { (icon, label, value) ->
            Row(
                Modifier.fillMaxWidth().padding(vertical = GgSpacing.space3).semantics(mergeDescendants = true) {},
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(GgSpacing.space3),
            ) {
                Icon(painterResource(icon.first), contentDescription = null, tint = icon.second, modifier = Modifier.size(20.dp))
                Text(stringResource(label), style = GgTheme.typography.body, color = colors.ink, modifier = Modifier.weight(1f))
                Text(
                    value,
                    style = GgTheme.typography.body.copy(fontWeight = FontWeight.SemiBold, fontFeatureSettings = "tnum"),
                    color = colors.ink,
                )
            }
        }
    }
}
