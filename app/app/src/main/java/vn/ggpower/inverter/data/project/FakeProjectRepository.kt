package vn.ggpower.inverter.data.project

import kotlinx.coroutines.delay
import vn.ggpower.inverter.domain.project.ProjectStatus
import vn.ggpower.inverter.domain.project.ProjectSummary
import vn.ggpower.inverter.domain.project.SiteType
import javax.inject.Inject
import javax.inject.Singleton

/** Dữ liệu mẫu màn 2 (đặc tả §3.2, html/Projects.dc.html). Có độ trễ để thấy trạng thái đang tải. */
@Singleton
class FakeProjectRepository @Inject constructor() : ProjectRepository {

    /** Đổi thành danh sách rỗng để xem trạng thái "Chưa có dự án nào" (ProjectsEmpty.png). */
    var projects: List<ProjectSummary> = SAMPLE

    override suspend fun getProjects(): List<ProjectSummary> {
        delay(FAKE_DELAY_MS)
        return projects
    }

    companion object {
        private const val FAKE_DELAY_MS = 600L

        val SAMPLE = listOf(
            ProjectSummary(
                id = "p1", name = "Nhà của bạn", address = "Văn Giang, Hưng Yên",
                configuration = "Inverter hybrid 5 kW · S16 Pre",
                siteType = SiteType.Home, status = ProjectStatus.Online,
                pvPowerKw = 4.2, todayEnergyKwh = 18.6, batterySocPercent = 76,
            ),
            ProjectSummary(
                id = "p2", name = "Nhà Ân Thi", address = "Mỹ Hào, Hưng Yên",
                configuration = "Inverter 8 kW · 2 x S16 Pre",
                siteType = SiteType.Home, status = ProjectStatus.Online,
                pvPowerKw = 6.1, todayEnergyKwh = 27.4, batterySocPercent = 88,
            ),
            ProjectSummary(
                id = "p3", name = "Nhà máy GG Power", address = "Yên Mỹ, Hưng Yên",
                configuration = "Inverter 30 kW · 4 × S16 Pre",
                siteType = SiteType.Factory, status = ProjectStatus.Warning,
                pvPowerKw = 21.8, todayEnergyKwh = 96.2, batterySocPercent = 54,
            ),
        )
    }
}
