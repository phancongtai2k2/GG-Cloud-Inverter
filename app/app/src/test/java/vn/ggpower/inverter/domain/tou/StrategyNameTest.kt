package vn.ggpower.inverter.domain.tou

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Đổi tên và thêm chiến lược trong bản nháp 16b (ADR-002 mục 5, 9). */
class StrategyNameTest {

    @Test fun `ten hop le 1 den 40 ky tu sau khi bo khoang trang`() {
        assertNull(validateStrategyName("Mùa mưa"))
        assertNull(validateStrategyName("  A  "))
        assertNull(validateStrategyName("x".repeat(40)))
    }

    @Test fun `ten trong hoac qua dai`() {
        assertEquals(StrategyNameError.Empty, validateStrategyName(""))
        assertEquals(StrategyNameError.Empty, validateStrategyName("   "))
        assertEquals(StrategyNameError.TooLong, validateStrategyName("x".repeat(41)))
    }

    @Test fun `chu cai theo vi tri`() {
        assertEquals(listOf('A', 'B', 'C', 'D'), (0..3).map { strategyLetter(it) })
    }

    @Test fun `them chien luoc trong vao cuoi ban nhap`() {
        val draft = SAMPLE.withNewStrategy("c", "Chiến lược C")
        assertEquals(3, draft.size)
        val added = draft.last()
        assertEquals("Chiến lược C", added.name)
        assertTrue(added.weekdays.isEmpty() && added.slots.isEmpty())
        // Chiến lược mới chưa có ngày → chặn Lưu (ADR-002 mục 3).
        assertEquals(listOf(added), saveBlockers(draft))
        assertTrue(!canSave(SAMPLE, draft))
    }
}
