package vn.ggpower.inverter.data.auth

import java.time.Clock
import java.time.Duration
import java.time.Instant

/**
 * Mô phỏng mã xác thực của backend cho `Fake…Repository` (đặc tả §3.12): mỗi lần gửi cấp mã mới [code] sống
 * [lifetime]; sai [maxWrong] lần liên tiếp thì khoá [lockFor]. Khoá còn hiệu lực thì mọi lần nhập đều bị từ chối.
 */
class FakeOtpBook(
    private val clock: Clock,
    private val code: String = SAMPLE_CODE,
    private val lifetime: Duration = Duration.ofMinutes(5),
    private val maxWrong: Int = 5,
    private val lockFor: Duration = Duration.ofMinutes(15),
) {
    private class Entry(var issuedAt: Instant, var wrong: Int = 0, var lockedUntil: Instant? = null)

    private val entries = mutableMapOf<Any, Entry>()

    /** Gửi mã mới cho [key]; giữ khoá nếu đang bị khoá. */
    fun issue(key: Any) {
        val now = clock.instant()
        val e = entries[key]
        if (e == null) entries[key] = Entry(now) else { e.issuedAt = now; e.wrong = 0 }
    }

    fun verify(key: Any, input: String): VerifyCodeResult {
        val now = clock.instant()
        val e = entries[key] ?: return VerifyCodeResult.Expired
        e.lockedUntil?.let { if (now < it) return VerifyCodeResult.Locked else e.lockedUntil = null }
        if (Duration.between(e.issuedAt, now) >= lifetime) return VerifyCodeResult.Expired
        if (input == code) {
            entries.remove(key)
            return VerifyCodeResult.Valid(token = "reset-$key")
        }
        e.wrong++
        if (e.wrong >= maxWrong) {
            e.wrong = 0
            e.lockedUntil = now.plus(lockFor)
            return VerifyCodeResult.Locked
        }
        return VerifyCodeResult.Wrong(attemptsLeft = maxWrong - e.wrong)
    }

    companion object {
        /** Mã giả lập được chấp nhận (quyết 03/10/2026). */
        const val SAMPLE_CODE = "123456"
    }
}
