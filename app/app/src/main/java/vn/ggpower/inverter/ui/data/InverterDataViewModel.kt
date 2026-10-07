package vn.ggpower.inverter.ui.data

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.SavedStateHandle
import androidx.navigation.toRoute
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import vn.ggpower.inverter.data.settings.SettingsStore
import vn.ggpower.inverter.data.telemetry.TelemetryLoadException
import vn.ggpower.inverter.domain.settings.TemperatureUnit
import vn.ggpower.inverter.data.telemetry.TelemetryRepository
import vn.ggpower.inverter.domain.telemetry.DeviceKind
import vn.ggpower.inverter.domain.telemetry.HistoryField
import vn.ggpower.inverter.domain.telemetry.HistoryRange
import vn.ggpower.inverter.domain.telemetry.InverterTelemetry
import vn.ggpower.inverter.navigation.DataInverter
import java.time.Clock
import javax.inject.Inject

@HiltViewModel
class InverterDataViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    repository: TelemetryRepository,
    clock: Clock,
    settings: SettingsStore,
) : ViewModel() {
    private val projectId = savedStateHandle.toRoute<DataInverter>().projectId
    private val controller = LiveController({ repository.inverter(projectId) }, InverterGroup.Overview, clock, viewModelScope)
    val state: StateFlow<InverterDataUiState> get() = controller.state
    val temperatureUnit: StateFlow<TemperatureUnit> = settings.temperatureUnitIn(viewModelScope)

    private val history = HistoryController(
        projectId, DeviceKind.Inverter, repository, clock, viewModelScope,
    )
    val historyState: StateFlow<HistoryUiState> get() = history.state

    fun onRangeChange(range: HistoryRange) = history.onRangeChange(range)
    fun onToggleField(field: HistoryField) = history.onToggleField(field)
    fun onSelectIndex(index: Int) = history.onSelectIndex(index)
    fun onHistoryRetry() = history.retry()

    fun onGroupChange(group: InverterGroup) = controller.onGroupChange(group)
    fun onRefresh() = controller.onRefresh()
}
