package vn.ggpower.inverter.ui.components

import androidx.annotation.DrawableRes
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import vn.ggpower.inverter.R
import vn.ggpower.inverter.ui.theme.GgPowerTheme
import vn.ggpower.inverter.ui.theme.GgRadius
import vn.ggpower.inverter.ui.theme.GgSpacing
import vn.ggpower.inverter.ui.theme.GgTheme

data class TabItem(
    val label: String,
    @param:DrawableRes val icon: Int,
    val selected: Boolean,
    val onClick: () -> Unit,
)

/**
 * Khoảng nội dung phải chừa ở đáy để không bị thanh thẻ che (đặc tả mục 4: 108 dp), cộng phần thanh điều hướng của
 * hệ thống vì thanh thẻ nằm trên phần đó (`navigationBarsPadding`).
 */
val FloatingTabBarContentPadding: Dp
    @Composable get() = 108.dp + WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()

/**
 * Thanh thẻ nổi dạng viên thuốc (gg-inverter-design: Projects.dc.html, Main.dc.html): cách mép 20 dp, cao 68 dp,
 * đệm 6 dp, khe 4 dp, viền 1 dp `cardBorder`, bóng overlay. Thẻ đang chọn rộng gấp 2,2 lần, nền `primarySoft`, icon +
 * chữ 13/18 600 màu `primary`; các thẻ khác chỉ có icon màu `inkMuted` (nhãn đọc bằng trình đọc màn hình).
 */
@Composable
fun FloatingTabBar(items: List<TabItem>, modifier: Modifier = Modifier) {
    val colors = GgTheme.colors
    val shape = RoundedCornerShape(GgRadius.radiusPill)
    val navLabel = stringResource(R.string.cd_main_navigation)
    Row(
        modifier = modifier
            .navigationBarsPadding()
            .padding(start = GgSpacing.space5, end = GgSpacing.space5, bottom = GgSpacing.space5)
            .fillMaxWidth()
            .height(68.dp)
            .shadow(elevation = 12.dp, shape = shape, ambientColor = colors.shadow, spotColor = colors.shadow)
            .clip(shape)
            .background(colors.surfaceRaised)
            .border(1.dp, colors.cardBorder, shape)
            .padding(6.dp)
            .semantics { contentDescription = navLabel }
            .selectableGroup(),
        horizontalArrangement = Arrangement.spacedBy(GgSpacing.space1),
    ) {
        items.forEach { item -> TabCell(item, Modifier.weight(if (item.selected) 2.2f else 1f)) }
    }
}

@Composable
private fun TabCell(item: TabItem, modifier: Modifier) {
    val colors = GgTheme.colors
    val tint = if (item.selected) colors.primary else colors.inkMuted
    Row(
        modifier = modifier
            .height(56.dp)
            .clip(RoundedCornerShape(GgRadius.radiusPill))
            .then(if (item.selected) Modifier.background(colors.primarySoft) else Modifier)
            .selectable(selected = item.selected, role = Role.Tab, onClick = item.onClick)
            .then(if (item.selected) Modifier else Modifier.semantics { contentDescription = item.label })
            .padding(horizontal = GgSpacing.space3),
        horizontalArrangement = Arrangement.spacedBy(GgSpacing.space2, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            painter = painterResource(item.icon),
            contentDescription = null,
            tint = tint,
            modifier = Modifier.size(22.dp),
        )
        if (item.selected) {
            Text(
                text = item.label,
                style = GgTheme.typography.label.copy(lineHeight = 18.sp),
                color = tint,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Preview(widthDp = 390, heightDp = 160, showBackground = true, backgroundColor = 0xFFF5F7F3)
@Composable
private fun FloatingTabBarAccountPreview() {
    GgPowerTheme(darkTheme = false) {
        Box(Modifier.fillMaxSize()) {
            FloatingTabBar(
                items = listOf(
                    TabItem(stringResource(R.string.tab_projects), R.drawable.ic_tab_projects, selected = true) {},
                    TabItem(stringResource(R.string.tab_me), R.drawable.ic_tab_me, selected = false) {},
                ),
                modifier = Modifier.align(Alignment.BottomCenter),
            )
        }
    }
}

@Preview(widthDp = 390, heightDp = 160, showBackground = true, backgroundColor = 0xFFF5F7F3)
@Composable
private fun FloatingTabBarProjectPreview() {
    GgPowerTheme(darkTheme = false) {
        Box(Modifier.fillMaxSize()) {
            FloatingTabBar(
                items = listOf(
                    TabItem(stringResource(R.string.tab_main), R.drawable.ic_tab_main, selected = true) {},
                    TabItem(stringResource(R.string.tab_devices), R.drawable.ic_tab_devices, selected = false) {},
                    TabItem(stringResource(R.string.tab_service), R.drawable.ic_tab_service, selected = false) {},
                ),
                modifier = Modifier.align(Alignment.BottomCenter),
            )
        }
    }
}
