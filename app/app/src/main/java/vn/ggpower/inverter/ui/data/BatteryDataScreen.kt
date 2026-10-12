package vn.ggpower.inverter.ui.data

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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import vn.ggpower.inverter.R
import vn.ggpower.inverter.data.telemetry.FakeTelemetryRepository
import vn.ggpower.inverter.domain.format.GgFormat
import vn.ggpower.inverter.domain.settings.TemperatureUnit
import vn.ggpower.inverter.domain.telemetry.BatteryTelemetry
import vn.ggpower.inverter.domain.telemetry.BmsFlag
import vn.ggpower.inverter.domain.telemetry.BmsFlagKind
import vn.ggpower.inverter.ui.components.GgCard
import vn.ggpower.inverter.ui.components.ScreenHeader
import vn.ggpower.inverter.ui.components.StatusChip
import vn.ggpower.inverter.ui.components.StatusTone
import vn.ggpower.inverter.ui.theme.GgPowerTheme
import vn.ggpower.inverter.ui.theme.GgSpacing
import vn.ggpower.inverter.ui.theme.GgTheme

@Composable
fun BatteryDataRoute(onBack: () -> Unit, viewModel: BatteryDataViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val showAllFlags by viewModel.showAllFlags.collectAsStateWithLifecycle()
    val temperatureUnit by viewModel.temperatureUnit.collectAsStateWithLifecycle()
    BatteryDataScreen(
        state, onBack, viewModel::onRefresh,
        temperatureUnit = temperatureUnit,
        showAllFlags = showAllFlags,
        onToggleAllFlags = viewModel::onToggleAllFlags,
    )
}

/**
 * Màn 17 · Theo dõi dữ liệu – Pin S16 Pre (gg-inverter-design DataBattery.dc.html, đặc tả §17.3–17.5). Số liệu gộp của cả
 * dàn pin, đọc qua inverter (quyết 02/10/2026). Năm nhóm xếp dọc, mỗi nhóm một thẻ; nhóm Bảo vệ có thêm thẻ cờ BMS.
 * Thiết kế mới bỏ hộp lệch điện áp, đồng hồ giới hạn dòng, ghi chú biểu đồ cell và biểu đồ lịch sử.
 */
@Composable
fun BatteryDataScreen(
    state: BatteryDataUiState,
    onBack: () -> Unit,
    onRefresh: () -> Unit,
    modifier: Modifier = Modifier,
    temperatureUnit: TemperatureUnit = TemperatureUnit.Celsius,
    showAllFlags: Boolean = false,
    onToggleAllFlags: () -> Unit = {},
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
                    BatteryGroup.entries.forEach { group ->
                        DataSection(stringResource(group.title), first = group.ordinal == 0) {
                            DataRowsCard(batteryRows(group, t, temperatureUnit))
                        }
                        if (group == BatteryGroup.Protection) FlagsCard(t.flags.orEmpty(), showAllFlags, onToggleAllFlags)
                    }
                }
            }
        }
    }
}

private val BatteryGroup.title: Int
    get() = when (this) {
        BatteryGroup.Overview -> R.string.bat_group_overview
        BatteryGroup.Limits -> R.string.bat_group_limits
        BatteryGroup.Cells -> R.string.bat_group_cells
        BatteryGroup.Protection -> R.string.bat_group_protection
        BatteryGroup.Versions -> R.string.bat_group_versions
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
                    ofKind.forEach { f ->
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

// ---------- Preview ----------

fun previewBatteryDataState(data: BatteryTelemetry = FakeTelemetryRepository.BATTERY): LiveUiState.Content<BatteryTelemetry> =
    LiveUiState.Content(data, data.updatedAt.plusSeconds(3))

@Preview(widthDp = 390, heightDp = 2000, showBackground = true)
@Composable
private fun BatteryDataPreview() {
    GgPowerTheme(darkTheme = false) { BatteryDataScreen(previewBatteryDataState(), {}, {}) }
}

@Preview(widthDp = 390, heightDp = 2000, showBackground = true, backgroundColor = 0xFF0C1612)
@Composable
private fun BatteryDataDarkPreview() {
    GgPowerTheme(darkTheme = true) { BatteryDataScreen(previewBatteryDataState(), {}, {}, showAllFlags = true) }
}
