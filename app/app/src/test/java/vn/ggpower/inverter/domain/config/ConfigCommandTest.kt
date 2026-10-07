package vn.ggpower.inverter.domain.config

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Ánh xạ trạng thái lệnh và mã lỗi sang hiển thị (BO_SUNG_v3.md §2–3; ADR-002 mục 15). */
class ConfigCommandTest {

    private fun cmd(
        status: CommandStatus,
        written: Boolean? = null,
        state: DeviceState? = null,
        failure: FailureCode? = null,
    ) = ConfigCommand("c1", WriteGroup.BackupSoc, status, deviceWritten = written, deviceState = state, failure = failure)

    @Test fun `applied`() {
        assertEquals(WriteOutcome.Applied, cmd(CommandStatus.Applied, written = true, state = DeviceState.New).outcome())
        assertEquals(WriteOutcome.Applied, cmd(CommandStatus.Applied).outcome())
    }

    @Test fun `applied nhung thiet bi khong ho tro khung xa`() {
        val written = cmd(CommandStatus.Applied, written = true, state = DeviceState.New).copy(unsupported = listOf("discharge_slots"))
        assertEquals(WriteOutcome.AppliedWithoutDischarge, written.outcome())
        // cả khi lịch mới chỉ lưu ở backend thì vẫn phải báo, không báo "Đã lưu lịch" như bình thường
        val saved = cmd(CommandStatus.Applied, written = false, state = DeviceState.OldUnchanged).copy(unsupported = listOf("discharge_slots"))
        assertEquals(WriteOutcome.AppliedWithoutDischarge, saved.outcome())
        // lệnh không thành công thì báo theo lỗi, không theo phần không hỗ trợ
        val failed = cmd(CommandStatus.Failed, state = DeviceState.OldUnchanged, failure = FailureCode.AckTimeout).copy(unsupported = listOf("discharge_slots"))
        assertEquals(WriteOutcome.Failed, failed.outcome())
    }

    @Test fun `applied nhung chi luu backend`() {
        assertEquals(WriteOutcome.SavedNotWritten, cmd(CommandStatus.Applied, written = false, state = DeviceState.OldUnchanged).outcome())
    }

    @Test fun `hoan ve`() {
        assertEquals(WriteOutcome.Reverted, cmd(CommandStatus.Reverted, state = DeviceState.OldRestored, failure = FailureCode.ReadbackMismatch).outcome())
    }

    @Test fun `that bai chua ghi gi`() {
        assertEquals(WriteOutcome.Failed, cmd(CommandStatus.Failed, state = DeviceState.OldUnchanged, failure = FailureCode.AckTimeout).outcome())
    }

    @Test fun `loi Modbus la mot thong bao chung`() {
        assertEquals(WriteOutcome.DeviceRejected, cmd(CommandStatus.Failed, state = DeviceState.OldUnchanged, failure = FailureCode.ModbusException).outcome())
    }

    @Test fun `device_state unknown uu tien nhat, ca khi failed hay reverted`() {
        assertEquals(WriteOutcome.Unknown, cmd(CommandStatus.Reverted, state = DeviceState.Unknown).outcome())
        assertEquals(WriteOutcome.Unknown, cmd(CommandStatus.Failed, state = DeviceState.Unknown, failure = FailureCode.ModbusException).outcome())
    }

    @Test(expected = IllegalArgumentException::class)
    fun `lenh chua xong khong co ket qua`() {
        cmd(CommandStatus.Verifying).outcome()
    }

    @Test fun `ma loi khoa ghi chuyen sang dai trang thai`() {
        assertEquals(WriteErrorReaction.Lock(WriteState.DeviceReadOnly), WriteErrorCode.DeviceReadOnly.reaction())
        assertEquals(WriteErrorReaction.Lock(WriteState.DisabledByServer), WriteErrorCode.WriteDisabledByServer.reaction())
        assertEquals(WriteErrorReaction.Lock(WriteState.GatewayOffline), WriteErrorCode.GatewayOffline.reaction())
    }

    @Test fun `cac ma loi con lai`() {
        assertEquals(WriteErrorReaction.Block(WriteGroup.BackupSoc, BlockReason.BmsNotConnected), WriteErrorCode.BmsNotConnected.reaction())
        assertEquals(WriteErrorReaction.ReloadAndReconfirm, WriteErrorCode.ConfigStale.reaction())
        assertEquals(WriteErrorReaction.Notify(WriteErrorMessage.NotPermitted), WriteErrorCode.RoleNotPermitted.reaction())
        assertEquals(WriteErrorReaction.Notify(WriteErrorMessage.NotPermitted), WriteErrorCode.GroupNotInAllowlist.reaction())
        assertEquals(WriteErrorReaction.Notify(WriteErrorMessage.CommandInProgress), WriteErrorCode.CommandInProgress.reaction())
        assertEquals(WriteErrorReaction.Notify(WriteErrorMessage.RateLimited), WriteErrorCode.RateLimited.reaction())
        assertEquals(WriteErrorReaction.Notify(WriteErrorMessage.InvalidData), WriteErrorCode.ValidationFailed.reaction())
    }

    @Test fun `quyen ghi sau khi bi tu choi`() {
        val access = WriteAccess(WriteState.Enabled, Role.Owner, WriteGroup.entries.toSet())
        val locked = access.after(WriteErrorReaction.Lock(WriteState.GatewayOffline))
        assertEquals(WriteState.GatewayOffline, locked.state)
        assertTrue(locked.writableGroups.isEmpty())

        val bms = access.after(WriteErrorReaction.Block(WriteGroup.BackupSoc, BlockReason.BmsNotConnected))
        assertEquals(setOf(WriteGroup.OperatingMode, WriteGroup.TouSchedule), bms.writableGroups)
        assertEquals(BlockReason.BmsNotConnected, bms.blockedGroups[WriteGroup.BackupSoc])
    }
}
