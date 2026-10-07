package vn.ggpower.chart

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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.setProgress
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.abs
import kotlin.math.roundToInt

enum class AxisSide { Left, Right }

/** Trục giá trị: miền [min, max] vẽ từ đáy (y 150) lên đỉnh (y 10). [baseline]: mốc vẽ nét liền (thường là 0). */
@Immutable
data class ValueAxis(
    val min: Double,
    val max: Double,
    val ticks: List<AxisTick>,
    val baseline: Double? = null,
)

/**
 * Một chuỗi đường. [values] cùng độ dài với [CartesianChartModel.xs]; null = không có dữ liệu (đứt đoạn).
 * [areaColor]: tô vùng từ đường xuống đáy vùng vẽ (SOC của biểu đồ Pin). [dash]: nét đứt (dp).
 */
@Immutable
data class LineSeries(
    val values: List<Double?>,
    val color: Color,
    val width: Dp = 2.dp,
    val dash: List<Dp>? = null,
    val axis: AxisSide = AxisSide.Left,
    val areaColor: Color? = null,
    val dotColor: Color = color,
)

/**
 * Một chuỗi cột mọc từ đường 0 (dương lên, âm xuống), cùng trục x với đường. Mỗi cột rộng [width], canh giữa tại
 * x, dịch ngang [offset] lần (bề rộng + 1 dp) để đặt nhiều chuỗi cạnh nhau. null hoặc 0 = không có cột.
 */
@Immutable
data class BarSeries(
    val values: List<Double?>,
    val color: Color,
    val width: Dp = 8.dp,
    val axis: AxisSide = AxisSide.Left,
    val offset: Float = 0f,
)

/**
 * Mô hình biểu đồ đường (và cột) theo trục x liên tục. [xs] tăng dần (ví dụ phút trong ngày), trục x chạy [xMin]…[xMax]
 * (cả ngày 0…1440 dù dữ liệu mới tới 09:41).
 */
@Immutable
data class CartesianChartModel(
    val xs: List<Double>,
    val xMin: Double,
    val xMax: Double,
    val xTicks: List<AxisTick>,
    val left: ValueAxis,
    val right: ValueAxis? = null,
    val series: List<LineSeries>,
    val bars: List<BarSeries> = emptyList(),
)

/**
 * Hình học của biểu đồ (đơn vị dp). [Default] theo Main.dc.html: khung cao 174, vùng vẽ y 10…150, nhãn trục x ở đường
 * chân chữ 168, vùng vẽ bắt đầu x 30 (nhãn trái canh phải ở x 24), kết thúc cách mép phải 2 (không có trục phải) hoặc 32.
 * [Compact] theo DataInverter / DataBattery.dc.html: vùng vẽ tràn hết chiều ngang, y 10…145, không vẽ số trên trục tung,
 * lưới nét liền.
 */
@Immutable
data class ChartGeometry(
    val height: Dp,
    val plotTop: Float,
    val plotBottom: Float,
    val xLabelBaseline: Float,
    val plotLeft: Float,
    val leftLabelRight: Float,
    val rightGapNoAxis: Float,
    val rightGapWithAxis: Float,
    val rightLabelOffset: Float,
    val dashedGrid: Boolean,
) {
    companion object {
        val Default = ChartGeometry(174.dp, 10f, 150f, 168f, 30f, 24f, 2f, 32f, 6f, dashedGrid = true)
        val Compact = ChartGeometry(170.dp, 10f, 145f, 166f, 0f, 0f, 0f, 0f, 0f, dashedGrid = false)
    }
}

/**
 * Biểu đồ đường / cột có thanh kéo (Công suất, Pin S16 Pre ở Màn hình chính; dùng lại cho 10a, 17).
 * Cột không được chọn mờ theo [ChartStyle.barDimAlpha].
 * Chạm hoặc kéo ngang để chọn điểm; [onSelect] nhận chỉ số trong [CartesianChartModel.xs].
 * TalkBack: điều chỉnh bằng `setProgress`, đọc [selectionDescription].
 */
@Composable
fun CartesianChart(
    model: CartesianChartModel,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    style: ChartStyle,
    modifier: Modifier = Modifier,
    contentDescription: String? = null,
    selectionDescription: String? = null,
    geometry: ChartGeometry = ChartGeometry.Default,
) {
    val measurer = rememberTextMeasurer()
    val currentOnSelect by rememberUpdatedState(onSelect)
    val lastIndex = model.xs.lastIndex
    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .height(geometry.height)
            .semantics {
                if (contentDescription != null) this.contentDescription = contentDescription
                if (selectionDescription != null) stateDescription = selectionDescription
                if (lastIndex > 0) {
                    progressBarRangeInfo = ProgressBarRangeInfo(selectedIndex.toFloat(), 0f..lastIndex.toFloat(), lastIndex - 1)
                    setProgress { v -> currentOnSelect(v.roundToInt().coerceIn(0, lastIndex)); true }
                }
            }
            .pointerInput(model) {
                detectTapGestures { p -> currentOnSelect(indexAt(model, geometry, p.x, size.width.toFloat(), density)) }
            }
            .pointerInput(model) {
                detectHorizontalDragGestures { change, _ ->
                    change.consume()
                    currentOnSelect(indexAt(model, geometry, change.position.x, size.width.toFloat(), density))
                }
            },
    ) {
        drawCartesian(model, geometry, selectedIndex, style, measurer)
    }
}

private fun plotRightDp(model: CartesianChartModel, g: ChartGeometry, widthDp: Float) =
    widthDp - if (model.right != null) g.rightGapWithAxis else g.rightGapNoAxis

private fun indexAt(model: CartesianChartModel, g: ChartGeometry, xPx: Float, widthPx: Float, density: Float): Int {
    val widthDp = widthPx / density
    val scale = LinearScale(model.xMin, model.xMax, g.plotLeft, plotRightDp(model, g, widthDp))
    return nearestIndex(model.xs, scale.invert(xPx / density)).coerceAtLeast(0)
}

private fun DrawScope.drawCartesian(model: CartesianChartModel, g: ChartGeometry, selected: Int, style: ChartStyle, measurer: TextMeasurer) {
    val d = density
    val widthDp = size.width / d
    val xScale = LinearScale(model.xMin, model.xMax, g.plotLeft * d, plotRightDp(model, g, widthDp) * d)
    val leftScale = LinearScale(model.left.min, model.left.max, g.plotBottom * d, g.plotTop * d)
    val rightScale = model.right?.let { LinearScale(it.min, it.max, g.plotBottom * d, g.plotTop * d) }
    val plotLeft = g.plotLeft * d
    val plotRight = plotRightDp(model, g, widthDp) * d
    val gridDash = PathEffect.dashPathEffect(floatArrayOf(3 * d, 4 * d))

    // Vùng tô (vẽ trước để nằm dưới lưới và đường)
    model.series.filter { it.areaColor != null }.forEach { s ->
        val scale = if (s.axis == AxisSide.Right && rightScale != null) rightScale else leftScale
        val pts = s.values.mapIndexedNotNull { i, v -> v?.let { Offset(xScale.map(model.xs[i]), scale.map(it)) } }
        if (pts.size >= 2) {
            val area = Path().apply {
                moveTo(pts.first().x, g.plotBottom * d)
                pts.forEach { lineTo(it.x, it.y) }
                lineTo(pts.last().x, g.plotBottom * d)
                close()
            }
            drawPath(area, s.areaColor!!)
        }
    }

    // Lưới và nhãn trục trái
    model.left.ticks.forEach { t ->
        val y = leftScale.map(t.value)
        val isBase = model.left.baseline == t.value
        drawLine(
            if (isBase) style.baselineColor else style.gridColor,
            Offset(plotLeft, y), Offset(plotRight, y), strokeWidth = 1 * d,
            pathEffect = if (isBase || !g.dashedGrid) null else gridDash,
        )
        if (t.label.isNotEmpty()) drawLabel(measurer, t.label, style, anchorX = g.leftLabelRight * d, centerY = y, align = LabelAlign.End)
    }
    // Nhãn trục phải
    if (model.right != null && rightScale != null) {
        model.right.ticks.forEach { t ->
            if (t.label.isNotEmpty()) drawLabel(measurer, t.label, style, anchorX = plotRight + g.rightLabelOffset * d, centerY = rightScale.map(t.value), align = LabelAlign.Start)
        }
    }
    // Nhãn trục x
    model.xTicks.forEach { t ->
        drawLabel(measurer, t.label, style, anchorX = xScale.map(t.value), baselineY = g.xLabelBaseline * d, align = LabelAlign.Center)
    }

    // Cột (dương lên, âm xuống từ đường 0)
    model.bars.forEach { b ->
        val scale = if (b.axis == AxisSide.Right && rightScale != null) rightScale else leftScale
        val zero = scale.map(0.0.coerceIn(scale.domainMin, scale.domainMax))
        val w = b.width.toPx()
        b.values.forEachIndexed { i, v ->
            if (v == null || v == 0.0) return@forEachIndexed
            val cx = xScale.map(model.xs[i]) + b.offset * (w + 1 * d)
            val y = scale.map(v)
            val alpha = if (i == selected) 1f else style.barDimAlpha
            drawRect(b.color, Offset(cx - w / 2, minOf(y, zero)), Size(w, abs(zero - y)), alpha = alpha)
        }
    }

    // Đường
    model.series.forEach { s ->
        val scale = if (s.axis == AxisSide.Right && rightScale != null) rightScale else leftScale
        val path = Path()
        var open = false
        s.values.forEachIndexed { i, v ->
            if (v == null) { open = false; return@forEachIndexed }
            val x = xScale.map(model.xs[i]); val y = scale.map(v)
            if (open) path.lineTo(x, y) else { path.moveTo(x, y); open = true }
        }
        drawPath(
            path, s.color,
            style = Stroke(
                width = s.width.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round,
                pathEffect = s.dash?.let { dash -> PathEffect.dashPathEffect(dash.map { it.toPx() }.toFloatArray()) },
            ),
        )
    }

    // Thanh kéo: vạch đứt, núm tròn 12 dp ở đáy, chấm 9 dp viền 1,5 dp trên từng chuỗi
    if (selected in model.xs.indices) {
        val x = xScale.map(model.xs[selected])
        drawLine(style.scrubColor, Offset(x, g.plotTop * d), Offset(x, g.plotBottom * d), strokeWidth = 1 * d, pathEffect = PathEffect.dashPathEffect(floatArrayOf(3 * d, 3 * d)))
        model.series.forEach { s ->
            val v = s.values.getOrNull(selected) ?: return@forEach
            val scale = if (s.axis == AxisSide.Right && rightScale != null) rightScale else leftScale
            val c = Offset(x, scale.map(v))
            drawCircle(style.surfaceColor, radius = 4.5f * d, center = c)
            drawCircle(s.dotColor, radius = 3f * d, center = c)
        }
        val grip = Offset(x, g.plotBottom * d)
        drawCircle(style.surfaceColor, radius = 6f * d, center = grip)
        drawCircle(style.scrubColor, radius = 5f * d, center = grip, style = Stroke(width = 2 * d))
    }
}

internal enum class LabelAlign { Start, Center, End }

/** Vẽ nhãn trục: canh theo [anchorX]; theo chiều dọc canh giữa [centerY] hoặc đặt chân chữ ở [baselineY]. */
internal fun DrawScope.drawLabel(
    measurer: TextMeasurer,
    text: String,
    style: ChartStyle,
    anchorX: Float,
    centerY: Float? = null,
    baselineY: Float? = null,
    align: LabelAlign,
) {
    val layout = measurer.measure(text, style.labelStyle.copy(color = style.labelColor))
    val x = when (align) {
        LabelAlign.Start -> anchorX
        LabelAlign.Center -> anchorX - layout.size.width / 2f
        LabelAlign.End -> anchorX - layout.size.width
    }.coerceIn(0f, (size.width - layout.size.width).coerceAtLeast(0f))
    val y = when {
        centerY != null -> centerY - layout.size.height / 2f
        baselineY != null -> baselineY - layout.firstBaseline
        else -> 0f
    }
    drawText(layout, topLeft = Offset(x, y))
}
