package vn.ggpower.inverter.ui.warranty

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import vn.ggpower.inverter.data.warranty.WarrantyLoadException
import vn.ggpower.inverter.data.warranty.WarrantyRepository
import vn.ggpower.inverter.navigation.Warranty
import java.time.Clock
import java.time.LocalDate
import javax.inject.Inject

@HiltViewModel
class WarrantyViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    repository: WarrantyRepository,
    clock: Clock,
) : ViewModel() {
    private val loader = WarrantyLoader(savedStateHandle.toRoute<Warranty>().projectId, repository, clock)
    val state: StateFlow<WarrantyUiState> get() = loader.state

    init { retry() }

    fun retry() { viewModelScope.launch { loader.load() } }
}

/** Tải bảo hành của dự án; tách khỏi ViewModel để test không cần SavedStateHandle. */
class WarrantyLoader(
    private val projectId: String,
    private val repository: WarrantyRepository,
    private val clock: Clock,
) {
    private val _state = MutableStateFlow<WarrantyUiState>(WarrantyUiState.Loading)
    val state: StateFlow<WarrantyUiState> = _state.asStateFlow()

    suspend fun load() {
        _state.value = WarrantyUiState.Loading
        _state.value = try {
            WarrantyUiState.Content(repository.getWarranty(projectId), LocalDate.now(clock))
        } catch (e: WarrantyLoadException) {
            WarrantyUiState.Error(noConnection = e.kind == WarrantyLoadException.Kind.NoConnection)
        }
    }
}
