package vn.ggpower.inverter.ui.home

import androidx.activity.compose.BackHandler
import androidx.annotation.StringRes
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.snapping.SnapLayoutInfoProvider
import androidx.compose.foundation.gestures.snapping.SnapPosition
import androidx.compose.foundation.gestures.snapping.rememberSnapFlingBehavior
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import kotlinx.coroutines.launch
import vn.ggpower.inverter.R
import vn.ggpower.inverter.data.home.FakeHomeRepository
import vn.ggpower.inverter.domain.home.EnergySnapshot
import vn.ggpower.inverter.domain.home.FlowView
import vn.ggpower.inverter.domain.home.HomeLayout
import vn.ggpower.inverter.domain.home.HomeWidget
import vn.ggpower.inverter.ui.components.PrimaryButton
import vn.ggpower.inverter.ui.components.SectionHeader
import vn.ggpower.inverter.ui.home.house.HouseFlowDiagram
import vn.ggpower.inverter.ui.home.house.NodesFlowDiagram
import vn.ggpower.inverter.ui.theme.GgPowerTheme
import vn.ggpower.inverter.ui.theme.GgRadius
import vn.ggpower.inverter.ui.theme.GgSpacing
import vn.ggpower.inverter.ui.theme.GgTheme
import kotlin.math.roundToInt

/** Chiều cao một dòng thẻ trong danh sách sắp xếp (Main.dc.html `RH`). */
private val RowHeight = 64.dp

/**
 * Màn "Tuỳ chỉnh màn hình chính" (Main.dc.html `vw.sheet`): phủ toàn màn, gồm carousel chọn kiểu sơ đồ (Hình nhà / Sơ đồ
 * khối, cuộn ngang bắt dính, tự cuộn tới kiểu đang chọn), danh sách thẻ đang hiện (kéo tay nắm ⋮⋮ để đổi chỗ, dấu trừ để
 * ẩn), thẻ có thể thêm (dấu +) và nút "Áp dụng". Thay đổi chỉ lưu khi bấm "Áp dụng"; đóng / nút quay lại thì bỏ.
 */
@Composable
fun HomeCustomizeScreen(
    initial: HomeLayout,
    snapshot: EnergySnapshot,
    co2Kg: Double?,
    onClose: () -> Unit,
    onApply: (HomeLayout) -> Unit,
) {
    val colors = GgTheme.colors
    var pending by remember { mutableStateOf(initial) }
    BackHandler(onBack = onClose)
    Column(
        Modifier
            .fillMaxSize()
            .background(colors.surface)
            // Chặn chạm xuyên xuống màn chính bên dưới.
            .clickable(enabled = false) {}
            .statusBarsPadding()
            .navigationBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(bottom = 32.dp),
    ) {
        Row(Modifier.height(64.dp).padding(start = 4.dp, end = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onClose, modifier = Modifier.size(48.dp)) {
                Icon(painterResource(R.drawable.ic_close), stringResource(R.string.cd_close), tint = colors.ink, modifier = Modifier.size(24.dp))
            }
            Text(
                stringResource(R.string.home_customize_title),
                style = GgTheme.typography.screenTitle,
                color = colors.ink,
                modifier = Modifier.padding(start = 4.dp).semantics { heading() },
            )
        }
        SectionHeader(stringResource(R.string.home_customize_view_section), Modifier.padding(horizontal = 20.dp, vertical = 4.dp))
        Hint(R.string.home_customize_view_hint)
        ViewCarousel(pending.view, snapshot, co2Kg) { pending = pending.copy(view = it) }

        SectionHeader(stringResource(R.string.home_widgets_shown), Modifier.padding(start = 20.dp, end = 20.dp, top = 28.dp, bottom = 4.dp))
        Hint(R.string.home_widgets_hint)
        ListCard {
            if (pending.widgets.isEmpty()) {
                EmptyText(R.string.home_widgets_none_shown)
            } else {
                ShownWidgets(pending, onChange = { pending = it })
            }
        }

        SectionHeader(stringResource(R.string.home_widgets_hidden), Modifier.padding(start = 20.dp, end = 20.dp, top = 24.dp, bottom = 12.dp))
        ListCard {
            if (pending.hidden.isEmpty()) {
                EmptyText(R.string.home_widgets_none_hidden)
            } else {
                pending.hidden.forEach { widget -> HiddenRow(widget) { pending = pending.show(widget) } }
            }
        }

        PrimaryButton(
            stringResource(R.string.home_customize_apply),
            onClick = { onApply(pending) },
            modifier = Modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, top = 24.dp),
        )
    }
}

@Composable
private fun Hint(@StringRes text: Int) {
    Text(
        stringResource(text),
        style = GgTheme.typography.bodySm,
        color = GgTheme.colors.inkMuted,
        modifier = Modifier.padding(start = 20.dp, end = 20.dp, bottom = 12.dp),
    )
}

@Composable
private fun EmptyText(@StringRes text: Int) {
    Text(stringResource(text), style = GgTheme.typography.bodySm, color = GgTheme.colors.inkMuted, modifier = Modifier.padding(16.dp))
}

/** Khung danh sách: nền thẻ, bo 16, bóng nhẹ, lề 20 dp. */
@Composable
private fun ListCard(content: @Composable () -> Unit) {
    val colors = GgTheme.colors
    val shape = RoundedCornerShape(GgRadius.radiusLg)
    Column(
        Modifier
            .padding(horizontal = 20.dp)
            .fillMaxWidth()
            .shadow(3.dp, shape, ambientColor = colors.shadow, spotColor = colors.shadow)
            .clip(shape)
            .background(colors.cardBg),
    ) { content() }
}

// ---------- Carousel kiểu sơ đồ ----------

private class ViewOption(val view: FlowView, @StringRes val title: Int, @StringRes val desc: Int)

private val ViewOptions = listOf(
    ViewOption(FlowView.House, R.string.home_view_house, R.string.home_view_house_desc),
    ViewOption(FlowView.Nodes, R.string.home_view_nodes, R.string.home_view_nodes_desc),
)

/**
 * Hai thẻ 280 dp cách 12 dp, đệm hai bên 55 dp để thẻ nằm giữa khi bắt dính; thẻ đang chọn viền 2 dp `primary`.
 * Hình xem trước là sơ đồ thật thu 0,72 (khung 252 dp). Dưới carousel là 2 chấm trang (đang chọn dài 18 dp).
 */
@Composable
private fun ViewCarousel(selected: FlowView, snapshot: EnergySnapshot, co2Kg: Double?, onSelect: (FlowView) -> Unit) {
    val colors = GgTheme.colors
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    val selectedIndex = ViewOptions.indexOfFirst { it.view == selected }
    LaunchedEffect(Unit) { listState.scrollToItem(selectedIndex) }
    // Prototype đệm 55 dp ở màn 390 dp để thẻ 280 dp nằm giữa; ở đây tính theo bề rộng thật.
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val side = ((maxWidth - 280.dp) / 2).coerceAtLeast(0.dp)
        LazyRow(
            state = listState,
            flingBehavior = rememberSnapFlingBehavior(remember(listState) { SnapLayoutInfoProvider(listState, SnapPosition.Center) }),
            contentPadding = PaddingValues(start = side, end = side, top = 4.dp, bottom = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            items(ViewOptions.size) { i ->
                val option = ViewOptions[i]
                val on = option.view == selected
                val shape = RoundedCornerShape(GgRadius.radiusLg)
                Column(
                    Modifier
                        .width(280.dp)
                        .shadow(if (on) 8.dp else 3.dp, shape, ambientColor = colors.shadow, spotColor = colors.shadow)
                        .clip(shape)
                        .background(colors.surfaceRaised)
                        .border(2.dp, if (on) colors.primary else Color.Transparent, shape)
                        .semantics { this.selected = on }
                        .clickable(role = Role.RadioButton) {
                            onSelect(option.view)
                            scope.launch { listState.animateScrollToItem(i) }
                        }
                        .padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    Box(Modifier.fillMaxWidth().height(240.dp), contentAlignment = Alignment.Center) {
                        val previewHeight = if (option.view == FlowView.House) 248.dp else 216.dp
                        Box(Modifier.size(252.dp, previewHeight).clipToBounds().clearAndSetSemantics { }) {
                            Box(
                                Modifier
                                    .wrapContentSize(Alignment.TopStart, unbounded = true)
                                    .size(350.dp, if (option.view == FlowView.House) 345.dp else 300.dp)
                                    .graphicsLayer {
                                        scaleX = 0.72f
                                        scaleY = 0.72f
                                        transformOrigin = TransformOrigin(0f, 0f)
                                    },
                            ) {
                                when (option.view) {
                                    FlowView.House -> HouseFlowDiagram(snapshot, co2Kg)
                                    FlowView.Nodes -> NodesFlowDiagram(snapshot)
                                }
                            }
                        }
                    }
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Box(
                            Modifier.size(22.dp).border(2.dp, if (on) colors.primary else colors.lineStrong, CircleShape),
                            contentAlignment = Alignment.Center,
                        ) {
                            if (on) Box(Modifier.size(10.dp).clip(CircleShape).background(colors.primary))
                        }
                        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Text(
                                stringResource(option.title),
                                style = GgTheme.typography.body.copy(fontSize = 15.sp, lineHeight = 22.sp, fontWeight = FontWeight.SemiBold),
                                color = colors.ink,
                            )
                            Text(stringResource(option.desc), style = GgTheme.typography.caption.copy(fontWeight = FontWeight.Normal), color = colors.inkMuted)
                        }
                    }
                }
            }
        }
    }
    Row(Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterHorizontally)) {
        ViewOptions.forEach { option ->
            val on = option.view == selected
            val width by animateDpAsState(if (on) 18.dp else 6.dp, tween(200), label = "dot")
            Box(Modifier.size(width, 6.dp).clip(CircleShape).background(if (on) colors.primaryFill else colors.sliderDisabled))
        }
    }
}

// ---------- Danh sách thẻ ----------

@StringRes
private fun widgetName(w: HomeWidget): Int = when (w) {
    HomeWidget.Summary -> R.string.widget_summary
    HomeWidget.Power -> R.string.widget_power
    HomeWidget.Yield -> R.string.widget_yield
    HomeWidget.Battery -> R.string.widget_battery
}

@StringRes
private fun widgetDesc(w: HomeWidget): Int = when (w) {
    HomeWidget.Summary -> R.string.widget_summary_desc
    HomeWidget.Power -> R.string.widget_power_desc
    HomeWidget.Yield -> R.string.widget_yield_desc
    HomeWidget.Battery -> R.string.widget_battery_desc
}

/**
 * Thẻ đang hiện, sắp xếp bằng kéo tay nắm (Main.dc.html `wl.shown`): dòng đang kéo nổi lên (nền `surfaceRaised`, bo 12,
 * bóng) theo tay; các dòng nằm giữa vị trí cũ và mới dịch một dòng (64 dp, 160 ms); thả tay thì đổi chỗ. TalkBack có
 * thao tác "Chuyển lên / xuống".
 */
@Composable
private fun ShownWidgets(layout: HomeLayout, onChange: (HomeLayout) -> Unit) {
    val rowPx = with(LocalDensity.current) { RowHeight.toPx() }
    var dragFrom by remember { mutableIntStateOf(-1) }
    var dragDy by remember { mutableFloatStateOf(0f) }
    val last = layout.widgets.lastIndex
    val target = if (dragFrom >= 0) (dragFrom + (dragDy / rowPx).roundToInt()).coerceIn(0, last) else -1
    val moveUp = stringResource(R.string.cd_widget_move_up)
    val moveDown = stringResource(R.string.cd_widget_move_down)
    Box {
        Column {
            layout.widgets.forEachIndexed { i, widget ->
                val shift = when {
                    dragFrom < 0 || i == dragFrom -> 0f
                    dragFrom < target && i in (dragFrom + 1)..target -> -rowPx
                    dragFrom > target && i in target until dragFrom -> rowPx
                    else -> 0f
                }
                val animatedShift by animateFloatAsState(shift, tween(160), label = "shift")
                val dragging = i == dragFrom
                val name = stringResource(widgetName(widget))
                WidgetRow(
                    modifier = Modifier
                        .zIndex(if (dragging) 1f else 0f)
                        .graphicsLayer { translationY = if (dragging) dragDy else animatedShift }
                        .semantics {
                            customActions = buildList {
                                if (i > 0) add(CustomAccessibilityAction(moveUp) { onChange(layout.move(i, i - 1)); true })
                                if (i < last) add(CustomAccessibilityAction(moveDown) { onChange(layout.move(i, i + 1)); true })
                            }
                        },
                    lifted = dragging,
                    leading = {
                        Box(
                            Modifier.size(36.dp).clip(RoundedCornerShape(10.dp)).background(GgTheme.colors.primarySoft),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                (i + 1).toString(),
                                style = GgTheme.typography.label.copy(fontSize = 13.sp, fontWeight = FontWeight.Bold, fontFeatureSettings = "tnum"),
                                color = GgTheme.colors.primary,
                            )
                        }
                    },
                    name = name,
                    desc = stringResource(widgetDesc(widget)),
                ) {
                    IconButton(onClick = { onChange(layout.hide(widget)) }, modifier = Modifier.size(40.dp, 44.dp)) {
                        Icon(
                            painterResource(R.drawable.ic_minus_circle),
                            stringResource(R.string.cd_widget_hide, name),
                            tint = GgTheme.colors.dangerText,
                            modifier = Modifier.size(20.dp),
                        )
                    }
                    Box(
                        Modifier
                            .size(44.dp, 48.dp)
                            .pointerInput(layout) {
                                detectDragGestures(
                                    onDragStart = { dragFrom = i; dragDy = 0f },
                                    onDragEnd = {
                                        val to = (dragFrom + (dragDy / rowPx).roundToInt()).coerceIn(0, last)
                                        val from = dragFrom
                                        dragFrom = -1
                                        dragDy = 0f
                                        if (from >= 0) onChange(layout.move(from, to))
                                    },
                                    onDragCancel = { dragFrom = -1; dragDy = 0f },
                                ) { change, amount ->
                                    change.consume()
                                    dragDy += amount.y
                                }
                            },
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            painterResource(R.drawable.ic_drag_handle),
                            stringResource(R.string.cd_widget_drag, name),
                            tint = GgTheme.colors.lineStrong,
                            modifier = Modifier.size(20.dp),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun HiddenRow(widget: HomeWidget, onAdd: () -> Unit) {
    val name = stringResource(widgetName(widget))
    WidgetRow(Modifier, lifted = false, leading = null, name = name, desc = stringResource(widgetDesc(widget))) {
        IconButton(onClick = onAdd, modifier = Modifier.size(40.dp, 44.dp)) {
            Icon(painterResource(R.drawable.ic_plus_circle), stringResource(R.string.cd_widget_add, name), tint = GgTheme.colors.primary, modifier = Modifier.size(20.dp))
        }
    }
}

/** Dòng 64 dp, đệm 8/8/8/12, cách 8: [leading], tên 14/20 600 + mô tả 12/16 một dòng, các nút bên phải. */
@Composable
private fun WidgetRow(
    modifier: Modifier,
    lifted: Boolean,
    leading: (@Composable () -> Unit)?,
    name: String,
    desc: String,
    trailing: @Composable () -> Unit,
) {
    val colors = GgTheme.colors
    val shape = RoundedCornerShape(12.dp)
    Row(
        modifier
            .fillMaxWidth()
            .height(RowHeight)
            .then(
                if (lifted) {
                    Modifier
                        .shadow(12.dp, shape, ambientColor = colors.shadow, spotColor = colors.shadow)
                        .clip(shape)
                        .background(colors.surfaceRaised)
                } else Modifier,
            )
            .padding(start = 12.dp, end = 8.dp, top = 8.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(GgSpacing.space2),
    ) {
        leading?.invoke()
        Column(Modifier.weight(1f).padding(start = if (leading == null) 4.dp else 0.dp)) {
            Text(name, style = GgTheme.typography.body.copy(fontSize = 14.sp, lineHeight = 20.sp, fontWeight = FontWeight.SemiBold), color = colors.ink)
            Text(
                desc,
                style = GgTheme.typography.caption.copy(fontWeight = FontWeight.Normal),
                color = colors.inkMuted,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        trailing()
    }
}

@Preview(widthDp = 390, heightDp = 1200, showBackground = true)
@Composable
private fun HomeCustomizePreview() {
    GgPowerTheme(darkTheme = false) {
        HomeCustomizeScreen(
            HomeLayout(widgets = listOf(HomeWidget.Power, HomeWidget.Summary, HomeWidget.Yield)),
            FakeHomeRepository.SAMPLE.energy,
            10.4,
            onClose = {},
            onApply = {},
        )
    }
}
