package vn.ggpower.inverter.data.project

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test
import vn.ggpower.inverter.domain.project.ProjectInfoInput
import vn.ggpower.inverter.domain.project.ProjectStatus
import java.time.LocalDate

/** Dự án mới hiện ở màn 2 với "Đang chờ kết nối"; tối đa 10 dự án (đặc tả §3.14). */
class FakeAddProjectRepositoryTest {
    private val info = ProjectInfoInput(name = " Nhà mới ", address = "Xuân Quan", installedOn = LocalDate.of(2026, 9, 26), pvKwp = "6,5")

    @Test fun `du an moi duoc them vao danh sach, dang cho ket noi`() = runTest {
        val projects = FakeProjectRepository()
        val repo = FakeAddProjectRepository(projects)
        val result = repo.createProject(NewProject(FakeAddProjectRepository.SAMPLE_DEVICE, info, 6.5, null))
        assertEquals(CreateProjectResult.Created("new-1"), result)
        val added = projects.projects.last()
        assertEquals("Nhà mới", added.name)
        assertEquals(ProjectStatus.WaitingConnection, added.status)
    }

    @Test fun `du 10 du an thi khong tao them`() = runTest {
        val projects = FakeProjectRepository().apply { this.projects = List(10) { FakeProjectRepository.SAMPLE[0].copy(id = "p$it") } }
        val repo = FakeAddProjectRepository(projects)
        assertEquals(CreateProjectResult.LimitReached, repo.createProject(NewProject(FakeAddProjectRepository.SAMPLE_DEVICE, info, 6.5, null)))
    }
}
