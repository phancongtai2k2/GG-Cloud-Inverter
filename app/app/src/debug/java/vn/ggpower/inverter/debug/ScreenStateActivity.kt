package vn.ggpower.inverter.debug

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import vn.ggpower.inverter.R
import vn.ggpower.inverter.ui.account.AccountActions
import vn.ggpower.inverter.ui.account.AccountScreen
import vn.ggpower.inverter.ui.account.AccountUiState
import vn.ggpower.inverter.ui.account.DeleteDialogState
import vn.ggpower.inverter.ui.account.DeleteError
import vn.ggpower.inverter.ui.account.accountSample
import vn.ggpower.inverter.data.alert.FakeAlertRepository
import vn.ggpower.inverter.data.device.FakeDeviceRepository
import vn.ggpower.inverter.domain.alert.AlertFilter
import vn.ggpower.inverter.data.battery.FakeBatteryEnergyRepository
import vn.ggpower.inverter.domain.home.BatteryContent
import vn.ggpower.inverter.ui.alerts.AlertDetailScreen
import vn.ggpower.inverter.ui.battery.BatteryEnergyScreen
import vn.ggpower.inverter.ui.forgot.FindError
import vn.ggpower.inverter.ui.login.AccountError
import vn.ggpower.inverter.ui.forgot.ForgotFindScreen
import vn.ggpower.inverter.ui.forgot.ForgotDoneScreen
import vn.ggpower.inverter.ui.register.RegisterInfoScreen
import vn.ggpower.inverter.ui.addproject.AddDeviceScreen
import vn.ggpower.inverter.ui.addproject.AddInfoSample
import vn.ggpower.inverter.ui.addproject.AddTariffScreen
import vn.ggpower.inverter.ui.qrscan.CameraAccess
import vn.ggpower.inverter.ui.qrscan.QrScanScreen
import vn.ggpower.inverter.ui.qrscan.qrScanSample
import vn.ggpower.inverter.ui.addproject.AddTariffUiState
import vn.ggpower.inverter.ui.addproject.CreateError
import vn.ggpower.inverter.domain.project.TariffError
import vn.ggpower.inverter.domain.project.TariffInput
import vn.ggpower.inverter.domain.project.TariffKind
import vn.ggpower.inverter.domain.project.normalizeMoneyInput
import vn.ggpower.inverter.ui.addproject.AddInfoScreen
import vn.ggpower.inverter.ui.addproject.AddInfoUiState
import vn.ggpower.inverter.ui.addproject.LocateNote
import vn.ggpower.inverter.domain.project.Coordinates
import vn.ggpower.inverter.domain.project.ProjectInfoError
import vn.ggpower.inverter.ui.addproject.AddDeviceUiState
import vn.ggpower.inverter.ui.addproject.SerialError
import vn.ggpower.inverter.data.project.FakeAddProjectRepository
import vn.ggpower.inverter.ui.register.RegisterOtpScreen
import vn.ggpower.inverter.ui.register.RegisterInfoUiState
import vn.ggpower.inverter.ui.register.RegisterSample
import vn.ggpower.inverter.domain.auth.RegisterFieldError
import vn.ggpower.inverter.ui.forgot.ForgotFindUiState
import vn.ggpower.inverter.ui.forgot.ForgotNewScreen
import vn.ggpower.inverter.ui.forgot.ForgotNewUiState
import vn.ggpower.inverter.domain.auth.PasswordError
import vn.ggpower.inverter.ui.forgot.ForgotOtpScreen
import vn.ggpower.inverter.ui.otp.OtpUiState
import vn.ggpower.inverter.ui.otp.OtpError
import vn.ggpower.inverter.ui.battery.BatteryEnergyUiState
import vn.ggpower.inverter.ui.alerts.AlertsScreen
import vn.ggpower.inverter.ui.alerts.SaveFailure
import vn.ggpower.inverter.ui.alerts.previewAlertDetail
import vn.ggpower.inverter.ui.alerts.AlertsUiState
import vn.ggpower.inverter.data.home.FakeHomeRepository
import vn.ggpower.inverter.data.telemetry.FakeTelemetryRepository
import vn.ggpower.inverter.ui.data.BatteryDataScreen
import vn.ggpower.inverter.ui.data.BatteryGroup
import vn.ggpower.inverter.ui.data.InverterDataScreen
import vn.ggpower.inverter.ui.data.previewBatteryDataState
import vn.ggpower.inverter.ui.data.previewBatteryHistory
import vn.ggpower.inverter.ui.data.LiveUiState
import vn.ggpower.inverter.ui.data.InverterGroup
import vn.ggpower.inverter.ui.data.previewInverterDataState
import vn.ggpower.inverter.ui.data.previewInverterHistory
import vn.ggpower.inverter.domain.settings.TemperatureUnit
import vn.ggpower.inverter.domain.telemetry.HistoryFields
import vn.ggpower.inverter.domain.telemetry.HistoryRange
import vn.ggpower.inverter.ui.devices.DevicesScreen
import vn.ggpower.inverter.ui.devices.DevicesUiState
import vn.ggpower.inverter.ui.devices.previewDeviceTabs
import vn.ggpower.inverter.data.support.FakeSupportContactRepository
import vn.ggpower.inverter.data.explore.FakeExploreRepository
import vn.ggpower.inverter.domain.explore.ExploreTab
import vn.ggpower.inverter.domain.explore.search
import vn.ggpower.inverter.ui.explore.ExploreScreen
import vn.ggpower.inverter.ui.explore.ExploreUiState
import vn.ggpower.inverter.domain.support.TicketAttachment
import vn.ggpower.inverter.ui.support.SubmitFailure
import vn.ggpower.inverter.ui.support.SupportActions
import vn.ggpower.inverter.ui.support.SupportScreen
import vn.ggpower.inverter.ui.support.SupportSentScreen
import vn.ggpower.inverter.ui.support.SupportSentUiState
import vn.ggpower.inverter.ui.support.SupportTab
import vn.ggpower.inverter.ui.support.TicketsState
import vn.ggpower.inverter.ui.support.DetailLoad
import vn.ggpower.inverter.ui.support.TicketDetailActions
import vn.ggpower.inverter.ui.support.TicketDetailScreen
import vn.ggpower.inverter.ui.support.TicketDetailUiState
import vn.ggpower.inverter.ui.support.previewTicketDetailState
import vn.ggpower.inverter.ui.support.previewSupportState
import vn.ggpower.inverter.ui.service.ContactField
import vn.ggpower.inverter.ui.service.ServiceScreen
import vn.ggpower.inverter.ui.service.ServiceUiState
import vn.ggpower.inverter.ui.service.previewServiceTabs
import vn.ggpower.inverter.data.project.FakeProjectRepository
import vn.ggpower.inverter.domain.config.OperatingMode
import vn.ggpower.inverter.domain.config.Role
import vn.ggpower.inverter.domain.config.WriteState
import vn.ggpower.inverter.domain.config.ConfigRequest
import vn.ggpower.inverter.domain.config.ControlState
import vn.ggpower.inverter.domain.config.WriteGroup
import vn.ggpower.inverter.ui.config.ConfigNotice
import vn.ggpower.inverter.ui.config.ConfigScreen
import vn.ggpower.inverter.ui.config.ConfirmState
import vn.ggpower.inverter.ui.config.NoticeKind
import vn.ggpower.inverter.ui.config.ConfigUiState
import vn.ggpower.inverter.ui.config.previewConfigState
import vn.ggpower.inverter.ui.home.HomeScreen
import vn.ggpower.inverter.ui.home.HomeUiState
import vn.ggpower.inverter.ui.home.StatsUiState
import vn.ggpower.inverter.ui.home.previewStats
import vn.ggpower.inverter.domain.home.PeriodSelection
import vn.ggpower.inverter.domain.home.StatsPeriod
import java.time.LocalDate
import java.time.LocalTime
import vn.ggpower.inverter.ui.components.TabItem
import vn.ggpower.inverter.ui.projects.ProjectsScreen
import vn.ggpower.inverter.ui.me.MeScreen
import vn.ggpower.inverter.ui.me.MeUiState
import vn.ggpower.inverter.ui.me.meSample
import vn.ggpower.inverter.ui.projects.ProjectsUiState
import vn.ggpower.inverter.ui.theme.GgPowerTheme
import vn.ggpower.inverter.domain.tou.SlotError
import vn.ggpower.inverter.domain.tou.SlotKind
import vn.ggpower.inverter.domain.tou.StrategyNameError
import vn.ggpower.inverter.domain.tou.TouSlot
import vn.ggpower.inverter.domain.tou.suggestSlot
import vn.ggpower.inverter.domain.tou.toInput
import vn.ggpower.inverter.domain.tou.withSlotAdded
import vn.ggpower.inverter.domain.tou.withSlotRemoved
import vn.ggpower.inverter.domain.tou.withSlotReplaced
import vn.ggpower.inverter.ui.tou.SlotSheetState
import vn.ggpower.inverter.ui.tou.TouConfirm
import vn.ggpower.inverter.domain.tou.toggleDay
import vn.ggpower.inverter.ui.tou.RenameState
import vn.ggpower.inverter.ui.tou.TouScheduleScreen
import vn.ggpower.inverter.ui.tou.TouStrategyScreen
import vn.ggpower.inverter.ui.tou.previewTouStrategyState
import java.time.DayOfWeek
import vn.ggpower.inverter.ui.tou.TouScheduleUiState
import vn.ggpower.inverter.ui.tou.previewTouScheduleState
import vn.ggpower.inverter.ui.warranty.WarrantyScreen
import vn.ggpower.inverter.ui.warranty.WarrantyUiState
import vn.ggpower.inverter.ui.warranty.previewWarrantyState

/**
 * Chỉ bản debug: hiện một màn ở trạng thái chỉ định để chụp và so với ảnh thiết kế (AGENTS.md §9).
 * adb shell am start -n vn.ggpower.inverter/.debug.ScreenStateActivity --es state projects_empty
 */
class ScreenStateActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        val state = intent.getStringExtra("state").orEmpty()
        val dark = intent.getBooleanExtra("dark", false)
        setContent { GgPowerTheme(darkTheme = dark) { States[state]?.invoke() } }
    }
}

private val States: Map<String, @Composable () -> Unit> = mapOf(
    "projects" to { Projects(ProjectsUiState.Content(FakeProjectRepository.SAMPLE)) },
    "projects_empty" to { Projects(ProjectsUiState.Content(emptyList())) },
    "projects_no_result" to { Projects(ProjectsUiState.Content(FakeProjectRepository.SAMPLE, query = "Hà Nội")) },
    "projects_loading" to { Projects(ProjectsUiState.Loading) },
    "me" to { Me(meSample()) },
    "me_language_open" to { Me(meSample(languageOpen = true)) },
    "me_loading" to { Me(meSample(loading = true)) },
    "account" to { AccountScreen(accountSample(), AccountActions()) },
    "account_dirty" to { AccountScreen(accountSample(dirty = true), AccountActions()) },
    "account_name_error" to { AccountScreen(accountSample(nameError = true), AccountActions()) },
    "account_delete" to { AccountScreen(accountSample(delete = DeleteDialogState()), AccountActions()) },
    "account_delete_wrong" to { AccountScreen(accountSample(delete = DeleteDialogState(password = "abc12345", error = DeleteError.WrongPassword)), AccountActions()) },
    "account_loading" to { AccountScreen(AccountUiState.Loading, AccountActions()) },
    "projects_error" to { Projects(ProjectsUiState.Error(noConnection = true)) },
    "home" to { Home(HomeUiState.Content(FakeHomeRepository.SAMPLE)) },
    "home_night" to {
        Home(
            HomeUiState.Content(
                FakeHomeRepository.SAMPLE.copy(
                    unresolvedAlerts = 0, yieldTodayKwh = 12.0, mode = OperatingMode.Tou,
                    energy = FakeHomeRepository.SAMPLE.energy.copy(pvKw = 0.0, gridKw = 0.2, batteryKw = -0.6, loadKw = 0.8, batterySocPercent = 41),
                ),
            ),
        )
    },
    "home_loading" to { Home(HomeUiState.Loading, null) },
    "home_month" to { Home(HomeUiState.Content(FakeHomeRepository.SAMPLE), previewStats().copy(selection = PeriodSelection(StatsPeriod.Month, LocalDate.of(2026, 9, 24)), yieldBuckets = FakeHomeRepository.YIELD_MONTH, yieldIndex = 23, curves = null, energyBuckets = FakeHomeRepository.ENERGY_MONTH, powerIndex = 23, batteryIndex = 23, summary = FakeHomeRepository.SUMMARY_MONTH)) },
    "home_year" to { Home(HomeUiState.Content(FakeHomeRepository.SAMPLE), previewStats().copy(selection = PeriodSelection(StatsPeriod.Year, LocalDate.of(2026, 9, 24)), yieldBuckets = FakeHomeRepository.YIELD_YEAR, yieldIndex = 8, curves = null, energyBuckets = FakeHomeRepository.ENERGY_YEAR, powerIndex = 8, batteryIndex = 8, summary = FakeHomeRepository.SUMMARY_YEAR)) },
    "devices" to { DevicesScreen(DevicesUiState.Content(FakeDeviceRepository.SAMPLE), previewDeviceTabs(), {}, {}, {}, {}, {}) },
    "devices_f" to { DevicesScreen(DevicesUiState.Content(FakeDeviceRepository.SAMPLE), previewDeviceTabs(), {}, {}, {}, {}, {}, temperatureUnit = TemperatureUnit.Fahrenheit) },
    "devices_loading" to { DevicesScreen(DevicesUiState.Loading, previewDeviceTabs(), {}, {}, {}, {}, {}) },
    "devices_error" to { DevicesScreen(DevicesUiState.Error(noConnection = true), previewDeviceTabs(), {}, {}, {}, {}, {}) },
    "service" to { ServiceScreen(ServiceUiState(FakeSupportContactRepository.SAMPLE), previewServiceTabs(), {}, {}, {}, {}, {}) },
    "service_copied" to { ServiceScreen(ServiceUiState(FakeSupportContactRepository.SAMPLE, copied = ContactField.Email), previewServiceTabs(), {}, {}, {}, {}, {}) },
    "explore" to { Explore(ExploreTab.Products) },
    "explore_news" to { Explore(ExploreTab.News) },
    "explore_videos" to { Explore(ExploreTab.Videos) },
    "explore_no_result" to { Explore(ExploreTab.News, query = "xyz") },
    "support" to { SupportScreen(previewSupportState(), SupportActions()) },
    "support_from_alert" to { SupportScreen(previewSupportState(fromAlert = true), SupportActions()) },
    "support_project_open" to { SupportScreen(previewSupportState(projectMenuOpen = true), SupportActions()) },
    "support_errors" to { SupportScreen(previewSupportState(showErrors = true, projectId = null), SupportActions()) },
    "support_send_failed" to {
        SupportScreen(
            previewSupportState(fromAlert = true).let {
                it.copy(
                    submitFailed = SubmitFailure(noConnection = true),
                    draft = it.draft.withAttachments(listOf(TicketAttachment("content://1", "IMG_20260924_0712.jpg", false), TicketAttachment("content://2", "VID_20260924_0715.mp4", true))),
                )
            },
            SupportActions(),
        )
    },
    "ticket_detail" to { TicketDetailScreen(previewTicketDetailState(), TicketDetailActions()) },
    "ticket_detail_copied" to { TicketDetailScreen(previewTicketDetailState(copied = true), TicketDetailActions()) },
    "ticket_detail_closed" to { TicketDetailScreen(previewTicketDetailState("t4"), TicketDetailActions()) },
    "ticket_detail_account" to { TicketDetailScreen(previewTicketDetailState("t3"), TicketDetailActions()) },
    "ticket_detail_send_failed" to {
        TicketDetailScreen(
            previewTicketDetailState(message = "Kỹ thuật gọi lúc mấy giờ ạ?", sendFailed = SubmitFailure(noConnection = true)),
            TicketDetailActions(),
        )
    },
    "ticket_detail_loading" to { TicketDetailScreen(TicketDetailUiState(), TicketDetailActions()) },
    "ticket_detail_error" to { TicketDetailScreen(TicketDetailUiState(load = DetailLoad.Error(noConnection = false)), TicketDetailActions()) },
    "warranty" to { WarrantyScreen(previewWarrantyState(), {}, {}, {}) },
    "warranty_expired" to { WarrantyScreen(previewWarrantyState(today = java.time.LocalDate.of(2026, 11, 10)), {}, {}, {}) },
    "warranty_loading" to { WarrantyScreen(WarrantyUiState.Loading, {}, {}, {}) },
    "warranty_error" to { WarrantyScreen(WarrantyUiState.Error(noConnection = true), {}, {}, {}) },
    "support_mine" to { SupportScreen(previewSupportState(tab = SupportTab.Mine), SupportActions()) },
    "support_mine_empty" to { SupportScreen(previewSupportState(tab = SupportTab.Mine).copy(tickets = TicketsState.Content(emptyList())), SupportActions()) },
    "support_sent" to { SupportSentScreen(SupportSentUiState("#HT-0310-0151", technical = true), {}, {}, {}, {}) },
    "support_sent_copied" to { SupportSentScreen(SupportSentUiState("#HT-0310-0151", technical = true, copied = true), {}, {}, {}, {}) },
    "data_inverter_ov" to { InverterDataScreen(previewInverterDataState(InverterGroup.Overview), {}, {}, {}) },
    "data_inverter_pv" to { InverterDataScreen(previewInverterDataState(InverterGroup.Pv), {}, {}, {}) },
    "data_inverter_grid" to { InverterDataScreen(previewInverterDataState(InverterGroup.GridLoad), {}, {}, {}) },
    "data_inverter_temp" to { InverterDataScreen(previewInverterDataState(InverterGroup.Temperature), {}, {}, {}) },
    "data_inverter_history" to { InverterDataScreen(previewInverterDataState(), {}, {}, {}, history = previewInverterHistory()) },
    "data_inverter_history_hour" to { InverterDataScreen(previewInverterDataState(), {}, {}, {}, history = previewInverterHistory(HistoryRange.Hour)) },
    "data_inverter_history_week" to { InverterDataScreen(previewInverterDataState(), {}, {}, {}, history = previewInverterHistory(HistoryRange.Week)) },
    "data_inverter_loading" to { InverterDataScreen(LiveUiState.Loading, {}, {}, {}) },
    "data_inverter_temp_f" to { InverterDataScreen(previewInverterDataState(InverterGroup.Temperature), {}, {}, {}, temperatureUnit = TemperatureUnit.Fahrenheit) },
    "data_inverter_history_f" to { InverterDataScreen(previewInverterDataState(), {}, {}, {}, temperatureUnit = TemperatureUnit.Fahrenheit, history = previewInverterHistory()) },
    "data_battery_f" to {
        val history = previewBatteryHistory().copy(selected = listOf(HistoryFields.Soc, HistoryFields.BatteryTemp))
        BatteryDataScreen(previewBatteryDataState(), {}, {}, {}, temperatureUnit = TemperatureUnit.Fahrenheit, history = history)
    },
    "data_battery_cells_f" to { BatteryDataScreen(previewBatteryDataState(BatteryGroup.Cells), {}, {}, {}, temperatureUnit = TemperatureUnit.Fahrenheit) },
    "data_battery" to { BatteryDataScreen(previewBatteryDataState(), {}, {}, {}, history = previewBatteryHistory()) },
    "data_battery_limits" to { BatteryDataScreen(previewBatteryDataState(BatteryGroup.Limits), {}, {}, {}) },
    "data_battery_limited" to { BatteryDataScreen(previewBatteryDataState(BatteryGroup.Limits, FakeTelemetryRepository.BATTERY.copy(packVoltageInverterV = 52.4, packCurrentA = 99.0)), {}, {}, {}) },
    "data_battery_deviation" to { BatteryDataScreen(previewBatteryDataState(data = FakeTelemetryRepository.BATTERY.copy(packVoltageInverterV = 52.4, packCurrentA = 99.0)), {}, {}, {}) },
    "data_battery_cells" to { BatteryDataScreen(previewBatteryDataState(BatteryGroup.Cells), {}, {}, {}) },
    "data_battery_protection" to { BatteryDataScreen(previewBatteryDataState(BatteryGroup.Protection), {}, {}, {}) },
    "data_battery_protection_all" to { BatteryDataScreen(previewBatteryDataState(BatteryGroup.Protection), {}, {}, {}, showAllFlags = true) },
    "data_battery_versions" to { BatteryDataScreen(previewBatteryDataState(BatteryGroup.Versions), {}, {}, {}) },
    "alerts" to { AlertsScreen(AlertsUiState.Content(FakeAlertRepository.SAMPLE, AlertFilter.All), {}, {}, {}, {}, {}) },
    "alerts_open" to { AlertsScreen(AlertsUiState.Content(FakeAlertRepository.SAMPLE, AlertFilter.Open), {}, {}, {}, {}, {}) },
    "alerts_empty" to { AlertsScreen(AlertsUiState.Content(FakeAlertRepository.SAMPLE.map { it.copy(resolved = true) }, AlertFilter.Open), {}, {}, {}, {}, {}) },
    "alerts_loading" to { AlertsScreen(AlertsUiState.Loading, {}, {}, {}, {}, {}) },
    "alert_grid" to { AlertDetailScreen(previewAlertDetail("grid"), {}, {}, {}, {}) },
    "alert_grid_marked" to { AlertDetailScreen(previewAlertDetail("grid", resolved = true), {}, {}, {}, {}) },
    "alert_temp" to { AlertDetailScreen(previewAlertDetail("temp"), {}, {}, {}, {}) },
    "alert_temp_f" to { AlertDetailScreen(previewAlertDetail("temp"), {}, {}, {}, {}, temperatureUnit = TemperatureUnit.Fahrenheit) },
    "alert_fw" to { AlertDetailScreen(previewAlertDetail("fw"), {}, {}, {}, {}) },
    "alert_pv" to { AlertDetailScreen(previewAlertDetail("pv"), {}, {}, {}, {}) },
    "alert_save_failed" to { AlertDetailScreen(previewAlertDetail("grid").copy(saveFailed = SaveFailure(1, noConnection = true)), {}, {}, {}, {}) },
    "energy" to { BatteryEnergyScreen(BatteryEnergyUiState.Content(FakeBatteryEnergyRepository.SAMPLE), {}, {}) },
    "energy_f" to { BatteryEnergyScreen(BatteryEnergyUiState.Content(FakeBatteryEnergyRepository.SAMPLE), {}, {}, temperatureUnit = TemperatureUnit.Fahrenheit) },
    "energy_discharging" to { BatteryEnergyScreen(BatteryEnergyUiState.Content(FakeBatteryEnergyRepository.SAMPLE.copy(powerKw = -0.8, currentA = -15.6)), {}, {}) },
    "energy_low" to { BatteryEnergyScreen(BatteryEnergyUiState.Content(FakeBatteryEnergyRepository.SAMPLE.copy(socPercent = 22.0, powerKw = 0.0, currentA = 0.0, content = BatteryContent(16.0, 0.6, 2.9))), {}, {}) },
    "energy_pv_only" to { BatteryEnergyScreen(BatteryEnergyUiState.Content(FakeBatteryEnergyRepository.SAMPLE.copy(socPercent = 98.0, content = BatteryContent(16.0, 15.7, 0.0))), {}, {}) },
    "energy_unknown" to { BatteryEnergyScreen(BatteryEnergyUiState.Content(FakeBatteryEnergyRepository.SAMPLE.copy(content = null, cellDeltaMv = null)), {}, {}) },
    "energy_loading" to { BatteryEnergyScreen(BatteryEnergyUiState.Loading, {}, {}) },
    "energy_error" to { BatteryEnergyScreen(BatteryEnergyUiState.Error(noConnection = true), {}, {}) },
    "forgot_find" to { ForgotFindScreen(ForgotFindUiState(account = "0912 345 678"), {}, {}, {}) },
    "forgot_find_invalid" to { ForgotFindScreen(ForgotFindUiState(account = "0912", accountError = AccountError.Invalid), {}, {}, {}) },
    "forgot_find_too_many" to { ForgotFindScreen(ForgotFindUiState(account = "0912 345 678", formError = FindError.TooManyRequests), {}, {}, {}) },
    "forgot_find_sending" to { ForgotFindScreen(ForgotFindUiState(account = "0912 345 678", sending = true), {}, {}, {}) },
    "forgot_otp" to { ForgotOtpScreen(OtpUiState(destination = "0912 ••• 678", code = "482913", resendIn = 45), {}, {}, {}, {}) },
    "forgot_otp_empty" to { ForgotOtpScreen(OtpUiState(destination = "d•••@ggp.vn", code = "48", resendIn = 0), {}, {}, {}, {}) },
    "forgot_otp_wrong" to { ForgotOtpScreen(OtpUiState(destination = "0912 ••• 678", code = "482910", error = OtpError.Wrong(4), resendIn = 12), {}, {}, {}, {}) },
    "forgot_otp_locked" to { ForgotOtpScreen(OtpUiState(destination = "0912 ••• 678", code = "482910", error = OtpError.Locked), {}, {}, {}, {}) },
    "forgot_new" to { ForgotNewScreen(ForgotNewUiState(password = "GGpower2026", confirm = "GGpower2026"), {}, {}, {}, {}) },
    "forgot_new_errors" to { ForgotNewScreen(ForgotNewUiState(password = "abc12", confirm = "abc", passwordError = PasswordError.TooWeak, confirmError = PasswordError.Mismatch), {}, {}, {}, {}) },
    "forgot_new_empty" to { ForgotNewScreen(ForgotNewUiState(), {}, {}, {}, {}) },
    "forgot_done" to { ForgotDoneScreen({}) },
    "register" to { RegisterInfoScreen(RegisterInfoUiState(RegisterSample, agreed = true), {}, {}, {}, {}, {}, {}, {}, {}, {}, {}, {}) },
    "register_unchecked" to { RegisterInfoScreen(RegisterInfoUiState(RegisterSample), {}, {}, {}, {}, {}, {}, {}, {}, {}, {}, {}) },
    "register_errors" to { RegisterInfoScreen(RegisterInfoUiState(RegisterSample.copy(name = "", email = "demo@ggp.vn", phone = "0912", password = "abc", confirm = "ab"), agreed = true, nameError = RegisterFieldError.NameEmpty, emailError = RegisterFieldError.EmailTaken, phoneError = RegisterFieldError.PhoneInvalid, passwordError = PasswordError.TooWeak, confirmError = PasswordError.Mismatch), {}, {}, {}, {}, {}, {}, {}, {}, {}, {}, {}) },
    "register_otp" to { RegisterOtpScreen(OtpUiState(destination = "0912 ••• 678", code = "730516", resendIn = 52), {}, {}, {}, {}) },
    "register_otp_expired" to { RegisterOtpScreen(OtpUiState(destination = "0912 ••• 678", code = "730516", error = OtpError.Expired), {}, {}, {}, {}) },
    "add_device" to { AddDeviceScreen(AddDeviceUiState(serial = FakeAddProjectRepository.SAMPLE_SERIAL, device = FakeAddProjectRepository.SAMPLE_DEVICE), {}, {}, {}, {}, {}, {}) },
    "add_device_empty" to { AddDeviceScreen(AddDeviceUiState(), {}, {}, {}, {}, {}, {}) },
    "add_device_checking" to { AddDeviceScreen(AddDeviceUiState(serial = FakeAddProjectRepository.SAMPLE_SERIAL, checking = true), {}, {}, {}, {}, {}, {}) },
    "add_device_owned" to { AddDeviceScreen(AddDeviceUiState(serial = FakeAddProjectRepository.OWNED_SERIAL, error = SerialError.OwnedByOther), {}, {}, {}, {}, {}, {}) },
    "add_device_not_found" to { AddDeviceScreen(AddDeviceUiState(serial = "SN0000000000", error = SerialError.NotFound), {}, {}, {}, {}, {}, {}) },
    "add_info" to { AddInfoScreen(AddInfoUiState(AddInfoSample), {}, {}, {}, {}, {}, {}) },
    "add_info_errors" to { AddInfoScreen(AddInfoUiState(AddInfoSample.copy(name = "", address = "", pvKwp = "0"), nameError = ProjectInfoError.NameEmpty, addressError = ProjectInfoError.AddressEmpty, kwpError = ProjectInfoError.KwpInvalid), {}, {}, {}, {}, {}, {}) },
    "add_info_located" to { AddInfoScreen(AddInfoUiState(AddInfoSample, locateNote = LocateNote.Found(Coordinates(20.9512, 105.9283))), {}, {}, {}, {}, {}, {}) },
    "add_info_denied" to { AddInfoScreen(AddInfoUiState(AddInfoSample, locateNote = LocateNote.Denied), {}, {}, {}, {}, {}, {}) },
    "add_tariff" to { AddTariffScreen(AddTariffUiState(), {}, {}, {}, {}) },
    "add_tariff_fixed" to { AddTariffScreen(AddTariffUiState(TariffInput(TariffKind.Fixed, fixedPrice = normalizeMoneyInput("2500"))), {}, {}, {}, {}) },
    "add_tariff_fixed_empty" to { AddTariffScreen(AddTariffUiState(TariffInput(TariffKind.Fixed), fixedError = TariffError.FixedEmpty), {}, {}, {}, {}) },
    "add_tariff_limit" to { AddTariffScreen(AddTariffUiState(formError = CreateError.LimitReached), {}, {}, {}, {}) },
    "add_tariff_creating" to { AddTariffScreen(AddTariffUiState(creating = true), {}, {}, {}, {}) },
    "qr" to { QrScanScreen(qrScanSample(""), CameraAccess.Checking, {}, {}, {}, {}, {}, {}, {}) },
    "qr_invalid" to { QrScanScreen(qrScanSample("invalid"), CameraAccess.Checking, {}, {}, {}, {}, {}, {}, {}) },
    "qr_found" to { QrScanScreen(qrScanSample("found"), CameraAccess.Checking, {}, {}, {}, {}, {}, {}, {}) },
    "qr_checking" to { QrScanScreen(qrScanSample("checking"), CameraAccess.Checking, {}, {}, {}, {}, {}, {}, {}) },
    "qr_owned" to { QrScanScreen(qrScanSample("owned"), CameraAccess.Checking, {}, {}, {}, {}, {}, {}, {}) },
    "qr_denied" to { QrScanScreen(qrScanSample(""), CameraAccess.Denied, {}, {}, {}, {}, {}, {}, {}) },
    "config" to { Config(previewConfigState()) },
    "config_owner" to { Config(previewConfigState(role = Role.Owner)) },
    "config_owner_grid" to { Config(previewConfigState(role = Role.Owner, mode = OperatingMode.MaxGridExport)) },
    "config_distributor" to { Config(previewConfigState(role = Role.Distributor)) },
    "config_offline" to { Config(previewConfigState(role = Role.Distributor, writeState = WriteState.GatewayOffline)) },
    "config_read_only" to { Config(previewConfigState(role = Role.Owner, writeState = WriteState.DeviceReadOnly)) },
    "config_bms" to { Config(previewConfigState(role = Role.Owner, bmsBlocked = true)) },
    "config_loading" to { Config(ConfigUiState.Loading) },
    "config_error" to { Config(ConfigUiState.Error(noConnection = false)) },
    "config_confirm_mode" to { Config(previewConfigState().copy(confirm = ConfirmState(ConfigRequest.Mode(OperatingMode.BatteryPriority)))) },
    "config_confirm_stale" to { Config(previewConfigState().copy(backupSocDraft = 30, confirm = ConfirmState(ConfigRequest.BackupSoc(30), stale = true))) },
    "config_writing" to { Config(previewConfigState().let { it.copy(writing = WriteGroup.OperatingMode, controls = it.controls.copy(modes = it.controls.modes.map { m -> m.copy(state = ControlState.Disabled) })) }) },
    "config_applied" to { Config(previewConfigState().copy(notice = ConfigNotice(1, NoticeKind.Applied))) },
    "config_reverted" to { Config(previewConfigState().copy(notice = ConfigNotice(1, NoticeKind.Reverted))) },
    "config_failed" to { Config(previewConfigState().copy(notice = ConfigNotice(1, NoticeKind.Failed))) },
    "config_unknown" to { Config(previewConfigState().copy(showUnknownDialog = true)) },
    "tou_schedule" to { TouScheduleScreen(previewTouScheduleState(), onBack = {}, onRetry = {}, onOpenStrategy = {}, onAddStrategy = {}) },
    "tou_schedule_read_only" to { TouScheduleScreen(previewTouScheduleState(addEnabled = false), onBack = {}, onRetry = {}, onOpenStrategy = {}, onAddStrategy = {}) },
    "tou_schedule_loading" to { TouScheduleScreen(TouScheduleUiState.Loading, onBack = {}, onRetry = {}, onOpenStrategy = {}, onAddStrategy = {}) },
    "tou_strategy" to { TouStrategyScreen(previewTouStrategyState(), onRetry = {}) },
    "tou_strategy_weekend" to { TouStrategyScreen(previewTouStrategyState(1), onRetry = {}) },
    "tou_strategy_changed" to { TouStrategyScreen(previewTouStrategyState(draft = { it.toggleDay(it[0].id, DayOfWeek.SATURDAY) }), onRetry = {}) },
    "tou_strategy_no_days" to { TouStrategyScreen(previewTouStrategyState(draft = { s -> s.map { if (it.id == s[0].id) it.copy(weekdays = emptySet()) else it } }), onRetry = {}) },
    "tou_strategy_rename" to { TouStrategyScreen(previewTouStrategyState().copy(rename = RenameState("", StrategyNameError.Empty)), onRetry = {}) },
    "tou_strategy_discard" to { TouStrategyScreen(previewTouStrategyState().copy(showDiscard = true), onRetry = {}) },
    "tou_strategy_read_only" to { TouStrategyScreen(previewTouStrategyState(editable = false), onRetry = {}) },
    "tou_strategy_edit_sheet" to { TouStrategyScreen(previewTouStrategyState().let { st -> st.copy(slotSheet = SlotSheetState(0, st.strategy.slots[0].toInput(), null)) }, onRetry = {}) },
    "tou_strategy_add_full" to { TouStrategyScreen(previewTouStrategyState(draft = { s -> s.map { if (it.id == s[0].id) it.withSlotAdded(TouSlot(SlotKind.Charge, LocalTime.of(16, 0), LocalTime.of(17, 0), 90, 3000)) else it } }).let { st -> st.copy(slotSheet = SlotSheetState(null, st.strategy.suggestSlot(SlotKind.Charge).toInput(), SlotError.LimitReached(SlotKind.Charge))) }, onRetry = {}) },
    "tou_strategy_overlap" to { TouStrategyScreen(previewTouStrategyState().let { st -> st.copy(slotSheet = SlotSheetState(1, st.strategy.slots[1].toInput().copy(start = "10:00"), SlotError.Overlap(SlotKind.Discharge, 1, LocalTime.of(9, 0), LocalTime.of(12, 0)))) }, onRetry = {}) },
    "tou_strategy_confirm" to { TouStrategyScreen(previewTouStrategyState(draft = { s -> s.toggleDay(s[0].id, DayOfWeek.SATURDAY).map { if (it.id == s[0].id) it.copy(name = "Mùa mưa").withSlotReplaced(1, it.slots[1].copy(maxPowerW = 3500)).withSlotRemoved(3) else it } }).let { st -> st.copy(confirm = TouConfirm(st.draft)) }, onRetry = {}) },
    "tou_strategy_confirm_delete" to { TouStrategyScreen(previewTouStrategyState(1).let { st -> st.copy(confirm = TouConfirm(st.draft.take(1), deleting = true)) }, onRetry = {}) },
    "tou_strategy_writing" to { TouStrategyScreen(previewTouStrategyState(draft = { s -> s.toggleDay(s[0].id, DayOfWeek.SATURDAY).map { if (it.id == s[0].id) it.copy(name = "Mùa mưa").withSlotReplaced(1, it.slots[1].copy(maxPowerW = 3500)).withSlotRemoved(3) else it } }).copy(writing = true), onRetry = {}) },
    "tou_strategy_saved" to { TouStrategyScreen(previewTouStrategyState().copy(notice = ConfigNotice(1, NoticeKind.SavedNotWritten)), onRetry = {}) },
)

@Composable
private fun Config(state: ConfigUiState) = ConfigScreen(state = state, onBack = {}, onRetry = {}, onOpenTouSchedule = {})

@Composable
private fun Home(state: HomeUiState, stats: StatsUiState? = previewStats()) = HomeScreen(
    state = state,
    stats = stats,
    tabs = listOf(
        TabItem(stringResource(R.string.tab_main), R.drawable.ic_tab_main, selected = true) {},
        TabItem(stringResource(R.string.tab_devices), R.drawable.ic_tab_devices, selected = false) {},
        TabItem(stringResource(R.string.tab_service), R.drawable.ic_tab_service, selected = false) {},
    ),
    onOpenAlerts = {}, onOpenConfig = {}, onOpenBatteryEnergy = {},
)

@Composable
private fun Projects(state: ProjectsUiState) = ProjectsScreen(
    state = state,
    tabs = listOf(
        TabItem(stringResource(R.string.tab_projects), R.drawable.ic_tab_projects, selected = true) {},
        TabItem(stringResource(R.string.tab_me), R.drawable.ic_tab_me, selected = false) {},
    ),
    onQueryChange = {}, onRetry = {}, onOpenProject = {}, onAddProject = {},
)

@Composable
private fun Me(state: MeUiState) = MeScreen(
    state = state,
    tabs = listOf(
        TabItem(stringResource(R.string.tab_projects), R.drawable.ic_tab_projects, selected = false) {},
        TabItem(stringResource(R.string.tab_me), R.drawable.ic_tab_me, selected = true) {},
    ),
    onOpenAccount = {}, onNotificationsChange = {}, onTemperatureUnit = {}, onThemeMode = {},
    onToggleLanguageList = {}, onLanguage = {}, onLogout = {},
)

@Composable
private fun Explore(tab: ExploreTab, query: String = "") = ExploreScreen(
    ExploreUiState(tab = tab, query = query, content = FakeExploreRepository.SAMPLE.search(query)),
    onBack = {}, onQueryChange = {}, onTabSelected = {}, onOpenItem = {},
)
