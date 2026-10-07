package vn.ggpower.inverter.ui.projects

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import vn.ggpower.inverter.data.project.FakeProjectRepository
import vn.ggpower.inverter.data.project.ProjectLoadException
import vn.ggpower.inverter.data.project.ProjectRepository
import vn.ggpower.inverter.domain.project.ProjectSummary

@OptIn(ExperimentalCoroutinesApi::class)
class ProjectsViewModelTest {

    private val dispatcher = StandardTestDispatcher()

    private class StubRepo(var result: () -> List<ProjectSummary>) : ProjectRepository {
        var calls = 0
        override suspend fun getProjects(): List<ProjectSummary> { calls++; return result() }
    }

    @Before fun setUp() = Dispatchers.setMain(dispatcher)
    @After fun tearDown() = Dispatchers.resetMain()

    @Test fun `dang tai roi co du lieu`() = runTest(dispatcher) {
        val vm = ProjectsViewModel(StubRepo { FakeProjectRepository.SAMPLE })
        assertEquals(ProjectsUiState.Loading, vm.state.value)
        advanceUntilIdle()
        val s = vm.state.value as ProjectsUiState.Content
        assertEquals(3, s.visible.size)
    }

    @Test fun `khong co du an thi la trang thai rong`() = runTest(dispatcher) {
        val vm = ProjectsViewModel(StubRepo { emptyList() })
        advanceUntilIdle()
        assertTrue((vm.state.value as ProjectsUiState.Content).isEmpty)
    }

    @Test fun `tim kiem loc danh sach, tim khong thay khac voi rong`() = runTest(dispatcher) {
        val vm = ProjectsViewModel(StubRepo { FakeProjectRepository.SAMPLE })
        advanceUntilIdle()
        vm.onQueryChange("nha may")
        assertEquals(listOf("p3"), (vm.state.value as ProjectsUiState.Content).visible.map { it.id })
        vm.onQueryChange("Hà Nội")
        val s = vm.state.value as ProjectsUiState.Content
        assertTrue(s.visible.isEmpty())
        assertEquals(false, s.isEmpty)
    }

    @Test fun `loi mat mang roi thu lai thanh cong`() = runTest(dispatcher) {
        val repo = StubRepo { throw ProjectLoadException(ProjectLoadException.Kind.NoConnection) }
        val vm = ProjectsViewModel(repo)
        advanceUntilIdle()
        assertEquals(ProjectsUiState.Error(noConnection = true), vm.state.value)
        repo.result = { FakeProjectRepository.SAMPLE }
        vm.retry()
        assertEquals(ProjectsUiState.Loading, vm.state.value)
        advanceUntilIdle()
        assertTrue(vm.state.value is ProjectsUiState.Content)
        assertEquals(2, repo.calls)
    }

    @Test fun `tai lai ngam khi quay lai, giu tu khoa, khong hien dang tai, bo qua khi lan dau chua xong`() = runTest(dispatcher) {
        var list = FakeProjectRepository.SAMPLE
        val repo = StubRepo { list }
        val vm = ProjectsViewModel(repo)
        vm.refresh()
        advanceUntilIdle()
        assertEquals(1, repo.calls)
        vm.onQueryChange("nha")
        list = FakeProjectRepository.SAMPLE + FakeProjectRepository.SAMPLE[0].copy(id = "new-1", name = "Nhà mới")
        vm.refresh()
        assertTrue(vm.state.value is ProjectsUiState.Content)
        advanceUntilIdle()
        val s = vm.state.value as ProjectsUiState.Content
        assertEquals(4, s.projects.size)
        assertEquals("nha", s.query)
    }
}
