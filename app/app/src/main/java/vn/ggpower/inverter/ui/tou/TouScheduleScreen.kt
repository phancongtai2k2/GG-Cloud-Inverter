package vn.ggpower.inverter.ui.tou

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
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
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import vn.ggpower.inverter.R
import vn.ggpower.inverter.data.config.FakeConfigRepository
import vn.ggpower.inverter.domain.format.GgFormat
import vn.ggpower.inverter.domain.tou.TouStrategy
import vn.ggpower.inverter.domain.tou.summary
import vn.ggpower.inverter.ui.components.BulletList
import vn.ggpower.inverter.ui.components.ButtonSize
import vn.ggpower.inverter.ui.components.DashedButton
import vn.ggpower.inverter.ui.components.GgCard
import vn.ggpower.inverter.ui.components.ScreenHeader
import vn.ggpower.inverter.ui.components.SecondaryButton
import vn.ggpower.inverter.ui.components.StatusChip
import vn.ggpower.inverter.ui.components.StatusTone
import vn.ggpower.inverter.ui.components.Timeline24h
import vn.ggpower.inverter.ui.components.TimelineSize
import vn.ggpower.inverter.ui.components.TimelineSlot
import vn.ggpower.inverter.ui.format.weekdayLabels
import vn.ggpower.inverter.ui.theme.GgPowerTheme
import vn.ggpower.inverter.ui.theme.GgRadius
import vn.ggpower.inverter.ui.theme.GgSpacing
import vn.ggpower.inverter.ui.theme.GgTheme

@Composable
fun TouScheduleRoute(
    onBack: () -> Unit,
    onOpenStrategy: (String) -> Unit,
    onAddStrategy: () -> Unit,
    viewModel: TouScheduleViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    // Quay về từ 16b: đọc lại để thấy danh sách vừa lưu.
    LifecycleResumeEffect(Unit) {
        viewModel.refresh()
        onPauseOrDispose { }
    }
    TouScheduleScreen(state, onBack, viewModel::retry, onOpenStrategy, onAddStrategy)
}

/**
 * Màn 16a · Lịch theo khung giờ – Danh sách chiến lược (TouSchedule.png; Thay đổi 0.6 §3; ADR-002 mục 9).
 * Chỉ xem danh sách đã lưu; mọi chỉnh sửa (kể cả chiến lược mới) diễn ra trong bản nháp của 16b.
 */
@Composable
fun TouScheduleScreen(
    state: TouScheduleUiState,
    onBack: () -> Unit,
    onRetry: () -> Unit,
    onOpenStrategy: (String) -> Unit,
    onAddStrategy: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier
            .fillMaxSize()
            .background(GgTheme.colors.surface)
            .statusBarsPadding(),
    ) {
        ScreenHeader(
            title = stringResource(R.string.tou_schedule_title),
            onBack = onBack,
            backDescription = stringResource(R.string.cd_back_to_config),
        )
        when (state) {
            TouScheduleUiState.Loading -> LoadingBlocks()
            is TouScheduleUiState.Error -> ErrorBlock(state.noConnection, onRetry)
            is TouScheduleUiState.Content -> Content(state, onOpenStrategy, onAddStrategy)
        }
    }
}

@Composable
private fun Content(state: TouScheduleUiState.Content, onOpenStrategy: (String) -> Unit, onAddStrategy: () -> Unit) {
    val colors = GgTheme.colors
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .navigationBarsPadding()
            .padding(start = GgSpacing.space5, end = GgSpacing.space5, bottom = GgSpacing.space6),
        verticalArrangement = Arrangement.spacedBy(GgSpacing.space3),
    ) {
        Text(stringResource(R.string.tou_schedule_intro), style = GgTheme.typography.bodySm, color = colors.inkMuted)
        state.strategies.forEach { strategy ->
            StrategyCard(strategy, today = strategy.id == state.todayStrategyId, onClick = { onOpenStrategy(strategy.id) })
        }
        if (state.canAdd) {
            DashedButton(stringResource(R.string.tou_add_strategy), onClick = onAddStrategy, enabled = state.addEnabled)
        }
        BulletList(listOf(stringResource(R.string.tou_schedule_note_limit), stringResource(R.string.tou_schedule_note_daily)))
    }
}

/**
 * Thẻ chiến lược (TouSchedule.dc.html): đệm 14/16, cách 10; tên 15/22 600; các thứ 12/16 `inkMuted`; chip "Hôm nay";
 * mũi tên 18 dp `lineStrong`; thanh 24h cỡ Mini; dòng tóm tắt 12/18 `inkMuted`
 * ("4 giai đoạn: 2 lần sạc, 2 lần xả · 09:00–12:00, …", sắp theo giờ bắt đầu).
 */
@Composable
private fun StrategyCard(strategy: TouStrategy, today: Boolean, onClick: () -> Unit) {
    val colors = GgTheme.colors
    val days = GgFormat.weekdays(strategy.weekdays, weekdayLabels()).ifEmpty { stringResource(R.string.tou_no_days) }
    val cd = stringResource(R.string.cd_open_strategy, strategy.name)
    GgCard(
        onClick = onClick,
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 14.dp),
        verticalSpacing = 10.dp,
        modifier = Modifier.semantics { contentDescription = cd },
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    strategy.name,
                    style = GgTheme.typography.body.copy(fontSize = 15.sp, lineHeight = 22.sp, fontWeight = FontWeight.SemiBold),
                    color = colors.ink,
                )
                Text(days, style = GgTheme.typography.caption.copy(fontWeight = FontWeight.Normal), color = colors.inkMuted)
            }
            if (today) StatusChip(stringResource(R.string.config_tou_today), StatusTone.Success, showDot = false, small = true)
            Icon(painterResource(R.drawable.ic_chevron_right), contentDescription = null, tint = colors.lineStrong, modifier = Modifier.size(18.dp))
        }
        Timeline24h(
            slots = strategy.slots.map { TimelineSlot(it.start, it.end, it.kind) },
            size = TimelineSize.Mini,
        )
        Text(
            summaryLine(strategy),
            style = GgTheme.typography.caption.copy(fontWeight = FontWeight.Normal, lineHeight = 18.sp),
            color = colors.inkMuted,
        )
    }
}

@Composable
private fun summaryLine(strategy: TouStrategy): String {
    val summary = strategy.summary()
    if (summary.isEmpty) return stringResource(R.string.tou_card_empty)
    val parts = buildList {
        if (summary.chargeCount > 0) add(pluralStringResource(R.plurals.config_tou_charge_count, summary.chargeCount, summary.chargeCount))
        if (summary.dischargeCount > 0) add(pluralStringResource(R.plurals.config_tou_discharge_count, summary.dischargeCount, summary.dischargeCount))
    }
    val head = pluralStringResource(R.plurals.config_tou_summary, summary.periods.size, summary.periods.size, parts.joinToString(", "))
    val ranges = summary.periods.joinToString(", ") { GgFormat.timeRangeCompact(it.start, it.end) }
    return stringResource(R.string.tou_card_summary, head, ranges)
}

/** Khung xương (§3.34). */
@Composable
private fun LoadingBlocks() {
    val sunken = GgTheme.colors.surfaceSunken
    Column(Modifier.padding(horizontal = GgSpacing.space5), verticalArrangement = Arrangement.spacedBy(GgSpacing.space3)) {
        Box(Modifier.fillMaxWidth().height(60.dp).clip(RoundedCornerShape(GgRadius.radiusSm)).background(sunken))
        repeat(2) { GgCard { Box(Modifier.fillMaxWidth().height(100.dp).clip(RoundedCornerShape(GgRadius.radiusMd)).background(sunken)) } }
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

/** Dữ liệu mẫu Thay đổi 0.6 §3 (hôm nay T5 thuộc "Ngày thường"). */
fun previewTouScheduleState(addEnabled: Boolean = true): TouScheduleUiState.Content {
    val sample = FakeConfigRepository.SAMPLE
    return TouScheduleUiState.Content(sample.strategies, sample.todayStrategyId, canAdd = true, addEnabled = addEnabled)
}

@Preview(widthDp = 390, heightDp = 844, showBackground = true)
@Composable
private fun TouSchedulePreview() {
    GgPowerTheme(darkTheme = false) {
        TouScheduleScreen(previewTouScheduleState(), onBack = {}, onRetry = {}, onOpenStrategy = {}, onAddStrategy = {})
    }
}

@Preview(widthDp = 390, heightDp = 844, showBackground = true)
@Composable
private fun TouScheduleLoadingPreview() {
    GgPowerTheme(darkTheme = false) {
        TouScheduleScreen(TouScheduleUiState.Loading, onBack = {}, onRetry = {}, onOpenStrategy = {}, onAddStrategy = {})
    }
}
