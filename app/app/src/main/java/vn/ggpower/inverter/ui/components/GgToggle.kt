package vn.ggpower.inverter.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import vn.ggpower.inverter.ui.theme.GgPowerTheme
import vn.ggpower.inverter.ui.theme.GgSpacing
import vn.ggpower.inverter.ui.theme.GgTheme

/**
 * Công tắc 52 × 32 dp (Config.dc.html, Me.dc.html): viền 2 dp; bật nền + viền `primary`, tắt nền `surfaceSunken`
 * viền `lineStrong`; núm trắng 22 dp có bóng, cách mép 5 dp; chuyển 150 ms. Vùng chạm 48 dp.
 * Bị khoá (ví dụ `CONFIG_READ_ONLY`): mờ 50 %.
 */
@Composable
fun GgToggle(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    contentDescription: String? = null,
) {
    val colors = GgTheme.colors
    val track by animateColorAsState(if (checked) colors.primary else colors.surfaceSunken, tween(150), label = "track")
    val border by animateColorAsState(if (checked) colors.primary else colors.lineStrong, tween(150), label = "border")
    val thumbX by animateDpAsState(if (checked) 25.dp else 5.dp, tween(150), label = "thumb")
    Box(
        modifier = modifier
            .size(52.dp, 48.dp)
            .toggleable(value = checked, enabled = enabled, role = Role.Switch, onValueChange = onCheckedChange)
            .then(if (contentDescription != null) Modifier.semantics { this.contentDescription = contentDescription } else Modifier)
            .alpha(if (enabled) 1f else 0.5f),
        contentAlignment = Alignment.CenterStart,
    ) {
        Box(
            Modifier
                .size(52.dp, 32.dp)
                .clip(CircleShape)
                .background(track)
                .border(2.dp, border, CircleShape),
        ) {
            Box(
                Modifier
                    .offset { IntOffset(thumbX.roundToPx(), 5.dp.roundToPx()) }
                    .size(22.dp)
                    .shadow(2.dp, CircleShape, ambientColor = colors.shadow, spotColor = colors.shadow)
                    .clip(CircleShape)
                    .background(colors.surfaceRaised),
            )
        }
    }
}

@Preview(showBackground = true, backgroundColor = 0xFFF5F7F3)
@Composable
private fun GgTogglePreview() {
    GgPowerTheme(darkTheme = false) {
        Row(Modifier.padding(GgSpacing.space5), horizontalArrangement = Arrangement.spacedBy(GgSpacing.space4)) {
            GgToggle(checked = true, onCheckedChange = {})
            GgToggle(checked = false, onCheckedChange = {})
            GgToggle(checked = true, onCheckedChange = {}, enabled = false)
        }
    }
}
