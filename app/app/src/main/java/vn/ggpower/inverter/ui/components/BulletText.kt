package vn.ggpower.inverter.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import vn.ggpower.inverter.ui.theme.GgSpacing
import vn.ggpower.inverter.ui.theme.GgTheme

/** Danh sách ghi chú có chấm đầu dòng (thẻ `<ul>` lùi 18 dp ở 16a, 16b): chữ 12/18 `inkMuted`, các dòng cách 4 dp. */
@Composable
fun BulletList(items: List<String>, modifier: Modifier = Modifier) {
    val colors = GgTheme.colors
    val style = GgTheme.typography.caption.copy(fontWeight = FontWeight.Normal, lineHeight = 18.sp)
    Column(modifier, verticalArrangement = Arrangement.spacedBy(GgSpacing.space1)) {
        items.forEach { text ->
            Row(Modifier.fillMaxWidth()) {
                Box(Modifier.size(18.dp), contentAlignment = Alignment.Center) {
                    Box(Modifier.size(5.dp).clip(CircleShape).background(colors.inkMuted))
                }
                Text(text, style = style, color = colors.inkMuted)
            }
        }
    }
}
