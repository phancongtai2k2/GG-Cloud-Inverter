package vn.ggpower.inverter.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
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
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import vn.ggpower.inverter.R
import vn.ggpower.inverter.data.home.FakeHomeRepository
import vn.ggpower.inverter.data.home.HomeOverview
import vn.ggpower.inverter.domain.config.OperatingMode
import vn.ggpower.inverter.domain.format.GgFormat
import vn.ggpower.inverter.domain.home.BatteryContent
import vn.ggpower.inverter.domain.home.Trend
import vn.ggpower.inverter.domain.home.compareYield
import vn.ggpower.inverter.ui.components.FloatingTabBar
import vn.ggpower.inverter.ui.components.FloatingTabBarContentPadding
import vn.ggpower.inverter.ui.components.GgCard
import vn.ggpower.inverter.ui.components.ScreenHeader
import vn.ggpower.inverter.ui.components.TabItem
import vn.ggpower.inverter.ui.projects.ProjectStatusChip
import vn.ggpower.inverter.ui.theme.GgPowerTheme
import vn.ggpower.inverter.ui.theme.GgSpacing
import vn.ggpower.inverter.ui.theme.GgTheme

/** Màn 4 · Màn hình chính: nối ViewModel với điều hướng. */
@Composable
fun HomeRoute(
    tabs: List<TabItem>,
    onBackToProjects: () -> Unit,
    onOpenAlerts: () -> Unit,
    onOpenConfig: () -> Unit,
    onOpenBatteryEnergy: () -> Unit,
    viewModel: HomeViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val stats by viewModel.statsState.collectAsStateWithLifecycle()
    HomeScreen(
        state, stats, tabs, onOpenAlerts, onOpenConfig, onOpenBatteryEnergy,
        onBackToProjects = onBackToProjects,
        statsActions = StatsActions(
            onPeriodChange = viewModel::onPeriodChange,
            onPrevious = viewModel::onPrevious,
            onNext = viewModel::onNext,
            onSelectPower = viewModel::onSelectPower,
            onSelectBattery = viewModel::onSelectBattery,
            onSelectYield = viewModel::onSelectYield,
        ),
    )
}

/**
 * Màn 4 · Màn hình chính (Main.png, html/Main.dc.html, đặc tả §3.4). Nút ← về danh sách Dự án (ADR-003). Phần 1: đầu trang, dòng dự án, nhận định,
 * sơ đồ dòng năng lượng, box Chế độ hoạt động, box Pin S16 Pre. Phần 2: bộ chọn kỳ và 3 biểu đồ ([HomeStats]).
 * Các khối cách 12 dp, lề 20 dp.
 */
@Composable
fun HomeScreen(
    state: HomeUiState,
    stats: StatsUiState?,
    tabs: List<TabItem>,
    onOpenAlerts: () -> Unit,
    onOpenConfig: () -> Unit,
    onOpenBatteryEnergy: () -> Unit,
    modifier: Modifier = Modifier,
    onBackToProjects: () -> Unit = {},
    statsActions: StatsActions = StatsActions(),
) {
    val colors = GgTheme.colors
    val overview = (state as? HomeUiState.Content)?.overview
    Box(modifier.fillMaxSize().background(colors.surface)) {
        Column(Modifier.fillMaxSize().statusBarsPadding()) {
            ScreenHeader(
                title = stringResource(R.string.screen_main),
                onBack = onBackToProjects,
                backDescription = stringResource(R.string.cd_back_to_projects),
            ) {
                BellButton(overview?.unresolvedAlerts ?: 0, onOpenAlerts)
            }
            if (overview == null) {
                LoadingBlocks()
            } else {
                Column(
                    Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(start = GgSpacing.space5, end = GgSpacing.space5, bottom = FloatingTabBarContentPadding),
                    verticalArrangement = Arrangement.spacedBy(GgSpacing.space3),
                ) {
                    ProjectLine(overview)
                    InsightBanner(overview)
                    GgCard(verticalSpacing = GgSpacing.space3) {
                        Text(
                            stringResource(R.string.energy_flow_title),
                            style = cardTitle(),
                            color = colors.ink,
                            modifier = Modifier.semantics { heading() },
                        )
                        EnergyFlowDiagram(overview.energy)
                    }
                    ModeBox(overview.mode, overview.backupSocPercent, onOpenConfig)
                    BatteryBox(overview.energy.batterySocPercent, overview.battery, onOpenBatteryEnergy)
                    if (stats != null) HomeStats(stats, statsActions)
                }
            }
        }
        FloatingTabBar(items = tabs, modifier = Modifier.align(Alignment.BottomCenter))
    }
}

/** Tiêu đề thẻ 18/26 600 ("Dòng năng lượng", "Công suất"…). */
@Composable
private fun cardTitle() = GgTheme.typography.body.copy(fontSize = 18.sp, lineHeight = 26.sp, fontWeight = FontWeight.SemiBold)

/**
 * Chuông cảnh báo 48 dp (Main.dc.html): khi còn cảnh báo chưa xử lý có chấm đỏ `danger` lõi 8 dp, viền 2 dp màu nền
 * (tổng 12 dp), cách mép trên và mép phải của nút 10 dp.
 */
@Composable
private fun BellButton(unresolved: Int, onClick: () -> Unit) {
    val colors = GgTheme.colors
    val description = if (unresolved > 0) {
        pluralStringResource(R.plurals.cd_alerts_unresolved, unresolved, unresolved)
    } else {
        stringResource(R.string.cd_alerts)
    }
    Box(Modifier.size(48.dp)) {
        IconButton(onClick = onClick, modifier = Modifier.size(48.dp).semantics { contentDescription = description }) {
            Icon(painterResource(R.drawable.ic_bell), contentDescription = null, tint = colors.ink, modifier = Modifier.size(24.dp))
        }
        if (unresolved > 0) {
            Box(
                Modifier
                    .align(Alignment.TopEnd)
                    .offset(x = (-10).dp, y = 10.dp)
                    .size(12.dp)
                    .clip(CircleShape)
                    .background(colors.surface)
                    .padding(2.dp)
                    .clip(CircleShape)
                    .background(colors.danger),
            )
        }
    }
}

/** "Nhà của bạn" 13/20 600 + chip trạng thái, cách 8 dp. */
@Composable
private fun ProjectLine(overview: HomeOverview) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(GgSpacing.space2)) {
        Text(overview.projectName, style = GgTheme.typography.bodySm.copy(fontWeight = FontWeight.SemiBold), color = GgTheme.colors.ink)
        ProjectStatusChip(overview.status)
    }
}

/** Dải nhận định (đặc tả 4.1): nền `primarySoft` bo 16 dp, đệm 12 / 16 dp, icon lá 22 dp, chữ 13/20, phần trăm đậm màu `primary`. */
@Composable
private fun InsightBanner(overview: HomeOverview) {
    val comparison = compareYield(overview.yieldTodayKwh, overview.yieldYesterdayKwh) ?: return
    val colors = GgTheme.colors
    val percent = GgFormat.withUnit(comparison.percent.toLong(), stringResource(R.string.unit_percent))
    val text = when (comparison.trend) {
        Trend.Higher -> emphasize(stringResource(R.string.home_insight_higher, percent), percent, colors.primary)
        Trend.Lower -> emphasize(stringResource(R.string.home_insight_lower, percent), percent, colors.primary)
        Trend.Same -> AnnotatedString(stringResource(R.string.home_insight_same))
    }
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(colors.primarySoft)
            .padding(horizontal = GgSpacing.space4, vertical = GgSpacing.space3),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(GgSpacing.space3),
    ) {
        Icon(painterResource(R.drawable.ic_leaf), contentDescription = null, tint = colors.primary, modifier = Modifier.size(22.dp))
        Text(text, style = GgTheme.typography.bodySm, color = colors.ink)
    }
}

private fun emphasize(full: String, part: String, color: Color): AnnotatedString = buildAnnotatedString {
    val i = full.indexOf(part)
    if (i < 0) { append(full); return@buildAnnotatedString }
    append(full.substring(0, i))
    withStyle(SpanStyle(fontWeight = FontWeight.Bold, color = color)) { append(part) }
    append(full.substring(i + part.length))
}

/**
 * Box Chế độ hoạt động (4.3a): thẻ đệm 14 / 12 / 14 / 16, ô icon 44 dp bo 12 dp, nhãn 12/16 500, tên chế độ 16/22 600,
 * tóm tắt 12/16 `inkMuted`, mũi tên 20 dp. Bấm → Cấu hình dự án (16).
 */
@Composable
private fun ModeBox(mode: OperatingMode, backupSoc: Int, onClick: () -> Unit) {
    val colors = GgTheme.colors
    val (nameRes, shortRes, icon) = modeTexts(mode)
    val name = stringResource(nameRes)
    val summary = stringResource(shortRes) + " · " +
        stringResource(R.string.mode_backup_soc, GgFormat.withUnit(backupSoc.toLong(), stringResource(R.string.unit_percent)))
    val cd = stringResource(R.string.cd_mode_box, name)
    GgCard(
        onClick = onClick,
        contentPadding = PaddingValues(start = 16.dp, top = 14.dp, end = 12.dp, bottom = 14.dp),
        modifier = Modifier.semantics { contentDescription = cd },
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(GgSpacing.space3),
        ) {
            Box(Modifier.size(44.dp).clip(RoundedCornerShape(12.dp)).background(colors.primarySoft), contentAlignment = Alignment.Center) {
                Icon(painterResource(icon), contentDescription = null, tint = colors.primary, modifier = Modifier.size(22.dp))
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(stringResource(R.string.mode_label), style = GgTheme.typography.caption, color = colors.inkMuted)
                Text(name, style = GgTheme.typography.body.copy(fontSize = 16.sp, lineHeight = 22.sp, fontWeight = FontWeight.SemiBold), color = colors.ink)
                Text(summary, style = GgTheme.typography.caption.copy(fontWeight = FontWeight.Normal), color = colors.inkMuted)
            }
            Icon(painterResource(R.drawable.ic_chevron_right), contentDescription = null, tint = colors.inkMuted, modifier = Modifier.size(20.dp))
        }
    }
}

private fun modeTexts(mode: OperatingMode): Triple<Int, Int, Int> = when (mode) {
    OperatingMode.SelfConsumption -> Triple(R.string.mode_self_consumption, R.string.mode_self_consumption_short, R.drawable.ic_mode_self)
    OperatingMode.BatteryPriority -> Triple(R.string.mode_battery_priority, R.string.mode_battery_priority_short, R.drawable.ic_battery)
    OperatingMode.MaxGridExport -> Triple(R.string.mode_max_grid_export, R.string.mode_max_grid_export_short, R.drawable.ic_energy_grid)
    OperatingMode.Tou -> Triple(R.string.mode_tou, R.string.mode_tou_short, R.drawable.ic_mode_tou)
    OperatingMode.GgAi -> Triple(R.string.mode_gg_ai, R.string.mode_gg_ai_short, R.drawable.ic_mode_ai)
    OperatingMode.Unknown -> Triple(R.string.mode_unknown, R.string.mode_unknown_short, R.drawable.ic_mode_self)
}

/**
 * Box Pin S16 Pre (4.3): đệm 16, các dòng cách 8 dp. Đầu box: icon pin 18 dp `energyBattery`, "Pin S16 Pre" 13/20 `inkMuted`,
 * mũi tên 18 dp. SOC 24/28 600 + "%" 12/16. Thanh 12 dp bo tròn: cam = Từ PV, xanh dương = Từ lưới. Chú giải 12/16 500.
 * Bấm → Nguồn năng lượng pin (11).
 */
@Composable
private fun BatteryBox(soc: Int, battery: BatteryContent, onClick: () -> Unit) {
    val colors = GgTheme.colors
    val kwh = stringResource(R.string.unit_kwh)
    val pvText = GgFormat.withUnit(battery.fromPvKwh, 1, kwh)
    val gridText = GgFormat.withUnit(battery.fromGridKwh, 1, kwh)
    val socText = GgFormat.withUnit(soc.toLong(), stringResource(R.string.unit_percent))
    GgCard(onClick = onClick, onClickLabel = stringResource(R.string.cd_battery_box), verticalSpacing = GgSpacing.space2) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Icon(painterResource(R.drawable.ic_battery), contentDescription = null, tint = colors.energyBattery, modifier = Modifier.size(18.dp))
            Text(stringResource(R.string.battery_box_title), style = GgTheme.typography.bodySm, color = colors.inkMuted, modifier = Modifier.weight(1f))
            Icon(painterResource(R.drawable.ic_chevron_right), contentDescription = null, tint = colors.inkMuted, modifier = Modifier.size(18.dp))
        }
        Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(GgSpacing.space1)) {
            Text(soc.toString(), style = GgTheme.typography.metric, color = colors.ink)
            Text(stringResource(R.string.unit_percent), style = GgTheme.typography.caption, color = colors.inkMuted, modifier = Modifier.padding(bottom = 3.dp))
        }
        val barDescription = stringResource(R.string.cd_battery_bar, socText, pvText, gridText)
        Row(
            Modifier
                .fillMaxWidth()
                .height(12.dp)
                .clip(CircleShape)
                .background(colors.surfaceSunken)
                .clearAndSetSemantics { contentDescription = barDescription },
        ) {
            if (battery.pvFraction > 0) Box(Modifier.weight(battery.pvFraction.toFloat()).height(12.dp).background(colors.energyPv))
            if (battery.gridFraction > 0) {
                // Vạch ngăn 1 dp màu nền thẻ giữa hai nguồn (inset box-shadow trong prototype).
                Box(Modifier.size(1.dp, 12.dp).background(colors.cardBg))
                Box(Modifier.weight(battery.gridFraction.toFloat()).height(12.dp).background(colors.energyGrid))
            }
            val rest = 1.0 - battery.pvFraction - battery.gridFraction
            if (rest > 0) Box(Modifier.weight(rest.toFloat()).height(12.dp))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(GgSpacing.space4)) {
            LegendValue(colors.energyPv, stringResource(R.string.battery_from_pv), pvText)
            LegendValue(colors.energyGrid, stringResource(R.string.battery_from_grid), gridText)
        }
    }
}

@Composable
private fun LegendValue(color: Color, label: String, value: String) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Box(Modifier.size(10.dp).clip(RoundedCornerShape(3.dp)).background(color))
        Text(
            buildAnnotatedString {
                append("$label ")
                withStyle(SpanStyle(fontWeight = FontWeight.SemiBold, color = GgTheme.colors.ink, fontFeatureSettings = "tnum")) { append(value) }
            },
            style = GgTheme.typography.caption,
            color = GgTheme.colors.inkMuted,
        )
    }
}

/** Khung xương các khối phần trên (§3.34). */
@Composable
private fun LoadingBlocks() {
    val sunken = GgTheme.colors.surfaceSunken
    Column(Modifier.padding(horizontal = GgSpacing.space5), verticalArrangement = Arrangement.spacedBy(GgSpacing.space3)) {
        Box(Modifier.fillMaxWidth(0.5f).height(20.dp).clip(RoundedCornerShape(6.dp)).background(sunken))
        Box(Modifier.fillMaxWidth().height(44.dp).clip(RoundedCornerShape(16.dp)).background(sunken))
        GgCard { Box(Modifier.fillMaxWidth().height(290.dp).clip(RoundedCornerShape(10.dp)).background(sunken)) }
        GgCard { Box(Modifier.fillMaxWidth().height(60.dp).clip(RoundedCornerShape(10.dp)).background(sunken)) }
    }
}

@Composable
private fun previewTabs() = listOf(
    TabItem(stringResource(R.string.tab_main), R.drawable.ic_tab_main, selected = true) {},
    TabItem(stringResource(R.string.tab_devices), R.drawable.ic_tab_devices, selected = false) {},
    TabItem(stringResource(R.string.tab_service), R.drawable.ic_tab_service, selected = false) {},
)

/** Thống kê mẫu như prototype: ngày 24/09/2026, điểm 09:41, cột 08:00–10:00. */
fun previewStats(): StatsUiState {
    val day = java.time.LocalDate.of(2026, 9, 24)
    return StatsUiState(
        selection = vn.ggpower.inverter.domain.home.PeriodSelection(vn.ggpower.inverter.domain.home.StatsPeriod.Day, day),
        today = day,
        curves = FakeHomeRepository.SAMPLE_DAY,
        powerIndex = FakeHomeRepository.SAMPLE_DAY.power.lastIndex,
        batteryIndex = FakeHomeRepository.SAMPLE_DAY.battery.lastIndex,
        yieldBuckets = FakeHomeRepository.YIELD_DAY,
        yieldIndex = 4,
        summary = FakeHomeRepository.SUMMARY_DAY,
    )
}

@Preview(name = "Phần 1 (Main.png)", widthDp = 390, heightDp = 844, showBackground = true)
@Composable
private fun HomePreview() {
    GgPowerTheme(darkTheme = false) {
        HomeScreen(HomeUiState.Content(FakeHomeRepository.SAMPLE), previewStats(), previewTabs(), {}, {}, {})
    }
}

@Preview(name = "Ban đêm: pin xả, mua lưới, chế độ TOU", widthDp = 390, heightDp = 844, showBackground = true)
@Composable
private fun HomeNightPreview() {
    GgPowerTheme(darkTheme = false) {
        val night = FakeHomeRepository.SAMPLE.copy(
            unresolvedAlerts = 0,
            yieldTodayKwh = 12.0,
            energy = FakeHomeRepository.SAMPLE.energy.copy(pvKw = 0.0, gridKw = 0.2, batteryKw = -0.6, loadKw = 0.8, batterySocPercent = 41),
            mode = OperatingMode.Tou,
        )
        HomeScreen(HomeUiState.Content(night), previewStats(), previewTabs(), {}, {}, {})
    }
}

@Preview(name = "Đang tải", widthDp = 390, heightDp = 844, showBackground = true)
@Composable
private fun HomeLoadingPreview() {
    GgPowerTheme(darkTheme = false) { HomeScreen(HomeUiState.Loading, null, previewTabs(), {}, {}, {}) }
}
