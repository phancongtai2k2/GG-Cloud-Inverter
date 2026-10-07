package vn.ggpower.inverter.ui.me

import vn.ggpower.inverter.domain.account.NotificationPrefs
import vn.ggpower.inverter.domain.account.UserProfile
import vn.ggpower.inverter.domain.settings.AppLanguage
import vn.ggpower.inverter.domain.settings.TemperatureUnit
import vn.ggpower.inverter.domain.settings.ThemeMode

/** Màn 3 · Tôi. [profile] null khi đang tải hồ sơ; cài đặt hệ thống đọc từ máy nên luôn có. */
data class MeUiState(
    val profile: UserProfile? = null,
    val notifications: NotificationPrefs = NotificationPrefs(),
    val temperatureUnit: TemperatureUnit = TemperatureUnit.Celsius,
    val themeMode: ThemeMode = ThemeMode.Auto,
    val language: AppLanguage = AppLanguage.Vietnamese,
    /** Danh sách thả xuống Ngôn ngữ đang mở (Me__language-open.png). */
    val languageOpen: Boolean = false,
)
