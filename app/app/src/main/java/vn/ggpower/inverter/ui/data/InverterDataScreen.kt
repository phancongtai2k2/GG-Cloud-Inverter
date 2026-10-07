package vn.ggpower.inverter.ui.data

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import vn.ggpower.inverter.R
import vn.ggpower.inverter.data.telemetry.FakeTelemetryRepository
import vn.ggpower.inverter.domain.settings.TemperatureUnit
import vn.ggpower.inverter.domain.telemetry.DeviceKind
import vn.ggpower.inverter.domain.telemetry.HistoryField
import vn.ggpower.inverter.domain.telemetry.HistoryFields
import vn.ggpower.inverter.domain.telemetry.HistoryRange
import vn.ggpower.inverter.domain.telemetry.InverterTelemetry
import vn.ggpower.inverter.ui.components.ScreenHeader
import vn.ggpower.inverter.ui.components.SegmentedControl
import vn.ggpower.inverter.ui.components.SegmentedSize
import vn.ggpower.inverter.ui.theme.GgPowerTheme
import vn.ggpower.inverter.ui.theme.GgSpacing
import vn.ggpower.inverter.ui.theme.GgTheme

@Composable
fun InverterDataRoute(onBack: () -> Unit, viewModel: InverterDataViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val history by viewModel.historyState.collectAsStateWithLifecycle()
    val temperatureUnit by viewModel.temperatureUnit.collectAsStateWithLifecycle()
    InverterDataScreen(
        state, onBack, viewModel::onRefresh, viewModel::onGroupChange,
        temperatureUnit = temperatureUnit,
        history = history,
        historyActions = HistoryActions(viewModel::onRangeChange, viewModel::onToggleField, viewModel::onSelectIndex, viewModel::onHistoryRetry),
    )
}

/**
 * Màn 17 · Theo dõi dữ liệu – Inverter (DataInverter.png, đặc tả §17.1–17.2, §17.5). Phần A: đầu trang, bốn nhóm dữ
 * liệu, trạng thái máy, các dòng giá trị, biểu đồ lịch sử (§17.4).
 */
@Composable
fun InverterDataScreen(
    state: InverterDataUiState,
    onBack: () -> Unit,
    onRefresh: () -> Unit,
    onGroupChange: (InverterGroup) -> Unit,
    modifier: Modifier = Modifier,
    temperatureUnit: TemperatureUnit = TemperatureUnit.Celsius,
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
                            stringResource(R.string.data_group_overview),
                            stringResource(R.string.data_group_pv),
                            stringResource(R.string.data_group_grid_load),
                            stringResource(R.string.data_group_temperature),
                        ),
                        selectedIndex = state.group.ordinal,
                        onSelect = { onGroupChange(InverterGroup.entries[it]) },
                        size = SegmentedSize.Groups,
                        groupLabel = stringResource(R.string.data_groups),
                    )
                    if (state.group == InverterGroup.Overview) MachineState(t.machineState)
                    DataRowsCard(inverterRows(state.group, t, temperatureUnit))
                    if (history != null) HistoryCard(history, HistoryFields.of(DeviceKind.Inverter), inverterFieldStyle(), historyActions, temperatureUnit)
                }
            }
        }
    }
}

/** Hộp "Trạng thái máy · 0x210 MachineState" (nền `primarySoft`, bo 14, đệm 12 14, dấu ✓ 20 dp). */
@Composable
private fun MachineState(text: String?) {
    val colors = GgTheme.colors
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(colors.primarySoft).padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(GgSpacing.space3),
    ) {
        Icon(painterResource(R.drawable.ic_check), contentDescription = null, tint = colors.primary, modifier = Modifier.size(20.dp))
        Column {
            Text(stringResource(R.string.data_machine_state), style = GgTheme.typography.caption.copy(fontWeight = FontWeight.Normal), color = colors.inkMuted)
            Text(
                text ?: stringResource(R.string.value_missing),
                style = GgTheme.typography.body.copy(fontSize = 15.sp, lineHeight = 22.sp, fontWeight = FontWeight.SemiBold),
                color = colors.ink,
            )
        }
    }
}

/** Các dòng của từng nhóm, đúng thứ tự và thanh ghi của đặc tả §17.2 / DataInverter.dc.html. */
@Composable
private fun inverterRows(group: InverterGroup, t: InverterTelemetry, temperatureUnit: TemperatureUnit): List<DataRow> {
    val w = stringResource(R.string.unit_watt)
    val v = stringResource(R.string.unit_volt)
    val a = stringResource(R.string.unit_ampere)
    val errors = when {
        t.activeErrors == null -> stringResource(R.string.value_missing)
        t.activeErrors.isEmpty() -> stringResource(R.string.data_no_errors)
        else -> t.activeErrors.joinToString(", ")
    }
    return when (group) {
        InverterGroup.Overview -> listOf(
            DataRow(stringResource(R.string.data_pv_total), stringResource(R.string.reg_pv_total), valueOrDash(t.pvTotalW, 0, w)),
            DataRow(stringResource(R.string.data_grid_power), stringResource(R.string.reg_0x23a), valueOrDash(t.gridPowerW, 0, w)),
            DataRow(stringResource(R.string.data_ups_load), stringResource(R.string.reg_0x21b), valueOrDash(t.upsLoadW, 0, w)),
            DataRow(stringResource(R.string.data_pv_charge_current), stringResource(R.string.reg_0x224), valueOrDash(t.pvChargeCurrentA, 1, a)),
            DataRow(stringResource(R.string.data_active_errors), stringResource(R.string.reg_0x200), errors),
        )
        InverterGroup.Pv -> listOf(
            DataRow(stringResource(R.string.data_pv1_voltage), "0x107", valueOrDash(t.pv1VoltageV, 1, v)),
            DataRow(stringResource(R.string.data_pv1_current), "0x108", valueOrDash(t.pv1CurrentA, 1, a)),
            DataRow(stringResource(R.string.data_pv1_power), "0x109", valueOrDash(t.pv1PowerW, 0, w)),
            DataRow(stringResource(R.string.data_pv2_voltage), "0x10F", valueOrDash(t.pv2VoltageV, 1, v)),
            DataRow(stringResource(R.string.data_pv2_current), "0x110", valueOrDash(t.pv2CurrentA, 1, a)),
            DataRow(stringResource(R.string.data_pv2_power), "0x111", valueOrDash(t.pv2PowerW, 0, w)),
        )
        InverterGroup.GridLoad -> {
            val dash = stringResource(R.string.value_missing)
            val output = if (t.outputVoltageV == null && t.outputCurrentA == null) dash
            else "${valueOrDash(t.outputVoltageV, 1, v)} · ${valueOrDash(t.outputCurrentA, 1, a)}"
            listOf(
                DataRow(stringResource(R.string.data_grid_voltage), stringResource(R.string.reg_0x213), valueOrDash(t.gridVoltageV, 1, v)),
                DataRow(stringResource(R.string.data_grid_current), stringResource(R.string.reg_0x214), valueOrDash(t.gridCurrentA, 1, a)),
                DataRow(stringResource(R.string.data_grid_frequency), stringResource(R.string.reg_0x215), valueOrDash(t.gridFrequencyHz, 2, stringResource(R.string.unit_hertz))),
                DataRow(stringResource(R.string.data_grid_power), stringResource(R.string.reg_0x23a_sign), valueOrDash(t.gridPowerW, 0, w)),
                DataRow(stringResource(R.string.data_output), "0x216 · 0x217", output),
                DataRow(stringResource(R.string.data_ups_load), stringResource(R.string.reg_0x21b), valueOrDash(t.upsLoadW, 0, w)),
                DataRow(stringResource(R.string.data_apparent), stringResource(R.string.reg_0x21c), valueOrDash(t.loadApparentVa, 0, stringResource(R.string.unit_va))),
                DataRow(stringResource(R.string.data_load_ratio), stringResource(R.string.reg_0x21f), valueOrDash(t.loadRatioPercent, 0, stringResource(R.string.unit_percent))),
                DataRow(stringResource(R.string.data_home_load), stringResource(R.string.reg_0x240), valueOrDash(t.homeLoadW, 0, w)),
                DataRow(stringResource(R.string.data_grid_charge_current), stringResource(R.string.reg_0x21e), valueOrDash(t.gridChargeCurrentA, 1, a)),
            )
        }
        InverterGroup.Temperature -> listOf(
            DataRow(stringResource(R.string.data_temp_dcdc), stringResource(R.string.reg_0x220), temperatureOrDash(t.tempDcDcC, temperatureUnit)),
            DataRow(stringResource(R.string.data_temp_dcac), stringResource(R.string.reg_0x221), temperatureOrDash(t.tempDcAcC, temperatureUnit)),
            DataRow(stringResource(R.string.data_temp_transformer), stringResource(R.string.reg_0x222), temperatureOrDash(t.tempTransformerC, temperatureUnit)),
            DataRow(stringResource(R.string.data_temp_ambient), stringResource(R.string.reg_0x223), temperatureOrDash(t.tempAmbientC, temperatureUnit)),
            DataRow(stringResource(R.string.data_active_errors), stringResource(R.string.reg_0x200_64), errors),
        )
    }
}

/** Chữ và màu các trường của biểu đồ inverter (màu theo BO_SUNG_v3.md §12). */
@Composable
private fun inverterFieldStyle(): (HistoryField) -> FieldStyle {
    val c = GgTheme.colors
    val styles = mapOf(
        HistoryFields.PvPower to (R.string.field_pv_power to c.energyPv),
        HistoryFields.LoadPower to (R.string.field_load_power to c.energyLoad),
        HistoryFields.TempDcAc to (R.string.field_temp_dcac to c.danger),
        HistoryFields.GridVoltage to (R.string.field_grid_voltage to c.energyGrid),
        HistoryFields.GridFrequency to (R.string.field_grid_frequency to c.strategy[2]),
    ).mapValues { (_, v) -> stringResource(v.first).let { label -> FieldStyle(label, label, v.second) } }
    return { styles.getValue(it) }
}

// ---------- Preview ----------

fun previewInverterHistory(range: HistoryRange = HistoryRange.Day): HistoryUiState {
    val now = java.time.LocalDateTime.of(2026, 9, 24, 23, 45)
    val series = vn.ggpower.inverter.data.telemetry.FakeHistory.series(range, HistoryFields.of(DeviceKind.Inverter), now)
    return HistoryUiState(DeviceKind.Inverter, range = range, series = series, index = series.lastIndex(), loading = false)
}

fun previewInverterDataState(group: InverterGroup = InverterGroup.Overview): LiveUiState.Content<InverterTelemetry, InverterGroup> {
    val t = FakeTelemetryRepository.INVERTER
    return LiveUiState.Content(t, t.updatedAt.plusSeconds(3), group)
}

@Preview(widthDp = 390, heightDp = 844, showBackground = true)
@Composable
private fun InverterDataPreview() {
    GgPowerTheme(darkTheme = false) { InverterDataScreen(previewInverterDataState(), {}, {}, {}) }
}

@Preview(widthDp = 390, heightDp = 844, showBackground = true)
@Composable
private fun InverterDataGridPreview() {
    GgPowerTheme(darkTheme = false) { InverterDataScreen(previewInverterDataState(InverterGroup.GridLoad), {}, {}, {}) }
}
