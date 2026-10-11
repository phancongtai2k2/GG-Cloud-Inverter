package vn.ggpower.inverter.ui.data

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import vn.ggpower.inverter.R
import vn.ggpower.inverter.data.telemetry.FakeHistory
import vn.ggpower.inverter.data.telemetry.FakeTelemetryRepository
import vn.ggpower.inverter.domain.format.GgFormat
import vn.ggpower.inverter.domain.settings.TemperatureUnit
import vn.ggpower.inverter.domain.telemetry.BatteryTelemetry
import vn.ggpower.inverter.domain.telemetry.BmsFlag
import vn.ggpower.inverter.domain.telemetry.BmsFlagKind
import vn.ggpower.inverter.domain.telemetry.CurrentLimitState
import vn.ggpower.inverter.domain.telemetry.DeviceKind
import vn.ggpower.inverter.domain.telemetry.HistoryField
import vn.ggpower.inverter.domain.telemetry.HistoryFields
import vn.ggpower.inverter.domain.telemetry.HistoryRange
import vn.ggpower.inverter.domain.telemetry.currentLimitState
import vn.ggpower.inverter.ui.components.GgCard
import vn.ggpower.inverter.ui.components.ScreenHeader
import vn.ggpower.inverter.ui.components.SegmentedControl
import vn.ggpower.inverter.ui.components.SegmentedSize
import vn.ggpower.inverter.ui.components.StatusChip
import vn.ggpower.inverter.ui.components.StatusTone
import vn.ggpower.inverter.ui.theme.GgPowerTheme
import vn.ggpower.inverter.ui.theme.GgSpacing
import vn.ggpower.inverter.ui.theme.GgTheme
import java.time.LocalDateTime
import kotlin.math.abs

@Composable
fun BatteryDataRoute(onBack: () -> Unit, viewModel: BatteryDataViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val history by viewModel.historyState.collectAsStateWithLifecycle()
    val showAllFlags by viewModel.showAllFlags.collectAsStateWithLifecycle()
    val temperatureUnit by viewModel.temperatureUnit.collectAsStateWithLifecycle()
    BatteryDataScreen(
        state, onBack, viewModel::onRefresh, viewModel::onGroupChange,
        temperatureUnit = temperatureUnit,
        showAllFlags = showAllFlags,
        onToggleAllFlags = viewModel::onToggleAllFlags,
        history = history,
        historyActions = HistoryActions(viewModel::onRangeChange, viewModel::onToggleField, viewModel::onSelectIndex, viewModel::onHistoryRetry),
    )
}

/**
 * Màn 17 · Theo dõi dữ liệu – Pin S16 Pre (DataBattery.png, đặc tả §17.3–17.5). Số liệu gộp của cả dàn pin, đọc qua
 * inverter (quyết 02/10/2026). Năm nhóm; Tổng quan có dòng lệch điện áp, Giới hạn có đồng hồ hai cung, Cell có ghi chú
 * pha 2, Bảo vệ có danh sách cờ; cuối màn là biểu đồ lịch sử dùng chung.
 */
@Composable
fun BatteryDataScreen(
    state: BatteryDataUiState,
    onBack: () -> Unit,
    onRefresh: () -> Unit,
    onGroupChange: (BatteryGroup) -> Unit,
    modifier: Modifier = Modifier,
    temperatureUnit: TemperatureUnit = TemperatureUnit.Celsius,
    showAllFlags: Boolean = false,
    onToggleAllFlags: () -> Unit = {},
    history: HistoryUiState? = null,
    historyActions: HistoryActions = HistoryActions(),
) {
    Column(modifier.fillMaxSize().background(GgTheme.colors.surface).statusBarsPadding()) {
        ScreenHeader(title = stringResource(R.string.data_title), onBack = onBack) { RefreshButton(onRefresh) }
        when (state) {
            LiveUiState.Loading -> DataLoadingBlocks()
            is LiveUiState.Error -> DataErrorBlock(state.noConnection, onRefresh)
            is LiveUiState.Content -> {
                val t = state.data
                DeviceLine(t.name, t.serial, t.status, t.updatedAt, state.now)
                Column(
                    Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .navigationBarsPadding()
                        .padding(start = GgSpacing.space5, end = GgSpacing.space5, bottom = GgSpacing.space6),
                    verticalArrangement = Arrangement.spacedBy(GgSpacing.space3),
                ) {
                    SegmentedControl(
                        options = listOf(
                            stringResource(R.string.bat_group_overview),
                            stringResource(R.string.bat_group_limits),
                            stringResource(R.string.bat_group_cells),
                            stringResource(R.string.bat_group_protection),
                            stringResource(R.string.bat_group_versions),
                        ),
                        selectedIndex = state.group.ordinal,
                        onSelect = { onGroupChange(BatteryGroup.entries[it]) },
                        size = SegmentedSize.GroupsDense,
                        groupLabel = stringResource(R.string.data_groups),
                    )
                    when (state.group) {
                        BatteryGroup.Overview -> DeviationBox(t)
                        BatteryGroup.Limits -> CurrentGauge(t)
                        else -> Unit
                    }
                    DataRowsCard(batteryRows(state.group, t, temperatureUnit))
                    when (state.group) {
                        BatteryGroup.Cells -> CellChartNote()
                        BatteryGroup.Protection -> FlagsCard(t.flags.orEmpty(), showAllFlags, onToggleAllFlags)
                        else -> Unit
                    }
                    if (history != null) HistoryCard(history, HistoryFields.of(DeviceKind.Battery), batteryFieldStyle(), historyActions, temperatureUnit)
                }
            }
        }
    }
}

// ---------- Nhóm A — dòng lệch điện áp ----------

/**
 * "Lệch điện áp inverter đo / BMS báo: 0,2 V" + chip (§17.3 nhóm A). Bình thường: nền `primarySoft`, chip xanh;
 * lệch quá 1 V: nền `warningSoft`, chip vàng "Cần kiểm tra" (BO_SUNG_v3.md §14). Đệm 10 14, bo 14, chữ 13/18.
 */
@Composable
private fun DeviationBox(t: BatteryTelemetry) {
    val colors = GgTheme.colors
    val high = t.voltageDeviationHigh
    val value = valueOrDash(t.voltageDeviationV, 1, stringResource(R.string.unit_volt))
    val text = stringResource(R.string.bat_deviation, value)
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(if (high) colors.warningSoft else colors.primarySoft)
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        val bold = text.lastIndexOf(value)
        Text(
            buildAnnotatedString {
                append(text.substring(0, bold))
                withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { append(value) }
                append(text.substring(bold + value.length))
            },
            style = GgTheme.typography.bodySm.copy(lineHeight = 18.sp),
            color = colors.ink,
            modifier = Modifier.weight(1f),
        )
        StatusChip(
            stringResource(if (high) R.string.bat_deviation_high else R.string.bat_deviation_ok),
            if (high) StatusTone.Warning else StatusTone.Success,
        )
    }
}

// ---------- Nhóm B — đồng hồ hai cung ----------

/**
 * Đồng hồ hai cung (DataBattery.dc.html, khung 280 × 160): nửa vòng tròn tâm (140, 140) bán kính 110, nét 16;
 * nửa trái 0 → giới hạn xả, nửa phải 0 → giới hạn sạc; cung giá trị từ đỉnh tới dòng hiện tại, xanh khi trong giới
 * hạn, cam khi chạm giới hạn (§17.3 nhóm B). Vạch 0 ở đỉnh; nhãn ±giới hạn ở hai đầu; giá trị 30/700 và trạng thái 12.
 */
@Composable
private fun CurrentGauge(t: BatteryTelemetry) {
    val colors = GgTheme.colors
    val measurer = rememberTextMeasurer()
    val current = t.packCurrentA
    val limitState = current?.let { currentLimitState(it, t.chargeLimitCurrentA, t.dischargeLimitCurrentA) }
    val limited = limitState == CurrentLimitState.LimitedCharge || limitState == CurrentLimitState.LimitedDischarge
    val amp = stringResource(R.string.unit_ampere)
    val valueText = current?.let { GgFormat.signedWithUnit(it, 1, amp) } ?: stringResource(R.string.value_missing)
    val stateText = when (limitState) {
        CurrentLimitState.Charging -> stringResource(R.string.bat_gauge_charging)
        CurrentLimitState.Discharging -> stringResource(R.string.bat_gauge_discharging)
        CurrentLimitState.Idle -> stringResource(R.string.bat_gauge_idle)
        CurrentLimitState.LimitedCharge, CurrentLimitState.LimitedDischarge -> stringResource(R.string.bat_gauge_limited)
        null -> ""
    }
    val leftLabel = t.dischargeLimitCurrentA?.let { GgFormat.signedWithUnit(-it, 0, amp) } ?: ""
    val rightLabel = t.chargeLimitCurrentA?.let { GgFormat.signedWithUnit(it, 0, amp) } ?: ""
    val track = colors.surfaceSunken
    val arc = if (limited) colors.energyPv else colors.energyBattery
    val tick = colors.inkMuted
    val small = TextStyle(fontFamily = GgTheme.typography.caption.fontFamily, fontSize = 11.sp, color = colors.inkMuted)
    val big = GgTheme.typography.body.copy(fontSize = 30.sp, lineHeight = 36.sp, fontWeight = FontWeight.Bold, color = colors.ink, fontFeatureSettings = "tnum")
    val sub = GgTheme.typography.caption.copy(fontWeight = FontWeight.Normal, color = colors.inkMuted)
    val cd = "$valueText. $stateText"
    GgCard(verticalSpacing = GgSpacing.space2) {
        Canvas(
            Modifier
                .size(280.dp, 160.dp)
                .align(Alignment.CenterHorizontally)
                .semantics { contentDescription = cd },
        ) {
            val d = density
            val topLeft = Offset(30 * d, 30 * d)
            val arcSize = Size(220 * d, 220 * d)
            val stroke = Stroke(width = 16 * d, cap = StrokeCap.Round)
            drawArc(track, 180f, 180f, false, topLeft, arcSize, style = stroke)
            if (current != null && limitState != CurrentLimitState.Idle) {
                val limit = if (current > 0) t.chargeLimitCurrentA else t.dischargeLimitCurrentA
                val fraction = limit?.let { (abs(current) / it).coerceIn(0.0, 1.0) } ?: 0.0
                val sweep = (90 * fraction).toFloat() * if (current > 0) 1 else -1
                drawArc(arc, 270f, sweep, false, topLeft, arcSize, style = stroke)
            }
            drawLine(tick, Offset(140 * d, 22 * d), Offset(140 * d, 40 * d), strokeWidth = 2 * d)
            fun centered(text: String, style: TextStyle, cx: Float, baseline: Float) {
                if (text.isEmpty()) return
                val layout = measurer.measure(text, style)
                drawText(layout, topLeft = Offset(cx * d - layout.size.width / 2f, baseline * d - layout.firstBaseline))
            }
            centered(leftLabel, small, 30f, 158f)
            centered(rightLabel, small, 250f, 158f)
            centered(valueText, big, 140f, 112f)
            centered(stateText, sub, 140f, 134f)
        }
        Row(Modifier.fillMaxWidth()) {
            val label = GgTheme.typography.caption.copy(fontWeight = FontWeight.Normal)
            Text(stringResource(R.string.bat_gauge_discharge_limit), style = label, color = colors.inkMuted, modifier = Modifier.weight(1f))
            Text(stringResource(R.string.bat_gauge_charge_limit), style = label, color = colors.inkMuted)
        }
        Text(stringResource(R.string.bat_gauge_note), style = GgTheme.typography.caption.copy(fontWeight = FontWeight.Normal), color = colors.inkMuted)
    }
}

// ---------- Nhóm C — ghi chú pha 2 ----------

/** Khung nét đứt `sliderTick`, bo 16, đệm 20 16: biểu đồ từng cell để pha 2 (§17.3 nhóm C). */
@Composable
private fun CellChartNote() {
    val colors = GgTheme.colors
    val border = colors.sliderTick
    Column(
        Modifier
            .fillMaxWidth()
            .drawBehind {
                val s = 1.dp.toPx()
                drawRoundRect(
                    border, topLeft = Offset(s / 2, s / 2), size = Size(size.width - s, size.height - s),
                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(16.dp.toPx()),
                    style = Stroke(width = s, pathEffect = PathEffect.dashPathEffect(floatArrayOf(4.dp.toPx(), 3.dp.toPx()))),
                )
            }
            .padding(horizontal = 16.dp, vertical = 20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text(stringResource(R.string.bat_cell_chart_title), style = GgTheme.typography.bodySm.copy(fontWeight = FontWeight.SemiBold), color = colors.ink, textAlign = TextAlign.Center)
        Text(stringResource(R.string.bat_cell_chart_body), style = GgTheme.typography.caption.copy(fontWeight = FontWeight.Normal), color = colors.inkMuted, textAlign = TextAlign.Center)
    }
}

// ---------- Nhóm D — cờ BMS ----------

/**
 * Cờ BMS (§17.3 nhóm D): "Cờ đang bật (n)" 14/20 600; chip theo mức độ (bảo vệ đỏ, cảnh báo vàng); loại không có cờ
 * bật thì một dòng "…: không có cờ nào đang bật."; nút "Xem tất cả trạng thái (64 cờ)" mở rộng ngay trong thẻ
 * (BO_SUNG_v3.md §13). Đệm 14 16, cách 10.
 */
@Composable
private fun FlagsCard(flags: List<BmsFlag>, showAll: Boolean, onToggle: () -> Unit) {
    val colors = GgTheme.colors
    val active = flags.filter { it.active }.sortedBy { it.kind != BmsFlagKind.Protect }
    GgCard(contentPadding = PaddingValues(horizontal = 16.dp, vertical = 14.dp), verticalSpacing = 10.dp) {
        Text(
            stringResource(R.string.bat_flags_active, active.size),
            style = GgTheme.typography.body.copy(fontSize = 14.sp, lineHeight = 20.sp, fontWeight = FontWeight.SemiBold),
            color = colors.ink,
        )
        if (active.isNotEmpty()) {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                active.forEach { f ->
                    StatusChip(
                        stringResource(R.string.bat_flag_chip, kindLabel(f.kind), flagName(f)),
                        if (f.kind == BmsFlagKind.Protect) StatusTone.Danger else StatusTone.Warning,
                    )
                }
            }
        }
        BmsFlagKind.entries.filter { k -> active.none { it.kind == k } }.forEach { k ->
            Text(stringResource(R.string.bat_flag_none, kindLabel(k)), style = GgTheme.typography.caption.copy(fontWeight = FontWeight.Normal), color = colors.inkMuted)
        }
        if (flags.isNotEmpty()) {
            Text(
                if (showAll) stringResource(R.string.bat_flags_collapse) else stringResource(R.string.bat_flags_show_all, flags.size),
                style = GgTheme.typography.bodySm.copy(fontWeight = FontWeight.SemiBold),
                color = colors.primary,
                modifier = Modifier
                    .heightIn(min = 40.dp)
                    .clickable(role = Role.Button, onClick = onToggle)
                    .padding(vertical = 10.dp),
            )
        }
        if (showAll) {
            BmsFlagKind.entries.forEach { k ->
                val ofKind = flags.filter { it.kind == k }.sortedBy { it.bit }
                Text(
                    stringResource(R.string.bat_flags_section, kindLabel(k), ofKind.size),
                    style = GgTheme.typography.bodySm.copy(fontWeight = FontWeight.SemiBold),
                    color = colors.ink,
                )
                Column {
                    ofKind.forEachIndexed { i, f ->
                        Row(Modifier.fillMaxWidth().height(36.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text(flagName(f), style = GgTheme.typography.bodySm, color = colors.ink, modifier = Modifier.weight(1f))
                            if (f.active) {
                                StatusChip(stringResource(R.string.bat_flag_on), if (k == BmsFlagKind.Protect) StatusTone.Danger else StatusTone.Warning)
                            } else {
                                Text(stringResource(R.string.bat_flag_off), style = GgTheme.typography.bodySm, color = colors.inkMuted)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun kindLabel(kind: BmsFlagKind): String =
    stringResource(if (kind == BmsFlagKind.Protect) R.string.bat_flag_protect else R.string.bat_flag_alarm)

@Composable
private fun flagName(f: BmsFlag): String = f.name ?: stringResource(
    if (f.kind == BmsFlagKind.Protect) R.string.bat_flag_raw_protect else R.string.bat_flag_raw_alarm, f.bit,
)

// ---------- Các dòng giá trị ----------

/** Các dòng của từng nhóm, đúng thứ tự và thanh ghi của đặc tả §17.3 / DataBattery.dc.html. */
@Composable
private fun batteryRows(group: BatteryGroup, t: BatteryTelemetry, temperatureUnit: TemperatureUnit): List<DataRow> {
    val dash = stringResource(R.string.value_missing)
    val v = stringResource(R.string.unit_volt)
    val a = stringResource(R.string.unit_ampere)
    val w = stringResource(R.string.unit_watt)
    val pct = stringResource(R.string.unit_percent)
    val mv = stringResource(R.string.unit_millivolt)
    fun count(n: Int?) = n?.toString() ?: dash
    return when (group) {
        BatteryGroup.Overview -> listOf(
            DataRow(stringResource(R.string.bat_soc), stringResource(R.string.reg_0x100), valueOrDash(t.socPercent, 0, pct)),
            DataRow(stringResource(R.string.bat_soh), stringResource(R.string.reg_0x104), valueOrDash(t.sohPercent, 0, pct)),
            DataRow(stringResource(R.string.bat_cycles), stringResource(R.string.reg_0x10c), t.cycles?.let { stringResource(R.string.bat_cycles_value, GgFormat.number(it.toLong())) } ?: dash),
            DataRow(stringResource(R.string.bat_pack_voltage_inverter), stringResource(R.string.reg_0x101), valueOrDash(t.packVoltageInverterV, 1, v)),
            DataRow(stringResource(R.string.bat_pack_voltage_bms), stringResource(R.string.reg_0x112), valueOrDash(t.packVoltageBmsV, 1, v)),
            DataRow(stringResource(R.string.bat_pack_current), stringResource(R.string.reg_0x113), t.packCurrentA?.let { GgFormat.signedWithUnit(it, 1, a) } ?: dash),
            DataRow(stringResource(R.string.bat_temp_bms), stringResource(R.string.reg_0x114), temperatureOrDash(t.tempBmsC, temperatureUnit)),
            DataRow(stringResource(R.string.bat_temp_inverter), stringResource(R.string.reg_0x103), temperatureOrDash(t.tempInverterC, temperatureUnit)),
        )
        BatteryGroup.Limits -> listOf(
            DataRow(stringResource(R.string.bat_charge_limit_voltage), stringResource(R.string.reg_0x115), valueOrDash(t.chargeLimitVoltageV, 1, v)),
            DataRow(stringResource(R.string.bat_charge_limit_current), stringResource(R.string.reg_0x116), valueOrDash(t.chargeLimitCurrentA, 0, a)),
            DataRow(stringResource(R.string.bat_discharge_limit_current), stringResource(R.string.reg_0x117), valueOrDash(t.dischargeLimitCurrentA, 0, a)),
            DataRow(stringResource(R.string.bat_allowed_charge), stringResource(R.string.reg_allowed_charge), valueOrDash(t.allowedChargeW, 0, w)),
            DataRow(stringResource(R.string.bat_allowed_discharge), stringResource(R.string.reg_allowed_discharge), valueOrDash(t.allowedDischargeW, 0, w)),
            DataRow(
                stringResource(R.string.bat_limit_mode), stringResource(R.string.reg_e025),
                when (t.chargeLimitMode) {
                    null -> dash
                    1 -> stringResource(R.string.config_charge_limit_bms, 1)
                    else -> t.chargeLimitMode.toString()
                },
            ),
        )
        BatteryGroup.Cells -> {
            val cellPattern = stringResource(R.string.bat_cell_value)
            fun cell(mvValue: Double?, index: Int?) = when {
                mvValue == null -> dash
                index == null -> GgFormat.withUnit(mvValue, 0, mv)
                else -> String.format(cellPattern, GgFormat.withUnit(mvValue, 0, mv), index)
            }
            listOf(
                DataRow(stringResource(R.string.bat_cell_max), stringResource(R.string.reg_cell_max), cell(t.cellMaxMv, t.cellMaxIndex)),
                DataRow(stringResource(R.string.bat_cell_min), stringResource(R.string.reg_cell_min), cell(t.cellMinMv, t.cellMinIndex)),
                DataRow(stringResource(R.string.bat_cell_delta), stringResource(R.string.reg_cell_delta), valueOrDash(t.cellDeltaMv, 0, mv)),
                DataRow(stringResource(R.string.bat_cell_temp_max), stringResource(R.string.reg_0x134), temperatureOrDash(t.cellTempMaxC, temperatureUnit)),
                DataRow(stringResource(R.string.bat_cell_temp_min), stringResource(R.string.reg_0x135), temperatureOrDash(t.cellTempMinC, temperatureUnit)),
            )
        }
        BatteryGroup.Protection -> listOf(
            DataRow(stringResource(R.string.bat_packs_online), stringResource(R.string.reg_0x12a), count(t.packsOnline)),
            DataRow(stringResource(R.string.bat_packs_charge_blocked), stringResource(R.string.reg_0x12c), count(t.packsChargeBlocked)),
            DataRow(stringResource(R.string.bat_packs_discharge_blocked), stringResource(R.string.reg_0x12d), count(t.packsDischargeBlocked)),
            DataRow(stringResource(R.string.bat_packs_offline), stringResource(R.string.reg_0x12e), count(t.packsOffline)),
            DataRow(
                stringResource(R.string.bat_system_faults), stringResource(R.string.reg_0x131),
                when {
                    t.systemFaults == null -> dash
                    t.systemFaults.isEmpty() -> stringResource(R.string.data_no_errors)
                    else -> t.systemFaults.joinToString(", ")
                },
            ),
        )
        BatteryGroup.Versions -> listOf(
            DataRow(stringResource(R.string.bat_manufacturer), stringResource(R.string.reg_0x13a), t.manufacturer ?: dash),
            DataRow(stringResource(R.string.bat_sw_version), stringResource(R.string.reg_0x12f), t.softwareVersion ?: dash),
            DataRow(stringResource(R.string.bat_hw_version), stringResource(R.string.reg_0x130), t.hardwareVersion ?: dash),
            DataRow(stringResource(R.string.bat_protocol_version), stringResource(R.string.reg_0x1c), t.protocolVersion ?: dash),
        )
    }
}

/** Chữ và màu các trường của biểu đồ pin (BO_SUNG_v3.md §12). */
@Composable
private fun batteryFieldStyle(): (HistoryField) -> FieldStyle {
    val c = GgTheme.colors
    val styles = mapOf(
        HistoryFields.Soc to FieldStyle(stringResource(R.string.field_soc), stringResource(R.string.field_soc), c.energyBattery),
        HistoryFields.PackCurrent to FieldStyle(stringResource(R.string.field_pack_current), stringResource(R.string.field_pack_current_legend), c.energyGrid),
        HistoryFields.BatteryTemp to FieldStyle(stringResource(R.string.field_battery_temp), stringResource(R.string.field_battery_temp), c.danger),
        HistoryFields.CellDelta to FieldStyle(stringResource(R.string.field_cell_delta), stringResource(R.string.field_cell_delta), c.strategy[3]),
    )
    return { styles.getValue(it) }
}

// ---------- Preview ----------

fun previewBatteryDataState(group: BatteryGroup = BatteryGroup.Overview, data: BatteryTelemetry = FakeTelemetryRepository.BATTERY): LiveUiState.Content<BatteryTelemetry, BatteryGroup> =
    LiveUiState.Content(data, data.updatedAt.plusSeconds(3), group)

fun previewBatteryHistory(): HistoryUiState {
    val series = FakeHistory.series(HistoryRange.Day, HistoryFields.of(DeviceKind.Battery), LocalDateTime.of(2026, 9, 24, 23, 45))
    return HistoryUiState(DeviceKind.Battery, series = series, index = series.lastIndex(), loading = false)
}

@Preview(widthDp = 390, heightDp = 1200, showBackground = true)
@Composable
private fun BatteryDataPreview() {
    GgPowerTheme(darkTheme = false) { BatteryDataScreen(previewBatteryDataState(), {}, {}, {}, history = previewBatteryHistory()) }
}

@Preview(widthDp = 390, heightDp = 900, showBackground = true)
@Composable
private fun BatteryLimitsPreview() {
    GgPowerTheme(darkTheme = false) { BatteryDataScreen(previewBatteryDataState(BatteryGroup.Limits), {}, {}, {}) }
}
