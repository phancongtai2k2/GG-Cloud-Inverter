package vn.ggpower.inverter.ui.alerts

import androidx.annotation.DrawableRes
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import vn.ggpower.inverter.R
import vn.ggpower.inverter.data.alert.FakeAlertRepository
import vn.ggpower.inverter.domain.alert.AlertFilter
import vn.ggpower.inverter.domain.alert.AlertSeverity
import vn.ggpower.inverter.domain.alert.AlertSummary
import vn.ggpower.inverter.domain.alert.counts
import vn.ggpower.inverter.domain.alert.filterBy
import vn.ggpower.inverter.domain.format.GgFormat
import vn.ggpower.inverter.ui.components.ButtonSize
import vn.ggpower.inverter.ui.components.GgCard
import vn.ggpower.inverter.ui.components.GgFilterChip
import vn.ggpower.inverter.ui.components.ScreenHeader
import vn.ggpower.inverter.ui.components.SecondaryButton
import vn.ggpower.inverter.ui.theme.GgPowerTheme
import vn.ggpower.inverter.ui.theme.GgRadius
import vn.ggpower.inverter.ui.theme.GgSpacing
import vn.ggpower.inverter.ui.theme.GgTheme

@Composable
fun AlertsRoute(
    onBack: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenAlert: (String) -> Unit,
    viewModel: AlertsViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    AlertsScreen(state, onBack, onOpenSettings, viewModel::onFilterChange, onOpenAlert, viewModel::retry)
}

/**
 * Màn 10 · Cảnh báo (Alerts.png, Alerts__filter-open.png; đặc tả §3.10). Lọc Tất cả / Chưa xử lý / Đã xử lý; chạm một
 * cảnh báo mở 10a (Thay đổi 0.6 §6); nút "Cài đặt thông báo" mở màn 28 (chưa có thiết kế).
 */
@Composable
fun AlertsScreen(
    state: AlertsUiState,
    onBack: () -> Unit,
    onOpenSettings: () -> Unit,
    onFilterChange: (AlertFilter) -> Unit,
    onOpenAlert: (String) -> Unit,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = GgTheme.colors
    Column(modifier.fillMaxSize().background(colors.surface).statusBarsPadding()) {
        ScreenHeader(title = stringResource(R.string.alerts_title), onBack = onBack) {
            IconButton(onClick = onOpenSettings, modifier = Modifier.size(48.dp)) {
                Icon(
                    painterResource(R.drawable.ic_sliders),
                    contentDescription = stringResource(R.string.cd_alert_settings),
                    tint = colors.ink,
                    modifier = Modifier.size(24.dp),
                )
            }
        }
        when (state) {
            AlertsUiState.Loading -> LoadingBlocks()
            is AlertsUiState.Error -> ErrorBlock(state.noConnection, onRetry)
            is AlertsUiState.Content -> {
                val counts = state.alerts.counts()
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = GgSpacing.space5),
                    horizontalArrangement = Arrangement.spacedBy(GgSpacing.space2),
                ) {
                    AlertFilter.entries.forEach { f ->
                        GgFilterChip(
                            text = stringResource(filterLabel(f), counts.getValue(f)),
                            selected = state.filter == f,
                            onClick = { onFilterChange(f) },
                        )
                    }
                }
                val items = state.alerts.filterBy(state.filter)
                if (items.isEmpty()) {
                    Text(
                        stringResource(R.string.alerts_empty),
                        style = GgTheme.typography.body,
                        color = colors.inkMuted,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth().padding(horizontal = GgSpacing.space8, vertical = GgSpacing.space8),
                    )
                } else {
                    Column(
                        Modifier
                            .fillMaxSize()
                            .verticalScroll(rememberScrollState())
                            .navigationBarsPadding()
                            .padding(start = GgSpacing.space5, end = GgSpacing.space5, top = GgSpacing.space1, bottom = GgSpacing.space4),
                        verticalArrangement = Arrangement.spacedBy(GgSpacing.space2),
                    ) {
                        items.forEach { AlertCard(it) { onOpenAlert(it.id) } }
                    }
                }
            }
        }
    }
}

private fun filterLabel(f: AlertFilter): Int = when (f) {
    AlertFilter.All -> R.string.alerts_filter_all
    AlertFilter.Open -> R.string.alerts_filter_open
    AlertFilter.Resolved -> R.string.alerts_filter_resolved
}

/** Màu, chữ và icon theo mức độ (Alerts.dc.html `K`): Lỗi `danger`, Cảnh báo `warning`, Thông tin `info`. */
data class SeverityStyle(val fg: Color, val bg: Color, val word: String, @param:DrawableRes val icon: Int)

@Composable
fun severityStyle(severity: AlertSeverity): SeverityStyle {
    val c = GgTheme.colors
    return when (severity) {
        AlertSeverity.Error -> SeverityStyle(c.danger, c.dangerSoft, stringResource(R.string.alert_severity_error), R.drawable.ic_alert_error)
        AlertSeverity.Warning -> SeverityStyle(c.warning, c.warningSoft, stringResource(R.string.alert_severity_warning), R.drawable.ic_alert_warning)
        AlertSeverity.Info -> SeverityStyle(c.info, c.infoSoft, stringResource(R.string.alert_severity_info), R.drawable.ic_alert_info)
    }
}

/**
 * Chip trạng thái xử lý (đệm 2 8, bo 6, chữ 12/16 600): "Chưa xử lý" chữ `ink` nền `surfaceSunken` viền `lineStrong`;
 * "Đã xử lý" chữ `primary` nền `primarySoft`.
 */
@Composable
fun ResolvedChip(resolved: Boolean) {
    val c = GgTheme.colors
    val shape = RoundedCornerShape(GgRadius.radiusSm)
    Text(
        stringResource(if (resolved) R.string.alert_resolved else R.string.alert_open),
        style = GgTheme.typography.label.copy(fontSize = 12.sp, lineHeight = 16.sp),
        color = if (resolved) c.primary else c.ink,
        maxLines = 1,
        modifier = Modifier
            .clip(shape)
            .background(if (resolved) c.primarySoft else c.surfaceSunken)
            .border(1.dp, if (resolved) c.primarySoft else c.lineStrong, shape)
            .padding(horizontal = GgSpacing.space2, vertical = 2.dp),
    )
}

/**
 * Thẻ cảnh báo: đệm 16, cách 12; icon tròn 40 dp; mức độ 12/16 600 màu theo mức; tiêu đề 15/22 600; mô tả 13/20
 * `inkMuted`; dòng cuối thời điểm `HH:mm · dd/MM/yyyy` 12/16 500 + chip trạng thái.
 */
@Composable
private fun AlertCard(alert: AlertSummary, onClick: () -> Unit) {
    val colors = GgTheme.colors
    val s = severityStyle(alert.severity)
    GgCard(onClick = onClick, onClickLabel = stringResource(R.string.cd_alert_open, alert.title)) {
        Row(horizontalArrangement = Arrangement.spacedBy(GgSpacing.space3)) {
            Box(Modifier.size(40.dp).clip(CircleShape).background(s.bg), contentAlignment = Alignment.Center) {
                Icon(painterResource(s.icon), contentDescription = null, tint = s.fg, modifier = Modifier.size(22.dp))
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(GgSpacing.space1)) {
                Text(s.word, style = GgTheme.typography.label.copy(fontSize = 12.sp, lineHeight = 16.sp), color = s.fg)
                Text(alert.title, style = GgTheme.typography.body.copy(fontSize = 15.sp, lineHeight = 22.sp, fontWeight = FontWeight.SemiBold), color = colors.ink)
                Text(alert.description, style = GgTheme.typography.bodySm, color = colors.inkMuted)
                Row(Modifier.fillMaxWidth().padding(top = GgSpacing.space1), verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        GgFormat.dateTimeFull(alert.startedAt),
                        style = GgTheme.typography.caption.copy(fontFeatureSettings = "tnum"),
                        color = colors.inkMuted,
                        modifier = Modifier.weight(1f),
                    )
                    ResolvedChip(alert.resolved)
                }
            }
        }
    }
}

@Composable
private fun LoadingBlocks() {
    val sunken = GgTheme.colors.surfaceSunken
    Column(Modifier.padding(horizontal = GgSpacing.space5), verticalArrangement = Arrangement.spacedBy(GgSpacing.space2)) {
        Box(Modifier.fillMaxWidth(0.8f).height(36.dp).clip(CircleShape).background(sunken))
        repeat(3) { GgCard { Box(Modifier.fillMaxWidth().height(90.dp).clip(RoundedCornerShape(GgRadius.radiusMd)).background(sunken)) } }
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

@Preview(widthDp = 390, heightDp = 844, showBackground = true)
@Composable
private fun AlertsPreview() {
    GgPowerTheme(darkTheme = false) {
        AlertsScreen(AlertsUiState.Content(FakeAlertRepository.SAMPLE, AlertFilter.All), {}, {}, {}, {}, {})
    }
}

@Preview(widthDp = 390, heightDp = 844, showBackground = true)
@Composable
private fun AlertsOpenPreview() {
    GgPowerTheme(darkTheme = false) {
        AlertsScreen(AlertsUiState.Content(FakeAlertRepository.SAMPLE, AlertFilter.Open), {}, {}, {}, {}, {})
    }
}
