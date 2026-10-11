package vn.ggpower.inverter.ui.home

import vn.ggpower.inverter.data.home.ChargedToday
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.runtime.saveable.rememberSaveable
import vn.ggpower.inverter.ui.home.house.NodesFlowDiagram
import vn.ggpower.inverter.domain.home.HomeLayout
import vn.ggpower.inverter.domain.home.FlowView
import vn.ggpower.inverter.ui.home.house.SkyHeight
import vn.ggpower.inverter.ui.home.house.SkyBackdrop
import vn.ggpower.inverter.domain.home.SkyMode
import vn.ggpower.inverter.domain.home.isNightSky
import java.time.LocalTime
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
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
import vn.ggpower.inverter.ui.home.house.HouseFlowDiagram
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
    val layout by viewModel.layout.collectAsStateWithLifecycle()
    HomeScreen(
        state, stats, tabs, onOpenAlerts, onOpenConfig, onOpenBatteryEnergy,
        onBackToProjects = onBackToProjects,
        layout = layout,
        onApplyLayout = viewModel::onApplyLayout,
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
    layout: HomeLayout = HomeLayout(),
    onApplyLayout: (HomeLayout) -> Unit = {},
) {
    val colors = GgTheme.colors
    val overview = (state as? HomeUiState.Content)?.overview
    // Màn "Tuỳ chỉnh màn hình chính" phủ toàn màn (cả thanh tab), như prototype.
    var customizing by rememberSaveable { mutableStateOf(false) }
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
                    EnergyFlowSection(overview, layout.view, onCustomize = { customizing = true })
                    ModeBatteryRow(overview, onOpenConfig, onOpenBatteryEnergy)
                    if (stats != null) HomeWidgets(layout, stats, statsActions)
                }
            }
        }
        FloatingTabBar(items = tabs, modifier = Modifier.align(Alignment.BottomCenter))
        if (customizing && overview != null) {
            HomeCustomizeScreen(
                initial = layout,
                snapshot = overview.energy,
                co2Kg = overview.co2TodayKg,
                onClose = { customizing = false },
                onApply = {
                    onApplyLayout(it)
                    customizing = false
                },
            )
        }
    }
}

/**
 * Mục "Dòng năng lượng" (Main.dc.html): không có thẻ, sơ đồ ngôi nhà cao 345 dp dưới tiêu đề. Ở chế độ tối có thêm
 * nền trời (MainDark / MainDarkNight): mục lùi xuống 16 dp, dải trời cao [SkyHeight] tràn 20 dp hai bên và bắt đầu
 * 28 dp trên tiêu đề, vẽ sau nội dung; ngày/đêm theo giờ máy (6h–18h là ngày).
 */
@Composable
private fun EnergyFlowSection(overview: HomeOverview, view: FlowView, onCustomize: () -> Unit) {
    val colors = GgTheme.colors
    var onScreen by remember { mutableStateOf(true) }
    val night = remember { isNightSky(SkyMode.Auto, LocalTime.now().hour) }
    Box(
        Modifier
            .padding(top = if (colors.isDark) GgSpacing.space4 else 0.dp)
            .onGloballyPositioned { onScreen = it.boundsInWindow().height > 0f },
    ) {
        if (colors.isDark) {
            SkyBackdrop(
                night = night,
                animate = onScreen,
                modifier = Modifier.layout { measurable, constraints ->
                    val bleed = 20.dp.roundToPx()
                    val p = measurable.measure(Constraints.fixed(constraints.maxWidth + 2 * bleed, SkyHeight.roundToPx()))
                    // Không chiếm chỗ trong bố cục: chỉ vẽ tràn ra ngoài mục.
                    layout(0, 0) { p.place(-bleed, -28.dp.roundToPx()) }
                },
            )
        }
        Column(verticalArrangement = Arrangement.spacedBy(GgSpacing.space3)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    stringResource(R.string.energy_flow_title),
                    style = cardTitle(),
                    color = colors.ink,
                    modifier = Modifier.weight(1f).semantics { heading() },
                )
                // Nút 44 dp lùi ra mép phải 10 dp và không làm hàng cao thêm (margin −9 −10 −9 0 của prototype).
                IconButton(
                    onClick = onCustomize,
                    modifier = Modifier
                        .layout { measurable, constraints ->
                            val p = measurable.measure(constraints)
                            val v = 9.dp.roundToPx()
                            layout(p.width - 10.dp.roundToPx(), (p.height - 2 * v).coerceAtLeast(0)) { p.place(0, -v) }
                        }
                        .size(44.dp),
                ) {
                    Icon(
                        painterResource(R.drawable.ic_layout_grid),
                        contentDescription = stringResource(R.string.cd_home_customize),
                        tint = colors.inkMuted,
                        modifier = Modifier.size(22.dp),
                    )
                }
            }
            when (view) {
                FlowView.House -> HouseFlowDiagram(overview.energy, overview.co2TodayKg)
                FlowView.Nodes -> NodesFlowDiagram(overview.energy)
            }
        }
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
 * Thẻ "Chế độ & pin" (Main.dc.html `wg.mode`, cố định ngay dưới sơ đồ): hai thẻ cạnh nhau cách 12 dp, cao bằng nhau,
 * đệm 14, các dòng cách 6 dp.
 */
@Composable
private fun ModeBatteryRow(overview: HomeOverview, onOpenConfig: () -> Unit, onOpenBatteryEnergy: () -> Unit) {
    Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(GgSpacing.space3)) {
        ModeCard(overview.mode, overview.backupSocPercent, onOpenConfig, Modifier.weight(1f).fillMaxHeight())
        BatteryCard(overview.energy.batterySocPercent, overview.chargedToday, onOpenBatteryEnergy, Modifier.weight(1f).fillMaxHeight())
    }
}

/**
 * Thẻ Chế độ hoạt động: ô icon 32 bo 10 `primarySoft`, mũi tên 20 `inkMuted`; "Chế độ hoạt động" 12/16 500, tên chế độ
 * 15/20 600, mô tả ngắn + SOC dự phòng 11/15 `inkMuted`. Bấm → Cấu hình dự án (16).
 */
@Composable
private fun ModeCard(mode: OperatingMode, backupSoc: Int, onClick: () -> Unit, modifier: Modifier) {
    val colors = GgTheme.colors
    val (nameRes, shortRes, icon) = modeTexts(mode)
    val name = stringResource(nameRes)
    val summary = stringResource(shortRes) + " · " +
        stringResource(R.string.mode_backup_soc, GgFormat.withUnit(backupSoc.toLong(), stringResource(R.string.unit_percent)))
    val cd = stringResource(R.string.cd_mode_box, name)
    GgCard(
        onClick = onClick,
        contentPadding = PaddingValues(14.dp),
        verticalSpacing = 6.dp,
        modifier = modifier.semantics { contentDescription = cd },
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(32.dp).clip(RoundedCornerShape(10.dp)).background(colors.primarySoft), contentAlignment = Alignment.Center) {
                Icon(painterResource(icon), contentDescription = null, tint = colors.primary, modifier = Modifier.size(20.dp))
            }
            Spacer(Modifier.weight(1f))
            Icon(painterResource(R.drawable.ic_chevron_right), contentDescription = null, tint = colors.inkMuted, modifier = Modifier.size(20.dp))
        }
        Text(stringResource(R.string.mode_label), style = GgTheme.typography.caption, color = colors.inkMuted)
        Text(name, style = GgTheme.typography.body.copy(fontSize = 15.sp, lineHeight = 20.sp, fontWeight = FontWeight.SemiBold), color = colors.ink)
        Text(summary, style = GgTheme.typography.caption.copy(fontSize = 11.sp, lineHeight = 15.sp, fontWeight = FontWeight.Normal), color = colors.inkMuted)
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
 * Thẻ Pin S16 Pre: icon pin 18 `energyBattery`, "Pin S16 Pre" 12/16 500, mũi tên 18; SOC 24/28 600 + "%" 12/16;
 * "Dung lượng sạc hôm nay" 11/14; chú giải Từ PV / Từ lưới (ô 8 bo 2, giá trị 600 canh phải) dồn xuống đáy thẻ.
 * [charged] null (bản live chưa có số) → "—". Bấm → Nguồn năng lượng pin (11).
 */
@Composable
private fun BatteryCard(soc: Int, charged: ChargedToday?, onClick: () -> Unit, modifier: Modifier) {
    val colors = GgTheme.colors
    val kwh = stringResource(R.string.unit_kwh)
    val missing = stringResource(R.string.value_missing)
    val pvText = charged?.let { GgFormat.withUnit(it.fromPvKwh, 1, kwh) } ?: missing
    val gridText = charged?.let { GgFormat.withUnit(it.fromGridKwh, 1, kwh) } ?: missing
    GgCard(
        onClick = onClick,
        onClickLabel = stringResource(R.string.cd_battery_box),
        contentPadding = PaddingValues(14.dp),
        verticalSpacing = 6.dp,
        modifier = modifier,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Icon(painterResource(R.drawable.ic_battery), contentDescription = null, tint = colors.energyBattery, modifier = Modifier.size(18.dp))
            Text(stringResource(R.string.battery_box_title), style = GgTheme.typography.caption, color = colors.inkMuted, modifier = Modifier.weight(1f), maxLines = 1)
            Icon(painterResource(R.drawable.ic_chevron_right), contentDescription = null, tint = colors.inkMuted, modifier = Modifier.size(18.dp))
        }
        Row(Modifier.padding(top = 2.dp), verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(GgSpacing.space1)) {
            Text(soc.toString(), style = GgTheme.typography.metric.copy(fontSize = 24.sp, lineHeight = 28.sp, fontWeight = FontWeight.SemiBold), color = colors.ink)
            Text(stringResource(R.string.unit_percent), style = GgTheme.typography.caption, color = colors.inkMuted, modifier = Modifier.padding(bottom = 3.dp))
        }
        Text(
            stringResource(R.string.battery_charged_today),
            style = GgTheme.typography.caption.copy(fontSize = 11.sp, lineHeight = 14.sp, fontWeight = FontWeight.Normal),
            color = colors.inkMuted,
            modifier = Modifier.offset(y = (-4).dp),
        )
        Spacer(Modifier.weight(1f))
        Column(verticalArrangement = Arrangement.spacedBy(GgSpacing.space1)) {
            LegendValue(colors.energyPv, stringResource(R.string.battery_from_pv), pvText)
            LegendValue(colors.energyGrid, stringResource(R.string.battery_from_grid), gridText)
        }
    }
}

@Composable
private fun LegendValue(color: Color, label: String, value: String) {
    Row(Modifier.fillMaxWidth().semantics(mergeDescendants = true) {}, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Box(Modifier.size(8.dp).clip(RoundedCornerShape(2.dp)).background(color))
        Text(label, style = GgTheme.typography.caption, color = GgTheme.colors.inkMuted, modifier = Modifier.weight(1f), maxLines = 1)
        Text(value, style = GgTheme.typography.caption.copy(fontWeight = FontWeight.SemiBold, fontFeatureSettings = "tnum"), color = GgTheme.colors.ink, maxLines = 1)
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
