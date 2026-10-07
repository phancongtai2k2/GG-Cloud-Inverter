package vn.ggpower.inverter.domain.account

import java.util.Locale

/** Ba công tắc thông báo của màn 3 (đặc tả §3.3), mặc định Bật · Bật · Tắt. */
data class NotificationPrefs(
    val deviceAlerts: Boolean = true,
    val dailyReport: Boolean = true,
    val ggPowerNews: Boolean = false,
)

/** Hồ sơ tài khoản đang đăng nhập (khối tài khoản của màn 3). */
data class UserProfile(
    val name: String,
    val email: String,
    val phone: String,
    /** Ảnh đại diện (URI); null thì avatar hiện chữ viết tắt của họ tên (đặc tả §3.15). */
    val avatarUri: String? = null,
    val notifications: NotificationPrefs = NotificationPrefs(),
)

/**
 * Chữ viết tắt trên avatar (đặc tả §3.3 "ảnh đại diện (chữ viết tắt)"): chữ đầu của từ đầu và từ cuối, viết hoa
 * ("Michael Demo" → "MD", "Nguyễn Văn An" → "NA"); tên một từ → một chữ; tên rỗng → "".
 */
fun initialsOf(name: String): String {
    val words = name.trim().split(Regex("\\s+")).filter { it.isNotEmpty() }
    if (words.isEmpty()) return ""
    val picked = if (words.size == 1) listOf(words.first()) else listOf(words.first(), words.last())
    return picked.joinToString("") { it.substring(0, it.offsetByCodePoints(0, 1)) }.uppercase(Locale.forLanguageTag("vi"))
}
