package vn.ggpower.inverter.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import vn.ggpower.inverter.R

val Montserrat = FontFamily(
    Font(R.font.montserrat_regular, FontWeight.Normal),
    Font(R.font.montserrat_medium, FontWeight.Medium),
    Font(R.font.montserrat_semibold, FontWeight.SemiBold),
    Font(R.font.montserrat_bold, FontWeight.Bold),
)

/** Kiểu chữ theo `gg-inverter-design/design/tokens.json` (mục Typography) và quy ước trên màn hình app. */
@Immutable
data class GgTypography(
    val display: TextStyle,
    val h1: TextStyle,
    val h2: TextStyle,
    val h3: TextStyle,
    val screenTitle: TextStyle,
    val eyebrow: TextStyle,
    val body: TextStyle,
    val bodySm: TextStyle,
    val label: TextStyle,
    val caption: TextStyle,
    val metricLg: TextStyle,
    val metric: TextStyle,
    val code: TextStyle,
    val tabLabel: TextStyle,
)

/**
 * Chiều cao dòng như CSS của prototype: giữ nửa khoảng cách dòng ở trên dòng đầu và dưới dòng cuối. Compose mặc định
 * cắt bỏ phần này (`Trim.Both`), làm mỗi khối chữ thấp hơn thiết kế khoảng 4 dp (review đợt 1, 02/10/2026).
 */
private val CssLineHeight = LineHeightStyle(LineHeightStyle.Alignment.Center, LineHeightStyle.Trim.None)

private fun style(size: Int, line: Int, weight: FontWeight, spacing: Float = 0f) = TextStyle(
    fontFamily = Montserrat,
    fontSize = size.sp,
    lineHeight = line.sp,
    fontWeight = weight,
    letterSpacing = spacing.em,
    lineHeightStyle = CssLineHeight,
)

val GgTypographyDefault = GgTypography(
    display = style(48, 56, FontWeight.Bold, -0.02f),
    h1 = style(32, 40, FontWeight.Bold, -0.01f),
    h2 = style(24, 32, FontWeight.SemiBold),
    h3 = style(18, 26, FontWeight.SemiBold),
    screenTitle = style(22, 28, FontWeight.SemiBold),
    eyebrow = style(12, 16, FontWeight.SemiBold, 0.2f),
    body = style(15, 24, FontWeight.Normal),
    bodySm = style(13, 20, FontWeight.Normal),
    label = style(13, 16, FontWeight.SemiBold),
    caption = style(12, 16, FontWeight.Medium),
    metricLg = style(40, 44, FontWeight.SemiBold, -0.02f).copy(fontFeatureSettings = "tnum"),
    metric = style(24, 28, FontWeight.SemiBold).copy(fontFeatureSettings = "tnum"),
    code = TextStyle(fontFamily = FontFamily.Monospace, fontSize = 13.sp, lineHeight = 20.sp, lineHeightStyle = CssLineHeight),
    // Nhãn thanh thẻ nổi: 11/14 600 (html/Projects.dc.html, Main.dc.html).
    tabLabel = style(11, 14, FontWeight.SemiBold),
)
