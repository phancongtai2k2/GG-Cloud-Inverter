package vn.ggpower.inverter.ui.service

import android.content.ClipData
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.ClipEntry
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.launch
import vn.ggpower.inverter.R
import vn.ggpower.inverter.data.support.FakeSupportContactRepository
import vn.ggpower.inverter.ui.components.FloatingTabBar
import vn.ggpower.inverter.ui.components.FloatingTabBarContentPadding
import vn.ggpower.inverter.ui.components.GgCard
import vn.ggpower.inverter.ui.components.ScreenHeader
import vn.ggpower.inverter.ui.components.SectionHeader
import vn.ggpower.inverter.ui.components.TabItem
import vn.ggpower.inverter.ui.theme.GgPowerTheme
import vn.ggpower.inverter.ui.theme.GgRadius
import vn.ggpower.inverter.ui.theme.GgSpacing
import vn.ggpower.inverter.ui.theme.GgTheme

/** Màn 6 · Dịch vụ: nối ViewModel với điều hướng; ghi bộ nhớ tạm rồi báo ViewModel hiện phản hồi. */
@Composable
fun ServiceRoute(
    tabs: List<TabItem>,
    onBackToProjects: () -> Unit,
    onOpenExplore: () -> Unit,
    onOpenSupport: () -> Unit,
    onOpenWarranty: () -> Unit,
    viewModel: ServiceViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val clipboard = LocalClipboard.current
    val scope = rememberCoroutineScope()
    ServiceScreen(
        state = state,
        tabs = tabs,
        onBackToProjects = onBackToProjects,
        onCopy = { field ->
            val text = when (field) {
                ContactField.Hotline -> state.contact.hotline
                ContactField.Email -> state.contact.email
            }
            scope.launch {
                clipboard.setClipEntry(ClipEntry(ClipData.newPlainText(field.name, text)))
                viewModel.onCopied(field)
            }
        },
        onOpenExplore = onOpenExplore,
        onOpenSupport = onOpenSupport,
        onOpenWarranty = onOpenWarranty,
    )
}

/**
 * Màn 6 · Dịch vụ (Service.png, html/Service.dc.html, đặc tả §6). Nút ← về Dự án (ADR-003, BO_SUNG §10).
 * Lề 20, các khối cách 12, chừa 108 dp cho thanh thẻ: box "Hỗ trợ GG Power" (Hotline, Email có nút sao chép), thông báo
 * "Đã sao chép…", mục TIỆN ÍCH với ba thẻ Khám phá (7) · Hỗ trợ (8) · Bảo hành (9).
 */
@Composable
fun ServiceScreen(
    state: ServiceUiState,
    tabs: List<TabItem>,
    onBackToProjects: () -> Unit,
    onCopy: (ContactField) -> Unit,
    onOpenExplore: () -> Unit,
    onOpenSupport: () -> Unit,
    onOpenWarranty: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = GgTheme.colors
    Box(modifier.fillMaxSize().background(colors.surface)) {
        Column(Modifier.fillMaxSize().statusBarsPadding()) {
            ScreenHeader(
                title = stringResource(R.string.screen_service),
                onBack = onBackToProjects,
                backDescription = stringResource(R.string.cd_back_to_projects),
            )
            Column(
                Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(start = GgSpacing.space5, end = GgSpacing.space5, bottom = FloatingTabBarContentPadding),
                verticalArrangement = Arrangement.spacedBy(GgSpacing.space3),
            ) {
                ContactCard(state, onCopy)
                CopiedToast(state.copied)
                SectionHeader(stringResource(R.string.service_section_tools), Modifier.padding(top = GgSpacing.space1))
                ToolCard(R.drawable.ic_compass, colors.infoSoft, colors.info, R.string.service_explore, R.string.service_explore_desc, onOpenExplore)
                ToolCard(R.drawable.ic_ticket, colors.accentSoft, colors.accentText, R.string.service_support, R.string.service_support_desc, onOpenSupport)
                ToolCard(R.drawable.ic_warranty, colors.primarySoft, colors.primary, R.string.service_warranty, R.string.service_warranty_desc, onOpenWarranty)
            }
        }
        FloatingTabBar(items = tabs, modifier = Modifier.align(Alignment.BottomCenter))
    }
}

private val titleStyle
    @Composable get() = GgTheme.typography.body.copy(lineHeight = 22.sp, fontWeight = FontWeight.SemiBold)

/** Box liên hệ: tiêu đề 15/22 600 (đệm 14 16 6); hai dòng cách nhau bằng viền 1 dp. */
@Composable
private fun ContactCard(state: ServiceUiState, onCopy: (ContactField) -> Unit) {
    GgCard(contentPadding = PaddingValues(0.dp)) {
        Text(
            stringResource(R.string.service_contact_title),
            style = titleStyle,
            color = GgTheme.colors.ink,
            modifier = Modifier
                .padding(start = GgSpacing.space4, end = GgSpacing.space4, top = 14.dp, bottom = 6.dp)
                .semantics { heading() },
        )
        ContactRow(
            icon = R.drawable.ic_phone,
            label = R.string.service_hotline_label,
            value = state.contact.hotline,
            copyDescription = R.string.cd_copy_hotline,
            copiedDescription = R.string.service_copied_hotline,
            copied = state.copied == ContactField.Hotline,
            onCopy = { onCopy(ContactField.Hotline) },
        )
        HorizontalDivider(thickness = 1.dp, color = GgTheme.colors.cardBorder)
        ContactRow(
            icon = R.drawable.ic_mail,
            label = R.string.service_email_label,
            value = state.contact.email,
            copyDescription = R.string.cd_copy_email,
            copiedDescription = R.string.service_copied_email,
            copied = state.copied == ContactField.Email,
            onCopy = { onCopy(ContactField.Email) },
        )
    }
}

/**
 * Dòng liên hệ (đệm 10 8 10 16, cách 12): icon tròn 40 nền `primarySoft`, nhãn 12/16 500, giá trị 15/22 600 số đều;
 * nút sao chép tròn 44 (vùng chạm 48) — đã sao chép thì dấu ✓ màu `primary` trên nền `primarySoft`.
 */
@Composable
private fun ContactRow(
    @DrawableRes icon: Int,
    @StringRes label: Int,
    value: String,
    @StringRes copyDescription: Int,
    @StringRes copiedDescription: Int,
    copied: Boolean,
    onCopy: () -> Unit,
) {
    val colors = GgTheme.colors
    Row(
        Modifier.fillMaxWidth().padding(start = GgSpacing.space4, end = 6.dp, top = 10.dp, bottom = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(GgSpacing.space3),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(40.dp).clip(CircleShape).background(colors.primarySoft), contentAlignment = Alignment.Center) {
            Icon(painterResource(icon), contentDescription = null, tint = colors.primary, modifier = Modifier.size(20.dp))
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(stringResource(label), style = GgTheme.typography.caption, color = colors.inkMuted)
            Text(value, style = titleStyle.copy(fontFeatureSettings = "tnum"), color = colors.ink)
        }
        Box(
            Modifier
                .size(48.dp)
                .clip(CircleShape)
                .clickable(role = Role.Button, onClick = onCopy),
            contentAlignment = Alignment.Center,
        ) {
            Box(
                Modifier.size(44.dp).clip(CircleShape).background(if (copied) colors.primarySoft else Color.Transparent),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    painterResource(if (copied) R.drawable.ic_check_thin else R.drawable.ic_copy),
                    contentDescription = stringResource(if (copied) copiedDescription else copyDescription),
                    tint = if (copied) colors.primary else colors.inkMuted,
                    modifier = Modifier.size(20.dp),
                )
            }
        }
    }
}

/** Thông báo nhỏ giữa trang: viên tròn nền `ink`, chữ 12/16 600 màu nền sáng, dấu ✓ 16; đệm 6 12. */
@Composable
private fun CopiedToast(copied: ContactField?) {
    val colors = GgTheme.colors
    // Giữ dòng của lần sao chép cuối để lúc mờ dần chữ không bị trống.
    val last = remember { arrayOfNulls<ContactField>(1) }
    if (copied != null) last[0] = copied
    val text = when (last[0]) {
        ContactField.Hotline -> stringResource(R.string.service_copied_hotline)
        ContactField.Email -> stringResource(R.string.service_copied_email)
        null -> ""
    }
    AnimatedVisibility(visible = copied != null, enter = fadeIn(), exit = fadeOut(), modifier = Modifier.fillMaxWidth()) {
        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            Row(
                Modifier
                    .clip(RoundedCornerShape(GgRadius.radiusPill))
                    .background(colors.ink)
                    .padding(horizontal = GgSpacing.space3, vertical = 6.dp)
                    .semantics { liveRegion = LiveRegionMode.Polite },
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(painterResource(R.drawable.ic_check_thin), contentDescription = null, tint = colors.surfaceRaised, modifier = Modifier.size(16.dp))
                Text(text, style = GgTheme.typography.caption.copy(fontWeight = FontWeight.SemiBold), color = colors.surfaceRaised)
            }
        }
    }
}

/** Thẻ tiện ích (cao tối thiểu 72, đệm 14 12 14 16, cách 14): ô icon 44 bo 12, tiêu đề 15/22 600, mô tả 12/16, mũi tên 18. */
@Composable
private fun ToolCard(
    @DrawableRes icon: Int,
    tileBg: Color,
    tileFg: Color,
    @StringRes title: Int,
    @StringRes desc: Int,
    onClick: () -> Unit,
) {
    val colors = GgTheme.colors
    val titleText = stringResource(title)
    GgCard(onClick = onClick, contentPadding = PaddingValues(0.dp)) {
        Row(
            Modifier.fillMaxWidth().heightIn(min = 72.dp).padding(start = GgSpacing.space4, end = GgSpacing.space3, top = 14.dp, bottom = 14.dp),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(Modifier.size(44.dp).clip(RoundedCornerShape(12.dp)).background(tileBg), contentAlignment = Alignment.Center) {
                Icon(painterResource(icon), contentDescription = null, tint = tileFg, modifier = Modifier.size(22.dp))
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(titleText, style = titleStyle, color = colors.ink)
                Text(stringResource(desc), style = GgTheme.typography.caption.copy(fontWeight = FontWeight.Normal), color = colors.inkMuted)
            }
            Icon(painterResource(R.drawable.ic_chevron_right), contentDescription = null, tint = colors.inkMuted, modifier = Modifier.size(18.dp))
        }
    }
}

@Composable
internal fun previewServiceTabs() = listOf(
    TabItem(stringResource(R.string.tab_main), R.drawable.ic_tab_main, false) {},
    TabItem(stringResource(R.string.tab_devices), R.drawable.ic_tab_devices, false) {},
    TabItem(stringResource(R.string.tab_service), R.drawable.ic_tab_service, true) {},
)

@Preview(widthDp = 390, heightDp = 844, showBackground = true, backgroundColor = 0xFFF5F7F3)
@Composable
private fun ServiceScreenPreview() {
    GgPowerTheme(darkTheme = false) {
        ServiceScreen(ServiceUiState(FakeSupportContactRepository.SAMPLE), previewServiceTabs(), {}, {}, {}, {}, {})
    }
}

@Preview(widthDp = 390, heightDp = 844, showBackground = true, backgroundColor = 0xFFF5F7F3)
@Composable
private fun ServiceScreenCopiedPreview() {
    GgPowerTheme(darkTheme = false) {
        ServiceScreen(ServiceUiState(FakeSupportContactRepository.SAMPLE, copied = ContactField.Hotline), previewServiceTabs(), {}, {}, {}, {}, {})
    }
}
