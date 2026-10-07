package vn.ggpower.inverter.ui.data

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import vn.ggpower.inverter.data.telemetry.TelemetryLoadException
import vn.ggpower.inverter.data.telemetry.TelemetryRepository
import vn.ggpower.inverter.domain.telemetry.DeviceKind
import vn.ggpower.inverter.domain.telemetry.HistoryField
import vn.ggpower.inverter.domain.telemetry.HistoryFields
import vn.ggpower.inverter.domain.telemetry.HistoryRange
import vn.ggpower.inverter.domain.telemetry.HistorySeries
import vn.ggpower.inverter.domain.telemetry.toggleField
import java.time.Clock
import java.time.LocalDateTime

/** Thẻ "Biểu đồ lịch sử" (đặc tả §17.4; BO_SUNG_v3.md §12). [index]: điểm đang chọn bằng thanh kéo. */
data class HistoryUiState(
    val device: DeviceKind,
    val range: HistoryRange = HistoryRange.Day,
    val selected: List<HistoryField> = HistoryFields.defaults(device),
    val series: HistorySeries? = null,
    val index: Int = 0,
    val loading: Boolean = true,
    val failed: Boolean = false,
)

/**
 * Tải lịch sử của mọi trường của thiết bị cho khoảng đang chọn (đổi trường không cần tải lại); đổi khoảng thì tải lại
 * và chọn sẵn điểm mới nhất. Dùng chung cho màn 17 inverter và pin.
 */
class HistoryController(
    private val projectId: String,
    private val device: DeviceKind,
    private val repository: TelemetryRepository,
    private val clock: Clock,
    private val scope: CoroutineScope,
) {
    private val _state = MutableStateFlow(HistoryUiState(device))
    val state: StateFlow<HistoryUiState> = _state.asStateFlow()
    private var job: Job? = null

    init { load() }

    fun onRangeChange(range: HistoryRange) {
        if (range == _state.value.range && _state.value.series != null) return
        _state.update { it.copy(range = range) }
        load()
    }

    fun onToggleField(field: HistoryField) = _state.update { it.copy(selected = toggleField(it.selected, field)) }

    fun onSelectIndex(index: Int) = _state.update { s ->
        val max = (s.series?.size ?: 1) - 1
        s.copy(index = index.coerceIn(0, max))
    }

    fun retry() = load()

    private fun load() {
        job?.cancel()
        val range = _state.value.range
        _state.update { it.copy(loading = true, failed = false) }
        job = scope.launch {
            try {
                val series = repository.history(projectId, device, range, HistoryFields.of(device), LocalDateTime.now(clock))
                _state.update { it.copy(series = series, index = series.lastIndex(), loading = false) }
            } catch (_: TelemetryLoadException) {
                _state.update { it.copy(loading = false, failed = true) }
            }
        }
    }
}
