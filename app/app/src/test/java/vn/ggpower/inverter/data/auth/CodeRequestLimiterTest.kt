package vn.ggpower.inverter.data.auth

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Clock
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset

/** Giới hạn gửi mã 3 lần / 10 phút cho cùng tài khoản (đặc tả §3.12). */
class CodeRequestLimiterTest {
    private var now = Instant.parse("2026-10-03T08:00:00Z")
    private val clock = object : Clock() {
        override fun getZone(): ZoneId = ZoneOffset.UTC
        override fun withZone(zone: ZoneId?): Clock = this
        override fun instant(): Instant = now
    }

    @Test fun `lan thu 4 trong 10 phut bi chan, tai khoan khac khong anh huong`() {
        val limiter = CodeRequestLimiter(clock)
        repeat(3) { assertTrue(limiter.tryAcquire("0912345678")) }
        assertFalse(limiter.tryAcquire("0912345678"))
        assertTrue(limiter.tryAcquire("demo@ggp.vn"))
    }

    @Test fun `du 10 phut tu lan dau thi gui lai duoc`() {
        val limiter = CodeRequestLimiter(clock)
        repeat(3) { limiter.tryAcquire("a"); now = now.plusSeconds(60) }
        now = Instant.parse("2026-10-03T08:09:59Z")
        assertFalse(limiter.tryAcquire("a"))
        now = Instant.parse("2026-10-03T08:10:00Z")
        assertTrue(limiter.tryAcquire("a"))
    }
}
