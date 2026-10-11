package vn.ggpower.inverter.ui.devices

import androidx.annotation.DrawableRes
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import vn.ggpower.inverter.R
import vn.ggpower.inverter.data.device.FakeDeviceRepository
import vn.ggpower.inverter.domain.device.BatteryState
import vn.ggpower.inverter.domain.device.BatterySummary
import vn.ggpower.inverter.domain.device.InverterSummary
import vn.ggpower.inverter.domain.format.GgFormat
import vn.ggpower.inverter.ui.components.ButtonSize
import vn.ggpower.inverter.ui.components.FloatingTabBar
import vn.ggpower.inverter.ui.components.FloatingTabBarContentPadding
import vn.ggpower.inverter.ui.components.GgCard
import vn.ggpower.inverter.ui.components.Metric
import vn.ggpower.inverter.ui.components.MetricRow
import vn.ggpower.inverter.ui.components.ScreenHeader
import vn.ggpower.inverter.ui.components.SecondaryButton
import vn.ggpower.inverter.ui.components.TabItem
import vn.ggpower.inverter.domain.settings.TemperatureUnit
import vn.ggpower.inverter.ui.format.temperatureText
import vn.ggpower.inverter.ui.projects.ProjectStatusChip
import vn.ggpower.inverter.ui.theme.GgPowerTheme
import vn.ggpower.inverter.ui.theme.GgRadius
import vn.ggpower.inverter.ui.theme.GgSpacing
import vn.ggpower.inverter.ui.theme.GgTheme

@Composable
fun DevicesRoute(
    tabs: List<TabItem>,
    onBackToProjects: () -> Unit,
    onAddDevice: () -> Unit,
    onOpenInverterData: () -> Unit,
    onOpenBatteryData: () -> Unit,
    viewModel: DevicesViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val temperatureUnit by viewModel.temperatureUnit.collectAsStateWithLifecycle()
    DevicesScreen(state, tabs, onBackToProjects, onAddDevice, viewModel::retry, onOpenInverterData, onOpenBatteryData, temperatureUnit = temperatureUnit)
}

/**
 * Màn 5 · Thiết bị (Device.png, html/Device.dc.html, đặc tả §3.5). Nút ← về Dự án (ADR-003); nút QR "Thêm thiết bị"
 * mở luồng 18. Mỗi inverter một thẻ, dàn pin của nó một thẻ (quyết 02/10/2026); chạm thẻ → Theo dõi dữ liệu (17).
 */
@Composable
fun DevicesScreen(
    state: DevicesUiState,
    tabs: List<TabItem>,
    onBackToProjects: () -> Unit,
    onAddDevice: () -> Unit,
    onRetry: () -> Unit,
    onOpenInverterData: () -> Unit,
    onOpenBatteryData: () -> Unit,
    modifier: Modifier = Modifier,
    temperatureUnit: TemperatureUnit = TemperatureUnit.Celsius,
) {
    val colors = GgTheme.colors
    Box(modifier.fillMaxSize().background(colors.surface)) {
        Column(Modifier.fillMaxSize().statusBarsPadding()) {
            ScreenHeader(
                title = stringResource(R.string.screen_devices),
                onBack = onBackToProjects,
                backDescription = stringResource(R.string.cd_back_to_projects),
            ) {
                IconButton(onClick = onAddDevice, modifier = Modifier.size(48.dp)) {
                    Icon(
                        painterResource(R.drawable.ic_qr),
                        contentDescription = stringResource(R.string.cd_add_device),
                        tint = colors.ink,
                        modifier = Modifier.size(24.dp),
                    )
                }
            }
            when (state) {
                DevicesUiState.Loading -> LoadingBlocks()
                is DevicesUiState.Error -> ErrorBlock(state.noConnection, onRetry)
                is DevicesUiState.Content -> Column(
                    Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(start = GgSpacing.space5, end = GgSpacing.space5, bottom = FloatingTabBarContentPadding),
                    verticalArrangement = Arrangement.spacedBy(GgSpacing.space3),
                ) {
                    state.units.forEach { unit ->
                        InverterCard(unit.inverter, temperatureUnit, onOpenInverterData)
                        unit.battery?.let { BatteryCard(it, onOpenBatteryData) }
                    }
                }
            }
        }
        FloatingTabBar(items = tabs, modifier = Modifier.align(Alignment.BottomCenter))
    }
}

// ---------- Thẻ thiết bị ----------

/** Khung thẻ thiết bị: đệm 16, các khối cách 16; chân thẻ "Theo dõi dữ liệu chi tiết" (viền trên, đệm trên 12). */
@Composable
private fun DeviceCard(onClick: () -> Unit, content: @Composable ColumnScope.() -> Unit) {
    val colors = GgTheme.colors
    GgCard(onClick = onClick, onClickLabel = stringResource(R.string.device_open_data), verticalSpacing = GgSpacing.space4) {
        content()
        Column {
            Row(
                Modifier.fillMaxWidth().padding(top = GgSpacing.space3),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    stringResource(R.string.device_open_data),
                    style = GgTheme.typography.bodySm.copy(fontWeight = FontWeight.SemiBold),
                    color = colors.primary,
                    modifier = Modifier.weight(1f),
                )
                Icon(painterResource(R.drawable.ic_chevron_right), contentDescription = null, tint = colors.primary, modifier = Modifier.size(18.dp))
            }
        }
    }
}

/** Ô icon 48 dp bo 12, nền `primarySoft`, icon 26 dp. */
@Composable
private fun DeviceIcon(@DrawableRes icon: Int, tint: Color) {
    Box(
        Modifier.size(48.dp).clip(RoundedCornerShape(12.dp)).background(GgTheme.colors.primarySoft),
        contentAlignment = Alignment.Center,
    ) {
        Icon(painterResource(icon), contentDescription = null, tint = tint, modifier = Modifier.size(26.dp))
    }
}

@Composable
private fun deviceTitle(): TextStyle = GgTheme.typography.body.copy(fontSize = 18.sp, lineHeight = 26.sp, fontWeight = FontWeight.SemiBold)

@Composable
private fun InverterCard(inverter: InverterSummary, temperatureUnit: TemperatureUnit, onClick: () -> Unit) {
    val colors = GgTheme.colors
    val dash = stringResource(R.string.value_missing)
    DeviceCard(onClick) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(GgSpacing.space3)) {
            DeviceIcon(R.drawable.ic_smartbox, colors.primary)
            Column(Modifier.weight(1f)) {
                Text(inverter.name, style = deviceTitle(), color = colors.ink)
                Text(
                    stringResource(R.string.device_serial, inverter.serial),
                    style = monoStyle(),
                    color = colors.inkMuted,
                )
            }
            ProjectStatusChip(inverter.status)
        }
        MetricRow(listOf(
            Metric(stringResource(R.string.device_power), inverter.powerKw?.let { GgFormat.withUnit(it, 1, stringResource(R.string.unit_kw)) } ?: dash),
            Metric(stringResource(R.string.device_temperature), inverter.temperatureC?.let { temperatureText(it, temperatureUnit) } ?: dash),
            Metric(stringResource(R.string.device_firmware), inverter.firmware ?: dash, mono = true),
        ))
    }
}

@Composable
private fun BatteryCard(battery: BatterySummary, onClick: () -> Unit) {
    val colors = GgTheme.colors
    val dash = stringResource(R.string.value_missing)
    val percent = stringResource(R.string.unit_percent)
    DeviceCard(onClick) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(GgSpacing.space3)) {
            DeviceIcon(R.drawable.ic_battery, colors.energyBattery)
            Column(Modifier.weight(1f)) {
                Text(battery.name, style = deviceTitle(), color = colors.ink)
                Text(batteryStatus(battery), style = GgTheme.typography.bodySm, color = colors.inkMuted)
            }
        }
        Column(verticalArrangement = Arrangement.spacedBy(GgSpacing.space2)) {
            val soc = battery.socPercent
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Bottom) {
                Text(stringResource(R.string.device_soc), style = GgTheme.typography.bodySm, color = colors.inkMuted, modifier = Modifier.weight(1f))
                Text(
                    soc?.let { GgFormat.withUnit(it.toLong(), percent) } ?: dash,
                    style = GgTheme.typography.metric,
                    color = colors.ink,
                )
            }
            val fraction = (soc ?: 0).coerceIn(0, 100) / 100f
            val bar = stringResource(R.string.cd_battery_level)
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(12.dp)
                    .clip(CircleShape)
                    .background(colors.surfaceSunken)
                    .semantics {
                        contentDescription = bar
                        progressBarRangeInfo = ProgressBarRangeInfo(fraction, 0f..1f)
                    },
            ) {
                Box(Modifier.fillMaxWidth(fraction).height(12.dp).clip(CircleShape).background(colors.energyBattery))
            }
        }
        MetricRow(listOf(
            Metric(stringResource(R.string.device_power), battery.powerKw?.let { GgFormat.withUnit(kotlin.math.abs(it), 1, stringResource(R.string.unit_kw)) } ?: dash),
            Metric(stringResource(R.string.device_voltage), battery.voltageV?.let { GgFormat.withUnit(it, 1, stringResource(R.string.unit_volt)) } ?: dash),
            Metric(stringResource(R.string.device_soh), battery.sohPercent?.let { GgFormat.withUnit(it.toLong(), percent) } ?: dash),
        ))
    }
}

/** "Đang sạc · đầy sau ~2 giờ" / "Đang xả · còn ~3 giờ" / "Đứng yên". */
@Composable
private fun batteryStatus(battery: BatterySummary): String {
    val hours = battery.hoursRemaining?.let { GgFormat.trimmed(it, 1) }
    return when (battery.state) {
        BatteryState.Charging -> if (hours != null) stringResource(R.string.battery_charging_until_full, hours) else stringResource(R.string.battery_charging)
        BatteryState.Discharging -> if (hours != null) stringResource(R.string.battery_discharging_remaining, hours) else stringResource(R.string.battery_discharging)
        BatteryState.Idle -> stringResource(R.string.battery_idle)
    }
}

@Composable
private fun monoStyle(): TextStyle = GgTheme.typography.code.copy(fontFamily = FontFamily.Monospace)


// ---------- Đang tải / lỗi ----------

@Composable
private fun LoadingBlocks() {
    val sunken = GgTheme.colors.surfaceSunken
    Column(Modifier.padding(horizontal = GgSpacing.space5), verticalArrangement = Arrangement.spacedBy(GgSpacing.space3)) {
        repeat(2) { GgCard { Box(Modifier.fillMaxWidth().height(170.dp).clip(RoundedCornerShape(GgRadius.radiusMd)).background(sunken)) } }
    }
}

@Composable
private fun ColumnScope.ErrorBlock(noConnection: Boolean, onRetry: () -> Unit) {
    Column(
        Modifier.weight(1f).fillMaxWidth().padding(start = GgSpacing.space8, end = GgSpacing.space8, bottom = FloatingTabBarContentPadding),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(GgSpacing.space4, Alignment.CenterVertically),
    ) {
        Text(
            stringResource(if (noConnection) R.string.error_no_internet else R.string.error_server),
            style = GgTheme.typography.body,
            color = GgTheme.colors.inkMuted,
            textAlign = TextAlign.Center,
        )
        SecondaryButton(stringResource(R.string.action_retry), onClick = onRetry, size = ButtonSize.Medium)
    }
}

// ---------- Preview ----------

@Composable
fun previewDeviceTabs() = listOf(
    TabItem(stringResource(R.string.tab_main), R.drawable.ic_tab_main, selected = false) {},
    TabItem(stringResource(R.string.tab_devices), R.drawable.ic_tab_devices, selected = true) {},
    TabItem(stringResource(R.string.tab_service), R.drawable.ic_tab_service, selected = false) {},
)

@Preview(widthDp = 390, heightDp = 844, showBackground = true)
@Composable
private fun DevicesPreview() {
    GgPowerTheme(darkTheme = false) {
        DevicesScreen(DevicesUiState.Content(FakeDeviceRepository.SAMPLE), previewDeviceTabs(), {}, {}, {}, {}, {})
    }
}

@Preview(widthDp = 390, heightDp = 844, showBackground = true)
@Composable
private fun DevicesLoadingPreview() {
    GgPowerTheme(darkTheme = false) {
        DevicesScreen(DevicesUiState.Loading, previewDeviceTabs(), {}, {}, {}, {}, {})
    }
}
