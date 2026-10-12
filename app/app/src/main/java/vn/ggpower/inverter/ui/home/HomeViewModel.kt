package vn.ggpower.inverter.ui.home

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import vn.ggpower.inverter.data.home.HomeRepository
import vn.ggpower.inverter.data.settings.SettingsStore
import vn.ggpower.inverter.domain.home.HomeLayout
import vn.ggpower.inverter.domain.home.PeriodSelection
import vn.ggpower.inverter.domain.home.StatsPeriod
import vn.ggpower.inverter.navigation.ProjectHome
import java.time.Clock
import java.time.LocalDate
import java.time.LocalDateTime
import javax.inject.Inject

@HiltViewModel
class HomeViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val repository: HomeRepository,
    private val clock: Clock,
    private val settings: SettingsStore,
) : ViewModel() {

    val projectId: String = savedStateHandle.toRoute<ProjectHome>().projectId

    /** Chỉ nhận số liệu khi màn đang hiển thị (đặc tả §17.5: vào nền thì ngắt cập nhật). */
    val state: StateFlow<HomeUiState> = repository.overview(projectId)
        .map<_, HomeUiState> { HomeUiState.Content(it) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HomeUiState.Loading)

    private val stats = StatsController(projectId, repository, clock, viewModelScope)
    val statsState: StateFlow<StatsUiState> = stats.state

    fun onPeriodChange(period: StatsPeriod) = stats.onPeriodChange(period)
    fun onPrevious() = stats.onPrevious()
    fun onNext() = stats.onNext()
    fun onSelectPower(index: Int) = stats.onSelectPower(index)
    fun onSelectBattery(index: Int) = stats.onSelectBattery(index)
    fun onSelectYield(index: Int) = stats.onSelectYield(index)

    /** Bố cục màn (kiểu sơ đồ, thẻ); mặc định cho tới khi DataStore đọc xong. */
    val layout: StateFlow<HomeLayout> = settings.homeLayout
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HomeLayout())

    /** "Áp dụng" ở màn Tuỳ chỉnh màn hình chính. */
    fun onApplyLayout(layout: HomeLayout) {
        viewModelScope.launch { settings.setHomeLayout(layout) }
    }
}

/**
 * Logic thống kê tách khỏi ViewModel để test được không cần SavedStateHandle / điều hướng.
 * Ngày: tải đường 15 phút (chọn sẵn điểm mới nhất); Tháng / Năm: tải năng lượng theo ngày / tháng (chọn sẵn cột chứa
 * thời điểm hiện tại). Sản lượng PV luôn tải theo kỳ, chọn sẵn cột hiện tại (prototype: 08:00–10:00 lúc 09:41).
 */
class StatsController(
    private val projectId: String,
    private val repository: HomeRepository,
    private val clock: Clock,
    private val scope: kotlinx.coroutines.CoroutineScope,
) {
    private val today: LocalDate = LocalDate.now(clock)
    private val _state = MutableStateFlow(StatsUiState(PeriodSelection(StatsPeriod.Day, today), today))
    val state: StateFlow<StatsUiState> = _state.asStateFlow()

    private var curvesJob: Job? = null
    private var energyJob: Job? = null
    private var yieldJob: Job? = null
    private var summaryJob: Job? = null

    init {
        loadPowerAndBattery()
        loadYield()
        loadSummary()
    }

    fun onPeriodChange(period: StatsPeriod) {
        if (period == _state.value.selection.period) return
        select(_state.value.selection.withPeriod(period))
    }

    fun onPrevious() = select(_state.value.selection.previous())

    fun onNext() = select(_state.value.selection.next(today))

    fun onSelectPower(index: Int) = _state.update { it.copy(powerIndex = index) }
    fun onSelectBattery(index: Int) = _state.update { it.copy(batteryIndex = index) }
    fun onSelectYield(index: Int) = _state.update { it.copy(yieldIndex = index) }

    private fun select(selection: PeriodSelection) {
        val old = _state.value.selection
        if (selection == old) return
        _state.update { it.copy(selection = selection) }
        loadPowerAndBattery()
        loadYield()
        loadSummary()
    }

    private fun loadPowerAndBattery() {
        curvesJob?.cancel()
        energyJob?.cancel()
        val selection = _state.value.selection
        _state.update { it.copy(curves = null, energyBuckets = null) }
        if (selection.period != StatsPeriod.Day) {
            energyJob = scope.launch {
                val buckets = repository.energyBuckets(projectId, selection)
                val index = selection.currentBucket(LocalDateTime.now(clock)).coerceIn(0, (buckets.size - 1).coerceAtLeast(0))
                _state.update { it.copy(energyBuckets = buckets, powerIndex = index, batteryIndex = index) }
            }
            return
        }
        val date = selection.anchor
        curvesJob = scope.launch {
            val curves = repository.dayCurves(projectId, date)
            _state.update {
                it.copy(
                    curves = curves,
                    powerIndex = curves.power.lastIndex.coerceAtLeast(0),
                    batteryIndex = curves.battery.lastIndex.coerceAtLeast(0),
                )
            }
        }
    }

    private fun loadSummary() {
        summaryJob?.cancel()
        val selection = _state.value.selection
        _state.update { it.copy(summary = null) }
        summaryJob = scope.launch {
            val summary = repository.periodSummary(projectId, selection)
            _state.update { it.copy(summary = summary) }
        }
    }

    private fun loadYield() {
        yieldJob?.cancel()
        val selection = _state.value.selection
        _state.update { it.copy(yieldBuckets = null) }
        yieldJob = scope.launch {
            val buckets = repository.yieldBuckets(projectId, selection)
            _state.update {
                it.copy(
                    yieldBuckets = buckets,
                    yieldIndex = selection.currentBucket(LocalDateTime.now(clock)).coerceIn(0, (buckets.size - 1).coerceAtLeast(0)),
                )
            }
        }
    }
}
