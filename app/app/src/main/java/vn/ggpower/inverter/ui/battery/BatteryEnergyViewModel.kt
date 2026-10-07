package vn.ggpower.inverter.ui.battery

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import vn.ggpower.inverter.data.battery.BatteryEnergyLoadException
import vn.ggpower.inverter.data.battery.BatteryEnergyRepository
import vn.ggpower.inverter.data.settings.SettingsStore
import vn.ggpower.inverter.domain.battery.BatteryEnergy
import vn.ggpower.inverter.domain.settings.TemperatureUnit
import vn.ggpower.inverter.navigation.BatteryEnergy as BatteryEnergyRoute
import javax.inject.Inject

sealed interface BatteryEnergyUiState {
    data object Loading : BatteryEnergyUiState
    data class Error(val noConnection: Boolean) : BatteryEnergyUiState
    data class Content(val energy: BatteryEnergy) : BatteryEnergyUiState
}

@HiltViewModel
class BatteryEnergyViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    repository: BatteryEnergyRepository,
    settings: SettingsStore,
) : ViewModel() {
    private val controller = BatteryEnergyController(savedStateHandle.toRoute<BatteryEnergyRoute>().projectId, repository, viewModelScope)
    val state: StateFlow<BatteryEnergyUiState> get() = controller.state
    val temperatureUnit: StateFlow<TemperatureUnit> = settings.temperatureUnitIn(viewModelScope)
    fun retry() = controller.retry()
}

/** Tách khỏi ViewModel để test không cần SavedStateHandle. Thử lại thì hiện lại khung tải. */
@OptIn(ExperimentalCoroutinesApi::class)
class BatteryEnergyController(
    private val projectId: String,
    private val repository: BatteryEnergyRepository,
    scope: CoroutineScope,
) {
    private val reloads = MutableStateFlow(0)

    val state: StateFlow<BatteryEnergyUiState> = reloads.flatMapLatest {
        repository.batteryEnergy(projectId)
            .map<BatteryEnergy, BatteryEnergyUiState> { BatteryEnergyUiState.Content(it) }
            .onStart { emit(BatteryEnergyUiState.Loading) }
            .catch { e -> if (e is BatteryEnergyLoadException) emit(BatteryEnergyUiState.Error(e.noConnection)) else throw e }
    }.stateIn(scope, SharingStarted.WhileSubscribed(5_000), BatteryEnergyUiState.Loading)

    fun retry() = reloads.update { it + 1 }
}
