package vn.ggpower.inverter.ui.warranty

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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import vn.ggpower.inverter.R
import vn.ggpower.inverter.data.warranty.FakeWarrantyRepository
import vn.ggpower.inverter.domain.format.GgFormat
import vn.ggpower.inverter.domain.warranty.DeviceWarranty
import vn.ggpower.inverter.domain.warranty.ProjectWarranty
import vn.ggpower.inverter.domain.warranty.WarrantyChannel
import vn.ggpower.inverter.domain.warranty.WarrantyEntryStatus
import vn.ggpower.inverter.domain.warranty.WarrantyHistoryEntry
import vn.ggpower.inverter.domain.warranty.WarrantyRemaining
import vn.ggpower.inverter.domain.warranty.WarrantyStatus
import vn.ggpower.inverter.domain.warranty.WarrantyTerm
import vn.ggpower.inverter.domain.warranty.WarrantyWork
import vn.ggpower.inverter.ui.components.ButtonSize
import vn.ggpower.inverter.ui.components.GgCard
import vn.ggpower.inverter.ui.components.ScreenHeader
import vn.ggpower.inverter.ui.components.SecondaryButton
import vn.ggpower.inverter.ui.components.SectionHeader
import vn.ggpower.inverter.ui.components.StatusChip
import vn.ggpower.inverter.ui.components.StatusTone
import vn.ggpower.inverter.ui.theme.GgPowerTheme
import vn.ggpower.inverter.ui.theme.GgRadius
import vn.ggpower.inverter.ui.theme.GgSpacing
import vn.ggpower.inverter.ui.theme.GgTheme
import java.time.LocalDate

@Composable
fun WarrantyRoute(
    onBack: () -> Unit,
    onRequestWarranty: () -> Unit,
    viewModel: WarrantyViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    WarrantyScreen(state, onBack, onRequestWarranty, viewModel::retry)
}

/**
 * Màn 9 · Bảo hành (Warranty.png, html/Warranty.dc.html, đặc tả §3.9). Quay lại → Dịch vụ (6); "Yêu cầu bảo hành"
 * mở màn 8 với dự án chọn sẵn (chốt 03/10/2026). Thẻ thiết bị và dòng lịch sử chỉ hiển thị, không chạm được.
 */
@Composable
fun WarrantyScreen(
    state: WarrantyUiState,
    onBack: () -> Unit,
    onRequestWarranty: () -> Unit,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier.fillMaxSize().background(GgTheme.colors.surface).statusBarsPadding()) {
        ScreenHeader(title = stringResource(R.string.screen_warranty), onBack = onBack)
        when (state) {
            WarrantyUiState.Loading -> LoadingBlocks()
            is WarrantyUiState.Error -> ErrorBlock(state.noConnection, onRetry)
            is WarrantyUiState.Content -> Content(state.warranty, state.today, onRequestWarranty)
        }
    }
}

@Composable
private fun Content(warranty: ProjectWarranty, today: LocalDate, onRequestWarranty: () -> Unit) {
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .navigationBarsPadding()
            .padding(start = GgSpacing.space5, end = GgSpacing.space5, bottom = GgSpacing.space4),
        verticalArrangement = Arrangement.spacedBy(GgSpacing.space3),
    ) {
        ProjectContext(warranty.projectName, warranty.devices.size)
        if (warranty.devices.isEmpty()) {
            Text(
                stringResource(R.string.warranty_empty),
                style = GgTheme.typography.body,
                color = GgTheme.colors.inkMuted,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth().padding(vertical = GgSpacing.space8),
            )
        }
        warranty.devices.forEach { DeviceCard(it, today) }
        if (warranty.history.isNotEmpty()) {
            SectionHeader(stringResource(R.string.warranty_history), Modifier.padding(top = GgSpacing.space1))
            HistoryCard(warranty.history)
        }
        SecondaryButton(
            text = stringResource(R.string.warranty_request),
            onClick = onRequestWarranty,
            icon = R.drawable.ic_ticket,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

/** "⌂ Dự án **Nhà của bạn** · 3 thiết bị": 13/20 `inkMuted`, tên dự án 600 `ink`. */
@Composable
private fun ProjectContext(projectName: String, deviceCount: Int) {
    val colors = GgTheme.colors
    val full = pluralStringResource(R.plurals.warranty_context, deviceCount, projectName, deviceCount)
    val text = remember(full, projectName, colors.ink) { boldName(full, projectName, colors.ink) }
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(GgSpacing.space2)) {
        Icon(painterResource(R.drawable.ic_site_home), contentDescription = null, tint = colors.inkMuted, modifier = Modifier.size(16.dp))
        Text(text, style = GgTheme.typography.bodySm, color = colors.inkMuted)
    }
}

private fun boldName(full: String, name: String, ink: Color): AnnotatedString = buildAnnotatedString {
    append(full)
    val start = full.indexOf(name)
    if (start >= 0 && name.isNotEmpty()) {
        addStyle(SpanStyle(fontWeight = FontWeight.SemiBold, color = ink), start, start + name.length)
    }
}

// ---------- Thẻ thiết bị ----------

@Composable
private fun DeviceCard(device: DeviceWarranty, today: LocalDate) {
    val colors = GgTheme.colors
    val status = device.status(today)
    GgCard(verticalSpacing = GgSpacing.space3) {
        Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(GgSpacing.space3)) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    device.name,
                    style = GgTheme.typography.body.copy(fontSize = 16.sp, lineHeight = 22.sp, fontWeight = FontWeight.SemiBold),
                    color = colors.ink,
                    modifier = Modifier.semantics { heading() },
                )
                Text(
                    stringResource(R.string.device_serial, device.serial),
                    style = GgTheme.typography.code.copy(fontSize = 12.sp, lineHeight = 16.sp),
                    color = colors.inkMuted,
                )
            }
            StatusChip(statusText(status), statusTone(status))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(GgSpacing.space3)) {
            DateField(stringResource(R.string.warranty_activated_on), device.activatedOn, Modifier.weight(1f))
            DateField(stringResource(R.string.warranty_expires_on), device.expiresOn, Modifier.weight(1f))
        }
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            UsedBar(device.usedFraction(today), device.usedPercent(today), barColor(status))
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(termText(device.term), style = small(), color = colors.inkMuted, modifier = Modifier.weight(1f))
                Text(remainingText(device.remaining(today)), style = small().copy(fontWeight = FontWeight.SemiBold), color = colors.ink)
            }
        }
    }
}

@Composable
private fun small(): TextStyle = GgTheme.typography.bodySm.copy(fontSize = 12.sp, lineHeight = 16.sp)

/** Nhãn 12/16 500 `inkMuted` + ngày 14/20 600 số đều. */
@Composable
private fun DateField(label: String, date: LocalDate, modifier: Modifier = Modifier) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(label, style = GgTheme.typography.caption, color = GgTheme.colors.inkMuted)
        Text(
            GgFormat.date(date),
            style = GgTheme.typography.body.copy(fontSize = 14.sp, lineHeight = 20.sp, fontWeight = FontWeight.SemiBold, fontFeatureSettings = "tnum"),
            color = GgTheme.colors.ink,
        )
    }
}

/** Thanh thời gian đã dùng: cao 8 dp, nền `surfaceSunken`, bo tròn. */
@Composable
private fun UsedBar(fraction: Float, percent: Int, color: Color) {
    val description = stringResource(R.string.cd_warranty_used, percent)
    Box(
        Modifier
            .fillMaxWidth()
            .height(8.dp)
            .clip(CircleShape)
            .background(GgTheme.colors.surfaceSunken)
            .semantics {
                contentDescription = description
                progressBarRangeInfo = ProgressBarRangeInfo(fraction, 0f..1f)
            },
    ) {
        Box(Modifier.fillMaxWidth(fraction).height(8.dp).clip(CircleShape).background(color))
    }
}

@Composable
private fun statusText(status: WarrantyStatus): String = stringResource(
    when (status) {
        WarrantyStatus.Active -> R.string.warranty_status_active
        WarrantyStatus.ExpiringSoon -> R.string.warranty_status_soon
        WarrantyStatus.Expired -> R.string.warranty_status_expired
    },
)

private fun statusTone(status: WarrantyStatus): StatusTone = when (status) {
    WarrantyStatus.Active -> StatusTone.Success
    WarrantyStatus.ExpiringSoon -> StatusTone.Warning
    WarrantyStatus.Expired -> StatusTone.Danger
}

/** Màu thanh (gg-inverter-design Warranty.dc.html): #149C4A / #8A6100 (`warning`) / #C62828. */
@Composable
private fun barColor(status: WarrantyStatus): Color = when (status) {
    WarrantyStatus.Active -> GgTheme.colors.ggGreen
    WarrantyStatus.ExpiringSoon -> GgTheme.colors.warning
    WarrantyStatus.Expired -> GgTheme.colors.danger
}

@Composable
private fun termText(term: WarrantyTerm): String = when {
    term.months == 0 -> pluralStringResource(R.plurals.warranty_term_years, term.years, term.years)
    term.years == 0 -> pluralStringResource(R.plurals.warranty_term_months, term.months, term.months)
    else -> stringResource(R.string.warranty_term_years_months, term.years, term.months)
}

@Composable
private fun remainingText(remaining: WarrantyRemaining): String = when (remaining) {
    is WarrantyRemaining.YearsMonths -> when {
        remaining.months == 0 -> pluralStringResource(R.plurals.warranty_left_years, remaining.years, remaining.years)
        remaining.years == 0 -> pluralStringResource(R.plurals.warranty_left_months, remaining.months, remaining.months)
        else -> stringResource(R.string.warranty_left_years_months, remaining.years, remaining.months)
    }
    is WarrantyRemaining.Days -> remaining.days.toInt().let { pluralStringResource(R.plurals.warranty_left_days, it, it) }
    WarrantyRemaining.EndsToday -> stringResource(R.string.warranty_left_today)
    WarrantyRemaining.Expired -> stringResource(R.string.warranty_left_expired)
}

// ---------- Lịch sử bảo hành ----------

/** Thẻ đệm 4/16; mỗi dòng đệm dọc 12, không kẻ phân cách. */
@Composable
private fun HistoryCard(entries: List<WarrantyHistoryEntry>) {
    GgCard(contentPadding = PaddingValues(horizontal = GgSpacing.space4, vertical = GgSpacing.space1)) {
        entries.forEach { entry ->
            HistoryRow(entry)
        }
    }
}

@Composable
private fun HistoryRow(entry: WarrantyHistoryEntry) {
    val colors = GgTheme.colors
    Row(
        Modifier.fillMaxWidth().padding(vertical = GgSpacing.space3),
        horizontalArrangement = Arrangement.spacedBy(GgSpacing.space3),
        verticalAlignment = Alignment.Top,
    ) {
        // Sửa chữa: ✓ nền xanh nhạt; cập nhật firmware: ↻ nền xám (theo prototype).
        val (icon, bg, tint) = when (entry.work) {
            is WarrantyWork.Repair -> Triple(R.drawable.ic_check, colors.primarySoft, colors.primary)
            is WarrantyWork.Firmware -> Triple(R.drawable.ic_refresh, colors.surfaceSunken, colors.inkMuted)
        }
        Box(Modifier.size(36.dp).clip(CircleShape).background(bg), contentAlignment = Alignment.Center) {
            Icon(painterResource(icon), contentDescription = null, tint = tint, modifier = Modifier.size(18.dp))
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                entry.title,
                style = GgTheme.typography.body.copy(fontSize = 14.sp, lineHeight = 20.sp, fontWeight = FontWeight.SemiBold),
                color = colors.ink,
            )
            Text(workLine(entry), style = small(), color = colors.inkMuted)
            Text(
                stringResource(R.string.warranty_history_when, GgFormat.date(entry.date), channelText(entry.channel)),
                style = small(),
                color = colors.inkMuted,
            )
        }
        StatusChip(
            text = when (entry.status) {
                WarrantyEntryStatus.Completed -> stringResource(R.string.warranty_entry_completed)
            },
            tone = StatusTone.Success,
            modifier = Modifier.align(Alignment.CenterVertically),
        )
    }
}

@Composable
private fun workLine(entry: WarrantyHistoryEntry): String = when (val work = entry.work) {
    is WarrantyWork.Repair -> stringResource(R.string.warranty_history_repair, entry.device, work.ticketCode)
    is WarrantyWork.Firmware -> stringResource(R.string.warranty_history_firmware, entry.device, work.fromVersion, work.toVersion)
}

@Composable
private fun channelText(channel: WarrantyChannel): String = stringResource(
    when (channel) {
        WarrantyChannel.OnSite -> R.string.warranty_channel_on_site
        WarrantyChannel.Remote -> R.string.warranty_channel_remote
    },
)

// ---------- Đang tải / lỗi ----------

@Composable
private fun LoadingBlocks() {
    val sunken = GgTheme.colors.surfaceSunken
    Column(Modifier.padding(horizontal = GgSpacing.space5), verticalArrangement = Arrangement.spacedBy(GgSpacing.space3)) {
        repeat(3) { GgCard { Box(Modifier.fillMaxWidth().height(130.dp).clip(RoundedCornerShape(GgRadius.radiusMd)).background(sunken)) } }
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

/** Trạng thái mẫu cho Preview và khoá debug: dữ liệu HTML, ngày cố định như lúc dựng ảnh để so với PNG. */
internal fun previewWarrantyState(
    today: LocalDate = FakeWarrantyRepository.DESIGN_TODAY,
    warranty: ProjectWarranty = FakeWarrantyRepository.SAMPLE,
): WarrantyUiState = WarrantyUiState.Content(warranty, today)

@Preview(widthDp = 390, heightDp = 844, showBackground = true, backgroundColor = 0xFFF5F7F3)
@Composable
private fun WarrantyPreview() {
    GgPowerTheme(darkTheme = false) { WarrantyScreen(previewWarrantyState(), {}, {}, {}) }
}

@Preview(widthDp = 390, heightDp = 844, showBackground = true, backgroundColor = 0xFFF5F7F3)
@Composable
private fun WarrantyExpiredPreview() {
    GgPowerTheme(darkTheme = false) { WarrantyScreen(previewWarrantyState(today = LocalDate.of(2026, 11, 10)), {}, {}, {}) }
}

@Preview(widthDp = 390, heightDp = 844, showBackground = true, backgroundColor = 0xFFF5F7F3)
@Composable
private fun WarrantyLoadingPreview() {
    GgPowerTheme(darkTheme = false) { WarrantyScreen(WarrantyUiState.Loading, {}, {}, {}) }
}
