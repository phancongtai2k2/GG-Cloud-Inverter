package vn.ggpower.inverter.data.config

import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import vn.ggpower.inverter.domain.config.ChangedValue
import vn.ggpower.inverter.domain.config.CommandStatus
import vn.ggpower.inverter.domain.config.ConfigCommand
import vn.ggpower.inverter.domain.config.ConfigRequest
import vn.ggpower.inverter.domain.config.ConfigWriteException
import vn.ggpower.inverter.domain.config.DeviceState
import vn.ggpower.inverter.domain.config.FailureCode
import vn.ggpower.inverter.domain.config.WriteErrorCode
import vn.ggpower.inverter.domain.config.ConfigChange
import vn.ggpower.inverter.domain.config.DeviceSettings
import vn.ggpower.inverter.domain.config.OperatingMode
import vn.ggpower.inverter.domain.config.ProjectConfig
import vn.ggpower.inverter.domain.config.ProtectionSettings
import vn.ggpower.inverter.domain.config.Role
import vn.ggpower.inverter.domain.config.WriteAccess
import vn.ggpower.inverter.domain.config.WriteGroup
import vn.ggpower.inverter.domain.config.WriteState
import vn.ggpower.inverter.domain.tou.SlotKind
import vn.ggpower.inverter.domain.tou.TouSlot
import vn.ggpower.inverter.domain.tou.TouStrategy
import vn.ggpower.inverter.domain.tou.strategyFor
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Dữ liệu mẫu màn 16 (html/Config.dc.html; chiến lược theo Thay đổi 0.6 §3, hôm nay T5 24/09/2026).
 * Vai trò mặc định cấp C như prototype; đổi [config] để xem vai trò / trạng thái ghi khác.
 */
@Singleton
class FakeConfigRepository @Inject constructor() : ConfigRepository {

    private val _config = MutableStateFlow(SAMPLE)

    /** Cấu hình hiện tại dạng luồng, để bản Fake của Màn hình chính hiện đúng chế độ / SOC vừa ghi ở màn 16. */
    val configFlow: StateFlow<ProjectConfig> = _config.asStateFlow()

    var config: ProjectConfig
        get() = _config.value
        set(value) { _config.value = value }
    var failure: ConfigLoadException.Kind? = null

    /** Kết quả mô phỏng của lệnh ghi tiếp theo (đổi để thử các trạng thái của BO_SUNG_v3.md §2). */
    var scenario: WriteScenario = WriteScenario.Applied

    /** Từ chối lệnh tiếp theo với mã lỗi này (BO_SUNG_v3.md §3); null = nhận lệnh. */
    var rejectWith: WriteErrorCode? = null

    private class Running(val command: ConfigCommand, val request: ConfigRequest, var polls: Int = 0)

    private val commands = mutableMapOf<String, Running>()
    private var nextId = 1
    private var versionCounter = 1

    override suspend fun getConfig(projectId: String): ProjectConfig {
        delay(FAKE_DELAY_MS)
        failure?.let { throw ConfigLoadException(it) }
        return config
    }

    override suspend fun submit(
        projectId: String,
        request: ConfigRequest,
        basedOnVersion: String,
        idempotencyKey: String,
    ): ConfigCommand {
        delay(FAKE_DELAY_MS)
        rejectWith?.let { code ->
            rejectWith = null
            throw ConfigWriteException(code, retryAfterSeconds = if (code == WriteErrorCode.RateLimited) 5 else null)
        }
        if (basedOnVersion != config.version) throw ConfigWriteException(WriteErrorCode.ConfigStale)
        val command = ConfigCommand(
            id = "cmd-${nextId++}", group = request.group, status = CommandStatus.Queued, pollAfterMs = POLL_MS,
        )
        commands[command.id] = Running(command, request)
        return command
    }

    /** Mỗi lần hỏi tiến một bước: đang ghi → đọc lại (sau ~3 s như §16.8) → trạng thái cuối theo [scenario]. */
    override suspend fun command(projectId: String, commandId: String): ConfigCommand {
        delay(FAKE_DELAY_MS / 4)
        val running = commands.getValue(commandId)
        running.polls++
        val base = running.command
        val result = when (running.polls) {
            1 -> base.copy(status = CommandStatus.Writing)
            2 -> base.copy(status = CommandStatus.Verifying)
            else -> finish(base, running.request)
        }
        if (result.status.isFinal) commands.remove(commandId)
        return result
    }

    private fun finish(base: ConfigCommand, request: ConfigRequest): ConfigCommand = when (scenario) {
        WriteScenario.Applied -> {
            val written = request !is ConfigRequest.Tou || config.mode == OperatingMode.Tou
            apply(request)
            base.copy(status = CommandStatus.Applied, pollAfterMs = null, deviceWritten = written,
                deviceState = if (written) DeviceState.New else DeviceState.OldUnchanged)
        }
        WriteScenario.Reverted -> base.copy(status = CommandStatus.Reverted, pollAfterMs = null,
            deviceState = DeviceState.OldRestored, failure = FailureCode.ReadbackMismatch)
        WriteScenario.Failed -> base.copy(status = CommandStatus.Failed, pollAfterMs = null,
            deviceState = DeviceState.OldUnchanged, failure = FailureCode.AckTimeout)
        WriteScenario.ModbusException -> base.copy(status = CommandStatus.Failed, pollAfterMs = null,
            deviceState = DeviceState.OldUnchanged, failure = FailureCode.ModbusException)
        WriteScenario.Unknown -> base.copy(status = CommandStatus.Reverted, pollAfterMs = null,
            deviceState = DeviceState.Unknown, failure = FailureCode.AckTimeout)
    }

    private fun apply(request: ConfigRequest) {
        val c = config
        val change = when (request) {
            is ConfigRequest.Mode -> ChangedValue.Mode(c.mode, request.mode)
            is ConfigRequest.BackupSoc -> ChangedValue.BackupSoc(c.backupSocPercent, request.percent)
            is ConfigRequest.Tou -> null
        }
        config = c.copy(
            version = "sample-${++versionCounter}",
            mode = (request as? ConfigRequest.Mode)?.mode ?: c.mode,
            backupSocPercent = (request as? ConfigRequest.BackupSoc)?.percent ?: c.backupSocPercent,
            strategies = (request as? ConfigRequest.Tou)?.strategies ?: c.strategies,
            todayStrategyId = (request as? ConfigRequest.Tou)?.let { it.strategies.strategyFor(LocalDate.now())?.id } ?: c.todayStrategyId,
            history = listOfNotNull(change?.let { ConfigChange(it, CURRENT_USER, LocalDateTime.now()) }) + c.history,
        )
    }

    enum class WriteScenario { Applied, Reverted, Failed, ModbusException, Unknown }

    companion object {
        private const val FAKE_DELAY_MS = 400L
        private const val POLL_MS = 1_000L
        /** Tên người đang đăng nhập trong dữ liệu mẫu (Config.dc.html "Michael Demo"). */
        private const val CURRENT_USER = "Michael Demo"

        private fun slot(kind: SlotKind, sh: Int, eh: Int, soc: Int, w: Int) =
            TouSlot(kind, LocalTime.of(sh, 0), LocalTime.of(eh, 0), soc, w)

        val WEEKDAYS = TouStrategy(
            id = "6f1c2a4e-0b7d-4d8a-9a51-1c0f3e2b7a01", name = "Ngày thường",
            weekdays = setOf(DayOfWeek.MONDAY, DayOfWeek.TUESDAY, DayOfWeek.WEDNESDAY, DayOfWeek.THURSDAY, DayOfWeek.FRIDAY),
            slots = listOf(
                slot(SlotKind.Charge, 22, 6, 90, 3000),
                slot(SlotKind.Charge, 13, 16, 100, 3000),
                slot(SlotKind.Discharge, 9, 12, 50, 4000),
                slot(SlotKind.Discharge, 18, 21, 20, 5000),
            ),
        )

        val WEEKEND = TouStrategy(
            id = "6f1c2a4e-0b7d-4d8a-9a51-1c0f3e2b7a02", name = "Cuối tuần",
            weekdays = setOf(DayOfWeek.SATURDAY, DayOfWeek.SUNDAY),
            slots = listOf(
                slot(SlotKind.Charge, 0, 6, 90, 3000),
                slot(SlotKind.Charge, 14, 16, 100, 3000),
                slot(SlotKind.Discharge, 10, 12, 60, 4000),
                slot(SlotKind.Discharge, 18, 23, 20, 5000),
            ),
        )

        private val ALL_MODES = setOf(
            OperatingMode.SelfConsumption, OperatingMode.BatteryPriority, OperatingMode.MaxGridExport, OperatingMode.Tou,
        )

        /** Quyền ghi theo vai trò khi mọi thứ bình thường (ADR-001; cấp A không có Phát tối đa — ADR-002 mục 10). */
        fun accessFor(role: Role, state: WriteState = WriteState.Enabled): WriteAccess = WriteAccess(
            state = state,
            role = role,
            writableGroups = if (state == WriteState.Enabled) WriteGroup.entries.toSet() else emptySet(),
            writableModes = if (role == Role.Owner) ALL_MODES - OperatingMode.MaxGridExport else ALL_MODES,
        )

        val SETTINGS = DeviceSettings(
            batteryCapacityKwh = 16.0,
            averageLoadKw = 0.5,
            stopChargeSocPercent = 100,
            lowSocAlarmPercent = 15,
            switchToGridSocPercent = 10,
            maxChargeCurrentA = 120,
            bmsChargeLimitA = 100,
            gridChargeCurrentA = 60,
            chargeLimitMode = 1,
            gridWorkMode = 3,
            zeroExportW = 20,
            maxLinePowerW = 8000,
            outputVoltageV = 220,
            outputFrequencyHz = 50,
            protection = ProtectionSettings(
                autoRestartOverload = true,
                autoRestartOverTemp = true,
                stopOnBmsLoss = false,
                alarmEnabled = true,
                powerSaving = false,
            ),
            bmsComm = 2,
            bmsProtocol = "[MÃ GIAO THỨC]",
            modbusAddress = 1,
            parallelMode = 0,
        )

        val SAMPLE = ProjectConfig(
            version = "sample-1",
            mode = OperatingMode.SelfConsumption,
            backupSocPercent = 20,
            strategies = listOf(WEEKDAYS, WEEKEND),
            todayStrategyId = WEEKDAYS.id,
            writeAccess = accessFor(Role.GgpTechnician),
            settings = SETTINGS,
            history = listOf(
                ConfigChange(ChangedValue.BackupSoc(10, 20), "Michael Demo", LocalDateTime.of(2026, 9, 18, 21, 4)),
                ConfigChange(
                    ChangedValue.Mode(OperatingMode.BatteryPriority, OperatingMode.SelfConsumption),
                    "Đại lý Solar Hưng Yên", LocalDateTime.of(2026, 9, 2, 9, 15),
                ),
            ),
        )
    }
}
