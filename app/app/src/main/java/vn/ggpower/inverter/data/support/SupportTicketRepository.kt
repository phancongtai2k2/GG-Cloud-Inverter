package vn.ggpower.inverter.data.support

import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import vn.ggpower.inverter.domain.support.MessageAuthor
import vn.ggpower.inverter.domain.support.NewTicket
import vn.ggpower.inverter.domain.support.TicketDetail
import vn.ggpower.inverter.domain.support.TicketMessage
import vn.ggpower.inverter.domain.support.TicketStatus
import vn.ggpower.inverter.domain.support.TicketSummary
import vn.ggpower.inverter.domain.support.TicketType
import vn.ggpower.inverter.domain.support.newestFirst
import java.time.Clock
import java.time.LocalDateTime
import javax.inject.Inject
import javax.inject.Singleton

/** Lỗi tải danh sách / gửi phiếu. Chưa có hợp đồng API nên chỉ phân loại. */
class SupportTicketException(val noConnection: Boolean) : Exception()

/** Phiếu hỗ trợ (màn 8). Chưa có hợp đồng API (docs/OPEN_QUESTIONS.md) — giai đoạn này dùng bản Fake. */
interface SupportTicketRepository {
    /** Phiếu của mọi dự án thuộc tài khoản, mới nhất trước. Ném [SupportTicketException] khi lỗi. */
    fun tickets(): Flow<List<TicketSummary>>

    /** Gửi phiếu; trả phiếu backend vừa tạo (có mã). Ném [SupportTicketException] khi lỗi. */
    suspend fun submit(ticket: NewTicket): TicketSummary

    /** Chi tiết một phiếu, cập nhật khi có lượt trao đổi mới. Ném [SupportTicketException] khi lỗi / không có phiếu. */
    fun detail(ticketId: String): Flow<TicketDetail>

    /** Gửi một tin nhắn; trả tin backend đã nhận. Ném [SupportTicketException] khi lỗi. */
    suspend fun sendMessage(ticketId: String, text: String): TicketMessage
}

/** Dữ liệu mẫu của Support.dc.html và TicketDetail.dc.html. Giữ phiếu trong bộ nhớ để phiếu / tin vừa gửi hiện lại. */
@Singleton
class FakeSupportTicketRepository @Inject constructor(private val clock: Clock) : SupportTicketRepository {

    private val _details = MutableStateFlow(SAMPLE_DETAILS)
    /** Giả lập lỗi tải danh sách / chi tiết (debug / test). */
    var loadFailure: SupportTicketException? = null
    /** Giả lập lỗi gửi phiếu (debug / test). */
    var submitFailure: SupportTicketException? = null
    /** Giả lập lỗi gửi tin nhắn (debug / test). */
    var messageFailure: SupportTicketException? = null
    private var sequence = 150
    private var messageSequence = 0

    override fun tickets(): Flow<List<TicketSummary>> = flow {
        delay(FAKE_DELAY_MS)
        loadFailure?.let { throw it }
        emitAll(_details.map { list -> list.map { it.summary }.newestFirst() })
    }

    override suspend fun submit(ticket: NewTicket): TicketSummary {
        delay(FAKE_DELAY_MS)
        submitFailure?.let { throw it }
        val now = LocalDateTime.now(clock)
        sequence += 1
        // Mã giả lập theo dạng của prototype "#HT-ddMM-nnnn"; mã thật do backend cấp.
        val code = "#HT-%02d%02d-%04d".format(now.dayOfMonth, now.monthValue, sequence)
        val created = TicketSummary(
            id = "t$sequence", code = code, title = ticket.title, type = ticket.type,
            projectName = ticket.projectName, createdAt = now, status = TicketStatus.Received,
        )
        _details.update { it + TicketDetail(created, ticket.description, ticket.attachments.size, emptyList()) }
        return created
    }

    override fun detail(ticketId: String): Flow<TicketDetail> = flow {
        delay(FAKE_DELAY_MS)
        loadFailure?.let { throw it }
        if (_details.value.none { it.summary.id == ticketId }) throw SupportTicketException(noConnection = false)
        emitAll(_details.map { list -> list.first { it.summary.id == ticketId } }.distinctUntilChanged())
    }

    override suspend fun sendMessage(ticketId: String, text: String): TicketMessage {
        delay(FAKE_DELAY_MS)
        messageFailure?.let { throw it }
        messageSequence += 1
        val message = TicketMessage("$ticketId-me$messageSequence", MessageAuthor.Me, text, LocalDateTime.now(clock))
        _details.update { list -> list.map { if (it.summary.id == ticketId) it.withMessage(message) else it } }
        return message
    }

    companion object {
        const val FAKE_DELAY_MS = 600L

        /**
         * t1 lấy nguyên từ TicketDetail.dc.html. Support.dc.html chỉ có ngày gửi; giờ và nội dung t2–t4 là dữ liệu
         * mẫu tạm của app (prototype không có chi tiết các phiếu này).
         */
        val SAMPLE_DETAILS = listOf(
            TicketDetail(
                TicketSummary("t1", "#HT-2609-0142", "Inverter báo lỗi mất lưới", TicketType.Technical, "Nhà của bạn", LocalDateTime.of(2026, 9, 26, 7, 40), TicketStatus.Received),
                description = "Sáng nay 07:12 inverter báo mất lưới và chuyển sang pin, trong khi hàng xóm vẫn có điện.",
                photoCount = 2,
                messages = listOf(
                    TicketMessage("t1-m1", MessageAuthor.Support, "Chào anh, chúng tôi đã nhận phiếu. Log cho thấy điện áp lưới dao động 180–190 V lúc 07:10. Kỹ thuật sẽ gọi anh trước 10:00.", LocalDateTime.of(2026, 9, 26, 7, 52)),
                    TicketMessage("t1-m2", MessageAuthor.Me, "Cảm ơn, tôi ở nhà cả buổi sáng.", LocalDateTime.of(2026, 9, 26, 8, 5)),
                ),
            ),
            TicketDetail(
                TicketSummary("t2", "#HT-2409-0117", "Nhiệt độ pin cao vào buổi chiều", TicketType.Technical, "Nhà máy GG Power", LocalDateTime.of(2026, 9, 24, 15, 10), TicketStatus.InProgress),
                description = "Nhiệt độ pin lên cao khoảng 14:00–16:00 các ngày nắng.",
                photoCount = 0,
                messages = emptyList(),
            ),
            TicketDetail(
                TicketSummary("t3", "#HT-1209-0086", "Không nhận mã OTP khi đổi số điện thoại", TicketType.Account, null, LocalDateTime.of(2026, 9, 12, 10, 5), TicketStatus.Resolved),
                description = "Đổi số điện thoại trong tài khoản nhưng không nhận được mã OTP.",
                photoCount = 1,
                messages = emptyList(),
            ),
            TicketDetail(
                TicketSummary("t4", "#HT-2808-0051", "Hỏi về lịch vệ sinh tấm pin", TicketType.Technical, "Nhà Ân Thi", LocalDateTime.of(2026, 8, 28, 16, 45), TicketStatus.Closed),
                description = "Tấm pin nên vệ sinh bao lâu một lần?",
                photoCount = 0,
                messages = emptyList(),
            ),
        )

        val SAMPLE: List<TicketSummary> = SAMPLE_DETAILS.map { it.summary }
    }
}
