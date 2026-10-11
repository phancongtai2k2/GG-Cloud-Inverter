package vn.ggpower.inverter.ui.config

import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role as SemanticsRole
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import vn.ggpower.inverter.R
import vn.ggpower.inverter.data.config.FakeConfigRepository
import vn.ggpower.inverter.domain.config.BlockReason
import vn.ggpower.inverter.domain.config.ChangedValue
import vn.ggpower.inverter.domain.config.ConfigChange
import vn.ggpower.inverter.domain.config.ConfigControls
import vn.ggpower.inverter.domain.config.ConfigRequest
import vn.ggpower.inverter.domain.config.ControlState
import vn.ggpower.inverter.domain.config.DeviceSettings
import vn.ggpower.inverter.domain.config.ModeControl
import vn.ggpower.inverter.domain.config.OperatingMode
import vn.ggpower.inverter.domain.config.ProjectConfig
import vn.ggpower.inverter.domain.config.Role
import vn.ggpower.inverter.domain.config.WriteGroup
import vn.ggpower.inverter.domain.config.WriteState
import vn.ggpower.inverter.domain.config.backupHours
import vn.ggpower.inverter.domain.config.configControls
import vn.ggpower.inverter.domain.format.GgFormat
import vn.ggpower.inverter.domain.tou.SlotKind
import vn.ggpower.inverter.domain.tou.TouStrategy
import vn.ggpower.inverter.domain.tou.summary
import vn.ggpower.inverter.ui.components.ButtonSize
import vn.ggpower.inverter.ui.components.ChangeDetail
import vn.ggpower.inverter.ui.components.ChangeLine
import vn.ggpower.inverter.ui.components.ConfirmChangesDialog
import vn.ggpower.inverter.ui.components.GgCard
import vn.ggpower.inverter.ui.components.GgToggle
import vn.ggpower.inverter.ui.components.LabeledSlider
import vn.ggpower.inverter.ui.components.NoticeDialog
import vn.ggpower.inverter.ui.components.ScreenHeader
import vn.ggpower.inverter.ui.components.SecondaryButton
import vn.ggpower.inverter.ui.components.SectionHeader
import vn.ggpower.inverter.ui.components.SliderTickLabel
import vn.ggpower.inverter.ui.components.StatusChip
import vn.ggpower.inverter.ui.components.StatusTone
import vn.ggpower.inverter.ui.components.Timeline24h
import vn.ggpower.inverter.ui.components.TimelineHourTicks
import vn.ggpower.inverter.ui.components.TimelineSize
import vn.ggpower.inverter.ui.components.TimelineSlot
import vn.ggpower.inverter.ui.theme.GgPowerTheme
import vn.ggpower.inverter.ui.theme.GgRadius
import vn.ggpower.inverter.ui.theme.GgSpacing
import vn.ggpower.inverter.ui.theme.GgTheme

@Composable
fun ConfigRoute(
    onBack: () -> Unit,
    onOpenTouSchedule: () -> Unit,
    viewModel: ConfigViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    // Quay về từ 16a / 16b: đọc lại để box Khung giờ sạc/xả đúng lịch vừa lưu.
    LifecycleResumeEffect(Unit) {
        viewModel.refresh()
        onPauseOrDispose { }
    }
    ConfigScreen(
        state = state,
        onBack = onBack,
        onRetry = viewModel::retry,
        onOpenTouSchedule = onOpenTouSchedule,
        actions = ConfigActions(
            onSelectMode = viewModel::onSelectMode,
            onBackupSocChange = viewModel::onBackupSocChange,
            onBackupSocChangeFinished = viewModel::onBackupSocChangeFinished,
            onConfirm = viewModel::onConfirm,
            onCancelConfirm = viewModel::onCancelConfirm,
            onNoticeAction = viewModel::onNoticeAction,
            onNoticeDismissed = viewModel::onNoticeDismissed,
            onUnknownDismissed = viewModel::onUnknownDismissed,
        ),
    )
}

/** Thao tác ghi của màn 16 (mặc định không làm gì — dùng cho Preview / màn debug). */
class ConfigActions(
    val onSelectMode: (OperatingMode) -> Unit = {},
    val onBackupSocChange: (Int) -> Unit = {},
    val onBackupSocChangeFinished: () -> Unit = {},
    val onConfirm: () -> Unit = {},
    val onCancelConfirm: () -> Unit = {},
    val onNoticeAction: () -> Unit = {},
    val onNoticeDismissed: (Long) -> Unit = {},
    val onUnknownDismissed: () -> Unit = {},
)

/**
 * Màn 16 · Cấu hình dự án (Config.png; vai trò A: Config__role-A-chu-ho.png; đặc tả §3.16, Thay đổi 0.6 §1–2,
 * ADR-001, ADR-002 mục 10–15, BO_SUNG_v3.md §1–3, §11). Chọn chế độ / thả thanh SOC dự phòng → hộp xác nhận
 * "cũ → mới" → lệnh ghi; giá trị chỉ đổi khi backend báo đã áp dụng.
 */
@Composable
fun ConfigScreen(
    state: ConfigUiState,
    onBack: () -> Unit,
    onRetry: () -> Unit,
    onOpenTouSchedule: () -> Unit,
    modifier: Modifier = Modifier,
    actions: ConfigActions = ConfigActions(),
) {
    ConfigScreenBody(state, onBack, onRetry, onOpenTouSchedule, modifier, actions)
}

@Composable
private fun ConfigScreenBody(
    state: ConfigUiState,
    onBack: () -> Unit,
    onRetry: () -> Unit,
    onOpenTouSchedule: () -> Unit,
    modifier: Modifier,
    actions: ConfigActions,
) {
    val colors = GgTheme.colors
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(colors.surface),
    ) {
        Column(Modifier.fillMaxSize().statusBarsPadding()) {
            ScreenHeader(
                title = stringResource(R.string.screen_config),
                onBack = onBack,
                actions = {
                    if (state is ConfigUiState.Content) {
                        StatusChip(
                            text = stringResource(roleName(state.config.writeAccess.role)),
                            tone = StatusTone.Neutral,
                            showDot = false,
                            modifier = Modifier.padding(horizontal = GgSpacing.space2),
                        )
                    }
                },
            )
            when (state) {
                ConfigUiState.Loading -> LoadingBlocks()
                is ConfigUiState.Error -> ErrorBlock(state.noConnection, onRetry)
                is ConfigUiState.Content -> Content(state, onOpenTouSchedule, actions)
            }
        }
        if (state is ConfigUiState.Content) {
            state.notice?.let { notice ->
                WriteNoticeSnackbar(
                    notice = notice,
                    onAction = actions.onNoticeAction,
                    onDismissed = actions.onNoticeDismissed,
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .navigationBarsPadding()
                        .padding(horizontal = GgSpacing.space5, vertical = GgSpacing.space4),
                )
            }
            state.confirm?.let { confirm ->
                ConfirmChangesDialog(
                    lines = listOf(changeLine(confirm.request, state.config)),
                    onConfirm = actions.onConfirm,
                    onDismiss = actions.onCancelConfirm,
                    warning = if (confirm.stale) stringResource(R.string.confirm_stale) else null,
                    detail = changeDetail(confirm.request, state.config.settings),
                )
            }
            if (state.showUnknownDialog) {
                NoticeDialog(
                    text = stringResource(R.string.write_unknown),
                    buttonText = stringResource(R.string.action_understood),
                    onDismiss = actions.onUnknownDismissed,
                )
            }
        }
    }
}

@Composable
private fun Content(state: ConfigUiState.Content, onOpenTouSchedule: () -> Unit, actions: ConfigActions) {
    val config = state.config
    val controls = state.controls
    val s = config.settings
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .navigationBarsPadding()
            .padding(start = GgSpacing.space5, end = GgSpacing.space5, bottom = GgSpacing.space6),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        controls.banner?.let { WriteBanner(it) }

        SectionTitle(R.string.config_section_mode)
        ModeList(controls.modes, actions.onSelectMode)
        if (state.writing == WriteGroup.OperatingMode) WritingRow()

        SectionHeader(stringResource(R.string.config_section_tou))
        TouBox(config.todayStrategy, onOpenTouSchedule)

        SectionTitle(R.string.config_section_battery)
        BatteryCard(state.backupSocDraft, s, controls, state.writing == WriteGroup.BackupSoc, actions)

        // Các mục chỉ xem bên dưới cần giá trị đọc từ inverter; chưa đọc được thì ẩn, không hiện số giả.
        if (s.available) {
            if (controls.showDistributorSections) {
                DistributorChargeCard(s)
                GridSection(s, showTechnician = controls.showTechnicianSections)
            }

            SectionTitle(R.string.config_section_protection)
            ProtectionCard(s, showTechnician = controls.showTechnicianSections)

            if (controls.showTechnicianSections) {
                DeviceSection(s)
            }
        }

        if (config.history.isNotEmpty()) {
            SectionTitle(R.string.config_section_history)
            HistoryCard(config.history)
        }
    }
}

// ---------- Ghi cấu hình (BO_SUNG_v3.md §1–3) ----------

/** Dòng "cũ → mới" không kèm tên tham số (Config.dc.html `dlg.value`); ô [changeDetail] bên dưới nói rõ hệ quả. */
@Composable
private fun changeLine(request: ConfigRequest, config: ProjectConfig): ChangeLine = when (request) {
    is ConfigRequest.Mode -> ChangeLine(
        "",
        stringResource(R.string.change_value, stringResource(modeCardTexts(config.mode).first), stringResource(modeCardTexts(request.mode).first)),
    )
    is ConfigRequest.BackupSoc -> {
        val percent = stringResource(R.string.unit_percent)
        ChangeLine(
            "",
            stringResource(
                R.string.change_value,
                GgFormat.withUnit(config.backupSocPercent.toLong(), percent),
                GgFormat.withUnit(request.percent.toLong(), percent),
            ),
        )
    }
    // Lịch TOU được sửa và xác nhận ở 16b, không mở từ màn này.
    is ConfigRequest.Tou -> ChangeLine(stringResource(R.string.config_section_tou), "")
}

/** Ô giải thích dưới dòng "cũ → mới" (Config.dc.html `dlg.descTitle` / `dlg.desc`). */
@Composable
private fun changeDetail(request: ConfigRequest, s: DeviceSettings): ChangeDetail? = when (request) {
    is ConfigRequest.Mode -> ChangeDetail(
        stringResource(R.string.config_confirm_mode_title, stringResource(modeCardTexts(request.mode).first)),
        stringResource(
            when (request.mode) {
                OperatingMode.GgAi -> R.string.config_confirm_mode_gg_ai
                OperatingMode.SelfConsumption -> R.string.config_confirm_mode_self
                OperatingMode.Tou -> R.string.config_confirm_mode_tou
                OperatingMode.MaxGridExport -> R.string.config_confirm_mode_grid
                OperatingMode.BatteryPriority -> R.string.config_confirm_mode_battery
                OperatingMode.Unknown -> return null
            },
        ),
    )
    is ConfigRequest.BackupSoc -> {
        val percent = GgFormat.withUnit(request.percent.toLong(), stringResource(R.string.unit_percent))
        val hours = backupHours(s.batteryCapacityKwh, request.percent, s.averageLoadKw)
        ChangeDetail(
            stringResource(R.string.config_confirm_soc_title),
            if (hours != null) stringResource(R.string.config_confirm_soc_hours, percent, GgFormat.number(hours, 1))
            else stringResource(R.string.config_confirm_soc, percent),
        )
    }
    is ConfigRequest.Tou -> null
}

// ---------- Đầu mục, nhãn cấp ----------

/** Tiêu đề mục cách khối trước thêm 8 dp (Config.dc.html: `margin-top: 8px` trong lưới cách 10 dp). */
@Composable
private fun SectionTitle(@StringRes text: Int, badge: (@Composable () -> Unit)? = null) {
    Row(
        Modifier.fillMaxWidth().padding(top = GgSpacing.space2),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(GgSpacing.space2),
    ) {
        SectionHeader(stringResource(text), Modifier.weight(1f))
        badge?.invoke()
    }
}

private enum class Level { Distributor, Technician }

/** Nhãn "CẤP B · NHÀ PHÂN PHỐI" / "CẤP C · KỸ THUẬT GGP": đệm 2/8, bo 6, chữ 11/16 700. */
@Composable
private fun LevelBadge(level: Level) {
    val colors = GgTheme.colors
    val (bg, fg, text) = when (level) {
        Level.Distributor -> Triple(colors.accentSoft, colors.badgeDistributorText, R.string.config_badge_distributor)
        Level.Technician -> Triple(colors.infoSoft, colors.info, R.string.config_badge_technician)
    }
    Text(
        text = stringResource(text),
        style = GgTheme.typography.label.copy(fontSize = 11.sp, lineHeight = 16.sp, fontWeight = FontWeight.Bold),
        color = fg,
        maxLines = 1,
        modifier = Modifier
            .clip(RoundedCornerShape(GgRadius.radiusSm))
            .background(bg)
            .padding(horizontal = GgSpacing.space2, vertical = 2.dp),
    )
}

// ---------- Chế độ vận hành ----------

@Composable
private fun ModeList(modes: List<ModeControl>, onSelect: (OperatingMode) -> Unit) {
    Column(Modifier.selectableGroup(), verticalArrangement = Arrangement.spacedBy(GgSpacing.space2)) {
        modes.forEach { ModeCard(it, onClick = { onSelect(it.mode) }) }
    }
}

/**
 * Thẻ chế độ (Config.dc.html `mk`): bo 14; đang chọn viền 2 dp `primary` nền `primarySoft`, ô icon trắng, dấu chọn tròn
 * 22 dp; không chọn viền 1 dp `cardBorder` nền `cardBg`. Bị khoá (GG AI pha 2, không có quyền ghi): mờ 60 %, trừ thẻ
 * đang chọn.
 */
@Composable
private fun ModeCard(control: ModeControl, onClick: () -> Unit) {
    val colors = GgTheme.colors
    val shape = RoundedCornerShape(14.dp)
    val enabled = control.state == ControlState.Enabled
    val (name, desc, icon) = modeCardTexts(control.mode)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .alpha(if (enabled || control.selected) 1f else 0.6f)
            .clip(shape)
            .background(if (control.selected) colors.primarySoft else colors.cardBg)
            .then(if (control.selected) Modifier.border(2.dp, colors.primary, shape) else Modifier)
            .selectable(
                selected = control.selected,
                enabled = enabled && !control.selected,
                role = SemanticsRole.RadioButton,
                onClick = onClick,
            )
            .padding(GgSpacing.space3),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(GgSpacing.space3),
    ) {
        Box(
            Modifier
                .size(40.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(if (control.selected) colors.surfaceRaised else colors.surfaceSunken),
            contentAlignment = Alignment.Center,
        ) {
            Icon(painterResource(icon), contentDescription = null, tint = colors.primary, modifier = Modifier.size(20.dp))
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(stringResource(name), style = rowTitle().copy(fontWeight = FontWeight.SemiBold), color = colors.ink)
                if (control.mode == OperatingMode.GgAi) {
                    Text(
                        stringResource(R.string.config_mode_phase2),
                        style = GgTheme.typography.label.copy(fontSize = 11.sp, lineHeight = 16.sp, fontWeight = FontWeight.Bold),
                        color = colors.inkMuted,
                        modifier = Modifier
                            .clip(RoundedCornerShape(GgRadius.radiusSm))
                            .background(colors.surfaceSunken)
                            .padding(horizontal = GgSpacing.space2, vertical = 2.dp),
                    )
                }
            }
            Text(stringResource(desc), style = GgTheme.typography.caption.copy(fontWeight = FontWeight.Normal), color = colors.inkMuted)
        }
        if (control.selected) {
            Box(Modifier.size(22.dp).clip(CircleShape).background(colors.primaryFill), contentAlignment = Alignment.Center) {
                Icon(painterResource(R.drawable.ic_check), contentDescription = null, tint = colors.onPrimary, modifier = Modifier.size(14.dp))
            }
        }
    }
}

private fun modeCardTexts(mode: OperatingMode): Triple<Int, Int, Int> = when (mode) {
    OperatingMode.GgAi -> Triple(R.string.mode_gg_ai, R.string.config_mode_desc_gg_ai, R.drawable.ic_mode_ai)
    OperatingMode.SelfConsumption -> Triple(R.string.mode_self_consumption, R.string.config_mode_desc_self, R.drawable.ic_mode_self)
    OperatingMode.Tou -> Triple(R.string.mode_tou, R.string.config_mode_desc_tou, R.drawable.ic_mode_tou)
    OperatingMode.MaxGridExport -> Triple(R.string.mode_max_grid_export, R.string.config_mode_desc_grid, R.drawable.ic_energy_grid)
    OperatingMode.BatteryPriority -> Triple(R.string.mode_battery_priority, R.string.config_mode_desc_battery, R.drawable.ic_battery)
    OperatingMode.Unknown -> Triple(R.string.mode_unknown, R.string.mode_unknown_short, R.drawable.ic_mode_self)
}

// ---------- Khung giờ sạc/xả (Thay đổi 0.6 §2) ----------

@Composable
private fun TouBox(today: TouStrategy?, onClick: () -> Unit) {
    val colors = GgTheme.colors
    val summary = today?.summary()
    GgCard(onClick = onClick, onClickLabel = stringResource(R.string.cd_config_tou_box)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(GgSpacing.space3)) {
            Column(Modifier.weight(1f)) {
                Text(stringResource(R.string.config_tou_today), style = GgTheme.typography.caption.copy(fontWeight = FontWeight.Normal), color = colors.inkMuted)
                Text(
                    today?.name ?: stringResource(R.string.config_tou_no_strategy),
                    style = GgTheme.typography.body.copy(fontSize = 16.sp, lineHeight = 22.sp, fontWeight = FontWeight.SemiBold),
                    color = colors.ink,
                )
            }
            Icon(painterResource(R.drawable.ic_chevron_right), contentDescription = null, tint = colors.lineStrong, modifier = Modifier.size(20.dp))
        }
        Timeline24h(
            slots = today?.slots.orEmpty().map { TimelineSlot(it.start, it.end, it.kind) },
            size = TimelineSize.Medium,
            modifier = Modifier.padding(top = 14.dp),
        )
        TimelineHourTicks(Modifier.padding(top = GgSpacing.space1))
        Column(Modifier.padding(top = GgSpacing.space3), verticalArrangement = Arrangement.spacedBy(GgSpacing.space1)) {
            if (summary == null || summary.isEmpty) {
                Text(stringResource(R.string.config_tou_empty), style = GgTheme.typography.bodySm, color = colors.inkMuted)
            } else {
                val parts = buildList {
                    if (summary.chargeCount > 0) add(pluralStringResource(R.plurals.config_tou_charge_count, summary.chargeCount, summary.chargeCount))
                    if (summary.dischargeCount > 0) add(pluralStringResource(R.plurals.config_tou_discharge_count, summary.dischargeCount, summary.dischargeCount))
                }
                Text(
                    pluralStringResource(R.plurals.config_tou_summary, summary.periods.size, summary.periods.size, parts.joinToString(", ")),
                    style = rowTitle().copy(fontWeight = FontWeight.SemiBold),
                    color = colors.ink,
                )
                val hour = stringResource(R.string.unit_hour)
                val minute = stringResource(R.string.unit_minute)
                val labelWidth = periodLabelWidth()
                summary.periods.forEach { p ->
                    val duration = GgFormat.duration(p.durationMinutes, hour, minute)
                    PeriodRow(
                        labelWidth = labelWidth,
                        kind = p.kind,
                        range = GgFormat.timeRange(p.start, p.end),
                        note = stringResource(
                            if (p.overnight) R.string.config_tou_duration_overnight else R.string.config_tou_duration,
                            duration,
                        ),
                    )
                }
            }
        }
    }
}

private fun periodLabelStyle(base: TextStyle) = base.copy(fontWeight = FontWeight.SemiBold)

/**
 * Bề rộng cột nhãn: 30 dp như prototype ("Sạc" / "Xả"), nới theo nhãn dài nhất khi ngôn ngữ khác
 * ("Discharge") để không bẻ chữ và cột giờ vẫn thẳng hàng.
 */
@Composable
private fun periodLabelWidth(): Dp {
    val measurer = rememberTextMeasurer()
    val style = periodLabelStyle(GgTheme.typography.bodySm)
    val labels = listOf(stringResource(R.string.tou_legend_charge), stringResource(R.string.tou_legend_discharge))
    val widest = labels.maxOf { measurer.measure(it, style, maxLines = 1).size.width }
    return maxOf(30.dp, with(LocalDensity.current) { widest.toDp() })
}

/** Dòng giai đoạn: chấm 8 dp, nhãn rộng [labelWidth] 600, giờ số đều, thời lượng `inkMuted`; chữ 13/20, cách 8 dp. */
@Composable
private fun PeriodRow(labelWidth: Dp, kind: SlotKind, range: String, note: String) {
    val colors = GgTheme.colors
    val style = GgTheme.typography.bodySm
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(GgSpacing.space2)) {
        Box(Modifier.size(8.dp).clip(CircleShape).background(if (kind == SlotKind.Charge) colors.touCharge else colors.touDischarge))
        Text(
            stringResource(if (kind == SlotKind.Charge) R.string.tou_legend_charge else R.string.tou_legend_discharge),
            style = periodLabelStyle(style),
            color = colors.ink,
            maxLines = 1,
            modifier = Modifier.width(labelWidth),
        )
        Text(range, style = style.copy(fontFeatureSettings = "tnum"), color = colors.ink, maxLines = 1)
        Text(note, style = style, color = colors.inkMuted, maxLines = 1)
    }
}

// ---------- Pin và ngưỡng SOC (§16.3) ----------

@Composable
private fun BatteryCard(backupSoc: Int, s: DeviceSettings, controls: ConfigControls, writing: Boolean, actions: ConfigActions) {
    val colors = GgTheme.colors
    val percent = stringResource(R.string.unit_percent)
    GgCard(contentPadding = PaddingValues(start = 16.dp, top = 14.dp, end = 16.dp, bottom = 4.dp)) {
        Column(verticalArrangement = Arrangement.spacedBy(GgSpacing.space2)) {
            LabeledSlider(
                label = stringResource(R.string.config_backup_soc),
                valueText = GgFormat.withUnit(backupSoc.toLong(), percent),
                value = backupSoc,
                onValueChange = actions.onBackupSocChange,
                onValueChangeFinished = actions.onBackupSocChangeFinished,
                valueRange = 5..95,
                step = 5,
                scale = 0..100,
                marks = listOf(50),
                tickLabels = listOf(
                    SliderTickLabel(0, "0"),
                    SliderTickLabel(20, "20"),
                    SliderTickLabel(50, "50"),
                    SliderTickLabel(100, "100 $percent"),
                ),
                enabled = controls.backupSoc == ControlState.Enabled,
                dimmed = controls.backupSocBlocked == BlockReason.BmsNotConnected,
            )
            if (writing) WritingRow()
            if (controls.backupSocBlocked == BlockReason.BmsNotConnected) {
                Text(stringResource(R.string.config_backup_bms_blocked), style = GgTheme.typography.caption.copy(fontWeight = FontWeight.Normal), color = colors.warning)
            }
            backupHours(s.batteryCapacityKwh, backupSoc, s.averageLoadKw)?.let { hours ->
                val load = GgFormat.withUnit(s.averageLoadKw, 1, stringResource(R.string.unit_kw))
                val bold = stringResource(R.string.config_backup_hours, GgFormat.number(hours, 1))
                val basis = stringResource(R.string.config_backup_basis, load)
                val lead = stringResource(R.string.config_backup_estimate)
                Text(
                    buildAnnotatedString {
                        append(lead); append(" ")
                        withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { append(bold) }
                        append(" ")
                        withStyle(SpanStyle(color = colors.inkMuted)) { append(basis) }
                    },
                    style = GgTheme.typography.bodySm,
                    color = colors.ink,
                )
            }
        }
        if (s.available) {
            ValueRow(R.string.config_stop_charge_soc, GgFormat.withUnit(s.stopChargeSocPercent.toLong(), percent))
            ValueRow(R.string.config_low_soc_alarm, GgFormat.withUnit(s.lowSocAlarmPercent.toLong(), percent))
            ValueRow(R.string.config_switch_to_grid_soc, GgFormat.withUnit(s.switchToGridSocPercent.toLong(), percent))
        }
    }
}

/** Thẻ cấp B dưới mục Pin: dòng sạc (§16.3). Chỉ xem ở bản demo (ADR-001). */
@Composable
private fun DistributorChargeCard(s: DeviceSettings) {
    val amp = stringResource(R.string.unit_ampere)
    GgCard(contentPadding = PaddingValues(start = 16.dp, top = 8.dp, end = 16.dp, bottom = 4.dp)) {
        Row(Modifier.fillMaxWidth().padding(vertical = GgSpacing.space1), horizontalArrangement = Arrangement.End) {
            LevelBadge(Level.Distributor)
        }
        ValueRow(
            R.string.config_max_charge_current, GgFormat.withUnit(s.maxChargeCurrentA.toLong(), amp),
            caption = s.bmsChargeLimitA?.let { stringResource(R.string.config_bms_limit, GgFormat.withUnit(it.toLong(), amp)) },
        )
        ValueRow(R.string.config_grid_charge_current, GgFormat.withUnit(s.gridChargeCurrentA.toLong(), amp))
        ValueRow(
            R.string.config_charge_limit_mode,
            if (s.chargeLimitMode == 1) stringResource(R.string.config_charge_limit_bms, 1) else s.chargeLimitMode.toString(),
        )
    }
}

// ---------- Lưới điện (§16.5) ----------

@Composable
private fun GridSection(s: DeviceSettings, showTechnician: Boolean) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        SectionTitle(R.string.config_section_grid) { LevelBadge(Level.Distributor) }
        GgCard(contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp)) {
            ValueRow(R.string.config_grid_work_mode, gridModeName(s.gridWorkMode))
            ValueRow(R.string.config_zero_export, GgFormat.withUnit(s.zeroExportW.toLong(), stringResource(R.string.unit_watt)))
            ValueRow(R.string.config_max_line_power, GgFormat.withUnit(s.maxLinePowerW.toLong(), stringResource(R.string.unit_watt)))
            if (showTechnician) {
                Row(Modifier.fillMaxWidth().padding(top = GgSpacing.space2), horizontalArrangement = Arrangement.End) {
                    LevelBadge(Level.Technician)
                }
                ValueRow(R.string.config_output_voltage, GgFormat.withUnit(s.outputVoltageV.toLong(), stringResource(R.string.unit_volt)))
                ValueRow(R.string.config_output_frequency, GgFormat.withUnit(s.outputFrequencyHz.toLong(), stringResource(R.string.unit_hertz)))
            }
        }
        Text(stringResource(R.string.config_grid_note), style = GgTheme.typography.caption.copy(fontWeight = FontWeight.Normal), color = GgTheme.colors.inkMuted)
    }
}

@Composable
private fun gridModeName(mode: Int): String = when (mode) {
    0 -> stringResource(R.string.config_grid_mode_0)
    1 -> stringResource(R.string.config_grid_mode_1)
    2 -> stringResource(R.string.config_grid_mode_2)
    3 -> stringResource(R.string.config_grid_mode_3)
    4 -> stringResource(R.string.config_grid_mode_4)
    else -> mode.toString()
}

// ---------- Bảo vệ và khởi động lại (§16.6) ----------

/**
 * Công tắc bảo vệ ngoài danh sách cho phép của ADR-001 → luôn bị vô hiệu ở bản demo, kèm chú thích
 * "Bản demo — chưa mở" dưới thẻ (BO_SUNG_v3.md §3).
 */
@Composable
private fun ProtectionCard(s: DeviceSettings, showTechnician: Boolean) {
    val p = s.protection
    Column(verticalArrangement = Arrangement.spacedBy(GgSpacing.space2)) {
        GgCard(contentPadding = PaddingValues(horizontal = 16.dp)) {
            ToggleRow(R.string.config_auto_restart_overload, p.autoRestartOverload)
            ToggleRow(R.string.config_auto_restart_overtemp, p.autoRestartOverTemp)
            ToggleRow(R.string.config_stop_on_bms_loss, p.stopOnBmsLoss)
            ToggleRow(R.string.config_alarm, p.alarmEnabled)
            ToggleRow(R.string.config_power_saving, p.powerSaving)
            if (showTechnician) {
                SettingRow(R.string.config_power_onoff, minHeight = 56) {
                    LevelBadge(Level.Technician)
                }
            }
        }
        Text(stringResource(R.string.config_demo_locked), style = GgTheme.typography.caption, color = GgTheme.colors.inkMuted)
    }
}

@Composable
private fun ToggleRow(@StringRes label: Int, checked: Boolean) {
    val name = stringResource(label)
    SettingRow(label, minHeight = 56) {
        GgToggle(checked = checked, onCheckedChange = {}, enabled = false, contentDescription = name)
    }
}

// ---------- Thiết bị và truyền thông (§16.7, cấp C) ----------

@Composable
private fun DeviceSection(s: DeviceSettings) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        SectionTitle(R.string.config_section_device) { LevelBadge(Level.Technician) }
        GgCard(contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp)) {
            val comm = when (s.bmsComm) {
                0 -> stringResource(R.string.config_bms_comm_0)
                1 -> stringResource(R.string.config_bms_comm_1)
                2 -> stringResource(R.string.config_bms_comm_2)
                else -> s.bmsComm.toString()
            }
            val parallel = when (s.parallelMode) {
                0 -> stringResource(R.string.config_parallel_0)
                1 -> stringResource(R.string.config_parallel_1)
                in 5..7 -> stringResource(R.string.config_parallel_3phase)
                else -> s.parallelMode.toString()
            }
            ValueRow(R.string.config_bms_comm, comm)
            ValueRow(R.string.config_bms_protocol, s.bmsProtocol)
            ValueRow(R.string.config_modbus_address, s.modbusAddress.toString())
            ValueRow(R.string.config_parallel_mode, parallel)
        }
    }
}

// ---------- Lịch sử thay đổi · 90 ngày (§16.8) ----------

@Composable
private fun HistoryCard(history: List<ConfigChange>) {
    val colors = GgTheme.colors
    GgCard(contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp)) {
        history.forEach { entry ->
            Row(Modifier.fillMaxWidth().padding(vertical = GgSpacing.space3), horizontalArrangement = Arrangement.spacedBy(GgSpacing.space3)) {
                Icon(
                    painterResource(R.drawable.ic_history), contentDescription = null, tint = colors.inkMuted,
                    modifier = Modifier.padding(top = 2.dp).size(18.dp),
                )
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(changeText(entry.value), style = GgTheme.typography.bodySm.copy(fontWeight = FontWeight.SemiBold), color = colors.ink)
                    Text(
                        stringResource(R.string.config_history_meta, entry.actor, GgFormat.dateTimeLog(entry.at)),
                        style = GgTheme.typography.caption.copy(fontWeight = FontWeight.Normal),
                        color = colors.inkMuted,
                    )
                }
            }
        }
    }
}

@Composable
private fun changeText(value: ChangedValue): String = when (value) {
    is ChangedValue.BackupSoc -> {
        val percent = stringResource(R.string.unit_percent)
        stringResource(
            R.string.config_history_change, stringResource(R.string.config_history_backup_soc),
            GgFormat.withUnit(value.old.toLong(), percent), GgFormat.withUnit(value.new.toLong(), percent),
        )
    }
    is ChangedValue.Mode -> stringResource(
        R.string.config_history_change, stringResource(R.string.config_history_mode),
        stringResource(modeCardTexts(value.old).first), stringResource(modeCardTexts(value.new).first),
    )
}

// ---------- Dòng tham số dùng chung ----------

/** Chữ 14/20 của tên tham số, tên chế độ, dòng tóm tắt. */
@Composable
private fun rowTitle(): TextStyle = GgTheme.typography.body.copy(fontSize = 14.sp, lineHeight = 20.sp)

/**
 * Dòng tham số (Config.dc.html): nội dung cao tối thiểu 52 dp (56 dp với công tắc) cộng đệm dọc 8; tên 14/20 500 (không hiện mã thanh ghi),
 * chú thích 12/16 `warning` nếu có; phần phải là [trailing].
 */
@Composable
private fun SettingRow(
    @StringRes label: Int,
    caption: String? = null,
    minHeight: Int = 52,
    trailing: @Composable () -> Unit,
) {
    val colors = GgTheme.colors
    Row(
        // Như CSS content-box của prototype: min-height tính cho phần nội dung, cộng thêm đệm 8 dp trên/dưới.
        Modifier.fillMaxWidth().padding(vertical = GgSpacing.space2).heightIn(min = minHeight.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(GgSpacing.space3),
    ) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                stringResource(label),
                style = rowTitle().copy(fontWeight = FontWeight.Medium),
                color = colors.ink,
            )
            if (caption != null) {
                Text(caption, style = GgTheme.typography.caption.copy(fontWeight = FontWeight.Normal), color = colors.warning)
            }
        }
        trailing()
    }
}

@Composable
private fun ValueRow(
    @StringRes label: Int,
    value: String,
    caption: String? = null,
) {
    SettingRow(label, caption = caption) {
        Text(
            value,
            style = rowTitle().copy(fontWeight = FontWeight.SemiBold, fontFeatureSettings = "tnum"),
            color = GgTheme.colors.ink,
            textAlign = TextAlign.End,
        )
    }
}

@StringRes
private fun roleName(role: Role): Int = when (role) {
    Role.Owner -> R.string.config_role_owner
    Role.Distributor -> R.string.config_role_distributor
    Role.GgpTechnician -> R.string.config_role_technician
}

// ---------- Đang tải / lỗi ----------

/** Khung xương (§3.34). */
@Composable
private fun LoadingBlocks() {
    val sunken = GgTheme.colors.surfaceSunken
    Column(
        Modifier.padding(horizontal = GgSpacing.space5, vertical = GgSpacing.space2),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Box(Modifier.fillMaxWidth(0.45f).height(16.dp).clip(RoundedCornerShape(GgRadius.radiusSm)).background(sunken))
        repeat(4) { Box(Modifier.fillMaxWidth().height(64.dp).clip(RoundedCornerShape(14.dp)).background(sunken)) }
        Box(Modifier.fillMaxWidth(0.45f).height(16.dp).clip(RoundedCornerShape(GgRadius.radiusSm)).background(sunken))
        GgCard { Box(Modifier.fillMaxWidth().height(150.dp).clip(RoundedCornerShape(GgRadius.radiusMd)).background(sunken)) }
    }
}

@Composable
private fun ColumnScope.ErrorBlock(noConnection: Boolean, onRetry: () -> Unit) {
    Column(
        Modifier.weight(1f).fillMaxWidth().padding(horizontal = GgSpacing.space8),
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

/** Trạng thái mẫu cho Preview và màn debug: [role], [writeState], đang chạy [mode]. */
fun previewConfigState(
    role: Role = Role.GgpTechnician,
    writeState: WriteState = WriteState.Enabled,
    mode: OperatingMode = OperatingMode.SelfConsumption,
    bmsBlocked: Boolean = false,
    configReadOnly: Boolean = false,
): ConfigUiState.Content {
    val base = FakeConfigRepository.accessFor(role, writeState)
    val access = if (bmsBlocked) {
        base.copy(
            writableGroups = base.writableGroups - WriteGroup.BackupSoc,
            blockedGroups = mapOf(WriteGroup.BackupSoc to BlockReason.BmsNotConnected),
        )
    } else base
    val config = FakeConfigRepository.SAMPLE.copy(mode = mode, writeAccess = access)
    return ConfigUiState.Content(config, configControls(access, mode, configReadOnly))
}

@Preview(widthDp = 390, heightDp = 3200, showBackground = true)
@Composable
private fun ConfigTechnicianPreview() {
    GgPowerTheme(darkTheme = false) {
        ConfigScreen(previewConfigState(), onBack = {}, onRetry = {}, onOpenTouSchedule = {})
    }
}

@Preview(widthDp = 390, heightDp = 2200, showBackground = true)
@Composable
private fun ConfigOwnerPreview() {
    GgPowerTheme(darkTheme = false) {
        ConfigScreen(previewConfigState(role = Role.Owner), onBack = {}, onRetry = {}, onOpenTouSchedule = {})
    }
}

@Preview(widthDp = 390, heightDp = 844, showBackground = true)
@Composable
private fun ConfigOfflinePreview() {
    GgPowerTheme(darkTheme = false) {
        ConfigScreen(
            previewConfigState(role = Role.Distributor, writeState = WriteState.GatewayOffline),
            onBack = {}, onRetry = {}, onOpenTouSchedule = {},
        )
    }
}

@Preview(widthDp = 390, heightDp = 844, showBackground = true)
@Composable
private fun ConfigLoadingPreview() {
    GgPowerTheme(darkTheme = false) {
        ConfigScreen(ConfigUiState.Loading, onBack = {}, onRetry = {}, onOpenTouSchedule = {})
    }
}

@Preview(widthDp = 390, heightDp = 844, showBackground = true)
@Composable
private fun ConfigErrorPreview() {
    GgPowerTheme(darkTheme = false) {
        ConfigScreen(ConfigUiState.Error(noConnection = true), onBack = {}, onRetry = {}, onOpenTouSchedule = {})
    }
}
