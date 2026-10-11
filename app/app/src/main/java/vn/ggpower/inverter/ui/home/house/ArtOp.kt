package vn.ggpower.inverter.ui.home.house

import android.graphics.Paint
import android.graphics.Typeface
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.vector.PathParser

/**
 * Một lệnh vẽ của hình minh hoạ tĩnh (sinh bởi `app/tools/svg2kt.mjs` vào [HouseBack] / [HouseFront]).
 * Toạ độ theo khung 700 × 690 của prototype; [drawArt] vẽ ở đúng khung đó, nơi gọi tự co giãn.
 */
internal sealed interface ArtOp

internal class FillOp(val path: Path, val color: Color, val brush: Brush?) : ArtOp

internal class StrokeOp(
    val path: Path,
    val color: Color,
    val brush: Brush?,
    val width: Float,
    val cap: StrokeCap,
    val join: StrokeJoin,
) : ArtOp

internal class ClipStartOp(val path: Path) : ArtOp

internal data object ClipEndOp : ArtOp

internal enum class TextAnchor { Start, Middle }

/** Đoạn chữ (tspan): cỡ chữ, khoảng cách chữ, [dx] tính theo đơn vị cục bộ của SVG. */
internal class TextRun(
    val text: String,
    val color: Color,
    val size: Float,
    val weight: Int,
    val letterSpacing: Float,
    val dx: Float,
)

/** Chữ vẽ theo ma trận affine SVG `[a b c d e f]` (đã gồm phép quy đổi viewBox). */
internal class TextOp(
    val matrix: FloatArray,
    val x: Float,
    val y: Float,
    val anchor: TextAnchor,
    val runs: List<TextRun>,
) : ArtOp

private fun path(d: String): Path = PathParser().parsePathString(d).toPath()

internal fun fill(d: String, color: Color): ArtOp = FillOp(path(d), color, null)
internal fun fill(d: String, brush: Brush): ArtOp = FillOp(path(d), Color.Unspecified, brush)

internal fun stroke(d: String, color: Color, width: Float, cap: StrokeCap = StrokeCap.Butt, join: StrokeJoin = StrokeJoin.Miter): ArtOp =
    StrokeOp(path(d), color, null, width, cap, join)

internal fun stroke(d: String, brush: Brush, width: Float, cap: StrokeCap = StrokeCap.Butt, join: StrokeJoin = StrokeJoin.Miter): ArtOp =
    StrokeOp(path(d), Color.Unspecified, brush, width, cap, join)

internal fun clipStart(d: String): ArtOp = ClipStartOp(path(d))
internal fun clipEnd(): ArtOp = ClipEndOp

internal fun text(matrix: FloatArray, x: Float, y: Float, anchor: TextAnchor, runs: List<TextRun>): ArtOp =
    TextOp(matrix, x, y, anchor, runs)

/** Vẽ [ops] theo thứ tự tài liệu SVG. */
internal fun DrawScope.drawArt(ops: List<ArtOp>) {
    val canvas = drawContext.canvas
    for (op in ops) {
        when (op) {
            is FillOp -> if (op.brush != null) drawPath(op.path, op.brush) else drawPath(op.path, op.color)
            is StrokeOp -> {
                val style = Stroke(width = op.width, cap = op.cap, join = op.join)
                if (op.brush != null) drawPath(op.path, op.brush, style = style) else drawPath(op.path, op.color, style = style)
            }
            is ClipStartOp -> {
                canvas.save()
                canvas.clipPath(op.path)
            }
            ClipEndOp -> canvas.restore()
            is TextOp -> drawTextOp(op)
        }
    }
}

private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG)

/** Chữ trên thiết bị chỉ cao 1–3 dp (logo trên Energy Hub / biến tần): dùng phông hệ thống, đậm khi weight ≥ 600. */
private fun DrawScope.drawTextOp(op: TextOp) {
    val native = drawContext.canvas.nativeCanvas
    val m = op.matrix
    val matrix = android.graphics.Matrix().apply {
        setValues(floatArrayOf(m[0], m[2], m[4], m[1], m[3], m[5], 0f, 0f, 1f))
    }
    fun Paint.apply(run: TextRun) {
        textSize = run.size
        letterSpacing = if (run.size > 0f) run.letterSpacing / run.size else 0f
        typeface = if (run.weight >= 600) Typeface.DEFAULT_BOLD else Typeface.DEFAULT
        color = run.color.toArgb()
    }
    val total = op.runs.sumOf { run -> textPaint.apply(run); (run.dx + textPaint.measureText(run.text)).toDouble() }.toFloat()
    var x = if (op.anchor == TextAnchor.Middle) op.x - total / 2 else op.x
    native.save()
    native.concat(matrix)
    for (run in op.runs) {
        textPaint.apply(run)
        x += run.dx
        native.drawText(run.text, x, op.y, textPaint)
        x += textPaint.measureText(run.text)
    }
    native.restore()
}
