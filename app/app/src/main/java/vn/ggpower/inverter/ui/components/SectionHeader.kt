package vn.ggpower.inverter.ui.components

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import vn.ggpower.inverter.ui.theme.GgPowerTheme
import vn.ggpower.inverter.ui.theme.GgSpacing
import vn.ggpower.inverter.ui.theme.GgTheme
import java.util.Locale

/**
 * Tiêu đề mục (AGENTS.md §4, token `eyebrow`): chữ hoa 12/16 600, giãn chữ 0.2em, màu `inkMuted`.
 * Truyền chữ thường như trong `strings.xml`; component tự viết hoa theo tiếng Việt.
 */
@Composable
fun SectionHeader(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text.uppercase(Locale.forLanguageTag("vi")),
        style = GgTheme.typography.eyebrow,
        color = GgTheme.colors.inkMuted,
        modifier = modifier.semantics { heading() },
    )
}

@Preview(widthDp = 390, showBackground = true, backgroundColor = 0xFFF5F7F3)
@Composable
private fun SectionHeaderPreview() {
    GgPowerTheme(darkTheme = false) {
        SectionHeader("Pin và ngưỡng SOC", Modifier.padding(GgSpacing.space5))
    }
}
