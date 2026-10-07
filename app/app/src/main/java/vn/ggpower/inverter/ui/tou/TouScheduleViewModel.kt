package vn.ggpower.inverter.ui.tou

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import vn.ggpower.inverter.data.config.ConfigLoadException
import vn.ggpower.inverter.data.config.ConfigRepository
import vn.ggpower.inverter.di.AppModule
import vn.ggpower.inverter.domain.config.ControlState
import vn.ggpower.inverter.domain.config.ProjectConfig
import vn.ggpower.inverter.domain.config.configControls
import vn.ggpower.inverter.domain.tou.canAddStrategy
import vn.ggpower.inverter.navigation.TouSchedule
import javax.inject.Inject
import javax.inject.Named

@HiltViewModel
class TouScheduleViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    repository: ConfigRepository,
    @Named(AppModule.CONFIG_READ_ONLY) configReadOnly: Boolean,
) : ViewModel() {
    private val loader = TouScheduleLoader(savedStateHandle.toRoute<TouSchedule>().projectId, repository, configReadOnly)
    val state: StateFlow<TouScheduleUiState> get() = loader.state

    init { viewModelScope.launch { loader.load() } }

    fun retry() { viewModelScope.launch { loader.load() } }

    /** Gọi mỗi khi màn hiện lại (quay về từ 16b) để thấy danh sách vừa lưu. */
    fun refresh() { viewModelScope.launch { loader.refresh() } }
}

/** Đọc danh sách chiến lược đã lưu (`ConfigSnapshot.strategies`); tách khỏi ViewModel để test. */
class TouScheduleLoader(
    private val projectId: String,
    private val repository: ConfigRepository,
    private val configReadOnly: Boolean,
) {
    private val _state = MutableStateFlow<TouScheduleUiState>(TouScheduleUiState.Loading)
    val state: StateFlow<TouScheduleUiState> = _state.asStateFlow()

    suspend fun load() {
        _state.value = TouScheduleUiState.Loading
        _state.value = try {
            content(repository.getConfig(projectId))
        } catch (e: ConfigLoadException) {
            TouScheduleUiState.Error(noConnection = e.kind == ConfigLoadException.Kind.NoConnection)
        }
    }

    /** Đọc lại không qua trạng thái đang tải; lỗi thì giữ danh sách cũ. Chỉ chạy khi đã có nội dung. */
    suspend fun refresh() {
        if (_state.value !is TouScheduleUiState.Content) return
        try {
            _state.value = content(repository.getConfig(projectId))
        } catch (_: ConfigLoadException) {
        }
    }

    private fun content(config: ProjectConfig): TouScheduleUiState.Content {
        val controls = configControls(config.writeAccess, config.mode, configReadOnly)
        return TouScheduleUiState.Content(
            strategies = config.strategies,
            todayStrategyId = config.todayStrategyId,
            canAdd = config.strategies.canAddStrategy(),
            addEnabled = controls.touSchedule == ControlState.Enabled,
        )
    }
}
