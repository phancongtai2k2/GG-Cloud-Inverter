package vn.ggpower.inverter.ui.support

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import vn.ggpower.inverter.data.support.SupportTicketException
import vn.ggpower.inverter.data.support.SupportTicketRepository
import vn.ggpower.inverter.domain.support.normalizedMessage
import vn.ggpower.inverter.navigation.TicketDetail
import vn.ggpower.inverter.ui.service.COPIED_FEEDBACK_MS
import javax.inject.Inject

@HiltViewModel
class TicketDetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    ticketRepository: SupportTicketRepository,
) : ViewModel() {
    private val controller = TicketDetailController(
        ticketId = savedStateHandle.toRoute<TicketDetail>().ticketId,
        repository = ticketRepository,
        scope = viewModelScope,
    )
    val state: StateFlow<TicketDetailUiState> get() = controller.state

    fun onMessage(text: String) = controller.onMessage(text)
    fun onSend() = controller.onSend()
    fun onCopied() = controller.onCopied()
    fun retry() = controller.load()
}

/**
 * Màn 8 · Chi tiết yêu cầu (đặc tả §3.8). Tách khỏi ViewModel để test.
 * - Tin nhắn chỉ hiện trong danh sách khi backend báo đã nhận; gửi lỗi thì giữ chữ trong ô và báo lỗi trên ô.
 * - Phiếu Đã đóng không gửi được tin (chốt 03/10/2026).
 */
class TicketDetailController(
    private val ticketId: String,
    private val repository: SupportTicketRepository,
    private val scope: CoroutineScope,
) {
    private val _state = MutableStateFlow(TicketDetailUiState())
    val state: StateFlow<TicketDetailUiState> = _state.asStateFlow()
    private var loadJob: Job? = null
    private var copiedJob: Job? = null

    init {
        load()
    }

    fun load() {
        loadJob?.cancel()
        _state.update { it.copy(load = DetailLoad.Loading) }
        loadJob = scope.launch {
            repository.detail(ticketId)
                .catch { e ->
                    if (e is SupportTicketException) _state.update { it.copy(load = DetailLoad.Error(e.noConnection)) }
                    else throw e
                }
                .collect { detail -> _state.update { it.copy(load = DetailLoad.Content(detail)) } }
        }
    }

    fun onMessage(text: String) = _state.update { it.copy(message = text) }

    fun onSend() {
        val s = _state.value
        if (!s.canSend) return
        val text = normalizedMessage(s.message) ?: return
        _state.update { it.copy(sending = true, sendFailed = null) }
        scope.launch {
            try {
                val sent = repository.sendMessage(ticketId, text)
                _state.update {
                    val load = it.load
                    it.copy(
                        sending = false,
                        message = "",
                        load = if (load is DetailLoad.Content) DetailLoad.Content(load.detail.withMessage(sent)) else load,
                    )
                }
            } catch (e: SupportTicketException) {
                _state.update { it.copy(sending = false, sendFailed = SubmitFailure(e.noConnection)) }
            }
        }
    }

    /** Giống màn 6 / 24: dấu ✓ và thông báo "Đã sao chép…" tự ẩn sau [COPIED_FEEDBACK_MS]. */
    fun onCopied() {
        _state.update { it.copy(copied = true) }
        copiedJob?.cancel()
        copiedJob = scope.launch {
            delay(COPIED_FEEDBACK_MS)
            _state.update { it.copy(copied = false) }
        }
    }
}
