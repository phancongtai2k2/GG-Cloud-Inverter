package vn.ggpower.inverter.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import vn.ggpower.inverter.ui.theme.GgPowerTheme
import vn.ggpower.inverter.ui.theme.GgRadius
import vn.ggpower.inverter.ui.theme.GgSpacing
import vn.ggpower.inverter.ui.theme.GgTheme

/**
 * Chip lọc (Alerts.dc.html: Tất cả · Chưa xử lý · Đã xử lý): cao 36 dp, đệm ngang 14 dp, bo tròn,
 * chữ 13/600. Đang chọn: nền + viền `primary`, chữ trắng; không chọn: nền `surfaceRaised`, viền `lineStrong`, chữ `ink`.
 * Vùng chạm cao 48 dp (chip nằm giữa).
 */
@Composable
fun GgFilterChip(
    text: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = GgTheme.colors
    Box(
        modifier = modifier
            .heightIn(min = 48.dp)
            .selectable(selected = selected, role = Role.RadioButton, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .height(36.dp)
                .clip(RoundedCornerShape(GgRadius.radiusPill))
                .background(if (selected) colors.primary else colors.surfaceRaised)
                .border(1.dp, if (selected) colors.primary else colors.lineStrong, RoundedCornerShape(GgRadius.radiusPill))
                .padding(horizontal = 14.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(text, style = GgTheme.typography.label, color = if (selected) colors.onPrimary else colors.ink)
        }
    }
}

/** Sắc thái của chip trạng thái (đặc tả mục 4: xanh Online, vàng Cảnh báo, đỏ Lỗi; thêm xanh dương Thông tin; [Muted]: chữ `inkMuted` — phiếu "Đã đóng" màn 8). */
enum class StatusTone { Success, Warning, Danger, Info, Neutral, Muted }

/**
 * Chip trạng thái (Projects.dc.html "Online", "Cảnh báo"): đệm 2/8 dp, bo 6 dp, chữ 12/16 600,
 * chấm tròn 6 dp cách chữ 6 dp. [showDot] = false cho nhãn như "Hôm nay" ở 16a (chữ 11/16).
 */
@Composable
fun StatusChip(
    text: String,
    tone: StatusTone,
    modifier: Modifier = Modifier,
    showDot: Boolean = true,
    small: Boolean = false,
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
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(GgRadius.radiusSm))
            .background(bg)
            .padding(horizontal = GgSpacing.space2, vertical = 2.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (showDot) Box(Modifier.size(6.dp).clip(CircleShape).background(fg))
        Text(
            text = text,
            style = GgTheme.typography.label.copy(fontSize = if (small) 11.sp else 12.sp, lineHeight = 16.sp),
            color = fg,
            maxLines = 1,
        )
    }
}

@Preview(widthDp = 390, showBackground = true, backgroundColor = 0xFFF5F7F3)
@Composable
private fun ChipsPreview() {
    GgPowerTheme(darkTheme = false) {
        Column(Modifier.padding(GgSpacing.space5), verticalArrangement = Arrangement.spacedBy(GgSpacing.space2)) {
            Row(horizontalArrangement = Arrangement.spacedBy(GgSpacing.space2)) {
                GgFilterChip("Tất cả (4)", selected = true, onClick = {})
                GgFilterChip("Chưa xử lý (2)", selected = false, onClick = {})
                GgFilterChip("Đã xử lý (2)", selected = false, onClick = {})
            }
            Row(horizontalArrangement = Arrangement.spacedBy(GgSpacing.space2)) {
                StatusChip("Online", StatusTone.Success)
                StatusChip("Cảnh báo", StatusTone.Warning)
                StatusChip("Lỗi", StatusTone.Danger)
                StatusChip("Hôm nay", StatusTone.Success, showDot = false, small = true)
            }
        }
    }
}
