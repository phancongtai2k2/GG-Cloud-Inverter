package vn.ggpower.inverter.ui.config

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import vn.ggpower.inverter.data.config.ConfigLoadException
import vn.ggpower.inverter.data.config.FakeConfigRepository
import vn.ggpower.inverter.data.config.FakeConfigRepository.WriteScenario
import vn.ggpower.inverter.domain.config.BlockReason
import vn.ggpower.inverter.domain.config.ChangedValue
import vn.ggpower.inverter.domain.config.ConfigRequest
import vn.ggpower.inverter.domain.config.ControlState
import vn.ggpower.inverter.domain.config.OperatingMode
import vn.ggpower.inverter.domain.config.WriteErrorCode
import vn.ggpower.inverter.domain.config.WriteGroup
import vn.ggpower.inverter.domain.config.WriteState

/** Luồng ghi màn 16 với [FakeConfigRepository] (AGENTS.md §7; BO_SUNG_v3.md §1–3). */
@OptIn(ExperimentalCoroutinesApi::class)
class ConfigControllerTest {

    private val repo = FakeConfigRepository()

    private fun TestScope.controller(readOnly: Boolean = false): ConfigController {
        var key = 0
        return ConfigController("p1", repo, readOnly, this) { "key-${++key}" }.also {
            it.load()
            advanceUntilIdle()
        }
    }

    private val ConfigController.content get() = state.value as ConfigUiState.Content

    @Test fun `tai xong thi co noi dung va control da anh xa`() = runTest {
        val c = controller()
        assertEquals("Ngày thường", c.content.config.todayStrategy?.name)
        assertEquals(ControlState.Enabled, c.content.controls.backupSoc)
        assertEquals(20, c.content.backupSocDraft)
    }

    @Test fun `CONFIG_READ_ONLY khoa thao tac`() = runTest {
        val c = controller(readOnly = true)
        assertEquals(ControlState.Disabled, c.content.controls.backupSoc)
        c.onSelectMode(OperatingMode.BatteryPriority)
        assertNull(c.content.confirm)
    }

    @Test fun `loi tai cau hinh`() = runTest {
        repo.failure = ConfigLoadException.Kind.NoConnection
        val c = controller()
        assertEquals(ConfigUiState.Error(noConnection = true), c.state.value)
        repo.failure = null
        c.load(); advanceUntilIdle()
        assertTrue(c.state.value is ConfigUiState.Content)
    }

    @Test fun `chon che do mo hop xac nhan, xac nhan thi chi doi khi applied`() = runTest {
        val c = controller()
        c.onSelectMode(OperatingMode.BatteryPriority)
        assertEquals(ConfirmState(ConfigRequest.Mode(OperatingMode.BatteryPriority)), c.content.confirm)

        c.onConfirm()
        assertNull(c.content.confirm)
        assertEquals(WriteGroup.OperatingMode, c.content.writing)
        assertTrue(c.content.controls.modes.all { it.state == ControlState.Disabled })
        runCurrent()
        // Đang ghi: vẫn hiện giá trị cũ.
        assertEquals(OperatingMode.SelfConsumption, c.content.config.mode)

        advanceUntilIdle()
        assertEquals(OperatingMode.BatteryPriority, c.content.config.mode)
        assertNull(c.content.writing)
        assertEquals(NoticeKind.Applied, c.content.notice?.kind)
        assertEquals(ChangedValue.Mode(OperatingMode.SelfConsumption, OperatingMode.BatteryPriority), c.content.config.history.first().value)
        assertEquals(ControlState.Enabled, c.content.controls.backupSoc)
    }

    @Test fun `khong mo xac nhan cho the dang chon hoac bi khoa`() = runTest {
        val c = controller()
        c.onSelectMode(OperatingMode.SelfConsumption)
        assertNull(c.content.confirm)
        c.onSelectMode(OperatingMode.GgAi)
        assertNull(c.content.confirm)
    }

    @Test fun `keo SOC, tha tay thi xac nhan, huy thi ve gia tri cu`() = runTest {
        val c = controller()
        c.onBackupSocChange(25)
        c.onBackupSocChange(30)
        assertEquals(30, c.content.backupSocDraft)
        assertNull(c.content.confirm)
        c.onBackupSocChangeFinished()
        assertEquals(ConfigRequest.BackupSoc(30), c.content.confirm?.request)
        c.onCancelConfirm()
        assertNull(c.content.confirm)
        assertEquals(20, c.content.backupSocDraft)
    }

    @Test fun `tha tay o dung gia tri cu thi khong hoi`() = runTest {
        val c = controller()
        c.onBackupSocChange(20)
        c.onBackupSocChangeFinished()
        assertNull(c.content.confirm)
    }

    @Test fun `ghi SOC thanh cong`() = runTest {
        val c = controller()
        c.onBackupSocChange(30); c.onBackupSocChangeFinished(); c.onConfirm()
        assertEquals(WriteGroup.BackupSoc, c.content.writing)
        assertEquals(30, c.content.backupSocDraft)
        advanceUntilIdle()
        assertEquals(30, c.content.config.backupSocPercent)
        assertEquals(30, c.content.backupSocDraft)
    }

    @Test fun `hoan ve thi giu gia tri cu`() = runTest {
        repo.scenario = WriteScenario.Reverted
        val c = controller()
        c.onBackupSocChange(30); c.onBackupSocChangeFinished(); c.onConfirm()
        advanceUntilIdle()
        assertEquals(20, c.content.config.backupSocPercent)
        assertEquals(20, c.content.backupSocDraft)
        assertEquals(NoticeKind.Reverted, c.content.notice?.kind)
    }

    @Test fun `that bai co nut Thu lai gui lai lenh`() = runTest {
        repo.scenario = WriteScenario.Failed
        val c = controller()
        c.onSelectMode(OperatingMode.Tou); c.onConfirm()
        advanceUntilIdle()
        assertEquals(NoticeKind.Failed, c.content.notice?.kind)
        assertEquals(OperatingMode.SelfConsumption, c.content.config.mode)

        repo.scenario = WriteScenario.Applied
        c.onNoticeAction()
        assertNull(c.content.notice)
        advanceUntilIdle()
        assertEquals(OperatingMode.Tou, c.content.config.mode)
        assertEquals(NoticeKind.Applied, c.content.notice?.kind)
    }

    @Test fun `loi Modbus`() = runTest {
        repo.scenario = WriteScenario.ModbusException
        val c = controller()
        c.onSelectMode(OperatingMode.Tou); c.onConfirm(); advanceUntilIdle()
        assertEquals(NoticeKind.DeviceRejected, c.content.notice?.kind)
    }

    @Test fun `khong ro ket qua thi doc lai va mo dialog`() = runTest {
        repo.scenario = WriteScenario.Unknown
        val c = controller()
        c.onSelectMode(OperatingMode.Tou); c.onConfirm(); advanceUntilIdle()
        assertTrue(c.content.showUnknownDialog)
        assertNull(c.content.writing)
        c.onUnknownDismissed()
        assertFalse(c.content.showUnknownDialog)
    }

    @Test fun `thiet bi chi xem thi hien dai va khoa ghi`() = runTest {
        repo.rejectWith = WriteErrorCode.DeviceReadOnly
        val c = controller()
        c.onSelectMode(OperatingMode.Tou); c.onConfirm(); advanceUntilIdle()
        assertEquals(WriteState.DeviceReadOnly, c.content.controls.banner)
        assertTrue(c.content.controls.modes.all { it.state == ControlState.Disabled })
        assertNull(c.content.notice)
    }

    @Test fun `thieu BMS thi khoa rieng SOC du phong`() = runTest {
        repo.rejectWith = WriteErrorCode.BmsNotConnected
        val c = controller()
        c.onBackupSocChange(30); c.onBackupSocChangeFinished(); c.onConfirm(); advanceUntilIdle()
        assertEquals(ControlState.Disabled, c.content.controls.backupSoc)
        assertEquals(BlockReason.BmsNotConnected, c.content.controls.backupSocBlocked)
        assertEquals(20, c.content.backupSocDraft)
        assertTrue(c.content.controls.modes.any { it.state == ControlState.Enabled })
    }

    @Test fun `cau hinh vua doi o noi khac thi doc lai va hoi lai`() = runTest {
        val c = controller()
        c.onBackupSocChange(30); c.onBackupSocChangeFinished()
        // Có người đổi ở màn LCD trong lúc hộp xác nhận đang mở.
        repo.config = repo.config.copy(version = "lcd-1", backupSocPercent = 25)
        c.onConfirm(); advanceUntilIdle()
        assertEquals(ConfirmState(ConfigRequest.BackupSoc(30), stale = true), c.content.confirm)
        assertEquals(25, c.content.config.backupSocPercent)

        c.onConfirm(); advanceUntilIdle()
        assertEquals(30, c.content.config.backupSocPercent)
    }

    @Test fun `qua nhanh thi bao so giay`() = runTest {
        repo.rejectWith = WriteErrorCode.RateLimited
        val c = controller()
        c.onSelectMode(OperatingMode.Tou); c.onConfirm(); advanceUntilIdle()
        assertEquals(NoticeKind.RateLimited, c.content.notice?.kind)
        assertEquals(5, c.content.notice?.retryAfterSeconds)
        c.onNoticeDismissed(c.content.notice!!.id)
        assertNull(c.content.notice)
    }
}
