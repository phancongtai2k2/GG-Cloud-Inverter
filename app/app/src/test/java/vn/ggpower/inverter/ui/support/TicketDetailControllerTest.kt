package vn.ggpower.inverter.ui.support

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import vn.ggpower.inverter.data.support.FakeSupportTicketRepository
import vn.ggpower.inverter.data.support.SupportTicketException
import vn.ggpower.inverter.domain.support.MessageAuthor
import vn.ggpower.inverter.ui.service.COPIED_FEEDBACK_MS
import java.time.Clock
import java.time.LocalDateTime
import java.time.ZoneOffset

@OptIn(ExperimentalCoroutinesApi::class)
class TicketDetailControllerTest {
    private val now = LocalDateTime.of(2026, 10, 3, 9, 41)
    private val clock = Clock.fixed(now.toInstant(ZoneOffset.UTC), ZoneOffset.UTC)
    private val repo = FakeSupportTicketRepository(clock)

    private fun TestScope.controller(ticketId: String = "t1"): TicketDetailController {
        val c = TicketDetailController(ticketId, repo, backgroundScope)
        backgroundScope.launch { c.state.collect {} }
        settle()
        return c
    }

    private fun TestScope.settle() { advanceTimeBy(2_000); runCurrent() }

    @Test fun `tai chi tiet phieu mau`() = runTest {
        val d = controller().state.value.detail!!
        assertEquals("#HT-2609-0142", d.summary.code)
        assertEquals(2, d.messages.size)
        assertEquals(2, d.photoCount)
    }

    @Test fun `phieu khong ton tai bao loi`() = runTest {
        assertEquals(DetailLoad.Error(noConnection = false), controller("khong-co").state.value.load)
    }

    @Test fun `loi tai roi thu lai`() = runTest {
        repo.loadFailure = SupportTicketException(noConnection = true)
        val c = controller()
        assertEquals(DetailLoad.Error(noConnection = true), c.state.value.load)
        repo.loadFailure = null
        c.load()
        assertEquals(DetailLoad.Loading, c.state.value.load)
        settle()
        assertTrue(c.state.value.load is DetailLoad.Content)
    }

    @Test fun `gui tin thanh cong thi them vao cuoi va xoa o nhap`() = runTest {
        val c = controller()
        c.onMessage("  Kỹ thuật gọi lúc mấy giờ ạ? ")
        c.onSend()
        assertTrue(c.state.value.sending)
        assertEquals(2, c.state.value.detail!!.messages.size)
        settle()
        val s = c.state.value
        assertFalse(s.sending)
        assertEquals("", s.message)
        val last = s.detail!!.messages.last()
        assertEquals(3, s.detail!!.messages.size)
        assertEquals(MessageAuthor.Me, last.author)
        assertEquals("Kỹ thuật gọi lúc mấy giờ ạ?", last.text)
        assertEquals(now, last.sentAt)
    }

    @Test fun `gui loi giu chu va bao loi, gui lai thi xoa loi`() = runTest {
        repo.messageFailure = SupportTicketException(noConnection = true)
        val c = controller()
        c.onMessage("Xin chào")
        c.onSend(); settle()
        assertEquals(SubmitFailure(noConnection = true), c.state.value.sendFailed)
        assertEquals("Xin chào", c.state.value.message)
        assertEquals(2, c.state.value.detail!!.messages.size)
        repo.messageFailure = null
        c.onSend()
        assertNull(c.state.value.sendFailed)
        settle()
        assertEquals(3, c.state.value.detail!!.messages.size)
    }

    @Test fun `o trong hoac dang gui thi khong gui`() = runTest {
        val c = controller()
        c.onMessage("   ")
        assertFalse(c.state.value.canSend)
        c.onSend()
        assertFalse(c.state.value.sending)
        c.onMessage("A")
        c.onSend(); c.onSend()
        settle()
        assertEquals(3, c.state.value.detail!!.messages.size)
    }

    @Test fun `phieu Da dong khong gui duoc`() = runTest {
        val c = controller("t4")
        c.onMessage("Còn ai không?")
        assertFalse(c.state.value.canSend)
        c.onSend(); settle()
        assertTrue(c.state.value.detail!!.messages.isEmpty())
    }

    @Test fun `sao chep tu an sau 2 giay`() = runTest {
        val c = controller()
        c.onCopied()
        assertTrue(c.state.value.copied)
        advanceTimeBy(COPIED_FEEDBACK_MS - 1); runCurrent()
        assertTrue(c.state.value.copied)
        advanceTimeBy(2); runCurrent()
        assertFalse(c.state.value.copied)
    }
}
