package vn.ggpower.inverter.ui.home.house

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathMeasure
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import vn.ggpower.inverter.domain.home.FlowDirection

/**
 * Một đường năng lượng của sơ đồ (Main.dc.html): [path] theo chiều "xuôi" trong khung vẽ của sơ đồ, độ dài vệt dài nhất
 * [head] và nét [stroke] cùng đơn vị đó, chu kỳ [periodMs] và phần chu kỳ vệt đang chạy [travel] (còn lại nghỉ) — lấy
 * nguyên từ `stroke-dasharray` / `<animate>` của prototype. [orange]: đường biến tần → tải.
 */
internal class FlowLine(
    val path: Path,
    val head: Float,
    val periodMs: Int,
    val travel: Float,
    val stroke: Float,
    val orange: Boolean = false,
)

/** 5 lớp của một vệt sáng: độ dài theo tỉ lệ vệt dài nhất, độ đậm tăng dần về đầu vệt. */
private val CometLengths = floatArrayOf(1f, 0.75f, 0.52f, 0.32f, 0.14f)
private val CometAlphas = floatArrayOf(0.25f, 0.45f, 0.65f, 0.85f, 1f)

/** Chiều vệt sáng: true = xuôi theo [FlowLine.path], false = ngược, null = không có dòng điện. */
internal fun FlowDirection.forward(forwardWhen: FlowDirection): Boolean? = when (this) {
    FlowDirection.None -> null
    forwardWhen -> true
    else -> false
}

/** Đồng hồ chung cho mọi vệt sáng (ms, tăng liên tục); đứng ở 0 khi [active] = false (khuất màn hình, không có dòng). */
@Composable
internal fun flowClock(active: Boolean): State<Float> {
    if (!active) return remember { mutableFloatStateOf(0f) }
    val transition = rememberInfiniteTransition(label = "flow")
    // 10 phút một vòng: đủ dài để lúc quay về 0 không thấy giật.
    return transition.animateFloat(
        initialValue = 0f,
        targetValue = 600_000f,
        animationSpec = infiniteRepeatable(tween(600_000, easing = LinearEasing), RepeatMode.Restart),
        label = "clock",
    )
}

private val segment = Path()
private val measure = PathMeasure()

/**
 * Vệt sáng 5 lớp (`stroke-dasharray` + `<animate stroke-dashoffset>`): đầu vệt đi từ 0 tới hết đường cộng độ dài vệt dài
 * nhất trong [FlowLine.travel] của chu kỳ, rồi nghỉ tới hết chu kỳ.
 */
internal fun DrawScope.drawComet(line: FlowLine, forward: Boolean, nowMs: Float, color: Color) {
    val phase = (nowMs % line.periodMs) / line.periodMs
    if (phase >= line.travel) return
    measure.setPath(line.path, false)
    val length = measure.length
    val head = phase / line.travel * (length + line.head)
    val style = Stroke(width = line.stroke, cap = StrokeCap.Round)
    for (i in CometLengths.indices) {
        var a = (head - line.head * CometLengths[i]).coerceIn(0f, length)
        var b = head.coerceIn(0f, length)
        if (b <= a) continue
        if (!forward) {
            val ra = length - b
            b = length - a
            a = ra
        }
        segment.reset()
        measure.getSegment(a, b, segment, true)
        drawPath(segment, color.copy(alpha = CometAlphas[i]), style = style)
    }
}
