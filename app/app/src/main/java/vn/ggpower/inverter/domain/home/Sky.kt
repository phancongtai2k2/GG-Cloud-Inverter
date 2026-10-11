package vn.ggpower.inverter.domain.home

/** Nền trời sau sơ đồ ngôi nhà ở chế độ tối (MainDark.dc.html tham số `sky`). */
enum class SkyMode { Auto, Day, Night }

/**
 * Ban đêm khi [mode] = Night, hoặc Auto và giờ địa phương trước 6h / từ 18h (quyết định 11/10/2026: theo giờ máy,
 * không theo vị trí dự án).
 */
fun isNightSky(mode: SkyMode, hour: Int): Boolean = when (mode) {
    SkyMode.Night -> true
    SkyMode.Day -> false
    SkyMode.Auto -> hour < 6 || hour >= 18
}

/** Một ngôi sao: vị trí ngang theo tỉ lệ bề rộng, đỉnh (dp), cỡ (dp), trễ và chu kỳ nhấp nháy (ms). */
data class SkyStar(val xFraction: Float, val topDp: Float, val sizeDp: Float, val delayMs: Int, val periodMs: Int)

/**
 * 46 ngôi sao sinh như prototype (bộ sinh số giả ngẫu nhiên tuyến tính, hạt giống 7) để lần nào mở màn cũng giống nhau
 * và khớp thiết kế.
 */
fun skyStars(): List<SkyStar> {
    var seed = 7L
    fun rnd(): Double { seed = (seed * 9301 + 49297) % 233280; return seed / 233280.0 }
    return List(46) {
        val size = if (rnd() < 0.18) 2.5f else if (rnd() < 0.5) 1.5f else 1f
        val x = rnd().toFloat()
        val top = Math.round(8 + rnd() * 120).toFloat()
        val delay = (rnd() * 3200).toInt()
        val period = (2400 + rnd() * 2400).toInt()
        SkyStar(x, top, size, delay, period)
    }
}
