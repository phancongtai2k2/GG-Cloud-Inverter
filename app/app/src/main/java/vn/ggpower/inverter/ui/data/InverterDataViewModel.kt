package vn.ggpower.inverter.ui.data

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.StateFlow
import vn.ggpower.inverter.data.settings.SettingsStore
import vn.ggpower.inverter.data.telemetry.TelemetryRepository
import vn.ggpower.inverter.domain.settings.TemperatureUnit
import vn.ggpower.inverter.navigation.DataInverter
import java.time.Clock
import javax.inject.Inject

/** Màn 17 · Theo dõi dữ liệu inverter: số liệu thời gian thực, mọi nhóm hiện cùng lúc (thiết kế mới, không có biểu đồ lịch sử). */
@HiltViewModel
class InverterDataViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    repository: TelemetryRepository,
    clock: Clock,
    settings: SettingsStore,
) : ViewModel() {
    private val projectId = savedStateHandle.toRoute<DataInverter>().projectId
    private val controller = LiveController({ repository.inverter(projectId) }, clock, viewModelScope)
    val state: StateFlow<InverterDataUiState> get() = controller.state
    val temperatureUnit: StateFlow<TemperatureUnit> = settings.temperatureUnitIn(viewModelScope)

    fun onRefresh() = controller.onRefresh()
}
