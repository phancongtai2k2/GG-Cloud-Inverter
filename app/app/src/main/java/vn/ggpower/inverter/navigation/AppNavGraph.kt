package vn.ggpower.inverter.navigation

import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.navigation
import androidx.navigation.compose.rememberNavController
import androidx.navigation.NavBackStackEntry
import androidx.navigation.toRoute
import vn.ggpower.inverter.R
import vn.ggpower.inverter.ui.components.TabItem
import vn.ggpower.inverter.ui.alerts.AlertDetailRoute
import vn.ggpower.inverter.ui.alerts.AlertsRoute
import vn.ggpower.inverter.ui.battery.BatteryEnergyRoute
import vn.ggpower.inverter.ui.config.ConfigRoute
import vn.ggpower.inverter.ui.data.BatteryDataRoute
import vn.ggpower.inverter.ui.data.InverterDataRoute
import vn.ggpower.inverter.ui.devices.DevicesRoute
import vn.ggpower.inverter.ui.forgot.ForgotDoneRoute
import vn.ggpower.inverter.ui.register.RegisterInfoRoute
import vn.ggpower.inverter.ui.addproject.AddDeviceRoute
import vn.ggpower.inverter.ui.qrscan.QrScanRoute
import vn.ggpower.inverter.ui.service.ServiceRoute
import vn.ggpower.inverter.ui.me.MeRoute
import vn.ggpower.inverter.ui.account.AccountRoute
import vn.ggpower.inverter.ui.addproject.AddInfoRoute
import vn.ggpower.inverter.ui.addproject.AddTariffRoute
import vn.ggpower.inverter.ui.addproject.AddProjectViewModel
import vn.ggpower.inverter.ui.register.RegisterOtpRoute
import vn.ggpower.inverter.ui.register.RegisterViewModel
import vn.ggpower.inverter.ui.forgot.ForgotFindRoute
import vn.ggpower.inverter.ui.forgot.ForgotNewRoute
import vn.ggpower.inverter.ui.forgot.ForgotOtpRoute
import vn.ggpower.inverter.ui.forgot.ForgotPasswordViewModel
import vn.ggpower.inverter.ui.home.HomeRoute
import vn.ggpower.inverter.ui.login.LoginRoute
import vn.ggpower.inverter.ui.projects.ProjectsRoute
import vn.ggpower.inverter.ui.theme.GgTheme
import vn.ggpower.inverter.ui.tou.TouScheduleRoute
import vn.ggpower.inverter.ui.tou.TouStrategyRoute
import vn.ggpower.inverter.ui.explore.ExploreRoute
import vn.ggpower.inverter.ui.support.SupportRoute
import vn.ggpower.inverter.ui.support.SupportSentRoute
import vn.ggpower.inverter.ui.support.TicketDetailRoute
import vn.ggpower.inverter.ui.warranty.WarrantyRoute
import vn.ggpower.inverter.domain.support.TicketType
import vn.ggpower.inverter.ui.placeholder.PlaceholderLink
import vn.ggpower.inverter.ui.placeholder.PlaceholderScreen

/** Id mẫu cho các liên kết của màn tạm (lấy từ dữ liệu mẫu trong HTML). Bỏ khi các màn thật có dữ liệu. */
private object SampleIds {
    const val PROJECT = "p1"
    const val ALERT = "a1"
}

@Composable
fun AppNavGraph(
    modifier: Modifier = Modifier,
    navController: NavHostController = rememberNavController(),
    session: SessionViewModel = hiltViewModel(),
) {
    val loggedIn by session.startLoggedIn.collectAsStateWithLifecycle()
    // Đang đọc phiên: chỉ vẽ nền (rất ngắn, đọc DataStore cục bộ).
    val startLoggedIn = loggedIn ?: run {
        Box(modifier.fillMaxSize().background(GgTheme.colors.surface))
        return
    }
    NavHost(
        navController = navController,
        startDestination = if (startLoggedIn) Projects else Login,
        modifier = modifier,
    ) {
        outsideSession(navController)
        accountLevel(navController, onLogout = { done -> session.logout(done) })
        projectLevel(navController)
    }
}

// ---------- Ngoài phiên đăng nhập ----------

private fun NavGraphBuilder.outsideSession(nav: NavHostController) {
    composable<Login> { entry ->
        // 12d đặt tài khoản vào đây để ô tài khoản được điền sẵn khi quay về (đặc tả §3.12).
        val prefill by entry.savedStateHandle.getStateFlow<String?>(PREFILL_ACCOUNT, null).collectAsStateWithLifecycle()
        val accountDeleted by entry.savedStateHandle.getStateFlow(ACCOUNT_DELETED, false).collectAsStateWithLifecycle()
        LoginRoute(
            notice = if (accountDeleted) stringResource(R.string.account_deleted_notice) else null,
            onNoticeShown = { entry.savedStateHandle[ACCOUNT_DELETED] = false },
            onLoggedIn = { nav.navigate(Projects) { popUpTo<Login> { inclusive = true } } },
            onForgotPassword = { account -> nav.navigate(ForgotPasswordFlow(account)) },
            onRegister = { nav.navigate(RegisterFlow) },
            prefillAccount = prefill,
            onPrefillConsumed = { entry.savedStateHandle[PREFILL_ACCOUNT] = null },
        )
    }

    navigation<ForgotPasswordFlow>(startDestination = ForgotPasswordFind) {
        composable<ForgotPasswordFind> { entry ->
            ForgotFindRoute(nav.forgotPasswordViewModel(entry), onBack = nav::popBackStack, onCodeSent = { nav.navigate(ForgotPasswordOtp) })
        }
        composable<ForgotPasswordOtp> { entry ->
            ForgotOtpRoute(nav.forgotPasswordViewModel(entry), onBack = nav::popBackStack, onVerified = { nav.navigate(ForgotPasswordNew) })
        }
        composable<ForgotPasswordNew> { entry ->
            ForgotNewRoute(nav.forgotPasswordViewModel(entry), onBack = nav::popBackStack, onChanged = {
                // Đổi xong không quay lại các bước trước được.
                nav.navigate(ForgotPasswordDone) { popUpTo<ForgotPasswordFind> { inclusive = true } }
            })
        }
        composable<ForgotPasswordDone> { entry ->
            val vm = nav.forgotPasswordViewModel(entry)
            ForgotDoneRoute(onToLogin = {
                // 12d → Đăng nhập: bỏ cả luồng khỏi back stack, điền sẵn tài khoản.
                runCatching { nav.getBackStackEntry<Login>().savedStateHandle[PREFILL_ACCOUNT] = vm.accountForLogin }
                nav.popBackStack<ForgotPasswordFlow>(inclusive = true)
            })
        }
    }

    navigation<RegisterFlow>(startDestination = RegisterInfo) {
        composable<RegisterInfo> { entry ->
            RegisterInfoRoute(
                nav.registerViewModel(entry),
                onBack = nav::popBackStack,
                onCodeSent = { nav.navigate(RegisterOtp) },
                onLogin = { account ->
                    if (account != null) runCatching { nav.getBackStackEntry<Login>().savedStateHandle[PREFILL_ACCOUNT] = account }
                    nav.popBackStack<RegisterFlow>(inclusive = true)
                },
                onOpenTerms = { nav.navigate(TermsOfUse) },
                onOpenPrivacy = { nav.navigate(PrivacyPolicy) },
            )
        }
        composable<RegisterOtp> { entry ->
            RegisterOtpRoute(nav.registerViewModel(entry), onBack = nav::popBackStack, onRegistered = {
                // Xác thực xong → Dự án (trạng thái rỗng), không quay lại được màn đăng ký.
                nav.navigate(Projects) { popUpTo<Login> { inclusive = true } }
            })
        }

    }

    composable<TermsOfUse> {
        Placeholder(R.string.register_terms, "—", onBack = nav::popBackStack)
    }
    composable<PrivacyPolicy> {
        Placeholder(R.string.register_privacy, "—", onBack = nav::popBackStack)
    }

    composable<QrScan> {
        QrScanRoute(
            hiltViewModel(),
            onClose = nav::popBackStack,
            onManualEntry = nav::popBackStack,
            // Trả serial về 14a qua savedStateHandle (ADR-004: 18 chỉ mở từ 14a); 14a điền và kiểm tra lại.
            onUseDevice = { serial ->
                nav.previousBackStackEntry?.savedStateHandle?.set(SCANNED_SERIAL, serial)
                nav.popBackStack()
            },
            // Chưa có dự án nên màn 8 mở không gắn dự án (BO_SUNG §21).
            onContactSupport = { nav.navigate(Support(projectId = "")) },
        )
    }
}

// ---------- Cấp tài khoản ----------

private enum class AccountTab { PROJECTS, ME }

@Composable
private fun accountTabs(nav: NavHostController, selected: AccountTab): List<TabItem> {
    fun go(route: Any) = nav.navigate(route) {
        popUpTo<Projects> { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
    return listOf(
        TabItem(stringResource(R.string.tab_projects), R.drawable.ic_tab_projects, selected == AccountTab.PROJECTS) { go(Projects) },
        TabItem(stringResource(R.string.tab_me), R.drawable.ic_tab_me, selected == AccountTab.ME) { go(Me) },
    )
}

private fun NavGraphBuilder.accountLevel(nav: NavHostController, onLogout: (onDone: () -> Unit) -> Unit) {
    composable<Projects> {
        ProjectsRoute(
            tabs = accountTabs(nav, AccountTab.PROJECTS),
            onOpenProject = { id -> nav.navigate(ProjectHome(id)) },
            onAddProject = { nav.navigate(AddProjectFlow) },
        )
    }
    composable<Me> {
        MeRoute(
            tabs = accountTabs(nav, AccountTab.ME),
            onOpenAccount = { nav.navigate(Account) },
            // Đăng xuất → xoá phiên → Đăng nhập, xoá toàn bộ back stack.
            onLogout = { onLogout { nav.navigate(Login) { popUpTo(nav.graph.id) { inclusive = true } } } },
        )
    }
    composable<Account> {
        AccountRoute(
            onLeave = nav::popBackStack,
            onChangeEmail = { nav.navigate(ChangeContact(phone = false)) },
            onChangePhone = { nav.navigate(ChangeContact(phone = true)) },
            onChangePassword = { nav.navigate(ChangePassword) },
            // Đã gửi yêu cầu xoá → xoá phiên → Đăng nhập (xoá toàn bộ back stack) kèm thông báo (BO_SUNG §24).
            onDeleted = {
                onLogout {
                    nav.navigate(Login) { popUpTo(nav.graph.id) { inclusive = true } }
                    runCatching { nav.getBackStackEntry<Login>().savedStateHandle[ACCOUNT_DELETED] = true }
                }
            },
        )
    }
    composable<ChangeContact> {
        Placeholder(R.string.screen_change_contact, "25", onBack = nav::popBackStack)
    }
    composable<ChangePassword> {
        Placeholder(R.string.screen_change_password, "26", onBack = nav::popBackStack)
    }

    navigation<AddProjectFlow>(startDestination = AddProjectDevice) {
        composable<AddProjectDevice> { entry ->
            val vm = nav.addProjectViewModel(entry)
            // Màn 18 trả serial qua savedStateHandle của entry này (ADR-004: 18 chỉ mở từ 14a).
            val scanned by entry.savedStateHandle.getStateFlow<String?>(SCANNED_SERIAL, null).collectAsStateWithLifecycle()
            LaunchedEffect(scanned) {
                scanned?.let {
                    vm.onScanned(it)
                    entry.savedStateHandle[SCANNED_SERIAL] = null
                }
            }
            AddDeviceRoute(
                vm,
                onBack = nav::popBackStack,
                onScanQr = { nav.navigate(QrScan) },
                onContinue = { nav.navigate(AddProjectInfo) },
                // Chưa có dự án nên màn 8 mở không gắn dự án (BO_SUNG §21).
                onContactSupport = { nav.navigate(Support(projectId = "")) },
            )
        }
        composable<AddProjectInfo> { entry ->
            AddInfoRoute(nav.addProjectViewModel(entry), onBack = nav::popBackStack, onContinue = { nav.navigate(AddProjectTariff) })
        }
        composable<AddProjectTariff> { entry ->
            AddTariffRoute(nav.addProjectViewModel(entry), onBack = nav::popBackStack, onCreated = { projectId ->
                // Tạo xong → Màn hình chính của dự án mới; bỏ cả luồng khỏi back stack.
                nav.navigate(ProjectHome(projectId)) { popUpTo<AddProjectFlow> { inclusive = true } }
            })
        }

    }
}

// ---------- Cấp dự án ----------

private enum class ProjectTab { HOME, DEVICES, SERVICE }

/**
 * Nút ← ở Màn hình chính / Thiết bị / Dịch vụ (ADR-003): về danh sách Dự án, bỏ các thẻ của dự án khỏi back stack.
 * Nếu Dự án không còn trong back stack (mở dự án từ nơi khác) thì mở Dự án làm màn gốc.
 */
private fun NavHostController.backToProjects() {
    if (!popBackStack<Projects>(inclusive = false)) {
        navigate(Projects) { popUpTo(graph.id) { inclusive = true } }
    }
}

@Composable
private fun projectTabs(nav: NavHostController, projectId: String, selected: ProjectTab): List<TabItem> {
    fun go(route: Any) = nav.navigate(route) {
        popUpTo(ProjectHome(projectId)) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
    return listOf(
        TabItem(stringResource(R.string.tab_main), R.drawable.ic_tab_main, selected == ProjectTab.HOME) { go(ProjectHome(projectId)) },
        TabItem(stringResource(R.string.tab_devices), R.drawable.ic_tab_devices, selected == ProjectTab.DEVICES) { go(Devices(projectId)) },
        TabItem(stringResource(R.string.tab_service), R.drawable.ic_tab_service, selected == ProjectTab.SERVICE) { go(Service(projectId)) },
    )
}

private fun NavGraphBuilder.projectLevel(nav: NavHostController) {
    composable<ProjectHome> { entry ->
        val id = entry.toRoute<ProjectHome>().projectId
        HomeRoute(
            tabs = projectTabs(nav, id, ProjectTab.HOME),
            onBackToProjects = { nav.backToProjects() },
            onOpenAlerts = { nav.navigate(Alerts(id)) },
            onOpenConfig = { nav.navigate(Config(id)) },
            onOpenBatteryEnergy = { nav.navigate(BatteryEnergy(id)) },
        )
    }
    composable<Devices> { entry ->
        val id = entry.toRoute<Devices>().projectId
        DevicesRoute(
            tabs = projectTabs(nav, id, ProjectTab.DEVICES),
            onBackToProjects = { nav.backToProjects() },
            // ADR-004 mục 3: nút QR ở màn 5 trỏ tới màn 27 (chưa có thiết kế), không mở màn 18.
            onAddDevice = { nav.navigate(AddDevice(id)) },
            onOpenInverterData = { nav.navigate(DataInverter(id)) },
            onOpenBatteryData = { nav.navigate(DataBattery(id)) },
        )
    }
    composable<Service> { entry ->
        val id = entry.toRoute<Service>().projectId
        ServiceRoute(
            tabs = projectTabs(nav, id, ProjectTab.SERVICE),
            onBackToProjects = { nav.backToProjects() },
            onOpenExplore = { nav.navigate(Explore(id)) },
            onOpenSupport = { nav.navigate(Support(id)) },
            onOpenWarranty = { nav.navigate(Warranty(id)) },
        )
    }

    composable<Config> { entry ->
        val id = entry.toRoute<Config>().projectId
        ConfigRoute(onBack = nav::popBackStack, onOpenTouSchedule = { nav.navigate(TouSchedule(id)) })
    }
    composable<TouSchedule> { entry ->
        val id = entry.toRoute<TouSchedule>().projectId
        TouScheduleRoute(
            onBack = nav::popBackStack,
            onOpenStrategy = { strategyId -> nav.navigate(TouStrategy(id, strategyId)) },
            onAddStrategy = { nav.navigate(TouStrategy(id, NEW_STRATEGY_ID)) },
        )
    }
    composable<TouStrategy> {
        TouStrategyRoute(onBack = nav::popBackStack)
    }
    composable<Alerts> { entry ->
        val id = entry.toRoute<Alerts>().projectId
        AlertsRoute(
            onBack = nav::popBackStack,
            onOpenSettings = { nav.navigate(AlertSettings(id)) },
            onOpenAlert = { alertId -> nav.navigate(AlertDetail(id, alertId)) },
        )
    }
    composable<AddDevice> {
        Placeholder(R.string.screen_add_device, "27", onBack = nav::popBackStack)
    }
    composable<AlertSettings> {
        Placeholder(R.string.screen_alert_settings, "28", onBack = nav::popBackStack)
    }
    composable<AlertDetail> { entry ->
        val route = entry.toRoute<AlertDetail>()
        // "Tạo yêu cầu hỗ trợ kỹ thuật": truyền alertId để màn 8 điền sẵn; quay lại về 10a.
        AlertDetailRoute(onBack = nav::popBackStack, onCreateSupport = { nav.navigate(Support(route.projectId, route.alertId)) })
    }
    composable<BatteryEnergy> {
        BatteryEnergyRoute(onBack = nav::popBackStack)
    }
    composable<DataInverter> {
        InverterDataRoute(onBack = nav::popBackStack)
    }
    composable<DataBattery> {
        BatteryDataRoute(onBack = nav::popBackStack)
    }
    composable<Explore> { entry ->
        val id = entry.toRoute<Explore>().projectId
        ExploreRoute(onBack = nav::popBackStack, onOpenItem = { itemId -> nav.navigate(ExploreDetail(id, itemId)) })
    }
    composable<ExploreDetail> {
        Placeholder(R.string.screen_explore_detail, "29", onBack = nav::popBackStack)
    }
    composable<Support> { entry ->
        val id = entry.toRoute<Support>().projectId
        // Quay lại về nơi mở: 10a khi tạo từ cảnh báo (Thay đổi 0.6 §6), 6, 9, hoặc 14a / 18.
        SupportRoute(
            onBack = nav::popBackStack,
            onOpenTicket = { ticketId -> nav.navigate(TicketDetail(id, ticketId)) },
            onSent = { ticket -> nav.navigate(SupportSent(id, ticket.code, ticket.type == TicketType.Technical)) },
        )
    }
    composable<SupportSent> {
        SupportSentRoute(
            // Màn 8 đã tự chuyển sang thẻ "Yêu cầu của tôi" với phiếu mới trên cùng.
            onBack = nav::popBackStack,
            onViewMine = nav::popBackStack,
            // Không có Dịch vụ trong ngăn xếp (mở từ 10a, 14a, 18) thì về nơi đã mở màn 8.
            onBackToService = {
                if (!nav.popBackStack<Service>(inclusive = false)) nav.popBackStack<Support>(inclusive = true)
            },
        )
    }
    composable<TicketDetail> {
        TicketDetailRoute(onBack = nav::popBackStack)
    }
    composable<Warranty> { entry ->
        val id = entry.toRoute<Warranty>().projectId
        WarrantyRoute(onBack = nav::popBackStack, onRequestWarranty = { nav.navigate(Support(id)) })
    }
}

@Composable
private fun Placeholder(
    @StringRes title: Int,
    number: String,
    onBack: (() -> Unit)? = null,
    @StringRes backDescription: Int? = null,
    links: List<Pair<Int, () -> Unit>> = emptyList(),
    tabs: List<TabItem>? = null,
) {
    PlaceholderScreen(
        title = stringResource(title),
        number = number,
        onBack = onBack?.let { back -> { back() } },
        backDescription = backDescription?.let { stringResource(it) },
        links = links.map { (label, action) -> PlaceholderLink(stringResource(label), action) },
        tabs = tabs,
    )
}

/** ViewModel dùng chung của luồng Quên mật khẩu, gắn với entry của nested graph [ForgotPasswordFlow]. */
@Composable
private fun NavHostController.forgotPasswordViewModel(entry: NavBackStackEntry): ForgotPasswordViewModel {
    val parent = remember(entry) { getBackStackEntry<ForgotPasswordFlow>() }
    return hiltViewModel(parent)
}

/** ViewModel dùng chung của luồng Đăng ký, gắn với entry của nested graph [RegisterFlow]. */
@Composable
private fun NavHostController.registerViewModel(entry: NavBackStackEntry): RegisterViewModel {
    val parent = remember(entry) { getBackStackEntry<RegisterFlow>() }
    return hiltViewModel(parent)
}

/** ViewModel dùng chung của luồng Thêm dự án, gắn với entry của nested graph [AddProjectFlow]. */
@Composable
private fun NavHostController.addProjectViewModel(entry: NavBackStackEntry): AddProjectViewModel {
    val parent = remember(entry) { getBackStackEntry<AddProjectFlow>() }
    return hiltViewModel(parent)
}

/** Khoá savedStateHandle của entry 14a: serial quét được ở màn 18. */
private const val SCANNED_SERIAL = "scanned_serial"

/** Khoá savedStateHandle của entry Đăng nhập: tài khoản điền sẵn khi quay về từ 12d. */
private const val PREFILL_ACCOUNT = "prefill_account"

/** Khoá savedStateHandle của entry Đăng nhập: vừa gửi yêu cầu xoá tài khoản ở màn 15 (hiện thông báo một lần). */
private const val ACCOUNT_DELETED = "account_deleted"
