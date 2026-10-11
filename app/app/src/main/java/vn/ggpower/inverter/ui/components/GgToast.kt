package vn.ggpower.inverter.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import vn.ggpower.inverter.R
import vn.ggpower.inverter.ui.theme.GgPowerTheme
import vn.ggpower.inverter.ui.theme.GgRadius
import vn.ggpower.inverter.ui.theme.GgSpacing
import vn.ggpower.inverter.ui.theme.GgTheme

/**
 * Toast nổi (gg-inverter-design: Service.dc.html, TicketDetail.dc.html): viên thuốc nền `toastBg` (light 62 % ink, dark
 * 66 % ink sáng), chữ 12/16 600 `onToast`, dấu ✓ 16, đệm 6 12, bóng 0 6 18. Là lớp phủ: màn đặt nó trong một `Box`
 * (thường cách đáy 104 dp) và tự ẩn sau 2 s. Prototype làm mờ nền phía sau (blur 8); Compose chưa có backdrop blur
 * trên mọi phiên bản Android nên bỏ qua hiệu ứng này.
 */
@Composable
fun GgToast(text: String, modifier: Modifier = Modifier, showCheck: Boolean = true) {
    val colors = GgTheme.colors
    val shape = RoundedCornerShape(GgRadius.radiusPill)
    Row(
        modifier
            .shadow(8.dp, shape, ambientColor = Color.Black, spotColor = Color.Black)
            .clip(shape)
            .background(colors.toastBg)
            .padding(horizontal = GgSpacing.space3, vertical = 6.dp)
            .semantics { liveRegion = LiveRegionMode.Polite },
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (showCheck) {
            Icon(painterResource(R.drawable.ic_check_thin), contentDescription = null, tint = colors.onToast, modifier = Modifier.size(16.dp))
        }
        Text(text, style = GgTheme.typography.caption.copy(fontWeight = FontWeight.SemiBold), color = colors.onToast)
    }
}

@Preview(showBackground = true, backgroundColor = 0xFFF5F7F3)
@Composable
private fun GgToastPreview() {
    Column(verticalArrangement = Arrangement.spacedBy(GgSpacing.space3)) {
        GgPowerTheme(darkTheme = false) { GgToast("Đã sao chép số hotline", Modifier.padding(GgSpacing.space4)) }
        GgPowerTheme(darkTheme = true) { GgToast("Đã sao chép số hotline", Modifier.padding(GgSpacing.space4)) }
    }
}
