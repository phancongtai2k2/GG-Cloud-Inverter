package vn.ggpower.inverter.data.auth

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Clock
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset

/** Mã sống 5 phút; sai 5 lần liên tiếp khoá 15 phút (đặc tả §3.12), mô phỏng ở bản Fake. */
class FakeOtpBookTest {
    private var now = Instant.parse("2026-10-03T08:00:00Z")
    private val clock = object : Clock() {
        override fun getZone(): ZoneId = ZoneOffset.UTC
        override fun withZone(zone: ZoneId?): Clock = this
        override fun instant(): Instant = now
    }

    @Test fun `ma dung thi hop le, dung xong khong dung lai duoc`() {
        val book = FakeOtpBook(clock)
        book.issue("a")
        assertTrue(book.verify("a", FakeOtpBook.SAMPLE_CODE) is VerifyCodeResult.Valid)
        assertEquals(VerifyCodeResult.Expired, book.verify("a", FakeOtpBook.SAMPLE_CODE))
    }

    @Test fun `ma het han sau 5 phut`() {
        val book = FakeOtpBook(clock)
        book.issue("a")
        now = now.plusSeconds(299)
        assertTrue(book.verify("a", FakeOtpBook.SAMPLE_CODE) is VerifyCodeResult.Valid)
        book.issue("a")
        now = now.plusSeconds(300)
        assertEquals(VerifyCodeResult.Expired, book.verify("a", FakeOtpBook.SAMPLE_CODE))
    }

    @Test fun `sai 5 lan thi khoa 15 phut, ke ca ma dung`() {
        val book = FakeOtpBook(clock)
        book.issue("a")
        for (left in 4 downTo 1) assertEquals(VerifyCodeResult.Wrong(left), book.verify("a", "000000"))
        assertEquals(VerifyCodeResult.Locked, book.verify("a", "000000"))
        book.issue("a")
        assertEquals(VerifyCodeResult.Locked, book.verify("a", FakeOtpBook.SAMPLE_CODE))
        now = now.plusSeconds(15 * 60)
        book.issue("a")
        assertTrue(book.verify("a", FakeOtpBook.SAMPLE_CODE) is VerifyCodeResult.Valid)
    }
}
