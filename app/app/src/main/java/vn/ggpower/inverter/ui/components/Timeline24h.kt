package vn.ggpower.inverter.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import vn.ggpower.inverter.R
import vn.ggpower.inverter.domain.time.MINUTES_PER_DAY
import vn.ggpower.inverter.domain.time.spansOf
import vn.ggpower.inverter.domain.tou.SlotKind
import vn.ggpower.inverter.ui.theme.GgPowerTheme
import vn.ggpower.inverter.ui.theme.GgSpacing
import vn.ggpower.inverter.ui.theme.GgTheme
import java.time.LocalTime

/** Một khung trên thanh 24h. [dimmed] = true khi đang sửa khung khác (mờ 35 %, Thay đổi 0.6 §5). */
data class TimelineSlot(
    val start: LocalTime,
    val end: LocalTime,
    val kind: SlotKind,
    val dimmed: Boolean = false,
)

/** Cỡ thanh: 16b cao 28 / bo 8, box màn 16 cao 24 / bo 8, thẻ 16a cao 14 / bo 6. */
enum class TimelineSize(val height: Dp, val radius: Dp) {
    Large(28.dp, 8.dp),
    Medium(24.dp, 8.dp),
    Mini(14.dp, 6.dp),
}

/**
 * Thanh 24 giờ (dùng chung cho 16, 16a, 16b): nền `surfaceSunken`, đoạn sạc `touCharge`, xả `touDischarge`.
 * Khung qua nửa đêm được vẽ thành hai đoạn ở hai đầu thanh.
 */
@Composable
fun Timeline24h(
    slots: List<TimelineSlot>,
    modifier: Modifier = Modifier,
    size: TimelineSize = TimelineSize.Large,
    contentDescription: String? = null,
) {
    val colors = GgTheme.colors
    val charge = colors.touCharge
    val discharge = colors.touDischarge
    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .height(size.height)
            .clip(RoundedCornerShape(size.radius))
            .background(colors.surfaceSunken)
            .then(if (contentDescription != null) Modifier.semantics { this.contentDescription = contentDescription } else Modifier),
    ) {
        slots.forEach { slot ->
            val color: Color = if (slot.kind == SlotKind.Charge) charge else discharge
            spansOf(slot.start, slot.end).forEach { span ->
                val x = this.size.width * span.start / MINUTES_PER_DAY
                val w = this.size.width * span.length / MINUTES_PER_DAY
                drawRect(
                    color = color,
                    topLeft = Offset(x, 0f),
                    size = Size(w, this.size.height),
                    alpha = if (slot.dimmed) 0.35f else 1f,
                )
            }
        }
    }
}

/** Mốc giờ dưới thanh: 00 · 06 · 12 · 18 · 24, chữ 11/14 `inkMuted`, số đều, dàn đều hai mép. */
@Composable
fun TimelineHourTicks(modifier: Modifier = Modifier) {
    Row(modifier = modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        listOf(0, 6, 12, 18, 24).forEach { h ->
            Text(
                text = h.toString().padStart(2, '0'),
                style = GgTheme.typography.caption.copy(fontSize = 11.sp, lineHeight = 14.sp, fontFeatureSettings = "tnum"),
                color = GgTheme.colors.inkMuted,
            )
        }
    }
}

/** Chú giải Sạc · Xả · Tự động (TouStrategy.dc.html): ô màu 10 dp bo 3 dp, chữ 12/16 `inkMuted`, cách 16 / 6 dp. */
@Composable
fun TimelineLegend(modifier: Modifier = Modifier) {
    val colors = GgTheme.colors
    Row(modifier = modifier, horizontalArrangement = Arrangement.spacedBy(GgSpacing.space4)) {
        LegendItem(stringResource(R.string.tou_legend_charge), colors.touCharge, border = null)
        LegendItem(stringResource(R.string.tou_legend_discharge), colors.touDischarge, border = null)
        LegendItem(stringResource(R.string.tou_legend_auto), colors.surfaceSunken, border = colors.line)
    }
}

@Composable
private fun LegendItem(text: String, swatch: Color, border: Color?) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        val shape = RoundedCornerShape(3.dp)
        Box(
            Modifier
                .size(10.dp)
                .clip(shape)
                .background(swatch)
                .then(if (border != null) Modifier.border(1.dp, border, shape) else Modifier),
        )
        Text(text, style = GgTheme.typography.caption.copy(lineHeight = 16.sp), color = GgTheme.colors.inkMuted)
    }
}

/** Dữ liệu mẫu "Ngày thường" (Thay đổi 0.6 §3) cho Preview. */
private val PreviewSlots = listOf(
    TimelineSlot(LocalTime.of(22, 0), LocalTime.of(6, 0), SlotKind.Charge),
    TimelineSlot(LocalTime.of(13, 0), LocalTime.of(16, 0), SlotKind.Charge),
    TimelineSlot(LocalTime.of(9, 0), LocalTime.of(12, 0), SlotKind.Discharge),
    TimelineSlot(LocalTime.of(18, 0), LocalTime.of(21, 0), SlotKind.Discharge),
)

@Preview(widthDp = 390, showBackground = true, backgroundColor = 0xFFF5F7F3)
@Composable
private fun Timeline24hPreview() {
    GgPowerTheme(darkTheme = false) {
        Column(Modifier.padding(GgSpacing.space5), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Timeline24h(PreviewSlots, size = TimelineSize.Large)
            TimelineHourTicks()
            TimelineLegend()
            Timeline24h(
                PreviewSlots.mapIndexed { i, s -> s.copy(dimmed = i != 0) },
                size = TimelineSize.Medium,
                modifier = Modifier.padding(top = 12.dp),
            )
            Timeline24h(PreviewSlots, size = TimelineSize.Mini, modifier = Modifier.padding(top = 12.dp))
        }
    }
}
