package vn.ggpower.inverter.ui.data

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import vn.ggpower.inverter.R
import vn.ggpower.inverter.data.telemetry.FakeTelemetryRepository
import vn.ggpower.inverter.domain.settings.TemperatureUnit
import vn.ggpower.inverter.domain.telemetry.InverterTelemetry
import vn.ggpower.inverter.ui.components.ScreenHeader
import vn.ggpower.inverter.ui.theme.GgPowerTheme
import vn.ggpower.inverter.ui.theme.GgSpacing
import vn.ggpower.inverter.ui.theme.GgTheme

@Composable
fun InverterDataRoute(onBack: () -> Unit, viewModel: InverterDataViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val temperatureUnit by viewModel.temperatureUnit.collectAsStateWithLifecycle()
    InverterDataScreen(state, onBack, viewModel::onRefresh, temperatureUnit = temperatureUnit)
}

/**
 * Màn 17 · Theo dõi dữ liệu – Inverter (gg-inverter-design DataInverter.dc.html, đặc tả §17.1–17.2, §17.5): đầu trang,
 * dòng thiết bị, rồi bốn nhóm Tổng quan · PV · Lưới & tải · Nhiệt độ xếp dọc, mỗi nhóm một thẻ. Không có biểu đồ lịch sử.
 */
@Composable
fun InverterDataScreen(
    state: InverterDataUiState,
    onBack: () -> Unit,
    onRefresh: () -> Unit,
    modifier: Modifier = Modifier,
    temperatureUnit: TemperatureUnit = TemperatureUnit.Celsius,
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
                    InverterGroup.entries.forEach { group ->
                        DataSection(stringResource(group.title), first = group.ordinal == 0) {
                            DataRowsCard(inverterRows(group, t, temperatureUnit))
                        }
                    }
                }
            }
        }
    }
}

private val InverterGroup.title: Int
    get() = when (this) {
        InverterGroup.Overview -> R.string.data_group_overview
        InverterGroup.Pv -> R.string.data_group_pv
        InverterGroup.GridLoad -> R.string.data_group_grid_load
        InverterGroup.Temperature -> R.string.data_group_temperature
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

// ---------- Preview ----------

fun previewInverterDataState(): LiveUiState.Content<InverterTelemetry> {
    val t = FakeTelemetryRepository.INVERTER
    return LiveUiState.Content(t, t.updatedAt.plusSeconds(3))
}

@Preview(widthDp = 390, heightDp = 1400, showBackground = true)
@Composable
private fun InverterDataPreview() {
    GgPowerTheme(darkTheme = false) { InverterDataScreen(previewInverterDataState(), {}, {}) }
}

@Preview(widthDp = 390, heightDp = 1400, showBackground = true, backgroundColor = 0xFF0C1612)
@Composable
private fun InverterDataDarkPreview() {
    GgPowerTheme(darkTheme = true) { InverterDataScreen(previewInverterDataState(), {}, {}) }
}
