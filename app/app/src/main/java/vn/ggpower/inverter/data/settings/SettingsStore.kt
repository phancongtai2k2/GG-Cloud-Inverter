package vn.ggpower.inverter.data.settings

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import vn.ggpower.inverter.domain.settings.TemperatureUnit
import vn.ggpower.inverter.domain.settings.ThemeMode
import javax.inject.Inject
import javax.inject.Singleton

private val Context.settingsDataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

/** Cài đặt hệ thống của màn 3 lưu trên máy: giao diện và đơn vị nhiệt độ. Ngôn ngữ do [AppLocales] giữ. */
@Singleton
class SettingsStore @Inject constructor(@ApplicationContext private val context: Context) {

    val themeMode: Flow<ThemeMode> = context.settingsDataStore.data.map { p ->
        p[THEME]?.let { v -> ThemeMode.entries.firstOrNull { it.name == v } } ?: ThemeMode.Auto
    }

    val temperatureUnit: Flow<TemperatureUnit> = context.settingsDataStore.data.map { p ->
        p[TEMPERATURE]?.let { v -> TemperatureUnit.entries.firstOrNull { it.name == v } } ?: TemperatureUnit.Celsius
    }

    /**
     * [temperatureUnit] cho các màn hiện nhiệt độ: đổi ở màn 3 thì màn đang mở cập nhật theo. Trước khi DataStore
     * đọc xong (rất nhanh) tạm là °C.
     */
    fun temperatureUnitIn(scope: CoroutineScope): StateFlow<TemperatureUnit> =
        temperatureUnit.stateIn(scope, SharingStarted.WhileSubscribed(5_000), TemperatureUnit.Celsius)

    suspend fun setThemeMode(mode: ThemeMode) {
        context.settingsDataStore.edit { it[THEME] = mode.name }
    }

    suspend fun setTemperatureUnit(unit: TemperatureUnit) {
        context.settingsDataStore.edit { it[TEMPERATURE] = unit.name }
    }

    private companion object {
        val THEME = stringPreferencesKey("theme_mode")
        val TEMPERATURE = stringPreferencesKey("temperature_unit")
    }
}
