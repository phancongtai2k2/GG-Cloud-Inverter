package vn.ggpower.inverter.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import vn.ggpower.inverter.ui.theme.GgPowerTheme
import vn.ggpower.inverter.ui.theme.GgSpacing
import vn.ggpower.inverter.ui.theme.GgTheme

/**
 * Cỡ segmented: [Regular] 36 dp / chữ 13 (Me: °C/°F, Sáng/Tối; sheet 16b: Khung sạc/xả),
 * [Compact] 34 dp / chữ 12 (Main: Ngày/Tháng/Năm); [Groups] / [GroupsDense] 36 dp / chữ 12 / 11 (nhóm dữ liệu màn 17).
 */
enum class SegmentedSize(val itemHeight: Dp, val fontSize: TextUnit) {
    Regular(36.dp, 13.sp),
    Compact(34.dp, 12.sp),
    /** Nhóm dữ liệu màn 17: inverter 4 nhóm chữ 12, pin 5 nhóm chữ 11 (DataInverter / DataBattery.dc.html). */
    Groups(36.dp, 12.sp),
    GroupsDense(36.dp, 11.sp),
}

/**
 * Bộ chọn dạng nổi (đặc tả mục 4): khung `surfaceSunken` bo 12 dp, đệm 4 dp, cách 4 dp;
 * mục đang chọn nền `surfaceRaised` bo 10 dp có bóng nhẹ, chữ `ink`; mục khác chữ `inkMuted`.
 */
@Composable
fun SegmentedControl(
    options: List<String>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
    size: SegmentedSize = SegmentedSize.Regular,
    groupLabel: String? = null,
    enabled: Boolean = true,
) {
    val colors = GgTheme.colors
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(colors.surfaceSunken)
            .padding(GgSpacing.space1)
            .then(if (groupLabel != null) Modifier.semantics { contentDescription = groupLabel } else Modifier)
            .selectableGroup(),
        horizontalArrangement = Arrangement.spacedBy(GgSpacing.space1),
    ) {
        options.forEachIndexed { index, label ->
            val selected = index == selectedIndex
            val itemShape = RoundedCornerShape(10.dp)
            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(size.itemHeight)
                    .then(
                        if (selected) Modifier
                            .shadow(2.dp, itemShape, ambientColor = colors.shadow, spotColor = colors.shadow)
                            .clip(itemShape)
                            .background(colors.surfaceRaised)
                        else Modifier.clip(itemShape),
                    )
                    .selectable(selected = selected, enabled = enabled, role = Role.Tab) { onSelect(index) },
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = label,
                    style = GgTheme.typography.label.copy(fontSize = size.fontSize),
                    color = if (selected) colors.ink else colors.inkMuted,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Preview(widthDp = 390, showBackground = true, backgroundColor = 0xFFF5F7F3)
@Composable
private fun SegmentedControlPreview() {
    GgPowerTheme(darkTheme = false) {
        Column(Modifier.padding(GgSpacing.space5), verticalArrangement = Arrangement.spacedBy(GgSpacing.space3)) {
            SegmentedControl(listOf("Ngày", "Tháng", "Năm"), selectedIndex = 0, onSelect = {}, size = SegmentedSize.Compact)
            SegmentedControl(listOf("Khung sạc", "Khung xả"), selectedIndex = 1, onSelect = {})
        }
    }
}
