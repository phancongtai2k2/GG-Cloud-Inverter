package vn.ggpower.inverter.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import vn.ggpower.chart.ChartStyle
import vn.ggpower.inverter.ui.theme.GgTheme

/** Ô màu trước nhãn chú giải (Main.dc.html). */
sealed interface LegendSwatch {
    /** Vạch 10 × 2,5 dp bo tròn — chuỗi đường. */
    data class Line(val color: Color) : LegendSwatch
    /** Ô 10 × 10 dp bo 3 dp — lớp cột. */
    data class Square(val color: Color) : LegendSwatch
    /** Ô 10 × 8 dp nền nhạt, viền trên đứt — vùng SOC. */
    data class Area(val fill: Color, val line: Color) : LegendSwatch
    /** Vạch đứt 14 dp — đường phụ (hệ số hiệu quả). */
    data class Dashed(val color: Color) : LegendSwatch
}

data class LegendEntry(val swatch: LegendSwatch, val label: String, val value: String)

/**
 * Chú giải biểu đồ dạng lưới 2 cột (cách 6 dp dọc, 16 dp ngang): ô màu + nhãn 11/16 500 `inkMuted`,
 * giá trị bên phải 600 `ink` số đều ([valueSize]: 12 cho Công suất / Pin, 11 cho Sản lượng PV).
 */
@Composable
fun ChartLegend(entries: List<LegendEntry>, modifier: Modifier = Modifier, valueSize: Int = 12) {
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        entries.chunked(2).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                row.forEach { LegendCell(it, valueSize, Modifier.weight(1f)) }
                if (row.size == 1) Box(Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun LegendCell(entry: LegendEntry, valueSize: Int, modifier: Modifier) {
    val colors = GgTheme.colors
    Row(modifier, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Swatch(entry.swatch)
        Text(
            entry.label,
            style = GgTheme.typography.caption.copy(fontSize = 11.sp, lineHeight = 16.sp),
            color = colors.inkMuted,
            modifier = Modifier.weight(1f),
        )
        Text(
            entry.value,
            style = GgTheme.typography.caption.copy(fontSize = valueSize.sp, lineHeight = 16.sp, fontWeight = FontWeight.SemiBold, fontFeatureSettings = "tnum"),
            color = colors.ink,
            maxLines = 1,
        )
    }
}

@Composable
private fun Swatch(swatch: LegendSwatch) {
    when (swatch) {
        is LegendSwatch.Line -> Box(Modifier.size(10.dp, 2.5.dp).clip(RoundedCornerShape(50)).background(swatch.color))
        is LegendSwatch.Square -> Box(Modifier.size(10.dp).clip(RoundedCornerShape(3.dp)).background(swatch.color))
        is LegendSwatch.Area -> Canvas(Modifier.size(10.dp, 8.dp)) {
            drawRect(swatch.fill)
            drawLine(
                swatch.line, Offset(0f, 0.75.dp.toPx()), Offset(size.width, 0.75.dp.toPx()),
                strokeWidth = 1.5.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(2.dp.toPx(), 1.5.dp.toPx())),
            )
        }
        is LegendSwatch.Dashed -> Canvas(Modifier.width(14.dp).height(2.dp)) {
            drawLine(
                swatch.color, Offset(0f, size.height / 2), Offset(size.width, size.height / 2),
                strokeWidth = 1.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(3.dp.toPx(), 3.dp.toPx())),
            )
        }
    }
}

/**
 * Kiểu biểu đồ lấy từ theme (Main.dc.html): nhãn trục 11/16 500 `inkMuted` số đều, lưới `cardBorder`
 * (biểu đồ cột: `line`), đường gốc `lineStrong`, vạch kéo `inkMuted`, nền thẻ `cardBg`.
 */
@Composable
fun ggChartStyle(barChart: Boolean = false): ChartStyle {
    val colors = GgTheme.colors
    return ChartStyle(
        labelStyle = GgTheme.typography.caption.copy(fontSize = 11.sp, lineHeight = 16.sp, fontFeatureSettings = "tnum"),
        labelColor = colors.inkMuted,
        gridColor = if (barChart) colors.line else colors.cardBorder,
        baselineColor = colors.lineStrong,
        scrubColor = colors.inkMuted,
        surfaceColor = colors.cardBg,
    )
}
