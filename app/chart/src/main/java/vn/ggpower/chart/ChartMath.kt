package vn.ggpower.chart

import kotlin.math.abs

/** Thang tuyến tính từ miền giá trị [domainMin, domainMax] sang khoảng vẽ [rangeStart, rangeEnd] (có thể đảo chiều). */
class LinearScale(
    val domainMin: Double,
    val domainMax: Double,
    val rangeStart: Float,
    val rangeEnd: Float,
) {
    init { require(domainMax > domainMin) { "domainMax phải > domainMin" } }

    fun map(value: Double): Float =
        (rangeStart + (value - domainMin) / (domainMax - domainMin) * (rangeEnd - rangeStart)).toFloat()

    fun invert(position: Float): Double =
        domainMin + (position - rangeStart) / (rangeEnd - rangeStart).toDouble() * (domainMax - domainMin)
}

/** Chỉ số phần tử của [xs] (tăng dần) gần [x] nhất; danh sách rỗng → -1. Dùng cho thanh kéo. */
fun nearestIndex(xs: List<Double>, x: Double): Int {
    if (xs.isEmpty()) return -1
    var lo = 0
    var hi = xs.lastIndex
    while (lo < hi) {
        val mid = (lo + hi) / 2
        if (xs[mid] < x) lo = mid + 1 else hi = mid
    }
    return if (lo > 0 && abs(xs[lo - 1] - x) <= abs(xs[lo] - x)) lo - 1 else lo
}

/** Một đoạn Bézier bậc ba: điểm đầu, hai điểm điều khiển, điểm cuối. */
data class Cubic(
    val x0: Float, val y0: Float,
    val c1x: Float, val c1y: Float,
    val c2x: Float, val c2y: Float,
    val x1: Float, val y1: Float,
)

/**
 * Đường cong mượt qua các điểm theo Catmull-Rom (hệ số 1/6), điểm điều khiển theo trục y bị kẹp trong
 * [clampMin, clampMax] để đường không vượt khỏi vùng vẽ (giống đường hệ số hiệu quả của prototype).
 */
fun catmullRom(points: List<Pair<Float, Float>>, clampMin: Float, clampMax: Float): List<Cubic> {
    if (points.size < 2) return emptyList()
    fun clamp(y: Float) = y.coerceIn(clampMin, clampMax)
    return (0 until points.lastIndex).map { i ->
        val p0 = points[maxOf(0, i - 1)]
        val p1 = points[i]
        val p2 = points[i + 1]
        val p3 = points[minOf(points.lastIndex, i + 2)]
        Cubic(
            p1.first, p1.second,
            p1.first + (p2.first - p0.first) / 6f, clamp(p1.second + (p2.second - p0.second) / 6f),
            p2.first - (p3.first - p1.first) / 6f, clamp(p2.second - (p3.second - p1.second) / 6f),
            p2.first, p2.second,
        )
    }
}
