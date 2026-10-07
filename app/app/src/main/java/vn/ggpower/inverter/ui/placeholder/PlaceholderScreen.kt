package vn.ggpower.inverter.ui.placeholder

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import vn.ggpower.inverter.R
import vn.ggpower.inverter.ui.components.FloatingTabBar
import vn.ggpower.inverter.ui.components.FloatingTabBarContentPadding
import vn.ggpower.inverter.ui.components.ScreenHeader
import vn.ggpower.inverter.ui.components.TabItem
import vn.ggpower.inverter.ui.theme.GgPowerTheme
import vn.ggpower.inverter.ui.theme.GgRadius
import vn.ggpower.inverter.ui.theme.GgSpacing
import vn.ggpower.inverter.ui.theme.GgTheme

/** Một liên kết sang màn khác (theo bảng liên kết của `docs/design/navigation.md`). */
data class PlaceholderLink(val label: String, val onClick: () -> Unit)

/**
 * Màn tạm cho Bước 0: đầu trang đúng thiết kế, số màn, và các nút tới màn đích để thử điều hướng.
 * Mỗi màn sẽ được thay bằng màn thật khi dựng (`/dung-man`).
 */
@Composable
fun PlaceholderScreen(
    title: String,
    number: String,
    modifier: Modifier = Modifier,
    onBack: (() -> Unit)? = null,
    backDescription: String? = null,
    links: List<PlaceholderLink> = emptyList(),
    tabs: List<TabItem>? = null,
) {
    val colors = GgTheme.colors
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(colors.surface),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .verticalScroll(rememberScrollState())
                .padding(bottom = if (tabs != null) FloatingTabBarContentPadding else GgSpacing.space6),
        ) {
            ScreenHeader(title = title, onBack = onBack, backDescription = backDescription)
            Text(
                text = stringResource(R.string.placeholder_caption, number),
                style = GgTheme.typography.caption,
                color = colors.inkMuted,
                modifier = Modifier.padding(horizontal = GgSpacing.space5),
            )
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = GgSpacing.space5, vertical = GgSpacing.space4),
                verticalArrangement = Arrangement.spacedBy(GgSpacing.space2),
            ) {
                links.forEach { link ->
                    OutlinedButton(
                        onClick = link.onClick,
                        shape = RoundedCornerShape(GgRadius.radiusMd),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = colors.primary),
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 48.dp),
                    ) {
                        Text(text = link.label, style = GgTheme.typography.label)
                    }
                }
            }
        }
        if (tabs != null) {
            FloatingTabBar(items = tabs, modifier = Modifier.align(Alignment.BottomCenter))
        }
    }
}

@Preview(widthDp = 390, heightDp = 844, showBackground = true)
@Composable
private fun PlaceholderProjectsPreview() {
    GgPowerTheme(darkTheme = false) {
        PlaceholderScreen(
            title = stringResource(R.string.screen_projects),
            number = "2",
            links = listOf(
                PlaceholderLink(stringResource(R.string.screen_main)) {},
                PlaceholderLink(stringResource(R.string.add_project_title)) {},
            ),
            tabs = listOf(
                TabItem(stringResource(R.string.tab_projects), R.drawable.ic_tab_projects, selected = true) {},
                TabItem(stringResource(R.string.tab_me), R.drawable.ic_tab_me, selected = false) {},
            ),
        )
    }
}

@Preview(widthDp = 390, heightDp = 844, showBackground = true)
@Composable
private fun PlaceholderSubScreenPreview() {
    GgPowerTheme(darkTheme = false) {
        PlaceholderScreen(
            title = stringResource(R.string.screen_config),
            number = "16",
            onBack = {},
            links = listOf(PlaceholderLink(stringResource(R.string.screen_tou_schedule)) {}),
        )
    }
}
