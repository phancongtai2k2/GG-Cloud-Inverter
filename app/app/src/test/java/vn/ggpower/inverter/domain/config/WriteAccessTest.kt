package vn.ggpower.inverter.domain.config

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import vn.ggpower.inverter.domain.config.ControlState.Disabled
import vn.ggpower.inverter.domain.config.ControlState.Enabled
import vn.ggpower.inverter.domain.config.OperatingMode.BatteryPriority
import vn.ggpower.inverter.domain.config.OperatingMode.GgAi
import vn.ggpower.inverter.domain.config.OperatingMode.MaxGridExport
import vn.ggpower.inverter.domain.config.OperatingMode.SelfConsumption
import vn.ggpower.inverter.domain.config.OperatingMode.Tou

/** Ánh xạ `write_access` + `CONFIG_READ_ONLY` sang control màn 16 (AGENTS.md §8; ADR-001; ADR-002 mục 10–13). */
class WriteAccessTest {

    private val allModes = setOf(SelfConsumption, BatteryPriority, MaxGridExport, Tou)

    private fun access(
        role: Role = Role.GgpTechnician,
        state: WriteState = WriteState.Enabled,
        groups: Set<WriteGroup> = WriteGroup.entries.toSet(),
        blocked: Map<WriteGroup, BlockReason> = emptyMap(),
        modes: Set<OperatingMode> = if (role == Role.Owner) allModes - MaxGridExport else allModes,
    ) = WriteAccess(state, role, if (state == WriteState.Enabled) groups else emptySet(), blocked, modes)

    private fun ConfigControls.mode(m: OperatingMode) = modes.firstOrNull { it.mode == m }

    @Test fun `cap C du quyen, moi nhom bat, GG AI van khoa`() {
        val c = configControls(access(), SelfConsumption, configReadOnly = false)
        assertNull(c.banner)
        assertEquals(listOf(GgAi, SelfConsumption, Tou, MaxGridExport, BatteryPriority), c.modes.map { it.mode })
        assertEquals(Disabled, c.mode(GgAi)!!.state)
        assertEquals(Enabled, c.mode(MaxGridExport)!!.state)
        assertTrue(c.mode(SelfConsumption)!!.selected)
        assertEquals(Enabled, c.backupSoc)
        assertEquals(Enabled, c.touSchedule)
        assertTrue(c.showDistributorSections)
        assertTrue(c.showTechnicianSections)
    }

    @Test fun `cap A an the Phat toi da va cac muc cap B, C`() {
        val c = configControls(access(role = Role.Owner), SelfConsumption, configReadOnly = false)
        assertNull(c.mode(MaxGridExport))
        assertEquals(4, c.modes.size)
        assertFalse(c.showDistributorSections)
        assertFalse(c.showTechnicianSections)
    }

    @Test fun `cap A dang chay Phat toi da thi van hien the, dang chon va bi khoa`() {
        val c = configControls(access(role = Role.Owner), MaxGridExport, configReadOnly = false)
        val grid = c.mode(MaxGridExport)!!
        assertTrue(grid.selected)
        assertEquals(Disabled, grid.state)
        assertEquals(Enabled, c.mode(Tou)!!.state)
    }

    @Test fun `cap B thay muc cap B nhung khong thay muc cap C`() {
        val c = configControls(access(role = Role.Distributor), Tou, configReadOnly = false)
        assertTrue(c.showDistributorSections)
        assertFalse(c.showTechnicianSections)
        assertEquals(Enabled, c.mode(MaxGridExport)!!.state)
    }

    @Test fun `CONFIG_READ_ONLY khoa moi control nhung khong an`() {
        val c = configControls(access(), SelfConsumption, configReadOnly = true)
        assertNull(c.banner)
        assertTrue(c.modes.all { it.state == Disabled })
        assertEquals(5, c.modes.size)
        assertEquals(Disabled, c.backupSoc)
        assertEquals(Disabled, c.touSchedule)
    }

    @Test fun `state khac enabled hien dai bao va khoa moi control`() {
        for (state in listOf(WriteState.DeviceReadOnly, WriteState.DisabledByServer, WriteState.GatewayOffline)) {
            val c = configControls(access(role = Role.Distributor, state = state), SelfConsumption, configReadOnly = false)
            assertEquals(state, c.banner)
            assertTrue(c.modes.all { it.state == Disabled })
            assertEquals(Disabled, c.backupSoc)
            assertEquals(Disabled, c.touSchedule)
            // Quyền theo vai trò không đổi: cấp B vẫn thấy thẻ Phát tối đa.
            assertEquals(Disabled, c.mode(MaxGridExport)!!.state)
        }
    }

    @Test fun `nhom ngoai writable_groups bi khoa`() {
        val c = configControls(access(groups = setOf(WriteGroup.TouSchedule)), SelfConsumption, configReadOnly = false)
        assertTrue(c.modes.all { it.state == Disabled })
        assertEquals(Disabled, c.backupSoc)
        assertEquals(Enabled, c.touSchedule)
    }

    @Test fun `thieu BMS khoa SOC du phong kem ly do`() {
        val c = configControls(
            access(blocked = mapOf(WriteGroup.BackupSoc to BlockReason.BmsNotConnected)),
            SelfConsumption, configReadOnly = false,
        )
        assertEquals(Disabled, c.backupSoc)
        assertEquals(BlockReason.BmsNotConnected, c.backupSocBlocked)
        assertEquals(Enabled, c.touSchedule)
    }

    @Test fun `dang o GG AI van chuyen duoc sang che do khac`() {
        val c = configControls(access(), GgAi, configReadOnly = false)
        assertTrue(c.mode(GgAi)!!.selected)
        assertEquals(Enabled, c.mode(SelfConsumption)!!.state)
        assertEquals(Enabled, c.backupSoc)
    }

    @Test fun `che do khong xac dinh thi khong the nao duoc chon`() {
        val c = configControls(access(), OperatingMode.Unknown, configReadOnly = false)
        assertTrue(c.modes.none { it.selected })
    }

    @Test fun `gio du phong uoc tinh theo prototype`() {
        assertEquals(6.4, backupHours(16.0, 20, 0.5)!!, 1e-9)
        assertEquals(32.0, backupHours(16.0, 100, 0.5)!!, 1e-9)
        assertNull(backupHours(16.0, 20, 0.0))
    }
}
