package vn.ggpower.inverter.ui.home.house

import android.graphics.BlurMaskFilter
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathOperation
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.unit.dp
import vn.ggpower.inverter.domain.home.SkyStar
import vn.ggpower.inverter.domain.home.skyStars

/** Chiều cao dải trời (MainDark.dc.html: tràn 20 dp hai bên, bắt đầu 28 dp trên tiêu đề "Dòng năng lượng"). */
val SkyHeight = 228.dp

private val DaySky = Brush.verticalGradient(
    0f to Color(0x005E5A45),
    0.12f to Color(0x8C5E5A45),
    0.26f to Color(0xFF5E5A45),
    0.68f to Color(0xFF5E5A45),
    0.84f to Color(0x8C5E5A45),
    1f to Color(0x005E5A45),
)

private val NightSky = Brush.verticalGradient(
    0f to Color(0x000A1024),
    0.14f to Color(0x730A1024),
    0.26f to Color(0xD90A1024),
    0.62f to Color(0xEB101A36),
    0.82f to Color(0x991C2A4E),
    1f to Color(0x001C2A4E),
)

private val Moon = Color(0xFFF1ECD2)
private val Ease = CubicBezierEasing(0.42f, 0f, 0.58f, 1f)

/** Sao băng: vị trí bắt đầu (dp, trong dải trời), dài, chu kỳ và độ trễ (MainDark.dc.html `.sky-shoot`). */
private class Shoot(val left: Float, val top: Float, val width: Float, val periodMs: Int, val delayMs: Int)

private val Shoots = listOf(Shoot(400f, 30f, 90f, 7000, 1200), Shoot(330f, 8f, 70f, 11000, 5500))

/**
 * Nền trời phía sau sơ đồ ngôi nhà ở chế độ tối (MainDark.dc.html / MainDarkNight.dc.html): ngày là dải vàng xám
 * `#5e5a45` mờ dần hai đầu; đêm là dải xanh đêm với 46 ngôi sao nhấp nháy, trăng lưỡi liềm và hai sao băng.
 * [animate] = false (khuất màn hình) thì vẽ tĩnh.
 */
@Composable
fun SkyBackdrop(night: Boolean, animate: Boolean, modifier: Modifier = Modifier) {
    val stars = remember { skyStars() }
    val clock = if (night && animate) {
        rememberInfiniteTransition(label = "sky").animateFloat(
            initialValue = 0f,
            targetValue = 924_000f, // bội của 7 s và 11 s để sao băng không giật khi quay vòng
            animationSpec = infiniteRepeatable(tween(924_000, easing = LinearEasing), RepeatMode.Restart),
            label = "skyClock",
        )
    } else null
    Canvas(modifier) {
        if (!night) {
            drawRect(DaySky)
            return@Canvas
        }
        drawRect(NightSky)
        val now = clock?.value
        stars.forEach { drawStar(it, now) }
        drawMoon()
        if (now != null) Shoots.forEach { drawShoot(it, now) }
    }
}

/** Sao nhấp nháy giữa 25 % và 100 % (keyframe `skyTw`, ease-in-out); vẽ tĩnh thì sáng hết. */
private fun DrawScope.drawStar(star: SkyStar, now: Float?) {
    val alpha = if (now == null || now < star.delayMs) {
        1f
    } else {
        val p = ((now - star.delayMs) % star.periodMs) / star.periodMs
        val rise = if (p < 0.5f) Ease.transform(p * 2) else 1 - Ease.transform((p - 0.5f) * 2)
        0.25f + 0.75f * rise
    }
    val r = star.sizeDp.dp.toPx() / 2
    drawCircle(Color.White.copy(alpha = alpha), r, Offset(size.width * star.xFraction + r, star.topDp.dp.toPx() + r))
}

/**
 * Trăng 26 dp, cách phải 56 dp, đỉnh 44 dp: lưỡi liềm = đĩa trừ đi chính nó lệch (−7, +3) dp (bóng đổ `inset` của
 * prototype), có quầng sáng mờ 6 dp.
 */
private fun DrawScope.drawMoon() {
    val d = 26.dp.toPx()
    val left = size.width - 56.dp.toPx() - d
    val top = 44.dp.toPx()
    val disc = Path().apply { addOval(Rect(Offset(left, top), Size(d, d))) }
    val hole = Path().apply { addOval(Rect(Offset(left - 7.dp.toPx(), top + 3.dp.toPx()), Size(d, d))) }
    val crescent = Path().apply { op(disc, hole, PathOperation.Difference) }
    drawIntoCanvas { canvas ->
        val glow = Paint().apply {
            color = Moon.copy(alpha = 0.45f)
            asFrameworkPaint().maskFilter = BlurMaskFilter(6.dp.toPx(), BlurMaskFilter.Blur.NORMAL)
        }
        canvas.drawPath(crescent, glow)
    }
    drawPath(crescent, Moon)
}

/**
 * Sao băng (keyframe `skyShoot`): trong 14 % đầu chu kỳ trượt (−460, +115) dp, hiện dần tới 3 % rồi tắt dần; nghiêng
 * −14°, cao 2 dp, đầu trắng nhạt dần sang xanh nhạt.
 */
private fun DrawScope.drawShoot(s: Shoot, now: Float) {
    if (now < s.delayMs) return
    val p = ((now - s.delayMs) % s.periodMs) / s.periodMs
    if (p >= 0.14f) return
    val alpha = if (p < 0.03f) p / 0.03f else 1 - (p - 0.03f) / 0.11f
    val move = p / 0.14f
    val x = (s.left - 460f * move).dp.toPx()
    val y = (s.top + 115f * move).dp.toPx()
    val w = s.width.dp.toPx()
    val h = 2.dp.toPx()
    translate(x, y) {
        rotate(-14f, pivot = Offset(0f, h / 2)) {
            drawRoundRect(
                Brush.horizontalGradient(
                    0f to Color.White,
                    0.35f to Color(0x80BED7FF),
                    1f to Color(0x00BED7FF),
                    startX = 0f,
                    endX = w,
                ),
                size = Size(w, h),
                cornerRadius = CornerRadius(h / 2),
                alpha = alpha.coerceIn(0f, 1f),
            )
        }
    }
}

