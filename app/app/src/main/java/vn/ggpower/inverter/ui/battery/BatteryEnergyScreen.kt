package vn.ggpower.inverter.ui.battery

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
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
import vn.ggpower.inverter.R
import vn.ggpower.inverter.data.battery.FakeBatteryEnergyRepository
import vn.ggpower.inverter.domain.battery.BatteryEnergy
import vn.ggpower.inverter.domain.settings.TemperatureUnit
import vn.ggpower.inverter.ui.format.temperatureText
import vn.ggpower.inverter.domain.battery.EnergyShares
import vn.ggpower.inverter.domain.battery.activity
import vn.ggpower.inverter.domain.battery.shares
import vn.ggpower.inverter.domain.format.GgFormat
import vn.ggpower.inverter.domain.home.BatteryActivity
import vn.ggpower.inverter.ui.components.ButtonSize
import vn.ggpower.inverter.ui.components.GgCard
import vn.ggpower.inverter.ui.components.Metric
import vn.ggpower.inverter.ui.components.MetricRow
import vn.ggpower.inverter.ui.components.ScreenHeader
import vn.ggpower.inverter.ui.components.SecondaryButton
import vn.ggpower.inverter.ui.components.StatusChip
import vn.ggpower.inverter.ui.components.StatusTone
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
    val shares = e.content?.shares()
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
            BigStat(shares?.let { GgFormat.number(it.availableKwh, 1) }, kwh, stringResource(R.string.energy_available), Modifier.weight(1f))
        }
        BatteryFigure(e, shares)
        Legend(shares)
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

/** Khung của BatteryEnergy.dc.html (viewBox 0 24 350 320): mặt trước x 80–220, đáy 300, cao 240; chiều sâu (44, -26). */
private const val VIEW_W = 350f
private const val VIEW_TOP = 24f
private const val VIEW_H = 320f
private const val FRONT_L = 80f
private const val FRONT_R = 220f
private const val BOTTOM = 300f
private const val HEIGHT = 240f
private const val DX = 44f
private const val DY = 26f
private const val LABEL_X = 282f

/** Tâm nhãn không xuống quá 268 (prototype: nhãn "Từ lưới" ở 268) và hai nhãn cách nhau tối thiểu 52 (khối chữ cao ~42). */
private const val LABEL_MAX_Y = 268f
private const val LABEL_MIN_GAP = 52f

/**
 * Tung độ (toạ độ viewBox) của vạch dẫn nhãn lớp PV và lớp lưới; null khi lớp rỗng. Vạch đặt giữa lớp, đo tại giữa
 * mặt bên (giữa mặt trước lùi lên 13); prototype: PV 182, lưới 268 (bị chặn dưới).
 */
internal fun labelLines(pvFraction: Double, gridFraction: Double): Pair<Float?, Float?> {
    val gridTop = BOTTOM - HEIGHT * gridFraction.toFloat()
    val pvTop = gridTop - HEIGHT * pvFraction.toFloat()
    val grid = if (gridFraction > 0) minOf((gridTop + BOTTOM) / 2 - DY / 2, LABEL_MAX_Y) else null
    var pv = if (pvFraction > 0) minOf((pvTop + gridTop) / 2 - DY / 2, LABEL_MAX_Y) else null
    if (pv != null && grid != null && grid - pv < LABEL_MIN_GAP) pv = grid - LABEL_MIN_GAP
    return pv to grid
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

/** Một lớp năng lượng từ đáy [bottom] lên đỉnh [top]: mặt trước, mặt bên, mặt trên. */
private fun DrawScope.layer(top: Float, bottom: Float, front: Color, side: Color, lid: Color) {
    drawPath(poly(FRONT_L, top, FRONT_R, top, FRONT_R, bottom, FRONT_L, bottom), front)
    drawPath(poly(FRONT_R, bottom, FRONT_R + DX, bottom - DY, FRONT_R + DX, top - DY, FRONT_R, top), side)
    drawPath(poly(FRONT_L, top, FRONT_R, top, FRONT_R + DX, top - DY, FRONT_L + DX, top - DY), lid)
}

/**
 * Khối pin kính 3D (đặc tả §3.11): lớp dưới Từ lưới (xanh dương), lớp trên Từ PV (cam), phần trên trống; nhãn bên
 * phải gồm nguồn 11/600, năng lượng 13/700, phần trăm 11/500. Vẽ theo viewBox 350 × 320 của prototype, co giãn theo
 * bề rộng.
 */
@Composable
private fun BatteryFigure(e: BatteryEnergy, shares: EnergyShares?) {
    val colors = GgTheme.colors
    val measurer = rememberTextMeasurer()
    val kwh = stringResource(R.string.unit_kwh)
    val pct = stringResource(R.string.unit_percent)
    val pvName = stringResource(R.string.battery_from_pv)
    val gridName = stringResource(R.string.battery_from_grid)
    val cd = if (shares != null && e.content != null) stringResource(
        R.string.energy_figure_cd, e.name,
        GgFormat.trimmed(e.content.capacityKwh, 1) + " " + kwh, GgFormat.withUnit(shares.availableKwh, 1, kwh),
        GgFormat.withUnit(shares.pvKwh, 1, kwh), GgFormat.withUnit(shares.pvPercent, 1, pct),
        GgFormat.withUnit(shares.gridKwh, 1, kwh), GgFormat.withUnit(shares.gridPercent, 1, pct),
        stringResource(
            when (e.activity) {
                BatteryActivity.Charging -> R.string.energy_cd_charging
                BatteryActivity.Discharging -> R.string.energy_cd_discharging
                BatteryActivity.Idle -> R.string.energy_cd_idle
            },
        ),
    ) else stringResource(R.string.energy_figure_cd_unknown, e.name)
    val font = GgTheme.typography.caption.fontFamily
    val nameStyle = TextStyle(fontFamily = font, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
    val valueStyle = TextStyle(fontFamily = font, fontSize = 13.sp, fontWeight = FontWeight.Bold, color = colors.ink, fontFeatureSettings = "tnum")
    val pctStyle = TextStyle(fontFamily = font, fontSize = 11.sp, fontWeight = FontWeight.Medium, color = colors.inkMuted, fontFeatureSettings = "tnum")
    // Lớp và nhãn theo kWh / dung lượng (không dùng phần trăm đã làm tròn), như prototype.
    val capacity = e.content?.capacityKwh
    val pvExact = if (shares != null && capacity != null) (shares.pvKwh / capacity).toFloat() else 0f
    val gridExact = if (shares != null && capacity != null) (shares.gridKwh / capacity).toFloat() else 0f
    val (pvLine, gridLine) = labelLines(pvExact.toDouble(), gridExact.toDouble())
    Canvas(
        Modifier
            .fillMaxWidth()
            .aspectRatio(VIEW_W / VIEW_H)
            .semantics { contentDescription = cd },
    ) {
        val s = size.width / VIEW_W
        withTransform({
            scale(s, s, Offset.Zero)
            translate(0f, -VIEW_TOP)
        }) {
            val outline = outlinePath()
            clipPath(outline) {
                drawPath(poly(FRONT_L, BOTTOM, FRONT_L, 60f, 124f, 34f, 264f, 34f, 264f, 274f, FRONT_R, BOTTOM), colors.batteryGlass)
                val gridTop = BOTTOM - HEIGHT * gridExact
                val pvTop = gridTop - HEIGHT * pvExact
                if (gridExact > 0) layer(gridTop, BOTTOM, colors.energyGrid, colors.batteryGridSide, colors.batteryGridTop)
                if (pvExact > 0) layer(pvTop, gridTop, colors.energyPv, colors.batteryPvSide, colors.batteryPvTop)
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
            listOfNotNull(pvLine, gridLine).forEach { y -> drawLine(colors.lineStrong, Offset(264f, y), Offset(278f, y), 1f) }
        }
        fun text(t: String, style: TextStyle, baseline: Float) {
            val l = measurer.measure(t, style)
            drawText(l, topLeft = Offset(LABEL_X * s, (baseline - VIEW_TOP) * s - l.firstBaseline))
        }
        if (shares != null) {
            pvLine?.let {
                text(pvName, nameStyle.copy(color = colors.accentText), it - 12)
                text(GgFormat.withUnit(shares.pvKwh, 1, kwh), valueStyle, it + 4)
                text(GgFormat.withUnit(shares.pvPercent, 1, pct), pctStyle, it + 19)
            }
            gridLine?.let {
                text(gridName, nameStyle.copy(color = colors.energyGrid), it - 12)
                text(GgFormat.withUnit(shares.gridKwh, 1, kwh), valueStyle, it + 4)
                text(GgFormat.withUnit(shares.gridPercent, 1, pct), pctStyle, it + 19)
            }
        }
    }
}

// ---------- Bảng chi tiết và thông số ----------

/** Thẻ đệm 4 16; dòng đệm 10, cách 10: ô màu 12 bo 3; tên 13/20; kWh 13/20 600; phần trăm rộng 56 12/16 500. */
@Composable
private fun Legend(shares: EnergyShares?) {
    val colors = GgTheme.colors
    val kwh = stringResource(R.string.unit_kwh)
    val pct = stringResource(R.string.unit_percent)
    val dash = stringResource(R.string.value_missing)
    val rows = listOf(
        Triple(colors.energyPv, stringResource(R.string.battery_from_pv), shares?.let { it.pvKwh to it.pvPercent }),
        Triple(colors.energyGrid, stringResource(R.string.energy_from_grid_full), shares?.let { it.gridKwh to it.gridPercent }),
        Triple(colors.surfaceSunken, stringResource(R.string.energy_empty), shares?.let { it.emptyKwh to it.emptyPercent }),
    )
    GgCard(contentPadding = PaddingValues(horizontal = GgSpacing.space4, vertical = GgSpacing.space1)) {
        rows.forEachIndexed { i, (swatch, name, v) ->
            Row(
                Modifier.fillMaxWidth().padding(vertical = 10.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(Modifier.size(12.dp).clip(RoundedCornerShape(3.dp)).background(swatch))
                Text(name, style = GgTheme.typography.bodySm, color = colors.ink, modifier = Modifier.weight(1f))
                Text(
                    v?.let { GgFormat.withUnit(it.first, 1, kwh) } ?: dash,
                    style = GgTheme.typography.bodySm.copy(fontWeight = FontWeight.SemiBold, fontFeatureSettings = "tnum"),
                    color = colors.ink,
                )
                Text(
                    v?.let { GgFormat.withUnit(it.second, 1, pct) } ?: dash,
                    style = GgTheme.typography.caption.copy(fontFeatureSettings = "tnum"),
                    color = colors.inkMuted,
                    textAlign = TextAlign.End,
                    modifier = Modifier.width(56.dp),
                )
            }
        }
    }
}

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
