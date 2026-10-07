package vn.ggpower.inverter.ui.tou

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import vn.ggpower.inverter.data.config.ConfigLoadException
import vn.ggpower.inverter.data.config.FakeConfigRepository
import vn.ggpower.inverter.domain.config.OperatingMode
import vn.ggpower.inverter.domain.config.Role
import vn.ggpower.inverter.domain.config.WriteErrorCode
import vn.ggpower.inverter.domain.config.WriteState
import vn.ggpower.inverter.domain.tou.SlotError
import vn.ggpower.inverter.domain.tou.SlotKind
import vn.ggpower.inverter.domain.tou.StrategyNameError
import vn.ggpower.inverter.domain.tou.TouChange
import vn.ggpower.inverter.navigation.NEW_STRATEGY_ID
import vn.ggpower.inverter.ui.config.NoticeKind
import java.time.DayOfWeek.FRIDAY
import java.time.DayOfWeek.SATURDAY

@OptIn(ExperimentalCoroutinesApi::class)
/** Bản nháp màn 16b (ADR-002 mục 2–5, 9; BO_SUNG_v3.md §4). */
class TouStrategyControllerTest {

    private val repo = FakeConfigRepository()
    private val weekdaysId = FakeConfigRepository.WEEKDAYS.id

    private suspend fun TestScope.controller(id: String = weekdaysId, readOnly: Boolean = false) =
        TouStrategyController("p1", id, repo, readOnly, newName = { "Chiến lược $it" }, scope = this, newId = { "new-1" }).also { it.load() }

    private val TouStrategyController.content get() = state.value as TouStrategyUiState.Content

    @Test fun `mo chien luoc da luu, chua co thay doi, Luu bi khoa`() = runTest {
        val c = controller()
        assertEquals("Ngày thường", c.content.strategy.name)
        assertEquals(0, c.content.index)
        assertFalse(c.content.hasChanges)
        assertFalse(c.content.saveEnabled)
        assertEquals(listOf(SATURDAY, java.time.DayOfWeek.SUNDAY), c.content.daysOfOthers.values.single())
    }

    @Test fun `chon thu cua chien luoc khac thi chuyen sang, chi trong ban nhap`() = runTest {
        val c = controller()
        c.onToggleDay(SATURDAY)
        assertTrue(SATURDAY in c.content.strategy.weekdays)
        assertFalse(SATURDAY in c.content.draft[1].weekdays)
        assertTrue(SATURDAY in c.content.saved[1].weekdays)
        assertTrue(c.content.saveEnabled)
        assertEquals(2, c.content.changes.count { it is TouChange.DaysChanged })
    }

    @Test fun `bo het ngay thi chan Luu`() = runTest {
        val c = controller()
        c.content.strategy.weekdays.forEach { c.onToggleDay(it) }
        assertTrue(c.content.missingDays)
        assertTrue(c.content.hasChanges)
        assertFalse(c.content.saveEnabled)
    }

    @Test fun `doi ten, trong thi bao loi, huy khong doi`() = runTest {
        val c = controller()
        c.onRenameOpen()
        assertEquals(RenameState("Ngày thường", null), c.content.rename)
        c.onRenameChange("")
        assertEquals(StrategyNameError.Empty, c.content.rename?.error)
        c.onRenameSave()
        assertEquals("Ngày thường", c.content.strategy.name)
        c.onRenameChange("  Mùa mưa ")
        c.onRenameSave()
        assertNull(c.content.rename)
        assertEquals("Mùa mưa", c.content.strategy.name)
        assertEquals(TouChange.Renamed("Mùa mưa", "Ngày thường"), c.content.changes.single())
        c.onRenameOpen(); c.onRenameChange("X"); c.onRenameCancel()
        assertEquals("Mùa mưa", c.content.strategy.name)
    }

    @Test fun `o nhap ten gioi han 40 ky tu`() = runTest {
        val c = controller()
        c.onRenameOpen()
        c.onRenameChange("x".repeat(50))
        assertEquals(40, c.content.rename?.text?.length)
    }

    @Test fun `them chien luoc moi chi co trong ban nhap, chua co ngay thi chan Luu`() = runTest {
        val c = controller(NEW_STRATEGY_ID)
        assertEquals(3, c.content.draft.size)
        assertEquals(2, c.content.saved.size)
        assertEquals("Chiến lược C", c.content.strategy.name)
        assertEquals(2, c.content.index)
        assertTrue(c.content.hasChanges)
        assertFalse(c.content.saveEnabled)
        c.onToggleDay(FRIDAY)
        assertTrue(c.content.saveEnabled)
    }

    @Test fun `roi man khi con thay doi thi hoi`() = runTest {
        val c = controller()
        assertTrue(c.onBackRequest())
        c.onToggleDay(SATURDAY)
        assertFalse(c.onBackRequest())
        assertTrue(c.content.showDiscard)
        c.onDiscardCancel()
        assertFalse(c.content.showDiscard)
    }

    @Test fun `khong co quyen ghi thi chi xem`() = runTest {
        repo.config = repo.config.copy(writeAccess = FakeConfigRepository.accessFor(Role.Owner, WriteState.DeviceReadOnly))
        val c = controller()
        assertFalse(c.content.editable)
        assertFalse(c.content.canDelete)
        c.onToggleDay(SATURDAY)
        assertFalse(c.content.hasChanges)
        assertTrue(controller(readOnly = true).let { !it.content.editable })
    }

    @Test fun `chien luoc khong ton tai hoac loi tai`() = runTest {
        assertEquals(TouStrategyUiState.Error(noConnection = false), controller("khong-co").state.value)
        repo.failure = ConfigLoadException.Kind.NoConnection
        assertEquals(TouStrategyUiState.Error(noConnection = true), controller().state.value)
    }

    // ---------- Popup khung (Thay đổi 0.6 §5) ----------

    @Test fun `them khung, mac dinh sac, gio goi y khong trung, luu vao ban nhap`() = runTest {
        val c = controller()
        c.onAddSlot()
        val sheet = c.content.slotSheet!!
        assertNull(sheet.editingIndex)
        assertEquals(SlotKind.Charge, sheet.input.kind)
        // 10:00–14:00 trùng Xả 1 (09–12) và Sạc 2 (13–16) → khoảng trống đầu tiên đủ 4 giờ: không có → thử 1 giờ.
        assertNull(sheet.error)
        c.onSlotSave()
        assertNull(c.content.slotSheet)
        assertEquals(3, c.content.strategy.slotsOf(SlotKind.Charge).size)
        assertTrue(c.content.hasChanges)
        assertTrue(c.content.changes.single() is TouChange.SlotAdded)
    }

    @Test fun `doi loai khung thi dien lai goi y cua loai do`() = runTest {
        val c = controller()
        c.onAddSlot()
        c.onSlotKindChange(SlotKind.Discharge)
        assertEquals(SlotKind.Discharge, c.content.slotSheet!!.input.kind)
        assertEquals("40", c.content.slotSheet!!.input.stopSoc)
    }

    @Test fun `sua khung, loi trung, khoa Luu, sua lai thi het loi`() = runTest {
        val c = controller()
        val idx = c.content.strategy.indexOf(SlotKind.Charge, 2) // Sạc 2 13:00–16:00
        c.onOpenSlot(idx)
        assertEquals("13:00", c.content.slotSheet!!.input.start)
        c.onSlotFieldChange(SlotField.Start, "1000")
        assertEquals("10:00", c.content.slotSheet!!.input.start)
        val err = c.content.slotSheet!!.error as SlotError.Overlap
        assertEquals(SlotKind.Discharge, err.kind)
        assertEquals(1, err.number)
        c.onSlotSave()
        assertTrue(c.content.slotSheet != null)
        c.onSlotFieldChange(SlotField.Start, "1230")
        assertNull(c.content.slotSheet!!.error)
        c.onSlotSave()
        assertEquals("12:30", vn.ggpower.inverter.domain.format.GgFormat.time(c.content.strategy.slots[idx].start))
    }

    @Test fun `loi theo thu tu, SOC, cong suat`() = runTest {
        val c = controller()
        c.onOpenSlot(0)
        c.onSlotFieldChange(SlotField.Soc, "101")
        assertEquals(SlotError.SocRange, c.content.slotSheet!!.error)
        c.onSlotFieldChange(SlotField.Soc, "90")
        c.onSlotFieldChange(SlotField.Power, "13.000")
        assertEquals(SlotError.PowerRange, c.content.slotSheet!!.error)
    }

    @Test fun `xoa khung, cac khung cung loai don len, danh so lai`() = runTest {
        val c = controller()
        c.onOpenSlot(c.content.strategy.indexOf(SlotKind.Charge, 1))
        c.onSlotDelete()
        val charges = c.content.strategy.slotsOf(SlotKind.Charge)
        assertEquals(1, charges.size)
        assertEquals(13, charges.single().start.hour) // Sạc 2 cũ thành Sạc 1
        assertTrue(c.content.changes.isNotEmpty())
    }

    @Test fun `huy popup khong doi ban nhap`() = runTest {
        val c = controller()
        c.onOpenSlot(0)
        c.onSlotFieldChange(SlotField.Start, "2300")
        c.onSlotCancel()
        assertNull(c.content.slotSheet)
        assertFalse(c.content.hasChanges)
    }
    // ---------- Lưu lịch (ADR-002 mục 2, 8, 14, 15) ----------

    @Test fun `luu khi che do khac TOU thi chi luu backend, ban nhap thanh ban da luu`() = runTest {
        val c = controller()
        c.onToggleDay(SATURDAY)
        c.onSave()
        assertEquals(TouConfirm(c.content.draft), c.content.confirm)
        c.onConfirm()
        assertNull(c.content.confirm)
        assertTrue(c.content.writing)
        assertFalse(c.content.saveEnabled)
        assertFalse(c.onBackRequest())
        assertFalse(c.content.showDiscard)
        advanceUntilIdle()
        assertFalse(c.content.writing)
        assertEquals(NoticeKind.SavedNotWritten, c.content.notice?.kind)
        assertTrue(SATURDAY in c.content.saved[0].weekdays)
        assertFalse(c.content.hasChanges)
        assertEquals(c.content.saved, repo.config.strategies)
    }

    @Test fun `luu khi dang o TOU thi da ap dung`() = runTest {
        repo.config = repo.config.copy(mode = OperatingMode.Tou)
        val c = controller()
        c.onRenameOpen(); c.onRenameChange("Mùa mưa"); c.onRenameSave()
        c.onSave(); c.onConfirm(); advanceUntilIdle()
        assertEquals(NoticeKind.Applied, c.content.notice?.kind)
        assertEquals("Mùa mưa", repo.config.strategies[0].name)
    }

    @Test fun `chien luoc moi luu xong van o man voi cung id`() = runTest {
        val c = controller(NEW_STRATEGY_ID)
        c.onToggleDay(SATURDAY)
        c.onSave(); c.onConfirm(); advanceUntilIdle()
        assertEquals(3, repo.config.strategies.size)
        assertEquals("new-1", c.content.strategyId)
        assertFalse(c.content.hasChanges)
        assertFalse(SATURDAY in repo.config.strategies[1].weekdays)
    }

    @Test fun `xoa chien luoc da luu, xac nhan xong thi ve 16a`() = runTest {
        val c = controller()
        assertFalse(c.onDeleteStrategy())
        val confirm = c.content.confirm!!
        assertTrue(confirm.deleting)
        assertEquals(listOf("Cuối tuần"), confirm.draft.map { it.name })
        c.onCancelConfirm()
        assertEquals(2, c.content.draft.size)
        c.onDeleteStrategy(); c.onConfirm(); advanceUntilIdle()
        assertTrue(c.content.finished)
        assertEquals(listOf("Cuối tuần"), repo.config.strategies.map { it.name })
    }

    @Test fun `xoa chien luoc moi chua luu chi bo ban nhap`() = runTest {
        val c = controller(NEW_STRATEGY_ID)
        assertFalse(c.onDeleteStrategy())
        assertTrue(c.content.showDiscard)
        assertNull(c.content.confirm)
    }

    @Test fun `hoan ve thi giu ban nhap`() = runTest {
        repo.scenario = FakeConfigRepository.WriteScenario.Reverted
        val c = controller()
        c.onToggleDay(SATURDAY); c.onSave(); c.onConfirm(); advanceUntilIdle()
        assertEquals(NoticeKind.Reverted, c.content.notice?.kind)
        assertTrue(c.content.hasChanges)
        assertFalse(SATURDAY in repo.config.strategies[0].weekdays)
    }

    @Test fun `that bai thi Thu lai gui lai`() = runTest {
        repo.scenario = FakeConfigRepository.WriteScenario.Failed
        val c = controller()
        c.onToggleDay(SATURDAY); c.onSave(); c.onConfirm(); advanceUntilIdle()
        assertEquals(NoticeKind.Failed, c.content.notice?.kind)
        repo.scenario = FakeConfigRepository.WriteScenario.Applied
        c.onNoticeAction(); advanceUntilIdle()
        assertTrue(SATURDAY in repo.config.strategies[0].weekdays)
        assertFalse(c.content.hasChanges)
    }

    @Test fun `khong ro ket qua thi doc lai va mo dialog`() = runTest {
        repo.scenario = FakeConfigRepository.WriteScenario.Unknown
        val c = controller()
        c.onToggleDay(SATURDAY); c.onSave(); c.onConfirm(); advanceUntilIdle()
        assertTrue(c.content.showUnknownDialog)
        assertFalse(c.content.hasChanges)
        c.onUnknownDismissed()
        assertFalse(c.content.showUnknownDialog)
    }

    @Test fun `bi khoa ghi thi hien dai va chuyen sang chi xem`() = runTest {
        repo.rejectWith = WriteErrorCode.GatewayOffline
        val c = controller()
        c.onToggleDay(SATURDAY); c.onSave(); c.onConfirm(); advanceUntilIdle()
        assertEquals(WriteState.GatewayOffline, c.content.banner)
        assertFalse(c.content.editable)
        assertTrue(c.content.hasChanges)
    }

    @Test fun `cau hinh vua doi o noi khac thi hoi lai, giu ban nhap`() = runTest {
        val c = controller()
        c.onToggleDay(SATURDAY); c.onSave()
        repo.config = repo.config.copy(version = "lcd-1")
        c.onConfirm(); advanceUntilIdle()
        assertTrue(c.content.confirm!!.stale)
        assertEquals("lcd-1", c.content.version)
        assertTrue(SATURDAY in c.content.strategy.weekdays)
        c.onConfirm(); advanceUntilIdle()
        assertTrue(SATURDAY in repo.config.strategies[0].weekdays)
    }

    @Test fun `thao tac qua nhanh`() = runTest {
        repo.rejectWith = WriteErrorCode.RateLimited
        val c = controller()
        c.onToggleDay(SATURDAY); c.onSave(); c.onConfirm(); advanceUntilIdle()
        assertEquals(NoticeKind.RateLimited, c.content.notice?.kind)
        assertEquals(5, c.content.notice?.retryAfterSeconds)
        assertTrue(c.content.hasChanges)
    }
}
