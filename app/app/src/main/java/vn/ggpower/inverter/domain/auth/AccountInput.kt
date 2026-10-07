package vn.ggpower.inverter.domain.auth

/**
 * Ô "Email hoặc số điện thoại" dùng chung cho đăng nhập (1), quên mật khẩu (12a) và đăng ký (13a).
 * Đặc tả §3.1: kiểm tra định dạng email / số điện thoại, báo lỗi ngay dưới ô nhập.
 */
sealed interface AccountInput {
    data object Empty : AccountInput
    data object Invalid : AccountInput
    /** Email đã bỏ khoảng trắng hai đầu và viết thường. */
    data class Email(val value: String) : AccountInput
    /** Số điện thoại Việt Nam dạng 10 số bắt đầu bằng 0 (đã bỏ dấu cách, dấu chấm, gạch; +84 / 84 đổi về 0). */
    data class Phone(val value: String) : AccountInput

    companion object {
        private val EMAIL = Regex("^[A-Za-z0-9._%+-]+@[A-Za-z0-9-]+(\\.[A-Za-z0-9-]+)*\\.[A-Za-z]{2,}$")
        private val PHONE_SEPARATORS = Regex("[\\s.\\-]")
        private val VN_MOBILE = Regex("^0[35789]\\d{8}$")

        fun parse(raw: String): AccountInput {
            val text = raw.trim()
            if (text.isEmpty()) return Empty
            if ('@' in text) {
                return if (EMAIL.matches(text)) Email(text.lowercase()) else Invalid
            }
            val digits = text.replace(PHONE_SEPARATORS, "")
            val local = when {
                digits.startsWith("+84") -> "0" + digits.removePrefix("+84")
                digits.startsWith("84") && digits.length == 11 -> "0" + digits.removePrefix("84")
                else -> digits
            }
            return if (VN_MOBILE.matches(local)) Phone(local) else Invalid
        }
    }
}
