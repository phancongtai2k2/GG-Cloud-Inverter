package vn.ggpower.inverter.domain.settings

/** Giao diện của app (đặc tả §3.3): Sáng / Tối / Tự động theo hệ điều hành, mặc định Tự động. */
enum class ThemeMode { Light, Dark, Auto }

/** Đơn vị nhiệt độ hiển thị (đặc tả §3.3), mặc định °C. Ngưỡng cấu hình thiết bị vẫn theo °C (quyết 03/10/2026). */
enum class TemperatureUnit {
    Celsius, Fahrenheit;

    /** Đổi giá trị đo (luôn là °C từ thiết bị) sang đơn vị này. */
    fun fromCelsius(celsius: Double): Double = when (this) {
        Celsius -> celsius
        Fahrenheit -> celsius * 9.0 / 5.0 + 32.0
    }
}

/** Ngôn ngữ app hỗ trợ (Thay đổi 0.6 §7). [tag] là mã BCP 47. */
enum class AppLanguage(val tag: String) {
    Vietnamese("vi"), English("en");

    companion object {
        /**
         * Ngôn ngữ ứng với một mã ngôn ngữ (của hệ điều hành hoặc đã lưu). Không thuộc hai ngôn ngữ hỗ trợ (hoặc
         * không có) thì dùng Tiếng Việt (đặc tả §3.19, giữ cho màn 3 theo MAU_THUAN M7).
         */
        fun fromTag(tag: String?): AppLanguage {
            val lang = tag?.substringBefore('-')?.substringBefore('_')?.lowercase()
            return entries.firstOrNull { it.tag == lang } ?: Vietnamese
        }
    }
}
