package vn.ggpower.inverter.domain.project

import org.junit.Assert.assertEquals
import org.junit.Test

class ProjectSearchTest {

    private fun p(id: String, name: String, address: String) = ProjectSummary(
        id, name, address, "", SiteType.Home, ProjectStatus.Online, null, null, null,
    )

    // Dữ liệu mẫu đặc tả §3.2
    private val all = listOf(
        p("p1", "Nhà của bạn", "Văn Giang, Hưng Yên"),
        p("p2", "Nhà Ân Thi", "Mỹ Hào, Hưng Yên"),
        p("p3", "Nhà máy GG Power", "Yên Mỹ, Hưng Yên"),
    )

    private fun ids(q: String) = all.search(q).map { it.id }

    @Test fun `rong thi giu nguyen`() {
        assertEquals(listOf("p1", "p2", "p3"), ids(""))
        assertEquals(listOf("p1", "p2", "p3"), ids("   "))
    }

    @Test fun `tim theo ten co dau`() = assertEquals(listOf("p2"), ids("Ân Thi"))

    @Test fun `tim khong dau khong phan biet hoa thuong`() {
        assertEquals(listOf("p2"), ids("an thi"))
        assertEquals(listOf("p3"), ids("NHA MAY"))
    }

    @Test fun `tim theo dia chi`() {
        assertEquals(listOf("p1"), ids("van giang"))
        assertEquals(listOf("p1", "p2", "p3"), ids("Hưng Yên"))
    }

    @Test fun `nhieu tu phai khop het, khong can lien nhau`() {
        assertEquals(listOf("p2"), ids("an hao"))
        assertEquals(listOf("p3"), ids("may yen"))
    }

    @Test fun `chu d gach`() = assertEquals("duong", foldVietnamese(" Đường "))

    @Test fun `khong khop`() = assertEquals(emptyList<String>(), ids("Hà Nội"))
}
