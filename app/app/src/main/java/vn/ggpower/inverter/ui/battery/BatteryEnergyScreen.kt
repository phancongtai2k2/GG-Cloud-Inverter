package vn.ggpower.inverter.ui.battery

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.delay
import vn.ggpower.inverter.R
import vn.ggpower.inverter.data.battery.FakeBatteryEnergyRepository
import vn.ggpower.inverter.domain.battery.BatteryEnergy
import vn.ggpower.inverter.domain.battery.activity
import vn.ggpower.inverter.domain.battery.availableKwh
import vn.ggpower.inverter.domain.format.GgFormat
import vn.ggpower.inverter.domain.home.BatteryActivity
import vn.ggpower.inverter.domain.settings.TemperatureUnit
import vn.ggpower.inverter.ui.components.ButtonSize
import vn.ggpower.inverter.ui.components.GgCard
import vn.ggpower.inverter.ui.components.Metric
import vn.ggpower.inverter.ui.components.MetricRow
import vn.ggpower.inverter.ui.components.ScreenHeader
import vn.ggpower.inverter.ui.components.SecondaryButton
import vn.ggpower.inverter.ui.components.StatusChip
import vn.ggpower.inverter.ui.components.StatusTone
import vn.ggpower.inverter.ui.format.temperatureText
import vn.ggpower.inverter.ui.theme.GgPowerTheme
import vn.ggpower.inverter.ui.theme.GgRadius
import vn.ggpower.inverter.ui.theme.GgSpacing
import vn.ggpower.inverter.ui.theme.GgTheme

@Composable
fun BatteryEnergyRoute(onBack: () -> Unit, viewModel: BatteryEnergyViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val temperatureUnit by viewModel.temperatureUnit.collectAsStateWithLifecycle()
    BatteryEnergyScreen(state, onBack, viewModel::retry, temperatureUnit = temperatureUnit)
}

/**
 * Màn 11 · Nguồn năng lượng pin (BatteryEnergy.png; đặc tả §3.11). Mở từ box Pin S16 Pre ở Màn hình chính, quay
 * lại về Màn hình chính. Tỷ lệ theo nguồn do cloud/EMS tính, app chỉ hiển thị.
 */
@Composable
fun BatteryEnergyScreen(
    state: BatteryEnergyUiState,
    onBack: () -> Unit,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
    temperatureUnit: TemperatureUnit = TemperatureUnit.Celsius,
) {
    val colors = GgTheme.colors
    Column(modifier.fillMaxSize().background(colors.surface).statusBarsPadding()) {
        ScreenHeader(title = stringResource(R.string.screen_battery_energy), onBack = onBack)
        when (state) {
            BatteryEnergyUiState.Loading -> LoadingBlocks()
            is BatteryEnergyUiState.Error -> ErrorBlock(state.noConnection, onRetry)
            is BatteryEnergyUiState.Content -> Content(state.energy, temperatureUnit)
        }
    }
}

@Composable
private fun Content(e: BatteryEnergy, temperatureUnit: TemperatureUnit) {
    val kwh = stringResource(R.string.unit_kwh)
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .navigationBarsPadding()
            .padding(start = GgSpacing.space5, end = GgSpacing.space5, bottom = GgSpacing.space4),
        verticalArrangement = Arrangement.spacedBy(GgSpacing.space3),
    ) {
        NameRow(e)
        Row(horizontalArrangement = Arrangement.spacedBy(GgSpacing.space3)) {
            BigStat(e.socPercent?.let { GgFormat.number(it, 0) }, stringResource(R.string.unit_percent), stringResource(R.string.bat_soc), Modifier.weight(1f))
            BigStat(e.availableKwh?.let { GgFormat.number(it, 1) }, kwh, stringResource(R.string.energy_available), Modifier.weight(1f))
        }
        BatteryFigure(e)
        Params(e, temperatureUnit)
    }
}

/** Tên pin 13/20 600 + chip "Đang sạc +1,1 kW" / "Đang xả -0,8 kW" / "Không sạc/xả" (BO_SUNG §16). */
@Composable
private fun NameRow(e: BatteryEnergy) {
    val kw = stringResource(R.string.unit_kw)
    val power = e.powerKw?.let { GgFormat.signedWithUnit(it, 1, kw) }.orEmpty()
    val (text, tone) = when (e.activity) {
        BatteryActivity.Charging -> stringResource(R.string.energy_chip_charging, power) to StatusTone.Success
        BatteryActivity.Discharging -> stringResource(R.string.energy_chip_discharging, power) to StatusTone.Info
        BatteryActivity.Idle -> stringResource(R.string.battery_idle) to StatusTone.Neutral
    }
    Row(horizontalArrangement = Arrangement.spacedBy(GgSpacing.space2), verticalAlignment = Alignment.CenterVertically) {
        Text(e.name, style = GgTheme.typography.bodySm.copy(fontWeight = FontWeight.SemiBold), color = GgTheme.colors.ink)
        StatusChip(text, tone)
    }
}

/** Chỉ số lớn: số 24/28 600 + đơn vị 12/16 500 `inkMuted` cách 4, chung đường chân chữ; nhãn 13/20 `inkMuted`. */
@Composable
private fun BigStat(value: String?, unit: String, label: String, modifier: Modifier = Modifier) {
    val colors = GgTheme.colors
    Column(modifier, verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(GgSpacing.space1)) {
            Text(
                value ?: stringResource(R.string.value_missing),
                style = GgTheme.typography.body.copy(fontSize = 24.sp, lineHeight = 28.sp, fontWeight = FontWeight.SemiBold, fontFeatureSettings = "tnum"),
                color = colors.ink,
                modifier = Modifier.alignByBaseline(),
            )
            if (value != null) Text(unit, style = GgTheme.typography.caption, color = colors.inkMuted, modifier = Modifier.alignByBaseline())
        }
        Text(label, style = GgTheme.typography.bodySm, color = colors.inkMuted)
    }
}

// ---------- Khối pin 3D ----------

/** Khung của BatteryEnergy.dc.html (viewBox −42 24 350 320): mặt trước x 80–220, đáy 300, cao 240; chiều sâu (44, −26). */
private const val VIEW_X = -42f
private const val VIEW_W = 350f
private const val VIEW_TOP = 24f
private const val VIEW_H = 320f
private const val FRONT_L = 80f
private const val FRONT_R = 220f
private const val BOTTOM = 300f
private const val HEIGHT = 240f
private const val DX = 44f
private const val DY = 26f

/** Nhãn vạch giới hạn canh phải tại x 62, vạch ngắn 66–78. */
private const val LIMIT_LABEL_X = 62f

/**
 * Sóng sánh khi mở màn (prototype: `<animate>` 2,6 s, bắt đầu sau 0,15 s, spline 0.4 0 0.6 1 giữa các mốc): độ lệch của
 * mép trái mặt chất lỏng; mép phải lệch ngược dấu. Kết thúc phẳng.
 */
private val SLOSH_TIMES = floatArrayOf(0f, 0.1f, 0.25f, 0.4f, 0.55f, 0.7f, 0.85f, 1f)
private val SLOSH_OFFSETS = floatArrayOf(0f, 10f, -8f, 6f, -4f, 2.5f, -1.2f, 0f)
private val SloshEasing = CubicBezierEasing(0.4f, 0f, 0.6f, 1f)
private const val SLOSH_MS = 2600
private const val SLOSH_DELAY_MS = 150L

/** Độ lệch mép trái của mặt chất lỏng tại tiến độ [t] (0..1) của sóng sánh. */
internal fun sloshOffset(t: Float): Float {
    if (t <= 0f || t >= 1f) return 0f
    val i = SLOSH_TIMES.indexOfLast { it <= t }.coerceAtMost(SLOSH_TIMES.size - 2)
    val local = (t - SLOSH_TIMES[i]) / (SLOSH_TIMES[i + 1] - SLOSH_TIMES[i])
    return SLOSH_OFFSETS[i] + (SLOSH_OFFSETS[i + 1] - SLOSH_OFFSETS[i]) * SloshEasing.transform(local)
}

private val OUTLINE = listOf(
    "M" to floatArrayOf(92.1f, 52.9f), "L" to floatArrayOf(111.9f, 41.1f), "Q" to floatArrayOf(124f, 34f, 138f, 34f),
    "L" to floatArrayOf(250f, 34f), "Q" to floatArrayOf(264f, 34f, 264f, 48f), "L" to floatArrayOf(264f, 260f),
    "Q" to floatArrayOf(264f, 274f, 251.9f, 281.1f), "L" to floatArrayOf(232.1f, 292.9f), "Q" to floatArrayOf(220f, 300f, 206f, 300f),
    "L" to floatArrayOf(94f, 300f), "Q" to floatArrayOf(80f, 300f, 80f, 286f), "L" to floatArrayOf(80f, 74f),
    "Q" to floatArrayOf(80f, 60f, 92.1f, 52.9f),
)

private fun outlinePath() = Path().apply {
    OUTLINE.forEach { (cmd, p) ->
        when (cmd) {
            "M" -> moveTo(p[0], p[1])
            "L" -> lineTo(p[0], p[1])
            else -> quadraticTo(p[0], p[1], p[2], p[3])
        }
    }
    close()
}

private fun poly(vararg p: Float) = Path().apply {
    moveTo(p[0], p[1])
    for (i in 2 until p.size step 2) lineTo(p[i], p[i + 1])
    close()
}

/**
 * Khối pin kính 3D (gg-inverter-design BatteryEnergy.dc.html): chất lỏng xanh tới mức SOC, mặt chất lỏng sóng sánh tắt
 * dần khi mở màn; hai vạch nét đứt giới hạn sạc / xả từ cấu hình BMS, nhãn bên trái (12/700 + 10/500). Vẽ theo viewBox
 * của prototype, co giãn theo bề rộng.
 */
@Composable
private fun BatteryFigure(e: BatteryEnergy) {
    val colors = GgTheme.colors
    val measurer = rememberTextMeasurer()
    val kwh = stringResource(R.string.unit_kwh)
    val pct = stringResource(R.string.unit_percent)
    val chargeName = stringResource(R.string.energy_charge_limit)
    val dischargeName = stringResource(R.string.energy_discharge_limit)
    val charge = e.chargeLimitSocPercent
    val discharge = e.dischargeLimitSocPercent
    val capacity = e.content?.capacityKwh
    val soc = e.socPercent
    val cd = if (soc != null && capacity != null) stringResource(
        R.string.energy_figure_cd, e.name, GgFormat.trimmed(capacity, 1) + " " + kwh, GgFormat.withUnit(soc, 0, pct),
        GgFormat.withUnit(e.availableKwh ?: 0.0, 1, kwh),
        if (charge != null && discharge != null) stringResource(
            R.string.energy_figure_cd_limits, GgFormat.withUnit(charge.toLong(), pct), GgFormat.withUnit(discharge.toLong(), pct),
        ) else "",
        stringResource(
            when (e.activity) {
                BatteryActivity.Charging -> R.string.energy_cd_charging
                BatteryActivity.Discharging -> R.string.energy_cd_discharging
                BatteryActivity.Idle -> R.string.energy_cd_idle
            },
        ),
    ) else stringResource(R.string.energy_figure_cd_unknown, e.name)
    val font = GgTheme.typography.caption.fontFamily
    val limitValueStyle = TextStyle(fontFamily = font, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = colors.ink, fontFeatureSettings = "tnum")
    val limitNameStyle = TextStyle(fontFamily = font, fontSize = 10.sp, fontWeight = FontWeight.Medium, color = colors.inkMuted)
    val level = ((soc ?: 0.0) / 100).coerceIn(0.0, 1.0).toFloat()
    val slosh = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        delay(SLOSH_DELAY_MS)
        slosh.animateTo(1f, tween(SLOSH_MS, easing = LinearEasing))
    }
    Canvas(
        Modifier
            .fillMaxWidth()
            .aspectRatio(VIEW_W / VIEW_H)
            .semantics { contentDescription = cd },
    ) {
        val s = size.width / VIEW_W
        withTransform({
            scale(s, s, Offset.Zero)
            translate(-VIEW_X, -VIEW_TOP)
        }) {
            val outline = outlinePath()
            clipPath(outline) {
                drawPath(poly(FRONT_L, BOTTOM, FRONT_L, 60f, 124f, 34f, 264f, 34f, 264f, 274f, FRONT_R, BOTTOM), colors.batteryGlass)
                if (level > 0f) {
                    val top = BOTTOM - HEIGHT * level
                    val a = sloshOffset(slosh.value)
                    val l = top + a
                    val r = top - a
                    drawPath(poly(FRONT_L, l, FRONT_R, r, FRONT_R, BOTTOM, FRONT_L, BOTTOM), colors.batteryLiquidFront)
                    drawPath(poly(FRONT_R, BOTTOM, FRONT_R + DX, BOTTOM - DY, FRONT_R + DX, r - DY, FRONT_R, r), colors.batteryLiquidSide)
                    drawPath(poly(FRONT_L, l, FRONT_R, r, FRONT_R + DX, r - DY, FRONT_L + DX, l - DY), colors.batteryLiquidTop)
                }
                drawRect(
                    Brush.horizontalGradient(listOf(colors.batteryHighlight.copy(alpha = 0.35f), colors.batteryHighlight.copy(alpha = 0.08f)), FRONT_L, FRONT_R),
                    topLeft = Offset(FRONT_L, 60f), size = androidx.compose.ui.geometry.Size(FRONT_R - FRONT_L, BOTTOM - 60f),
                )
                drawPath(poly(FRONT_R, BOTTOM, 264f, 274f, 264f, 34f, FRONT_R, 60f), colors.batteryHighlight.copy(alpha = 0.1f))
                drawPath(poly(FRONT_L, 60f, FRONT_R, 60f, 264f, 34f, 124f, 34f), colors.batteryHighlight.copy(alpha = 0.55f))
                drawPath(
                    Path().apply { moveTo(84f, 60f); lineTo(216f, 60f); lineTo(258f, 35f) },
                    colors.batteryHighlight.copy(alpha = 0.9f),
                    style = Stroke(1.5f, cap = StrokeCap.Round, join = StrokeJoin.Round),
                )
                drawLine(colors.batteryEdge, Offset(FRONT_R, 66f), Offset(FRONT_R, 294f), 1f, StrokeCap.Round)
                drawLine(colors.batteryEdge, Offset(86f, 60f), Offset(216f, 60f), 1f, StrokeCap.Round)
            }
            drawPath(outline, colors.batteryOutline, style = Stroke(1.5f, join = StrokeJoin.Round))
            listOfNotNull(charge, discharge).forEach { limit ->
                val y = BOTTOM - HEIGHT * limit.coerceIn(0, 100) / 100f
                drawPath(
                    Path().apply { moveTo(FRONT_L, y); lineTo(FRONT_R, y); lineTo(FRONT_R + DX, y - DY) },
                    colors.batteryLimitLine,
                    style = Stroke(1.5f, pathEffect = PathEffect.dashPathEffect(floatArrayOf(5f, 4f))),
                )
                drawLine(colors.batteryLimitLine, Offset(66f, y), Offset(78f, y), 1.5f)
            }
        }
        fun text(t: String, style: TextStyle, baseline: Float) {
            val l = measurer.measure(t, style)
            drawText(l, topLeft = Offset((LIMIT_LABEL_X - VIEW_X) * s - l.size.width, (baseline - VIEW_TOP) * s - l.firstBaseline))
        }
        listOfNotNull(charge?.let { it to chargeName }, discharge?.let { it to dischargeName }).forEach { (limit, name) ->
            val y = BOTTOM - HEIGHT * limit.coerceIn(0, 100) / 100f
            text(GgFormat.withUnit(limit.toLong(), pct), limitValueStyle, y + 4)
            text(name, limitNameStyle, y + 17)
        }
    }
}

// ---------- Thông số ----------

/**
 * Thông số pin: lưới 3 cột [MetricRow]. Pin đang xả thì nhãn
 * "Dòng xả" và giá trị dương (BO_SUNG §16).
 */
@Composable
private fun Params(e: BatteryEnergy, temperatureUnit: TemperatureUnit) {
    val dash = stringResource(R.string.value_missing)
    val discharging = e.activity == BatteryActivity.Discharging
    val items = listOf(
        stringResource(R.string.device_voltage) to e.voltageV?.let { GgFormat.withUnit(it, 1, stringResource(R.string.unit_volt)) },
        stringResource(if (discharging) R.string.energy_discharge_current else R.string.energy_charge_current) to
            e.currentA?.let { GgFormat.withUnit(kotlin.math.abs(it), 1, stringResource(R.string.unit_ampere)) },
        stringResource(R.string.device_temperature) to e.temperatureC?.let { temperatureText(it, temperatureUnit) },
        stringResource(R.string.device_soh) to e.sohPercent?.let { GgFormat.withUnit(it, 0, stringResource(R.string.unit_percent)) },
        stringResource(R.string.energy_cycles) to e.cycles?.let { GgFormat.number(it.toLong()) },
        stringResource(R.string.bat_cell_delta) to e.cellDeltaMv?.let { GgFormat.withUnit(it, 0, stringResource(R.string.unit_millivolt)) },
    )
    GgCard(verticalSpacing = GgSpacing.space3) {
        Text(
            stringResource(R.string.energy_params),
            style = GgTheme.typography.body.copy(fontSize = 15.sp, lineHeight = 22.sp, fontWeight = FontWeight.SemiBold),
            color = GgTheme.colors.ink,
        )
        items.chunked(3).forEach { row -> MetricRow(row.map { (label, value) -> Metric(label, value ?: dash) }, columns = 3) }
    }
}

@Composable
private fun LoadingBlocks() {
    val sunken = GgTheme.colors.surfaceSunken
    Column(Modifier.padding(horizontal = GgSpacing.space5), verticalArrangement = Arrangement.spacedBy(GgSpacing.space3)) {
        Box(Modifier.fillMaxWidth(0.6f).height(24.dp).clip(RoundedCornerShape(GgRadius.radiusSm)).background(sunken))
        Box(Modifier.fillMaxWidth().height(320.dp).clip(RoundedCornerShape(GgRadius.radiusLg)).background(sunken))
        GgCard { Box(Modifier.fillMaxWidth().height(110.dp).clip(RoundedCornerShape(GgRadius.radiusMd)).background(sunken)) }
    }
}

@Composable
private fun ColumnScope.ErrorBlock(noConnection: Boolean, onRetry: () -> Unit) {
    Column(
        Modifier.weight(1f).fillMaxWidth().padding(horizontal = GgSpacing.space8),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(GgSpacing.space4, Alignment.CenterVertically),
    ) {
        Text(
            stringResource(if (noConnection) R.string.error_no_internet else R.string.error_server),
            style = GgTheme.typography.body,
            color = GgTheme.colors.inkMuted,
            textAlign = TextAlign.Center,
        )
        SecondaryButton(stringResource(R.string.action_retry), onClick = onRetry, size = ButtonSize.Medium)
    }
}

@Preview(widthDp = 390, heightDp = 844, showBackground = true)
@Composable
private fun BatteryEnergyPreview() {
    GgPowerTheme(darkTheme = false) {
        BatteryEnergyScreen(BatteryEnergyUiState.Content(FakeBatteryEnergyRepository.SAMPLE), {}, {})
    }
}

@Preview(widthDp = 390, heightDp = 844, showBackground = true)
@Composable
private fun BatteryEnergyDischargingPreview() {
    GgPowerTheme(darkTheme = false) {
        BatteryEnergyScreen(BatteryEnergyUiState.Content(FakeBatteryEnergyRepository.SAMPLE.copy(powerKw = -0.8, currentA = -15.6)), {}, {})
    }
}
