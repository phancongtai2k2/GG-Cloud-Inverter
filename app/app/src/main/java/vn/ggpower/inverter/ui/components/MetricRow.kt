package vn.ggpower.inverter.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import vn.ggpower.inverter.ui.theme.GgPowerTheme
import vn.ggpower.inverter.ui.theme.GgSpacing
import vn.ggpower.inverter.ui.theme.GgTheme

/** Một chỉ số: nhãn và giá trị; [mono] cho mã firmware (monospace 13 400). */
data class Metric(val label: String, val value: String, val mono: Boolean = false)

/**
 * Hàng chỉ số chia đều, cách 12 (Device.dc.html, BatteryEnergy.dc.html "Thông số pin"): nhãn 12/16 500 `inkMuted`,
 * giá trị 15/22 600 số đều, cách 2. Mỗi ô đọc là "nhãn: giá trị". Hàng thiếu ô vẫn giữ độ rộng cột theo [columns].
 */
@Composable
fun MetricRow(items: List<Metric>, modifier: Modifier = Modifier, columns: Int = items.size) {
    val colors = GgTheme.colors
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(GgSpacing.space3)) {
        items.forEach { (label, value, mono) ->
            Column(
                Modifier.weight(1f).clearAndSetSemantics { contentDescription = "$label: $value" },
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Text(label, style = GgTheme.typography.caption, color = colors.inkMuted, maxLines = 1)
                Text(
                    value,
                    style = if (mono) GgTheme.typography.code.copy(fontFamily = FontFamily.Monospace, lineHeight = 22.sp)
                    else GgTheme.typography.body.copy(fontSize = 15.sp, lineHeight = 22.sp, fontWeight = FontWeight.SemiBold, fontFeatureSettings = "tnum"),
                    color = colors.ink,
                    maxLines = 1,
                )
            }
        }
        repeat(columns - items.size) { Column(Modifier.weight(1f)) {} }
    }
}

@Preview(widthDp = 390, showBackground = true, backgroundColor = 0xFFF9FAF7)
@Composable
private fun MetricRowPreview() {
    GgPowerTheme(darkTheme = false) {
        MetricRow(listOf(Metric("Điện áp", "51,2 V"), Metric("Dòng sạc", "21,5 A"), Metric("Firmware", "v1.3.2", mono = true)))
    }
}
