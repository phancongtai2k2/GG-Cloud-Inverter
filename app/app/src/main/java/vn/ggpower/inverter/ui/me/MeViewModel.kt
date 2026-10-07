package vn.ggpower.inverter.ui.me

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import vn.ggpower.inverter.data.account.ProfileRepository
import vn.ggpower.inverter.data.settings.AppLocales
import vn.ggpower.inverter.data.settings.SettingsStore
import vn.ggpower.inverter.domain.account.NotificationPrefs
import vn.ggpower.inverter.domain.settings.AppLanguage
import vn.ggpower.inverter.domain.settings.TemperatureUnit
import vn.ggpower.inverter.domain.settings.ThemeMode
import javax.inject.Inject

/**
 * Màn 3 · Tôi (đặc tả §3.3, Thay đổi 0.6 §7). Công tắc thông báo và ngôn ngữ lưu vào hồ sơ tài khoản; giao diện và đơn
 * vị nhiệt độ lưu trên máy. Ngôn ngữ và giao diện áp dụng ngay (Activity được dựng lại, ViewModel giữ nguyên).
 */
@HiltViewModel
class MeViewModel @Inject constructor(
    private val profiles: ProfileRepository,
    private val settings: SettingsStore,
    private val appLocales: AppLocales,
) : ViewModel() {

    private val _state = MutableStateFlow(MeUiState(language = appLocales.current()))
    val state: StateFlow<MeUiState> = _state.asStateFlow()

    init {
        // Theo dõi hồ sơ: lưu tên / ảnh ở màn 15 thì thẻ hồ sơ cập nhật khi quay lại.
        viewModelScope.launch {
            profiles.observeProfile().collect { profile ->
                _state.update { it.copy(profile = profile, notifications = profile.notifications) }
            }
        }
        viewModelScope.launch { settings.themeMode.collect { m -> _state.update { it.copy(themeMode = m) } } }
        viewModelScope.launch { settings.temperatureUnit.collect { u -> _state.update { it.copy(temperatureUnit = u) } } }
    }

    fun onNotificationsChange(prefs: NotificationPrefs) {
        _state.update { it.copy(notifications = prefs) }
        viewModelScope.launch { profiles.setNotifications(prefs) }
    }

    fun onTemperatureUnit(unit: TemperatureUnit) {
        viewModelScope.launch { settings.setTemperatureUnit(unit) }
    }

    fun onThemeMode(mode: ThemeMode) {
        if (mode == _state.value.themeMode) return
        viewModelScope.launch {
            settings.setThemeMode(mode)
            appLocales.apply(mode)
        }
    }

    fun onToggleLanguageList() = _state.update { it.copy(languageOpen = !it.languageOpen) }

    /** Chọn xong tự đóng và áp dụng ngay (Thay đổi 0.6 §7). */
    fun onLanguage(language: AppLanguage) {
        val changed = language != _state.value.language
        _state.update { it.copy(language = language, languageOpen = false) }
        if (!changed) return
        viewModelScope.launch { profiles.setLanguage(language) }
        appLocales.apply(language)
    }
}
