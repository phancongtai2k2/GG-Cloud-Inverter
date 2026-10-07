package vn.ggpower.inverter.ui.support

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import vn.ggpower.inverter.navigation.SupportSent
import vn.ggpower.inverter.ui.service.COPIED_FEEDBACK_MS
import javax.inject.Inject

/** Màn 24 · Gửi yêu cầu thành công. Constructor nhận [initial] để test không cần SavedStateHandle. */
@HiltViewModel
class SupportSentViewModel internal constructor(initial: SupportSentUiState) : ViewModel() {

    @Inject constructor(savedStateHandle: SavedStateHandle) :
        this(savedStateHandle.toRoute<SupportSent>().let { SupportSentUiState(it.ticketCode, it.technical) })

    private val _state = MutableStateFlow(initial)
    val state: StateFlow<SupportSentUiState> = _state.asStateFlow()
    private var resetJob: Job? = null

    /** Giống màn 6: dấu ✓ và thông báo "Đã sao chép…" tự ẩn sau [COPIED_FEEDBACK_MS]. */
    fun onCopied() {
        _state.update { it.copy(copied = true) }
        resetJob?.cancel()
        resetJob = viewModelScope.launch {
            delay(COPIED_FEEDBACK_MS)
            _state.update { it.copy(copied = false) }
        }
    }
}
