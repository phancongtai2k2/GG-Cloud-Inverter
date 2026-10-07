package vn.ggpower.inverter.ui.devices

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import vn.ggpower.inverter.data.device.DeviceLoadException
import vn.ggpower.inverter.data.device.DeviceRepository
import vn.ggpower.inverter.data.settings.SettingsStore
import vn.ggpower.inverter.domain.settings.TemperatureUnit
import vn.ggpower.inverter.navigation.Devices
import javax.inject.Inject

@HiltViewModel
class DevicesViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    repository: DeviceRepository,
    settings: SettingsStore,
) : ViewModel() {
    private val loader = DevicesLoader(savedStateHandle.toRoute<Devices>().projectId, repository)
    val state: StateFlow<DevicesUiState> get() = loader.state
    val temperatureUnit: StateFlow<TemperatureUnit> = settings.temperatureUnitIn(viewModelScope)

    init { retry() }

    fun retry() { viewModelScope.launch { loader.load() } }
}

/** Tải thiết bị của dự án; tách khỏi ViewModel để test không cần SavedStateHandle. */
class DevicesLoader(private val projectId: String, private val repository: DeviceRepository) {
    private val _state = MutableStateFlow<DevicesUiState>(DevicesUiState.Loading)
    val state: StateFlow<DevicesUiState> = _state.asStateFlow()

    suspend fun load() {
        _state.value = DevicesUiState.Loading
        _state.value = try {
            DevicesUiState.Content(repository.getDevices(projectId))
        } catch (e: DeviceLoadException) {
            DevicesUiState.Error(noConnection = e.kind == DeviceLoadException.Kind.NoConnection)
        }
    }
}
