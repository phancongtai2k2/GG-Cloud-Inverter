package vn.ggpower.inverter.ui.tou

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.InlineTextContent
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.appendInlineContent
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.Placeholder
import androidx.compose.ui.text.PlaceholderVerticalAlign
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import vn.ggpower.inverter.R
import vn.ggpower.inverter.data.config.FakeConfigRepository
import vn.ggpower.inverter.domain.config.OperatingMode
import vn.ggpower.inverter.domain.format.GgFormat
import vn.ggpower.inverter.domain.tou.MAX_SLOTS_PER_TYPE
import vn.ggpower.inverter.domain.tou.MAX_STRATEGY_NAME_LENGTH
import vn.ggpower.inverter.domain.tou.SlotError
import vn.ggpower.inverter.domain.tou.SlotKind
import vn.ggpower.inverter.domain.tou.StrategyNameError
import vn.ggpower.inverter.domain.tou.TouChange
import vn.ggpower.inverter.domain.tou.TouSlot
import vn.ggpower.inverter.domain.tou.TouStrategy
import vn.ggpower.inverter.domain.tou.diffSchedules
import vn.ggpower.inverter.domain.tou.strategyLetter
import vn.ggpower.inverter.ui.components.BulletList
import vn.ggpower.inverter.ui.components.ButtonSize
import vn.ggpower.inverter.ui.components.ChangeLine
import vn.ggpower.inverter.ui.components.ConfirmChangesDialog
import vn.ggpower.inverter.ui.components.DangerTextButton
import vn.ggpower.inverter.ui.components.DashedButton
import vn.ggpower.inverter.ui.components.DayChips
import vn.ggpower.inverter.ui.components.ErrorBox
import vn.ggpower.inverter.ui.components.GgBottomSheet
import vn.ggpower.inverter.ui.components.GgCard
import vn.ggpower.inverter.ui.components.GgDialog
import vn.ggpower.inverter.ui.components.GgTextField
import vn.ggpower.inverter.ui.components.NoticeDialog
import vn.ggpower.inverter.ui.components.PrimaryButton
import vn.ggpower.inverter.ui.components.ScreenHeader
import vn.ggpower.inverter.ui.components.SecondaryButton
import vn.ggpower.inverter.ui.components.SegmentedControl
import vn.ggpower.inverter.ui.components.SegmentedSize
import vn.ggpower.inverter.ui.components.Timeline24h
import vn.ggpower.inverter.ui.components.TimelineHourTicks
import vn.ggpower.inverter.ui.components.TimelineLegend
import vn.ggpower.inverter.ui.components.TimelineSize
import vn.ggpower.inverter.ui.components.TimelineSlot
import vn.ggpower.inverter.ui.config.WriteBanner
import vn.ggpower.inverter.ui.config.WriteNoticeSnackbar
import vn.ggpower.inverter.ui.config.WritingRow
import vn.ggpower.inverter.ui.format.weekdayLabels
import vn.ggpower.inverter.ui.theme.GgPowerTheme
import vn.ggpower.inverter.ui.theme.GgRadius
import vn.ggpower.inverter.ui.theme.GgSpacing
import vn.ggpower.inverter.ui.theme.GgTheme
import java.time.DayOfWeek

@Composable
fun TouStrategyRoute(onBack: () -> Unit, viewModel: TouStrategyViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val requestBack = { if (viewModel.onBackRequest()) onBack() }
    val content = state as? TouStrategyUiState.Content
    BackHandler(enabled = content?.hasChanges == true || content?.writing == true) { requestBack() }
    // Xoá chiến lược thành công → về 16a (ADR-002 mục 8).
    LaunchedEffect(content?.finished) { if (content?.finished == true) onBack() }
    TouStrategyScreen(
        state = state,
        onRetry = viewModel::retry,
        actions = TouStrategyActions(
            onBack = requestBack,
            onToggleDay = viewModel::onToggleDay,
            onRenameOpen = viewModel::onRenameOpen,
            onRenameChange = viewModel::onRenameChange,
            onRenameSave = viewModel::onRenameSave,
            onRenameCancel = viewModel::onRenameCancel,
            onDiscardConfirm = onBack,
            onDiscardCancel = viewModel::onDiscardCancel,
            onOpenSlot = viewModel::onOpenSlot,
            onAddSlot = viewModel::onAddSlot,
            onSlotKindChange = viewModel::onSlotKindChange,
            onSlotFieldChange = viewModel::onSlotFieldChange,
            onSlotSave = viewModel::onSlotSave,
            onSlotDelete = viewModel::onSlotDelete,
            onSlotCancel = viewModel::onSlotCancel,
            onSave = viewModel::onSave,
            onDeleteStrategy = { if (viewModel.onDeleteStrategy()) onBack() },
            onConfirm = viewModel::onConfirm,
            onCancelConfirm = viewModel::onCancelConfirm,
            onNoticeAction = viewModel::onNoticeAction,
            onNoticeDismissed = viewModel::onNoticeDismissed,
            onUnknownDismissed = viewModel::onUnknownDismissed,
        ),
    )
}

/** Thao tác của màn 16b (mặc định không làm gì — dùng cho Preview / màn debug). */
class TouStrategyActions(
    val onBack: () -> Unit = {},
    val onToggleDay: (DayOfWeek) -> Unit = {},
    val onRenameOpen: () -> Unit = {},
    val onRenameChange: (String) -> Unit = {},
    val onRenameSave: () -> Unit = {},
    val onRenameCancel: () -> Unit = {},
    val onDiscardConfirm: () -> Unit = {},
    val onDiscardCancel: () -> Unit = {},
    val onOpenSlot: (Int) -> Unit = {},
    val onAddSlot: () -> Unit = {},
    val onSlotKindChange: (SlotKind) -> Unit = {},
    val onSlotFieldChange: (SlotField, String) -> Unit = { _, _ -> },
    val onSlotSave: () -> Unit = {},
    val onSlotDelete: () -> Unit = {},
    val onSlotCancel: () -> Unit = {},
    val onDeleteStrategy: () -> Unit = {},
    val onSave: () -> Unit = {},
    val onConfirm: () -> Unit = {},
    val onCancelConfirm: () -> Unit = {},
    val onNoticeAction: () -> Unit = {},
    val onNoticeDismissed: (Long) -> Unit = {},
    val onUnknownDismissed: () -> Unit = {},
)

/**
 * Màn 16b · Chi tiết chiến lược (TouStrategy.png, TouStrategy__weekend.png; Thay đổi 0.6 §4; ADR-002 mục 2–5, 8;
 * BO_SUNG_v3.md §4). Mọi thao tác chỉ đổi bản nháp; thanh đáy "Lưu thay đổi" gửi một lệnh cho cả danh sách.
 */
@Composable
fun TouStrategyScreen(
    state: TouStrategyUiState,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
    actions: TouStrategyActions = TouStrategyActions(),
) {
    Box(modifier.fillMaxSize()) {
        Column(
            Modifier
                .fillMaxSize()
                .background(GgTheme.colors.surface)
                .statusBarsPadding(),
        ) {
            val content = state as? TouStrategyUiState.Content
            ScreenHeader(
                title = content?.strategy?.name ?: stringResource(R.string.screen_tou_strategy),
                onBack = actions.onBack,
                backDescription = stringResource(R.string.cd_back_to_strategies),
                actions = {
                    if (content?.interactive == true) {
                        IconButton(onClick = actions.onRenameOpen, modifier = Modifier.size(48.dp)) {
                            Icon(
                                painterResource(R.drawable.ic_edit),
                                contentDescription = stringResource(R.string.cd_rename_strategy),
                                tint = GgTheme.colors.ink,
                                modifier = Modifier.size(20.dp),
                            )
                        }
                    }
                },
            )
            when (state) {
                TouStrategyUiState.Loading -> LoadingBlocks()
                is TouStrategyUiState.Error -> ErrorBlock(state.noConnection, onRetry)
                is TouStrategyUiState.Content -> {
                    Body(state, actions, Modifier.weight(1f))
                    if (state.editable || state.writing) SaveBar(state.hasChanges, state.saveEnabled, state.writing, actions.onSave)
                }
            }
        }
        if (state is TouStrategyUiState.Content) {
            state.rename?.let { RenameSheet(it, actions) }
            state.slotSheet?.let { SlotSheet(it, state.strategy, actions) }
            if (state.showDiscard) DiscardDialog(actions)
            state.confirm?.let { confirm ->
                ConfirmChangesDialog(
                    lines = touChangeLines(state.saved, confirm.draft, state.strategy.name),
                    onConfirm = actions.onConfirm,
                    onDismiss = actions.onCancelConfirm,
                    warning = if (confirm.stale) stringResource(R.string.confirm_stale) else null,
                    info = if (state.mode != OperatingMode.Tou) stringResource(R.string.confirm_not_tou) else null,
                )
            }
            if (state.showUnknownDialog) {
                NoticeDialog(stringResource(R.string.write_unknown), stringResource(R.string.action_understood), actions.onUnknownDismissed)
            }
            state.notice?.let { notice ->
                WriteNoticeSnackbar(
                    notice = notice,
                    onAction = actions.onNoticeAction,
                    onDismissed = actions.onNoticeDismissed,
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .navigationBarsPadding()
                        .padding(start = GgSpacing.space5, end = GgSpacing.space5, bottom = if (state.editable || state.writing) 96.dp else GgSpacing.space4),
                )
            }
        }
    }
}

@Composable
private fun Body(state: TouStrategyUiState.Content, actions: TouStrategyActions, modifier: Modifier) {
    val strategy = state.strategy
    Column(
        modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(start = GgSpacing.space5, end = GgSpacing.space5, bottom = GgSpacing.space6),
        verticalArrangement = Arrangement.spacedBy(GgSpacing.space3),
    ) {
        state.banner?.let { WriteBanner(it) }
        Intro(state.index)
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            // Đang sửa một khung: các khung khác mờ 35 % (Thay đổi 0.6 §5).
            val editing = state.slotSheet?.editingIndex
            Timeline24h(
                slots = strategy.slots.mapIndexed { i, it -> TimelineSlot(it.start, it.end, it.kind, dimmed = editing != null && i != editing) },
                size = TimelineSize.Large,
            )
            TimelineHourTicks()
            TimelineLegend()
        }
        SlotsCard(state, actions)
        DashedButton(stringResource(R.string.tou_add_slot), onClick = actions.onAddSlot, enabled = state.interactive)
        BulletList(listOf(stringResource(R.string.tou_strategy_note_limit), stringResource(R.string.tou_strategy_note_tap)))
        if (state.canDelete) DangerTextButton(stringResource(R.string.tou_delete_strategy), onClick = actions.onDeleteStrategy)
    }
}

/** Dòng giới thiệu bắt đầu bằng ô chữ cái 22 dp (bo 8, màu chiến lược, chữ 11/700 trắng), chữ 13/20 `inkMuted`. */
@Composable
private fun Intro(index: Int) {
    val colors = GgTheme.colors
    val letter = strategyLetter(index).toString()
    val badgeColor = colors.strategy[index % colors.strategy.size]
    val text = buildAnnotatedString {
        appendInlineContent(BADGE, letter)
        append(" ")
        append(stringResource(R.string.tou_strategy_intro))
    }
    val inline = mapOf(
        BADGE to InlineTextContent(Placeholder(22.sp, 22.sp, PlaceholderVerticalAlign.TextCenter)) {
            Box(
                Modifier.fillMaxSize().clip(RoundedCornerShape(8.dp)).background(badgeColor),
                contentAlignment = Alignment.Center,
            ) {
                Text(letter, style = GgTheme.typography.label.copy(fontSize = 11.sp, lineHeight = 14.sp, fontWeight = FontWeight.Bold), color = colors.onPrimary)
            }
        },
    )
    Text(text, inlineContent = inline, style = GgTheme.typography.bodySm, color = colors.inkMuted)
}

private const val BADGE = "badge"

/** Thẻ khung + ngày áp dụng (TouStrategy.dc.html `<section>`): đệm 8/16/12, cách 8. */
@Composable
private fun SlotsCard(state: TouStrategyUiState.Content, actions: TouStrategyActions) {
    val colors = GgTheme.colors
    val strategy = state.strategy
    GgCard(contentPadding = PaddingValues(start = 16.dp, top = 8.dp, end = 16.dp, bottom = 12.dp), verticalSpacing = GgSpacing.space2) {
        val order = strategy.slots.indices.sortedWith(compareBy({ strategy.slots[it].kind.ordinal }, { strategy.numberOf(it) }))
        if (order.isEmpty()) {
            Text(
                stringResource(R.string.config_tou_empty),
                style = GgTheme.typography.bodySm,
                color = colors.inkMuted,
                modifier = Modifier.padding(top = GgSpacing.space3, bottom = GgSpacing.space1),
            )
        } else {
            // Như prototype: các dòng là con trực tiếp của thẻ (cách 8 dp), viền trên thuộc dòng sau.
            Column(verticalArrangement = Arrangement.spacedBy(GgSpacing.space2)) {
                order.forEachIndexed { i, index ->
                    Column {
                        if (i > 0) HorizontalDivider(thickness = 1.dp, color = colors.cardBorder)
                        SlotRow(strategy.slots[index], strategy.numberOf(index), enabled = state.interactive) { actions.onOpenSlot(index) }
                    }
                }
            }
        }
        DaysSection(state, actions)
    }
}

/** Dòng khung: vạch màu 4 dp, "Sạc 1 · 22:00 – 06:00" 14/20 600, dòng phụ 12/16 `inkMuted`, mũi tên; đệm dọc 10. */
@Composable
private fun SlotRow(slot: TouSlot, number: Int, enabled: Boolean, onClick: () -> Unit) {
    val colors = GgTheme.colors
    val name = slotName(slot.kind, number)
    val cd = stringResource(R.string.cd_edit_slot, name)
    val percent = stringResource(R.string.unit_percent)
    val meta = stringResource(
        if (slot.kind == SlotKind.Charge) R.string.tou_slot_meta_charge else R.string.tou_slot_meta_discharge,
        GgFormat.withUnit(slot.stopSocPercent.toLong(), percent),
        GgFormat.withUnit(slot.maxPowerW.toLong(), stringResource(R.string.unit_watt)),
    )
    Row(
        Modifier
            .fillMaxWidth()
            .height(IntrinsicSize.Min)
            .clickable(enabled = enabled, onClickLabel = cd, role = Role.Button, onClick = onClick)
            .padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(GgSpacing.space3),
    ) {
        Box(
            Modifier
                .width(4.dp)
                .fillMaxHeight()
                .clip(CircleShape)
                .background(if (slot.kind == SlotKind.Charge) colors.touCharge else colors.touDischarge),
        )
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                "$name · ${GgFormat.timeRange(slot.start, slot.end)}",
                style = GgTheme.typography.body.copy(fontSize = 14.sp, lineHeight = 20.sp, fontWeight = FontWeight.SemiBold, fontFeatureSettings = "tnum"),
                color = colors.ink,
            )
            Text(meta, style = GgTheme.typography.caption.copy(fontWeight = FontWeight.Normal), color = colors.inkMuted)
        }
        Icon(painterResource(R.drawable.ic_chevron_right), contentDescription = null, tint = colors.lineStrong, modifier = Modifier.size(18.dp))
    }
}

@Composable
private fun slotName(kind: SlotKind, number: Int): String =
    stringResource(if (kind == SlotKind.Charge) R.string.tou_slot_charge else R.string.tou_slot_discharge, number)

/**
 * "Áp dụng các ngày" (viền trên 1 dp, đệm trên 6, cách 6): tiêu đề 13/18 600; chip ngày; dòng nhắc thứ đang thuộc
 * chiến lược khác 12/18 `inkMuted`; chưa chọn ngày nào → dòng đỏ 12/18 600 (ADR-002 mục 3); mã E225.
 */
@Composable
private fun DaysSection(state: TouStrategyUiState.Content, actions: TouStrategyActions) {
    val colors = GgTheme.colors
    val labels = weekdayLabels()
    val small = GgTheme.typography.caption.copy(fontWeight = FontWeight.Normal, lineHeight = 18.sp)
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        HorizontalDivider(thickness = 1.dp, color = colors.cardBorder)
        Text(
            stringResource(R.string.tou_apply_days),
            style = GgTheme.typography.body.copy(fontSize = 13.sp, lineHeight = 18.sp, fontWeight = FontWeight.SemiBold),
            color = colors.ink,
            modifier = Modifier.padding(top = GgSpacing.space2),
        )
        DayChips(selected = state.strategy.weekdays, onToggle = actions.onToggleDay, labels = labels, enabled = state.interactive)
        val others = state.daysOfOthers
        if (others.isNotEmpty()) {
            val pattern = stringResource(R.string.tou_days_owned)
            val owned = others.entries.joinToString("; ") { (s, days) ->
                String.format(pattern, days.joinToString(", ") { labels.short.getValue(it) }, s.name)
            }
            Text(stringResource(R.string.tou_days_owned_note, owned), style = small, color = colors.inkMuted)
        }
        if (state.missingDays) {
            Text(stringResource(R.string.tou_no_days_warning), style = small.copy(fontWeight = FontWeight.SemiBold), color = colors.dangerText)
        }
    }
}

/**
 * Thanh đáy cố định (BO_SUNG_v3.md §4): cao 80 dp, nền `surfaceRaised`, viền trên 1 dp `line`, lề 20 dp;
 * trái "Có thay đổi chưa lưu" khi có thay đổi, "Đang ghi…" khi đang có lệnh (BO_SUNG_v3.md §2); phải "Lưu thay đổi".
 */
@Composable
private fun SaveBar(hasChanges: Boolean, enabled: Boolean, writing: Boolean, onSave: () -> Unit) {
    val colors = GgTheme.colors
    Column(Modifier.fillMaxWidth().background(colors.surfaceRaised).navigationBarsPadding()) {
        HorizontalDivider(thickness = 1.dp, color = colors.line)
        Row(
            Modifier.fillMaxWidth().height(79.dp).padding(horizontal = GgSpacing.space5),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(GgSpacing.space3),
        ) {
            Box(Modifier.weight(1f)) {
                if (writing) {
                    WritingRow()
                } else if (hasChanges) {
                    Text(stringResource(R.string.tou_unsaved), style = GgTheme.typography.bodySm, color = colors.inkMuted)
                }
            }
            PrimaryButton(stringResource(R.string.tou_save_changes), onClick = onSave, enabled = enabled, loading = writing, size = ButtonSize.Medium)
        }
    }
}

/** Đổi tên chiến lược (BO_SUNG_v3.md §4): một ô nhập, đếm "n/40", lỗi khi trống; "Lưu" chỉ đổi bản nháp. */
@Composable
private fun RenameSheet(rename: RenameState, actions: TouStrategyActions) {
    val colors = GgTheme.colors
    GgBottomSheet(onDismiss = actions.onRenameCancel, title = stringResource(R.string.tou_rename_title)) {
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            GgTextField(
                label = stringResource(R.string.tou_rename_label),
                value = rename.text,
                onValueChange = actions.onRenameChange,
                isError = rename.error != null,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
            )
            Text(
                stringResource(R.string.tou_rename_counter, rename.text.length, MAX_STRATEGY_NAME_LENGTH),
                style = GgTheme.typography.caption.copy(fontWeight = FontWeight.Normal, fontFeatureSettings = "tnum"),
                color = colors.inkMuted,
                textAlign = TextAlign.End,
                modifier = Modifier.fillMaxWidth(),
            )
        }
        if (rename.error == StrategyNameError.Empty) ErrorBox(stringResource(R.string.tou_rename_empty))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            SecondaryButton(stringResource(R.string.action_cancel), onClick = actions.onRenameCancel, size = ButtonSize.Medium, modifier = Modifier.weight(1f))
            PrimaryButton(
                stringResource(R.string.action_save), onClick = actions.onRenameSave, size = ButtonSize.Medium,
                enabled = rename.error == null, modifier = Modifier.weight(2f),
            )
        }
    }
}

/**
 * Popup thêm / sửa khung (TouStrategy__edit-sheet.png, TouStrategy__add-sheet-full.png; Thay đổi 0.6 §5): vạch màu
 * theo loại; khi thêm có bộ chọn "Khung sạc / Khung xả"; lưới 2 × 2 ô nhập cách 10 dp; khung sạc có dòng "Nguồn sạc:
 * lưới điện"; hộp lỗi đỏ; "Huỷ" · "Lưu" (1 : 2); khi sửa có "Xoá khung này".
 */
@Composable
private fun SlotSheet(sheet: SlotSheetState, strategy: TouStrategy, actions: TouStrategyActions) {
    val colors = GgTheme.colors
    val kind = sheet.input.kind
    val title = if (sheet.editingIndex == null) {
        stringResource(R.string.tou_sheet_add_title)
    } else {
        stringResource(R.string.tou_sheet_edit_title, slotName(kind, strategy.numberOf(sheet.editingIndex)))
    }
    val numeric = KeyboardOptions(keyboardType = KeyboardType.Number)
    GgBottomSheet(
        onDismiss = actions.onSlotCancel,
        title = title,
        accent = if (kind == SlotKind.Charge) colors.touCharge else colors.touDischarge,
    ) {
        if (sheet.editingIndex == null) {
            SegmentedControl(
                options = listOf(stringResource(R.string.tou_sheet_kind_charge), stringResource(R.string.tou_sheet_kind_discharge)),
                selectedIndex = kind.ordinal,
                onSelect = { actions.onSlotKindChange(SlotKind.entries[it]) },
                size = SegmentedSize.Regular,
                groupLabel = stringResource(R.string.tou_sheet_kind_group),
            )
        }
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                GgTextField(
                    stringResource(R.string.tou_sheet_start), sheet.input.start, { actions.onSlotFieldChange(SlotField.Start, it) },
                    Modifier.weight(1f), keyboardOptions = numeric,
                )
                GgTextField(
                    stringResource(R.string.tou_sheet_end), sheet.input.end, { actions.onSlotFieldChange(SlotField.End, it) },
                    Modifier.weight(1f), keyboardOptions = numeric,
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                GgTextField(
                    stringResource(R.string.tou_sheet_soc), sheet.input.stopSoc, { actions.onSlotFieldChange(SlotField.Soc, it) },
                    Modifier.weight(1f), keyboardOptions = numeric,
                )
                GgTextField(
                    stringResource(R.string.tou_sheet_power), sheet.input.maxPower, { actions.onSlotFieldChange(SlotField.Power, it) },
                    Modifier.weight(1f), keyboardOptions = numeric,
                )
            }
        }
        if (kind == SlotKind.Charge) {
            Text(stringResource(R.string.tou_sheet_source), style = GgTheme.typography.caption.copy(fontWeight = FontWeight.Normal), color = colors.inkMuted)
        }
        sheet.error?.let { ErrorBox(slotErrorText(it)) }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            SecondaryButton(stringResource(R.string.action_cancel), onClick = actions.onSlotCancel, size = ButtonSize.Medium, modifier = Modifier.weight(1f))
            PrimaryButton(
                stringResource(R.string.action_save), onClick = actions.onSlotSave, size = ButtonSize.Medium,
                enabled = sheet.error == null, modifier = Modifier.weight(2f),
            )
        }
        if (sheet.editingIndex != null) DangerTextButton(stringResource(R.string.tou_sheet_delete), onClick = actions.onSlotDelete)
    }
}

/** Thông báo lỗi của popup, đúng chữ của Thay đổi 0.6 §5 (số khung tối đa theo ADR-002 mục 1). */
@Composable
private fun slotErrorText(error: SlotError): String = when (error) {
    is SlotError.LimitReached -> pluralStringResource(
        if (error.kind == SlotKind.Charge) R.plurals.tou_err_limit_charge else R.plurals.tou_err_limit_discharge,
        MAX_SLOTS_PER_TYPE, MAX_SLOTS_PER_TYPE,
    )
    SlotError.TimeFormat -> stringResource(R.string.tou_err_time_format)
    SlotError.SameStartEnd -> stringResource(R.string.tou_err_same_time)
    SlotError.SocRange -> stringResource(R.string.tou_err_soc)
    SlotError.PowerRange -> stringResource(R.string.tou_err_power)
    is SlotError.Overlap -> stringResource(
        R.string.tou_err_overlap, slotName(error.kind, error.number), GgFormat.timeRange(error.start, error.end),
    )
}

// ---------- Hộp xác nhận lưu lịch (BO_SUNG_v3.md §1) ----------

/**
 * Các dòng "cũ → mới" từ bản đã lưu sang [draft]: mỗi khung thêm / sửa / xoá, đổi tên, đổi ngày; thay đổi của chiến lược
 * khác (thứ bị chuyển đi) ghi thêm tên chiến lược ở đầu ("Cuối tuần · Ngày áp dụng"); xoá chiến lược là một câu.
 */
@Composable
private fun touChangeLines(saved: List<TouStrategy>, draft: List<TouStrategy>, currentName: String?): List<ChangeLine> {
    val labels = weekdayLabels()
    val none = stringResource(R.string.tou_change_none)
    val deleted = stringResource(R.string.tou_change_deleted)
    val percent = stringResource(R.string.unit_percent)
    val watt = stringResource(R.string.unit_watt)
    val prefix = stringResource(R.string.tou_change_prefix)
    val valuePattern = stringResource(R.string.change_value)
    val slotPattern = stringResource(R.string.tou_slot_summary)
    val chargePattern = stringResource(R.string.tou_slot_charge)
    val dischargePattern = stringResource(R.string.tou_slot_discharge)
    val nameLabel = stringResource(R.string.tou_change_name)
    val daysLabel = stringResource(R.string.tou_change_days)
    val addedPattern = stringResource(R.string.tou_change_added_strategy)
    val removedPattern = stringResource(R.string.tou_change_removed_strategy)
    val removedNoDaysPattern = stringResource(R.string.tou_change_removed_strategy_no_days)

    fun days(set: Set<DayOfWeek>) = GgFormat.weekdays(set, labels).ifEmpty { none }
    fun slot(s: TouSlot) = String.format(
        slotPattern, GgFormat.timeRange(s.start, s.end),
        GgFormat.withUnit(s.stopSocPercent.toLong(), percent), GgFormat.withUnit(s.maxPowerW.toLong(), watt),
    )
    fun slotLabel(kind: SlotKind, number: Int) = String.format(if (kind == SlotKind.Charge) chargePattern else dischargePattern, number)
    fun label(change: TouChange, name: String) =
        if (change.strategyName == currentName) name else String.format(prefix, change.strategyName, name)
    fun value(old: String, new: String) = String.format(valuePattern, old, new)

    return diffSchedules(saved, draft).map { c ->
        when (c) {
            is TouChange.StrategyAdded -> ChangeLine("", String.format(addedPattern, c.strategyName))
            is TouChange.StrategyRemoved -> ChangeLine(
                "",
                if (c.days.isEmpty()) String.format(removedNoDaysPattern, c.strategyName)
                else String.format(removedPattern, c.strategyName, days(c.days)),
            )
            is TouChange.Renamed -> ChangeLine(label(c, nameLabel), value(c.oldName, c.strategyName))
            is TouChange.DaysChanged -> ChangeLine(label(c, daysLabel), value(days(c.old), days(c.new)))
            is TouChange.SlotAdded -> ChangeLine(label(c, slotLabel(c.kind, c.number)), value(none, slot(c.slot)))
            is TouChange.SlotRemoved -> ChangeLine(
                label(c, slotLabel(c.kind, c.number)), value(GgFormat.timeRange(c.slot.start, c.slot.end), deleted),
            )
            is TouChange.SlotChanged -> ChangeLine(label(c, slotLabel(c.kind, c.number)), value(slot(c.old), slot(c.new)))
        }
    }
}

/** "Bỏ thay đổi?" khi rời màn còn thay đổi chưa lưu (BO_SUNG_v3.md §4). */
@Composable
private fun DiscardDialog(actions: TouStrategyActions) {
    GgDialog(onDismiss = actions.onDiscardCancel) {
        Text(
            stringResource(R.string.tou_discard_title),
            style = GgTheme.typography.label,
            color = GgTheme.colors.ink,
            modifier = Modifier.semantics { heading() },
        )
        Text(stringResource(R.string.tou_discard_body), style = GgTheme.typography.body, color = GgTheme.colors.ink)
        Row(horizontalArrangement = Arrangement.spacedBy(GgSpacing.space3), verticalAlignment = Alignment.CenterVertically) {
            SecondaryButton(stringResource(R.string.tou_discard_stay), onClick = actions.onDiscardCancel, size = ButtonSize.Medium, modifier = Modifier.weight(1f))
            DangerTextButton(stringResource(R.string.tou_discard_confirm), onClick = actions.onDiscardConfirm, icon = null, modifier = Modifier.weight(1f))
        }
    }
}

/** Khung xương (§3.34). */
@Composable
private fun LoadingBlocks() {
    val sunken = GgTheme.colors.surfaceSunken
    Column(Modifier.padding(horizontal = GgSpacing.space5), verticalArrangement = Arrangement.spacedBy(GgSpacing.space3)) {
        Box(Modifier.fillMaxWidth().height(40.dp).clip(RoundedCornerShape(GgRadius.radiusSm)).background(sunken))
        Box(Modifier.fillMaxWidth().height(28.dp).clip(RoundedCornerShape(8.dp)).background(sunken))
        GgCard { Box(Modifier.fillMaxWidth().height(260.dp).clip(RoundedCornerShape(GgRadius.radiusMd)).background(sunken)) }
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

/** Trạng thái mẫu: chiến lược [strategyIndex] của dữ liệu mẫu Thay đổi 0.6 §3. */
fun previewTouStrategyState(strategyIndex: Int = 0, editable: Boolean = true, draft: ((List<TouStrategy>) -> List<TouStrategy>)? = null): TouStrategyUiState.Content {
    val sample = FakeConfigRepository.SAMPLE
    val saved = sample.strategies
    return TouStrategyUiState.Content(
        version = sample.version,
        saved = saved,
        draft = draft?.invoke(saved) ?: saved,
        strategyId = saved[strategyIndex].id,
        editable = editable,
        mode = sample.mode,
    )
}

@Preview(widthDp = 390, heightDp = 1020, showBackground = true)
@Composable
private fun TouStrategyPreview() {
    GgPowerTheme(darkTheme = false) { TouStrategyScreen(previewTouStrategyState(), onRetry = {}) }
}

@Preview(widthDp = 390, heightDp = 1020, showBackground = true)
@Composable
private fun TouStrategyWeekendPreview() {
    GgPowerTheme(darkTheme = false) { TouStrategyScreen(previewTouStrategyState(1), onRetry = {}) }
}
