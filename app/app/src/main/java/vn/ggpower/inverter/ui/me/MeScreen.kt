package vn.ggpower.inverter.ui.me

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.delay
import vn.ggpower.inverter.R
import vn.ggpower.inverter.data.account.FakeProfileRepository
import vn.ggpower.inverter.domain.account.NotificationPrefs
import vn.ggpower.inverter.domain.account.UserProfile
import vn.ggpower.inverter.ui.components.Avatar
import vn.ggpower.inverter.domain.settings.AppLanguage
import vn.ggpower.inverter.domain.settings.TemperatureUnit
import vn.ggpower.inverter.domain.settings.ThemeMode
import vn.ggpower.inverter.ui.components.DangerOutlineButton
import vn.ggpower.inverter.ui.components.FloatingTabBar
import vn.ggpower.inverter.ui.components.FloatingTabBarContentPadding
import vn.ggpower.inverter.ui.components.GgCard
import vn.ggpower.inverter.ui.components.GgToggle
import vn.ggpower.inverter.ui.components.ScreenHeader
import vn.ggpower.inverter.ui.components.SectionHeader
import vn.ggpower.inverter.ui.components.SegmentedControl
import vn.ggpower.inverter.ui.components.TabItem
import vn.ggpower.inverter.ui.theme.GgPowerTheme
import vn.ggpower.inverter.ui.theme.GgSpacing
import vn.ggpower.inverter.ui.theme.GgTheme

/** Màn 3 · Tôi: nối ViewModel với điều hướng. */
@Composable
fun MeRoute(
    tabs: List<TabItem>,
    onOpenAccount: () -> Unit,
    onLogout: () -> Unit,
    viewModel: MeViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    MeScreen(
        state = state,
        tabs = tabs,
        onOpenAccount = onOpenAccount,
        onNotificationsChange = viewModel::onNotificationsChange,
        onTemperatureUnit = viewModel::onTemperatureUnit,
        onThemeMode = viewModel::onThemeMode,
        onToggleLanguageList = viewModel::onToggleLanguageList,
        onLanguage = viewModel::onLanguage,
        onLogout = onLogout,
    )
}

/**
 * Màn 3 · Tôi (Me.png, Me__language-open.png; đặc tả §3.3; Thay đổi 0.6 §7). Lề 20, các khối cách 8, tiêu đề mục
 * cách thêm 4 ở trên; chừa 108 dp cho thanh thẻ. Thẻ hồ sơ, Thông báo (3 công tắc), Cài đặt hệ thống (°C/°F, giao diện,
 * ngôn ngữ thả xuống trong thẻ), nút Đăng xuất viền đỏ.
 */
@Composable
fun MeScreen(
    state: MeUiState,
    tabs: List<TabItem>,
    onOpenAccount: () -> Unit,
    onNotificationsChange: (NotificationPrefs) -> Unit,
    onTemperatureUnit: (TemperatureUnit) -> Unit,
    onThemeMode: (ThemeMode) -> Unit,
    onToggleLanguageList: () -> Unit,
    onLanguage: (AppLanguage) -> Unit,
    onLogout: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = GgTheme.colors
    Box(modifier.fillMaxSize().background(colors.surface)) {
        Column(Modifier.fillMaxSize().statusBarsPadding()) {
            ScreenHeader(title = stringResource(R.string.screen_me))
            Column(
                Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(start = GgSpacing.space5, end = GgSpacing.space5, bottom = FloatingTabBarContentPadding),
                verticalArrangement = Arrangement.spacedBy(GgSpacing.space2),
            ) {
                ProfileCard(state.profile, onOpenAccount)
                SectionHeader(stringResource(R.string.me_section_notifications), Modifier.padding(top = GgSpacing.space1))
                NotificationsCard(state.notifications, onNotificationsChange)
                SectionHeader(stringResource(R.string.me_section_system), Modifier.padding(top = GgSpacing.space1))
                SystemCard(state, onTemperatureUnit, onThemeMode, onToggleLanguageList, onLanguage)
                DangerOutlineButton(stringResource(R.string.me_logout), onClick = onLogout, icon = R.drawable.ic_logout)
            }
        }
        FloatingTabBar(items = tabs, modifier = Modifier.align(Alignment.BottomCenter))
    }
}

private val rowTitle
    @Composable get() = GgTheme.typography.body.copy(fontSize = 14.sp, lineHeight = 20.sp, fontWeight = FontWeight.Medium)

private val rowDesc
    @Composable get() = GgTheme.typography.caption.copy(fontWeight = FontWeight.Normal)

@Composable
private fun CardDivider() = HorizontalDivider(thickness = 1.dp, color = GgTheme.colors.cardBorder)

/** Thẻ hồ sơ: avatar 52 chữ viết tắt 18/700, tên 16/22 600, email và SĐT 12/16; dòng "Thông tin tài khoản" cao 52. */
@Composable
private fun ProfileCard(profile: UserProfile?, onOpenAccount: () -> Unit) {
    val colors = GgTheme.colors
    GgCard(contentPadding = PaddingValues(0.dp)) {
        Row(
            Modifier.fillMaxWidth().padding(GgSpacing.space4),
            horizontalArrangement = Arrangement.spacedBy(GgSpacing.space3),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (profile != null) {
                Avatar(
                    profile.name,
                    profile.avatarUri,
                    size = 52.dp,
                    textStyle = GgTheme.typography.body.copy(fontSize = 18.sp, lineHeight = 22.sp, fontWeight = FontWeight.Bold),
                )
            } else {
                Box(Modifier.size(52.dp).clip(CircleShape).background(colors.surfaceSunken))
            }
            if (profile != null) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(
                        profile.name,
                        style = GgTheme.typography.body.copy(fontSize = 16.sp, lineHeight = 22.sp, fontWeight = FontWeight.SemiBold),
                        color = colors.ink,
                    )
                    Text(profile.email, style = rowDesc, color = colors.inkMuted)
                    Text(profile.phone, style = rowDesc, color = colors.inkMuted)
                }
            } else {
                // Đang tải hồ sơ: khung giữ chỗ cùng kích thước ba dòng chữ.
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Box(Modifier.fillMaxWidth(0.55f).height(16.dp).clip(RoundedCornerShape(6.dp)).background(colors.surfaceSunken))
                    Box(Modifier.fillMaxWidth(0.4f).height(12.dp).clip(RoundedCornerShape(6.dp)).background(colors.surfaceSunken))
                    Box(Modifier.fillMaxWidth(0.35f).height(12.dp).clip(RoundedCornerShape(6.dp)).background(colors.surfaceSunken))
                }
            }
        }
        CardDivider()
        Row(
            Modifier
                .fillMaxWidth()
                .heightIn(min = 52.dp)
                .clickable(role = Role.Button, onClick = onOpenAccount)
                .padding(horizontal = GgSpacing.space4),
            horizontalArrangement = Arrangement.spacedBy(GgSpacing.space3),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(painterResource(R.drawable.ic_tab_me), contentDescription = null, tint = colors.inkMuted, modifier = Modifier.size(20.dp))
            Text(stringResource(R.string.screen_account), style = rowTitle, color = colors.ink, modifier = Modifier.weight(1f))
            Icon(painterResource(R.drawable.ic_chevron_right), contentDescription = null, tint = colors.inkMuted, modifier = Modifier.size(18.dp))
        }
    }
}

/** Ba công tắc thông báo: dòng cao tối thiểu 56, đệm 6 16; tiêu đề 14/20 500, mô tả 12/16 `inkMuted`. */
@Composable
private fun NotificationsCard(prefs: NotificationPrefs, onChange: (NotificationPrefs) -> Unit) {
    GgCard(contentPadding = PaddingValues(0.dp)) {
        SwitchRow(R.string.me_notif_alerts, R.string.me_notif_alerts_desc, prefs.deviceAlerts) { onChange(prefs.copy(deviceAlerts = it)) }
        CardDivider()
        SwitchRow(R.string.me_notif_daily, R.string.me_notif_daily_desc, prefs.dailyReport) { onChange(prefs.copy(dailyReport = it)) }
        CardDivider()
        SwitchRow(R.string.me_notif_news, R.string.me_notif_news_desc, prefs.ggPowerNews) { onChange(prefs.copy(ggPowerNews = it)) }
    }
}

@Composable
private fun SwitchRow(title: Int, desc: Int, checked: Boolean, onChange: (Boolean) -> Unit) {
    val colors = GgTheme.colors
    val titleText = stringResource(title)
    Row(
        Modifier.fillMaxWidth().heightIn(min = 56.dp).padding(horizontal = GgSpacing.space4, vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(GgSpacing.space3),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(titleText, style = rowTitle, color = colors.ink)
            Text(stringResource(desc), style = rowDesc, color = colors.inkMuted)
        }
        GgToggle(checked = checked, onCheckedChange = onChange, contentDescription = titleText)
    }
}

/** Cài đặt hệ thống: hai bộ chọn (đệm 10 16, cách 6) và dòng Ngôn ngữ cao 52 mở danh sách ngay trong thẻ. */
@Composable
private fun SystemCard(
    state: MeUiState,
    onTemperatureUnit: (TemperatureUnit) -> Unit,
    onThemeMode: (ThemeMode) -> Unit,
    onToggleLanguageList: () -> Unit,
    onLanguage: (AppLanguage) -> Unit,
) {
    GgCard(contentPadding = PaddingValues(0.dp)) {
        val units = TemperatureUnit.entries
        SegmentRow(
            R.string.me_temperature_unit,
            listOf(stringResource(R.string.unit_celsius), stringResource(R.string.unit_fahrenheit)),
            units.indexOf(state.temperatureUnit),
        ) { onTemperatureUnit(units[it]) }
        CardDivider()
        val modes = listOf(ThemeMode.Light, ThemeMode.Dark, ThemeMode.Auto)
        SegmentRow(
            R.string.me_theme,
            listOf(stringResource(R.string.me_theme_light), stringResource(R.string.me_theme_dark), stringResource(R.string.me_theme_auto)),
            modes.indexOf(state.themeMode),
        ) { onThemeMode(modes[it]) }
        CardDivider()
        LanguageRow(state.language, state.languageOpen, onToggleLanguageList, onLanguage)
    }
}

@Composable
private fun SegmentRow(label: Int, options: List<String>, selected: Int, onSelect: (Int) -> Unit) {
    val text = stringResource(label)
    Column(
        Modifier.fillMaxWidth().padding(horizontal = GgSpacing.space4, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text(text, style = rowTitle, color = GgTheme.colors.ink)
        SegmentedControl(options = options, selectedIndex = selected, onSelect = onSelect, groupLabel = text)
    }
}

private fun AppLanguage.nameRes() = when (this) {
    AppLanguage.Vietnamese -> R.string.language_vi
    AppLanguage.English -> R.string.language_en
}

/**
 * Dòng Ngôn ngữ + danh sách thả xuống (Thay đổi 0.6 §7): giá trị hiện tại 13/20 `inkMuted`, mũi tên xoay 180° khi mở.
 * Danh sách: lề 0 12 12, đệm 6, cách 2, nền `surfaceRaised` viền `cardBorder` bo 12 có bóng; mục cao 44 bo 8 chữ
 * 14/20 600, mục đang chọn nền `primarySoft` và dấu ✓ 20 `primary`.
 */
@Composable
private fun LanguageRow(
    current: AppLanguage,
    open: Boolean,
    onToggle: () -> Unit,
    onPick: (AppLanguage) -> Unit,
) {
    val colors = GgTheme.colors
    val chevron by animateFloatAsState(if (open) 180f else 0f, tween(150), label = "chevron")
    // Mở danh sách: cuộn cho đủ cả danh sách và khoảng thanh thẻ nổi phía dưới (không bị che).
    val bringIntoView = remember { BringIntoViewRequester() }
    val tabBarSpace = with(LocalDensity.current) { FloatingTabBarContentPadding.toPx() }
    var size by remember { mutableStateOf(IntSize.Zero) }
    LaunchedEffect(open) {
        if (open) {
            delay(170) // chờ danh sách mở xong (150 ms) để biết chiều cao thật
            bringIntoView.bringIntoView(Rect(0f, 0f, size.width.toFloat(), size.height + tabBarSpace))
        }
    }
    val expanded = stringResource(if (open) R.string.state_expanded else R.string.state_collapsed)
    Column(Modifier.bringIntoViewRequester(bringIntoView).onSizeChanged { size = it }) {
        Row(
            Modifier
                .fillMaxWidth()
                .heightIn(min = 52.dp)
                .clickable(role = Role.DropdownList, onClick = onToggle)
                .semantics { stateDescription = expanded }
                .padding(horizontal = GgSpacing.space4),
            horizontalArrangement = Arrangement.spacedBy(GgSpacing.space3),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(painterResource(R.drawable.ic_globe), contentDescription = null, tint = colors.inkMuted, modifier = Modifier.size(20.dp))
            Text(stringResource(R.string.me_language), style = rowTitle, color = colors.ink, modifier = Modifier.weight(1f))
            Text(stringResource(current.nameRes()), style = GgTheme.typography.bodySm, color = colors.inkMuted)
            Icon(
                painterResource(R.drawable.ic_chevron_down),
                contentDescription = null,
                tint = colors.inkMuted,
                modifier = Modifier.size(18.dp).rotate(chevron),
            )
        }
        AnimatedVisibility(open, enter = expandVertically(tween(150)), exit = shrinkVertically(tween(150))) {
            val shape = RoundedCornerShape(12.dp)
            val languageLabel = stringResource(R.string.me_language)
            Column(
                Modifier
                    .padding(start = GgSpacing.space3, end = GgSpacing.space3, bottom = GgSpacing.space3)
                    .fillMaxWidth()
                    .shadow(8.dp, shape, ambientColor = colors.shadow, spotColor = colors.shadow)
                    .clip(shape)
                    .background(colors.surfaceRaised)
                    .border(1.dp, colors.cardBorder, shape)
                    .padding(6.dp)
                    .semantics { contentDescription = languageLabel }
                    .selectableGroup(),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                AppLanguage.entries.forEach { lang ->
                    val selected = lang == current
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .heightIn(min = 44.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(if (selected) colors.primarySoft else colors.surfaceRaised)
                            .selectable(selected = selected, role = Role.RadioButton, onClick = { onPick(lang) })
                            .padding(10.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            stringResource(lang.nameRes()),
                            style = rowTitle.copy(fontWeight = FontWeight.SemiBold),
                            color = colors.ink,
                            modifier = Modifier.weight(1f),
                        )
                        Box(Modifier.size(20.dp)) {
                            if (selected) {
                                Icon(painterResource(R.drawable.ic_check_success), contentDescription = null, tint = colors.primary, modifier = Modifier.size(20.dp))
                            }
                        }
                    }
                }
            }
        }
    }
}

/** Trạng thái mẫu cho Preview và màn debug. */
fun meSample(languageOpen: Boolean = false, loading: Boolean = false) = MeUiState(
    profile = if (loading) null else FakeProfileRepository.SAMPLE,
    languageOpen = languageOpen,
)

@Composable
private fun PreviewMe(state: MeUiState) {
    GgPowerTheme(darkTheme = false) {
        MeScreen(
            state,
            tabs = listOf(
                TabItem(stringResource(R.string.tab_projects), R.drawable.ic_tab_projects, false) {},
                TabItem(stringResource(R.string.tab_me), R.drawable.ic_tab_me, true) {},
            ),
            {}, {}, {}, {}, {}, {}, {},
        )
    }
}

@Preview(widthDp = 390, heightDp = 844, showBackground = true)
@Composable
private fun MePreview() = PreviewMe(meSample())

@Preview(widthDp = 390, heightDp = 844, showBackground = true)
@Composable
private fun MeLanguageOpenPreview() = PreviewMe(meSample(languageOpen = true))
