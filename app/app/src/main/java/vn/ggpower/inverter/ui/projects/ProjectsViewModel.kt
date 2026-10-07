package vn.ggpower.inverter.ui.projects

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import vn.ggpower.inverter.data.project.ProjectLoadException
import vn.ggpower.inverter.data.project.ProjectRepository
import javax.inject.Inject

@HiltViewModel
class ProjectsViewModel @Inject constructor(
    private val repository: ProjectRepository,
) : ViewModel() {

    private val _state = MutableStateFlow<ProjectsUiState>(ProjectsUiState.Loading)
    val state: StateFlow<ProjectsUiState> = _state.asStateFlow()

    init { load() }

    fun retry() = load()

    /**
     * Tải lại ngầm khi màn quay lại phía trước (ví dụ vừa tạo dự án ở luồng 14): giữ chữ tìm kiếm, không hiện khung
     * đang tải; bỏ qua khi lần tải đầu chưa xong. Lỗi khi tải ngầm thì giữ danh sách cũ.
     */
    fun refresh() {
        if (_state.value !is ProjectsUiState.Content) return
        viewModelScope.launch {
            try {
                val projects = repository.getProjects()
                _state.update { s -> if (s is ProjectsUiState.Content) s.copy(projects = projects) else s }
            } catch (_: ProjectLoadException) {
                // Giữ danh sách đang hiện; lần mở sau sẽ thử lại.
            }
        }
    }

    fun onQueryChange(query: String) = _state.update { s ->
        if (s is ProjectsUiState.Content) s.copy(query = query) else s
    }

    private fun load() {
        _state.value = ProjectsUiState.Loading
        viewModelScope.launch {
            _state.value = try {
                ProjectsUiState.Content(repository.getProjects())
            } catch (e: ProjectLoadException) {
                ProjectsUiState.Error(noConnection = e.kind == ProjectLoadException.Kind.NoConnection)
            }
        }
    }
}
