package vn.ggpower.inverter.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import vn.ggpower.inverter.domain.format.WeekdayLabels
import vn.ggpower.inverter.ui.format.weekdayLabels
import vn.ggpower.inverter.ui.theme.GgPowerTheme
import vn.ggpower.inverter.ui.theme.GgSpacing
import vn.ggpower.inverter.ui.theme.GgTheme
import java.time.DayOfWeek

/**
 * Bảy chip T2…CN "Áp dụng các ngày" (Thay đổi 0.6 §4, TouStrategy.dc.html): cao 32 dp, bo 8 dp, cách 4 dp, chữ 12.
 * Chọn: nền + viền `primary`, chữ trắng 600. Không chọn: nền `dayChipOffBg`, viền `cardBorder`, chữ `dayChipOffText` 500,
 * mờ 60 %; riêng CN chữ `sundayText`. Vùng chạm cao 48 dp (chip nằm giữa).
 */
@Composable
fun DayChips(
    selected: Set<DayOfWeek>,
    onToggle: (DayOfWeek) -> Unit,
    modifier: Modifier = Modifier,
    labels: WeekdayLabels = weekdayLabels(),
    enabled: Boolean = true,
) {
    val colors = GgTheme.colors
    Row(modifier = modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(GgSpacing.space1)) {
        DayOfWeek.entries.forEach { day ->
            val on = day in selected
            val shape = RoundedCornerShape(8.dp)
            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(48.dp)
                    .toggleable(value = on, enabled = enabled, role = Role.Checkbox) { onToggle(day) },
                contentAlignment = Alignment.Center,
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(32.dp)
                        .alpha(if (on) 1f else 0.6f)
                        .clip(shape)
                        .background(if (on) colors.primaryFill else colors.dayChipOffBg)
                        .border(1.dp, if (on) colors.primaryFill else colors.cardBorder, shape),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = labels.short.getValue(day),
                        style = GgTheme.typography.label.copy(
                            fontSize = 12.sp,
                            fontWeight = if (on) FontWeight.SemiBold else FontWeight.Medium,
                        ),
                        color = when {
                            on -> colors.onPrimary
                            day == DayOfWeek.SUNDAY -> colors.sundayText
                            else -> colors.dayChipOffText
                        },
                    )
                }
            }
        }
    }
}

@Preview(widthDp = 390, showBackground = true, backgroundColor = 0xFFF9FAF7)
@Composable
private fun DayChipsPreview() {
    GgPowerTheme(darkTheme = false) {
        Column(Modifier.padding(GgSpacing.space5)) {
            DayChips(
                selected = setOf(DayOfWeek.MONDAY, DayOfWeek.TUESDAY, DayOfWeek.WEDNESDAY, DayOfWeek.THURSDAY, DayOfWeek.FRIDAY),
                onToggle = {},
            )
            DayChips(selected = setOf(DayOfWeek.SATURDAY), onToggle = {})
        }
    }
}
