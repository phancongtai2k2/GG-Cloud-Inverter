package vn.ggpower.chart

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle

/**
 * Màu và chữ của biểu đồ, do app truyền vào (module không biết theme của app).
 * Giá trị mặc định trong prototype Main.dc.html: nhãn trục 11/16 500 `#56625c`, vạch lưới `#e3e8e1` đứt 3/4,
 * đường gốc 0 `#7f8b85`, vạch kéo đứt `#56625c`, viền chấm theo nền thẻ `#f9faf7`.
 */
@Immutable
data class ChartStyle(
    val labelStyle: TextStyle,
    val labelColor: Color,
    val gridColor: Color,
    val baselineColor: Color,
    val scrubColor: Color,
    /** Nền thẻ chứa biểu đồ: viền quanh chấm giá trị, vạch ngăn giữa các lớp cột, ruột núm kéo. */
    val surfaceColor: Color,
    /** Độ đậm của cột không được chọn (prototype: 0,45). */
    val barDimAlpha: Float = 0.45f,
)

/** Một mốc trên trục: vị trí theo giá trị và nhãn đã định dạng sẵn ("4", "2,5", "50%", "6h"…). */
data class AxisTick(val value: Double, val label: String)
