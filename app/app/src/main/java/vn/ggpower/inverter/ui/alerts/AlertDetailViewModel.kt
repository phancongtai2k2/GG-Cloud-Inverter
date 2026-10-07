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
import kotlinx.coroutines.launch
import vn.ggpower.inverter.data.alert.AlertLoadException
import vn.ggpower.inverter.data.alert.AlertRepository
import vn.ggpower.inverter.data.settings.SettingsStore
import vn.ggpower.inverter.domain.alert.AlertDetail
import vn.ggpower.inverter.domain.settings.TemperatureUnit
import vn.ggpower.inverter.navigation.AlertDetail as AlertDetailRoute
import java.time.Clock
import java.time.LocalDateTime
import javax.inject.Inject

sealed interface AlertDetailUiState {
    data object Loading : AlertDetailUiState
    data class Error(val noConnection: Boolean) : AlertDetailUiState

    /**
     * [now]: để tính thời lượng của cảnh báo đang diễn ra. [saving]: đang gửi đánh dấu / bỏ đánh dấu (khoá nút).
     * [saveFailed]: lần đánh dấu vừa rồi lỗi → snackbar; null khi không có lỗi.
     */
    data class Content(
        val detail: AlertDetail,
        val now: LocalDateTime,
        val saving: Boolean = false,
        val saveFailed: SaveFailure? = null,
    ) : AlertDetailUiState
}

data class SaveFailure(val id: Long, val noConnection: Boolean)

@HiltViewModel
class AlertDetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    repository: AlertRepository,
    clock: Clock,
    settings: SettingsStore,
) : ViewModel() {
    private val route = savedStateHandle.toRoute<AlertDetailRoute>()
    private val controller = AlertDetailController(route.projectId, route.alertId, repository, clock, viewModelScope)
    val state: StateFlow<AlertDetailUiState> get() = controller.state
    val temperatureUnit: StateFlow<TemperatureUnit> = settings.temperatureUnitIn(viewModelScope)
    fun onToggleResolved() = controller.onToggleResolved()
    fun onSaveFailureShown(id: Long) = controller.onSaveFailureShown(id)
    fun retry() = controller.retry()
}

/** Chi tiết cảnh báo + đánh dấu đã xử lý (Thay đổi 0.6 §6; quyết 02/10/2026). Tách khỏi ViewModel để test. */
@OptIn(ExperimentalCoroutinesApi::class)
class AlertDetailController(
    private val projectId: String,
    private val alertId: String,
    private val repository: AlertRepository,
    private val clock: Clock,
    private val scope: CoroutineScope,
) {
    private val reloads = MutableStateFlow(0)
    private val saving = MutableStateFlow(false)
    private val failure = MutableStateFlow<SaveFailure?>(null)
    private var failureId = 0L
    private var current: AlertDetail? = null

    private val feed = reloads.flatMapLatest {
        repository.detail(projectId, alertId)
            .map<AlertDetail, Result<AlertDetail>> { Result.success(it) }
            .catch { e -> if (e is AlertLoadException) emit(Result.failure(e)) else throw e }
    }

    val state: StateFlow<AlertDetailUiState> = combine(feed, saving, failure) { r, busy, fail ->
        r.fold(
            onSuccess = {
                current = it
                AlertDetailUiState.Content(it, LocalDateTime.now(clock), busy, fail)
            },
            onFailure = { AlertDetailUiState.Error((it as AlertLoadException).kind == AlertLoadException.Kind.NoConnection) },
        )
    }.stateIn(scope, SharingStarted.WhileSubscribed(5_000), AlertDetailUiState.Loading)

    /** Bật / tắt "Đã xử lý"; giao diện đổi khi backend xác nhận (luồng chi tiết phát lại). */
    fun onToggleResolved() {
        val d = current ?: return
        if (saving.value) return
        saving.value = true
        scope.launch {
            try {
                repository.setResolved(projectId, alertId, !d.resolved)
            } catch (e: AlertLoadException) {
                failure.value = SaveFailure(++failureId, e.kind == AlertLoadException.Kind.NoConnection)
            } finally {
                saving.value = false
            }
        }
    }

    fun onSaveFailureShown(id: Long) = failure.update { if (it?.id == id) null else it }

    fun retry() = reloads.update { it + 1 }
}
