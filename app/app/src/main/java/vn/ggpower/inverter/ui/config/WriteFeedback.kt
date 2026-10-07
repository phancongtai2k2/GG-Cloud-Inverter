package vn.ggpower.inverter.ui.config

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import vn.ggpower.inverter.R
import vn.ggpower.inverter.domain.config.WriteState
import vn.ggpower.inverter.ui.components.GgSnackbar
import vn.ggpower.inverter.ui.components.StatusTone
import vn.ggpower.inverter.ui.theme.GgRadius
import vn.ggpower.inverter.ui.theme.GgSpacing
import vn.ggpower.inverter.ui.theme.GgTheme

// Phản hồi của lệnh ghi cấu hình, dùng chung màn 16 và 16b (BO_SUNG_v3.md §2–3).

/** "Đang ghi…": vòng xoay 16 dp + chữ `body-sm` `ink-muted`, ngay dưới mục đang ghi. */
@Composable
fun WritingRow() {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(GgSpacing.space2)) {
        CircularProgressIndicator(Modifier.size(16.dp), color = GgTheme.colors.inkMuted, strokeWidth = 2.dp)
        Text(stringResource(R.string.write_in_progress), style = GgTheme.typography.bodySm, color = GgTheme.colors.inkMuted)
    }
}

/** Snackbar kết quả, tự ẩn theo thời gian của BO_SUNG_v3.md §2 (3 / 4 / 5 s); "Thử lại" không tự ẩn. */
@Composable
fun WriteNoticeSnackbar(notice: ConfigNotice, onAction: () -> Unit, onDismissed: (Long) -> Unit, modifier: Modifier) {
    val spec = noticeSpec(notice)
    val currentDismiss by rememberUpdatedState(onDismissed)
    if (spec.durationMs != null) {
        LaunchedEffect(notice.id) {
            delay(spec.durationMs)
            currentDismiss(notice.id)
        }
    }
    GgSnackbar(
        text = spec.text,
        tone = spec.tone,
        actionLabel = if (notice.kind == NoticeKind.Failed) stringResource(R.string.action_retry) else null,
        onAction = onAction,
        modifier = modifier,
    )
}

private class NoticeSpec(val text: String, val tone: StatusTone, val durationMs: Long?)

@Composable
private fun noticeSpec(notice: ConfigNotice): NoticeSpec = when (notice.kind) {
    NoticeKind.Applied -> NoticeSpec(stringResource(R.string.write_applied), StatusTone.Success, 3_000)
    NoticeKind.AppliedWithoutDischarge -> NoticeSpec(stringResource(R.string.write_applied_without_discharge), StatusTone.Warning, 8_000)
    NoticeKind.SavedNotWritten -> NoticeSpec(stringResource(R.string.write_saved_not_written), StatusTone.Info, 4_000)
    NoticeKind.Reverted -> NoticeSpec(stringResource(R.string.write_reverted), StatusTone.Warning, 5_000)
    NoticeKind.Failed -> NoticeSpec(stringResource(R.string.write_failed), StatusTone.Danger, null)
    NoticeKind.DeviceRejected -> NoticeSpec(stringResource(R.string.write_device_rejected), StatusTone.Danger, 5_000)
    NoticeKind.NotPermitted -> NoticeSpec(stringResource(R.string.write_not_permitted), StatusTone.Danger, 5_000)
    NoticeKind.CommandInProgress -> NoticeSpec(stringResource(R.string.write_command_in_progress), StatusTone.Warning, 5_000)
    NoticeKind.RateLimited -> {
        val seconds = notice.retryAfterSeconds ?: 5
        NoticeSpec(pluralStringResource(R.plurals.write_rate_limited, seconds, seconds), StatusTone.Warning, 5_000)
    }
    NoticeKind.InvalidData -> NoticeSpec(stringResource(R.string.write_invalid), StatusTone.Danger, 5_000)
    NoticeKind.NoInternet -> NoticeSpec(stringResource(R.string.error_no_internet), StatusTone.Neutral, 5_000)
    NoticeKind.Generic -> NoticeSpec(stringResource(R.string.error_server), StatusTone.Danger, 5_000)
}

/** Dải trạng thái ghi trên đầu màn (BO_SUNG_v3.md §3). */
@Composable
fun WriteBanner(state: WriteState) {
    val colors = GgTheme.colors
    val (bg, fg, text) = when (state) {
        WriteState.DeviceReadOnly -> Triple(colors.infoSoft, colors.info, R.string.config_banner_device_read_only)
        WriteState.DisabledByServer -> Triple(colors.infoSoft, colors.info, R.string.config_banner_disabled_by_server)
        WriteState.GatewayOffline -> Triple(colors.warningSoft, colors.warning, R.string.config_banner_gateway_offline)
        WriteState.Enabled -> return
    }
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(GgRadius.radiusMd))
            .background(bg)
            .padding(horizontal = GgSpacing.space3, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(GgSpacing.space2),
    ) {
        Icon(painterResource(R.drawable.ic_lock), contentDescription = null, tint = fg, modifier = Modifier.size(18.dp))
        Text(stringResource(text), style = GgTheme.typography.bodySm, color = fg)
    }
}

