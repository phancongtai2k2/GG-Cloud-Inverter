package vn.ggpower.inverter.data.remote

import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.JsonPrimitive
import vn.ggpower.inverter.domain.device.LinkType
import vn.ggpower.inverter.domain.device.SignalLevel
import android.content.Context
import androidx.core.net.toUri
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flow
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.put
import vn.ggpower.inverter.data.alert.AlertLoadException
import vn.ggpower.inverter.data.alert.AlertRepository
import vn.ggpower.inverter.data.auth.AuthRepository
import vn.ggpower.inverter.data.auth.CodeSendResult
import vn.ggpower.inverter.data.auth.LoginResult
import vn.ggpower.inverter.data.auth.RegisterResult
import vn.ggpower.inverter.data.auth.ResetPasswordResult
import vn.ggpower.inverter.data.auth.SessionStore
import vn.ggpower.inverter.data.auth.VerifyCodeResult
import vn.ggpower.inverter.data.battery.BatteryEnergyLoadException
import vn.ggpower.inverter.data.battery.BatteryEnergyRepository
import vn.ggpower.inverter.data.device.DeviceLoadException
import vn.ggpower.inverter.data.device.DeviceRepository
import vn.ggpower.inverter.data.home.BatterySample
import vn.ggpower.inverter.data.home.ChargedToday
import vn.ggpower.inverter.data.home.DayCurves
import vn.ggpower.inverter.data.home.EnergyBucket
import vn.ggpower.inverter.data.home.HomeOverview
import vn.ggpower.inverter.data.home.HomeRepository
import vn.ggpower.inverter.data.home.PeriodSummary
import vn.ggpower.inverter.data.home.PowerSample
import vn.ggpower.inverter.data.home.YieldBucket
import vn.ggpower.inverter.data.project.AddProjectRepository
import vn.ggpower.inverter.data.project.CreateProjectResult
import vn.ggpower.inverter.data.project.NewProject
import vn.ggpower.inverter.data.project.ProjectLoadException
import vn.ggpower.inverter.data.project.ProjectRepository
import vn.ggpower.inverter.data.project.SerialCheckResult
import vn.ggpower.inverter.data.telemetry.TelemetryLoadException
import vn.ggpower.inverter.data.telemetry.TelemetryRepository
import vn.ggpower.inverter.domain.alert.AlertChart
import vn.ggpower.inverter.domain.alert.AlertDetail
import vn.ggpower.inverter.domain.alert.AlertEndKind
import vn.ggpower.inverter.domain.alert.AlertSeverity
import vn.ggpower.inverter.domain.alert.AlertSummary
import vn.ggpower.inverter.domain.auth.AccountInput
import vn.ggpower.inverter.domain.battery.BatteryEnergy
import vn.ggpower.inverter.domain.config.OperatingMode
import vn.ggpower.inverter.domain.device.BatteryState
import vn.ggpower.inverter.domain.device.BatterySummary
import vn.ggpower.inverter.domain.device.DataloggerSummary
import vn.ggpower.inverter.domain.device.DeviceUnit
import vn.ggpower.inverter.domain.device.InverterSummary
import vn.ggpower.inverter.domain.home.BatteryContent
import vn.ggpower.inverter.domain.home.EnergySnapshot
import vn.ggpower.inverter.domain.home.PeriodSelection
import vn.ggpower.inverter.domain.home.StatsPeriod
import vn.ggpower.inverter.domain.project.DeviceIdentity
import vn.ggpower.inverter.domain.project.ProjectStatus
import vn.ggpower.inverter.domain.project.ProjectSummary
import vn.ggpower.inverter.domain.project.SiteType
import vn.ggpower.inverter.domain.project.TariffKind
import vn.ggpower.inverter.domain.telemetry.BatteryTelemetry
import vn.ggpower.inverter.domain.telemetry.DeviceKind
import vn.ggpower.inverter.domain.telemetry.FieldUnit
import vn.ggpower.inverter.domain.telemetry.HistoryField
import vn.ggpower.inverter.domain.telemetry.HistoryRange
import vn.ggpower.inverter.domain.telemetry.HistorySeries
import vn.ggpower.inverter.domain.telemetry.InverterTelemetry
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.roundToInt

// Các repository của bản `live`: gọi backend theo hợp đồng `docs/api/openapi.yaml`.

private const val V1 = "/api/v1"

private fun status(name: String?): ProjectStatus = when (name) {
    "online" -> ProjectStatus.Online
    "warning" -> ProjectStatus.Warning
    "error" -> ProjectStatus.Error
    "waiting_connection" -> ProjectStatus.WaitingConnection
    else -> ProjectStatus.Offline
}

private fun mode(name: String?): OperatingMode = when (name) {
    "self_consumption" -> OperatingMode.SelfConsumption
    "battery_priority" -> OperatingMode.BatteryPriority
    "max_grid_export" -> OperatingMode.MaxGridExport
    "tou" -> OperatingMode.Tou
    "gg_ai" -> OperatingMode.GgAi
    else -> OperatingMode.Unknown
}

private fun instant(text: String?): Instant = runCatching { Instant.parse(text) }.getOrNull()
    ?: runCatching { java.time.OffsetDateTime.parse(text).toInstant() }.getOrDefault(Instant.EPOCH)

private fun localTime(text: String?): LocalDateTime = runCatching { LocalDateTime.parse(text) }.getOrDefault(LocalDateTime.MIN)

@Singleton
class LiveAuthRepository @Inject constructor(private val api: ApiClient, session: SessionStore) : AuthRepository {

    override val isLoggedIn: Flow<Boolean> = session.isLoggedIn

    private fun account(a: AccountInput): String = when (a) {
        is AccountInput.Email -> a.value
        is AccountInput.Phone -> a.value
        else -> ""
    }

    override suspend fun login(account: AccountInput, password: String): LoginResult = try {
        val r = api.post("$V1/auth/login", buildJsonObject { put("account", account(account)); put("password", password) }, auth = false)
        api.signIn(r.body!!.jsonObject)
        LoginResult.Success
    } catch (e: ApiException) {
        if (e.status in 400..499) LoginResult.InvalidCredentials else LoginResult.ServerError
    } catch (_: NoConnectionException) {
        LoginResult.NoConnection
    }

    override suspend fun logout() {
        runCatching {
            api.refreshTokenForLogout()?.let { api.post("$V1/auth/logout", buildJsonObject { put("refresh_token", it) }, auth = false) }
        }
        api.signOutLocally()
    }

    private suspend fun codeRequest(path: String, body: JsonObject): CodeSendResult = try {
        api.post(path, body, auth = false)
        CodeSendResult.Sent
    } catch (e: ApiException) {
        if (e.status == 429) CodeSendResult.TooManyRequests else CodeSendResult.ServerError
    } catch (_: NoConnectionException) {
        CodeSendResult.NoConnection
    }

    private fun verifyError(e: ApiException): VerifyCodeResult = when (e.code) {
        "CODE_WRONG" -> VerifyCodeResult.Wrong(e.body.int("attempts_left") ?: 0)
        "CODE_EXPIRED" -> VerifyCodeResult.Expired
        "CODE_LOCKED" -> VerifyCodeResult.Locked
        else -> VerifyCodeResult.ServerError
    }

    override suspend fun requestResetCode(account: AccountInput): CodeSendResult =
        codeRequest("$V1/auth/password/forgot", buildJsonObject { put("account", account(account)) })

    override suspend fun verifyResetCode(account: AccountInput, code: String): VerifyCodeResult = try {
        val r = api.post("$V1/auth/password/verify", buildJsonObject { put("account", account(account)); put("code", code) }, auth = false)
        VerifyCodeResult.Valid(r.body!!.jsonObject.str("reset_token")!!)
    } catch (e: ApiException) {
        verifyError(e)
    } catch (_: NoConnectionException) {
        VerifyCodeResult.NoConnection
    }

    override suspend fun resetPassword(token: String, newPassword: String): ResetPasswordResult = try {
        api.post("$V1/auth/password/reset", buildJsonObject { put("reset_token", token); put("new_password", newPassword) }, auth = false)
        ResetPasswordResult.Success
    } catch (e: ApiException) {
        if (e.code == "TOKEN_EXPIRED") ResetPasswordResult.Expired else ResetPasswordResult.ServerError
    } catch (_: NoConnectionException) {
        ResetPasswordResult.NoConnection
    }

    override suspend fun register(name: String, email: AccountInput.Email, phone: AccountInput.Phone, password: String): RegisterResult = try {
        api.post(
            "$V1/auth/register",
            buildJsonObject { put("name", name); put("email", email.value); put("phone", phone.value); put("password", password) },
            auth = false,
        )
        RegisterResult.CodeSent
    } catch (e: ApiException) {
        when (e.code) {
            "EMAIL_TAKEN" -> RegisterResult.EmailTaken
            "PHONE_TAKEN" -> RegisterResult.PhoneTaken
            else -> RegisterResult.ServerError
        }
    } catch (_: NoConnectionException) {
        RegisterResult.NoConnection
    }

    override suspend fun resendRegistrationCode(phone: AccountInput.Phone): CodeSendResult =
        codeRequest("$V1/auth/register/resend", buildJsonObject { put("phone", phone.value) })

    /** Mã đúng: máy chủ kích hoạt tài khoản và trả luôn token, nên người dùng vào thẳng app. */
    override suspend fun verifyRegistration(phone: AccountInput.Phone, code: String): VerifyCodeResult = try {
        val r = api.post("$V1/auth/register/verify", buildJsonObject { put("phone", phone.value); put("code", code) }, auth = false)
        api.signIn(r.body!!.jsonObject)
        VerifyCodeResult.Valid("")
    } catch (e: ApiException) {
        verifyError(e)
    } catch (_: NoConnectionException) {
        VerifyCodeResult.NoConnection
    }
}

@Singleton
class LiveProjectRepository @Inject constructor(private val api: ApiClient) : ProjectRepository {

    override suspend fun getProjects(): List<ProjectSummary> = try {
        api.get("$V1/projects").asObjects().map(::summary)
    } catch (_: NoConnectionException) {
        // Mất mạng: hiện danh sách lần trước nếu có.
        api.cached("$V1/projects")?.asObjects()?.map(::summary) ?: throw ProjectLoadException(ProjectLoadException.Kind.NoConnection)
    } catch (_: ApiException) {
        throw ProjectLoadException(ProjectLoadException.Kind.Server)
    }

    private fun summary(o: JsonObject) = ProjectSummary(
        id = o.str("id").orEmpty(),
        name = o.str("name").orEmpty(),
        address = o.str("address").orEmpty(),
        configuration = o.str("configuration").orEmpty(),
        siteType = if (o.str("site_type") == "factory") SiteType.Factory else SiteType.Home,
        status = status(o.str("status")),
        pvPowerKw = o.num("pv_power_kw"),
        todayEnergyKwh = o.num("today_energy_kwh"),
        batterySocPercent = o.int("battery_soc_percent"),
    )
}

@Singleton
class LiveAddProjectRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val api: ApiClient,
) : AddProjectRepository {

    override suspend fun checkSerial(serial: String): SerialCheckResult = try {
        val o = api.post("$V1/projects/check-serial", buildJsonObject { put("serial", serial) }).body!!.jsonObject
        SerialCheckResult.Valid(
            DeviceIdentity(
                serial = o.str("serial") ?: serial,
                name = o.str("name") ?: "Inverter",
                model = o.str("model").orEmpty(),
                ratedPowerKw = o.num("rated_power_kw") ?: 0.0,
                // Backend chưa có ngày sản xuất (xem OPEN_QUESTIONS): dùng mốc 01/01/1970 để thấy rõ là chưa có.
                manufacturedOn = o.str("manufactured_on")?.let { runCatching { LocalDate.parse(it) }.getOrNull() } ?: LocalDate.of(1970, 1, 1),
            ),
        )
    } catch (e: ApiException) {
        when (e.code) {
            "SERIAL_NOT_FOUND" -> SerialCheckResult.NotFound
            "SERIAL_OWNED" -> SerialCheckResult.OwnedByOther
            else -> SerialCheckResult.ServerError
        }
    } catch (_: NoConnectionException) {
        SerialCheckResult.NoConnection
    }

    override suspend fun createProject(project: NewProject): CreateProjectResult = try {
        val info = project.info
        val body = buildJsonObject {
            put("serial", project.device.serial)
            put("name", info.name.trim())
            put("site_type", if (info.siteType == SiteType.Factory) "factory" else "home")
            put("address", info.address.trim())
            info.coordinates?.let { put("latitude", it.latitude); put("longitude", it.longitude) }
            put("time_zone", info.timeZone)
            put("installed_on", info.installedOn.toString())
            put("pv_kwp", project.pvKwp)
            project.tariff?.let { t ->
                put("tariff", buildJsonObject {
                    put("kind", if (t.kind == TariffKind.Fixed) "fixed" else "evn_tiered")
                    t.fixedPerKwh?.let { put("fixed_per_kwh", it) }
                    put("sell_per_kwh", t.sellPerKwh)
                })
            }
        }
        val id = api.post("$V1/projects", body).body!!.jsonObject.str("project_id")!!
        // Ảnh công trình gửi sau khi có dự án; ảnh lỗi không làm mất dự án vừa tạo.
        info.photoUri?.let { uri ->
            runCatching {
                val bytes = withContext(Dispatchers.IO) { context.contentResolver.openInputStream(uri.toUri())?.use { it.readBytes() } }
                if (bytes != null) api.upload("PUT", "$V1/projects/$id/photo", bytes)
            }
        }
        CreateProjectResult.Created(id)
    } catch (e: ApiException) {
        if (e.code == "PROJECT_LIMIT") CreateProjectResult.LimitReached else CreateProjectResult.ServerError
    } catch (_: NoConnectionException) {
        CreateProjectResult.NoConnection
    }
}

@Singleton
class LiveHomeRepository @Inject constructor(private val api: ApiClient, private val realtime: RealtimeClient) : HomeRepository {

    /**
     * Hiện ngay bản lưu lần trước, rồi bản mới từ máy chủ, sau đó cập nhật theo kênh thời gian thực.
     * Luồng không ném lỗi: khi chưa có gì để hiện thì phát trạng thái "mất kết nối".
     */
    override fun overview(projectId: String): Flow<HomeOverview> = flow {
        val path = "$V1/projects/$projectId/overview"
        var current: HomeOverview? = api.cached(path)?.let { overview(it.jsonObject) }
        current?.let { emit(it) }
        runCatching { api.get(path) }.getOrNull()?.let { current = overview(it.jsonObject); emit(current!!) }
        if (current == null) { current = OFFLINE; emit(OFFLINE) }

        realtime.messages(projectId).collect { m ->
            when (m.str("type")) {
                "device" -> {
                    val t = m.obj("telemetry")
                    val base = current!!
                    current = base.copy(
                        status = status(m.str("status")),
                        unresolvedAlerts = m.int("unresolved_alerts") ?: base.unresolvedAlerts,
                        yieldTodayKwh = t?.num("yield_today_kwh") ?: base.yieldTodayKwh,
                        energy = if (t == null) ZERO else EnergySnapshot(
                            pvKw = (t.num("pv_w") ?: 0.0) / 1000,
                            gridKw = (t.num("grid_w") ?: 0.0) / 1000,
                            batteryKw = (t.num("battery_w") ?: 0.0) / 1000,
                            loadKw = (t.num("load_w") ?: 0.0) / 1000,
                            batterySocPercent = t.num("soc_percent")?.roundToInt() ?: 0,
                        ),
                        battery = m.obj("battery_content")?.let(::content) ?: base.battery,
                        // Thiếu trường (backend cũ) thì giữ số của bản tổng quan; null tường minh thì xoá.
                        co2TodayKg = if ("co2_today_kg" in m) m.num("co2_today_kg") else base.co2TodayKg,
                        chargedToday = if ("battery_charged_today" in m) m.obj("battery_charged_today")?.let(::charged) else base.chargedToday,
                    )
                    emit(current!!)
                }
                // Chế độ hoặc số cảnh báo đổi: đọc lại bản tổng quan.
                "config_changed", "alerts_changed" ->
                    runCatching { api.get(path) }.getOrNull()?.let { current = overview(it.jsonObject); emit(current!!) }
            }
        }
    }.catch { /* phiên hết hạn hoặc kênh đóng hẳn: giữ giá trị cuối */ }

    private fun content(o: JsonObject): BatteryContent? {
        val capacity = o.num("capacity_kwh") ?: return null
        if (capacity <= 0) return null
        return BatteryContent(capacity, o.num("from_pv_kwh") ?: 0.0, o.num("from_grid_kwh") ?: 0.0)
    }

    private fun overview(o: JsonObject): HomeOverview {
        val e = o.obj("energy")
        return HomeOverview(
            projectName = o.str("project_name").orEmpty(),
            status = status(o.str("status")),
            unresolvedAlerts = o.int("unresolved_alerts") ?: 0,
            yieldTodayKwh = o.num("yield_today_kwh") ?: 0.0,
            yieldYesterdayKwh = o.num("yield_yesterday_kwh") ?: 0.0,
            energy = if (e == null) ZERO else EnergySnapshot(
                pvKw = e.num("pv_kw") ?: 0.0, gridKw = e.num("grid_kw") ?: 0.0, batteryKw = e.num("battery_kw") ?: 0.0,
                loadKw = e.num("load_kw") ?: 0.0, batterySocPercent = e.int("battery_soc_percent") ?: 0,
            ),
            mode = mode(o.str("mode")),
            backupSocPercent = o.int("backup_soc_percent") ?: 0,
            battery = o.obj("battery")?.let(::content) ?: NO_BATTERY,
            co2TodayKg = o.num("co2_today_kg"),
            chargedToday = o.obj("battery_charged_today")?.let(::charged),
        )
    }

    /** `BatteryChargedToday` (contract 2026-10-11): kWh sạc vào pin hôm nay theo nguồn. */
    private fun charged(o: JsonObject): ChargedToday =
        ChargedToday(fromPvKwh = o.num("from_pv_kwh") ?: 0.0, fromGridKwh = o.num("from_grid_kwh") ?: 0.0)

    private suspend fun stats(projectId: String, selection: PeriodSelection): JsonObject {
        val period = when (selection.period) {
            StatsPeriod.Day -> "day"
            StatsPeriod.Month -> "month"
            StatsPeriod.Year -> "year"
        }
        val path = "$V1/projects/$projectId/stats?period=$period&anchor=${selection.anchor}"
        return (runCatching { api.get(path) }.getOrNull() ?: api.cached(path) ?: EMPTY).jsonObject
    }

    override suspend fun dayCurves(projectId: String, date: LocalDate): DayCurves {
        val path = "$V1/projects/$projectId/day-curves?date=$date"
        val o = (runCatching { api.get(path) }.getOrNull() ?: api.cached(path) ?: EMPTY).jsonObject
        return DayCurves(
            power = o.arr("power").map {
                PowerSample(it.int("minute") ?: 0, it.num("pv_kw") ?: 0.0, it.num("grid_kw") ?: 0.0, it.num("battery_kw") ?: 0.0, it.num("load_kw") ?: 0.0)
            },
            battery = o.arr("battery").map { BatterySample(it.int("minute") ?: 0, it.num("current_a") ?: 0.0, it.int("soc_percent") ?: 0) },
        )
    }

    override suspend fun energyBuckets(projectId: String, selection: PeriodSelection): List<EnergyBucket> {
        val rows = stats(projectId, selection).arr("energy_buckets")
        return List(selection.bucketCount) { i ->
            val b = rows.getOrNull(i)
            EnergyBucket(
                pvKwh = b?.num("pv_kwh") ?: 0.0, gridNetKwh = b?.num("grid_net_kwh") ?: 0.0,
                batteryChargeKwh = b?.num("battery_charge_kwh") ?: 0.0, batteryDischargeKwh = b?.num("battery_discharge_kwh") ?: 0.0,
                loadKwh = b?.num("load_kwh") ?: 0.0, averageSocPercent = b?.int("average_soc_percent") ?: 0,
            )
        }
    }

    override suspend fun periodSummary(projectId: String, selection: PeriodSelection): PeriodSummary {
        val s = stats(projectId, selection).obj("summary")
        return PeriodSummary(
            consumptionKwh = s?.num("consumption_kwh") ?: 0.0,
            selfUsePercent = s?.int("self_use_percent") ?: 0,
            savingVnd = s?.num("saving_vnd")?.toLong() ?: 0L,
            co2Kg = s?.num("co2_kg") ?: 0.0,
        )
    }

    override suspend fun yieldBuckets(projectId: String, selection: PeriodSelection): List<YieldBucket> {
        val rows = stats(projectId, selection).arr("yield_buckets")
        return List(selection.bucketCount) { i ->
            val b = rows.getOrNull(i)
            YieldBucket(b?.num("self_kwh") ?: 0.0, b?.num("charge_kwh") ?: 0.0, b?.num("export_kwh") ?: 0.0)
        }
    }

    private companion object {
        val EMPTY: JsonElement = JsonObject(emptyMap())
        val ZERO = EnergySnapshot(0.0, 0.0, 0.0, 0.0, 0)
        // Model của màn chính chưa có trạng thái "không có pin": dùng dung lượng giả 1 kWh, phần đã nạp bằng 0.
        val NO_BATTERY = BatteryContent(capacityKwh = 1.0, fromPvKwh = 0.0, fromGridKwh = 0.0)
        val OFFLINE = HomeOverview("", ProjectStatus.Offline, 0, 0.0, 0.0, ZERO, OperatingMode.Unknown, 0, NO_BATTERY)
    }
}

@Singleton
class LiveDeviceRepository @Inject constructor(private val api: ApiClient) : DeviceRepository {

    override suspend fun getDevices(projectId: String): List<DeviceUnit> {
        val path = "$V1/projects/$projectId/devices"
        val data = try {
            api.get(path)
        } catch (_: NoConnectionException) {
            api.cached(path) ?: throw DeviceLoadException(DeviceLoadException.Kind.NoConnection)
        } catch (_: ApiException) {
            throw DeviceLoadException(DeviceLoadException.Kind.Server)
        }
        return data.asObjects().map { o ->
            val inv = o.obj("inverter")!!
            val bat = o.obj("battery")
            DeviceUnit(
                inverter = InverterSummary(
                    name = inv.str("name").orEmpty(), serial = inv.str("serial").orEmpty(), status = status(inv.str("status")),
                    powerKw = inv.num("power_kw"), temperatureC = inv.num("temperature_c"), firmware = inv.str("firmware"),
                ),
                battery = bat?.let {
                    BatterySummary(
                        name = it.str("name").orEmpty(),
                        state = when (it.str("state")) {
                            "charging" -> BatteryState.Charging
                            "discharging" -> BatteryState.Discharging
                            else -> BatteryState.Idle
                        },
                        hoursRemaining = it.num("hours_remaining"), socPercent = it.int("soc_percent"), powerKw = it.num("power_kw"),
                        voltageV = it.num("voltage_v"), sohPercent = it.int("soh_percent"),
                    )
                },
                // Khối `datalogger` (contract 2026-10-11); backend cũ không có thì suy từ inverter như trước: firmware lấy từ
                // logger_firmware, online theo trạng thái inverter (datalogger là đường liên lạc duy nhất).
                datalogger = o.obj("datalogger")?.let(::datalogger) ?: DataloggerSummary(
                    serial = null,
                    online = when (inv.str("status")) {
                        null, "waiting_connection" -> null
                        "offline" -> false
                        else -> true
                    },
                    signal = null,
                    link = null,
                    firmware = inv.str("logger_firmware"),
                ),
            )
        }
    }

    /** `Datalogger` (contract 2026-10-11): RSSI quy ra mức tín hiệu; kiểu kết nối lạ thì để trống ("—"). */
    private fun datalogger(o: JsonObject) = DataloggerSummary(
        serial = o.str("serial"),
        online = (o["online"] as? JsonPrimitive)?.booleanOrNull,
        signal = SignalLevel.fromRssi(o.int("rssi_dbm")),
        link = when (o.str("connection")) {
            "wifi" -> LinkType.WiFi
            "ethernet" -> LinkType.Ethernet
            "4g" -> LinkType.Cellular
            else -> null
        },
        firmware = o.str("firmware"),
    )
}

/** Ảnh chụp trạng thái thiết bị: bản lưu → bản mới qua REST → cập nhật qua kênh thời gian thực. */
private fun deviceSnapshots(api: ApiClient, realtime: RealtimeClient, projectId: String): Flow<JsonObject> = flow {
    val path = "$V1/projects/$projectId/telemetry"
    var emitted = false
    api.cached(path)?.let { emit(it.jsonObject); emitted = true }
    try {
        emit(api.get(path).jsonObject)
        emitted = true
    } catch (e: NoConnectionException) {
        if (!emitted) throw e
    }
    realtime.messages(projectId).collect { if (it.str("type") == "device") emit(it) }
}

@Singleton
class LiveTelemetryRepository @Inject constructor(private val api: ApiClient, private val realtime: RealtimeClient) : TelemetryRepository {

    private fun <T> Flow<T>.mapErrors(): Flow<T> = catch { e ->
        throw TelemetryLoadException(if (e is NoConnectionException) TelemetryLoadException.Kind.NoConnection else TelemetryLoadException.Kind.Server)
    }

    override fun inverter(projectId: String): Flow<InverterTelemetry> = flow {
        deviceSnapshots(api, realtime, projectId).collect { m ->
            val t = m.obj("telemetry")
            emit(
                InverterTelemetry(
                    name = m.str("model") ?: "Inverter", serial = m.str("serial").orEmpty(), status = status(m.str("status")),
                    updatedAt = instant(m.str("updated_at")),
                    // Trạng thái máy do datalogger báo (0–3); model yêu cầu chuỗi tiếng Việt đã giải mã.
                    machineState = when (t?.int("inv_status")) {
                        0 -> "Đang khởi tạo"
                        1 -> "Chờ (standby)"
                        2 -> "Chạy điện lưới"
                        3 -> "Chạy inverter"
                        else -> null
                    },
                    pv1VoltageV = t?.num("pv1_v"), pv1CurrentA = t?.num("pv1_a"), pv1PowerW = t?.num("pv1_w"),
                    pv2VoltageV = t?.num("pv2_v"), pv2CurrentA = t?.num("pv2_a"), pv2PowerW = t?.num("pv2_w"),
                    gridVoltageV = t?.num("grid_v"), gridCurrentA = t?.num("grid_a"), gridFrequencyHz = t?.num("grid_hz"), gridPowerW = t?.num("grid_w"),
                    outputVoltageV = t?.num("load_v"), outputCurrentA = t?.num("load_a"),
                    // Datalogger chỉ gửi một công suất tải (ngõ ra); chưa tách tải UPS / tải nhà.
                    upsLoadW = t?.num("load_w"), loadApparentVa = null, loadRatioPercent = null, homeLoadW = null,
                    gridChargeCurrentA = null, pvChargeCurrentA = null,
                    tempDcDcC = null, tempDcAcC = t?.num("inv_temp_c"), tempTransformerC = null, tempAmbientC = null,
                    // Mã lỗi inverter đang báo (máy chủ giữ theo cảnh báo chưa hết); máy chủ cũ chưa gửi thì để trống.
                    activeErrors = if (!m.has("active_errors")) null else m.arr("active_errors").map { e ->
                        listOfNotNull(e.str("code"), e.str("title")?.takeIf { it.isNotBlank() }).joinToString(" · ")
                    },
                ),
            )
        }
    }.mapErrors()

    override fun battery(projectId: String): Flow<BatteryTelemetry> = flow {
        deviceSnapshots(api, realtime, projectId).collect { m ->
            val b = m.obj("battery")
            emit(
                BatteryTelemetry(
                    name = b?.str("model") ?: "Battery", serial = b?.str("bms_serial").orEmpty(), status = status(m.str("status")),
                    updatedAt = instant(m.str("updated_at")),
                    socPercent = b?.num("soc_percent"), sohPercent = b?.num("soh_percent"), cycles = b?.int("cycles"),
                    packVoltageInverterV = b?.num("voltage_v"), packVoltageBmsV = null, packCurrentA = b?.num("current_a"),
                    tempBmsC = b?.num("temp_bms_c"), tempInverterC = b?.num("temp_cells_c"),
                    chargeLimitVoltageV = null, chargeLimitCurrentA = null, dischargeLimitCurrentA = null, chargeLimitMode = null,
                    cellMaxMv = null, cellMaxIndex = null, cellMinMv = null, cellMinIndex = null, cellTempMaxC = null, cellTempMinC = null,
                    packsOnline = null, packsChargeBlocked = null, packsDischargeBlocked = null, packsOffline = null,
                    systemFaults = null, flags = null, manufacturer = null, softwareVersion = null, hardwareVersion = null, protocolVersion = null,
                ),
            )
        }
    }.mapErrors()

    override suspend fun history(projectId: String, device: DeviceKind, range: HistoryRange, fields: List<HistoryField>, now: LocalDateTime): HistorySeries {
        val name = when (range) {
            HistoryRange.Hour -> "hour"
            HistoryRange.Day -> "day"
            HistoryRange.Week -> "week"
        }
        val path = "$V1/projects/$projectId/history?range=$name&fields=${fields.joinToString(",") { it.id }}"
        val o = try {
            api.get(path).jsonObject
        } catch (_: NoConnectionException) {
            api.cached(path)?.jsonObject ?: throw TelemetryLoadException(TelemetryLoadException.Kind.NoConnection)
        } catch (_: ApiException) {
            throw TelemetryLoadException(TelemetryLoadException.Kind.Server)
        }
        val size = range.spanMinutes / range.stepMinutes + 1
        val values = o.obj("values")
        return HistorySeries(
            range = range,
            start = localTime(o.str("start")),
            values = fields.associate { f ->
                val list = (values?.get(f.id) as? kotlinx.serialization.json.JsonArray)
                    ?.map { (it as? kotlinx.serialization.json.JsonPrimitive)?.content?.toDoubleOrNull() }.orEmpty()
                f.id to List(size) { i -> list.getOrNull(i) }
            },
        )
    }
}

@Singleton
class LiveAlertRepository @Inject constructor(private val api: ApiClient, private val realtime: RealtimeClient) : AlertRepository {

    private fun severity(name: String?) = when (name) {
        "error" -> AlertSeverity.Error
        "warning" -> AlertSeverity.Warning
        else -> AlertSeverity.Info
    }

    private fun <T> Flow<T>.mapErrors(): Flow<T> = catch { e ->
        throw AlertLoadException(if (e is NoConnectionException) AlertLoadException.Kind.NoConnection else AlertLoadException.Kind.Server)
    }

    /** Danh sách tự làm mới khi máy chủ báo có thay đổi. */
    override fun alerts(projectId: String): Flow<List<AlertSummary>> = flow {
        val path = "$V1/projects/$projectId/alerts"
        suspend fun load() = api.get(path).asObjects().map {
            AlertSummary(
                id = it.str("id").orEmpty(), severity = severity(it.str("severity")), title = it.str("title").orEmpty(),
                description = it.str("description").orEmpty(), startedAt = localTime(it.str("started_at")), resolved = it.bool("resolved") ?: false,
            )
        }
        emit(load())
        realtime.messages(projectId).collect { if (it.str("type") == "alerts_changed") runCatching { load() }.getOrNull()?.let { list -> emit(list) } }
    }.mapErrors()

    override fun detail(projectId: String, alertId: String): Flow<AlertDetail> = flow {
        val path = "$V1/projects/$projectId/alerts/$alertId"
        suspend fun load(): AlertDetail {
            val o = api.get(path).jsonObject
            val chart = o.obj("chart")?.let { c ->
                AlertChart(
                    label = "", unit = FieldUnit.Volt, kind = AlertChart.ChartKind.Grid,
                    values = (c["values"] as? kotlinx.serialization.json.JsonArray)
                        ?.mapNotNull { (it as? kotlinx.serialization.json.JsonPrimitive)?.content?.toDoubleOrNull() }.orEmpty(),
                    start = localTime(c.str("start")), stepSeconds = (c.int("step_seconds") ?: 60).toLong(), markIndex = c.int("mark_index") ?: 0, note = "",
                )
            }
            fun strings(name: String) = (o[name] as? kotlinx.serialization.json.JsonArray)
                ?.mapNotNull { (it as? kotlinx.serialization.json.JsonPrimitive)?.content }.orEmpty()
            return AlertDetail(
                id = o.str("id").orEmpty(), severity = severity(o.str("severity")), title = o.str("title").orEmpty(),
                description = o.str("description").orEmpty(), device = o.str("device").orEmpty(), projectName = o.str("project_name").orEmpty(),
                startedAt = localTime(o.str("started_at")), endedAt = o.str("ended_at")?.let(::localTime),
                endKind = if (o.has("end_kind")) AlertEndKind.AutoRecovered else null,
                resolved = o.bool("resolved") ?: false, code = o.str("code").orEmpty(), codeName = o.str("code_name").orEmpty(),
                chart = chart?.takeIf { it.values.isNotEmpty() }, causes = strings("causes"), steps = strings("steps"),
            )
        }
        emit(load())
        realtime.messages(projectId).collect { if (it.str("type") == "alerts_changed") runCatching { load() }.getOrNull()?.let { d -> emit(d) } }
    }.mapErrors()

    override suspend fun setResolved(projectId: String, alertId: String, resolved: Boolean) {
        try {
            api.put("$V1/projects/$projectId/alerts/$alertId/resolved", buildJsonObject { put("resolved", resolved) })
        } catch (_: NoConnectionException) {
            throw AlertLoadException(AlertLoadException.Kind.NoConnection)
        } catch (_: ApiException) {
            throw AlertLoadException(AlertLoadException.Kind.Server)
        }
    }
}

@Singleton
class LiveBatteryEnergyRepository @Inject constructor(private val api: ApiClient, private val realtime: RealtimeClient) : BatteryEnergyRepository {

    override fun batteryEnergy(projectId: String): Flow<BatteryEnergy> = flow {
        deviceSnapshots(api, realtime, projectId).collect { m ->
            val b = m.obj("battery")
            val c = m.obj("battery_content")
            val current = b?.num("current_a")
            val voltage = b?.num("voltage_v")
            emit(
                BatteryEnergy(
                    name = b?.str("model") ?: "Battery",
                    socPercent = b?.num("soc_percent"),
                    powerKw = if (current != null && voltage != null) current * voltage / 1000 else null,
                    content = c?.num("capacity_kwh")?.takeIf { it > 0 }?.let { BatteryContent(it, c.num("from_pv_kwh") ?: 0.0, c.num("from_grid_kwh") ?: 0.0) },
                    voltageV = voltage, currentA = current, temperatureC = b?.num("temp_cells_c"),
                    sohPercent = b?.num("soh_percent"), cycles = b?.int("cycles"), cellDeltaMv = null,
                    chargeLimitSocPercent = b?.int("charge_limit_soc_percent"),
                    dischargeLimitSocPercent = b?.int("discharge_limit_soc_percent"),
                ),
            )
        }
    }.catch { e -> throw BatteryEnergyLoadException(noConnection = e is NoConnectionException) }
}
