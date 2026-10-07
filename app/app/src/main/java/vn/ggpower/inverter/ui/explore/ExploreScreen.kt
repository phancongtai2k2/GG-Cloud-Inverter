package vn.ggpower.inverter.ui.explore

import androidx.annotation.DrawableRes
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import vn.ggpower.inverter.R
import vn.ggpower.inverter.data.explore.FakeExploreRepository
import vn.ggpower.inverter.domain.explore.ExploreArticle
import vn.ggpower.inverter.domain.explore.ExploreIcon
import vn.ggpower.inverter.domain.explore.ExploreProduct
import vn.ggpower.inverter.domain.explore.ExploreTab
import vn.ggpower.inverter.domain.explore.ExploreVideo
import vn.ggpower.inverter.domain.explore.formatVideoDuration
import vn.ggpower.inverter.domain.explore.search
import vn.ggpower.inverter.ui.components.GgCard
import vn.ggpower.inverter.ui.components.ScreenHeader
import vn.ggpower.inverter.ui.components.SearchField
import vn.ggpower.inverter.ui.components.SegmentedControl
import vn.ggpower.inverter.ui.theme.GgPowerTheme
import vn.ggpower.inverter.ui.theme.GgRadius
import vn.ggpower.inverter.ui.theme.GgSpacing
import vn.ggpower.inverter.ui.theme.GgTheme

/** Màn 7 · Khám phá: nối ViewModel với điều hướng. Chạm một mục → màn 29 (chưa thiết kế, placeholder). */
@Composable
fun ExploreRoute(
    onBack: () -> Unit,
    onOpenItem: (itemId: String) -> Unit,
    viewModel: ExploreViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    ExploreScreen(
        state = state,
        onBack = onBack,
        onQueryChange = viewModel::onQueryChange,
        onTabSelected = viewModel::onTabSelected,
        onOpenItem = onOpenItem,
    )
}

/**
 * Màn 7 · Khám phá (Explore.png, html/Explore.dc.html, đặc tả §7). Tiêu đề, ô tìm (đệm 0 20 8) và bộ chọn nổi
 * (cách trên 4) cố định; nội dung bên dưới cuộn dọc, đệm 16 20, các thẻ cách 12.
 */
@Composable
fun ExploreScreen(
    state: ExploreUiState,
    onBack: () -> Unit,
    onQueryChange: (String) -> Unit,
    onTabSelected: (ExploreTab) -> Unit,
    onOpenItem: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = GgTheme.colors
    val navBar = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    Column(modifier.fillMaxSize().background(colors.surface).statusBarsPadding()) {
        ScreenHeader(title = stringResource(R.string.screen_explore), onBack = onBack)
        SearchField(
            value = state.query,
            onValueChange = onQueryChange,
            placeholder = stringResource(R.string.explore_search_placeholder),
            label = stringResource(R.string.explore_search_label),
            modifier = Modifier.padding(start = GgSpacing.space5, end = GgSpacing.space5, bottom = GgSpacing.space2),
        )
        SegmentedControl(
            options = listOf(
                stringResource(R.string.explore_tab_products),
                stringResource(R.string.explore_tab_news),
                stringResource(R.string.explore_tab_videos),
            ),
            selectedIndex = state.tab.ordinal,
            onSelect = { onTabSelected(ExploreTab.entries[it]) },
            groupLabel = stringResource(R.string.explore_tabs_label),
            modifier = Modifier.padding(start = GgSpacing.space5, end = GgSpacing.space5, top = GgSpacing.space1),
        )
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                start = GgSpacing.space5,
                end = GgSpacing.space5,
                top = GgSpacing.space4,
                bottom = GgSpacing.space4 + navBar,
            ),
            verticalArrangement = Arrangement.spacedBy(GgSpacing.space3),
        ) {
            if (state.noResult) {
                item(key = "no-result") {
                    Text(
                        stringResource(R.string.explore_no_result),
                        style = GgTheme.typography.bodySm,
                        color = colors.inkMuted,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth().padding(vertical = GgSpacing.space6),
                    )
                }
            }
            when (state.tab) {
                ExploreTab.Products -> items(state.content.products, key = { it.id }) { ProductCard(it) { onOpenItem(it.id) } }
                ExploreTab.News -> items(state.content.articles, key = { it.id }) { article ->
                    if (article.featured) FeaturedArticleCard(article) { onOpenItem(article.id) }
                    else ArticleCard(article) { onOpenItem(article.id) }
                }
                ExploreTab.Videos -> items(state.content.videos, key = { it.id }) { VideoCard(it) { onOpenItem(it.id) } }
            }
        }
    }
}

/** Cặp màu ô minh hoạ theo prototype: nền nhạt + màu nét icon. */
private data class Tone(val bg: Color, val fg: Color)

@Composable
private fun ExploreIcon.tone(): Tone {
    val c = GgTheme.colors
    return when (this) {
        ExploreIcon.Battery -> Tone(c.primarySoft, c.energyBattery)
        ExploreIcon.SmartBox, ExploreIcon.Article -> Tone(c.primarySoft, c.primary)
        ExploreIcon.Inverter -> Tone(c.infoSoft, c.info)
        ExploreIcon.SolarPanel -> Tone(c.accentSoft, c.accentText)
        ExploreIcon.Accessory -> Tone(c.surfaceSunken, c.inkMuted)
    }
}

@get:DrawableRes
private val ExploreIcon.drawable: Int
    get() = when (this) {
        ExploreIcon.Battery -> R.drawable.ic_explore_battery
        ExploreIcon.SmartBox -> R.drawable.ic_explore_smartbox
        ExploreIcon.Inverter -> R.drawable.ic_explore_inverter
        ExploreIcon.SolarPanel -> R.drawable.ic_explore_solar
        ExploreIcon.Accessory -> R.drawable.ic_explore_accessory
        ExploreIcon.Article -> R.drawable.ic_explore_article
    }

/** Ô minh hoạ vuông bo 12. */
@Composable
private fun IconTile(icon: ExploreIcon, size: Dp, iconSize: Dp) {
    val tone = icon.tone()
    Box(Modifier.size(size).clip(RoundedCornerShape(12.dp)).background(tone.bg), contentAlignment = Alignment.Center) {
        Icon(painterResource(icon.drawable), contentDescription = null, tint = tone.fg, modifier = Modifier.size(iconSize))
    }
}

private val secondaryStyle
    @Composable get() = GgTheme.typography.caption.copy(fontWeight = FontWeight.Normal)

/** Thẻ sản phẩm (đệm 12, cách 12): ô 72 / icon 34, tên 15/22 600, mô tả 12/16, mũi tên 18. */
@Composable
private fun ProductCard(product: ExploreProduct, onClick: () -> Unit) {
    val colors = GgTheme.colors
    GgCard(onClick = onClick, contentPadding = PaddingValues(GgSpacing.space3)) {
        Row(horizontalArrangement = Arrangement.spacedBy(GgSpacing.space3), verticalAlignment = Alignment.CenterVertically) {
            IconTile(product.icon, 72.dp, 34.dp)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(product.name, style = GgTheme.typography.body.copy(lineHeight = 22.sp, fontWeight = FontWeight.SemiBold), color = colors.ink)
                Text(product.description, style = secondaryStyle, color = colors.inkMuted)
            }
            Icon(painterResource(R.drawable.ic_chevron_right), contentDescription = null, tint = colors.inkMuted, modifier = Modifier.size(18.dp))
        }
    }
}

/** Tin NỔI BẬT: ảnh bìa cao 150 phủ kín, rồi (đệm 12 16, cách 4) nhãn NỔI BẬT, tiêu đề 15/22 600, ngày đăng. */
@Composable
private fun FeaturedArticleCard(article: ExploreArticle, onClick: () -> Unit) {
    val colors = GgTheme.colors
    GgCard(onClick = onClick, contentPadding = PaddingValues(0.dp)) {
        // Ảnh mẫu của prototype (assets/explore_banner.jpg) cho tới khi có ảnh bìa từ CMS.
        Image(
            painterResource(R.drawable.explore_banner),
            contentDescription = article.imageDescription.ifEmpty { null },
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxWidth().height(150.dp),
        )
        Column(
            Modifier.padding(horizontal = GgSpacing.space4, vertical = GgSpacing.space3),
            verticalArrangement = Arrangement.spacedBy(GgSpacing.space1),
        ) {
            Text(stringResource(R.string.explore_featured), style = GgTheme.typography.eyebrow, color = colors.primary)
            Text(article.title, style = GgTheme.typography.body.copy(lineHeight = 22.sp, fontWeight = FontWeight.SemiBold), color = colors.ink)
            if (article.dateLabel.isNotEmpty()) Text(article.dateLabel, style = secondaryStyle, color = colors.inkMuted)
        }
    }
}

/** Bài viết (đệm 12, cách 12): ô 64 / icon 28, tiêu đề 14/20 600, "Chuyên mục · N phút đọc" 12/16; không có mũi tên. */
@Composable
private fun ArticleCard(article: ExploreArticle, onClick: () -> Unit) {
    val colors = GgTheme.colors
    GgCard(onClick = onClick, contentPadding = PaddingValues(GgSpacing.space3)) {
        Row(horizontalArrangement = Arrangement.spacedBy(GgSpacing.space3), verticalAlignment = Alignment.CenterVertically) {
            IconTile(article.icon, 64.dp, 28.dp)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(GgSpacing.space1)) {
                Text(article.title, style = itemTitleStyle, color = colors.ink)
                Text(
                    stringResource(R.string.explore_article_meta, article.category, article.readMinutes),
                    style = secondaryStyle,
                    color = colors.inkMuted,
                )
            }
        }
    }
}

private val itemTitleStyle
    @Composable get() = GgTheme.typography.body.copy(fontSize = 14.sp, lineHeight = 20.sp, fontWeight = FontWeight.SemiBold)

/**
 * Video (không có khung thẻ, cách 8): ảnh thu nhỏ cao 190 bo 16 nền nhạt, icon sản phẩm 32 mờ 60 % ở góc (16, 16),
 * nút phát tròn 56 nền trắng có bóng, nhãn thời lượng (góc dưới phải, cách 10) nền `ink` chữ 11/14 600 số đều;
 * bên dưới tiêu đề 14/20 600 và sản phẩm liên quan 12/16.
 */
@Composable
private fun VideoCard(video: ExploreVideo, onClick: () -> Unit) {
    val colors = GgTheme.colors
    val tone = video.icon.tone()
    Column(
        // Không bo góc cả khối: bo 16 sẽ cắt chữ đầu dòng cuối (sản phẩm liên quan).
        Modifier
            .fillMaxWidth()
            .clickable(role = Role.Button, onClick = onClick),
        verticalArrangement = Arrangement.spacedBy(GgSpacing.space2),
    ) {
        Box(
            Modifier.fillMaxWidth().height(190.dp).clip(RoundedCornerShape(GgRadius.radiusLg)).background(tone.bg),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                painterResource(video.icon.drawable),
                contentDescription = null,
                tint = tone.fg,
                modifier = Modifier.align(Alignment.TopStart).padding(GgSpacing.space4).size(32.dp).alpha(0.6f),
            )
            Box(
                Modifier
                    .size(56.dp)
                    .shadow(8.dp, CircleShape, ambientColor = colors.shadow, spotColor = colors.shadow)
                    .clip(CircleShape)
                    .background(colors.surfaceRaised),
                contentAlignment = Alignment.Center,
            ) {
                Icon(painterResource(R.drawable.ic_play), contentDescription = null, tint = colors.ink, modifier = Modifier.size(24.dp))
            }
            Text(
                formatVideoDuration(video.durationSeconds),
                style = GgTheme.typography.tabLabel.copy(fontFeatureSettings = "tnum"),
                color = colors.surfaceRaised,
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(10.dp)
                    .clip(RoundedCornerShape(GgRadius.radiusSm))
                    .background(colors.ink)
                    .padding(horizontal = 6.dp, vertical = 2.dp),
            )
        }
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(video.title, style = itemTitleStyle, color = colors.ink)
            Text(video.relatedProduct, style = secondaryStyle, color = colors.inkMuted)
        }
    }
}

@Composable
private fun PreviewExplore(tab: ExploreTab, query: String = "") {
    GgPowerTheme(darkTheme = false) {
        ExploreScreen(
            ExploreUiState(tab = tab, query = query, content = FakeExploreRepository.SAMPLE.search(query)),
            onBack = {}, onQueryChange = {}, onTabSelected = {}, onOpenItem = {},
        )
    }
}

@Preview(widthDp = 390, heightDp = 844, showBackground = true, backgroundColor = 0xFFF5F7F3)
@Composable
private fun ExploreProductsPreview() = PreviewExplore(ExploreTab.Products)

@Preview(widthDp = 390, heightDp = 844, showBackground = true, backgroundColor = 0xFFF5F7F3)
@Composable
private fun ExploreNewsPreview() = PreviewExplore(ExploreTab.News)

@Preview(widthDp = 390, heightDp = 844, showBackground = true, backgroundColor = 0xFFF5F7F3)
@Composable
private fun ExploreVideosPreview() = PreviewExplore(ExploreTab.Videos)

@Preview(widthDp = 390, heightDp = 844, showBackground = true, backgroundColor = 0xFFF5F7F3)
@Composable
private fun ExploreNoResultPreview() = PreviewExplore(ExploreTab.Products, query = "xyz")
