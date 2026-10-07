package vn.ggpower.inverter.ui.service

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import vn.ggpower.inverter.data.support.SupportContactRepository
import javax.inject.Inject

/** Màn 6 · Dịch vụ (đặc tả §6). Việc ghi bộ nhớ tạm do UI làm; ViewModel chỉ giữ phản hồi "đã sao chép". */
@HiltViewModel
class ServiceViewModel @Inject constructor(repository: SupportContactRepository) : ViewModel() {

    private val _state = MutableStateFlow(ServiceUiState(contact = repository.getContact()))
    val state: StateFlow<ServiceUiState> = _state.asStateFlow()

    private var resetJob: Job? = null

    /** Sao chép dòng khác hoặc sao chép lại: phản hồi chuyển sang dòng mới và đếm lại từ đầu. */
    fun onCopied(field: ContactField) {
        _state.update { it.copy(copied = field) }
        resetJob?.cancel()
        resetJob = viewModelScope.launch {
            delay(COPIED_FEEDBACK_MS)
            _state.update { it.copy(copied = null) }
        }
    }
}
