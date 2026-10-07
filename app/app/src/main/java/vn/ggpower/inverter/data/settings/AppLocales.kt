package vn.ggpower.inverter.data.settings

import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import vn.ggpower.inverter.domain.settings.AppLanguage
import vn.ggpower.inverter.domain.settings.ThemeMode
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Áp dụng ngôn ngữ và giao diện cho toàn app ngay, không khởi động lại (Thay đổi 0.6 §7): AppCompat tự lưu ngôn ngữ
 * đã chọn (API 33+ qua LocaleManager, thấp hơn qua `autoStoreLocales` trong manifest) và dựng lại Activity.
 */
@Singleton
class AppLocales @Inject constructor() {

    /** Ngôn ngữ đang dùng: đã chọn trong app, không thì theo hệ điều hành (ngoài vi / en → Tiếng Việt). */
    fun current(): AppLanguage {
        val chosen = AppCompatDelegate.getApplicationLocales()
        val locale = if (chosen.isEmpty) LocaleListCompat.getAdjustedDefault()[0] else chosen[0]
        return AppLanguage.fromTag(locale?.toLanguageTag())
    }

    fun apply(language: AppLanguage) {
        AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags(language.tag))
    }

    fun apply(mode: ThemeMode) {
        AppCompatDelegate.setDefaultNightMode(
            when (mode) {
                ThemeMode.Light -> AppCompatDelegate.MODE_NIGHT_NO
                ThemeMode.Dark -> AppCompatDelegate.MODE_NIGHT_YES
                ThemeMode.Auto -> AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM
            },
        )
    }
}
