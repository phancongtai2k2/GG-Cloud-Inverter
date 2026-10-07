package vn.ggpower.inverter.ui.support

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import vn.ggpower.inverter.data.alert.FakeAlertRepository
import vn.ggpower.inverter.data.project.FakeProjectRepository
import vn.ggpower.inverter.data.support.FakeSupportTicketRepository
import vn.ggpower.inverter.data.support.SupportTicketException
import vn.ggpower.inverter.domain.support.TicketFieldError
import vn.ggpower.inverter.domain.support.TicketType
import java.time.Clock
import java.time.LocalDateTime
import java.time.ZoneOffset

@OptIn(ExperimentalCoroutinesApi::class)
class SupportControllerTest {
    private val now = LocalDateTime.of(2026, 10, 3, 9, 41)
    private val clock = Clock.fixed(now.toInstant(ZoneOffset.UTC), ZoneOffset.UTC)
    private val projects = FakeProjectRepository()
    private val tickets = FakeSupportTicketRepository(clock)

    private fun TestScope.controller(projectId: String = "p1", alertId: String? = null): SupportController {
        val c = SupportController(
            projectId, alertId, projects, FakeAlertRepository(), tickets,
            prefillDescription = { "Mô tả ${it.code}" },
            scope = backgroundScope,
        )
        backgroundScope.launch { c.state.collect {} }
        advanceTimeBy(2_000); runCurrent()
        return c
    }

    @Test fun `mo tu Dich vu chon san du an dang mo, form trong`() = runTest {
        val s = controller().state.value
        assertEquals(FormLoad.Ready, s.load)
        assertEquals("p1", s.draft.projectId)
        assertEquals(TicketType.Technical, s.draft.type)
        assertEquals("", s.draft.title)
        assertNull(s.alert)
        assertTrue(s.tickets is TicketsState.Content)
    }

    @Test fun `mo tu 10a dien san tu canh bao`() = runTest {
        val s = controller(alertId = "grid").state.value
        assertEquals("Mất kết nối lưới điện", s.alert?.title)
        assertEquals("Mất kết nối lưới điện – Nhà của bạn", s.draft.title)
        assertEquals("Mô tả 0x200 CurrErrReg · bit [MÃ BIT]", s.draft.description)
        assertEquals("p1", s.draft.projectId)
    }

    @Test fun `mo tu 14a khong gan du an`() = runTest {
        assertNull(controller(projectId = "").state.value.draft.projectId)
    }

    @Test fun `loi kiem tra chi hien sau khi bam Gui`() = runTest {
        val c = controller(projectId = "")
        assertTrue(c.state.value.errors.isEmpty())
        c.onSubmit()
        assertEquals(
            setOf(TicketFieldError.ProjectMissing, TicketFieldError.TitleEmpty, TicketFieldError.DescriptionEmpty),
            c.state.value.errors,
        )
        c.onPickProject("p2")
        c.onTitle("Pin không sạc")
        assertEquals(setOf(TicketFieldError.DescriptionEmpty), c.state.value.errors)
        assertFalse(c.state.value.submitting)
    }

    @Test fun `chon du an thi danh sach tu dong`() = runTest {
        val c = controller()
        c.onToggleProjectMenu()
        assertTrue(c.state.value.projectMenuOpen)
        c.onPickProject("p3")
        assertFalse(c.state.value.projectMenuOpen)
        assertEquals("Nhà máy GG Power", c.state.value.selectedProject?.name)
    }

    @Test fun `gui thanh cong chuyen sang Yeu cau cua toi, phieu moi tren cung`() = runTest {
        val c = controller(alertId = "grid")
        c.onSubmit()
        assertTrue(c.state.value.submitting)
        advanceTimeBy(1_000); runCurrent()

        val s = c.state.value
        assertFalse(s.submitting)
        assertEquals(SupportTab.Mine, s.tab)
        val sent = s.sent!!
        assertEquals("#HT-0310-0151", sent.code)
        assertEquals("Nhà của bạn", sent.projectName)
        assertEquals(sent.id, (s.tickets as TicketsState.Content).tickets.first().id)
        // Form trở về trống, không còn dải "Tạo từ cảnh báo".
        assertNull(s.alert)
        assertEquals("", s.draft.title)
        assertEquals("p1", s.draft.projectId)

        c.onSentHandled()
        assertNull(c.state.value.sent)
    }

    @Test fun `gui loi giu nguyen noi dung va bao loi`() = runTest {
        val c = controller(alertId = "grid")
        tickets.submitFailure = SupportTicketException(noConnection = true)
        c.onSubmit()
        advanceTimeBy(1_000); runCurrent()

        val s = c.state.value
        assertEquals(SubmitFailure(noConnection = true), s.submitFailed)
        assertEquals(SupportTab.New, s.tab)
        assertEquals("Mất kết nối lưới điện – Nhà của bạn", s.draft.title)
        assertNotNull(s.alert)

        tickets.submitFailure = null
        c.onSubmit()
        advanceTimeBy(1_000); runCurrent()
        assertNull(c.state.value.submitFailed)
        assertEquals(SupportTab.Mine, c.state.value.tab)
    }

    @Test fun `tai danh sach loi roi thu lai`() = runTest {
        tickets.loadFailure = SupportTicketException(noConnection = false)
        val c = controller()
        assertEquals(TicketsState.Error(noConnection = false), c.state.value.tickets)
        tickets.loadFailure = null
        c.loadTickets()
        advanceTimeBy(1_000); runCurrent()
        assertEquals(4, (c.state.value.tickets as TicketsState.Content).tickets.size)
    }
}
