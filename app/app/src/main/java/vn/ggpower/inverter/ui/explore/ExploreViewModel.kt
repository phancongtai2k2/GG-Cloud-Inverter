package vn.ggpower.inverter.ui.explore

import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import vn.ggpower.inverter.data.explore.ExploreRepository
import vn.ggpower.inverter.domain.explore.ExploreTab
import vn.ggpower.inverter.domain.explore.search
import javax.inject.Inject

/** Màn 7 · Khám phá (đặc tả §7): giữ thẻ đang chọn và câu tìm; ô tìm lọc nội dung của thẻ đang mở. */
@HiltViewModel
class ExploreViewModel @Inject constructor(repository: ExploreRepository) : ViewModel() {

    private val all = repository.getContent()

    private val _state = MutableStateFlow(ExploreUiState(content = all))
    val state: StateFlow<ExploreUiState> = _state.asStateFlow()

    fun onTabSelected(tab: ExploreTab) = _state.update { it.copy(tab = tab) }

    fun onQueryChange(query: String) = _state.update { it.copy(query = query, content = all.search(query)) }
}
