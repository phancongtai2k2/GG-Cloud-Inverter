package vn.ggpower.chart

import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.setProgress
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt

/**
 * Biểu đồ cột xếp chồng (Sản lượng PV). Mỗi cột gồm các lớp [segments] theo thứ tự từ dưới lên, màu theo
 * [segmentColors]. [overlay]: đường phụ theo trục phải (hệ số hiệu quả), vẽ cong mượt nét đứt.
 */
@Immutable
data class StackedBarModel(
    val segments: List<List<Double>>,
    val segmentColors: List<Color>,
    val labels: List<String>,
    val leftMax: Double,
    val leftTicks: List<AxisTick>,
    val barWidth: Dp,
    val overlay: List<Double>? = null,
    val overlayColor: Color = Color.Unspecified,
    val rightMax: Double = 1.0,
    val rightTicks: List<AxisTick> = emptyList(),
)

// Hình học theo prototype: cột nhãn hai bên rộng 30, cách vùng vẽ 6; vùng vẽ cao 150; nhãn trục x
// (11/16) bắt đầu ở y 156 (khoảng cách 12 của thẻ trừ 6); vạch lưới đứt ở đỉnh và giữa; bo trên của cột 3 dp.
private const val SIDE = 30f
private const val GAP = 6f
private const val PLOT_H = 150f
private const val LABEL_ROW_TOP = 156f
private const val LABEL_ROW_H = 16f
private val BarChartHeight = (LABEL_ROW_TOP + LABEL_ROW_H).dp

/**
 * Biểu đồ cột xếp chồng có chọn cột: cột đang chọn đậm, cột khác mờ ([ChartStyle.barDimAlpha]); vạch đứt dọc ở cột
 * đang chọn. Chạm hoặc kéo ngang để chọn cột.
 */
@Composable
fun StackedBarChart(
    model: StackedBarModel,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    style: ChartStyle,
    modifier: Modifier = Modifier,
    contentDescription: String? = null,
    selectionDescription: String? = null,
    zoom: ChartZoomState? = null,
    zoomLabel: ((Int) -> String)? = null,
) {
    val measurer = rememberTextMeasurer()
    val currentOnSelect by rememberUpdatedState(onSelect)
    val n = model.segments.size
    val (start, count) = barWindow(n, zoom?.window)
    val view = if (start == 0 && count == n) model else model.window(start, count, zoom?.window?.zoom ?: 1f, zoomLabel)
    val shown = view.segments.size
    val density = LocalDensity.current.density
    var widthPx by remember { mutableFloatStateOf(0f) }
    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .height(BarChartHeight)
            .onSizeChanged { widthPx = it.width.toFloat() }
            .then(
                if (zoom != null) {
                    val side = (SIDE + GAP) * density
                    Modifier.chartPinchZoom(zoom, side) { widthPx - side }
                } else Modifier,
            )
            .semantics {
                if (contentDescription != null) this.contentDescription = contentDescription
                if (selectionDescription != null) stateDescription = selectionDescription
                if (n > 1) {
                    progressBarRangeInfo = ProgressBarRangeInfo(selectedIndex.toFloat(), 0f..(n - 1).toFloat(), n - 2)
                    setProgress { v -> currentOnSelect(v.roundToInt().coerceIn(0, n - 1)); true }
                }
            }
            .pointerInput(view, start) {
                detectTapGestures { p -> currentOnSelect(start + barAt(shown, p.x, size.width.toFloat(), density)) }
            }
            .pointerInput(view, start) {
                detectHorizontalDragGestures { change, _ ->
                    change.consume()
                    currentOnSelect(start + barAt(shown, change.position.x, size.width.toFloat(), density))
                }
            },
    ) {
        drawBars(view, selectedIndex - start, style, measurer)
    }
}

private fun barAt(n: Int, xPx: Float, widthPx: Float, density: Float): Int {
    if (n == 0) return 0
    val left = (SIDE + GAP) * density
    val plotW = widthPx - 2 * left
    return (((xPx - left) / plotW) * n).toInt().coerceIn(0, n - 1)
}

private fun DrawScope.drawBars(model: StackedBarModel, selected: Int, style: ChartStyle, measurer: TextMeasurer) {
    val d = density
    val n = model.segments.size
    if (n == 0) return
    val left = (SIDE + GAP) * d
    val right = size.width - left
    val plotW = right - left
    val plotH = PLOT_H * d
    val slot = plotW / n
    val yScale = LinearScale(0.0, model.leftMax, plotH, 0f)
    val dash = PathEffect.dashPathEffect(floatArrayOf(3 * d, 3 * d))

    // Lưới: đỉnh và giữa
    listOf(0f, plotH / 2).forEach { y ->
        drawLine(style.gridColor, Offset(left, y), Offset(right, y), strokeWidth = 1 * d, pathEffect = dash)
    }
    // Nhãn trục trái (canh phải sát cột) và phải (canh trái)
    model.leftTicks.forEach { t ->
        drawLabel(measurer, t.label, style, anchorX = SIDE * d, centerY = yScale.map(t.value), align = LabelAlign.End)
    }
    val rScale = LinearScale(0.0, model.rightMax, plotH, 0f)
    model.rightTicks.forEach { t ->
        drawLabel(measurer, t.label, style, anchorX = right + GAP * d, centerY = rScale.map(t.value), align = LabelAlign.Start)
    }

    // Cột
    val bw = model.barWidth.toPx()
    val radius = 3 * d
    model.segments.forEachIndexed { i, segs ->
        val cx = left + slot * (i + 0.5f)
        val heights = segs.map { (it / model.leftMax * plotH).toFloat().coerceAtLeast(0f) }
        val total = heights.sum()
        if (total <= 0f) return@forEachIndexed
        val alpha = if (i == selected) 1f else style.barDimAlpha
        val top = plotH - total
        val clip = Path().apply {
            addRoundRect(
                RoundRect(
                    left = cx - bw / 2, top = top, right = cx + bw / 2, bottom = plotH,
                    topLeftCornerRadius = CornerRadius(radius), topRightCornerRadius = CornerRadius(radius),
                ),
            )
        }
        clipPath(clip) {
            var y = plotH
            heights.forEachIndexed { k, h ->
                if (h <= 0f) return@forEachIndexed
                drawRect(model.segmentColors[k], Offset(cx - bw / 2, y - h), Size(bw, h), alpha = alpha)
                // Vạch ngăn 1 dp màu nền thẻ ở đỉnh lớp nếu còn lớp phía trên.
                if (heights.drop(k + 1).any { it > 0f } && h > 1 * d) {
                    drawRect(style.surfaceColor, Offset(cx - bw / 2, y - h), Size(bw, 1 * d))
                }
                y -= h
            }
        }
    }

    // Đường phụ (hệ số hiệu quả)
    model.overlay?.let { values ->
        val pts = values.mapIndexed { i, v -> (left + slot * (i + 0.5f)) to rScale.map(v) }
        val cubics = catmullRom(pts, 0f, plotH)
        if (cubics.isNotEmpty()) {
            val path = Path().apply {
                moveTo(cubics.first().x0, cubics.first().y0)
                cubics.forEach { c -> cubicTo(c.c1x, c.c1y, c.c2x, c.c2y, c.x1, c.y1) }
            }
            drawPath(path, model.overlayColor, style = Stroke(width = 1 * d, cap = StrokeCap.Round, pathEffect = dash))
        }
    }

    // Vạch chọn
    if (selected in 0 until n) {
        val x = left + slot * (selected + 0.5f)
        drawLine(style.scrubColor, Offset(x, 0f), Offset(x, plotH), strokeWidth = 1 * d, pathEffect = dash)
    }

    // Nhãn trục x dưới từng cột
    model.labels.forEachIndexed { i, label ->
        if (label.isEmpty()) return@forEachIndexed
        drawLabel(measurer, label, style, anchorX = left + slot * (i + 0.5f), centerY = (LABEL_ROW_TOP + LABEL_ROW_H / 2) * d, align = LabelAlign.Center)
    }
}

/**
 * Cửa sổ cột đang xem khi phóng (Main.dc.html `ys0`, `ycnt`): cột đầu = làm tròn [ZoomWindow.start] × [n], số cột =
 * làm tròn [n] / [ZoomWindow.zoom] (ít nhất 1, không vượt cuối). Không phóng: (0, [n]).
 */
fun barWindow(n: Int, window: ZoomWindow?): Pair<Int, Int> {
    if (n == 0 || window == null || !window.zoomed) return 0 to n
    val start = minOf(n - 1, Math.round(window.start * n))
    val count = (Math.round(n / window.zoom)).coerceIn(1, n - start)
    return start to count
}

/** Phần mô hình của [count] cột từ [start]: cột rộng thêm theo [zoom] (tối đa 24 dp); ≤ 8 cột thì nhãn từng cột bằng [label]. */
private fun StackedBarModel.window(start: Int, count: Int, zoom: Float, label: ((Int) -> String)?): StackedBarModel {
    val range = start until start + count
    return copy(
        segments = segments.slice(range),
        labels = if (count <= 8 && label != null) range.map(label) else labels.slice(range),
        barWidth = minOf(24.dp, barWidth * zoom),
        overlay = overlay?.slice(range),
    )
}
