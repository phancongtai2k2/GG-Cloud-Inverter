package vn.ggpower.inverter.ui.home

import androidx.annotation.DrawableRes
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathMeasure
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
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

/** Khung vẽ của prototype (Main.dc.html): 318 × 256 dp, đúng bằng bề rộng trong thẻ trên màn 390 dp. */
private val DiagramWidth = 318.dp
private val DiagramHeight = 256.dp

/**
 * Đường cong bậc ba nối một nút với GG SmartBox, toạ độ theo khung 318 × 256, luôn vẽ theo chiều nút → SmartBox.
 * (Prototype vẽ đường pin và tải theo chiều SmartBox → nút; ở đây đảo lại cho thống nhất, chiều tia do [FlowDirection].)
 */
private class Curve(val x0: Float, val y0: Float, val c1x: Float, val c1y: Float, val c2x: Float, val c2y: Float, val x1: Float, val y1: Float)

private val PvCurve = Curve(70f, 22f, 105f, 22f, 122f, 60f, 139f, 77f)
private val GridCurve = Curve(248f, 22f, 213f, 22f, 196f, 60f, 179f, 77f)
private val BatteryCurve = Curve(70f, 172f, 105f, 172f, 122f, 134f, 139f, 117f)
private val LoadCurve = Curve(248f, 172f, 213f, 172f, 196f, 134f, 179f, 117f)

/**
 * Sơ đồ dòng năng lượng (đặc tả §3.4 mục 4.2): 4 nút PV · Lưới điện · S16 Pre · Tải tiêu thụ quanh GG SmartBox,
 * đường nối cong màu trắng, tia sáng xanh chạy theo chiều dòng điện (đuôi mờ dần, 1,6 s một vòng).
 * Đường không có dòng điện thì không có tia sáng.
 */
@Composable
fun EnergyFlowDiagram(snapshot: EnergySnapshot, modifier: Modifier = Modifier) {
    val colors = GgTheme.colors
    val edges = remember(snapshot) { snapshot.edges() }
    val kw = stringResource(R.string.unit_kw)
    val pct = stringResource(R.string.unit_percent)
    val pvText = GgFormat.withUnit(snapshot.pvKw, 1, kw)
    val gridText = GgFormat.withUnit(kotlin.math.abs(snapshot.gridKw), 1, kw)
    val batteryText = GgFormat.signedWithUnit(snapshot.batteryKw, 1, kw)
    val loadText = GgFormat.withUnit(snapshot.loadKw, 1, kw)
    val socText = GgFormat.withUnit(snapshot.batterySocPercent.toLong(), pct)
    val activityText = stringResource(
        when (snapshot.batteryActivity) {
            BatteryActivity.Charging -> R.string.battery_charging
            BatteryActivity.Discharging -> R.string.battery_discharging
            BatteryActivity.Idle -> R.string.battery_idle
        },
    )
    val description = stringResource(R.string.cd_energy_flow, pvText, gridText, batteryText, activityText, loadText)

    // Tia sáng chỉ chạy khi sơ đồ đang nằm trong màn hình và có ít nhất một đường có dòng điện: cuộn khuất hoặc
    // hệ thống đứng yên thì không vẽ lại liên tục (đỡ tốn pin).
    var onScreen by remember { mutableStateOf(true) }
    val flowing = edges.pv != FlowDirection.None || edges.grid != FlowDirection.None ||
        edges.battery != FlowDirection.None || edges.load != FlowDirection.None
    val progress = cometProgress(active = onScreen && flowing)

    BoxWithConstraints(
        modifier = modifier
            .fillMaxWidth()
            .onGloballyPositioned { onScreen = it.boundsInWindow().height > 0f }
            .clearAndSetSemantics { contentDescription = description },
        contentAlignment = Alignment.Center,
    ) {
        // Sơ đồ vẽ theo khung cố định 318 × 256 dp; màn hẹp hơn thì thu nhỏ cả khung cho vừa, không cắt nút.
        val fit = (maxWidth / DiagramWidth).coerceAtMost(1f)
        Box(Modifier.size(DiagramWidth * fit, DiagramHeight * fit), contentAlignment = Alignment.Center) {
        Box(Modifier.requiredSize(DiagramWidth, DiagramHeight).graphicsLayer { scaleX = fit; scaleY = fit }) {
            Canvas(Modifier.fillMaxSize()) {
                val unit = size.width / 318f
                listOf(
                    PvCurve to edges.pv,
                    GridCurve to edges.grid,
                    BatteryCurve to edges.battery,
                    LoadCurve to edges.load,
                ).forEach { (curve, direction) ->
                    val path = curve.toPath(unit)
                    drawPath(path, colors.surfaceRaised, style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round))
                    if (direction != FlowDirection.None) {
                        drawComet(path, progress, reverse = direction == FlowDirection.FromHub, colors.flowGlow, colors.flowHead, colors.primarySoft)
                    }
                }
            }
            Node(R.drawable.ic_energy_pv, colors.accentSoft, colors.energyPv, x = 26.dp, y = 0.dp)
            NodeLabel(stringResource(R.string.energy_pv), pvText, null, x = (-2).dp, y = 48.dp)
            Node(R.drawable.ic_energy_grid, colors.infoSoft, colors.energyGrid, x = 248.dp, y = 0.dp)
            NodeLabel(stringResource(R.string.energy_grid), gridText, null, x = 220.dp, y = 48.dp)
            Node(R.drawable.ic_battery, colors.primarySoft, colors.energyBattery, x = 26.dp, y = 150.dp)
            NodeLabel(stringResource(R.string.energy_battery, socText), batteryText, activityText, x = (-2).dp, y = 198.dp)
            Node(R.drawable.ic_energy_load, colors.surfaceSunken, colors.energyLoad, x = 248.dp, y = 150.dp)
            NodeLabel(stringResource(R.string.energy_load), loadText, null, x = 220.dp, y = 198.dp)
            // GG SmartBox
            Box(
                Modifier.offset(131.dp, 69.dp).size(56.dp).clip(CircleShape).background(colors.flowHub),
                contentAlignment = Alignment.Center,
            ) {
                Icon(painterResource(R.drawable.ic_smartbox), contentDescription = null, tint = colors.onPrimary, modifier = Modifier.size(26.dp))
            }
            Text(
                stringResource(R.string.energy_hub),
                style = GgTheme.typography.caption,
                color = colors.inkMuted,
                textAlign = TextAlign.Center,
                modifier = Modifier.offset(109.dp, 129.dp).width(100.dp),
            )
        }
        }
    }
}

/** Tiến độ 0..1 của tia sáng (1,6 s một vòng); đứng yên ở 0 khi [active] là false. */
@Composable
private fun cometProgress(active: Boolean): Float {
    if (!active) return 0f
    val transition = rememberInfiniteTransition(label = "flow")
    val progress by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(1600, easing = LinearEasing), RepeatMode.Restart),
        label = "comet",
    )
    return progress
}

@Composable
private fun Node(@DrawableRes icon: Int, background: Color, tint: Color, x: Dp, y: Dp) {
    Box(
        Modifier.offset(x, y).size(44.dp).clip(CircleShape).background(background),
        contentAlignment = Alignment.Center,
    ) {
        Icon(painterResource(icon), contentDescription = null, tint = tint, modifier = Modifier.size(22.dp))
    }
}

/** Nhãn dưới nút: tên 12/16 500 `inkMuted`, giá trị 15/20 600 số đều, dòng phụ 12/16 (chỉ pin). Khung rộng 100 dp, canh giữa. */
@Composable
private fun NodeLabel(title: String, value: String, extra: String?, x: Dp, y: Dp) {
    val colors = GgTheme.colors
    Column(Modifier.offset(x, y).width(100.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Text(title, style = GgTheme.typography.caption, color = colors.inkMuted, textAlign = TextAlign.Center, maxLines = 1)
        Text(
            value,
            style = GgTheme.typography.label.copy(fontSize = 15.sp, lineHeight = 20.sp, fontWeight = FontWeight.SemiBold, fontFeatureSettings = "tnum"),
            color = colors.ink,
            maxLines = 1,
        )
        if (extra != null) Text(extra, style = GgTheme.typography.caption, color = colors.inkMuted, maxLines = 1)
    }
}

private fun Curve.toPath(unit: Float) = Path().apply {
    moveTo(x0 * unit, y0 * unit)
    cubicTo(c1x * unit, c1y * unit, c2x * unit, c2y * unit, x1 * unit, y1 * unit)
}

/**
 * Tia sáng có đuôi (theo prototype: nét 8 đơn vị chạy hết đường trong 1,6 s, 8 lớp đuôi cách nhau 6 đơn vị, mờ dần;
 * mỗi lớp gồm quầng rộng 6 `flowGlow`, nét 2,5 `flowHead`, lõi 1 `primarySoft`).
 */
private fun DrawScope.drawComet(path: Path, progress: Float, reverse: Boolean, glow: Color, head: Color, core: Color) {
    val measure = PathMeasure().apply { setPath(path, false) }
    val length = measure.length
    val unit = size.width / 318f
    val dash = 8f * unit
    val step = 6f * unit
    val trail = 8
    // Đầu tia đi từ 0 tới length + đuôi để đuôi cũng chạy hết khỏi đường.
    val headPos = progress * (length + trail * step)
    for (k in trail - 1 downTo 0) {
        val alpha = ((trail - k).toFloat() / trail).let { it * it }
        var end = headPos - k * step
        var start = end - dash
        if (end <= 0f || start >= length) continue
        start = start.coerceAtLeast(0f); end = end.coerceAtMost(length)
        if (reverse) { val s = length - end; end = length - start; start = s }
        val seg = Path()
        if (!measure.getSegment(start, end, seg, true)) continue
        drawPath(seg, glow, alpha = alpha * 0.35f, style = Stroke(width = 6.dp.toPx(), cap = StrokeCap.Round))
        drawPath(seg, head, alpha = alpha, style = Stroke(width = 2.5.dp.toPx(), cap = StrokeCap.Round))
        drawPath(seg, core, alpha = alpha, style = Stroke(width = 1.dp.toPx(), cap = StrokeCap.Round))
    }
}

@Preview(widthDp = 390, showBackground = true, backgroundColor = 0xFFF9FAF7)
@Composable
private fun EnergyFlowDiagramPreview() {
    GgPowerTheme(darkTheme = false) {
        EnergyFlowDiagram(
            EnergySnapshot(pvKw = 4.2, gridKw = 0.0, batteryKw = 1.1, loadKw = 3.1, batterySocPercent = 76),
            modifier = Modifier.padding(16.dp),
        )
    }
}
