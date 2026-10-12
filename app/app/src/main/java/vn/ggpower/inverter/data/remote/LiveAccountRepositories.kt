package vn.ggpower.inverter.data.remote

import vn.ggpower.inverter.domain.config.BlockReason
import android.content.Context
import android.net.Uri
import androidx.core.net.toUri
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject
import vn.ggpower.inverter.data.account.DeleteAccountResult
import vn.ggpower.inverter.data.account.ProfileRepository
import vn.ggpower.inverter.data.account.SaveProfileResult
import vn.ggpower.inverter.data.config.ConfigLoadException
import vn.ggpower.inverter.data.config.ConfigRepository
import vn.ggpower.inverter.data.explore.ExploreRepository
import vn.ggpower.inverter.data.support.SupportContactRepository
import vn.ggpower.inverter.data.support.SupportTicketException
import vn.ggpower.inverter.data.support.SupportTicketRepository
import vn.ggpower.inverter.data.warranty.WarrantyLoadException
import vn.ggpower.inverter.data.warranty.WarrantyRepository
import vn.ggpower.inverter.domain.account.NotificationPrefs
import vn.ggpower.inverter.domain.account.UserProfile
import vn.ggpower.inverter.domain.config.ChangedValue
import vn.ggpower.inverter.domain.config.CommandStatus
import vn.ggpower.inverter.domain.config.ConfigChange
import vn.ggpower.inverter.domain.config.ConfigCommand
import vn.ggpower.inverter.domain.config.ConfigRequest
import vn.ggpower.inverter.domain.config.ConfigWriteException
import vn.ggpower.inverter.domain.config.DeviceSettings
import vn.ggpower.inverter.domain.config.DeviceState
import vn.ggpower.inverter.domain.config.FailureCode
import vn.ggpower.inverter.domain.config.OperatingMode
import vn.ggpower.inverter.domain.config.ProjectConfig
import vn.ggpower.inverter.domain.config.ProtectionSettings
import vn.ggpower.inverter.domain.config.Role
import vn.ggpower.inverter.domain.config.WriteAccess
import vn.ggpower.inverter.domain.config.WriteErrorCode
import vn.ggpower.inverter.domain.config.WriteGroup
import vn.ggpower.inverter.domain.config.WriteState
import vn.ggpower.inverter.domain.explore.ExploreArticle
import vn.ggpower.inverter.domain.explore.ExploreContent
import vn.ggpower.inverter.domain.explore.ExploreIcon
import vn.ggpower.inverter.domain.explore.ExploreProduct
import vn.ggpower.inverter.domain.explore.ExploreVideo
import vn.ggpower.inverter.domain.settings.AppLanguage
import vn.ggpower.inverter.domain.support.MessageAuthor
import vn.ggpower.inverter.domain.support.NewTicket
import vn.ggpower.inverter.domain.support.SupportContact
import vn.ggpower.inverter.domain.support.TicketDetail
import vn.ggpower.inverter.domain.support.TicketMessage
import vn.ggpower.inverter.domain.support.TicketStatus
import vn.ggpower.inverter.domain.support.TicketSummary
import vn.ggpower.inverter.domain.support.TicketType
import vn.ggpower.inverter.domain.tou.SlotKind
import vn.ggpower.inverter.domain.tou.TouSlot
import vn.ggpower.inverter.domain.tou.TouStrategy
import vn.ggpower.inverter.domain.warranty.DeviceWarranty
import vn.ggpower.inverter.domain.warranty.ProjectWarranty
import vn.ggpower.inverter.domain.warranty.WarrantyChannel
import vn.ggpower.inverter.domain.warranty.WarrantyEntryStatus
import vn.ggpower.inverter.domain.warranty.WarrantyHistoryEntry
import vn.ggpower.inverter.domain.warranty.WarrantyWork
import java.io.File
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.OffsetDateTime
import java.time.ZoneId
import javax.inject.Inject
import javax.inject.Singleton

// Bản `live` của Cấu hình, Hồ sơ, Phiếu hỗ trợ, Bảo hành, Khám phá và Liên hệ hỗ trợ.

private const val V1 = "/api/v1"

/** Thời điểm UTC của máy chủ đổi sang giờ của máy. */
private fun utcToLocal(text: String?): LocalDateTime {
    val instant = runCatching { Instant.parse(text) }.getOrNull()
        ?: runCatching { OffsetDateTime.parse(text).toInstant() }.getOrNull()
        // Máy chủ có thể trả thời điểm UTC không kèm múi giờ.
        ?: runCatching { LocalDateTime.parse(text).atZone(ZoneId.of("UTC")).toInstant() }.getOrDefault(Instant.EPOCH)
    return LocalDateTime.ofInstant(instant, ZoneId.systemDefault())
}

private fun strings(o: JsonObject, name: String): List<String> =
    (o[name] as? JsonArray)?.mapNotNull { (it as? JsonPrimitive)?.takeIf { p -> p.isString }?.content }.orEmpty()

private fun operatingMode(name: String?): OperatingMode = when (name) {
    "self_consumption" -> OperatingMode.SelfConsumption
    "battery_priority" -> OperatingMode.BatteryPriority
    "max_grid_export" -> OperatingMode.MaxGridExport
    "tou" -> OperatingMode.Tou
    "gg_ai" -> OperatingMode.GgAi
    else -> OperatingMode.Unknown
}

private fun modeName(mode: OperatingMode): String = when (mode) {
    OperatingMode.SelfConsumption -> "self_consumption"
    OperatingMode.BatteryPriority -> "battery_priority"
    OperatingMode.MaxGridExport -> "max_grid_export"
    OperatingMode.Tou -> "tou"
    OperatingMode.GgAi -> "gg_ai"
    OperatingMode.Unknown -> "unknown"
}

private val DAYS = listOf(
    "mon" to DayOfWeek.MONDAY, "tue" to DayOfWeek.TUESDAY, "wed" to DayOfWeek.WEDNESDAY, "thu" to DayOfWeek.THURSDAY,
    "fri" to DayOfWeek.FRIDAY, "sat" to DayOfWeek.SATURDAY, "sun" to DayOfWeek.SUNDAY,
)

@Singleton
class LiveConfigRepository @Inject constructor(private val api: ApiClient) : ConfigRepository {

    private val serials = mutableMapOf<String, String>()

    /** Cấu hình gắn với thiết bị (serial); màn hình làm việc theo dự án, nên tra serial của inverter đầu tiên. */
    private suspend fun serial(projectId: String): String {
        serials[projectId]?.let { return it }
        val path = "$V1/projects/$projectId/devices"
        val devices = try {
            api.get(path)
        } catch (_: NoConnectionException) {
            api.cached(path) ?: throw ConfigLoadException(ConfigLoadException.Kind.NoConnection)
        } catch (_: ApiException) {
            throw ConfigLoadException(ConfigLoadException.Kind.Server)
        }
        val serial = devices.asObjects().firstOrNull()?.obj("inverter")?.str("serial") ?: throw ConfigLoadException(ConfigLoadException.Kind.Server)
        serials[projectId] = serial
        return serial
    }

    private fun time(o: JsonObject?): LocalTime = LocalTime.of(o?.int("hour") ?: 0, o?.int("minute") ?: 0)

    private fun slots(o: JsonObject, name: String, kind: SlotKind): List<TouSlot> = o.arr(name).map {
        TouSlot(kind, time(it.obj("start")), time(it.obj("end")), it.int("stop_soc_percent") ?: 0, it.int("max_power_w") ?: 0)
    }

    private fun strategy(o: JsonObject) = TouStrategy(
        id = o.str("id").orEmpty(),
        name = o.str("name").orEmpty(),
        weekdays = strings(o, "weekdays").mapNotNull { d -> DAYS.firstOrNull { it.first == d }?.second }.toSet(),
        slots = slots(o, "charge_slots", SlotKind.Charge) + slots(o, "discharge_slots", SlotKind.Discharge),
    )

    private fun access(o: JsonObject?): WriteAccess = WriteAccess(
        state = when (o?.str("state")) {
            "enabled" -> WriteState.Enabled
            "disabled_by_server" -> WriteState.DisabledByServer
            "gateway_offline" -> WriteState.GatewayOffline
            else -> WriteState.DeviceReadOnly
        },
        role = when (o?.str("role")) {
            "distributor" -> Role.Distributor
            "ggp_technician" -> Role.GgpTechnician
            else -> Role.Owner
        },
        writableGroups = o?.let { strings(it, "writable_groups") }.orEmpty().mapNotNull {
            when (it) {
                "operating_mode" -> WriteGroup.OperatingMode
                "backup_soc" -> WriteGroup.BackupSoc
                "tou_schedule" -> WriteGroup.TouSchedule
                else -> null
            }
        }.toSet(),
        // Chỉ BMS_NOT_CONNECTED cần lời giải thích riêng (contract 2026-10-11); NOT_SUPPORTED_BY_DEVICE đã không nằm trong
        // writable_groups nên control tự bị khoá.
        blockedGroups = o?.arr("blocked_groups").orEmpty()
            .filter { it.str("group") == "backup_soc" && it.str("reason") == "BMS_NOT_CONNECTED" }
            .associate { WriteGroup.BackupSoc to BlockReason.BmsNotConnected },
        writableModes = o?.let { strings(it, "writable_modes") }.orEmpty().map(::operatingMode).filter { it != OperatingMode.Unknown }.toSet(),
    )

    override suspend fun getConfig(projectId: String): ProjectConfig {
        val serial = serial(projectId)
        val path = "$V1/devices/$serial/config"
        val o = try {
            api.get(path).jsonObject
        } catch (_: NoConnectionException) {
            api.cached(path)?.jsonObject ?: throw ConfigLoadException(ConfigLoadException.Kind.NoConnection)
        } catch (_: ApiException) {
            throw ConfigLoadException(ConfigLoadException.Kind.Server)
        }
        val s = o.obj("settings")
        val capacity = s?.num("battery_capacity_kwh") ?: 0.0
        return ProjectConfig(
            version = o.str("version").orEmpty(),
            mode = operatingMode(o.str("mode")),
            backupSocPercent = o.int("backup_soc_percent") ?: 0,
            strategies = o.arr("strategies").map(::strategy),
            todayStrategyId = o.str("today_strategy_id"),
            writeAccess = access(o.obj("write_access")),
            settings = DeviceSettings(
                batteryCapacityKwh = capacity,
                // Không biết dung lượng pin thì không ước tính giờ dự phòng.
                averageLoadKw = if (capacity > 0) s?.num("average_load_kw_7d") ?: 0.0 else 0.0,
                stopChargeSocPercent = 0, lowSocAlarmPercent = 0, switchToGridSocPercent = 0, maxChargeCurrentA = 0, bmsChargeLimitA = null,
                gridChargeCurrentA = s?.int("grid_charge_current_a") ?: 0, chargeLimitMode = 0, gridWorkMode = 0,
                zeroExportW = s?.int("zero_export_w") ?: 0, maxLinePowerW = 0, outputVoltageV = 0, outputFrequencyHz = 0,
                protection = ProtectionSettings(false, false, false, false, false),
                bmsComm = 0, bmsProtocol = "", modbusAddress = 0, parallelMode = 0,
                available = false,
            ),
            history = history(serial),
        )
    }

    /** Lịch sử thay đổi: các lệnh đổi chế độ đã áp dụng (nhật ký 90 ngày của máy chủ). */
    private suspend fun history(serial: String): List<ConfigChange> {
        val items = runCatching { api.get("$V1/devices/$serial/config-commands?limit=20").jsonObject.arr("items") }.getOrDefault(emptyList())
        return items.filter { it.str("status") == "applied" }.mapNotNull { c ->
            val change = c.arr("changes").firstOrNull { it.str("parameter") == "operating_mode" } ?: return@mapNotNull null
            ConfigChange(
                value = ChangedValue.Mode(operatingMode(change.str("old")), operatingMode(change.str("new"))),
                actor = c.obj("initiator")?.str("display_name").orEmpty(),
                at = utcToLocal(c.str("updated_at")),
            )
        }
    }

    private fun command(o: JsonObject) = ConfigCommand(
        id = o.str("command_id").orEmpty(),
        group = when (o.str("group")) {
            "backup_soc" -> WriteGroup.BackupSoc
            "tou_schedule" -> WriteGroup.TouSchedule
            else -> WriteGroup.OperatingMode
        },
        status = when (o.str("status")) {
            "queued" -> CommandStatus.Queued
            "writing" -> CommandStatus.Writing
            "verifying" -> CommandStatus.Verifying
            "reverting" -> CommandStatus.Reverting
            "applied" -> CommandStatus.Applied
            "reverted" -> CommandStatus.Reverted
            else -> CommandStatus.Failed
        },
        pollAfterMs = o.int("poll_after_ms")?.toLong(),
        deviceWritten = o.obj("outcome")?.bool("device_written"),
        deviceState = when (o.obj("outcome")?.str("device_state")) {
            "new" -> DeviceState.New
            "old_restored" -> DeviceState.OldRestored
            "old_unchanged" -> DeviceState.OldUnchanged
            "unknown" -> DeviceState.Unknown
            else -> null
        },
        failure = when (o.obj("failure")?.str("code")) {
            "READBACK_MISMATCH" -> FailureCode.ReadbackMismatch
            "ACK_TIMEOUT" -> FailureCode.AckTimeout
            "GATEWAY_OFFLINE" -> FailureCode.GatewayOffline
            "GATEWAY_ERROR" -> FailureCode.GatewayError
            "MODBUS_EXCEPTION" -> FailureCode.ModbusException
            else -> null
        },
        unsupported = o.obj("outcome")?.let { strings(it, "unsupported") }.orEmpty(),
    )

    private fun slotJson(s: TouSlot) = buildJsonObject {
        putJsonObject("start") { put("hour", s.start.hour); put("minute", s.start.minute) }
        putJsonObject("end") { put("hour", s.end.hour); put("minute", s.end.minute) }
        put("stop_soc_percent", s.stopSocPercent)
        put("max_power_w", s.maxPowerW)
    }

    override suspend fun submit(projectId: String, request: ConfigRequest, basedOnVersion: String, idempotencyKey: String): ConfigCommand {
        val serial = serial(projectId)
        val body = buildJsonObject {
            put("based_on_version", basedOnVersion)
            when (request) {
                is ConfigRequest.Mode -> { put("group", "operating_mode"); put("mode", modeName(request.mode)) }
                is ConfigRequest.BackupSoc -> { put("group", "backup_soc"); put("soc_percent", request.percent) }
                is ConfigRequest.Tou -> {
                    put("group", "tou_schedule")
                    putJsonArray("strategies") {
                        request.strategies.forEach { st ->
                            add(buildJsonObject {
                                put("id", st.id)
                                put("name", st.name)
                                putJsonArray("weekdays") { DAYS.filter { it.second in st.weekdays }.forEach { add(it.first) } }
                                put("charge_slots", buildJsonArray { st.slotsOf(SlotKind.Charge).forEach { add(slotJson(it)) } })
                                put("discharge_slots", buildJsonArray { st.slotsOf(SlotKind.Discharge).forEach { add(slotJson(it)) } })
                            })
                        }
                    }
                }
            }
        }
        return try {
            command(api.post("$V1/devices/$serial/config-commands", body, headers = mapOf("Idempotency-Key" to idempotencyKey)).body!!.jsonObject)
        } catch (e: ApiException) {
            val code = when (e.code) {
                "UNAUTHENTICATED" -> WriteErrorCode.Unauthenticated
                "ROLE_NOT_PERMITTED" -> WriteErrorCode.RoleNotPermitted
                "GROUP_NOT_IN_ALLOWLIST" -> WriteErrorCode.GroupNotInAllowlist
                "DEVICE_NOT_FOUND" -> WriteErrorCode.DeviceNotFound
                "DEVICE_READ_ONLY" -> WriteErrorCode.DeviceReadOnly
                "WRITE_DISABLED_BY_SERVER" -> WriteErrorCode.WriteDisabledByServer
                "GATEWAY_OFFLINE" -> WriteErrorCode.GatewayOffline
                "BMS_NOT_CONNECTED" -> WriteErrorCode.BmsNotConnected
                "VALIDATION_FAILED" -> WriteErrorCode.ValidationFailed
                "CONFIG_STALE" -> WriteErrorCode.ConfigStale
                "COMMAND_IN_PROGRESS" -> WriteErrorCode.CommandInProgress
                "IDEMPOTENCY_KEY_REUSED" -> WriteErrorCode.IdempotencyKeyReused
                "RATE_LIMITED" -> WriteErrorCode.RateLimited
                else -> throw ConfigLoadException(ConfigLoadException.Kind.Server)
            }
            throw ConfigWriteException(code, e.body.int("retry_after_seconds"))
        } catch (_: NoConnectionException) {
            throw ConfigLoadException(ConfigLoadException.Kind.NoConnection)
        }
    }

    override suspend fun command(projectId: String, commandId: String): ConfigCommand = try {
        // Không dùng bản lưu: trạng thái lệnh đổi theo từng giây.
        command(api.get("$V1/devices/${serial(projectId)}/config-commands/$commandId").jsonObject)
    } catch (_: NoConnectionException) {
        throw ConfigLoadException(ConfigLoadException.Kind.NoConnection)
    } catch (_: ApiException) {
        throw ConfigLoadException(ConfigLoadException.Kind.Server)
    }
}

@Singleton
class LiveProfileRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val api: ApiClient,
) : ProfileRepository {

    private val profile = MutableStateFlow<UserProfile?>(null)
    private var avatarVersion: String? = null

    /** Ảnh đại diện cần token để tải, nên giữ một bản trong bộ nhớ đệm của app và đưa đường dẫn tệp cho giao diện. */
    private suspend fun avatar(url: String?): String? {
        if (url == null) return null
        val file = File(context.cacheDir, "avatar_" + url.substringAfter("v=", "0") + ".img")
        if (!file.exists() || file.length() == 0L) {
            context.cacheDir.listFiles { f -> f.name.startsWith("avatar_") }?.forEach { it.delete() }
            if (!runCatching { api.download(url, file) }.getOrDefault(false)) return null
        }
        return Uri.fromFile(file).toString()
    }

    private suspend fun parse(o: JsonObject): UserProfile {
        val n = o.obj("notifications")
        avatarVersion = o.str("avatar_url")
        return UserProfile(
            name = o.str("name").orEmpty(), email = o.str("email").orEmpty(), phone = o.str("phone").orEmpty(),
            avatarUri = avatar(o.str("avatar_url")),
            notifications = NotificationPrefs(
                deviceAlerts = n?.bool("device_alerts") ?: true, dailyReport = n?.bool("daily_report") ?: true, ggPowerNews = n?.bool("gg_power_news") ?: false,
            ),
        )
    }

    private suspend fun load(): UserProfile {
        val o = try {
            api.get("$V1/me").jsonObject
        } catch (e: NoConnectionException) {
            api.cached("$V1/me")?.jsonObject ?: throw e
        }
        return parse(o).also { profile.value = it }
    }

    override suspend fun getProfile(): UserProfile = load()

    override fun observeProfile(): Flow<UserProfile> = flow {
        api.cached("$V1/me")?.let { runCatching { profile.value = parse(it.jsonObject) } }
        runCatching { load() }
        emitAll(profile.filterNotNull())
    }

    override suspend fun setNotifications(prefs: NotificationPrefs) {
        profile.value = profile.value?.copy(notifications = prefs)
        runCatching {
            api.patch("$V1/me", buildJsonObject {
                putJsonObject("notifications") {
                    put("device_alerts", prefs.deviceAlerts); put("daily_report", prefs.dailyReport); put("gg_power_news", prefs.ggPowerNews)
                }
            })
        }.onFailure { runCatching { load() } }   // không lưu được: hiện lại giá trị thật trên máy chủ
    }

    override suspend fun setLanguage(language: AppLanguage) {
        runCatching { api.patch("$V1/me", buildJsonObject { put("language", language.tag) }) }
    }

    override suspend fun updateProfile(name: String, avatarUri: String?): SaveProfileResult = try {
        api.patch("$V1/me", buildJsonObject { put("name", name) })
        val before = profile.value?.avatarUri
        if (avatarUri != before) {
            if (avatarUri == null) {
                api.delete("$V1/me/avatar")
            } else {
                val bytes = withContext(Dispatchers.IO) { context.contentResolver.openInputStream(avatarUri.toUri())?.use { it.readBytes() } }
                    ?: return SaveProfileResult.ServerError
                api.upload("PUT", "$V1/me/avatar", bytes)
            }
        }
        load()
        SaveProfileResult.Success
    } catch (_: NoConnectionException) {
        SaveProfileResult.NoConnection
    } catch (_: ApiException) {
        SaveProfileResult.ServerError
    }

    override suspend fun requestDeletion(password: String): DeleteAccountResult = try {
        api.post("$V1/me/deletion", buildJsonObject { put("password", password) })
        api.signOutLocally()
        DeleteAccountResult.Requested
    } catch (e: ApiException) {
        if (e.code == "WRONG_PASSWORD") DeleteAccountResult.WrongPassword else DeleteAccountResult.ServerError
    } catch (_: NoConnectionException) {
        DeleteAccountResult.NoConnection
    }
}

@Singleton
class LiveSupportTicketRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val api: ApiClient,
) : SupportTicketRepository {

    private val list = MutableStateFlow<List<TicketSummary>?>(null)
    private val changed = MutableStateFlow(0)

    private fun summary(o: JsonObject) = TicketSummary(
        id = o.str("id").orEmpty(), code = o.str("code").orEmpty(), title = o.str("title").orEmpty(),
        type = when (o.str("type")) {
            "account" -> TicketType.Account
            "other" -> TicketType.Other
            else -> TicketType.Technical
        },
        projectName = o.str("project_name"),
        createdAt = utcToLocal(o.str("created_at")),
        status = when (o.str("status")) {
            "processing" -> TicketStatus.InProgress
            "resolved" -> TicketStatus.Resolved
            "closed" -> TicketStatus.Closed
            else -> TicketStatus.Received
        },
    )

    private suspend fun <T> call(block: suspend () -> T): T = try {
        block()
    } catch (_: NoConnectionException) {
        throw SupportTicketException(noConnection = true)
    } catch (_: ApiException) {
        throw SupportTicketException(noConnection = false)
    }

    override fun tickets(): Flow<List<TicketSummary>> = flow {
        list.value = call { api.get("$V1/tickets").asObjects().map(::summary) }
        emitAll(list.filterNotNull())
    }

    override suspend fun submit(ticket: NewTicket): TicketSummary = call {
        val created = api.post("$V1/tickets", buildJsonObject {
            put("type", when (ticket.type) {
                TicketType.Technical -> "technical"
                TicketType.Account -> "account"
                TicketType.Other -> "other"
            })
            put("title", ticket.title)
            put("description", ticket.description)
            ticket.projectId?.takeIf { it.isNotEmpty() }?.let { put("project_id", it) }
            ticket.alertId?.takeIf { it.isNotEmpty() }?.let { put("alert_id", it) }
        }).body!!.jsonObject
        val result = summary(created)
        // Tệp đính kèm gửi sau khi có phiếu; một tệp lỗi không làm mất phiếu đã tạo.
        ticket.attachments.forEach { a ->
            runCatching {
                val bytes = withContext(Dispatchers.IO) { context.contentResolver.openInputStream(a.uri.toUri())?.use { it.readBytes() } }
                if (bytes != null) api.upload("POST", "$V1/tickets/${result.id}/attachments", bytes)
            }
        }
        list.value = listOf(result) + list.value.orEmpty()
        result
    }

    private suspend fun loadDetail(ticketId: String): TicketDetail {
        val o = api.get("$V1/tickets/$ticketId").jsonObject
        return TicketDetail(
            summary = summary(o.obj("summary")!!),
            description = o.str("description").orEmpty(),
            photoCount = o.arr("attachments").size,
            messages = o.arr("messages").map {
                TicketMessage(
                    id = it.str("id").orEmpty(), author = if (it.str("author") == "support") MessageAuthor.Support else MessageAuthor.Me,
                    text = it.str("text").orEmpty(), sentAt = utcToLocal(it.str("created_at")),
                )
            },
        )
    }

    /** Tải lại sau mỗi tin gửi đi và mỗi 20 giây khi màn đang mở, để thấy trả lời của bộ phận hỗ trợ. */
    override fun detail(ticketId: String): Flow<TicketDetail> = flow {
        var last = call { loadDetail(ticketId) }
        emit(last)
        var seen = changed.value
        while (true) {
            repeat(20) { if (changed.value == seen) delay(1_000) }
            seen = changed.value
            runCatching { loadDetail(ticketId) }.getOrNull()?.let { if (it != last) { last = it; emit(it) } }
        }
    }

    override suspend fun sendMessage(ticketId: String, text: String): TicketMessage = call {
        val o = api.post("$V1/tickets/$ticketId/messages", buildJsonObject { put("text", text) }).body!!.jsonObject
        changed.value++
        TicketMessage(o.str("id").orEmpty(), MessageAuthor.Me, o.str("text") ?: text, utcToLocal(o.str("created_at")))
    }
}

@Singleton
class LiveWarrantyRepository @Inject constructor(private val api: ApiClient) : WarrantyRepository {

    private fun date(text: String?): LocalDate = runCatching { LocalDate.parse(text) }.getOrDefault(LocalDate.of(1970, 1, 1))

    override suspend fun getWarranty(projectId: String): ProjectWarranty {
        val path = "$V1/projects/$projectId/warranty"
        val o = try {
            api.get(path).jsonObject
        } catch (_: NoConnectionException) {
            api.cached(path)?.jsonObject ?: throw WarrantyLoadException(WarrantyLoadException.Kind.NoConnection)
        } catch (_: ApiException) {
            throw WarrantyLoadException(WarrantyLoadException.Kind.Server)
        }
        return ProjectWarranty(
            projectName = o.str("project_name").orEmpty(),
            devices = o.arr("devices").map { DeviceWarranty(it.str("name").orEmpty(), it.str("serial").orEmpty(), date(it.str("activated_on")), date(it.str("expires_on"))) },
            history = o.arr("history").map {
                val ref = it.str("work_ref").orEmpty()
                WarrantyHistoryEntry(
                    title = it.str("title").orEmpty(), device = it.str("device").orEmpty(),
                    work = if (it.str("work_kind") == "firmware") WarrantyWork.Firmware(ref.substringBefore('>'), ref.substringAfter('>', "")) else WarrantyWork.Repair(ref),
                    date = date(it.str("date")),
                    channel = if (it.str("channel") == "remote") WarrantyChannel.Remote else WarrantyChannel.OnSite,
                    status = WarrantyEntryStatus.Completed,
                )
            },
        )
    }
}

/**
 * Hai nguồn dưới đây được giao diện đọc đồng bộ, nên giữ giá trị mới nhất trong bộ nhớ: nạp bản lưu rồi làm mới
 * ngầm khi app khởi động.
 */
@Singleton
class LiveSupportContactRepository @Inject constructor(private val api: ApiClient) : SupportContactRepository {

    @Volatile private var contact = SupportContact(hotline = "1900 6868", email = "support@ggpower.vn")

    init {
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            val path = "$V1/support/contact"
            suspend fun apply(o: JsonObject?) { o?.let { contact = SupportContact(it.str("hotline") ?: contact.hotline, it.str("email") ?: contact.email) } }
            apply(api.cached(path)?.jsonObject)
            runCatching { apply(api.get(path).jsonObject) }
        }
    }

    override fun getContact(): SupportContact = contact
}

@Singleton
class LiveExploreRepository @Inject constructor(private val api: ApiClient) : ExploreRepository {

    @Volatile private var content = ExploreContent(emptyList(), emptyList(), emptyList())

    private fun icon(name: String?) = when (name) {
        "battery" -> ExploreIcon.Battery
        "smartbox" -> ExploreIcon.SmartBox
        "inverter" -> ExploreIcon.Inverter
        "solar_panel" -> ExploreIcon.SolarPanel
        "accessory" -> ExploreIcon.Accessory
        else -> ExploreIcon.Article
    }

    private fun parse(o: JsonObject) = ExploreContent(
        products = o.arr("products").map { ExploreProduct(it.str("id").orEmpty(), it.str("name").orEmpty(), it.str("description").orEmpty(), icon(it.str("icon"))) },
        articles = o.arr("articles").map {
            ExploreArticle(
                it.str("id").orEmpty(), it.str("title").orEmpty(), it.str("category").orEmpty(), it.int("read_minutes") ?: 0, icon(it.str("icon")),
                featured = it.bool("featured") ?: false, dateLabel = it.str("date_label").orEmpty(), imageDescription = it.str("image_description").orEmpty(),
            )
        },
        videos = o.arr("videos").map {
            ExploreVideo(it.str("id").orEmpty(), it.str("title").orEmpty(), it.str("related_product").orEmpty(), it.int("duration_seconds") ?: 0, icon(it.str("icon")))
        },
    )

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private fun refresh() = scope.launch {
        val path = "$V1/explore"
        api.cached(path)?.let { runCatching { content = parse(it.jsonObject) } }
        runCatching { content = parse(api.get(path).jsonObject) }
    }

    init { refresh() }

    /** Trả nội dung đang có; nếu chưa có gì (ví dụ được tạo trước khi đăng nhập) thì nạp lại cho lần mở sau. */
    override fun getContent(): ExploreContent {
        if (content.products.isEmpty() && content.articles.isEmpty() && content.videos.isEmpty()) refresh()
        return content
    }
}
