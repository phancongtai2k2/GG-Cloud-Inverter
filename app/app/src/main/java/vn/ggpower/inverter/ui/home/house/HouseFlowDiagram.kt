package vn.ggpower.inverter.ui.home.house

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.keyframes
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.PathMeasure
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import vn.ggpower.inverter.R
import vn.ggpower.inverter.domain.format.GgFormat
import vn.ggpower.inverter.domain.home.BatteryActivity
import vn.ggpower.inverter.domain.home.EnergySnapshot
import vn.ggpower.inverter.domain.home.FlowDirection
import vn.ggpower.inverter.domain.home.batteryActivity
import vn.ggpower.inverter.domain.home.edges
import vn.ggpower.inverter.ui.theme.GgPowerTheme
import vn.ggpower.inverter.ui.theme.GgTheme
import kotlin.math.abs

/** Khung của prototype: 700 × 690 px thu 0,5 → 350 × 345 dp (đúng bề rộng nội dung màn 390 dp). */
private const val ArtWidth = 700f
private const val ArtHeight = 690f

// viewBox "28 2 270 213" của SVG ngôi nhà trong khung 700 × 552 (xMidYMid meet) — cùng phép quy đổi với svg2kt.mjs.
private val ViewScale = minOf(700f / 270f, 552f / 213f)
private val ViewTx = (700f - 270f * ViewScale) / 2f - 28f * ViewScale
private val ViewTy = (552f - 213f * ViewScale) / 2f - 2f * ViewScale

private fun svgPath(vararg points: Float): Path = Path().apply {
    for (i in points.indices step 2) {
        val x = points[i] * ViewScale + ViewTx
        val y = points[i + 1] * ViewScale + ViewTy
        if (i == 0) moveTo(x, y) else lineTo(x, y)
    }
}

/** Đường của sơ đồ ngôi nhà: toạ độ và vệt theo đơn vị SVG, quy về khung 700 × 690; nét 1,7. */
private fun houseLine(head: Float, periodMs: Int, travel: Float, vararg points: Float, orange: Boolean = false) =
    FlowLine(svgPath(*points), head * ViewScale, periodMs, travel, FlowStroke * ViewScale, orange)


/** PV: tấm pin trên mái → biến tần. */
private val PvLine = houseLine(29.15f, 2690, 0.817f, 128.15f, 94.3f, 113.5f, 96.7f, 113.5f, 133.15f)

/** Biến tần → Energy Hub (chiều xuôi = sạc pin). */
private val BatteryLine = houseLine(9.75f, 1250, 0.72f, 111.9f, 157.9f, 111.9f, 170.1f)

/** Biến tần → tải trong nhà, màu cam. */
private val LoadLine = houseLine(35f, 2710, 0.871f, 121f, 145.2f, 155.5f, 150f, 173.5f, 145.95f, orange = true)

/** Cột điện → biến tần (chiều xuôi = mua điện). */
private val GridLine = houseLine(35f, 8390, 0.958f, 40f, 203.3f, 76.5f, 195f, 76.5f, 139f, 106f, 143.1f)

private const val FlowStroke = 1.7f

/**
 * Sơ đồ dòng năng lượng dạng ngôi nhà (Main.dc.html khối `vw.house`; CLAUDE.md thiết kế §6): hình minh hoạ tĩnh sinh từ SVG
 * ([HouseBack], [HouseFront]), 4 đường năng lượng có vệt sáng chạy theo chiều dòng điện, đường nét đứt nối chỉ số PV /
 * Lưới / Pin / Tải, chip "Giảm CO₂" trôi nhẹ trên mái. Đường không có dòng điện thì không có vệt sáng.
 *
 * [co2Kg]: lượng CO₂ giảm hôm nay; null → "—" (bản live chưa có trường này, `contract/CHANGES.md` mục Đang chờ).
 */
@Composable
fun HouseFlowDiagram(snapshot: EnergySnapshot, co2Kg: Double?, modifier: Modifier = Modifier) {
    val colors = GgTheme.colors
    val edges = remember(snapshot) { snapshot.edges() }
    val kw = stringResource(R.string.unit_kw)
    val pct = stringResource(R.string.unit_percent)
    val pvText = GgFormat.withUnit(snapshot.pvKw, 1, kw)
    val gridText = GgFormat.withUnit(abs(snapshot.gridKw), 1, kw)
    val batteryText = GgFormat.withUnit(abs(snapshot.batteryKw), 1, kw)
    val loadText = GgFormat.withUnit(snapshot.loadKw, 1, kw)
    val socText = GgFormat.withUnit(snapshot.batterySocPercent.toLong(), pct)
    val activity = snapshot.batteryActivity
    val activityText = stringResource(
        when (activity) {
            BatteryActivity.Charging -> R.string.battery_charging
            BatteryActivity.Discharging -> R.string.battery_discharging
            BatteryActivity.Idle -> R.string.battery_idle
        },
    )
    val co2Text = co2Kg?.let { GgFormat.withUnit(it, 1, stringResource(R.string.unit_kg)) } ?: stringResource(R.string.value_missing)
    val description = stringResource(
        R.string.cd_energy_flow, pvText, gridText, GgFormat.signedWithUnit(snapshot.batteryKw, 1, kw), activityText, loadText,
    ) + ". " + stringResource(R.string.cd_house_co2, co2Text)

    // Vệt sáng chỉ chạy khi sơ đồ còn trong màn hình (đỡ tốn pin khi cuộn khuất).
    var onScreen by remember { mutableStateOf(true) }
    val flows = listOf(
        PvLine to edges.pv.forward(FlowDirection.ToHub),
        GridLine to edges.grid.forward(FlowDirection.ToHub),
        BatteryLine to edges.battery.forward(FlowDirection.FromHub),
        LoadLine to edges.load.forward(FlowDirection.FromHub),
    )
    val active = onScreen && flows.any { it.second != null }
    val clock = flowClock(active)

    BoxWithConstraints(
        modifier
            .fillMaxWidth()
            .onGloballyPositioned { onScreen = it.boundsInWindow().height > 0f }
            .clearAndSetSemantics { contentDescription = description },
    ) {
        // 1 px của prototype = k dp (k = 0,5 ở bề rộng 350 dp).
        val k = maxWidth.value / ArtWidth
        Box(
            Modifier
                .fillMaxWidth()
                .height((ArtHeight * k).dp)
                .clipToBounds(),
        ) {
            Canvas(Modifier.fillMaxSize().graphicsLayer()) {
                scale(size.width / ArtWidth, pivot = Offset.Zero) {
                    drawArt(HouseBack)
                    val track = Stroke(width = PvLine.stroke, cap = StrokeCap.Round, join = StrokeJoin.Round)
                    flows.forEach { (line, _) -> drawPath(line.path, colors.houseFlowTrack, style = track) }
                }
            }
            Canvas(Modifier.fillMaxSize().graphicsLayer()) {
                val now = clock.value
                scale(size.width / ArtWidth, pivot = Offset.Zero) {
                    flows.forEach { (line, forward) ->
                        if (forward != null) drawComet(line, forward, now, if (line.orange) colors.accent else colors.houseFlowGreen)
                    }
                }
            }
            Canvas(Modifier.fillMaxSize().graphicsLayer()) {
                scale(size.width / ArtWidth, pivot = Offset.Zero) {
                    drawArt(HouseFront)
                    val dash = Stroke(width = 1.6f, cap = StrokeCap.Round, pathEffect = PathEffect.dashPathEffect(floatArrayOf(2f, 6f)))
                    val leader = colors.inkMuted.copy(alpha = 0.45f)
                    LeaderLines.forEach { (x, y0, y1) ->
                        drawLine(leader, Offset(x, y0), Offset(x, y1), strokeWidth = dash.width, cap = StrokeCap.Round, pathEffect = dash.pathEffect)
                    }
                }
            }

            // Nhãn PV trên mái (tâm tại 238,6 × 85,6).
            ArtLabel(k, centerX = 238.6f, top = 85.6f, centerY = true) {
                Text(stringResource(R.string.energy_pv), style = artText(k, 20f, 24f), color = colors.inkMuted)
                Text(pvText, style = artText(k, 24f, 30f, FontWeight.Bold), color = colors.ink)
            }
            ArtLabel(k, centerX = 68f, top = 560f, gap = 4f) {
                Text(stringResource(R.string.house_grid), style = artText(k, 22f, 28f), color = colors.inkMuted)
                Text(gridText, style = artText(k, 28f, 36f, FontWeight.Bold), color = colors.ink)
                when (edges.grid) {
                    FlowDirection.ToHub -> Text(stringResource(R.string.house_grid_buy), style = artText(k, 20f, 26f, FontWeight.SemiBold), color = colors.info)
                    FlowDirection.FromHub -> Text(stringResource(R.string.house_grid_sell), style = artText(k, 20f, 26f, FontWeight.SemiBold), color = colors.primary)
                    FlowDirection.None -> Unit
                }
            }
            ArtLabel(k, centerX = 230f, top = 560f, gap = 4f) {
                Text(stringResource(R.string.house_battery), style = artText(k, 22f, 28f), color = colors.inkMuted)
                Text(batteryText, style = artText(k, 28f, 36f, FontWeight.Bold), color = colors.ink)
                Text(
                    if (activity == BatteryActivity.Idle) socText else stringResource(R.string.house_battery_state, activityText, socText),
                    style = artText(k, 20f, 26f, FontWeight.SemiBold),
                    color = when (activity) {
                        BatteryActivity.Charging -> colors.primary
                        BatteryActivity.Discharging -> colors.accentText
                        BatteryActivity.Idle -> colors.inkMuted
                    },
                )
            }
            ArtLabel(k, centerX = 530f, top = 560f, gap = 4f) {
                Text(stringResource(R.string.house_load), style = artText(k, 22f, 28f), color = colors.inkMuted)
                Text(loadText, style = artText(k, 28f, 36f, FontWeight.Bold), color = colors.ink)
            }
            Co2Chip(k, co2Text, animate = onScreen)
        }
    }
}

/** Đường nét đứt dọc nối chỉ số với sơ đồ (x, y đầu, y cuối) — khung 700 × 690. */
private val LeaderLines = listOf(
    Triple(238f, 128f, 216f),
    Triple(68f, 558f, 518f),
    Triple(230f, 558f, 506f),
    Triple(530f, 558f, 420f),
)

/** Chữ theo cỡ px của prototype (đã nhân [k] → dp), không phóng theo cỡ chữ hệ thống để giữ đúng bố cục hình. */
@Composable
internal fun artText(k: Float, sizePx: Float, lineHeightPx: Float, weight: FontWeight = FontWeight.Normal): TextStyle {
    val fontScale = LocalDensity.current.fontScale
    fun px(v: Float): TextUnit = (v * k / fontScale).sp
    return GgTheme.typography.body.copy(fontSize = px(sizePx), lineHeight = px(lineHeightPx), fontWeight = weight, fontFeatureSettings = "tnum")
}

/** Cột chữ căn giữa tại [centerX]; đỉnh tại [top] (hoặc tâm dọc nếu [centerY]) — toạ độ khung 700 × 690. */
@Composable
internal fun ArtLabel(k: Float, centerX: Float, top: Float, centerY: Boolean = false, gap: Float = 0f, content: @Composable () -> Unit) {
    Layout(
        content = { Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy((gap * k).dp)) { content() } },
    ) { measurables, constraints ->
        val p = measurables.first().measure(constraints.copy(minWidth = 0, minHeight = 0))
        val x = (centerX * k).dp.roundToPx() - p.width / 2
        val y = (top * k).dp.roundToPx() - if (centerY) p.height / 2 else 0
        layout(constraints.maxWidth, constraints.maxHeight) { p.place(x, y) }
    }
}

/**
 * Chip "Giảm CO₂" (Main.dc.html `.gg-co2`): đáy chip neo tại (526,6; 113,7), trôi ngang 7,3 s và lên xuống 4,7 s
 * (keyframe `ggCo2XL` / `ggCo2FloatL`, ease-in-out). Viên thuốc đệm 8/24, viền 2, mũi nhọn xoay 45° và chấm tròn bên dưới.
 */
@Composable
private fun Co2Chip(k: Float, value: String, animate: Boolean) {
    val colors = GgTheme.colors
    val dx: Float
    val dy: Float
    if (animate) {
        val t = rememberInfiniteTransition(label = "co2")
        dx = t.animateFloat(
            0f, 0f,
            infiniteRepeatable(
                keyframes {
                    durationMillis = 7300
                    0f at 0 using FastOutSlowInEasing
                    -18f at 1241 using FastOutSlowInEasing
                    10f at 2774 using FastOutSlowInEasing
                    24f at 4015 using FastOutSlowInEasing
                    -8f at 5256 using FastOutSlowInEasing
                    14f at 6424 using FastOutSlowInEasing
                },
            ),
            label = "co2x",
        ).value
        dy = t.animateFloat(
            0f, 0f,
            infiniteRepeatable(
                keyframes {
                    durationMillis = 4700
                    0f at 0 using FastOutSlowInEasing
                    -12f at 1034 using FastOutSlowInEasing
                    -4f at 1927 using FastOutSlowInEasing
                    -16f at 2961 using FastOutSlowInEasing
                    -6f at 3948 using FastOutSlowInEasing
                },
            ),
            label = "co2y",
        ).value
    } else {
        dx = 0f
        dy = 0f
    }
    val border = (2f * k).dp
    Layout(
        content = {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                val pill = RoundedCornerShape(50)
                Column(
                    Modifier
                        .shadow((12f * k).dp, pill, ambientColor = colors.co2ChipShadow, spotColor = colors.co2ChipShadow)
                        .clip(pill)
                        .background(colors.co2ChipBg)
                        .border(border, colors.co2ChipBorder, pill)
                        .padding(horizontal = (24f * k).dp, vertical = (8f * k).dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(stringResource(R.string.house_co2), style = artText(k, 18f, 22f), color = colors.inkMuted)
                    Text(value, style = artText(k, 24f, 30f, FontWeight.Bold), color = colors.primary)
                }
                // Mũi nhọn: ô vuông 16 px xoay 45°, chỉ viền phải + dưới, lùi lên 10 px che mép viên thuốc.
                Canvas(
                    Modifier
                        .offset(y = (-10f * k).dp)
                        .size((16f * k).dp)
                        .rotate(45f),
                ) {
                    drawRect(colors.co2ChipBg)
                    val w = border.toPx()
                    drawLine(colors.co2ChipBorder, Offset(size.width - w / 2, 0f), Offset(size.width - w / 2, size.height), w)
                    drawLine(colors.co2ChipBorder, Offset(0f, size.height - w / 2), Offset(size.width, size.height - w / 2), w)
                }
                Box(
                    Modifier
                        .offset(y = (-4f * k).dp)
                        .size((10f * k).dp)
                        .clip(CircleShape)
                        .background(colors.co2ChipBg)
                        .border(border, colors.co2ChipBorder, CircleShape),
                )
            }
        },
    ) { measurables, constraints ->
        val p = measurables.first().measure(constraints.copy(minWidth = 0, minHeight = 0))
        val x = ((526.6f + dx) * k).dp.roundToPx() - p.width / 2
        val y = ((113.7f + dy) * k).dp.roundToPx() - p.height
        layout(constraints.maxWidth, constraints.maxHeight) { p.place(x, y) }
    }
}

@Preview(widthDp = 350, heightDp = 345, showBackground = true, backgroundColor = 0xFFF5F7F3)
@Composable
private fun HouseFlowDiagramPreview() {
    GgPowerTheme(darkTheme = false) {
        HouseFlowDiagram(EnergySnapshot(pvKw = 4.2, gridKw = 0.5, batteryKw = 1.6, loadKw = 3.1, batterySocPercent = 76), co2Kg = 10.4)
    }
}

@Preview(widthDp = 350, heightDp = 345, showBackground = true, backgroundColor = 0xFF0C1612)
@Composable
private fun HouseFlowDiagramDarkPreview() {
    GgPowerTheme(darkTheme = true) {
        HouseFlowDiagram(EnergySnapshot(pvKw = 0.0, gridKw = -0.8, batteryKw = -1.2, loadKw = 2.0, batterySocPercent = 54), co2Kg = null)
    }
}
