package vn.ggpower.inverter.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import vn.ggpower.inverter.R
import vn.ggpower.inverter.ui.theme.GgPowerTheme
import vn.ggpower.inverter.ui.theme.GgSpacing
import vn.ggpower.inverter.ui.theme.GgTheme

private val TITLE_MIN_FONT_SIZE = 16.sp

/**
 * Đầu trang màn hình (thẻ `<header>` trong các file .dc.html): cao 64 dp, tiêu đề 22/28 600.
 * Màn con có nút quay lại 48 dp (mũi tên 24 dp) ở trái. Màn có thanh thẻ: cấp tài khoản không có nút quay lại; cấp dự án
 * có nút quay lại về danh sách Dự án (ADR-003).
 */
@Composable
fun ScreenHeader(
    title: String,
    modifier: Modifier = Modifier,
    onBack: (() -> Unit)? = null,
    backDescription: String? = null,
    actions: @Composable RowScope.() -> Unit = {},
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(64.dp)
            .padding(start = if (onBack != null) GgSpacing.space1 else GgSpacing.space5, end = GgSpacing.space2),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(GgSpacing.space1),
    ) {
        if (onBack != null) {
            IconButton(onClick = onBack, modifier = Modifier.size(48.dp)) {
                Icon(
                    painter = painterResource(R.drawable.ic_back),
                    contentDescription = backDescription ?: stringResource(R.string.cd_back),
                    tint = GgTheme.colors.ink,
                    modifier = Modifier.size(24.dp),
                )
            }
        }
        val titleStyle = GgTheme.typography.screenTitle
        Text(
            text = title,
            style = titleStyle,
            color = GgTheme.colors.ink,
            maxLines = 1,
            // Vừa chỗ thì giữ 22 sp; chỉ thu nhỏ khi tràn (tiêu đề tiếng Anh dài cạnh nút/chip bên phải).
            autoSize = TextAutoSize.StepBased(minFontSize = TITLE_MIN_FONT_SIZE, maxFontSize = titleStyle.fontSize),
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier
                .weight(1f)
                .semantics { heading() },
        )
        actions()
    }
}

@Preview(widthDp = 390, showBackground = true, backgroundColor = 0xFFF5F7F3)
@Composable
private fun ScreenHeaderPreview() {
    GgPowerTheme(darkTheme = false) {
        ScreenHeader(title = stringResource(R.string.screen_config), onBack = {})
    }
}
