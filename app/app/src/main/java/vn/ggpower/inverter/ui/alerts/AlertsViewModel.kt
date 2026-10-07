package vn.ggpower.inverter.ui.alerts

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
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import vn.ggpower.inverter.data.alert.AlertLoadException
import vn.ggpower.inverter.data.alert.AlertRepository
import vn.ggpower.inverter.domain.alert.AlertFilter
import vn.ggpower.inverter.domain.alert.AlertSummary
import vn.ggpower.inverter.navigation.Alerts
import javax.inject.Inject

sealed interface AlertsUiState {
    data object Loading : AlertsUiState
    data class Error(val noConnection: Boolean) : AlertsUiState
    data class Content(val alerts: List<AlertSummary>, val filter: AlertFilter) : AlertsUiState
}

@HiltViewModel
class AlertsViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    repository: AlertRepository,
) : ViewModel() {
    private val controller = AlertsController(savedStateHandle.toRoute<Alerts>().projectId, repository, viewModelScope)
    val state: StateFlow<AlertsUiState> get() = controller.state
    fun onFilterChange(filter: AlertFilter) = controller.onFilterChange(filter)
    fun retry() = controller.retry()
}

/** Danh sách cảnh báo theo bộ lọc; cập nhật khi đánh dấu đã xử lý ở 10a. Tách khỏi ViewModel để test. */
@OptIn(ExperimentalCoroutinesApi::class)
class AlertsController(private val projectId: String, private val repository: AlertRepository, scope: CoroutineScope) {
    private val filter = MutableStateFlow(AlertFilter.All)
    private val reloads = MutableStateFlow(0)

    private val feed = reloads.flatMapLatest {
        repository.alerts(projectId)
            .map<List<AlertSummary>, Result<List<AlertSummary>>> { Result.success(it) }
            .catch { e -> if (e is AlertLoadException) emit(Result.failure(e)) else throw e }
    }

    val state: StateFlow<AlertsUiState> = combine(feed, filter) { r, f ->
        r.fold(
            onSuccess = { AlertsUiState.Content(it, f) },
            onFailure = { AlertsUiState.Error((it as AlertLoadException).kind == AlertLoadException.Kind.NoConnection) },
        )
    }.stateIn(scope, SharingStarted.WhileSubscribed(5_000), AlertsUiState.Loading)

    fun onFilterChange(f: AlertFilter) { filter.value = f }
    fun retry() = reloads.update { it + 1 }
}
