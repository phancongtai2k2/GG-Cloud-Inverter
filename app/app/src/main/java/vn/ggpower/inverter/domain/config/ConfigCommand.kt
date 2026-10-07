package vn.ggpower.inverter.domain.config

import vn.ggpower.inverter.domain.tou.TouStrategy

/** Một yêu cầu ghi thuộc đúng một nhóm (hợp đồng API `ConfigCommandRequest`). */
sealed interface ConfigRequest {
    val group: WriteGroup

    data class Mode(val mode: OperatingMode) : ConfigRequest {
        override val group get() = WriteGroup.OperatingMode
    }

    data class BackupSoc(val percent: Int) : ConfigRequest {
        override val group get() = WriteGroup.BackupSoc
    }

    data class Tou(val strategies: List<TouStrategy>) : ConfigRequest {
        override val group get() = WriteGroup.TouSchedule
    }
}

/** `CommandStatus`: bốn trạng thái đang chạy và ba trạng thái cuối. */
enum class CommandStatus(val isFinal: Boolean) {
    Queued(false), Writing(false), Verifying(false), Reverting(false),
    Applied(true), Reverted(true), Failed(true),
}

/** `outcome.device_state`: giá trị inverter đang chạy sau lệnh. */
enum class DeviceState { New, OldRestored, OldUnchanged, Unknown }

/** `failure.code`. */
enum class FailureCode { ReadbackMismatch, AckTimeout, GatewayOffline, GatewayError, ModbusException }

/** Lệnh ghi (hợp đồng API `ConfigCommand`), chỉ các trường app dùng. */
data class ConfigCommand(
    val id: String,
    val group: WriteGroup,
    val status: CommandStatus,
    val pollAfterMs: Long? = null,
    val deviceWritten: Boolean? = null,
    val deviceState: DeviceState? = null,
    val failure: FailureCode? = null,
    /** Phần của yêu cầu mà thiết bị không thực hiện được (hiện chỉ có "discharge_slots": datalogger chưa hỗ trợ khung xả). */
    val unsupported: List<String> = emptyList(),
)

/** Mã lỗi khi gửi lệnh (HTTP 4xx, không tạo lệnh) — hợp đồng API `Error.code`. */
enum class WriteErrorCode {
    Unauthenticated, RoleNotPermitted, GroupNotInAllowlist, DeviceNotFound, DeviceReadOnly, WriteDisabledByServer,
    GatewayOffline, BmsNotConnected, ValidationFailed, ConfigStale, CommandInProgress, IdempotencyKeyReused, RateLimited,
}

class ConfigWriteException(val code: WriteErrorCode, val retryAfterSeconds: Int? = null) : Exception(code.name)

/** Kết quả hiển thị của một lệnh ở trạng thái cuối (BO_SUNG_v3.md §2). */
enum class WriteOutcome {
    /** "Đã áp dụng." */
    Applied,
    /** Đã lưu / áp dụng, nhưng thiết bị không chạy được khung xả: phải báo cho người dùng, không im lặng. */
    AppliedWithoutDischarge,
    /** Lịch được lưu nhưng chưa nạp xuống thiết bị: "Đã lưu lịch. Lịch sẽ chạy khi…". */
    SavedNotWritten,
    /** "Thiết bị không nhận giá trị mới, đã giữ giá trị cũ." */
    Reverted,
    /** "Chưa thay đổi được. Thử lại sau." + nút Thử lại. */
    Failed,
    /** "Thiết bị từ chối thay đổi, thử lại sau." */
    DeviceRejected,
    /** Không chắc thiết bị đang chạy giá trị nào: đọc lại cấu hình và báo bằng dialog. */
    Unknown,
}

/**
 * Ánh xạ lệnh ở trạng thái cuối sang kết quả hiển thị (BO_SUNG_v3.md §2). `device_state = unknown` ưu tiên nhất
 * vì người dùng phải kiểm tra lại; tiếp theo là lỗi Modbus (một thông báo chung, ADR-002 mục 15).
 */
fun ConfigCommand.outcome(): WriteOutcome {
    require(status.isFinal) { "Lệnh chưa ở trạng thái cuối: $status" }
    return when {
        status == CommandStatus.Applied && "discharge_slots" in unsupported -> WriteOutcome.AppliedWithoutDischarge
        status == CommandStatus.Applied -> if (deviceWritten == false) WriteOutcome.SavedNotWritten else WriteOutcome.Applied
        deviceState == DeviceState.Unknown -> WriteOutcome.Unknown
        failure == FailureCode.ModbusException -> WriteOutcome.DeviceRejected
        status == CommandStatus.Reverted -> WriteOutcome.Reverted
        else -> WriteOutcome.Failed
    }
}

/** Cách app phản ứng với một mã lỗi khi gửi lệnh (BO_SUNG_v3.md §3). */
sealed interface WriteErrorReaction {
    /** Khoá ghi cả màn và hiện dải trạng thái. */
    data class Lock(val state: WriteState) : WriteErrorReaction
    /** Chặn riêng một nhóm (thiếu BMS). */
    data class Block(val group: WriteGroup, val reason: BlockReason) : WriteErrorReaction
    /** Đọc lại cấu hình rồi mở lại hộp xác nhận với giá trị cũ mới đọc. */
    data object ReloadAndReconfirm : WriteErrorReaction
    /** Chỉ báo bằng snackbar. */
    data class Notify(val message: WriteErrorMessage) : WriteErrorReaction
}

enum class WriteErrorMessage { NotPermitted, CommandInProgress, RateLimited, InvalidData, Generic }

fun WriteErrorCode.reaction(): WriteErrorReaction = when (this) {
    WriteErrorCode.DeviceReadOnly -> WriteErrorReaction.Lock(WriteState.DeviceReadOnly)
    WriteErrorCode.WriteDisabledByServer -> WriteErrorReaction.Lock(WriteState.DisabledByServer)
    WriteErrorCode.GatewayOffline -> WriteErrorReaction.Lock(WriteState.GatewayOffline)
    WriteErrorCode.BmsNotConnected -> WriteErrorReaction.Block(WriteGroup.BackupSoc, BlockReason.BmsNotConnected)
    WriteErrorCode.ConfigStale -> WriteErrorReaction.ReloadAndReconfirm
    WriteErrorCode.RoleNotPermitted, WriteErrorCode.GroupNotInAllowlist -> WriteErrorReaction.Notify(WriteErrorMessage.NotPermitted)
    WriteErrorCode.CommandInProgress -> WriteErrorReaction.Notify(WriteErrorMessage.CommandInProgress)
    WriteErrorCode.RateLimited -> WriteErrorReaction.Notify(WriteErrorMessage.RateLimited)
    WriteErrorCode.ValidationFailed -> WriteErrorReaction.Notify(WriteErrorMessage.InvalidData)
    WriteErrorCode.Unauthenticated, WriteErrorCode.DeviceNotFound, WriteErrorCode.IdempotencyKeyReused ->
        WriteErrorReaction.Notify(WriteErrorMessage.Generic)
}

/** Quyền ghi sau khi backend từ chối với [reaction] (trước khi đọc lại cấu hình). */
fun WriteAccess.after(reaction: WriteErrorReaction): WriteAccess = when (reaction) {
    is WriteErrorReaction.Lock -> copy(state = reaction.state, writableGroups = emptySet())
    is WriteErrorReaction.Block -> copy(
        writableGroups = writableGroups - reaction.group,
        blockedGroups = blockedGroups + (reaction.group to reaction.reason),
    )
    else -> this
}
