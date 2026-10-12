package vn.ggpower.inverter.ui.home.house

import androidx.annotation.DrawableRes
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
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

/** Khung của prototype `vw.nodes`: 350 × 300 dp. */
private const val FrameWidth = 350f
private const val FrameHeight = 300f

private fun curve(x0: Float, y0: Float, c1x: Float, c1y: Float, c2x: Float, c2y: Float, x1: Float, y1: Float) =
    Path().apply { moveTo(x0, y0); cubicTo(c1x, c1y, c2x, c2y, x1, y1) }

private const val NodeStroke = 3.4f

/** PV → SmartBox (chiều xuôi = PV phát). */
private val PvCurve = FlowLine(curve(88f, 46f, 140f, 46f, 140f, 132f, 175f, 132f), 103.6f, 3370, 0.82f, NodeStroke)

/** Lưới → SmartBox (chiều xuôi = mua điện). */
private val GridCurve = FlowLine(curve(262f, 46f, 210f, 46f, 210f, 132f, 175f, 132f), 103.6f, 3370, 0.82f, NodeStroke)

/** SmartBox → pin (chiều xuôi = sạc). */
private val BatteryCurve = FlowLine(curve(175f, 132f, 140f, 132f, 140f, 214f, 88f, 214f), 101.2f, 3290, 0.82f, NodeStroke)

/** SmartBox → tải, màu cam. */
private val LoadCurve = FlowLine(curve(175f, 132f, 210f, 132f, 210f, 214f, 262f, 214f), 101.2f, 3290, 0.82f, NodeStroke, orange = true)

/**
 * Sơ đồ khối (Main.dc.html khối `vw.nodes`): PV, Lưới, Pin, Tải là các vòng 56 dp ở bốn góc nối bằng đường cong với
 * GG SmartBox (vòng 80 dp `flowHub`) ở giữa; vệt sáng 5 lớp chạy theo chiều dòng điện, đường tới tải màu cam.
 */
@Composable
fun NodesFlowDiagram(snapshot: EnergySnapshot, modifier: Modifier = Modifier) {
    val colors = GgTheme.colors
    val edges = remember(snapshot) { snapshot.edges() }
    val kw = stringResource(R.string.unit_kw)
    val pvText = GgFormat.withUnit(snapshot.pvKw, 1, kw)
    val gridText = GgFormat.withUnit(abs(snapshot.gridKw), 1, kw)
    val batteryText = GgFormat.withUnit(abs(snapshot.batteryKw), 1, kw)
    val loadText = GgFormat.withUnit(snapshot.loadKw, 1, kw)
    val socText = GgFormat.withUnit(snapshot.batterySocPercent.toLong(), stringResource(R.string.unit_percent))
    val activity = snapshot.batteryActivity
    val activityText = stringResource(
        when (activity) {
            BatteryActivity.Charging -> R.string.battery_charging
            BatteryActivity.Discharging -> R.string.battery_discharging
            BatteryActivity.Idle -> R.string.battery_idle
        },
    )
    val description = stringResource(
        R.string.cd_energy_flow, pvText, gridText, GgFormat.signedWithUnit(snapshot.batteryKw, 1, kw), activityText, loadText,
    )

    var onScreen by remember { mutableStateOf(true) }
    val flows = listOf(
        PvCurve to edges.pv.forward(FlowDirection.ToHub),
        GridCurve to edges.grid.forward(FlowDirection.ToHub),
        BatteryCurve to edges.battery.forward(FlowDirection.FromHub),
        LoadCurve to edges.load.forward(FlowDirection.FromHub),
    )
    val clock = flowClock(onScreen && flows.any { it.second != null })

    BoxWithConstraints(
        modifier
            .fillMaxWidth()
            .onGloballyPositioned { onScreen = it.boundsInWindow().height > 0f }
            .clearAndSetSemantics { contentDescription = description },
    ) {
        val k = maxWidth.value / FrameWidth
        Box(Modifier.fillMaxWidth().height((FrameHeight * k).dp)) {
            Canvas(Modifier.fillMaxSize().graphicsLayer()) {
                scale(size.width / FrameWidth, pivot = androidx.compose.ui.geometry.Offset.Zero) {
                    val track = Stroke(width = NodeStroke, cap = StrokeCap.Round, join = StrokeJoin.Round)
                    flows.forEach { (line, _) -> drawPath(line.path, colors.nodesFlowTrack, style = track) }
                }
            }
            Canvas(Modifier.fillMaxSize().graphicsLayer()) {
                val now = clock.value
                scale(size.width / FrameWidth, pivot = androidx.compose.ui.geometry.Offset.Zero) {
                    flows.forEach { (line, forward) ->
                        if (forward != null) drawComet(line, forward, now, if (line.orange) colors.accent else colors.houseFlowGreen)
                    }
                }
            }
            Node(k, 60f, 18f, R.drawable.ic_energy_pv, colors.accentSoft, colors.energyPv, stringResource(R.string.energy_pv), pvText, null, colors.ink)
            Node(
                k, 290f, 18f, R.drawable.ic_energy_grid, colors.infoSoft, colors.energyGrid, stringResource(R.string.house_grid), gridText,
                when (edges.grid) {
                    FlowDirection.ToHub -> stringResource(R.string.house_grid_buy)
                    FlowDirection.FromHub -> stringResource(R.string.house_grid_sell)
                    FlowDirection.None -> null
                },
                if (edges.grid == FlowDirection.FromHub) colors.primary else colors.info,
            )
            Node(
                k, 60f, 186f, R.drawable.ic_battery, colors.primarySoft, colors.energyBattery,
                stringResource(R.string.nodes_battery, socText), batteryText,
                if (activity == BatteryActivity.Idle) null else activityText,
                if (activity == BatteryActivity.Discharging) colors.accentText else colors.primary,
            )
            Node(k, 290f, 186f, R.drawable.ic_energy_load, colors.nodeLoadBg, colors.inkMuted, stringResource(R.string.house_load), loadText, null, colors.ink)
            ArtLabel(k, centerX = 175f, top = 92f, gap = 6f) {
                Box(
                    Modifier
                        .size((80 * k).dp)
                        .shadow((12 * k).dp, CircleShape, ambientColor = colors.flowHub.copy(alpha = 0.28f), spotColor = colors.flowHub.copy(alpha = 0.28f))
                        .clip(CircleShape)
                        .background(colors.flowHub),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(painterResource(R.drawable.ic_smartbox), contentDescription = null, tint = Color.White, modifier = Modifier.size((32 * k).dp))
                }
                Text(stringResource(R.string.energy_hub), style = artText(k, 12f, 16f, FontWeight.SemiBold), color = colors.inkMuted)
            }
        }
    }
}

/** Nút góc: vòng 56 dp (icon 26), tên 11/14, giá trị 14/18 700, dòng phụ 10/13 600 màu [subColor]; tâm ngang tại [cx]. */
@Composable
private fun Node(
    k: Float,
    cx: Float,
    top: Float,
    @DrawableRes icon: Int,
    background: Color,
    tint: Color,
    name: String,
    value: String,
    sub: String?,
    subColor: Color,
) {
    val colors = GgTheme.colors
    ArtLabel(k, centerX = cx, top = top, gap = 2f) {
        Box(
            Modifier
                .padding(bottom = (4 * k).dp)
                .size((56 * k).dp)
                .clip(CircleShape)
                .background(background),
            contentAlignment = Alignment.Center,
        ) {
            Icon(painterResource(icon), contentDescription = null, tint = tint, modifier = Modifier.size((26 * k).dp))
        }
        Text(name, style = artText(k, 11f, 14f), color = colors.inkMuted, maxLines = 1)
        Text(value, style = artText(k, 14f, 18f, FontWeight.Bold), color = colors.ink, maxLines = 1)
        if (sub != null) Text(sub, style = artText(k, 10f, 13f, FontWeight.SemiBold), color = subColor, maxLines = 1)
    }
}

@Preview(widthDp = 350, heightDp = 300, showBackground = true, backgroundColor = 0xFFF5F7F3)
@Composable
private fun NodesFlowDiagramPreview() {
    GgPowerTheme(darkTheme = false) {
        NodesFlowDiagram(EnergySnapshot(pvKw = 4.2, gridKw = 0.5, batteryKw = 1.6, loadKw = 3.1, batterySocPercent = 76))
    }
}

