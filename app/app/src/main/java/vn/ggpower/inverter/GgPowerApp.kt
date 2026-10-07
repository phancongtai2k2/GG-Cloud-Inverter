package vn.ggpower.inverter

import android.app.Application
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import vn.ggpower.inverter.data.settings.AppLocales
import vn.ggpower.inverter.data.settings.SettingsStore
import javax.inject.Inject

@HiltAndroidApp
class GgPowerApp : Application() {
    @Inject lateinit var settings: SettingsStore
    @Inject lateinit var appLocales: AppLocales

    override fun onCreate() {
        super.onCreate()
        // Giao diện Sáng / Tối đã chọn ở màn 3 phải có trước khi Activity đầu tiên vẽ (AppCompat không tự lưu).
        appLocales.apply(runBlocking { settings.themeMode.first() })
    }
}
