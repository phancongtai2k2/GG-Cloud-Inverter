package vn.ggpower.inverter.ui.alerts

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import java.time.LocalDateTime
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToLong
import kotlinx.coroutines.delay
import vn.ggpower.chart.chartPinchZoom
import vn.ggpower.chart.rememberChartZoomState
import vn.ggpower.chart.windowLength
import vn.ggpower.inverter.R
import vn.ggpower.inverter.data.alert.FakeAlertRepository
import vn.ggpower.inverter.domain.alert.AlertChart
import vn.ggpower.inverter.domain.alert.AlertDetail
import vn.ggpower.inverter.domain.alert.AlertEndKind
import vn.ggpower.inverter.domain.alert.durationMinutes
import vn.ggpower.inverter.domain.format.GgFormat
import vn.ggpower.inverter.domain.settings.TemperatureUnit
import vn.ggpower.inverter.ui.components.ButtonSize
import vn.ggpower.inverter.ui.components.GgCard
import vn.ggpower.inverter.ui.components.GgSnackbar
import vn.ggpower.inverter.ui.components.PrimaryButton
import vn.ggpower.inverter.ui.components.ScreenHeader
import vn.ggpower.inverter.ui.components.SecondaryButton
import vn.ggpower.inverter.ui.components.SectionHeader
import vn.ggpower.inverter.ui.components.StatusTone
import vn.ggpower.inverter.ui.components.ZoomResetPill
import vn.ggpower.inverter.ui.format.fieldUnitLabel
import vn.ggpower.inverter.ui.theme.GgPowerTheme
import vn.ggpower.inverter.ui.theme.GgRadius
import vn.ggpower.inverter.ui.theme.GgSpacing
import vn.ggpower.inverter.ui.theme.GgTheme

@Composable
fun AlertDetailRoute(onBack: () -> Unit, onCreateSupport: () -> Unit, viewModel: AlertDetailViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val temperatureUnit by viewModel.temperatureUnit.collectAsStateWithLifecycle()
    AlertDetailScreen(
        state, onBack, onCreateSupport, viewModel::onToggleResolved, viewModel::retry,
        onSaveFailureShown = viewModel::onSaveFailureShown,
        temperatureUnit = temperatureUnit,
    )
}

/**
 * Màn 10a · Chi tiết cảnh báo (AlertDetail.png và các biến thể temp / firmware / pv; Thay đổi 0.6 §6; đặc tả §3.23).
 * "Tạo yêu cầu hỗ trợ kỹ thuật" mở màn 8 với `alertId`; "Đánh dấu đã xử lý" bật / tắt mọi lúc (quyết 02/10/2026).
 */
@Composable
fun AlertDetailScreen(
    state: AlertDetailUiState,
    onBack: () -> Unit,
    onCreateSupport: () -> Unit,
    onToggleResolved: () -> Unit,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
    onSaveFailureShown: (Long) -> Unit = {},
    temperatureUnit: TemperatureUnit = TemperatureUnit.Celsius,
) {
    val colors = GgTheme.colors
    Box(modifier.fillMaxSize().background(colors.surface)) {
        Column(Modifier.fillMaxSize().statusBarsPadding()) {
            ScreenHeader(
                title = stringResource(R.string.screen_alert_detail),
                onBack = onBack,
                backDescription = stringResource(R.string.cd_back_to_alerts),
            )
            when (state) {
                AlertDetailUiState.Loading -> LoadingBlocks()
                is AlertDetailUiState.Error -> ErrorBlock(state.noConnection, onRetry)
                is AlertDetailUiState.Content -> Content(state, temperatureUnit, onCreateSupport, onToggleResolved)
            }
        }
        val failure = (state as? AlertDetailUiState.Content)?.saveFailed
        if (failure != null) {
            val dismiss by rememberUpdatedState(onSaveFailureShown)
            LaunchedEffect(failure.id) {
                delay(4_000)
                dismiss(failure.id)
            }
            GgSnackbar(
                stringResource(if (failure.noConnection) R.string.error_no_internet else R.string.error_server),
                StatusTone.Danger,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .navigationBarsPadding()
                    .padding(GgSpacing.space5),
            )
        }
    }
}

@Composable
private fun Content(
    state: AlertDetailUiState.Content,
    temperatureUnit: TemperatureUnit,
    onCreateSupport: () -> Unit,
    onToggleResolved: () -> Unit,
) {
    val colors = GgTheme.colors
    val d = state.detail
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .navigationBarsPadding()
            .padding(start = GgSpacing.space5, end = GgSpacing.space5, bottom = GgSpacing.space6),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        HeadCard(d, state.now)
        SectionHeader(stringResource(R.string.alert_code_section))
        GgCard(contentPadding = PaddingValues(horizontal = 16.dp, vertical = 14.dp), verticalSpacing = GgSpacing.space1) {
            Text(d.code, style = GgTheme.typography.code.copy(lineHeight = 18.sp), color = colors.ink)
            Text(d.codeName, style = GgTheme.typography.bodySm, color = colors.inkMuted)
        }
        d.chart?.let {
            SectionHeader(stringResource(R.string.alert_chart_section))
            ChartCard(it, temperatureUnit)
        }
        SectionHeader(stringResource(R.string.alert_causes_section))
        GgCard(contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp), verticalSpacing = 6.dp) {
            d.causes.forEach { cause ->
                Row {
                    Box(Modifier.size(18.dp, 20.dp), contentAlignment = Alignment.Center) {
                        Box(Modifier.size(5.dp).clip(CircleShape).background(colors.ink))
                    }
                    Text(cause, style = GgTheme.typography.bodySm, color = colors.ink)
                }
            }
        }
        SectionHeader(stringResource(R.string.alert_steps_section))
        GgCard(contentPadding = PaddingValues(horizontal = 16.dp, vertical = 6.dp)) {
            d.steps.forEachIndexed { i, step ->
                Row(Modifier.padding(vertical = 10.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Box(Modifier.size(24.dp).clip(CircleShape).background(colors.primarySoft), contentAlignment = Alignment.Center) {
                        Text("${i + 1}", style = GgTheme.typography.label.copy(fontSize = 12.sp, fontWeight = FontWeight.Bold), color = colors.primary)
                    }
                    Text(step, style = GgTheme.typography.bodySm, color = colors.ink, modifier = Modifier.weight(1f))
                }
            }
        }
        Column(Modifier.padding(top = GgSpacing.space1), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            PrimaryButton(
                stringResource(R.string.alert_create_support), onClick = onCreateSupport,
                modifier = Modifier.fillMaxWidth(), icon = R.drawable.ic_headset,
            )
            ResolvedButton(d.resolved, enabled = !state.saving, onClick = onToggleResolved)
            Text(
                stringResource(R.string.alert_support_note),
                style = GgTheme.typography.caption.copy(fontWeight = FontWeight.Normal, lineHeight = 18.sp),
                color = colors.inkMuted,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

/**
 * Thẻ đầu: icon tròn 48 dp; chip mức độ + chip trạng thái; tiêu đề 18/26 700; mô tả 13/20; bảng Thiết bị · Dự án ·
 * Bắt đầu · Kết thúc · Thời lượng (dòng đệm 9, viền trên). "Kết thúc" của cảnh báo đang diễn ra chưa đánh dấu: chữ đỏ
 * "Đang diễn ra"; đã đánh dấu: "Đã đánh dấu xử lý" (prototype; quyết 03/10/2026).
 */
@Composable
private fun HeadCard(d: AlertDetail, now: LocalDateTime) {
    val colors = GgTheme.colors
    val s = severityStyle(d.severity)
    val end = when {
        d.endedAt != null -> when (d.endKind) {
            AlertEndKind.AutoUpdated -> stringResource(R.string.alert_end_auto_updated, GgFormat.dateTimeFull(d.endedAt))
            AlertEndKind.AutoRecovered -> stringResource(R.string.alert_end_auto_recovered, GgFormat.dateTimeFull(d.endedAt))
            null -> GgFormat.dateTimeFull(d.endedAt)
        }
        d.resolved -> stringResource(R.string.alert_end_marked)
        else -> stringResource(R.string.alert_end_ongoing)
    }
    val duration = d.durationMinutes(now)?.let { GgFormat.duration(it.toInt(), stringResource(R.string.unit_hour), stringResource(R.string.unit_minute)) }
        ?: stringResource(R.string.value_missing)
    val facts = listOf(
        stringResource(R.string.alert_fact_device) to d.device,
        stringResource(R.string.alert_fact_project) to d.projectName,
        stringResource(R.string.alert_fact_start) to GgFormat.dateTimeFull(d.startedAt),
        stringResource(R.string.alert_fact_end) to end,
        stringResource(R.string.alert_fact_duration) to duration,
    )
    GgCard(verticalSpacing = GgSpacing.space3) {
        Row(horizontalArrangement = Arrangement.spacedBy(GgSpacing.space3)) {
            Box(Modifier.size(48.dp).clip(CircleShape).background(s.bg), contentAlignment = Alignment.Center) {
                Icon(painterResource(s.icon), contentDescription = null, tint = s.fg, modifier = Modifier.size(26.dp))
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        s.word,
                        style = GgTheme.typography.label.copy(fontSize = 12.sp, lineHeight = 16.sp),
                        color = s.fg,
                        modifier = Modifier.clip(RoundedCornerShape(GgRadius.radiusSm)).background(s.bg).padding(horizontal = GgSpacing.space2, vertical = 2.dp),
                    )
                    ResolvedChip(d.resolved)
                }
                Text(d.title, style = GgTheme.typography.body.copy(fontSize = 18.sp, lineHeight = 26.sp, fontWeight = FontWeight.Bold), color = colors.ink)
                Text(d.description, style = GgTheme.typography.bodySm, color = colors.inkMuted)
            }
        }
        Column {
            facts.forEachIndexed { i, (k, v) ->
                Row(Modifier.fillMaxWidth().padding(vertical = 9.dp), horizontalArrangement = Arrangement.spacedBy(GgSpacing.space3)) {
                    Text(k, style = GgTheme.typography.bodySm.copy(lineHeight = 18.sp), color = colors.inkMuted)
                    Text(
                        v,
                        style = GgTheme.typography.bodySm.copy(lineHeight = 18.sp, fontWeight = FontWeight.SemiBold, fontFeatureSettings = "tnum"),
                        color = if (i == 3 && d.ongoing && !d.resolved) colors.danger else colors.ink,
                        textAlign = TextAlign.End,
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }
    }
}

/**
 * Diễn biến 30 phút (AlertDetail.dc.html): nhãn 13/18 600; vùng vẽ cao 100 dp lùi trái 34 dp cho nhãn trục; lưới ở
 * đỉnh / giữa / đáy; vạch đứt tại thời điểm cảnh báo; đường 2 dp màu theo đại lượng; trục tung từ min(0, nhỏ nhất)
 * tới bội 10 kế trên của lớn nhất; nhãn giờ đầu · thời điểm cảnh báo (đậm) · cuối; ghi chú 12/16. Biểu đồ nhiệt độ
 * vẽ theo cài đặt °C/°F; ghi chú của backend hiện nguyên văn (quyết 04/10/2026).
 */
@Composable
private fun ChartCard(source: AlertChart, temperatureUnit: TemperatureUnit) {
    val colors = GgTheme.colors
    val chart = remember(source, temperatureUnit) { source.inTemperatureUnit(temperatureUnit) }
    val title = stringResource(R.string.alert_chart_label, chart.label, fieldUnitLabel(chart.unit, temperatureUnit))
    val measurer = rememberTextMeasurer()
    val color = when (chart.kind) {
        AlertChart.ChartKind.Grid -> colors.energyGrid
        AlertChart.ChartKind.Pv, AlertChart.ChartKind.Battery -> colors.energyPv
    }
    val hi = ceil(chart.values.max() / 10) * 10
    val lo = min(chart.values.min(), 0.0)
    val small = TextStyle(fontFamily = GgTheme.typography.caption.fontFamily, fontSize = 10.sp, lineHeight = 14.sp, color = colors.inkMuted, fontFeatureSettings = "tnum")
    val bold = small.copy(color = colors.ink, fontWeight = FontWeight.Bold)
    // Chụm hai ngón để phóng trục thời gian 1× / 2× / 4× (gg-inverter-design AlertDetail.dc.html, hàm ZM).
    val zoom = rememberChartZoomState(maxZoom = 4f)
    val win = zoom.window
    val last = chart.values.lastIndex
    fun timeAt(fraction: Float) = GgFormat.time(chart.start.plusSeconds((fraction * last * chart.stepSeconds).roundToLong()).toLocalTime())
    val markFraction = chart.markIndex.toFloat() / last
    val markLocal = win.local(markFraction)
    val markShown = win.contains(markFraction)
    // Nhãn mép bị ẩn khi mốc sự cố nằm sát mép (dưới 20 % cửa sổ), như prototype.
    val t0 = if (markShown && markLocal < 0.2f) "" else timeAt(win.start)
    val t1 = if (markShown && markLocal > 0.8f) "" else timeAt(win.end)
    val tm = GgFormat.time(chart.timeAt(chart.markIndex).toLocalTime())
    val totalMinutes = last * chart.stepSeconds / 60.0
    val windowLabel = "${windowLength(totalMinutes, win.zoom, GgFormat.defaultStyle.decimalSeparator)} ${stringResource(R.string.unit_minute)}"
    val grid = colors.cardBorder
    val base = colors.line
    val ink = colors.ink
    val cd = "$title. ${chart.note}"
    val density = LocalDensity.current
    val plotStart = with(density) { 34.dp.toPx() }
    var widthPx by remember { mutableFloatStateOf(0f) }
    GgCard(contentPadding = PaddingValues(start = 16.dp, top = 14.dp, end = 16.dp, bottom = 12.dp), verticalSpacing = GgSpacing.space2) {
        Row(Modifier.fillMaxWidth().heightIn(min = 26.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(
                title,
                style = GgTheme.typography.bodySm.copy(lineHeight = 18.sp, fontWeight = FontWeight.SemiBold),
                color = colors.ink,
                modifier = Modifier.weight(1f),
            )
            if (win.zoomed) ZoomResetPill(windowLabel, zoom::reset, Modifier.height(26.dp))
        }
        Canvas(
            Modifier
                .fillMaxWidth()
                .height(120.dp)
                .onSizeChanged { widthPx = it.width.toFloat() }
                .chartPinchZoom(zoom, plotStart) { widthPx }
                .semantics { contentDescription = cd },
        ) {
            val d = density.density
            val left = 34 * d
            val w = size.width - left
            val h = 100 * d
            fun x(i: Int) = left + w * win.local(i.toFloat() / last)
            fun y(v: Double) = (h - (v - lo) / (hi - lo) * h).toFloat()
            drawLine(grid, Offset(left, 0f), Offset(size.width, 0f), strokeWidth = d)
            drawLine(grid, Offset(left, h / 2), Offset(size.width, h / 2), strokeWidth = d)
            drawLine(base, Offset(left, h), Offset(size.width, h), strokeWidth = d)
            clipRect(left = left, top = 0f, right = size.width, bottom = h + d) {
                if (markShown) {
                    val mx = left + w * markLocal
                    drawLine(ink, Offset(mx, 0f), Offset(mx, h), strokeWidth = d, pathEffect = PathEffect.dashPathEffect(floatArrayOf(3 * d, 3 * d)))
                }
                val path = Path().apply {
                    chart.values.forEachIndexed { i, v -> if (i == 0) moveTo(x(i), y(v)) else lineTo(x(i), y(v)) }
                }
                drawPath(path, color, style = Stroke(width = 2 * d, join = StrokeJoin.Round))
            }
            fun label(text: String, style: TextStyle, xPos: Float, top: Float, align: Int) {
                if (text.isEmpty()) return
                val l = measurer.measure(text, style)
                val lx = when (align) { -1 -> xPos; 1 -> xPos - l.size.width; else -> xPos - l.size.width / 2f }
                drawText(l, topLeft = Offset(max(0f, min(lx, size.width - l.size.width)), top))
            }
            label(GgFormat.trimmed(hi, 0), small, left - 6 * d, -7 * d, 1)
            label(GgFormat.trimmed(lo, 0), small, left - 6 * d, 93 * d, 1)
            label(t0, small, left, 104 * d, -1)
            if (markShown) label(tm, bold, left + w * markLocal, 104 * d, 0)
            label(t1, small, size.width, 104 * d, 1)
        }
        Text(chart.note, style = GgTheme.typography.caption.copy(fontWeight = FontWeight.Normal), color = colors.inkMuted)
    }
}

/**
 * Nút "Đánh dấu đã xử lý" (cao 48, bo 10, dấu ✓): chưa xử lý viền `lineStrong` nền trắng chữ `ink`; đã xử lý nền +
 * viền `primarySoft`, chữ `primary` "Đã xử lý · chạm để bỏ đánh dấu".
 */
@Composable
private fun ResolvedButton(resolved: Boolean, enabled: Boolean, onClick: () -> Unit) {
    val colors = GgTheme.colors
    val shape = RoundedCornerShape(GgRadius.radiusMd)
    val fg = if (resolved) colors.primary else colors.ink
    val state = stringResource(if (resolved) R.string.alert_resolved else R.string.alert_open)
    Row(
        Modifier
            .fillMaxWidth()
            .height(48.dp)
            .clip(shape)
            .background(if (resolved) colors.primarySoft else colors.surfaceRaised)
            .border(1.dp, if (resolved) colors.primarySoft else colors.lineStrong, shape)
            .clickable(enabled = enabled, role = Role.Switch, onClick = onClick)
            .semantics { stateDescription = state },
        horizontalArrangement = Arrangement.spacedBy(GgSpacing.space2, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(painterResource(R.drawable.ic_check), contentDescription = null, tint = fg, modifier = Modifier.size(20.dp))
        Text(
            stringResource(if (resolved) R.string.alert_unmark else R.string.alert_mark),
            style = GgTheme.typography.label.copy(fontSize = 14.sp, lineHeight = 20.sp),
            color = fg,
        )
    }
}

@Composable
private fun LoadingBlocks() {
    val sunken = GgTheme.colors.surfaceSunken
    Column(Modifier.padding(horizontal = GgSpacing.space5), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        GgCard { Box(Modifier.fillMaxWidth().height(260.dp).clip(RoundedCornerShape(GgRadius.radiusMd)).background(sunken)) }
        GgCard { Box(Modifier.fillMaxWidth().height(60.dp).clip(RoundedCornerShape(GgRadius.radiusMd)).background(sunken)) }
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

/** Thời điểm hiện tại của prototype (09:41 · 24/09/2026) để thời lượng ra "2 giờ 29 phút". */
val PreviewNow: LocalDateTime = LocalDateTime.of(2026, 9, 24, 9, 41)

fun previewAlertDetail(id: String = "grid", resolved: Boolean? = null): AlertDetailUiState.Content {
    val d = FakeAlertRepository.detailSample(id)
    return AlertDetailUiState.Content(if (resolved != null) d.copy(resolved = resolved) else d, PreviewNow)
}

@Preview(widthDp = 390, heightDp = 1420, showBackground = true)
@Composable
private fun AlertDetailPreview() {
    GgPowerTheme(darkTheme = false) { AlertDetailScreen(previewAlertDetail(), {}, {}, {}, {}) }
}

@Preview(widthDp = 390, heightDp = 1000, showBackground = true)
@Composable
private fun AlertDetailFirmwarePreview() {
    GgPowerTheme(darkTheme = false) { AlertDetailScreen(previewAlertDetail("fw"), {}, {}, {}, {}) }
}
