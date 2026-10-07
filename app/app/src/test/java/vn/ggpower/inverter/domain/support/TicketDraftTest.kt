package vn.ggpower.inverter.domain.support

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import vn.ggpower.inverter.data.alert.FakeAlertRepository
import java.time.LocalDateTime

class TicketDraftTest {

    private fun file(n: Int) = TicketAttachment("content://media/$n", "IMG_$n.jpg", isVideo = false)

    @Test fun `form trong bao loi tieu de, mo ta va du an khi chon Ky thuat`() {
        assertEquals(
            setOf(TicketFieldError.ProjectMissing, TicketFieldError.TitleEmpty, TicketFieldError.DescriptionEmpty),
            TicketDraft().validate(hasProjects = true),
        )
    }

    @Test fun `chi khoang trang van tinh la trong`() {
        val draft = TicketDraft(projectId = "p1", title = "   ", description = "\n ")
        assertEquals(setOf(TicketFieldError.TitleEmpty, TicketFieldError.DescriptionEmpty), draft.validate(true))
    }

    @Test fun `loai Tai khoan va Khac khong can du an`() {
        val draft = TicketDraft(type = TicketType.Account, title = "Đổi email", description = "Không nhận được mã")
        assertTrue(draft.validate(true).isEmpty())
        assertFalse(draft.showsProject(true))
        assertTrue(draft.copy(type = TicketType.Other).validate(true).isEmpty())
    }

    @Test fun `tai khoan chua co du an thi an o du an va cho gui`() {
        val draft = TicketDraft(title = "Thiết bị thuộc tài khoản khác", description = "Serial SN2609A00137")
        assertFalse(draft.showsProject(hasProjects = false))
        assertTrue(draft.validate(hasProjects = false).isEmpty())
    }

    @Test fun `noi dung gui di bo khoang trang va bo du an khi khong phai Ky thuat`() {
        val names = mapOf("p1" to "Nhà của bạn")
        val tech = TicketDraft(projectId = "p1", title = "  Mất lưới ", description = " Từ 7 giờ ")
            .toNewTicket(true, names::get, alertId = "grid")
        assertEquals("p1", tech.projectId)
        assertEquals("Nhà của bạn", tech.projectName)
        assertEquals("Mất lưới", tech.title)
        assertEquals("Từ 7 giờ", tech.description)
        assertEquals("grid", tech.alertId)

        val account = TicketDraft(type = TicketType.Account, projectId = "p1", title = "a", description = "b")
            .toNewTicket(true, names::get, alertId = "grid")
        assertNull(account.projectId)
        assertNull(account.projectName)
        assertNull(account.alertId)
    }

    @Test fun `dinh kem bo tep trung va toi da 5 tep`() {
        var draft = TicketDraft().withAttachments(listOf(file(1), file(2), file(1)))
        assertEquals(listOf("IMG_1.jpg", "IMG_2.jpg"), draft.attachments.map { it.name })
        assertEquals(3, draft.attachmentSlotsLeft)

        draft = draft.withAttachments((2..8).map(::file))
        assertEquals(MAX_TICKET_ATTACHMENTS, draft.attachments.size)
        assertEquals(listOf(1, 2, 3, 4, 5).map { "IMG_$it.jpg" }, draft.attachments.map { it.name })
        assertEquals(0, draft.attachmentSlotsLeft)

        draft = draft.withoutAttachment("content://media/3")
        assertEquals(listOf(1, 2, 4, 5).map { "IMG_$it.jpg" }, draft.attachments.map { it.name })
    }

    @Test fun `dien san tu canh bao dang dien ra theo prototype`() {
        val alert = FakeAlertRepository.detailSample("grid")
        assertEquals("Mất kết nối lưới điện – Nhà của bạn", alertTicketTitle(alert))
        assertEquals(
            "Cảnh báo Lỗi lúc 07:12 · 24/09/2026, vẫn đang diễn ra. Mã lỗi: 0x200 CurrErrReg · bit [MÃ BIT].",
            alertTicketDescription(alert, "Lỗi", ONGOING, ENDED),
        )
    }

    @Test fun `dien san tu canh bao da ket thuc`() {
        val alert = FakeAlertRepository.detailSample("pv")
        assertEquals(
            "Cảnh báo Cảnh báo lúc ${vn.ggpower.inverter.domain.format.GgFormat.dateTimeFull(alert.startedAt)}, " +
                "đã kết thúc lúc 17:13 · 20/09/2026. Mã lỗi: 0x200 CurrErrReg · bit [MÃ BIT].",
            alertTicketDescription(alert, "Cảnh báo", ONGOING, ENDED),
        )
    }

    @Test fun `phieu moi nhat len dau`() {
        val old = TicketSummary("a", "#HT-1", "A", TicketType.Other, null, LocalDateTime.of(2026, 8, 1, 9, 0), TicketStatus.Closed)
        val new = old.copy(id = "b", createdAt = LocalDateTime.of(2026, 10, 3, 9, 0))
        assertEquals(listOf("b", "a"), listOf(old, new).newestFirst().map { it.id })
    }

    private companion object {
        const val ONGOING = "Cảnh báo %1\$s lúc %2\$s, vẫn đang diễn ra. Mã lỗi: %3\$s."
        const val ENDED = "Cảnh báo %1\$s lúc %2\$s, đã kết thúc lúc %3\$s. Mã lỗi: %4\$s."
    }
}
