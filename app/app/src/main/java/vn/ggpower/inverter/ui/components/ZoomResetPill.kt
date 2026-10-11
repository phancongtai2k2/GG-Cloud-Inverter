package vn.ggpower.inverter.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import vn.ggpower.inverter.R
import vn.ggpower.inverter.ui.theme.GgPowerTheme
import vn.ggpower.inverter.ui.theme.GgRadius
import vn.ggpower.inverter.ui.theme.GgTheme

/**
 * Nút "đặt lại thu phóng" ở góc phải tiêu đề biểu đồ (gg-inverter-design: AlertDetail, Main): viên thuốc cao 26, đệm
 * 0 8 0 10, viền `zoomPillBorder`, nền `primarySoft`, chữ 11/600 số đều `primary` = độ dài cửa sổ đang xem ([label],
 * ví dụ "15 phút") + "×". Chỉ hiện khi đang phóng; vùng chạm cao 48.
 */
@Composable
fun ZoomResetPill(label: String, onReset: () -> Unit, modifier: Modifier = Modifier) {
    val colors = GgTheme.colors
    val shape = RoundedCornerShape(GgRadius.radiusPill)
    val cd = stringResource(R.string.cd_reset_zoom)
    Box(
        modifier
            .heightIn(min = 48.dp)
            .clickable(role = Role.Button, onClick = onReset)
            .semantics { contentDescription = cd },
        contentAlignment = Alignment.Center,
    ) {
        Row(
            Modifier
                .height(26.dp)
                .clip(shape)
                .background(colors.primarySoft)
                .border(1.dp, colors.zoomPillBorder, shape)
                .padding(start = 10.dp, end = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            val style = GgTheme.typography.caption.copy(fontSize = 11.sp, fontWeight = FontWeight.SemiBold, fontFeatureSettings = "tnum")
            Text(label, style = style, color = colors.primary)
            Text("×", style = style.copy(fontSize = 13.sp, lineHeight = 13.sp), color = colors.primary)
        }
    }
}

@Preview(showBackground = true, backgroundColor = 0xFFF9FAF7)
@Composable
private fun ZoomResetPillPreview() {
    GgPowerTheme(darkTheme = false) { ZoomResetPill("15 phút", {}) }
}
