package vn.ggpower.inverter.ui.projects

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import vn.ggpower.inverter.R
import vn.ggpower.inverter.data.project.FakeProjectRepository
import vn.ggpower.inverter.ui.components.ButtonSize
import vn.ggpower.inverter.ui.components.FloatingTabBar
import vn.ggpower.inverter.ui.components.FloatingTabBarContentPadding
import vn.ggpower.inverter.ui.components.GgCard
import vn.ggpower.inverter.ui.components.PrimaryButton
import vn.ggpower.inverter.ui.components.ScreenHeader
import vn.ggpower.inverter.ui.components.SearchField
import vn.ggpower.inverter.ui.components.SecondaryButton
import vn.ggpower.inverter.ui.components.TabItem
import vn.ggpower.inverter.ui.theme.GgPowerTheme
import vn.ggpower.inverter.ui.theme.GgSpacing
import vn.ggpower.inverter.ui.theme.GgTheme

/** Màn 2 · Dự án: nối ViewModel với điều hướng. */
@Composable
fun ProjectsRoute(
    tabs: List<TabItem>,
    onOpenProject: (projectId: String) -> Unit,
    onAddProject: () -> Unit,
    viewModel: ProjectsViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    // Quay lại từ luồng Thêm dự án: tải lại để thấy dự án mới.
    LifecycleResumeEffect(viewModel) {
        viewModel.refresh()
        onPauseOrDispose { }
    }
    ProjectsScreen(
        state = state,
        tabs = tabs,
        onQueryChange = viewModel::onQueryChange,
        onRetry = viewModel::retry,
        onOpenProject = onOpenProject,
        onAddProject = onAddProject,
    )
}

/**
 * Màn 2 · Dự án (Projects.png, ProjectsEmpty.png, đặc tả §3.2). Đầu trang có nút + Thêm dự án; ô tìm kiếm và danh sách
 * thẻ cách nhau 12 dp, lề 20 dp, chừa 108 dp cho thanh thẻ. Không có dự án → minh hoạ + nút "Thêm dự án đầu tiên".
 */
@Composable
fun ProjectsScreen(
    state: ProjectsUiState,
    tabs: List<TabItem>,
    onQueryChange: (String) -> Unit,
    onRetry: () -> Unit,
    onOpenProject: (String) -> Unit,
    onAddProject: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = GgTheme.colors
    Box(modifier.fillMaxSize().background(colors.surface)) {
        Column(Modifier.fillMaxSize().statusBarsPadding()) {
            ScreenHeader(title = stringResource(R.string.screen_projects)) {
                IconButton(onClick = onAddProject, modifier = Modifier.size(48.dp)) {
                    Icon(
                        painterResource(R.drawable.ic_add),
                        contentDescription = stringResource(R.string.cd_add_project),
                        tint = colors.ink,
                    )
                }
            }
            when (state) {
                ProjectsUiState.Loading -> LoadingList()
                is ProjectsUiState.Error -> ErrorBlock(state.noConnection, onRetry)
                is ProjectsUiState.Content ->
                    if (state.isEmpty) EmptyBlock(onAddProject)
                    else ProjectList(state, onQueryChange, onOpenProject)
            }
        }
        FloatingTabBar(items = tabs, modifier = Modifier.align(Alignment.BottomCenter))
    }
}

@Composable
private fun ProjectList(
    state: ProjectsUiState.Content,
    onQueryChange: (String) -> Unit,
    onOpenProject: (String) -> Unit,
) {
    val visible = state.visible
    LazyColumn(
        contentPadding = PaddingValues(
            start = GgSpacing.space5, end = GgSpacing.space5, bottom = FloatingTabBarContentPadding,
        ),
        verticalArrangement = Arrangement.spacedBy(GgSpacing.space3),
    ) {
        item(key = "search") {
            SearchField(
                value = state.query,
                onValueChange = onQueryChange,
                placeholder = stringResource(R.string.projects_search_placeholder),
                label = stringResource(R.string.projects_search_label),
            )
        }
        items(visible, key = { it.id }) { project ->
            ProjectCard(project, onClick = { onOpenProject(project.id) })
        }
        if (visible.isEmpty()) {
            item(key = "no-result") {
                Text(
                    stringResource(R.string.projects_no_result),
                    style = GgTheme.typography.bodySm,
                    color = GgTheme.colors.inkMuted,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth().padding(vertical = GgSpacing.space6),
                )
            }
        }
    }
}

/** ProjectsEmpty.dc.html: khối giữa màn, lề ngang 32 dp, đáy 140 dp, các phần cách 16 dp. */
@Composable
private fun EmptyBlock(onAddProject: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(start = GgSpacing.space8, end = GgSpacing.space8, bottom = 140.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(GgSpacing.space4, Alignment.CenterVertically),
    ) {
        Image(painterResource(R.drawable.ill_projects_empty), contentDescription = null, modifier = Modifier.size(180.dp, 130.dp))
        Text(
            stringResource(R.string.projects_empty_title),
            style = GgTheme.typography.body.copy(fontSize = 20.sp, lineHeight = 28.sp, fontWeight = FontWeight.Bold),
            color = GgTheme.colors.ink,
            textAlign = TextAlign.Center,
            modifier = Modifier.semantics { heading() },
        )
        Text(
            stringResource(R.string.projects_empty_body),
            style = GgTheme.typography.body.copy(fontSize = 14.sp, lineHeight = 22.sp),
            color = GgTheme.colors.inkMuted,
            textAlign = TextAlign.Center,
        )
        PrimaryButton(
            text = stringResource(R.string.projects_empty_action),
            onClick = onAddProject,
            icon = R.drawable.ic_plus,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

/** Khung xương đúng hình thẻ (§3.34), không dùng vòng quay toàn màn. */
@Composable
private fun LoadingList() {
    val sunken = GgTheme.colors.surfaceSunken
    Column(
        Modifier.padding(horizontal = GgSpacing.space5),
        verticalArrangement = Arrangement.spacedBy(GgSpacing.space3),
    ) {
        Box(Modifier.fillMaxWidth().height(44.dp).clip(RoundedCornerShape(10.dp)).background(sunken))
        repeat(3) {
            GgCard(verticalSpacing = GgSpacing.space3) {
                Box(Modifier.fillMaxWidth(0.6f).height(16.dp).clip(RoundedCornerShape(6.dp)).background(sunken))
                Box(Modifier.fillMaxWidth(0.8f).height(12.dp).clip(RoundedCornerShape(6.dp)).background(sunken))
                Box(Modifier.fillMaxWidth().height(36.dp).clip(RoundedCornerShape(6.dp)).background(sunken))
            }
        }
    }
}

@Composable
private fun ErrorBlock(noConnection: Boolean, onRetry: () -> Unit) {
    Column(
        Modifier.fillMaxSize().padding(start = GgSpacing.space8, end = GgSpacing.space8, bottom = 140.dp),
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

// ---------- Preview ----------

@Composable
private fun previewTabs() = listOf(
    TabItem(stringResource(R.string.tab_projects), R.drawable.ic_tab_projects, selected = true) {},
    TabItem(stringResource(R.string.tab_me), R.drawable.ic_tab_me, selected = false) {},
)

@Composable
private fun ProjectsPreviewHost(state: ProjectsUiState) {
    GgPowerTheme(darkTheme = false) {
        ProjectsScreen(state, previewTabs(), {}, {}, {}, {})
    }
}

@Preview(name = "Có dữ liệu (Projects.png)", widthDp = 390, heightDp = 844, showBackground = true)
@Composable
private fun ProjectsPreview() = ProjectsPreviewHost(ProjectsUiState.Content(FakeProjectRepository.SAMPLE))

@Preview(name = "Rỗng (ProjectsEmpty.png)", widthDp = 390, heightDp = 844, showBackground = true)
@Composable
private fun ProjectsEmptyPreview() = ProjectsPreviewHost(ProjectsUiState.Content(emptyList()))

@Preview(name = "Tìm không thấy", widthDp = 390, heightDp = 844, showBackground = true)
@Composable
private fun ProjectsNoResultPreview() = ProjectsPreviewHost(ProjectsUiState.Content(FakeProjectRepository.SAMPLE, query = "Hà Nội"))

@Preview(name = "Đang tải", widthDp = 390, heightDp = 844, showBackground = true)
@Composable
private fun ProjectsLoadingPreview() = ProjectsPreviewHost(ProjectsUiState.Loading)

@Preview(name = "Lỗi", widthDp = 390, heightDp = 844, showBackground = true)
@Composable
private fun ProjectsErrorPreview() = ProjectsPreviewHost(ProjectsUiState.Error(noConnection = true))
