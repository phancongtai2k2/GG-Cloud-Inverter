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

/** Bộ màu đang dùng (light hoặc dark), lấy từ [GgLight] / [GgDark]. Màn hình chỉ đọc màu qua `GgTheme.colors`. */
@Immutable
data class GgColorScheme(
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
    /** Màu bóng; dark không đổ bóng (tokens.md: shadow-card dark = none). */
    val shadow: Color,
    /** Đoạn sạc / xả trên thanh 24h (tokens.md: tou-charge = energy-pv, tou-discharge = energy-battery). */
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
    /** Lớp phủ sau bottom sheet / dialog: rgba(14,21,18,0.5) (TouStrategy.dc.html). */
    val scrim: Color,
    /** Sơ đồ dòng năng lượng (Main.dc.html): vòng GG SmartBox #60eb98 (chưa có token); tia sáng: quầng #34c26f, lõi #5bd68c
     *  (giá trị primary / primary-hover của token dark, prototype dùng cả ở light). */
    val flowHub: Color,
    val flowGlow: Color,
    val flowHead: Color,
    /** Chữ nhãn "CẤP B · NHÀ PHÂN PHỐI" (#8a4b00 trên nền accent-soft, Config.dc.html). */
    val badgeDistributorText: Color,
    /** Khối pin 3D màn 11 (BatteryEnergy.dc.html, chưa có token): nền kính #eef3f8, viền #b3c3d3, cạnh #c9d6e3;
     *  mặt bên / mặt trên lớp PV #b85a0a / #f7a452, lớp lưới #24588f / #6fa8e8 (mặt trước dùng energyPv / energyGrid). */
    val batteryGlass: Color,
    val batteryOutline: Color,
    val batteryEdge: Color,
    val batteryPvSide: Color,
    val batteryPvTop: Color,
    val batteryGridSide: Color,
    val batteryGridTop: Color,
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

val GgLightColors = GgColorScheme(
    ggOrange = GgLight.ggOrange, ggGreen = GgLight.ggGreen,
    surface = GgLight.surface, surfaceRaised = GgLight.surfaceRaised, surfaceSunken = GgLight.surfaceSunken,
    line = GgLight.line, lineStrong = GgLight.lineStrong,
    ink = GgLight.ink, inkMuted = GgLight.inkMuted,
    primary = GgLight.primary, onPrimary = GgLight.onPrimary, primarySoft = GgLight.primarySoft,
    accent = GgLight.accent, onAccent = GgLight.onAccent, accentText = GgLight.accentText, accentSoft = GgLight.accentSoft,
    focus = GgLight.focus,
    success = GgLight.success, successSoft = GgLight.successSoft,
    warning = GgLight.warning, warningSoft = GgLight.warningSoft,
    danger = GgLight.danger, dangerSoft = GgLight.dangerSoft,
    info = GgLight.info, infoSoft = GgLight.infoSoft,
    energyPv = GgLight.energyPv, energyBattery = GgLight.energyBattery,
    energyGrid = GgLight.energyGrid, energyLoad = GgLight.energyLoad,
    cardBg = GgLight.cardBg, cardBorder = GgLight.cardBorder,
    shadow = GgLight.ink,
    touCharge = GgLight.energyPv, touDischarge = GgLight.energyBattery,
    sundayText = GgLight.danger,
    strategy = StrategyColors,
    dangerText = Color(0xFFB3261E),
    disabledFill = Color(0xFFC9D1CB),
    dayChipOffBg = Color(0xFFEEF1EC),
    dayChipOffText = Color(0xFF9AA59F),
    sliderTick = Color(0xFFB3BDB7),
    scrim = Color(0x800E1512),
    flowHub = Color(0xFF60EB98),
    flowGlow = GgDark.primary,
    flowHead = GgDark.primaryHover,
    badgeDistributorText = Color(0xFF8A4B00),
    batteryGlass = Color(0xFFEEF3F8),
    batteryOutline = Color(0xFFB3C3D3),
    batteryEdge = Color(0xFFC9D6E3),
    batteryPvSide = Color(0xFFB85A0A),
    batteryPvTop = Color(0xFFF7A452),
    batteryGridSide = Color(0xFF24588F),
    batteryGridTop = Color(0xFF6FA8E8),
    batteryHighlight = Color.White,
    qrBackground = Color(0xFF0E1512),
    qrScrim = Color(0xB80E1512),
    qrFrameFill = Color(0x0FFFFFFF),
    qrHint = Color(0xFFE3E8E1),
    qrButton = Color(0x24FFFFFF),
    qrContent = Color(0xFFFFFFFF),
    qrOnContent = Color(0xFF13201B),
)

// Token dark không có card-bg / card-border: thẻ tách bằng surface-raised + line (tokens.md, shadow-card).
val GgDarkColors = GgColorScheme(
    ggOrange = GgDark.ggOrange, ggGreen = GgDark.ggGreen,
    surface = GgDark.surface, surfaceRaised = GgDark.surfaceRaised, surfaceSunken = GgDark.surfaceSunken,
    line = GgDark.line, lineStrong = GgDark.lineStrong,
    ink = GgDark.ink, inkMuted = GgDark.inkMuted,
    primary = GgDark.primary, onPrimary = GgDark.onPrimary, primarySoft = GgDark.primarySoft,
    accent = GgDark.accent, onAccent = GgDark.onAccent, accentText = GgDark.accentText, accentSoft = GgDark.accentSoft,
    focus = GgDark.focus,
    success = GgDark.success, successSoft = GgDark.successSoft,
    warning = GgDark.warning, warningSoft = GgDark.warningSoft,
    danger = GgDark.danger, dangerSoft = GgDark.dangerSoft,
    info = GgDark.info, infoSoft = GgDark.infoSoft,
    energyPv = GgDark.energyPv, energyBattery = GgDark.energyBattery,
    energyGrid = GgDark.energyGrid, energyLoad = GgDark.energyLoad,
    cardBg = GgDark.surfaceRaised, cardBorder = GgDark.line,
    shadow = Color.Transparent,
    touCharge = GgDark.energyPv, touDischarge = GgDark.energyBattery,
    sundayText = GgDark.danger,
    strategy = StrategyColors,
    // Chưa có thiết kế dark cho các giá trị dưới: tạm lấy token dark gần nghĩa nhất.
    dangerText = GgDark.danger,
    disabledFill = GgDark.lineStrong,
    dayChipOffBg = GgDark.surfaceSunken,
    dayChipOffText = GgDark.inkMuted,
    sliderTick = GgDark.lineStrong,
    scrim = Color(0x99000000),
    flowHub = GgDark.primary,
    flowGlow = GgDark.primary,
    flowHead = GgDark.primaryHover,
    badgeDistributorText = GgDark.accentText,
    batteryGlass = GgDark.surfaceRaised,
    batteryOutline = GgDark.lineStrong,
    batteryEdge = GgDark.line,
    batteryPvSide = Color(0xFFB85A0A),
    batteryPvTop = Color(0xFFF7A452),
    batteryGridSide = Color(0xFF24588F),
    batteryGridTop = GgLight.energyGrid,
    batteryHighlight = GgDark.ink,
    qrBackground = Color(0xFF0E1512),
    qrScrim = Color(0xB80E1512),
    qrFrameFill = Color(0x0FFFFFFF),
    qrHint = Color(0xFFE3E8E1),
    qrButton = Color(0x24FFFFFF),
    qrContent = Color(0xFFFFFFFF),
    qrOnContent = Color(0xFF13201B),
)

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
            primary = colors.primary, onPrimary = colors.onPrimary,
            background = colors.surface, onBackground = colors.ink,
            surface = colors.surfaceRaised, onSurface = colors.ink,
            surfaceVariant = colors.surfaceSunken, onSurfaceVariant = colors.inkMuted,
            outline = colors.lineStrong, outlineVariant = colors.line,
            error = colors.danger,
        )
    } else {
        lightColorScheme(
            primary = colors.primary, onPrimary = colors.onPrimary,
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

