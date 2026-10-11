package vn.ggpower.inverter.ui.data

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import vn.ggpower.inverter.data.settings.SettingsStore
import vn.ggpower.inverter.data.telemetry.TelemetryRepository
import vn.ggpower.inverter.domain.settings.TemperatureUnit
import vn.ggpower.inverter.domain.telemetry.BatteryTelemetry
import vn.ggpower.inverter.navigation.DataBattery
import java.time.Clock
import javax.inject.Inject

/** Năm nhóm dữ liệu pin (đặc tả §17.3), xếp dọc theo thứ tự: Tổng quan · Giới hạn · Cell · Bảo vệ · Phiên bản. */
enum class BatteryGroup { Overview, Limits, Cells, Protection, Versions }

typealias BatteryDataUiState = LiveUiState<BatteryTelemetry>

/** Màn 17 · Theo dõi dữ liệu pin: số liệu thời gian thực, mọi nhóm hiện cùng lúc (thiết kế mới, không có biểu đồ lịch sử). */
@HiltViewModel
class BatteryDataViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    repository: TelemetryRepository,
    clock: Clock,
    settings: SettingsStore,
) : ViewModel() {
    private val projectId = savedStateHandle.toRoute<DataBattery>().projectId
    private val controller = LiveController({ repository.battery(projectId) }, clock, viewModelScope)
    val state: StateFlow<BatteryDataUiState> get() = controller.state
    val temperatureUnit: StateFlow<TemperatureUnit> = settings.temperatureUnitIn(viewModelScope)

    /** "Xem tất cả trạng thái (64 cờ)" đang mở (BO_SUNG_v3.md §13). */
    private val _showAllFlags = MutableStateFlow(false)
    val showAllFlags: StateFlow<Boolean> = _showAllFlags.asStateFlow()

    fun onRefresh() = controller.onRefresh()
    fun onToggleAllFlags() = _showAllFlags.update { !it }
}
