package vn.ggpower.inverter.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color

// Bảng màu của thiết kế mới, chép từ gg-inverter-design/design/theme/Color.kt (lớp đổi tên GgColors → GgPalette
// để không nhầm với GgColorScheme). Sửa bảng màu thì sửa ở thiết kế trước rồi chép lại đây.

@Immutable
data class GgPalette(
    val surface: Color,        // nền trang
    val card: Color,           // card nội dung
    val raised: Color,         // dialog, bottom nav, sheet
    val sunken: Color,         // track segmented / slider
    val line: Color,
    val lineStrong: Color,     // viền input, nút phụ
    val ink: Color,
    val inkMuted: Color,
    val primary: Color,        // chữ / trạng thái chọn
    val primaryFill: Color,    // nền nút chính
    val onPrimary: Color,
    val primarySoft: Color,
    val accent: Color,         // cam PV / xả
    val accentText: Color,
    val accentSoft: Color,
    val warning: Color, val warningSoft: Color,
    val danger: Color, val dangerSoft: Color,
    val info: Color, val infoSoft: Color,
    val energyPv: Color, val energyBattery: Color, val energyGrid: Color, val energyLoad: Color,
    val touCharge: Color, val touDischarge: Color,
    val disabledFill: Color,
)

val LightGgPalette = GgPalette(
    surface = Color(0xFFF5F7F3), card = Color(0xFFF9FAF7), raised = Color(0xFFFFFFFF), sunken = Color(0xFFEAEEE8),
    line = Color(0xFFD9DFD8), lineStrong = Color(0xFF7F8B85),
    ink = Color(0xFF13201B), inkMuted = Color(0xFF56625C),
    primary = Color(0xFF0F7A3A), primaryFill = Color(0xFF0F7A3A), onPrimary = Color(0xFFFFFFFF), primarySoft = Color(0xFFE3F3E8),
    accent = Color(0xFFF5831F), accentText = Color(0xFFA34B00), accentSoft = Color(0xFFFFF1E3),
    warning = Color(0xFF8A6100), warningSoft = Color(0xFFFDF3D6),
    danger = Color(0xFFC62828), dangerSoft = Color(0xFFFDECEA),
    info = Color(0xFF2F6FB3), infoSoft = Color(0xFFE7F0FA),
    energyPv = Color(0xFFE0700F), energyBattery = Color(0xFF149C4A), energyGrid = Color(0xFF2F6FB3), energyLoad = Color(0xFF6B7771),
    touCharge = Color(0xFF149C4A), touDischarge = Color(0xFFE0700F),
    disabledFill = Color(0xFFD9DFD8),
)

val DarkGgPalette = GgPalette(
    surface = Color(0xFF0C1612), card = Color(0xFF15221D), raised = Color(0xFF1C2B25), sunken = Color(0xFF08100D),
    line = Color(0xFF26352F), lineStrong = Color(0xFF6B7A73),
    ink = Color(0xFFEEF3EF), inkMuted = Color(0xFF9FADA6),
    primary = Color(0xFF34C26F), primaryFill = Color(0xFF178A47), onPrimary = Color(0xFFFFFFFF), primarySoft = Color(0xFF12301F),
    accent = Color(0xFFF7963A), accentText = Color(0xFFF7963A), accentSoft = Color(0xFF3A2410),
    warning = Color(0xFFE0B341), warningSoft = Color(0xFF33280A),
    danger = Color(0xFFFF7A70), dangerSoft = Color(0xFF3A1513),
    info = Color(0xFF6FA8E8), infoSoft = Color(0xFF102436),
    energyPv = Color(0xFFF7963A), energyBattery = Color(0xFF34C26F), energyGrid = Color(0xFF6FA8E8), energyLoad = Color(0xFF9FADA6),
    touCharge = Color(0xFF34C26F), touDischarge = Color(0xFFF7963A),
    disabledFill = Color(0xFF26352F),
)

// Màu thương hiệu (logo)
val GgOrange = Color(0xFFF5831F)
val GgGreen = Color(0xFF149C4A)
// Nền trời Dark mode màn hình chính (ban ngày)
val DarkDaySky = Color(0xFF5E5A45)

/** Màu chữ cái chiến lược A–D (tokens.md: strategy-a … strategy-d). */
val StrategyColors = listOf(Color(0xFF0F7A3A), Color(0xFF2F6FB3), Color(0xFF8A4FBF), Color(0xFFB36B00))
