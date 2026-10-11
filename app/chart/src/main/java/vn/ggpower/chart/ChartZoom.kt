package vn.ggpower.chart

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChanged
import kotlin.math.abs
import kotlin.math.max

/**
 * Cửa sổ đang xem của một trục thời gian, tính theo phần của toàn trục: từ [start] tới [end] (0..1), độ phóng [zoom] ≥ 1.
 * Logic thu phóng theo prototype gg-inverter-design (hàm `ZM` trong AlertDetail.dc.html / Main.dc.html).
 */
data class ZoomWindow(val zoom: Float = 1f, val start: Float = 0f) {
    val end: Float get() = start + 1f / zoom
    val zoomed: Boolean get() = zoom > 1.02f

    /** Vị trí trong cửa sổ (0..1) của một điểm trên toàn trục; ngoài cửa sổ thì < 0 hoặc > 1. */
    fun local(fraction: Float): Float = (fraction - start) * zoom

    /** Điểm trên toàn trục ứng với vị trí [local] (0..1) trong cửa sổ. */
    fun global(local: Float): Float = start + local / zoom

    fun contains(fraction: Float): Boolean = fraction >= start - 1e-6f && fraction <= end + 1e-6f

    /**
     * Áp một bước chụm hai ngón: phóng thêm [factor] quanh điểm [focus] (vị trí 0..1 trong cửa sổ hiện tại, giữ nguyên
     * dưới tay), rồi dời [pan] (phần của cửa sổ). Kết quả bị kẹp trong [1, maxZoom] và trong trục.
     */
    fun transformed(factor: Float, focus: Float, pan: Float, maxZoom: Float): ZoomWindow {
        val anchor = global(focus)
        val z = (zoom * factor).coerceIn(1f, maxZoom)
        val s = anchor - (focus - pan) / z
        return ZoomWindow(z, s.coerceIn(0f, max(0f, 1f - 1f / z)))
    }
}

@Stable
class ChartZoomState(val maxZoom: Float) {
    private var zoom by mutableFloatStateOf(1f)
    private var start by mutableFloatStateOf(0f)

    val window: ZoomWindow get() = ZoomWindow(zoom, start)

    fun apply(w: ZoomWindow) {
        zoom = w.zoom
        start = w.start
    }

    fun reset() = apply(ZoomWindow())
}

/** [maxZoom]: mức phóng lớn nhất (AlertDetail: 4; biểu đồ ngày của màn chính: 8). */
@Composable
fun rememberChartZoomState(maxZoom: Float = 4f): ChartZoomState = remember(maxZoom) { ChartZoomState(maxZoom) }

/**
 * Chụm / dãn hai ngón theo chiều ngang để thu phóng trục thời gian. Một ngón không bị chặn: trang vẫn cuộn và biểu đồ
 * vẫn kéo dò được. [plotStart] / [plotEnd]: mép trái / phải vùng vẽ tính bằng px (bỏ phần nhãn trục).
 */
fun Modifier.chartPinchZoom(state: ChartZoomState, plotStart: Float = 0f, plotEnd: () -> Float): Modifier =
    pointerInput(state) {
        awaitEachGesture {
            awaitFirstDown(requireUnconsumed = false)
            var lastDistance = 0f
            var lastCentroid = 0f
            do {
                val event = awaitPointerEvent(PointerEventPass.Initial)
                val pressed = event.changes.filter { it.pressed }
                if (pressed.size >= 2) {
                    val a = pressed[0].position.x
                    val b = pressed[1].position.x
                    val distance = max(8f, abs(a - b))
                    val centroid = (a + b) / 2
                    val width = max(1f, plotEnd() - plotStart)
                    if (lastDistance > 0f) {
                        val focus = ((lastCentroid - plotStart) / width).coerceIn(0f, 1f)
                        val pan = -(centroid - lastCentroid) / width
                        state.apply(state.window.transformed(distance / lastDistance, focus, pan, state.maxZoom))
                    }
                    lastDistance = distance
                    lastCentroid = centroid
                    pressed.forEach { if (it.positionChanged()) it.consume() }
                } else {
                    lastDistance = 0f
                }
            } while (event.changes.any { it.pressed })
        }
    }

/** Nhãn độ dài cửa sổ, ví dụ 30 phút ở mức 4 → "7,5": bỏ phần lẻ khi gần số nguyên, dấu thập phân theo [decimalSeparator]. */
fun windowLength(total: Double, zoom: Float, decimalSeparator: Char = ','): String {
    val v = total / zoom
    val r = Math.round(v)
    return if (abs(v - r) < 0.05) r.toString() else String.format(java.util.Locale.ROOT, "%.1f", v).replace('.', decimalSeparator)
}

