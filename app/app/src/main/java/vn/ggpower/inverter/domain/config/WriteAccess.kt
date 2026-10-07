package vn.ggpower.inverter.domain.config

/** Vai trò do backend cấp (đặc tả §16.1; hợp đồng API `Role`): A chủ hộ, B nhà phân phối, C kỹ thuật GGP. */
enum class Role { Owner, Distributor, GgpTechnician }

/** Ba nhóm trong danh sách cho phép của ADR-001 (hợp đồng API `WriteGroup`). */
enum class WriteGroup { OperatingMode, BackupSoc, TouSchedule }

/** `write_access.state` (hợp đồng API). */
enum class WriteState { Enabled, DeviceReadOnly, DisabledByServer, GatewayOffline }

/** Lý do chặn tạm thời một nhóm (`blocked_groups[].reason`). */
enum class BlockReason { BmsNotConnected }

/**
 * Quyền ghi của người đang xem (hợp đồng API `WriteAccess`). [writableModes]: chế độ vai trò này được chọn;
 * app coi đây là quyền theo vai trò, không phụ thuộc [state] (OPEN_QUESTIONS 02/10/2026).
 */
data class WriteAccess(
    val state: WriteState,
    val role: Role,
    val writableGroups: Set<WriteGroup>,
    val blockedGroups: Map<WriteGroup, BlockReason> = emptyMap(),
    val writableModes: Set<OperatingMode> = emptySet(),
)

enum class ControlState { Enabled, Disabled, Hidden }

/** Một thẻ chế độ ở màn 16: [selected] = chế độ đang chạy. */
data class ModeControl(val mode: OperatingMode, val state: ControlState, val selected: Boolean)

/**
 * Trạng thái các control của màn 16 sau khi ánh xạ quyền (ADR-001, ADR-002 mục 10–13).
 * [banner]: dải báo trên đầu màn khi `state` khác `Enabled` (BO_SUNG_v3.md §3), null khi không cần.
 * Các tham số ngoài danh sách cho phép (nhóm cấp B, bảo vệ, DF00, thiết bị) luôn bị vô hiệu ở bản demo.
 */
data class ConfigControls(
    val banner: WriteState?,
    val modes: List<ModeControl>,
    val backupSoc: ControlState,
    val backupSocBlocked: BlockReason?,
    val touSchedule: ControlState,
    val showDistributorSections: Boolean,
    val showTechnicianSections: Boolean,
)

/** Thứ tự thẻ chế độ theo Config.png. */
val ModeCardOrder = listOf(
    OperatingMode.GgAi,
    OperatingMode.SelfConsumption,
    OperatingMode.Tou,
    OperatingMode.MaxGridExport,
    OperatingMode.BatteryPriority,
)

/**
 * Ánh xạ `write_access` và `BuildConfig.CONFIG_READ_ONLY` sang trạng thái control (AGENTS.md §8).
 *
 * - Một nhóm ghi được khi: không chỉ xem, `state = Enabled`, nhóm nằm trong `writable_groups` và không bị chặn.
 * - Thẻ chế độ: GG AI luôn hiện và bị khoá (pha 2). Chế độ khác hiện khi nằm trong `writable_modes` hoặc đang chạy;
 *   không thì ẩn (ADR-002 mục 10: cấp A không thấy "Phát tối đa lên lưới" trừ khi đang chạy). Bật khi nhóm chế độ
 *   ghi được và chế độ nằm trong `writable_modes`.
 * - SOC dự phòng: bị chặn do BMS thì vô hiệu kèm lý do (ADR-002 mục 12).
 * - Mục cấp B hiện với B và C; mục cấp C chỉ với C (§16.1; app ẩn/hiện theo vai trò backend cấp — ADR-001 kiểm soát 3).
 */
fun configControls(access: WriteAccess, currentMode: OperatingMode, configReadOnly: Boolean): ConfigControls {
    val open = !configReadOnly && access.state == WriteState.Enabled
    fun writable(group: WriteGroup) = open && group in access.writableGroups && group !in access.blockedGroups

    val modeWritable = writable(WriteGroup.OperatingMode)
    val modes = ModeCardOrder.mapNotNull { mode ->
        val selected = mode == currentMode
        val state = when {
            mode == OperatingMode.GgAi -> ControlState.Disabled
            mode !in access.writableModes && !selected -> ControlState.Hidden
            modeWritable && mode in access.writableModes -> ControlState.Enabled
            else -> ControlState.Disabled
        }
        if (state == ControlState.Hidden) null else ModeControl(mode, state, selected)
    }
    return ConfigControls(
        banner = access.state.takeIf { it != WriteState.Enabled },
        modes = modes,
        backupSoc = if (writable(WriteGroup.BackupSoc)) ControlState.Enabled else ControlState.Disabled,
        backupSocBlocked = access.blockedGroups[WriteGroup.BackupSoc],
        touSchedule = if (writable(WriteGroup.TouSchedule)) ControlState.Enabled else ControlState.Disabled,
        showDistributorSections = access.role != Role.Owner,
        showTechnicianSections = access.role == Role.GgpTechnician,
    )
}
