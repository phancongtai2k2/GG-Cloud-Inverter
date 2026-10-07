package vn.ggpower.inverter.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import vn.ggpower.inverter.R
import vn.ggpower.inverter.ui.theme.GgPowerTheme
import vn.ggpower.inverter.ui.theme.GgSpacing
import vn.ggpower.inverter.ui.theme.GgTheme

/**
 * Thanh bước của luồng nhiều bước (ForgotPassword*.dc.html, AddProject*.dc.html): các đoạn chia đều cách 8, mỗi đoạn
 * vạch 4 dp bo tròn + nhãn 11/14 600 `inkMuted` cách 6. Vạch tới bước [current] (tính từ 1) màu `primary`, còn lại
 * `line`; [current] lớn hơn số bước (màn thành công) thì tô hết. TalkBack đọc "Bước 1/3 · Tài khoản".
 */
@Composable
fun StepProgress(labels: List<String>, current: Int, modifier: Modifier = Modifier) {
    val colors = GgTheme.colors
    val shown = current.coerceAtMost(labels.size)
    val description = stringResource(R.string.cd_step_progress, shown, labels.size, labels[shown - 1].substringAfter(". "))
    Row(
        modifier.fillMaxWidth().clearAndSetSemantics { contentDescription = description },
        horizontalArrangement = Arrangement.spacedBy(GgSpacing.space2),
    ) {
        labels.forEachIndexed { i, label ->
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(4.dp)
                        .clip(CircleShape)
                        .background(if (i < current) colors.primary else colors.line),
                )
                Text(
                    label,
                    style = GgTheme.typography.caption.copy(fontSize = 11.sp, lineHeight = 14.sp, fontWeight = FontWeight.SemiBold),
                    color = colors.inkMuted,
                    maxLines = 1,
                )
            }
        }
    }
}

@Preview(widthDp = 390, showBackground = true, backgroundColor = 0xFFF5F7F3)
@Composable
private fun StepProgressPreview() {
    GgPowerTheme(darkTheme = false) {
        StepProgress(listOf("1. Tài khoản", "2. Mã xác thực", "3. Mật khẩu mới"), current = 2)
    }
}
