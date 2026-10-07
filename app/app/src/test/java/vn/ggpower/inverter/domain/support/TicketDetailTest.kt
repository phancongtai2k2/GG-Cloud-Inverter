package vn.ggpower.inverter.domain.support

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDateTime

class TicketDetailTest {
    private val created = LocalDateTime.of(2026, 9, 26, 7, 40)

    private fun detail(type: TicketType = TicketType.Technical, status: TicketStatus = TicketStatus.Received, photos: Int = 2) =
        TicketDetail(
            TicketSummary("t1", "#HT-2609-0142", "Inverter báo lỗi mất lưới", type, "Nhà của bạn", created, status),
            description = "Mô tả", photoCount = photos, messages = emptyList(),
        )

    @Test fun `dong dinh kem du hai ve`() {
        assertEquals(AttachmentLine.PhotosAndSystem(2), detail().attachmentLine)
    }

    @Test fun `dong dinh kem khi thieu mot ve`() {
        assertEquals(AttachmentLine.SystemOnly, detail(photos = 0).attachmentLine)
        assertEquals(AttachmentLine.PhotosOnly(1), detail(type = TicketType.Account, photos = 1).attachmentLine)
        assertEquals(AttachmentLine.None, detail(type = TicketType.Other, photos = 0).attachmentLine)
    }

    @Test fun `chi phieu Da dong khong nhan tin`() {
        assertFalse(detail(status = TicketStatus.Closed).acceptsReplies)
        assertTrue(detail(status = TicketStatus.Resolved).acceptsReplies)
        assertTrue(detail(status = TicketStatus.InProgress).acceptsReplies)
        assertTrue(detail(status = TicketStatus.Received).acceptsReplies)
    }

    @Test fun `them tin khong trung`() {
        val m = TicketMessage("m1", MessageAuthor.Me, "Chào", created.plusMinutes(5))
        val once = detail().withMessage(m)
        assertEquals(listOf(m), once.messages)
        assertEquals(listOf(m), once.withMessage(m).messages)
    }

    @Test fun `tin nhan bo khoang trang, trong thi null`() {
        assertEquals("Cảm ơn", normalizedMessage("  Cảm ơn \n"))
        assertNull(normalizedMessage(" \n\t "))
        assertNull(normalizedMessage(""))
    }

    @Test fun `gio tin nhan chi them ngay khi khac ngay gui phieu`() {
        assertFalse(messageShowsDate(LocalDateTime.of(2026, 9, 26, 23, 59), created))
        assertTrue(messageShowsDate(LocalDateTime.of(2026, 9, 27, 0, 0), created))
        assertTrue(messageShowsDate(LocalDateTime.of(2026, 10, 26, 7, 52), created))
    }
}
