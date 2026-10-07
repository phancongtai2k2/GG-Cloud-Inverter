package vn.ggpower.inverter.data.auth

import java.time.Clock
import java.time.Duration
import java.time.Instant

/**
 * Mô phỏng giới hạn gửi mã của backend (đặc tả §3.12): tối đa [max] lần trong [window] cho cùng một khoá (tài khoản).
 * Chỉ dùng cho `Fake…Repository`; backend thật tự kiểm tra.
 */
class CodeRequestLimiter(
    private val clock: Clock,
    private val max: Int = 3,
    private val window: Duration = Duration.ofMinutes(10),
) {
    private val requests = mutableMapOf<Any, MutableList<Instant>>()

    /** Ghi nhận một lần gửi; false nếu đã đủ [max] lần trong [window] (lần này không được tính). */
    fun tryAcquire(key: Any): Boolean {
        val now = clock.instant()
        val recent = requests.getOrPut(key) { mutableListOf() }
        recent.removeAll { Duration.between(it, now) >= window }
        if (recent.size >= max) return false
        recent += now
        return true
    }
}
