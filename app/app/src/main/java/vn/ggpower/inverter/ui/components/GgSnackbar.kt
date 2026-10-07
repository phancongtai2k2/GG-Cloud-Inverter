package vn.ggpower.inverter.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import vn.ggpower.inverter.ui.theme.GgPowerTheme
import vn.ggpower.inverter.ui.theme.GgRadius
import vn.ggpower.inverter.ui.theme.GgSpacing
import vn.ggpower.inverter.ui.theme.GgTheme

/**
 * Snackbar theo sắc thái (BO_SUNG_v3.md §2–3): nền `*-soft` theo [tone], chữ `body-sm` `ink`, bo `radius-md`,
 * bóng nhẹ; nút [actionLabel] (ví dụ "Thử lại") chữ đậm màu của sắc thái. Thời gian hiện do màn quyết định.
 */
@Composable
fun GgSnackbar(
    text: String,
    tone: StatusTone,
    modifier: Modifier = Modifier,
    actionLabel: String? = null,
    onAction: () -> Unit = {},
) {
    val colors = GgTheme.colors
    val (bg: Color, fg: Color) = when (tone) {
        StatusTone.Success -> colors.successSoft to colors.success
        StatusTone.Warning -> colors.warningSoft to colors.warning
        StatusTone.Danger -> colors.dangerSoft to colors.danger
        StatusTone.Info -> colors.infoSoft to colors.info
        StatusTone.Neutral -> colors.surfaceSunken to colors.ink
        StatusTone.Muted -> colors.surfaceSunken to colors.inkMuted
    }
    val shape = RoundedCornerShape(GgRadius.radiusMd)
    Row(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .shadow(6.dp, shape, ambientColor = colors.shadow, spotColor = colors.shadow)
            .clip(shape)
            .background(bg)
            .padding(start = GgSpacing.space4, end = if (actionLabel != null) GgSpacing.space1 else GgSpacing.space4)
            .semantics { liveRegion = LiveRegionMode.Polite },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(GgSpacing.space2),
    ) {
        Text(
            text,
            style = GgTheme.typography.bodySm,
            color = colors.ink,
            modifier = Modifier.weight(1f).padding(vertical = GgSpacing.space3),
        )
        if (actionLabel != null) {
            TextButton(onClick = onAction, modifier = Modifier.heightIn(min = 48.dp)) {
                Text(actionLabel, style = GgTheme.typography.label, color = fg)
            }
        }
    }
}

@Preview(widthDp = 390, showBackground = true, backgroundColor = 0xFFF5F7F3)
@Composable
private fun GgSnackbarPreview() {
    GgPowerTheme(darkTheme = false) {
        Column(Modifier.padding(GgSpacing.space5), verticalArrangement = Arrangement.spacedBy(GgSpacing.space3)) {
            GgSnackbar("Đã áp dụng.", StatusTone.Success)
            GgSnackbar("Thiết bị không nhận giá trị mới, đã giữ giá trị cũ.", StatusTone.Warning)
            GgSnackbar("Chưa thay đổi được. Thử lại sau.", StatusTone.Danger, actionLabel = "Thử lại")
        }
    }
}
