package vn.ggpower.inverter.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import vn.ggpower.inverter.domain.format.GgFormat
import vn.ggpower.inverter.domain.format.NumberStyle
import vn.ggpower.inverter.domain.settings.AppLanguage

/** Bộ màu đang dùng (light hoặc dark), dựng từ [LightGgPalette] / [DarkGgPalette]. Màn hình chỉ đọc màu qua `GgTheme.colors`. */
@Immutable
data class GgColorScheme(
    /** Đang dùng bộ màu tối (nền trời sau sơ đồ ngôi nhà chỉ có ở Dark — MainDark.dc.html). */
    val isDark: Boolean,
    val ggOrange: Color,
    val ggGreen: Color,
    val surface: Color,
    val surfaceRaised: Color,
    val surfaceSunken: Color,
    val line: Color,
    val lineStrong: Color,
    val ink: Color,
    val inkMuted: Color,
    val primary: Color,
    /** Nền nút chính và mọi khối tô `primary` mang chữ / icon [onPrimary] (dark: #178A47, đậm hơn [primary]). */
    val primaryFill: Color,
    val onPrimary: Color,
    val primarySoft: Color,
    val accent: Color,
    val onAccent: Color,
    val accentText: Color,
    val accentSoft: Color,
    val focus: Color,
    val success: Color,
    val successSoft: Color,
    val warning: Color,
    val warningSoft: Color,
    val danger: Color,
    val dangerSoft: Color,
    val info: Color,
    val infoSoft: Color,
    val energyPv: Color,
    val energyBattery: Color,
    val energyGrid: Color,
    val energyLoad: Color,
    val cardBg: Color,
    val cardBorder: Color,
    /** Màu bóng (thiết kế mới: dark cũng đổ bóng, màu đen). */
    val shadow: Color,
    /** Đoạn sạc / xả TOU (thiết kế mới: sạc xanh, xả cam). */
    val touCharge: Color,
    val touDischarge: Color,
    /** Chữ "CN" khi chưa chọn (tokens.md: sunday-text = danger). */
    val sundayText: Color,
    /** Màu chữ cái chiến lược A–D (tokens.md: strategy-a … strategy-d). */
    val strategy: List<Color>,
    // --- Giá trị prototype dùng nhưng chưa có trong tokens.json (OPEN_QUESTIONS 01/10/2026) ---
    /** Chữ lỗi / nút xoá màu đỏ (#b3261e trong TouStrategy, Config…). */
    val dangerText: Color,
    /** Nền nút chính khi bị khoá (#c9d1cb, nút Lưu của sheet 16b). */
    val disabledFill: Color,
    /** Chip ngày không chọn: nền #eef1ec, chữ #9aa59f (Thay đổi 0.6 §4). */
    val dayChipOffBg: Color,
    val dayChipOffText: Color,
    /** Vạch mốc trên thanh trượt (#b3bdb7, SOC dự phòng màn 16). */
    val sliderTick: Color,
    /** Phần đã chọn và viền núm thanh trượt khi khoá ghi (Config.dc.html `resOff`: #b3bdb7 / #3a4743). */
    val sliderDisabled: Color,
    /** Ô mô tả trong hộp xác nhận (Config.dc.html: #f5f7f3 / ConfigDark: #15221d). */
    val dialogInset: Color,
    /** Sơ đồ ngôi nhà (Main.dc.html): ray đường năng lượng #c9cdd2, tia sáng xanh #2fd27a (chung sáng/tối). */
    val houseFlowTrack: Color,
    val houseFlowGreen: Color,
    /** Chip "Giảm CO₂" trôi trên mái: nền / viền / bóng (sáng: trắng 55 %, #cfe9d8 70 %; tối: #15221d 55 %, #34c26f 35 %). */
    val co2ChipBg: Color,
    val co2ChipBorder: Color,
    val co2ChipShadow: Color,
    /** Toast nổi (gg-inverter-design CLAUDE.md §4): nền rgba(19,32,27,.62) / rgba(238,243,239,.66), chữ trắng / #0c1612. */
    /** Nền công tắc khi tắt (Me.dc.html: #eaeee8 / MeDark: #0e1814). */
    val toggleOffTrack: Color,
    /** Viền nút "đặt lại thu phóng" biểu đồ (#cfe9d8 / #1f4a30). */
    val zoomPillBorder: Color,
    /** Chất lỏng trong khối pin 3D màn 11 (thiết kế mới, một màu theo SOC): mặt trước / bên / trên. Vạch giới hạn: ink 60 % / 75 %. */
    val batteryLiquidFront: Color,
    val batteryLiquidSide: Color,
    val batteryLiquidTop: Color,
    val batteryLimitLine: Color,
    val toastBg: Color,
    val onToast: Color,
    /** Lớp phủ sau bottom sheet / dialog: rgba(14,21,18,0.5) (TouStrategy.dc.html). */
    val scrim: Color,
    /** Sơ đồ khối (Main.dc.html `vw.nodes`): vòng GG SmartBox #149c4a (chung sáng/tối); ray đường nối #c9cdd2 / #3a4743;
     *  nền nút Tải #eaeee8 / #0e1814. */
    val flowHub: Color,
    val nodesFlowTrack: Color,
    val nodeLoadBg: Color,
    /** Chữ nhãn "CẤP B · NHÀ PHÂN PHỐI" (#8a4b00 trên nền accent-soft, Config.dc.html). */
    val badgeDistributorText: Color,
    /** Khối pin 3D màn 11 (BatteryEnergy.dc.html, chưa có token): nền kính #eef3f8, viền #b3c3d3, cạnh #c9d6e3;
     *  chất lỏng: batteryLiquid*. */
    val batteryGlass: Color,
    val batteryOutline: Color,
    val batteryEdge: Color,
    /** Phản sáng của kính (#ffffff, độ mờ theo từng mặt trong prototype). */
    val batteryHighlight: Color,
    /** Màn 18 · Quét QR (QRScan.dc.html, nền tối cả ở light): nền #0e1512, lớp phủ quanh khung (nền 72 %), lòng khung
     *  trắng 6 %, chữ hướng dẫn #e3e8e1, nút tròn đèn pin trắng 14 %, chữ phụ trong khung #b3bdb7; chữ, icon, góc khung
     *  và nút "Nhập số serial bằng tay" màu trắng #ffffff ([qrContent]). */
    val qrBackground: Color,
    val qrScrim: Color,
    val qrFrameFill: Color,
    val qrHint: Color,
    val qrButton: Color,
    val qrContent: Color,
    /** Chữ / icon trên nút nền [qrContent] (#13201b, giữ tối cả ở giao diện tối). */
    val qrOnContent: Color,
)

// Bảng màu thiết kế mới (GgColors.kt). Các ô ngoài bảng màu (vẽ minh hoạ, QR…) giữ giá trị prototype cũ cho tới khi
// làm lại màn tương ứng.
private fun schemeOf(p: GgPalette, dark: Boolean) = GgColorScheme(
    isDark = dark,
    ggOrange = GgOrange, ggGreen = GgGreen,
    surface = p.surface, surfaceRaised = p.raised, surfaceSunken = p.sunken,
    line = p.line, lineStrong = p.lineStrong,
    ink = p.ink, inkMuted = p.inkMuted,
    primary = p.primary, primaryFill = p.primaryFill, onPrimary = p.onPrimary, primarySoft = p.primarySoft,
    accent = p.accent, onAccent = if (dark) p.surface else p.ink, accentText = p.accentText, accentSoft = p.accentSoft,
    focus = if (dark) PrimaryHoverDark else p.primary,
    success = p.primary, successSoft = p.primarySoft,
    warning = p.warning, warningSoft = p.warningSoft,
    danger = p.danger, dangerSoft = p.dangerSoft,
    info = p.info, infoSoft = p.infoSoft,
    energyPv = p.energyPv, energyBattery = p.energyBattery, energyGrid = p.energyGrid, energyLoad = p.energyLoad,
    cardBg = p.card,
    // Thẻ không còn viền; màu này chỉ còn cho viền thanh thẻ nổi và đường kẻ đặc biệt (#e3e8e1 / #26352f).
    cardBorder = if (dark) p.line else Color(0xFFE3E8E1),
    shadow = if (dark) Color.Black else p.ink,
    touCharge = p.touCharge, touDischarge = p.touDischarge,
    sundayText = p.danger,
    strategy = StrategyColors,
    dangerText = if (dark) p.danger else Color(0xFFB3261E),
    disabledFill = p.disabledFill,
    dayChipOffBg = if (dark) p.sunken else Color(0xFFEEF1EC),
    dayChipOffText = if (dark) p.inkMuted else Color(0xFF9AA59F),
    sliderTick = if (dark) p.lineStrong else Color(0xFFB3BDB7),
    sliderDisabled = if (dark) Color(0xFF3A4743) else Color(0xFFB3BDB7),
    dialogInset = if (dark) p.card else p.surface,
    houseFlowTrack = Color(0xFFC9CDD2),
    houseFlowGreen = Color(0xFF2FD27A),
    co2ChipBg = if (dark) Color(0x8C15221D) else Color(0x8CFFFFFF),
    co2ChipBorder = if (dark) Color(0x5934C26F) else Color(0xB3CFE9D8),
    co2ChipShadow = if (dark) Color(0x38000000) else Color(0x0F13201B),
    toggleOffTrack = if (dark) Color(0xFF0E1814) else p.sunken,
    zoomPillBorder = if (dark) Color(0xFF1F4A30) else Color(0xFFCFE9D8),
    batteryLiquidFront = if (dark) Color(0xFF2FB466) else Color(0xFF149C4A),
    batteryLiquidSide = if (dark) Color(0xFF1F9A52) else Color(0xFF0F7A3A),
    batteryLiquidTop = if (dark) Color(0xFF5BD68C) else Color(0xFF4CC47A),
    batteryLimitLine = p.ink.copy(alpha = if (dark) 0.75f else 0.6f),
    toastBg = if (dark) Color(0xA8EEF3EF) else Color(0x9E13201B),
    onToast = if (dark) p.surface else Color.White,
    scrim = if (dark) Color(0x99000000) else Color(0x800E1512),
    flowHub = Color(0xFF149C4A),
    nodesFlowTrack = if (dark) Color(0xFF3A4743) else Color(0xFFC9CDD2),
    nodeLoadBg = if (dark) Color(0xFF0E1814) else p.sunken,
    badgeDistributorText = if (dark) p.accentText else Color(0xFF8A4B00),
    batteryGlass = if (dark) p.card else Color(0xFFEEF3F8),
    batteryOutline = if (dark) p.lineStrong else Color(0xFFB3C3D3),
    batteryEdge = if (dark) p.line else Color(0xFFC9D6E3),
    batteryHighlight = if (dark) p.ink else Color.White,
    qrBackground = Color(0xFF0E1512),
    qrScrim = Color(0xB80E1512),
    qrFrameFill = Color(0x0FFFFFFF),
    qrHint = Color(0xFFE3E8E1),
    qrButton = Color(0x24FFFFFF),
    qrContent = Color(0xFFFFFFFF),
    qrOnContent = Color(0xFF13201B),
)

private val PrimaryHoverDark = Color(0xFF5BD68C)

val GgLightColors = schemeOf(LightGgPalette, dark = false)
val GgDarkColors = schemeOf(DarkGgPalette, dark = true)

private val LocalGgColors = staticCompositionLocalOf { GgLightColors }
private val LocalGgTypography = staticCompositionLocalOf { GgTypographyDefault }

object GgTheme {
    val colors: GgColorScheme
        @Composable get() = LocalGgColors.current
    val typography: GgTypography
        @Composable get() = LocalGgTypography.current
}

private const val MAX_FONT_SCALE = 1.3f

@Composable
fun GgPowerTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    // Dấu thập phân / dấu nghìn theo ngôn ngữ app (đặc tả mục 5). Đổi ngôn ngữ thì Activity dựng lại nên đặt ở đây là đủ.
    val language = AppLanguage.fromTag(LocalConfiguration.current.locales[0]?.toLanguageTag())
    GgFormat.defaultStyle = NumberStyle.of(language)
    val colors = if (darkTheme) GgDarkColors else GgLightColors
    // Material 3 chỉ dùng cho component có sẵn (dialog, sheet, text field…); màu vẫn lấy từ token.
    val material = if (darkTheme) {
        darkColorScheme(
            primary = colors.primaryFill, onPrimary = colors.onPrimary,
            background = colors.surface, onBackground = colors.ink,
            surface = colors.surfaceRaised, onSurface = colors.ink,
            surfaceVariant = colors.surfaceSunken, onSurfaceVariant = colors.inkMuted,
            outline = colors.lineStrong, outlineVariant = colors.line,
            error = colors.danger,
        )
    } else {
        lightColorScheme(
            primary = colors.primaryFill, onPrimary = colors.onPrimary,
            background = colors.surface, onBackground = colors.ink,
            surface = colors.surfaceRaised, onSurface = colors.ink,
            surfaceVariant = colors.surfaceSunken, onSurfaceVariant = colors.inkMuted,
            outline = colors.lineStrong, outlineVariant = colors.line,
            error = colors.danger,
        )
    }
    // Cỡ chữ hệ thống lớn hơn 130 % làm vỡ các ô số liệu và nhãn biểu đồ (bố cục thiết kế cho khổ 390 dp), nên app
    // chỉ phóng chữ tới 130 %. Xem OPEN_QUESTIONS (06/10/2026): cần thiết kế riêng nếu muốn hỗ trợ cỡ lớn hơn.
    val density = LocalDensity.current
    val cappedDensity = if (density.fontScale > MAX_FONT_SCALE) Density(density.density, MAX_FONT_SCALE) else density
    CompositionLocalProvider(
        LocalGgColors provides colors,
        LocalGgTypography provides GgTypographyDefault,
        LocalDensity provides cappedDensity,
    ) {
        MaterialTheme(colorScheme = material, content = content)
    }
}

